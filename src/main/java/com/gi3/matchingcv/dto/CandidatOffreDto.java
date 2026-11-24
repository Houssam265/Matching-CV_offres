package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.time.LocalDateTime;

/**
 * Candidat dans la liste des candidatures d'une offre pour le recruteur.
 * Trié par score décroissant (snapshot à la candidature).
 * Aucune coordonnée (email) n'est exposée : le recruteur contacte le candidat via la messagerie.
 * Le mot de passe n'est JAMAIS présent.
 * Inclut le statut favori (étoile) et la note interne privée réservés au recruteur.
 */
public record CandidatOffreDto(
        Long candidatureId,
        Long etudiantId,
        Long offreId,
        String offreTitre,
        String nom,
        String prenom,
        String filiere,
        String etablissement,
        Double score,
        StatutCandidature statut,
        LocalDateTime dateCandidature,
        Boolean favori,
        String noteInterne,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String message
) {
    public CandidatOffreDto(Long candidatureId, Long etudiantId, Long offreId, String offreTitre,
                            String nom, String prenom, String filiere, String etablissement,
                            Double score, StatutCandidature statut, LocalDateTime dateCandidature) {
        this(candidatureId, etudiantId, offreId, offreTitre, nom, prenom, filiere, etablissement,
                score, statut, dateCandidature, false, null, null);
    }

    public static CandidatOffreDto from(Candidature c) {
        Etudiant e = c.getEtudiant();
        return new CandidatOffreDto(
                c.getId(),
                e.getId(),
                c.getOffre().getId(),
                c.getOffre().getTitre(),
                e.getNom(),
                e.getPrenom(),
                e.getFiliere(),
                e.getEtablissement(),
                c.getScore(),
                c.getStatut(),
                c.getDateCandidature(),
                c.getFavori() != null ? c.getFavori() : false,
                c.getNoteInterne(),
                c.getMessage()
        );
    }
}
