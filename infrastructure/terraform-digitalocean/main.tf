data "digitalocean_ssh_key" "main" {
  name = var.ssh_key_name
}

resource "digitalocean_vpc" "gimlee_vpc" {
  name   = "gimlee-vpc"
  region = var.region
}

# Application Droplets
resource "digitalocean_droplet" "app" {
  count    = var.app_node_count
  name     = "gimlee-app-${count.index + 1}"
  region   = var.region
  size     = var.app_size
  image    = "ubuntu-22-04-x64"
  vpc_uuid = digitalocean_vpc.gimlee_vpc.id
  ssh_keys = [data.digitalocean_ssh_key.main.id]
  tags     = ["gimlee", "app"]
}

# Database Droplet
resource "digitalocean_droplet" "db" {
  name     = "gimlee-db"
  region   = var.region
  size     = var.db_size
  image    = "ubuntu-22-04-x64"
  vpc_uuid = digitalocean_vpc.gimlee_vpc.id
  ssh_keys = [data.digitalocean_ssh_key.main.id]
  tags     = ["gimlee", "db"]
}

# Wallet Droplet
resource "digitalocean_droplet" "wallet" {
  name     = "gimlee-wallet"
  region   = var.region
  size     = var.wallet_size
  image    = "ubuntu-22-04-x64"
  vpc_uuid = digitalocean_vpc.gimlee_vpc.id
  ssh_keys = [data.digitalocean_ssh_key.main.id]
  tags     = ["gimlee", "wallet"]
}

# Load Balancer
resource "digitalocean_loadbalancer" "public" {
  name   = "gimlee-lb"
  region = var.region

  forwarding_rule {
    entry_port     = 80
    entry_protocol = "http"

    target_port     = 80
    target_protocol = "http"
  }

  forwarding_rule {
    entry_port     = 443
    entry_protocol = "https"

    target_port     = 443
    target_protocol = "https"
    
    # In a real scenario, you'd manage SSL here or use Traefik on the droplets.
    # For Traefik on droplets, we can use TCP pass-through or just use a Floating IP for one node.
    # To keep it simple and powerful (Traefik), we'll let Traefik handle SSL.
    # So we forward 443 as TCP.
    # Wait, DO LB doesn't support ACME easily if we forward as TCP without certs.
    # Let's use DO LB for 80/443 and forward to Traefik on port 80/443.
  }

  healthcheck {
    port     = 80
    protocol = "http"
    path     = "/health" # We'll need to configure this in Traefik or the app
  }

  droplet_ids = digitalocean_droplet.app.*.id
  vpc_uuid    = digitalocean_vpc.gimlee_vpc.id
}

# Firewall for App Nodes
resource "digitalocean_firewall" "app" {
  name = "gimlee-app-firewall"

  droplet_ids = digitalocean_droplet.app.*.id

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "80"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "443"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

# Firewall for DB Node (only allow access from VPC)
resource "digitalocean_firewall" "db" {
  name = "gimlee-db-firewall"

  droplet_ids = [digitalocean_droplet.db.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "27017"
    source_tags      = ["gimlee"]
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
  }

  # --- Allow Monitoring from Wallet Node ---
  inbound_rule {
    protocol           = "tcp"
    port_range         = "9100" # Node Exporter
    source_droplet_ids = [digitalocean_droplet.wallet.id]
  }

  inbound_rule {
    protocol           = "tcp"
    port_range         = "9216" # MongoDB Exporter
    source_droplet_ids = [digitalocean_droplet.wallet.id]
  }
  # -----------------------------------------

  outbound_rule {
    protocol              = "tcp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}

# Firewall for Wallet Node
resource "digitalocean_firewall" "wallet" {
  name = "gimlee-wallet-firewall"

  droplet_ids = [digitalocean_droplet.wallet.id]

  inbound_rule {
    protocol         = "tcp"
    port_range       = "22"
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "45453"
    source_tags      = ["app"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "19232" # Ycash RPC (Mainnet)
    source_tags      = ["app"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "18232" # Ycash RPC (gimlee-internal-testnet)
    source_tags      = ["app"]
  }


  inbound_rule {
    protocol         = "tcp"
    port_range       = "9067" # Ycash Lightwalletd (Mainnet)
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "19067" # Ycash Lightwalletd (gimlee-internal-testnet)
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "18232" # ycashd (gimlee-internal-testnet)
    source_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "tcp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }

  outbound_rule {
    protocol              = "udp"
    port_range            = "all"
    destination_addresses = ["0.0.0.0/0", "::/0"]
  }
}
