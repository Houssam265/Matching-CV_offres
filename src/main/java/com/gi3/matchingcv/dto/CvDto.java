package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;
import java.util.List;

/**
 * Données d'un CV généré à partir du profil de l'étudiant.
 * Coordonnées privées (email, téléphone) : renseignées uniquement pour l'étudiant lui-même
 * (CV à réutiliser ailleurs). Un recruteur ne les reçoit jamais : il passe par la messagerie.
 */
public record CvDto(
        Long etudiantId,
        String prenom,
        String nom,
        String titre,
        String resume,
        String disponibilite,
        @JsonInclude(JsonInclude.Include.NON_NULL) String email,
        @JsonInclude(JsonInclude.Include.NON_NULL) String telephone,
        String ville,
        List<Lien> liens,
        Formation formation,
        List<GroupeCompetences> competences,
        List<Experience> experiences,
        List<Projet> projets,
        List<String> langues,
        List<String> interets
) {
    /** type : linkedin, github, portfolio, web — sert à choisir l'icône. */
    public record Lien(String type, String libelle, String url) {}
    public record Formation(String filiere, String etablissement, String periode) {}
    public record GroupeCompetences(String categorie, List<String> competences) {}
    public record Experience(String poste, String entreprise, LocalDate dateDebut, LocalDate dateFin, List<String> competences) {}
    public record Projet(String titre, String description, List<String> competences) {}
}
