package com.allcenter.modulesystem.exception;

import org.springframework.http.HttpStatus;

public class ConflictException extends ApiException {
    public ConflictException(String message) {
        super(HttpStatus.CONFLICT, "CONFLICT", message);
    }
}
