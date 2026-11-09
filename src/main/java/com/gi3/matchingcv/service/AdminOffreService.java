package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AdminOffreDto;
import com.gi3.matchingcv.model.enums.StatutOffre;

import java.util.List;

/**
 * Service de gestion des offres pour les administrateurs.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
public interface AdminOffreService {

    /**
     * Liste toutes les offres avec filtres optionnels par statut, domaine et recherche textuelle.
     */
    List<AdminOffreDto> listerOffres(StatutOffre statut, String domaine, String query);

    /**
     * Clôture une offre avec motif optionnel et notifie le recruteur concerné.
     */
    void cloturerOffre(Long offreId, String motif, Long adminId);
}
