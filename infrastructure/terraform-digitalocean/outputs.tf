output "app_ips" {
  value = digitalocean_droplet.app.*.ipv4_address
}

output "db_ip" {
  value = digitalocean_droplet.db.ipv4_address
}

output "wallet_ip" {
  value = digitalocean_droplet.wallet.ipv4_address
}

output "lb_ip" {
  value = digitalocean_loadbalancer.public.ip
}

output "media_bucket_endpoint" {
  value = digitalocean_spaces_bucket.media.bucket_domain_name
}

output "media_bucket_access_key" {
  value     = digitalocean_spaces_bucket_key.media_keys.access_key
  sensitive = true
}

output "media_bucket_secret_key" {
  value     = digitalocean_spaces_bucket_key.media_keys.secret_key
  sensitive = true
}