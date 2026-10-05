package com.gi3.matchingcv.exception;

/**
 * Exception levée lorsqu'un compte avec cet e-mail existe déjà dans le système.
 */
public class EmailDejaUtiliseException extends RuntimeException {

    public EmailDejaUtiliseException(String message) {
        super(message);
    }
}
