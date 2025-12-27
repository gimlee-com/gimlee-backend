# Gimlee Monitoring Stack

This directory contains the configuration for the monitoring infrastructure of the Gimlee project. We use a standard **Prometheus + Grafana** stack to monitor application performance, system resources, and database metrics.

## Architecture Overview

To optimize resource usage and security, the monitoring stack is deployed as follows:

*   **Host Node:** The `wallet` node (PirateChain) hosts the Prometheus and Grafana containers.
    *   *Reason:* The wallet node is disk/RAM intensive but has stable CPU usage, making it a good candidate to host the monitoring "brains" without impacting the high-throughput API nodes.
*   **Data Collection (Exporters):**
    *   **Node Exporter:** Runs on *every* server (App, DB, Wallet) to report CPU, RAM, and Disk usage.
    *   **MongoDB Exporter:** Runs on the `db` node to report internal MongoDB metrics.
    *   **Spring Boot Actuator:** Runs inside the `gimlee-api` container to report JVM and HTTP metrics.

## Prerequisites

Before deploying, ensure the infrastructure handles the traffic:

1.  **Application Config:** The Spring Boot API must expose endpoints.
    *   `management.endpoints.web.exposure.include="health,info,prometheus"`
    *   Port: `12060` (exposed via Docker `ports` mapping in Ansible).
2.  **Firewalls (Terraform):**
    *   The `wallet` node must be whitelisted to access ports `9100` (Node Exporter), `9216` (Mongo Exporter), and `12060` (API) on the App and DB nodes.
    *   *Note:* This is handled automatically in our Terraform `main.tf`.

## Configuration Files

*   **`prometheus.yml`**: Defines the "targets" (IP addresses and ports) Prometheus should scrape.
    *   *Note:* You must update the target IPs in this file to match your `inventory.ini` or Terraform outputs before deployment.
*   **`datasource.yml`**: Automatically configures Grafana to connect to the local Prometheus instance so you don't have to do it manually.

## Deployment

The deployment is handled via Ansible playbooks in `infrastructure/ansible/playbooks/`.

**1. Deploy Exporters (The "Ears")**
Installs Node Exporter and MongoDB Exporter on target machines.
```
bash ansible-playbook -i ../inventory.ini deploy-exporters.yml
```

**2. Deploy Monitoring Stack (The "Brains")**
Installs Prometheus and Grafana on the `wallet` node.

```
bash ansible-playbook -i ../inventory.ini deploy-monitoring.yml
```

## Accessing Grafana

For security reasons, **Grafana (port 3000) is NOT exposed to the public internet**. It is only accessible via SSH Tunneling.

**1. Create the Tunnel**
Run this command on your local machine:

```bash
Syntax: ssh -L 3000:localhost:3000 root@<WALLET_NODE_IP>
ssh -L 3000:localhost:3000 root@X.X.X.X
```

**2. Open in Browser**
Navigate to [http://localhost:3000](http://localhost:3000).

**3. Login**
*   **User:** `admin`
*   **Password:** Defined in `deploy-monitoring.yml` (default: `admin`, change immediately).

## Recommended Dashboards

We rely on industry-standard dashboards. To set them up:
1.  Go to **Dashboards** -> **New** -> **Import**.
2.  Enter the ID below and click **Load**.
3.  Select "Prometheus" as the data source.

| Component            | Dashboard Name     | Grafana ID |
|:---------------------|:-------------------|:-----------|
| **Spring Boot API**  | JVM (Micrometer)   | `4701`     |
| **System Resources** | Node Exporter Full | `1860`     |
| **Database**         | MongoDB by Percona | `20867`    |