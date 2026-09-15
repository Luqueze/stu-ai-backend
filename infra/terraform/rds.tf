data "aws_vpc" "default" {
  default = true
}

resource "aws_security_group" "rds" {
  name        = "ai-exam-rds-sg"
  description = "Allow Postgres access to the ai-exam RDS instance from a trusted IP only"
  vpc_id      = data.aws_vpc.default.id

  ingress {
    description = "Postgres from trusted IP"
    from_port   = 5432
    to_port     = 5432
    protocol    = "tcp"
    cidr_blocks = [var.my_ip_cidr]
  }

  ingress {
    description     = "Postgres from app EC2"
    from_port       = 5432
    to_port         = 5432
    protocol        = "tcp"
    security_groups = [aws_security_group.app.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Project = "ai-exam"
  }
}

resource "aws_db_instance" "ai_exam" {
  identifier     = "ai-exam-db"
  engine         = "postgres"
  engine_version = "16"

  instance_class    = var.db_instance_class
  allocated_storage = var.db_allocated_storage
  storage_type      = "gp3"

  db_name  = "postgres"
  username = var.db_username
  password = var.db_password

  publicly_accessible    = true
  vpc_security_group_ids = [aws_security_group.rds.id]

  # Learning setup: kept cheap and disposable, not production-grade.
  multi_az                = false
  backup_retention_period = 0
  skip_final_snapshot     = true
  deletion_protection     = false
  apply_immediately       = true

  tags = {
    Project = "ai-exam"
  }
}
