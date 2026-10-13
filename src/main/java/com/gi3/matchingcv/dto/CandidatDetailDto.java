package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutCompetence;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Vue détaillée d'un candidat pour une offre précise :
 * - Profil complet de l'étudiant (compétences avec provenance, projets, expériences).
 * - Explication détaillée du score de matching calculé pour CETTE offre.
 * - Comparaison entre score à la candidature et score actuel recalculé.
 * - L'email n'est exposé QUE si la candidature est ACCEPTEE.
 * - Aucun mot de passe exposé.
 */
public record CandidatDetailDto(
        Long candidatureId,
        Long etudiantId,
        Long offreId,
        String offreTitre,
        String nom,
        String prenom,
        String filiere,
        String etablissement,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String email,
        StatutCandidature statut,
        LocalDateTime dateCandidature,
        Double scoreCandidature,
        Double scoreActuel,
        boolean scoreModifie,
        MatchingDetailDto matchingDetail,
        List<CompetenceProfilDto> competences,
        List<ProjetCandidatDto> projets,
        List<ExperienceCandidatDto> experiences
) {
    public record CompetenceProfilDto(
            Long competenceId,
            String nom,
            String categorie,
            StatutCompetence statut,
            String provenance,
            String provenanceLabel
    ) {}

    public record ProjetCandidatDto(
            Long id,
            String titre,
            String description,
            List<String> competences
    ) {}

    public record ExperienceCandidatDto(
            Long id,
            String poste,
            String entreprise,
            LocalDate dateDebut,
            LocalDate dateFin,
            List<String> competences
    ) {}
}
