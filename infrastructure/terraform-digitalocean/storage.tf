# Add this resource to generate a random string
resource "random_id" "bucket_suffix" {
  byte_length = 4
}

# A "Space" for storing user-uploaded media like images
resource "digitalocean_spaces_bucket" "media" {
  # Append the random suffix to the name for uniqueness
  name   = "gimlee-media-${random_id.bucket_suffix.hex}"
  region = var.region
  acl    = "public-read" # Makes uploaded files readable by anyone with the link
}

# Create API keys for the application to be able to upload files
resource "digitalocean_spaces_bucket_key" "media_keys" {
  bucket = digitalocean_spaces_bucket.media.name
}