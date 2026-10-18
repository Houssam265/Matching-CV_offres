package com.gi3.matchingcv.dto;

/**
 * Résultat d'une simulation "et si" pour une offre.
 */
public record SimulationResultDto(
        Long etudiantId,
        Long offreId,
        Double scoreActuel,
        Double scoreSimule,
        Double gain,
        MatchingDetailDto detailSimule
) {
}
