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
resource "digitalocean_certificate" "lb_cert" {
  count   = var.domain != "" ? 1 : 0
  name    = "gimlee-lb-cert"
  type    = "lets_encrypt"
  domains = [var.domain]
}

resource "digitalocean_loadbalancer" "public" {
  name   = "gimlee-lb"
  region = var.region

  redirect_http_to_https = var.domain != ""

  forwarding_rule {
    entry_port     = 80
    entry_protocol = "http"

    target_port     = 80
    target_protocol = "http"
  }

  dynamic "forwarding_rule" {
    for_each = var.domain != "" ? [1] : []
    content {
      entry_port       = 443
      entry_protocol   = "https"
      target_port      = 80
      target_protocol  = "http"
      certificate_name = digitalocean_certificate.lb_cert[0].name
    }
  }

  dynamic "forwarding_rule" {
    for_each = var.domain == "" ? [1] : []
    content {
      entry_port      = 443
      entry_protocol  = "tcp"
      target_port     = 443
      target_protocol = "tcp"
    }
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

  # --- Allow Redis (chat SSE event bus) from App Nodes ---
  inbound_rule {
    protocol         = "tcp"
    port_range       = "6379"
    source_tags      = ["app"]
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
  }

  inbound_rule {
    protocol           = "tcp"
    port_range         = "9121" # Redis Exporter
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
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "45454"
    source_tags      = ["app"]
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "19232" # Ycash RPC (Mainnet)
    source_tags      = ["app"]
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
  }

  inbound_rule {
    protocol         = "tcp"
    port_range       = "18232" # Ycash RPC (gimlee-internal-testnet)
    source_tags      = ["app"]
    source_addresses = [for ip in var.management_ips : "${ip}/32"]
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
