package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.enums.Role;

public record RecruteurDto(Long id, String nom, String prenom, String email, Role role,
                           String nomEntreprise, String secteurActivite) {
    public static RecruteurDto from(Recruteur r) {
        return new RecruteurDto(r.getId(), r.getNom(), r.getPrenom(), r.getEmail(), r.getRole(),
                r.getNomEntreprise(), r.getSecteurActivite());
    }
}
