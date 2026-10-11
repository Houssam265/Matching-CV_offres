package com.gi3.matchingcv.dto;

import java.util.List;

/**
 * Détail complet du calcul du score de matching entre un étudiant et une offre.
 */
public record MatchingDetailDto(
        Long etudiantId,
        Long offreId,
        String offreTitre,
        String entreprise,
        Double score,
        boolean scoreDisponible,
        String message,
        Double scoreRequises,
        Double scoreAtouts,
        Double partRequisesMax,
        Double partAtoutsMax,
        int nbRequisesValidees,
        int nbAtoutsValides,
        List<CompetenceDetailScoreDto> competences
) {
}
