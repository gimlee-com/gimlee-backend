variable "location" {
  default     = "nbg1" # Nuremberg (Germany)
  description = "Hetzner Data Center location"
}

variable "network_zone" {
  default     = "eu-central"
  description = "Hetzner Network Zone"
}

variable "app_node_count" {
  default = 1
}

variable "app_server_type" {
  default = "cx23" # 2 vCPU, 2GB RAM
}

variable "db_server_type" {
  default = "cpx11" # 2 vCPU, 2GB RAM
}

variable "wallet_server_type" {
  default = "cpx31" # 4 vCPU, 8GB RAM (For PirateChain node)
}

variable "ssh_key_name" {
  description = "Name of the SSH key already uploaded to Hetzner Cloud"
}

variable "management_ips" {
  type        = list(string)
  default     = []
  description = "List of external IP addresses allowed to access management ports (e.g. MongoDB, SSH if restricted)"
}
