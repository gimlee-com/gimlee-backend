#!/bin/bash

# Script to generate test wallet records (seed phrase, address, viewing keys)
# by calling a PirateChain or Ycash regtest node.

set -e

# Default values
COUNT=10
CURRENCY="pirate"
RPC_USER="gimlee"
RPC_PASS="gimlee"
RPC_HOST="127.0.0.1"
OUTPUT="wallets.csv"
MODE="independent"

show_help() {
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --count NUMBER    Number of records to generate (default: 10)"
    echo "  --currency TYPE   Currency: 'pirate' or 'ycash' (default: pirate)"
    echo "  --mode MODE       Mode: 'independent' (separate seeds) or 'shared' (same seed) (default: independent)"
    echo "  --host HOST       RPC host (default: 127.0.0.1)"
    echo "  --user USER       RPC username (default: gimlee)"
    echo "  --pass PASS       RPC password (default: gimlee)"
    echo "  --port PORT       RPC port (optional, defaults: pirate=45455, ycash=18232)"
    echo "  --output FILE     Output CSV file (default: wallets.csv)"
    echo "  --help            Show this help message"
}

# Parse arguments
while [[ "$#" -gt 0 ]]; do
    case $1 in
        --count) COUNT="$2"; shift ;;
        --currency) CURRENCY="$2"; shift ;;
        --mode) MODE="$2"; shift ;;
        --host) RPC_HOST="$2"; shift ;;
        --user) RPC_USER="$2"; shift ;;
        --pass) RPC_PASS="$2"; shift ;;
        --port) CUSTOM_PORT="$2"; shift ;;
        --output) OUTPUT="$2"; shift ;;
        --help) show_help; exit 0 ;;
        *) echo "Unknown parameter passed: $1"; show_help; exit 1 ;;
    esac
    shift
done

# Validate mode
if [[ "$MODE" != "independent" && "$MODE" != "shared" ]]; then
    echo "Error: Unsupported mode: $MODE. Use 'independent' or 'shared'."
    exit 1
fi

# Set port and container based on currency if not provided
if [ "$CURRENCY" == "pirate" ]; then
    RPC_PORT=${CUSTOM_PORT:-45455}
    CONTAINER="piratechain-regtest-local"
    DAEMON="/usr/local/bin/pirated"
    CLI="/usr/local/bin/pirate-cli"
    TEMP_RPC_PORT=55460
elif [ "$CURRENCY" == "ycash" ]; then
    RPC_PORT=${CUSTOM_PORT:-18232}
    CONTAINER="ycash-regtest-local"
    DAEMON="/usr/local/bin/ycashd"
    CLI="/usr/local/bin/ycash-cli"
    TEMP_RPC_PORT=28240
else
    echo "Error: Unsupported currency: $CURRENCY"
    exit 1
fi

RPC_URL="http://$RPC_HOST:$RPC_PORT"

call_rpc() {
    local method=$1
    local params=$2
    local url=${3:-$RPC_URL}
    local user=${4:-$RPC_USER}
    local pass=${5:-$RPC_PASS}

    local response=$(curl --silent --user "$user:$pass" \
         --data-binary "{\"jsonrpc\": \"1.0\", \"id\":\"gen-script\", \"method\": \"$method\", \"params\": $params }" \
         -H 'content-type: text/plain;' "$url")
    
    if [ -z "$response" ]; then
        return 1
    fi

    local error=$(echo "$response" | jq -r '.error')
    if [ "$error" != "null" ]; then
        return 1
    fi
    echo "$response" | jq -r '.result'
}

# CSV Header
echo "seed_phrase,address,viewing_key,ivk" > "$OUTPUT"

# Cleanup on exit
trap "docker exec $CONTAINER bash -c 'rm -rf /tmp/gimlee_gen_${CURRENCY}_*' > /dev/null 2>&1" EXIT

if [ "$MODE" == "shared" ]; then
    echo "Generating $COUNT records for $CURRENCY in SHARED mode (all sharing the same seed)..."
    
    # Verify connection
    if ! call_rpc "getblockchaininfo" "[]" > /dev/null; then
        echo "Error: Could not connect to $CURRENCY node at $RPC_URL. Is it running?"
        exit 1
    fi

    # Get seed phrase
    SEED_PHRASE=$(call_rpc "z_exportseedphrase" "[]" || echo "")
    if [ -z "$SEED_PHRASE" ]; then
        SEED_PHRASE="N/A (Not supported by node)"
    fi

    for ((i=1; i<=COUNT; i++)); do
        # Generate new address
        # For pirate, z_getnewaddresskey creates a new independent key within the wallet
        if [ "$CURRENCY" == "pirate" ]; then
            ADDR=$(call_rpc "z_getnewaddresskey" "[]" || call_rpc "z_getnewaddress" "[]")
        else
            ADDR=$(call_rpc "z_getnewaddress" "[]")
        fi
        
        # Export viewing key
        VIEW_KEY=$(call_rpc "z_exportviewingkey" "[\"$ADDR\"]")
        
        # Export IVK
        IVK="N/A"
        if [ "$CURRENCY" == "ycash" ]; then
            IVK=$(call_rpc "z_exportivk" "[\"$ADDR\"]" || echo "N/A")
        fi
        
        # Append to CSV
        echo "\"$SEED_PHRASE\",\"$ADDR\",\"$VIEW_KEY\",\"$IVK\"" >> "$OUTPUT"
        echo "Progress: $i/$COUNT"
    done
