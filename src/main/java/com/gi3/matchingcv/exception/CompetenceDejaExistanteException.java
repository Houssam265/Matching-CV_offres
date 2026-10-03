package com.gi3.matchingcv.exception;

/**
 * Exception levée lorsqu'une compétence avec le même nom existe déjà dans le référentiel.
 */
public class CompetenceDejaExistanteException extends RuntimeException {

    public CompetenceDejaExistanteException(String message) {
        super(message);
    }
}
