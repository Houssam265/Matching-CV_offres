package com.gi3.matchingcv.dto;

/**
 * DTO pour attacher une compétence à un profil étudiant.
 * Deux modes exclusifs :
 * <ul>
 *   <li>Compétence existante : renseigner uniquement {@code competenceId}</li>
 *   <li>Nouvelle compétence proposée : renseigner {@code nouvelleCompetence} et optionnellement {@code categorie}</li>
 * </ul>
 */
public class CompetenceAttachRequest {

    /** Identifiant d'une compétence existante du référentiel. */
    private Long competenceId;

    /** Nom d'une nouvelle compétence à proposer (statut EN_ATTENTE). */
    private String nouvelleCompetence;

    /** Catégorie de la nouvelle compétence (optionnel). */
    private String categorie;

    public Long getCompetenceId() { return competenceId; }
    public void setCompetenceId(Long competenceId) { this.competenceId = competenceId; }

    public String getNouvelleCompetence() { return nouvelleCompetence; }
    public void setNouvelleCompetence(String nouvelleCompetence) { this.nouvelleCompetence = nouvelleCompetence; }

    public String getCategorie() { return categorie; }
    public void setCategorie(String categorie) { this.categorie = categorie; }
}
