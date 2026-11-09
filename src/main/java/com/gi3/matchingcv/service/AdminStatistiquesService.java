package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AdminStatsDto;

/**
 * Service de calcul des statistiques globales de la plateforme pour l'espace administrateur.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
public interface AdminStatistiquesService {

    /**
     * Calcule et retourne les métriques globales de la plateforme.
     */
    AdminStatsDto obtenirStatistiques();
}
