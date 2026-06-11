#!/bin/bash

# ---------------------------------------------------------------------------
# Run a local Ycash (YEC) wallet in Regtest Mode.
#
# This is the local-development counterpart of the "Deploy Ycash Wallet" play
# in infrastructure/ansible/playbooks/deploy-services.yml (the
# 'gimlee-internal-testnet' instance). Instead of provisioning a remote host,
# it spins everything up on your machine using Docker:
#   1. Builds the ycashd image          (wallets/ycash/Dockerfile)
#   2. Builds the lightwalletd image    (wallets/ycash/lightwalletd.Dockerfile)
#   3. Fetches the Zcash/Ycash zk-SNARK params (once, cached locally)
#   4. Writes a regtest ycash.conf (Sapling/Overwinter forced active at block 1)
#   5. Runs ycashd, lightwalletd and a "heartbeat miner" that mines 1 block/min
#      so the chain keeps advancing (regtest does not mine on its own).
#
# Prerequisites:
#   - Docker installed and running.
#
# Usage:
#   scripts/run_ycash_regtest_local.sh [up|down|status|logs|cli|expose|unexpose ...]
#
#   up        Build images (if needed) and start the regtest stack (default).
#   down      Stop and remove the regtest containers.
#   status    Show the status of the regtest containers.
#   logs      Follow the ycashd container logs (Ctrl-C to stop).
#   cli       Run a ycash-cli command against the node,
#             e.g.: scripts/run_ycash_regtest_local.sh cli getinfo
#   expose    Open the host firewall for the lightwalletd port so the wallet is
#             reachable from your LAN / the internet (uses ufw, firewalld or
#             iptables, whichever is active; needs sudo).
#   unexpose  Remove that firewall rule again.
#
# Exposing to the LAN / internet:
#   With --network host the containers bind directly to the host's ports, so the
#   only thing that usually blocks LAN/internet access is the host firewall.
#   Run 'expose' (or set YCASH_LWD_EXPOSE=true) to open YCASH_LWD_PORT.
#   NOTE: lightwalletd here runs WITHOUT TLS and the node has no auth on the
#   gRPC port, so only expose a throwaway regtest wallet you don't mind sharing.
#
# Configuration (override via environment variables):
#   YCASH_RPC_USER       RPC username           (default: gimlee)
#   YCASH_RPC_PASSWORD   RPC password           (default: gimlee)
#   YCASH_RPC_PORT       Host RPC port          (default: 18232)
#   YCASH_LWD_PORT       lightwalletd gRPC port (default: 19067)
#   YCASH_RPC_EXPOSE     Auto-open the firewall for YCASH_RPC_PORT on 'up'
#                        and remove it on 'down' (true/false, default: false)
#   YCASH_LWD_EXPOSE     Auto-open the firewall for YCASH_LWD_PORT on 'up'
#                        and remove it on 'down' (true/false, default: false)
#   YCASH_DATA_DIR       Local data/params dir  (default: ./.ycash-regtest-local)
#   YCASH_MINE_INTERVAL  Seconds between mined blocks (default: 60)
# ---------------------------------------------------------------------------

set -euo pipefail

# --- Resolve repository root so the script works from any working directory ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# Source shared firewall library
# shellcheck source=scripts/lib_firewall.sh
source "$SCRIPT_DIR/lib_firewall.sh"

# --- Configuration ---
YCASH_RPC_USER="${YCASH_RPC_USER:-gimlee}"
YCASH_RPC_PASSWORD="${YCASH_RPC_PASSWORD:-gimlee}"
YCASH_RPC_PORT="${YCASH_RPC_PORT:-18232}"
YCASH_LWD_PORT="${YCASH_LWD_PORT:-19067}"
YCASH_RPC_EXPOSE="${YCASH_RPC_EXPOSE:-false}"
YCASH_LWD_EXPOSE="${YCASH_LWD_EXPOSE:-false}"
YCASH_DATA_DIR="${YCASH_DATA_DIR:-$REPO_ROOT/.ycash-regtest-local}"
YCASH_MINE_INTERVAL="${YCASH_MINE_INTERVAL:-60}"

# Image names mirror the ansible deployment.
YCASH_IMAGE="ycash:latest"
LWD_IMAGE="ycash-lightwalletd:latest"

# Container names (suffixed to make them obviously local/regtest).
NODE_CONTAINER="ycash-regtest-local"
LWD_CONTAINER="ycash-lightwalletd-regtest-local"
MINER_CONTAINER="ycash-miner-regtest-local"

