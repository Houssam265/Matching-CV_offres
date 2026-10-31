package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.util.List;
import java.util.Map;

/**
 * Données complètes pour le tableau de bord recruteur :
 * - Indicateurs d'offres (actives, clôturées, total)
 * - Indicateurs de candidatures (reçues, à traiter)
 * - Répartition par statut de candidature
 * - Liste des candidatures les plus récentes
 * - Candidatures reçues sur 7 et 30 jours
 * - Compétences les plus demandées dans ses offres
 * - Offres sans candidature depuis 14 jours
 */
public record RecruteurDashboardDto(
        long offresActives,
        long offresCloturees,
        long totalOffres,
        long candidaturesRecues,
        long candidaturesATraiter,
        Map<StatutCandidature, Long> repartitionStatuts,
        List<CandidatOffreDto> dernieresCandidatures,
        long candidatures7Jours,
        long candidatures30Jours,
        List<CompetenceDemandeDto> competencesLesPlusDemandees,
        List<OffreDto> offresSansCandidature14Jours
) {
    public RecruteurDashboardDto(
            long offresActives,
            long offresCloturees,
            long totalOffres,
            long candidaturesRecues,
            long candidaturesATraiter,
            Map<StatutCandidature, Long> repartitionStatuts,
            List<CandidatOffreDto> dernieresCandidatures
    ) {
        this(offresActives, offresCloturees, totalOffres, candidaturesRecues, candidaturesATraiter,
                repartitionStatuts, dernieresCandidatures, 0L, 0L, List.of(), List.of());
    }
}
