output "app_ips" {
  value = hcloud_server.app.*.ipv4_address
}

output "db_ip" {
  value = hcloud_server.db.ipv4_address
}

output "wallet_ip" {
  value = hcloud_server.wallet.ipv4_address
}

output "lb_ip" {
  value = hcloud_load_balancer.public.ipv4
}
