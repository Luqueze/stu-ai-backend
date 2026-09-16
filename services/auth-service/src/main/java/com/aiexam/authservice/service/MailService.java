package com.aiexam.authservice.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {

    private final JavaMailSender mailSender;
    private final String fromAddress;

    public MailService(JavaMailSender mailSender, @Value("${app.mail.from}") String fromAddress) {
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(toEmail);
        message.setSubject("Redefinição de senha - STU");
        message.setText(
                "Recebemos uma solicitação para redefinir sua senha.\n\n"
                        + "Clique no link abaixo para escolher uma nova senha:\n"
                        + resetLink
                        + "\n\n"
                        + "Se você não solicitou isso, pode ignorar este email.");
        mailSender.send(message);
    }
}
