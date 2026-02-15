<div style="height: 50px;">
    <svg>
        <g>
            <text font-size="24" fill="#4A3022" font-family="FreeSans" font-weight="100" x="10" text-anchor="start" y="24">gimlee</text>
        </g>
    </svg>
</div>

# Gimlee: A Community-Driven Peer-to-Peer Cryptocurrency Marketplace

## Overview

Gimlee is envisioned as a decentralized online marketplace platform connecting buyers and sellers directly.
The platform facilitates the exchange of goods and services using cryptocurrency payments, eliminating the need
for traditional financial intermediaries. Its design aims to empower direct commerce and foster economic freedom
through secure, transparent, and peer-to-peer transactions.

## Core Concept: Trustless Payment Verification

Gimlee employs a non-custodial payment verification system to enable seamless and secure transactions. Sellers import
their cryptocurrency wallet's **viewing key** into the platform. This key grants read-only access to incoming
transaction data (*it does not grant spending capabilities or visibility of outgoing transactions*).

This mechanism facilitates:
1. Continuous monitoring of the designated blockchain for incoming transactions to the seller's registered address.
2. Automated confirmation upon successful receipt of order payments.

These capabilities are beneficial in many ways, including:
1. Enhanced User Experience: The platform delivers comprehensive purchase status visibility throughout the entire
   purchasing process, from the point of purchase initiation to the successful delivery of the item to the buyer.
2. Integrity of Reputation System: Buyer feedback, submitted post-transaction, combined with the system's verification
   of legitimate payment, contributes to more authentic and reliable seller ratings. This, in turn, underpins
   the development of a robust reputation system by mitigating manipulative or fraudulent activities.

Payment verification occurs directly on the blockchain, ensuring transparency and security **without the platform ever
taking custody of user funds**. This approach is compatible with privacy-focused cryptocurrencies like Monero (XMR),
PirateChain (ARRR), YCash (YEC), and Firo (FIRO).

## Project Status & Roadmap

**Gimlee is currently in the early stages of active development.**

*   **Stage 1 (In Progress):** Integration with **PirateChain (ARRR)** and **YCash (YEC)** blockchains. These privacy-focused cryptocurrencies 
    were selected for the initial implementation due to their fast transaction confirmation times and advanced privacy features.
*   **Stage 2 (Planned):** Implementation of decentralized storage for core data (e.g., users, listings). 
    Potential solutions under consideration include **Dash Platform Drive**. This aims to enable the creation 
    of independent Gimlee instances (clones) that can synchronize the full dataset or specific subsets
    (e.g., regional data) directly from the decentralized storage layer.

### Potential Future Directions

*   **Standalone Instance Mode:** Explore providing an option (e.g., a simple Docker image) to run a local Gimlee
    instance without decentralized data synchronization, suitable for operating an independent, self-hosted store.
*   **Expanded Cryptocurrency Support:** Integrate support for Monero (XMR), Firo (FIRO), and potentially
    other cryptocurrencies based on community interest and feasibility.
*   **Modular Crypto Clients:** Once the blockchain client integrations (starting with PirateChain) are robust and
    well-tested, consider extracting them into standalone libraries/repositories for broader community use.
*   **Marketplace Feature Expansion:** Continuously add and refine marketplace features based on community
    contributions and identified needs.

**(Community contributions in all areas are welcome!)**

## Local Development Setup

This section guides developers looking to contribute or run a local instance for testing.

### Prerequisites

*   **Java Development Kit (JDK):** Version 21 or later.
*   **MongoDB:** Version 8.0 or later. The provided Docker configuration simplifies local setup:
    `docker-compose -f docker/mongo/docker-compose.yml up`. Authentication is enabled by default with `admin` as the username and `password` as the password.
