#!/bin/bash

# ---------------------------------------------------------------------------
# Run a local PirateChain (ARRR) wallet in Regtest Mode.
#
# This is the local-development counterpart of the "Deploy PirateChain Wallet" play
# in infrastructure/ansible/playbooks/deploy-services.yml. Instead of provisioning
# a remote host, it spins everything up on your machine using Docker:
#   1. Builds the pirated image           (wallets/pirate-chain/Dockerfile)
#   2. Fetches the Zcash/Pirate zk-SNARK params (once, cached locally)
#   3. Writes a regtest PIRATE.conf
#   4. Runs pirated and a "heartbeat miner" that mines 1 block/min
#      so the chain keeps advancing (regtest does not mine on its own).
#
# Prerequisites:
#   - Docker installed and running.
#
# Usage:
#   scripts/run_piratechain_regtest_local.sh [up|down|status|logs|cli|expose|unexpose ...]
#
#   up        Build images (if needed) and start the regtest stack (default).
#   down      Stop and remove the regtest containers.
#   status    Show the status of the regtest containers.
#   logs      Follow the pirated container logs (Ctrl-C to stop).
#   cli       Run a pirate-cli command against the node,
#             e.g.: scripts/run_piratechain_regtest_local.sh cli getinfo
#   expose    Open the host firewall for the RPC port so the wallet is
#             reachable from your LAN / the internet (uses ufw, firewalld or
#             iptables, whichever is active; needs sudo).
#   unexpose  Remove that firewall rule again.
#
# Exposing to the LAN / internet:
#   With --network host the containers bind directly to the host's ports, so the
#   only thing that usually blocks LAN/internet access is the host firewall.
#   Run 'expose' (or set PIRATE_RPC_EXPOSE=true) to open PIRATE_RPC_PORT.
#   NOTE: pirated RPC here runs WITHOUT TLS, although it has basic auth.
#   Only expose a throwaway regtest wallet you don't mind sharing.
#
# Configuration (override via environment variables):
#   PIRATE_RPC_USER       RPC username           (default: gimlee)
#   PIRATE_RPC_PASSWORD   RPC password           (default: gimlee)
#   PIRATE_RPC_PORT       Host RPC port          (default: 45455)
#   PIRATE_LWD_PORT       lightwalletd gRPC port (default: 45467)
#   PIRATE_LWD_EXPOSE     Auto-open the firewall for ports on 'up'
#                        and remove it on 'down' (true/false, default: false)
#   PIRATE_DATA_DIR       Local data/params dir  (default: ./.piratechain-regtest-local)
#   PIRATE_MINE_INTERVAL  Seconds between mined blocks (default: 60)
# ---------------------------------------------------------------------------

set -euo pipefail

# --- Resolve repository root so the script works from any working directory ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source shared firewall library
# shellcheck source=scripts/lib_firewall.sh
source "$SCRIPT_DIR/lib_firewall.sh"

# --- Configuration ---
PIRATE_RPC_USER="${PIRATE_RPC_USER:-gimlee}"
PIRATE_RPC_PASSWORD="${PIRATE_RPC_PASSWORD:-gimlee}"
PIRATE_RPC_PORT="${PIRATE_RPC_PORT:-45455}"
PIRATE_LWD_PORT="${PIRATE_LWD_PORT:-45467}"
PIRATE_LWD_EXPOSE="${PIRATE_LWD_EXPOSE:-${PIRATE_RPC_EXPOSE:-false}}"
PIRATE_DATA_DIR="${PIRATE_DATA_DIR:-$REPO_ROOT/.piratechain-regtest-local}"
PIRATE_MINE_INTERVAL="${PIRATE_MINE_INTERVAL:-60}"

# Image names mirror the ansible deployment.
PIRATE_IMAGE="piratechain:latest"
LWD_IMAGE="piratechain-lightwalletd:latest"

# Container names (suffixed to make them obviously local/regtest).
NODE_CONTAINER="piratechain-regtest-local"
LWD_CONTAINER="piratechain-lightwalletd-regtest-local"
MINER_CONTAINER="piratechain-miner-regtest-local"

PARAMS_DIR="$PIRATE_DATA_DIR/.zcash-params"
NODE_DATA_DIR="$PIRATE_DATA_DIR/PIRATE"
CONF_FILE="$NODE_DATA_DIR/PIRATE.conf"

# ---------------------------------------------------------------------------

require_docker() {
    if ! command -v docker &> /dev/null; then
        echo "❌ Error: 'docker' command not found. Please install Docker first."
        exit 1
    fi
    if ! docker info &> /dev/null; then
        echo "❌ Error: Docker daemon does not seem to be running."
        exit 1
    fi
}