else
    echo "Generating $COUNT records for $CURRENCY in INDEPENDENT mode (each with its own seed)..."
    echo "Note: This uses temporary wallets in the Docker container and takes ~10s per record."

    for ((i=1; i<=COUNT; i++)); do
        # Use a unique port for each record to avoid TIME_WAIT issues
        CURRENT_PORT=$((TEMP_RPC_PORT + (i % 100)))
        TMP_DIR="/tmp/gimlee_gen_${CURRENCY}_${i}"
        TMP_URL="http://127.0.0.1:$CURRENT_PORT"
        
        # Create temp dir and config
        docker exec "$CONTAINER" mkdir -p "$TMP_DIR"
        if [ "$CURRENCY" == "ycash" ]; then
            docker exec "$CONTAINER" bash -c "echo 'exportdir=/tmp' > $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'rpcuser=gimlee' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'rpcpassword=gimlee' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'rpcport=$CURRENT_PORT' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'server=1' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'listen=0' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'regtest=1' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'rpcbind=0.0.0.0' >> $TMP_DIR/ycash.conf"
            docker exec "$CONTAINER" bash -c "echo 'rpcallowip=0.0.0.0/0' >> $TMP_DIR/ycash.conf"
        else
            docker exec "$CONTAINER" touch "$TMP_DIR/pirate.conf"
        fi

        # Start temp daemon
        docker exec -d "$CONTAINER" "$DAEMON" -regtest -datadir="$TMP_DIR" -rpcuser=gimlee -rpcpassword=gimlee -rpcport=$CURRENT_PORT -port=$((CURRENT_PORT + 1000)) -listen=0 -server=1
        
        # Wait for RPC
        READY=false
        for ((j=1; j<=30; j++)); do
            if docker exec "$CONTAINER" "$CLI" -regtest -datadir="$TMP_DIR" -rpcuser=gimlee -rpcpassword=gimlee -rpcport=$CURRENT_PORT getinfo > /dev/null 2>&1; then
                READY=true
                break
            fi
            sleep 1
        done

        if [ "$READY" == "false" ]; then
            echo "Error: Temp node failed to start for record $i (Port $CURRENT_PORT)"
            docker exec "$CONTAINER" rm -rf "$TMP_DIR"
            continue
        fi

        # Extract data
        ADDR=$(call_rpc "z_getnewaddress" "[]" "$TMP_URL" "gimlee" "gimlee")
        VIEW_KEY=$(call_rpc "z_exportviewingkey" "[\"$ADDR\"]" "$TMP_URL" "gimlee" "gimlee")
        
        SEED_PHRASE=""
        if [ "$CURRENCY" == "pirate" ]; then
            SEED_PHRASE=$(call_rpc "z_exportseedphrase" "[]" "$TMP_URL" "gimlee" "gimlee")
        elif [ "$CURRENCY" == "ycash" ]; then
            # For Ycash, extract HDSeed from wallet dump
            # Use a unique dump name to avoid conflicts (alphanumeric only)
            DUMP_NAME="dump${i}"
            docker exec "$CONTAINER" "$CLI" -regtest -datadir="$TMP_DIR" -rpcuser=gimlee -rpcpassword=gimlee -rpcport=$CURRENT_PORT z_exportwallet "$DUMP_NAME" > /dev/null
            SEED_PHRASE=$(docker exec "$CONTAINER" cat "/tmp/$DUMP_NAME" | grep "HDSeed=" | cut -d'=' -f2 | cut -d' ' -f1)
            SEED_PHRASE="HexSeed:$SEED_PHRASE"
            docker exec "$CONTAINER" rm "/tmp/$DUMP_NAME"
        fi

        IVK="N/A"
        if [ "$CURRENCY" == "ycash" ]; then
            IVK=$(call_rpc "z_exportivk" "[\"$ADDR\"]" "$TMP_URL" "gimlee" "gimlee" || echo "N/A")
        fi

        # Append to CSV
        echo "\"$SEED_PHRASE\",\"$ADDR\",\"$VIEW_KEY\",\"$IVK\"" >> "$OUTPUT"
        
        # Stop and clean up
        call_rpc "stop" "[]" "$TMP_URL" "gimlee" "gimlee" > /dev/null 2>&1 || true
        sleep 2
        docker exec "$CONTAINER" rm -rf "$TMP_DIR"
        
        echo "Progress: $i/$COUNT"
    done
fi

echo ""
echo "Done! $COUNT records saved to $OUTPUT"
