package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.TypeConseilCoach;

/**
 * Représente un conseil concret, chiffré et actionnable produit par le Coach CV.
 */
public record ConseilCoachDto(
        String id,
        TypeConseilCoach type,
        String titre,
        String description,
        Double gainPoints,
        Long competenceId,
        String competenceNom,
        Long offreId,
        String offreTitre,
        String entreprise,
        String actionType,
        String actionLabel,
        String actionUrl,
        Double niveauActuel,
        Double niveauCible,
        int priorite
) {
}
