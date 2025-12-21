# Infrastructure as Code with Terraform (Hetzner Cloud)

This directory contains Terraform scripts to provision the infrastructure for the Gimlee project on Hetzner Cloud.

## Architecture Overview

The scripts provision:
- A **Private Network** (VPC equivalent) for secure internal networking.
- **Application Servers**: Multiple nodes for horizontal scaling (managed by `app_node_count`).
- **Database Server**: A dedicated node for MongoDB.
- **Wallet Server**: A dedicated node for the PirateChain Full Node.
- **Load Balancer**: A Hetzner Load Balancer to distribute traffic across application nodes.
- **Firewalls**: Configured to restrict access (e.g., MongoDB is only accessible by application nodes).

## Prerequisites

1.  **Hetzner Cloud Account**: You need an active account on Hetzner Cloud.
2.  **Terraform CLI**: Installed on your local machine.
3.  **SSH Key**: An SSH key must be uploaded to your Hetzner Cloud project (**Security** -> **SSH Keys**). Take note of the **Name** you gave it.

---

## 1. Getting a Hetzner Cloud Token

1.  Log in to the [Hetzner Cloud Console](https://console.hetzner.cloud/).
2.  Select your **Project** (or create a new one).
3.  In the left sidebar, click on **Security**.
4.  Go to the **API Tokens** tab and click **Generate API Token**.
5.  Give it a name (e.g., `gimlee-terraform`) and select **Read & Write** permissions.
6.  **Copy the token** immediately; it won't be shown again.

---

## 2. Using the Token

You can provide the token in several ways:

### Option A: Environment Variable (Recommended)
```bash
export TF_VAR_hcloud_token="your_hetzner_token_here"
```

### Option B: `terraform.tfvars` file
Create a file named `terraform.tfvars` in this directory:
```hcl
hcloud_token = "your_hetzner_token_here"
ssh_key_name = "your_ssh_key_name_on_hetzner"
```
*Note: `terraform.tfvars` is ignored by Git to prevent leaking secrets.*

---

## 3. Deployment Steps

Navigate to this directory and run:

1.  **Initialize**:
    ```bash
    terraform init
    ```

2.  **Plan**: Preview changes.
    ```bash
    terraform plan -var="ssh_key_name=your_key_name"
    ```

3.  **Apply**: Provision the infrastructure.
    ```bash
    terraform apply -var="ssh_key_name=your_key_name"
    ```
    *(Type `yes` when prompted)*

---

## 4. Outputs

Once finished, Terraform will output the IP addresses:
- `lb_ip`: The public IP for your application (point your domain here).
- `app_ips`: IPv4 addresses of the app nodes.
- `db_ip`: IP of the database node.
- `wallet_ip`: IP of the PirateChain node.

Copy these IPs to your **Ansible inventory** (`infrastructure/ansible/inventory.ini`) to proceed with software deployment.

## Troubleshooting

- **SSH Key Error**: Ensure `ssh_key_name` matches the exact name in the Hetzner Cloud Console.
- **Permissions**: Ensure your API token has "Read & Write" access.
- **Location**: Default is `nbg1` (Nuremberg). Change it in `variables.tf` or via `-var="location=..."`.
