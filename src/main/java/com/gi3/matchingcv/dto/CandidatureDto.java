package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeContrat;

import java.time.LocalDateTime;

/**
 * Vue aplatie d'une candidature — aucune boucle JSON possible.
 * Le message du recruteur lors de l'acceptation ou du refus y figure pour l'étudiant.
 * La note interne n'est JAMAIS exposée ici. Le favori est inclus pour la réponse du PATCH /favori.
 */
public record CandidatureDto(
        Long id,
        Long etudiantId,
        Long offreId,
        String offreTitre,
        String entreprise,
        String domaine,
        String localisation,
        TypeContrat typeContrat,
        StatutOffre statutOffre,
        Double score,
        StatutCandidature statut,
        LocalDateTime dateCandidature,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        String message,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        Boolean favori
) {
    public CandidatureDto(Long id, Long etudiantId, Long offreId, String offreTitre,
                          String entreprise, String domaine, String localisation,
                          TypeContrat typeContrat, StatutOffre statutOffre,
                          Double score, StatutCandidature statut, LocalDateTime dateCandidature) {
        this(id, etudiantId, offreId, offreTitre, entreprise, domaine, localisation, typeContrat, statutOffre, score, statut, dateCandidature, null, null);
    }

    public CandidatureDto(Long id, Long etudiantId, Long offreId, String offreTitre,
                          String entreprise, String domaine, String localisation,
                          TypeContrat typeContrat, StatutOffre statutOffre,
                          Double score, StatutCandidature statut, LocalDateTime dateCandidature,
                          String message) {
        this(id, etudiantId, offreId, offreTitre, entreprise, domaine, localisation, typeContrat, statutOffre, score, statut, dateCandidature, message, null);
    }

    public static CandidatureDto from(Candidature c) {
        var offre = c.getOffre();
        String entreprise = (offre.getRecruteur() != null) ? offre.getRecruteur().getNomEntreprise() : "";
        return new CandidatureDto(
                c.getId(),
                c.getEtudiant().getId(),
                offre.getId(),
                offre.getTitre(),
                entreprise,
                offre.getDomaine(),
                offre.getLocalisation(),
                offre.getTypeContrat(),
                offre.getStatut(),
                c.getScore(),
                c.getStatut(),
                c.getDateCandidature(),
                c.getMessage(),
                c.getFavori()
        );
    }
}
