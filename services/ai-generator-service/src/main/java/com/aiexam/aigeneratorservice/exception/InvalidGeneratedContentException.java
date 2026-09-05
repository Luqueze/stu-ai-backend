package com.aiexam.aigeneratorservice.exception;

public class InvalidGeneratedContentException extends RuntimeException {

    public InvalidGeneratedContentException(String message) {
        super(message);
    }
}
