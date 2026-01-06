data "hcloud_ssh_key" "main" {
  name = var.ssh_key_name
}

# Private Network (VPC equivalent)
resource "hcloud_network" "gimlee_net" {
  name     = "gimlee-network"
  ip_range = "10.0.0.0/16"
}

resource "hcloud_network_subnet" "gimlee_subnet" {
  network_id   = hcloud_network.gimlee_net.id
  type         = "cloud"
  network_zone = var.network_zone
  ip_range     = "10.0.1.0/24"
}

# Application Servers
resource "hcloud_server" "app" {
  count       = var.app_node_count
  name        = "gimlee-app-${count.index + 1}"
  image       = "ubuntu-22.04"
  server_type = var.app_server_type
  location    = var.location
  ssh_keys    = [data.hcloud_ssh_key.main.id]
  labels = {
    project = "gimlee"
    role    = "app"
  }
}

resource "hcloud_server_network" "app" {
  count     = var.app_node_count
  server_id = hcloud_server.app[count.index].id
  network_id = hcloud_network.gimlee_net.id
}

# Database Server
resource "hcloud_server" "db" {
  name        = "gimlee-db"
  image       = "ubuntu-22.04"
  server_type = var.db_server_type
  location    = var.location
  ssh_keys    = [data.hcloud_ssh_key.main.id]
  labels = {
    project = "gimlee"
    role    = "db"
  }
}

resource "hcloud_server_network" "db" {
  server_id = hcloud_server.db.id
  network_id = hcloud_network.gimlee_net.id
}

# Wallet Server
resource "hcloud_server" "wallet" {
  name        = "gimlee-wallet"
  image       = "ubuntu-22.04"
  server_type = var.wallet_server_type
  location    = var.location
  ssh_keys    = [data.hcloud_ssh_key.main.id]
  labels = {
    project = "gimlee"
    role    = "wallet"
  }
}

resource "hcloud_server_network" "wallet" {
  server_id = hcloud_server.wallet.id
  network_id = hcloud_network.gimlee_net.id
}

# Load Balancer
resource "hcloud_load_balancer" "public" {
  name               = "gimlee-lb"
  load_balancer_type = "lb11"
  location           = var.location
}

resource "hcloud_load_balancer_network" "public" {
  load_balancer_id = hcloud_load_balancer.public.id
  network_id       = hcloud_network.gimlee_net.id
}

resource "hcloud_load_balancer_target" "app_target" {
  type             = "label_selector"
  load_balancer_id = hcloud_load_balancer.public.id
  label_selector   = "role=app"
  use_private_ip   = true
  depends_on       = [hcloud_load_balancer_network.public]
}

resource "hcloud_load_balancer_service" "http" {
  load_balancer_id = hcloud_load_balancer.public.id
  protocol         = "http"
  listen_port      = 80
  destination_port = 80
  health_check {
    protocol = "http"
    port     = 80
    interval = 10
    timeout  = 5
    retries  = 3
    http {
      path = "/health"
    }
  }
}

resource "hcloud_load_balancer_service" "https" {
  load_balancer_id = hcloud_load_balancer.public.id
  protocol         = "tcp" # Passthrough to Traefik
  listen_port      = 443
  destination_port = 443
}

# Firewalls
resource "hcloud_firewall" "app" {
  name = "gimlee-app-firewall"
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "22"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "80"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "443"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
  # --- Allow Monitoring from Wallet Node ---
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "9100" # Node Exporter
    source_ips = ["${hcloud_server.wallet.ipv4_address}/32"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "12060" # gimlee-api Spring Boot Actuator
    source_ips = ["${hcloud_server.wallet.ipv4_address}/32"]
  }
  # ---------------------------------------------------
  apply_to {
    label_selector = "role=app"
  }
}

resource "hcloud_firewall" "db" {
  name = "gimlee-db-firewall"
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "22"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "27017"
    source_ips = [for s in hcloud_server.app : "${s.ipv4_address}/32"]
  }
  # --- Allow Monitoring from Wallet Node ---
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "9100" # Node Exporter
    source_ips = ["${hcloud_server.wallet.ipv4_address}/32"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "9216" # MongoDB Exporter
    source_ips = ["${hcloud_server.wallet.ipv4_address}/32"]
  }
  # ---------------------------------------------------
  apply_to {
    label_selector = "role=db"
  }
}

resource "hcloud_firewall" "wallet" {
  name = "gimlee-wallet-firewall"
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "22"
    source_ips = ["0.0.0.0/0", "::/0"]
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "45453"
    source_ips = [for s in hcloud_server.app : "${s.ipv4_address}/32"] # Only allow App nodes
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "19232"
    source_ips = [for s in hcloud_server.app : "${s.ipv4_address}/32"] # Ycash RPC (Mainnet)
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "18232"
    source_ips = [for s in hcloud_server.app : "${s.ipv4_address}/32"] # Ycash RPC (gimlee-internal-testnet)
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "9067"
    source_ips = ["0.0.0.0/0", "::/0"] # Ycash Lightwalletd (Mainnet)
  }
  rule {
    direction = "in"
    protocol  = "tcp"
    port      = "19067"
    source_ips = ["0.0.0.0/0", "::/0"] # Ycash Lightwalletd (gimlee-internal-testnet)
  }
  apply_to {
    label_selector = "role=wallet"
  }
}
