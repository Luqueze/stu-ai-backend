package com.aiexam.authservice.exception;

public class EmailNotVerifiedException extends RuntimeException {

    public EmailNotVerifiedException(String email) {
        super("Email must be verified before accessing an existing account: " + email);
    }
}
