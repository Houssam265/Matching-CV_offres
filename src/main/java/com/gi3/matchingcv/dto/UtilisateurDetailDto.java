package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.Role;

import java.time.LocalDateTime;

/**
 * Fiche détaillée d'un utilisateur pour l'administration.
 */
public record UtilisateurDetailDto(
        Long id,
        String nom,
        String prenom,
        String email,
        Role role,
        boolean actif,
        LocalDateTime dateInscription,

        // Données étudiant
        String filiere,
        String etablissement,
        long nbCompetences,
        long nbProjets,
        long nbCandidatures,

        // Données recruteur
        String nomEntreprise,
        String secteurActivite,
        long nbOffres,
        long nbCandidaturesRecues
) {
}
