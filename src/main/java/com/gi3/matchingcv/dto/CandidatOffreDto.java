package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.time.LocalDateTime;

/**
 * Candidat dans la liste des candidatures d'une offre pour le recruteur.
 * Trié par score décroissant (snapshot à la candidature).
 * L'email est masqué (null) tant que la candidature n'est pas ACCEPTEE.
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
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String email,
        Boolean favori,
        String noteInterne,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String message
) {
    public CandidatOffreDto(Long candidatureId, Long etudiantId, Long offreId, String offreTitre,
                            String nom, String prenom, String filiere, String etablissement,
                            Double score, StatutCandidature statut, LocalDateTime dateCandidature,
                            String email) {
        this(candidatureId, etudiantId, offreId, offreTitre, nom, prenom, filiere, etablissement,
                score, statut, dateCandidature, email, false, null, null);
    }

    public static CandidatOffreDto from(Candidature c) {
        Etudiant e = c.getEtudiant();
        String email = (c.getStatut() == StatutCandidature.ACCEPTEE) ? e.getEmail() : null;
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
                email,
                c.getFavori() != null ? c.getFavori() : false,
                c.getNoteInterne(),
                c.getMessage()
        );
    }
}
