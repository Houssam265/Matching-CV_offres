package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.model.enums.TypeExigence;

/**
 * Détail d'une compétence de l'offre dans le calcul du score de matching.
 */
public record CompetenceDetailScoreDto(
        Long competenceId,
        String nom,
        TypeExigence typeExigence,
        StatutCompetence statut,
        String provenance,
        String provenanceLabel,
        Double niveau,
        boolean comptee,
        String raison,
        boolean possedee
) {
}
