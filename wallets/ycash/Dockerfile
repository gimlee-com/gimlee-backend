FROM ubuntu:22.04

# Install utilities to download the wallet
RUN apt-get update && apt-get install -y \
    wget \
    ca-certificates \
    tar \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /usr/local/bin

# Download Ycash Binaries (Check https://github.com/ycashfoundation/ycash/releases for latest version)
ARG YCASH_URL=https://github.com/ycashfoundation/ycash/releases/download/v4.4.4/ycashd_v4.4.4_linux.tar.gz

RUN wget -O ycash.tar.gz "$YCASH_URL" \
    && tar -xzvf ycash.tar.gz --strip-components=1 ycashd_v4.4.4_linux/ycashd ycashd_v4.4.4_linux/ycash-cli ycashd_v4.4.4_linux/ycash-tx \
    && rm ycash.tar.gz

# Create directory for blockchain data
RUN mkdir -p /root/.ycash

# Expose RPC Port (Default is 19232 for Mainnet, 18232 for gimlee-internal-testnet)
EXPOSE 19232
EXPOSE 18232
