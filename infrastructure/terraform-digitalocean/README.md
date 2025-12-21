# Infrastructure as Code with Terraform (DigitalOcean)

This directory contains Terraform scripts to provision the infrastructure for the Gimlee project on DigitalOcean.

## Architecture Overview

The scripts provision:
- A dedicated **VPC** (Virtual Private Cloud) for secure internal networking.
- **Application Droplets**: Multiple nodes for horizontal scaling (managed by `app_node_count`).
- **Database Droplet**: A dedicated node for MongoDB.
- **Wallet Droplet**: A dedicated node for the PirateChain Full Node.
- **Load Balancer**: A DigitalOcean Load Balancer to distribute traffic across application nodes.
- **Firewalls**: Configured to restrict access (e.g., MongoDB is only accessible within the VPC).

## Prerequisites

1.  **DigitalOcean Account**: You need an active account on DigitalOcean.
2.  **Terraform CLI**: Installed on your local machine.
3.  **SSH Key**: An SSH key must be uploaded to your DigitalOcean account (**Settings** -> **Security** -> **SSH Keys**). Take note of the **Name** you gave it.

---

## 1. Getting a DigitalOcean Token

1.  Log in to your [DigitalOcean Control Panel](https://cloud.digitalocean.com/).
2.  Click on **API** in the left sidebar.
3.  In the **Tokens/Keys** tab, click **Generate New Token**.
4.  Name it (e.g., `gimlee-terraform`) and select **Full Access**.
5.  **Copy the token** immediately; it won't be shown again.

---

## 2. Using the Token

You can provide the token in several ways:

### Option A: Environment Variable (Recommended)
```bash
export TF_VAR_do_token="your_digitalocean_token_here"
```

### Option B: `terraform.tfvars` file
Create a file named `terraform.tfvars` in this directory:
```hcl
do_token     = "your_digitalocean_token_here"
ssh_key_name = "your_ssh_key_name_on_do"
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
- `app_ips`: Internal/External IPs of the app nodes.
- `db_ip`: IP of the database node.
- `wallet_ip`: IP of the PirateChain node.

Copy these IPs to your **Ansible inventory** (`infrastructure/ansible/inventory.ini`) to proceed with software deployment.

## Troubleshooting

- **SSH Key Error**: Ensure `ssh_key_name` matches the exact name in the DigitalOcean UI.
- **Permissions**: Ensure your API token has "Write" access.
- **Region**: Default is `fra1` (Frankfurt). Change it in `variables.tf` or via `-var="region=..."`.
