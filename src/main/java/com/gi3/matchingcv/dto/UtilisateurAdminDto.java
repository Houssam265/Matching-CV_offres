package com.gi3.matchingcv.dto;

import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.Role;

import java.time.LocalDateTime;

/**
 * DTO pour la liste des utilisateurs dans l'espace administrateur.
 */
public record UtilisateurAdminDto(
        Long id,
        String nom,
        String prenom,
        String email,
        Role role,
        boolean actif,
        LocalDateTime dateInscription,
        String organisation // Filière pour étudiant, entreprise pour recruteur
) {
    public static UtilisateurAdminDto from(Utilisateur u) {
        String org = null;
        if (u instanceof Etudiant e) {
            org = e.getFiliere();
        } else if (u instanceof Recruteur r) {
            org = r.getNomEntreprise();
        }
        return new UtilisateurAdminDto(
                u.getId(),
                u.getNom(),
                u.getPrenom(),
                u.getEmail(),
                u.getRole(),
                u.isActif(),
                u.getDateInscription(),
                org
        );
    }
}
