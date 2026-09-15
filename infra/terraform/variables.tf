variable "aws_region" {
  description = "AWS region where the RDS instance is created"
  type        = string
  default     = "us-east-1"
}

variable "my_ip_cidr" {
  description = "Your public IP in CIDR form (e.g. 203.0.113.7/32), allowed to reach RDS on port 5432. Find yours with `curl ifconfig.me`."
  type        = string
}

variable "db_username" {
  description = "Master username for the RDS instance"
  type        = string
  default     = "postgres"
}

variable "db_password" {
  description = "Master password for the RDS instance. Pass via TF_VAR_db_password env var, never commit it."
  type        = string
  sensitive   = true
}

variable "db_instance_class" {
  description = "RDS instance class (db.t3.micro is Free Tier eligible for 12 months on new accounts)"
  type        = string
  default     = "db.t3.micro"
}

variable "db_allocated_storage" {
  description = "Allocated storage in GB (20GB gp3 is Free Tier eligible)"
  type        = number
  default     = 20
}

variable "ec2_instance_type" {
  description = "EC2 instance type running docker-compose (t3.micro is Free Tier eligible)"
  type        = string
  default     = "t3.micro"
}

variable "ssh_public_key_path" {
  description = "Path to your SSH public key, used to access the app EC2 instance"
  type        = string
  default     = "~/.ssh/id_ed25519.pub"
}
