package com.gi3.matchingcv.dto;

import java.util.Map;

/**
 * Statistiques d'une offre :
 * - Entonnoir : envoyées → vues → acceptées / refusées
 * - Score moyen des candidats
 * - Répartition des scores par tranches (<40%, 40-69%, >=70%)
 * - Délai moyen de traitement (en heures)
 */
public record OffreStatistiquesDto(
        Long offreId,
        String offreTitre,
        long totalCandidatures,
        EntonnoirDto entonnoir,
        Double scoreMoyen,
        Map<String, Long> repartitionScores,
        Double delaiMoyenTraitementHeures
) {
    public record EntonnoirDto(
            long envoyees,
            long vues,
            long acceptees,
            long refusees
    ) {}
}
