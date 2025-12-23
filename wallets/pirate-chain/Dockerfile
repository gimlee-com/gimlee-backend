FROM ubuntu:22.04

# Install utilities to download the wallet
RUN apt-get update && apt-get install -y \
    wget \
    ca-certificates \
    unzip \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/local/bin

# Download Pirate Chain Binaries (Check https://github.com/PirateNetwork/pirate/releases for latest version)
ARG PIRATE_URL=https://github.com/PirateNetwork/pirate/releases/download/v5.9.0/pirate-cli-ubuntu2004-v5.9.0.zip

RUN wget -O pirate.zip "$PIRATE_URL" \
    && unzip -j pirate.zip \
    && rm pirate.zip

# Create directory for blockchain data
RUN mkdir -p /root/.komodo/PIRATE

# Expose RPC Port (Default is usually 45453 for Mainnet)
EXPOSE 45453