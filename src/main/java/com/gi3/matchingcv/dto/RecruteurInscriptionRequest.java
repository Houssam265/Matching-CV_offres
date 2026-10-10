package com.gi3.matchingcv.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.gi3.matchingcv.model.enums.Role;
import jakarta.validation.constraints.*;

public record RecruteurInscriptionRequest(
        @NotBlank @Size(max = 255) String nom,
        @NotBlank @Size(max = 255) String prenom,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 6, max = 255) String motDePasse,
        @JsonAlias("entreprise") @NotBlank @Size(max = 255) String nomEntreprise,
        @JsonAlias("secteur") @NotBlank @Size(max = 255) String secteurActivite) {
    public InscriptionRequest toAuthRequest() {
        InscriptionRequest request = new InscriptionRequest();
        request.setNom(nom); request.setPrenom(prenom); request.setEmail(email);
        request.setMotDePasse(motDePasse); request.setRole(Role.RECRUTEUR);
        request.setNomEntreprise(nomEntreprise); request.setSecteurActivite(secteurActivite);
        return request;
    }
}
