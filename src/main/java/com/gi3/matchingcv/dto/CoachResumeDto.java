package com.gi3.matchingcv.dto;

import java.util.List;

/**
 * Synthèse globale du Coach CV pour un étudiant.
 */
public record CoachResumeDto(
        Long etudiantId,
        int scoreCompletionProfil,
        Double gainPotentielMax,
        int totalConseils,
        List<ConseilCoachDto> topConseils,
        List<ConseilCoachDto> tousConseils,
        int nbCompetencesEnAttente,
        int nbCompetencesRejetees,
        int nbProjetsSansCompetences,
        int nbExperiencesSansCompetences
) {
}
