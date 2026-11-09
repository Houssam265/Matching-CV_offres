package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.AuditLog;

import java.util.List;

/**
 * Service de gestion du journal d'audit administratif.
 * // TODO sécurité : réservé ADMIN.
 */
public interface AuditService {

    /**
     * Enregistre une entrée dans le journal d'audit.
     *
     * @param adminId identifiant de l'administrateur ayant déclenché l'action (nullable pour actions système)
     * @param action  code action (ex: VALIDER_COMPETENCE, SUSPENDRE_UTILISATEUR, etc.)
     * @param cible   désignation de la cible concernée
     * @param details détails contextuels ou motif
     */
    AuditLog journaliser(Long adminId, String action, String cible, String details);

    /**
     * Liste les entrées du journal d'audit avec filtres optionnels.
     *
     * @param action filtre sur le code action (optionnel)
     * @param query  recherche textuelle sur adminNom, cible ou details (optionnel)
     * @return la liste ordonnée antichronologiquement
     */
    List<AuditLog> listerLogs(String action, String query);
}
