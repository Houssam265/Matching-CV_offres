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
 */
public record RecruteurDashboardDto(
        long offresActives,
        long offresCloturees,
        long totalOffres,
        long candidaturesRecues,
        long candidaturesATraiter,
        Map<StatutCandidature, Long> repartitionStatuts,
        List<CandidatOffreDto> dernieresCandidatures
) {
}
