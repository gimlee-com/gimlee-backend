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
1. Enhanced User Experience: The platform delivers comprehensive order status visibility throughout the entire
   purchasing process, from the point of order initiation to the successful delivery of the item to the buyer.
2. Integrity of Reputation System: Buyer feedback, submitted post-transaction, combined with the system's verification
   of legitimate payment, contributes to more authentic and reliable seller ratings. This, in turn, underpins
   the development of a robust reputation system by mitigating manipulative or fraudulent activities.

Payment verification occurs directly on the blockchain, ensuring transparency and security **without the platform ever
taking custody of user funds**. This approach is compatible with privacy-focused cryptocurrencies like Monero (XMR),
PirateChain (ARRR), and Firo (FIRO).

## Project Status & Roadmap

**Gimlee is currently in the early stages of active development.**

*   **Stage 1 (In Progress):** Integration with the **PirateChain (ARRR)** blockchain. PirateChain was selected 
    for the initial implementation primarily due to its relatively fast transaction confirmation times, contributing to a better user experience.
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
    `docker-compose -f docker/mongo/docker-compose.yml up`.
*   **PirateChain Full Node:** A running [PirateChain](https://piratechain.com) full node wallet is required for 
    transaction verification during development. (*Note: By default, this node will connect to the PirateChain mainnet.*)
*   **SMTP Server:** A configured SMTP server is necessary for the application to send emails (e.g., notifications,
    confirmations).

### Configuration

Before running the application for the first time:

1.  Navigate to the `gimlee-api/src/main/resources/` directory.
2.  Create a copy of `application-local-EXAMPLE.yaml`.
3.  Rename the copy to `application-local.yaml`.
4.  Edit `application-local.yaml` and replace all `<fillme>` placeholders with the appropriate configuration values:
    *   **PirateChain RPC:** The `rpc-url`, `user`, and `password` values for the `gimlee.payments.piratechain` section
        can typically be found in the PirateChain full node's configuration file (usually located
        at `~/.komodo/PIRATE/PIRATE.conf`).
    *   **Media Storage:** Set `gimlee.media.store.directory` to the absolute path where uploaded media files should
        be stored. The application will attempt to create this directory if it doesn't exist (ensure the process has
        write permissions).
    *   **Email:** Configure the `spring.mail` section with valid SMTP server details.
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