build_image() {
    echo "🔨 Building PirateChain node image ($PIRATE_IMAGE)..."
    docker build -t "$PIRATE_IMAGE" -f "$REPO_ROOT/wallets/pirate-chain/Dockerfile" "$REPO_ROOT/wallets/pirate-chain"

    echo "🔨 Building PirateChain lightwalletd image ($LWD_IMAGE)..."
    docker build -t "$LWD_IMAGE" -f "$REPO_ROOT/wallets/pirate-chain/lightwalletd.Dockerfile" "$REPO_ROOT/wallets/pirate-chain/" >/dev/null
}

fetch_params() {
    # zk-SNARK params are required for Sapling. They are large (~1.7GB) but only
    # need to be downloaded once; we cache them in the local data directory.
    if [ -f "$PARAMS_DIR/sapling-output.params" ]; then
        echo "✅ PirateChain params already present, skipping download."
        return
    fi

    echo "⬇️  Fetching PirateChain params (this can take a while on first run)..."
    mkdir -p "$PIRATE_DATA_DIR"
    # Run fetch-params.sh inside the node image; HOME is set so params land in
    # $PIRATE_DATA_DIR/.zcash-params on the host via the bind mount.
    docker run --rm \
        -e HOME=/data \
        -v "$PIRATE_DATA_DIR:/data" \
        --entrypoint /bin/bash \
        "$PIRATE_IMAGE" -c '
            set -e
            apt-get update >/dev/null && apt-get install -y curl >/dev/null
            curl -fsSL https://raw.githubusercontent.com/PirateNetwork/pirate/master/zcutil/fetch-params.sh -o /tmp/fetch-params.sh
            chmod +x /tmp/fetch-params.sh
            /tmp/fetch-params.sh
        '
}

write_config() {
    echo "📝 Writing regtest PIRATE.conf to $CONF_FILE"
    mkdir -p "$NODE_DATA_DIR"
    cat > "$CONF_FILE" <<EOF
server=1
listen=1
txindex=1
addressindex=1
spentindex=1
timestampindex=1
rpcuser=$PIRATE_RPC_USER
rpcpassword=$PIRATE_RPC_PASSWORD
rpcport=$PIRATE_RPC_PORT
rpcbind=0.0.0.0
rpcallowip=0.0.0.0/0
# Regtest settings
regtest=1
gen=0
lightwalletd=1
# Force Sapling activation at block 1 (ID:Height)
# Overwinter: 5ba81b19, Sapling: 76b809bb
nuparams=5ba81b19:1
nuparams=76b809bb:1
EOF
    chmod 600 "$CONF_FILE"
}

remove_container_if_exists() {
    local name="$1"
    if [ -n "$(docker ps -aq -f name="^${name}$")" ]; then
        docker rm -f "$name" >/dev/null
    fi
}

# --- Firewall handling -----------------------------------------------------

expose_firewall() {
    expose_port "$PIRATE_RPC_PORT" "piratechain-regtest-local RPC"
    expose_port "$PIRATE_LWD_PORT" "piratechain-regtest-local lightwalletd"
    echo "✅ PirateChain RPC is now reachable on TCP $PIRATE_RPC_PORT from your LAN / the internet."
    echo "✅ lightwalletd is now reachable on TCP $PIRATE_LWD_PORT from your LAN / the internet."
}

unexpose_firewall() {
    unexpose_port "$PIRATE_LWD_PORT"
    unexpose_port "$PIRATE_RPC_PORT"
    echo "✅ Firewall rules for TCP $PIRATE_RPC_PORT and $PIRATE_LWD_PORT removed."
}