PARAMS_DIR="$YCASH_DATA_DIR/.zcash-params"
NODE_DATA_DIR="$YCASH_DATA_DIR/gimlee-internal-testnet"
CONF_FILE="$NODE_DATA_DIR/ycash.conf"

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

build_images() {
    echo "🔨 Building Ycash node image ($YCASH_IMAGE)..."
    docker build -t "$YCASH_IMAGE" -f "$REPO_ROOT/wallets/ycash/Dockerfile" "$REPO_ROOT/wallets/ycash"

    echo "🔨 Building Ycash lightwalletd image ($LWD_IMAGE)..."
    docker build -t "$LWD_IMAGE" -f "$REPO_ROOT/wallets/ycash/lightwalletd.Dockerfile" "$REPO_ROOT/wallets/ycash"
}

fetch_params() {
    # zk-SNARK params are required for Sapling. They are large (~1.7GB) but only
    # need to be downloaded once; we cache them in the local data directory.
    if [ -f "$PARAMS_DIR/sapling-output.params" ]; then
        echo "✅ Zcash params already present, skipping download."
        return
    fi

    echo "⬇️  Fetching Zcash/Ycash params (this can take a while on first run)..."
    mkdir -p "$YCASH_DATA_DIR"
    # Run fetch-params.sh inside the node image; HOME is set so params land in
    # $YCASH_DATA_DIR/.zcash-params on the host via the bind mount.
    docker run --rm \
        -e HOME=/data \
        -v "$YCASH_DATA_DIR:/data" \
        --entrypoint /bin/bash \
        "$YCASH_IMAGE" -c '
            set -e
            apt-get update >/dev/null && apt-get install -y curl >/dev/null
            curl -fsSL https://raw.githubusercontent.com/ycashfoundation/ycash/master/zcutil/fetch-params.sh -o /tmp/fetch-params.sh
            chmod +x /tmp/fetch-params.sh
            /tmp/fetch-params.sh
        '
}

