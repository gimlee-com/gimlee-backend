variable "region" {
  default = "fra1" # Frankfurt
}

variable "app_node_count" {
  default = 2
}

variable "app_size" {
  default = "s-1vcpu-1gb"
}

variable "db_size" {
  default = "s-1vcpu-2gb"
}

variable "wallet_size" {
  default = "s-4vcpu-8gb" # Blockchain nodes need more resources
}

variable "ssh_key_name" {
  description = "Name of the SSH key in DigitalOcean"
}
