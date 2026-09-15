output "rds_endpoint" {
  description = "RDS connection endpoint (host:port)"
  value       = aws_db_instance.ai_exam.endpoint
}

output "rds_address" {
  description = "RDS host only, for DB_HOST"
  value       = aws_db_instance.ai_exam.address
}

output "rds_port" {
  value = aws_db_instance.ai_exam.port
}

output "ec2_public_ip" {
  description = "Public IP of the app server (Elastic IP) - share http://<this>:8086 with friends"
  value       = aws_eip.app.public_ip
}
