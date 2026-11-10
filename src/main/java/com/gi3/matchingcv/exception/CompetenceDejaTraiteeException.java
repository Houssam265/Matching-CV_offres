package com.gi3.matchingcv.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class CompetenceDejaTraiteeException extends RuntimeException {

    public CompetenceDejaTraiteeException(String message) {
        super(message);
    }
}

