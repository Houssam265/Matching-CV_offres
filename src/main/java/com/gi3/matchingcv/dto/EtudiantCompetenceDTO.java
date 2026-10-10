package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.enums.StatutCompetence;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO représentant une compétence dans la liste personnelle d'un étudiant
 * enrichie de ses statistiques d'utilisation (nombre de projets et d'expériences).
 * Évite les références circulaires JSON.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class EtudiantCompetenceDTO {

    private Long id;
    private String nom;
    private String categorie;
    private StatutCompetence statut;
    private int nbProjets;
    private int nbExperiences;
}
