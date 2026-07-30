output "volume_id" {
  value = aws_ebs_volume.data.id
}

output "device_name" {
  value = var.device_name
}
