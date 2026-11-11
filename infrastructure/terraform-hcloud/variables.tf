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
  default = "cx23" # 2 vCPU, 4GB RAM
}

variable "db_server_type" {
  default = "cx23" # 2 vCPU, 4GB RAM
}

variable "wallet_server_type" {
  default = "cx23" # 2 vCPU, 4GB RAM
#  default = "cpx31" # 4 vCPU, 8GB RAM (For PirateChain node)
}

variable "ssh_key_name" {
  description = "Name of the SSH key already uploaded to Hetzner Cloud"
}

variable "management_ips" {
  type        = list(string)
  default     = []
  description = "List of external IP addresses allowed to access management ports (e.g. MongoDB, SSH if restricted)"
}

variable "domain" {
  type        = string
  default     = ""
  description = "Production domain name for SSL termination (e.g., api.gimlee.com). Used when app_node_count > 1."
}
