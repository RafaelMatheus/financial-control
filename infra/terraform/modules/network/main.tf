# VPC propria (D-34), sem NAT Gateway.
#
# Subnet publica: EC2 com a aplicacao e o nginx.
# Subnets privadas: banco RDS. Precisam de DUAS AZs — exigencia do
# db_subnet_group, mesmo em deploy single-AZ.
#
# Sem NAT: o banco nao precisa de saida para a internet, e a EC2 sai pelo IGW.

resource "aws_vpc" "main" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = { Name = "${var.project_name}-vpc" }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_name}-igw" }
}

data "aws_availability_zones" "available" {
  state = "available"
}

# ------------------------------------------------------------ publica
resource "aws_subnet" "public" {
  vpc_id                  = aws_vpc.main.id
  cidr_block              = var.public_subnet_cidr
  availability_zone       = data.aws_availability_zones.available.names[0]
  map_public_ip_on_launch = true

  tags = { Name = "${var.project_name}-subnet-public" }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.main.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.main.id
  }

  tags = { Name = "${var.project_name}-rt-public" }
}

resource "aws_route_table_association" "public" {
  subnet_id      = aws_subnet.public.id
  route_table_id = aws_route_table.public.id
}

# ------------------------------------------------------------ privadas
# Duas AZs: o db_subnet_group exige, ainda que o banco seja single-AZ.
resource "aws_subnet" "private" {
  count = 2

  vpc_id            = aws_vpc.main.id
  cidr_block        = var.private_subnet_cidrs[count.index]
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = { Name = "${var.project_name}-subnet-private-${count.index + 1}" }
}

# Route table sem rota default: sem saida para a internet, sem NAT.
resource "aws_route_table" "private" {
  vpc_id = aws_vpc.main.id

  tags = { Name = "${var.project_name}-rt-private" }
}

# Rota default condicional, so quando o banco e exposto.
#
# publicly_accessible = true no RDS nao basta: a AWS atribui o IP publico, mas
# sem rota para o IGW na subnet da instancia o pacote de resposta nao sai, e a
# conexao morre em timeout — o mesmo sintoma de estar fechado, com a diferenca
# de que o banco ja estaria exposto. Um dos dois sem o outro e o pior estado
# possivel: sem acesso e sem isolamento.
#
# Com esta rota as subnets do banco deixam de ser privadas de fato. O que
# preserva o isolamento passa a ser exclusivamente o security group.
resource "aws_route" "private_internet" {
  count = var.enable_database_internet_route ? 1 : 0

  route_table_id         = aws_route_table.private.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.main.id
}

resource "aws_route_table_association" "private" {
  count = 2

  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private.id
}
