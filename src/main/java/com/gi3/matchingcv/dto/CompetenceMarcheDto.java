package com.gi3.matchingcv.dto;

public record CompetenceMarcheDto(
        String nom,
        long demandees, // Dans les offres
        long possedees, // Par les étudiants
        long ecart      // demandees - possedees
) {
}
