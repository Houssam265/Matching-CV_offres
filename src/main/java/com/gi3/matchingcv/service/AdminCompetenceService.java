package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAModererDto;
import com.gi3.matchingcv.model.Competence;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Service de gestion administrative des compétences.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
public interface AdminCompetenceService {

    /** File de modération : toutes les compétences EN_ATTENTE avec usage et doublons. */
    List<CompetenceAModererDto> listerAModerer();

    /**
     * Valide une compétence EN_ATTENTE après modifications éventuelles.
     * Délègue à {@link CompetenceService#changerStatut} pour l'événement et la notification.
     */
    Competence validerAvecModifications(Long id, String nom, String categorie, List<String> synonymes, Long adminId);

    /**
     * Rejette une compétence EN_ATTENTE.
     * Délègue à {@link CompetenceService#changerStatut} puis journalise le motif.
     */
    Competence rejeterAvecMotif(Long id, String motif, Long adminId);

    /**
     * Fusionne la compétence source dans la cible dans une seule transaction :
     * re-pointe toutes les tables de jointure, ajoute l'ancien nom aux synonymes,
     * notifie l'auteur de la compétence source et journalise.
     */
    void fusionner(Long sourceId, Long cibleId, Long adminId);

    /** Validation en masse. */
    void validerGroupes(List<Long> ids, Long adminId);

    /** Rejet en masse avec motif commun. */
    void rejeterGroupes(List<Long> ids, String motif, Long adminId);

    /** Modifie une compétence existante (nom, catégorie, synonymes) et journalise. */
    Competence modifier(Long id, String nom, String categorie, List<String> synonymes, Long adminId);

    /** Import idempotent depuis un CSV (format nom;categorie;synonymes). */
    Map<String, Integer> importerCsv(InputStream csv, Long adminId) throws Exception;

    /** Export CSV de toutes les compétences VALIDEE. */
    String exporterCsv();
}
