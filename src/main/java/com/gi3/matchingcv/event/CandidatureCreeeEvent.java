package com.gi3.matchingcv.event;

public record CandidatureCreeeEvent(
        Long candidatureId,
        Long etudiantId,
        Long offreId,
        Long recruteurId,
        Double score,
        String etudiantNom,
        String etudiantPrenom,
        String offreTitre
) {}
