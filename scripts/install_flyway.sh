#!/bin/bash

# A corrected script to install a specific version of the Flyway CLI on Linux.
# It keeps the application directory intact and uses a symbolic link.
#
# Usage: ./install_flyway.sh <version>
# Example: ./install_flyway.sh 10.12.0

set -e

# --- Configuration ---
# Ensure the version number is provided
if [ -z "$1" ];  then
  echo "Error: No version specified."
  echo "Usage: $0 <flyway_version>"
  echo "Example: $0 10.12.0"
  exit 1
fi

FLYWAY_VERSION=$1
# Directory to store different versions of flyway
INSTALL_PARENT_DIR="$HOME/flyway"
# The full path for the specific version being installed
INSTALL_DIR="$INSTALL_PARENT_DIR/flyway-$FLYWAY_VERSION"
# Standard directory for user binaries
BIN_DIR="$HOME/.local/bin"
DOWNLOAD_URL="https://download.red-gate.com/maven/release/com/redgate/flyway/flyway-commandline/${FLYWAY_VERSION}/flyway-commandline-${FLYWAY_VERSION}-linux-x64.tar.gz"
DOWNLOAD_FILE="/tmp/flyway-$FLYWAY_VERSION.tar.gz"

# --- Installation ---
echo "Installing Flyway CLI version ${FLYWAY_VERSION}..."

# Check if the version is already installed
if [ -d "$INSTALL_DIR" ]; then
  echo "Version ${FLYWAY_VERSION} is already installed at ${INSTALL_DIR}"
else
  # Create the installation directory and download
  mkdir -p "$INSTALL_DIR"
  echo "Downloading from ${DOWNLOAD_URL}..."
  wget -qO "$DOWNLOAD_FILE" "$DOWNLOAD_URL"

  # Extract the archive into the target directory
  echo "Extracting archive to ${INSTALL_DIR}..."
  tar -xzf "$DOWNLOAD_FILE" -C "$INSTALL_DIR" --strip-components=1

  # Clean up the downloaded archive
  echo "Cleaning up temporary files..."
  rm "$DOWNLOAD_FILE"
fi

# --- Symlinking ---
# Ensure the binary directory exists
mkdir -p "$BIN_DIR"

# Create a symbolic link to the flyway executable.
# The 'f' flag overwrites any existing symlink, making it easy to switch versions.
echo "Creating symbolic link in ${BIN_DIR}..."
ln -snf "$INSTALL_DIR/flyway" "$BIN_DIR/flyway"

# --- Final Instructions ---
echo ""
echo "✅ Flyway CLI version ${FLYWAY_VERSION} has been successfully installed."
echo ""
echo "The 'flyway' command is now linked from: ${BIN_DIR}/flyway"
echo ""
echo "IMPORTANT: Please ensure '${BIN_DIR}' is in your PATH."
echo "You can check by running: echo \$PATH"
echo ""
echo "If it's not, add the following line to your shell configuration file (e.g., ~/.bashrc, ~/.zshrc):"
echo "export PATH=\"\$PATH:${BIN_DIR}\""
echo ""
echo "After adding the line, restart your terminal or run 'source ~/.bashrc' (or your shell's equivalent)."
echo "You can then verify the installation by running: flyway --version"