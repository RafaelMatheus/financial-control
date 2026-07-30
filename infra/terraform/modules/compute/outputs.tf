output "instance_id" {
  value = aws_instance.app.id
}

output "public_ip" {
  value = aws_eip.app.public_ip
}

output "availability_zone" {
  value = aws_instance.app.availability_zone
}
