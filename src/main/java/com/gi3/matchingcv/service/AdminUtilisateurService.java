package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AdminCreationRequest;
import com.gi3.matchingcv.dto.UtilisateurAdminDto;
import com.gi3.matchingcv.dto.UtilisateurDetailDto;
import com.gi3.matchingcv.model.Administrateur;
import com.gi3.matchingcv.model.enums.Role;

import java.util.List;

/**
 * Service de gestion des utilisateurs pour les administrateurs.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
public interface AdminUtilisateurService {

    /**
     * Liste les utilisateurs avec filtres optionnels par rôle, statut actif/suspendu et recherche textuelle.
     */
    List<UtilisateurAdminDto> listerUtilisateurs(Role role, Boolean actif, String query);

    /**
     * Récupère la fiche détaillée d'un utilisateur (étudiant, recruteur ou admin).
     */
    UtilisateurDetailDto obtenirDetail(Long id);

    /**
     * Suspend ou réactive un utilisateur.
     * Un utilisateur suspendu (actif=false) ne peut plus se connecter et ses offres sont masquées.
     */
    void changerStatut(Long id, boolean actif, Long adminId);

    /**
     * Crée un compte administrateur à l'initiative d'un admin existant.
     */
    Administrateur creerAdmin(AdminCreationRequest request, Long adminId);
}
