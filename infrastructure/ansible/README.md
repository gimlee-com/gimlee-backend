# Ansible Deployment for Gimlee

This directory contains Ansible playbooks to configure servers and deploy the Gimlee application and its dependencies.

## Prerequisites

1.  **Ansible**: Installed on your local machine.
2.  **Infrastructure**: Servers provisioned (e.g., via Terraform for [DigitalOcean](../terraform-digitalocean/README.md) or [Hetzner](../terraform-hcloud/README.md)).
3.  **SSH Access**: You must be able to SSH into the servers as `root` or a user with `sudo` privileges.

## 1. Inventory Configuration

Create an `inventory.ini` file in this directory (you can use `inventory.ini.example` as a template). Replace the placeholder IPs with the outputs from your Terraform deployment.

```ini
[app]
app1 ansible_host=1.2.3.4
app2 ansible_host=1.2.3.5

[db]
db1 ansible_host=1.2.3.6

[wallet]
wallet1 ansible_host=1.2.3.7
```

## 2. Playbooks Overview

- **`setup.yml`**: Installs Docker and basic dependencies on all nodes.
- **`deploy-services.yml`**: Deploys MongoDB (on `db` nodes) and the PirateChain Full Node (on `wallet` nodes).
- **`deploy-traefik.yml`**: Deploys the Traefik reverse proxy on `app` nodes to handle SSL (via Let's Encrypt) and load balancing.
- **`deploy-app.yml`**: Deploys the Gimlee API application on `app` nodes.

## 3. Deployment Steps

Run the playbooks from the `playbooks` directory.

**1. Basic Server Setup**  
This installs Docker, `pip`, and the required Python libraries on all nodes. Run it once.
```bash
ansible-playbook -i ../inventory.ini setup.yml
```

**2. Deploy Infrastructure Services**  
This deploys MongoDB on db nodes and the PirateChain Full Node on wallet nodes.
```bash
ansible-playbook -i ../inventory.ini deploy-services.yml \
  --extra-vars "piratechain_user=YOUR_RPC_USER" \
  --extra-vars "piratechain_password=YOUR_RPC_PASSWORD"
```

**3. Deploy Traefik Reverse Proxy**  
   This deploys the Traefik reverse proxy to handle SSL and route traffic. It requires your domain and an email for Let's Encrypt SSL certificate registration.
```bash
   export DOMAIN="test-api.yourdomain.com"
   export ACME_EMAIL="your-email@example.com"
   ansible-playbook -i ../inventory.ini deploy-traefik.yml
```

**4. Deploy the Gimlee API Application**  
This is the final step. The application requires several pieces of configuration, including secrets (like passwords and API keys) and details for connecting to the object storage.
For security, these are passed directly on the command line using Ansible's --extra-vars flag, rather than being stored in files.
```bash
ansible-playbook -i ../inventory.ini deploy-app.yml \
  --extra-vars "domain=test-api.gimlee.com" \
  --extra-vars "mail_host=YOUR_SMTP_HOST" \
  --extra-vars "mail_port=587" \
  --extra-vars "mail_username=some@username.com" \
  --extra-vars "mail_password=YOUR_SMTP_PASSWORD" \
  --extra-vars "jwt_key=YOUR_SUPER_SECRET_JWT_KEY" \
  --extra-vars "s3_endpoint=https://your-bucket.s3.provider.com" \
  --extra-vars "s3_region=your-region" \
  --extra-vars "s3_bucket_name=your-bucket-name" \
  --extra-vars "s3_access_key=YOUR_S3_ACCESS_KEY" \
  --extra-vars "s3_secret_key=YOUR_S3_SECRET_KEY" \
  --extra-vars "piratechain_user=YOUR_RPC_USER" \
  --extra-vars "piratechain_password=YOUR_RPC_PASSWORD"
```

**Where to get these values:**
* `mail_password` & `jwt_key`: These are secrets you should generate and manage.
* `s3_*` variables: These come from your object storage provider.
  * If using DigitalOcean, run `terraform output` in the `terraform-digitalocean` directory to get the keys and bucket name.
  * If using Hetzner, you will have these from the manual setup process.

Note: For a more advanced setup, these secrets should be encrypted using Ansible Vault.

## Horizontal Scaling

To add more application nodes:
1. Increase `app_node_count` in your Terraform `variables.tf`.
2. Run `terraform apply`.
3. Add the new IP(s) to the `[app]` group in `inventory.ini`.
4. Re-run the playbooks. Traefik will automatically detect and load balance to the new nodes.
