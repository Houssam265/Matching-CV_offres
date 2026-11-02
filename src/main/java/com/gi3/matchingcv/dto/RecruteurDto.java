package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.enums.Role;

public record RecruteurDto(Long id, String nom, String prenom, String email, Role role,
                           String nomEntreprise, String secteurActivite,
                           String ville, String siteWeb, String descriptionEntreprise) {

    public RecruteurDto(Long id, String nom, String prenom, String email, Role role,
                        String nomEntreprise, String secteurActivite) {
        this(id, nom, prenom, email, role, nomEntreprise, secteurActivite, null, null, null);
    }

    public static RecruteurDto from(Recruteur r) {
        return new RecruteurDto(r.getId(), r.getNom(), r.getPrenom(), r.getEmail(), r.getRole(),
                r.getNomEntreprise(), r.getSecteurActivite(),
                r.getVille(), r.getSiteWeb(), r.getDescriptionEntreprise());
    }
}