*   **Flyway CLI:** For database migrations, ensure the Flyway CLI is installed and available in your PATH.
*   **PirateChain Full Node:** A running [PirateChain](https://piratechain.com) full node wallet is required for 
    transaction verification during development. (*Note: By default, this node will connect to the PirateChain mainnet.*)
*   **YCash Full Node:** A running [YCash](https://ycash.xyz) full node wallet is required for 
    transaction verification during development. (*Note: By default, this node will connect to the YCash mainnet.*)
*   **SMTP Server:** A configured SMTP server is necessary for the application to send emails (e.g., notifications,
    confirmations).
*   **Open Exchange Rates API Account:** An API key from [Open Exchange Rates](https://openexchangerates.org/)
    is required for fiat-to-crypto currency conversions.

### Configuration

Before running the application for the first time:

1.  Navigate to the `gimlee-api/src/main/resources/` directory.
2.  Create a copy of `application-local-EXAMPLE.yaml`.
3.  Rename the copy to `application-local.yaml`.
4.  Edit `application-local.yaml` and replace all `<fillme>` placeholders with the appropriate configuration values:
    *   **PirateChain RPC:** The `rpc-url`, `user`, and `password` values for the `gimlee.payments.piratechain` section
        can typically be found in the PirateChain full node's configuration file (usually located
        at `~/.komodo/PIRATE/PIRATE.conf`).
    *   **YCash RPC:** The `rpc-url`, `user`, and `password` values for the `gimlee.payments.ycash` section
        can typically be found in the YCash full node's configuration file (usually located
        at `~/.ycash/ycash.conf`).
    *   **Media Storage:** The application supports two media storage backends: local filesystem and S3-compatible object storage. This is controlled by the `gimlee.media.store.storage-type` property.
        *   **For local storage (default):** Set `gimlee.media.store.storage-type` to `LOCAL` (or omit it). Then, configure `gimlee.media.store.local.directory` with the absolute path where files should be saved.
        *   **For S3-compatible storage:** Set `gimlee.media.store.storage-type` to `S3`. Then, configure the properties under `gimlee.media.store.s3`, including `endpoint`, `region`, `bucket`, `access-key`, and `secret-key`.
    *   **Email:** Configure the `spring.mail` section with valid SMTP server details.
    *   **Open Exchange Rates:** Set your API key for `gimlee.payments.exchange.open-exchange-rates.app-id`.
    *   **JWT Key:** Set a secure secret key for `gimlee.auth.rest.jwt.key`.

### Running the Application

Once the prerequisites and configuration are complete, run the following command from the project's root directory:

```bash
./gradlew :gimlee-api:bootRun --args='--spring.profiles.active=local'
```

This will start the Gimlee API application using the `local` Spring profile, loading settings from
`application-local.yaml`.

### API Usage & Testing

Example API requests can be found in the `.http` files located within:
*   `gimlee-api/docs/http/`
*   `gimlee-payments/docs/http/`

These files (compatible with IDEs like IntelliJ IDEA's HTTP Client) provide examples for various endpoints.
The `playground.http` file in `gimlee-api/docs/http/` contains requests useful for populating the local development
environment with sample data.

### MongoDB indexes
If any of the project modules requires any MongoDB indexes, you will find a flyway.conf file in the module's
root directory. This file contains the configuration for the Flyway CLI to apply the necessary indexes.

As a prerequisite, ensure that the Flyway CLI is installed and available in your PATH.
You can find a helper [Flyway CLI installation shell script](scripts/install_flyway.sh) in the `scripts` directory.

Once the flyway CLI is set up, run the following commands:
```
flyway migrate -configFiles=gimlee-ads/flyway.conf -baselineOnMigrate=true -url="jdbc:mongodb://admin:password@localhost:27017/gimlee?authSource=admin"
```

## Infrastructure & Deployment

Gimlee is designed for easy deployment and scaling using Infrastructure as Code (Terraform) and Configuration Management (Ansible).

Detailed instructions can be found in the `infrastructure/` directory:

1.  **Provisioning**: Choose your cloud provider:
    *   [DigitalOcean Setup](infrastructure/terraform-digitalocean/README.md)
    *   [Hetzner Cloud Setup](infrastructure/terraform-hcloud/README.md)
2.  **Configuration & Deployment**: Use Ansible to set up the nodes and deploy services.
    *   [Ansible Playbooks](infrastructure/ansible/README.md)