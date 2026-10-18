package com.gi3.matchingcv.event;

public record OffrePublieeEvent(
        Long offreId,
        Long recruteurId,
        String titre
) {}