start_stack() {
    # Start fresh so config changes always take effect.
    remove_container_if_exists "$NODE_CONTAINER"
    remove_container_if_exists "$LWD_CONTAINER"
    remove_container_if_exists "$MINER_CONTAINER"

    echo "🚀 Starting PirateChain regtest node ($NODE_CONTAINER) on RPC port $PIRATE_RPC_PORT..."
    docker run -d \
        --name "$NODE_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -v "$NODE_DATA_DIR:/root/.komodo/PIRATE" \
        -v "$CONF_FILE:/root/.komodo/PIRATE/PIRATE.conf:ro" \
        -v "$PARAMS_DIR:/root/.zcash-params:ro" \
        "$PIRATE_IMAGE" ./pirated -printtoconsole -ac_name=PIRATE -regtest >/dev/null

    echo "⏳ Waiting for PirateChain RPC to be ready..."
    local attempt=1
    while ! "$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli getinfo >/dev/null 2>&1; do
        if [ $attempt -gt 30 ]; then
            echo "❌ Error: PirateChain RPC did not become ready in time."
            exit 1
        fi
        sleep 2
        attempt=$((attempt + 1))
    done

    # If this is a fresh chain (height 0), initialize it
    local current_height
    current_height=$("$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli getblockcount 2>/dev/null || echo "0")
    if [ "$current_height" -eq 0 ]; then
        echo "⛏️  Fresh chain detected. Mining initial blocks (101)..."
        # Generate 101 blocks so coinbase is mature and spendable
        "$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli generate 101 >/dev/null

        echo "🛡️  Shielding coinbase funds..."
        local zaddr
        zaddr=$("$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli z_getnewaddress)
        "$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli z_shieldcoinbase "*" "$zaddr" >/dev/null

        echo "⛏️  Mining 1 block to confirm shielding..."
        "$REPO_ROOT/scripts/run_piratechain_regtest_local.sh" cli generate 1 >/dev/null
    fi

    echo "🚀 Starting lightwalletd ($LWD_CONTAINER) on gRPC port $PIRATE_LWD_PORT..."
    docker run -d \
        --name "$LWD_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -v "$NODE_DATA_DIR:/root/.komodo/PIRATE:ro" \
        "$LWD_IMAGE" \
        --pirate-conf-path /root/.komodo/PIRATE/PIRATE.conf \
        --grpc-bind-addr "0.0.0.0:$PIRATE_LWD_PORT" \
        --data-dir /root/lwd-data \
        --log-file /dev/stdout \
        --no-tls-very-insecure >/dev/null

    echo "⛏️  Starting heartbeat miner ($MINER_CONTAINER, 1 block / ${PIRATE_MINE_INTERVAL}s)..."
    docker run -d \
        --name "$MINER_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -e RPC_USER="$PIRATE_RPC_USER" \
        -e RPC_PASSWORD="$PIRATE_RPC_PASSWORD" \
        -e RPC_PORT="$PIRATE_RPC_PORT" \
        -e MINE_INTERVAL="$PIRATE_MINE_INTERVAL" \
        alpine:latest \
        sh -c '
            apk add --no-cache curl >/dev/null &&
            echo "Starting Heartbeat Miner..." &&
            while true; do
                curl -s --user "$RPC_USER:$RPC_PASSWORD" \
                    --data-binary "{\"jsonrpc\": \"1.0\", \"id\": \"miner\", \"method\": \"generate\", \"params\": [1]}" \
                    -H "content-type: text/plain;" \
                    "http://127.0.0.1:$RPC_PORT/" > /dev/null;
                echo "[$(date)] Mined 1 block";
                sleep "$MINE_INTERVAL";
            done
        ' >/dev/null

    echo ""
    echo "✅ PirateChain regtest stack is up!"
    echo "   • RPC:          http://127.0.0.1:$PIRATE_RPC_PORT (user: $PIRATE_RPC_USER)"
    echo "   • lightwalletd: 127.0.0.1:$PIRATE_LWD_PORT (gRPC, no TLS)"
    echo "   • Data dir:     $PIRATE_DATA_DIR"
    echo ""
    if is_truthy "$PIRATE_LWD_EXPOSE"; then
        echo ""
        expose_firewall
    else
        echo ""
        echo "   ℹ️  The ports are currently only reachable from this machine if your"
        echo "      firewall blocks them. To open them to your LAN / internet:"
        echo "        scripts/run_piratechain_regtest_local.sh expose"
    fi

    echo ""
    echo "   Useful commands:"
    echo "     scripts/run_piratechain_regtest_local.sh status"
    echo "     scripts/run_piratechain_regtest_local.sh logs"
    echo "     scripts/run_piratechain_regtest_local.sh cli getinfo"
    echo "     scripts/run_piratechain_regtest_local.sh expose"
    echo "     scripts/run_piratechain_regtest_local.sh down"
}

stop_stack() {
    echo "🛑 Stopping PirateChain regtest stack..."
    remove_container_if_exists "$MINER_CONTAINER"
    remove_container_if_exists "$LWD_CONTAINER"
    remove_container_if_exists "$NODE_CONTAINER"
    if is_truthy "$PIRATE_LWD_EXPOSE"; then
        unexpose_firewall
    fi
    echo "✅ Stopped. (Blockchain data & params kept in $PIRATE_DATA_DIR)"
}

show_status() {
    docker ps -a --filter "name=$NODE_CONTAINER" --filter "name=$LWD_CONTAINER" --filter "name=$MINER_CONTAINER" \
        --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

follow_logs() {
    docker logs -f "$NODE_CONTAINER"
}

run_cli() {
    docker exec "$NODE_CONTAINER" ./pirate-cli -ac_name=PIRATE -conf=/root/.komodo/PIRATE/PIRATE.conf "$@"
}

# --- Main ---
COMMAND="${1:-up}"
case "$COMMAND" in
    up)
        require_docker
        build_image
        fetch_params
        write_config
        start_stack
        ;;
    down)
        require_docker
        stop_stack
        ;;
    status)
        require_docker
        show_status
        ;;
    logs)
        require_docker
        follow_logs
        ;;
    cli)
        require_docker
        shift
        run_cli "$@"
        ;;
    expose)
        expose_firewall
        ;;
    unexpose)
        unexpose_firewall
        ;;
    *)
        echo "Usage: $0 [up|down|status|logs|cli|expose|unexpose ...]"
        exit 1
        ;;
esac
