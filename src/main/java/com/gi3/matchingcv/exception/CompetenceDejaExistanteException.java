package com.gi3.matchingcv.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception levée lorsqu'une compétence avec le même nom existe déjà dans le référentiel.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class CompetenceDejaExistanteException extends RuntimeException {

    public CompetenceDejaExistanteException(String message) {
        super(message);
    }
}
