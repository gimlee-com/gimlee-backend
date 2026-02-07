#!/bin/bash

# Script to run Flyway migrations locally for all modules.
# Prerequisites:
# 1. Flyway CLI installed and in PATH.
# 2. MongoDB JDBC Driver (DbSchema) in Flyway's drivers directory.
# 3. MongoDB running on localhost:27017.

set -e

# --- Configuration ---
# Default database name is 'gimlee'. You can override this if needed.
DB_NAME="gimlee"
DB_URL="jdbc:mongodb://localhost:27017/${DB_NAME}"
BASELINE_ON_MIGRATE=true
BASELINE_VERSION=0

# List of modules to run migrations for
MODULES=(
    "gimlee-ads"
    "gimlee-auth"
    "gimlee-chat"
    "gimlee-media-store"
    "gimlee-payments"
    "gimlee-purchases"
    "gimlee-user"
)

echo "🚀 Starting Flyway migrations for Gimlee modules..."

# Check if Flyway is installed
if ! command -v flyway &> /dev/null; then
    echo "❌ Error: 'flyway' command not found."
    echo "Please install Flyway CLI first. You can use 'scripts/install_flyway.sh <version>'."
    exit 1
fi

# Check if MongoDB is reachable
if ! nc -z localhost 27017 &> /dev/null; then
    echo "⚠️ Warning: MongoDB does not seem to be running on localhost:27017."
    echo "Attempting to start MongoDB via docker-compose..."
    if [ -f "docker/mongo/docker-compose.yml" ]; then
        docker compose -f docker/mongo/docker-compose.yml up -d
        echo "Waiting for MongoDB to be ready..."
        sleep 5
    else
        echo "❌ Error: docker/mongo/docker-compose.yml not found. Please start MongoDB manually."
        exit 1
    fi
fi

# Run migrations for each module
for MODULE in "${MODULES[@]}"; do
    echo ""
    echo "------------------------------------------------------------------------"
    echo "📦 Migrating module: $MODULE"
    echo "------------------------------------------------------------------------"
    
    CONFIG_FILE="$MODULE/flyway.conf"
    
    if [ ! -f "$CONFIG_FILE" ]; then
        echo "⚠️ Warning: Config file $CONFIG_FILE not found. Skipping."
        continue
    fi
    
    # We run from the module directory to ensure relative paths in flyway.conf work correctly.
    (
        cd "$MODULE"
        # We override the URL and baselineOnMigrate to ensure they are consistent for local run.
        # The -configFiles will load the rest of the settings (locations, table, etc.)
        flyway migrate \
            -url="$DB_URL" \
            -n \
            -baselineOnMigrate="$BASELINE_ON_MIGRATE" \
            -baselineVersion="$BASELINE_VERSION" \
            -configFiles="flyway.conf"
    )
done

echo ""
echo "✅ All migrations completed successfully!"