write_config() {
    echo "📝 Writing regtest ycash.conf to $CONF_FILE"
    mkdir -p "$NODE_DATA_DIR"
    # Mirrors the 'gimlee-internal-testnet' config from deploy-services.yml.
    cat > "$CONF_FILE" <<EOF
server=1
listen=1
discover=0
txindex=1
insightexplorer=1
experimentalfeatures=1
lightwalletd=1
addressindex=1
timestampindex=1
spentindex=1
# Private solo gimlee-internal-testnet (Regtest Mode)
regtest=1
gen=0
# Force Sapling activation at block 1 (ID:Height)
# Overwinter: 5ba81b19, Sapling: 76b809bb
nuparams=5ba81b19:1
nuparams=76b809bb:1
rpcuser=$YCASH_RPC_USER
rpcpassword=$YCASH_RPC_PASSWORD
rpcport=$YCASH_RPC_PORT
rpcbind=0.0.0.0
rpcallowip=0.0.0.0/0
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
# We only manage the lightwalletd gRPC port. ycashd's RPC port is intentionally
# left closed: it is the trusted control channel and must not be world-open.

expose_firewall() {
    if is_truthy "$YCASH_RPC_EXPOSE"; then
        expose_port "$YCASH_RPC_PORT" "ycash-regtest-local RPC"
    fi
    expose_port "$YCASH_LWD_PORT" "ycash-regtest-local lightwalletd"
    echo "✅ lightwalletd is now reachable on TCP $YCASH_LWD_PORT from your LAN / the internet."
    if is_truthy "$YCASH_RPC_EXPOSE"; then
        echo "✅ Ycash RPC is now reachable on TCP $YCASH_RPC_PORT from your LAN / the internet."
    fi
}

unexpose_firewall() {
    unexpose_port "$YCASH_LWD_PORT"
    if is_truthy "$YCASH_RPC_EXPOSE"; then
        unexpose_port "$YCASH_RPC_PORT"
    fi
    echo "✅ Firewall rules removed."
}

start_stack() {
    # Start fresh so config changes always take effect.
    remove_container_if_exists "$NODE_CONTAINER"
    remove_container_if_exists "$LWD_CONTAINER"
    remove_container_if_exists "$MINER_CONTAINER"

    echo "🚀 Starting Ycash regtest node ($NODE_CONTAINER) on RPC port $YCASH_RPC_PORT..."
    docker run -d \
        --name "$NODE_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -v "$NODE_DATA_DIR:/root/.ycash" \
        -v "$CONF_FILE:/root/.ycash/ycash.conf:ro" \
        -v "$PARAMS_DIR:/root/.zcash-params:ro" \
        "$YCASH_IMAGE" ./ycashd -printtoconsole >/dev/null

    echo "⏳ Waiting for Ycash RPC to be ready..."
    local attempt=1
    while ! "$REPO_ROOT/scripts/run_ycash_regtest_local.sh" cli getinfo >/dev/null 2>&1; do
        if [ $attempt -gt 30 ]; then
            echo "❌ Error: Ycash RPC did not become ready in time."
            exit 1
        fi
        sleep 2
        attempt=$((attempt + 1))
    done

    # If this is a fresh chain (height 0), mine 2 blocks to activate Overwinter/Sapling
    local current_height
    current_height=$("$REPO_ROOT/scripts/run_ycash_regtest_local.sh" cli getblockcount 2>/dev/null || echo "0")
    if [ "$current_height" -eq 0 ]; then
        echo "⛏️  Fresh chain detected. Mining 2 blocks to activate Sapling..."
        "$REPO_ROOT/scripts/run_ycash_regtest_local.sh" cli generate 2 >/dev/null
    fi

    echo "🚀 Starting lightwalletd ($LWD_CONTAINER) on gRPC port $YCASH_LWD_PORT..."
    docker run -d \
        --name "$LWD_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -v "$NODE_DATA_DIR:/root/.ycash:ro" \
        "$LWD_IMAGE" \
        -conf-file /root/.ycash/ycash.conf \
        -bind-addr "0.0.0.0:$YCASH_LWD_PORT" \
        -log-file /dev/stdout \
        -no-tls >/dev/null

    echo "⛏️  Starting heartbeat miner ($MINER_CONTAINER, 1 block / ${YCASH_MINE_INTERVAL}s)..."
    docker run -d \
        --name "$MINER_CONTAINER" \
        --network host \
        --restart unless-stopped \
        -e RPC_USER="$YCASH_RPC_USER" \
        -e RPC_PASSWORD="$YCASH_RPC_PASSWORD" \
        -e RPC_PORT="$YCASH_RPC_PORT" \
        -e MINE_INTERVAL="$YCASH_MINE_INTERVAL" \
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
    echo "✅ Ycash regtest stack is up!"
    echo "   • RPC:          http://127.0.0.1:$YCASH_RPC_PORT (user: $YCASH_RPC_USER)"
    echo "   • lightwalletd: 127.0.0.1:$YCASH_LWD_PORT (gRPC, no TLS)"
    echo "   • Data dir:     $YCASH_DATA_DIR"
    echo ""
    if is_truthy "$YCASH_LWD_EXPOSE" || is_truthy "$YCASH_RPC_EXPOSE"; then
        echo ""
        expose_firewall
    else
        echo ""
        echo "   ℹ️  Ports are currently only reachable from this machine if your"
        echo "      firewall blocks them. To open them to your LAN / internet:"
        echo "        scripts/run_ycash_regtest_local.sh expose"
    fi

    echo ""
    echo "   Useful commands:"
    echo "     scripts/run_ycash_regtest_local.sh status"
    echo "     scripts/run_ycash_regtest_local.sh logs"
    echo "     scripts/run_ycash_regtest_local.sh cli getinfo"
    echo "     scripts/run_ycash_regtest_local.sh expose"
    echo "     scripts/run_ycash_regtest_local.sh down"
}

stop_stack() {
    echo "🛑 Stopping Ycash regtest stack..."
    remove_container_if_exists "$MINER_CONTAINER"
    remove_container_if_exists "$LWD_CONTAINER"
    remove_container_if_exists "$NODE_CONTAINER"
    if is_truthy "$YCASH_LWD_EXPOSE" || is_truthy "$YCASH_RPC_EXPOSE"; then
        unexpose_firewall
    fi
    echo "✅ Stopped. (Blockchain data & params kept in $YCASH_DATA_DIR)"
}

show_status() {
    docker ps -a --filter "name=$NODE_CONTAINER" --filter "name=$LWD_CONTAINER" --filter "name=$MINER_CONTAINER" \
        --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
}

follow_logs() {
    docker logs -f "$NODE_CONTAINER"
}

run_cli() {
    docker exec "$NODE_CONTAINER" ./ycash-cli -conf=/root/.ycash/ycash.conf "$@"
}

# --- Main ---
COMMAND="${1:-up}"
case "$COMMAND" in
    up)
        require_docker
        build_images
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
