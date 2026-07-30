# Volume EBS DECLARADO SEPARADAMENTE da instancia (RF-50).
#
# Esta e a mitigacao mais importante do risco R-05: como o volume nao faz parte
# do recurso aws_instance, qualquer mudanca que force recriacao da EC2 — troca de
# AMI, de tipo, de user-data — desanexa e reanexa o volume em vez de destrui-lo
# com os dados dentro.
#
# ATENCAO: nao ha backup (decisao D-36). Este volume e a unica copia dos dados.

resource "aws_ebs_volume" "data" {
  availability_zone = var.availability_zone
  size              = var.size_gb
  type              = "gp3"
  encrypted         = true

  tags = { Name = "${var.project_name}-data" }

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_volume_attachment" "data" {
  device_name = var.device_name
  volume_id   = aws_ebs_volume.data.id
  instance_id = var.instance_id

  # Nao forcar detach: evita corromper o filesystem se o volume estiver montado.
  force_detach = false
}
