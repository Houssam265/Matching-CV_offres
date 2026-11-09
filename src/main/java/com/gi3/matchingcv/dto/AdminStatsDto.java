package com.gi3.matchingcv.dto;

import java.util.List;
import java.util.Map;

/**
 * Statistiques globales pour le tableau de bord administrateur.
 */
public record AdminStatsDto(
        Map<String, Long> utilisateursParRole,
        List<SemaineInscriptionDto> inscriptionsParSemaine,
        Map<String, DomaineStatDto> offresParDomaine,
        EntonnoirCandidaturesDto entonnoir,
        Double scoreMoyen,
        Double tauxAcceptation,
        List<CompetenceMarcheDto> ecartMarche,
        long competencesEnAttente
) {
}
