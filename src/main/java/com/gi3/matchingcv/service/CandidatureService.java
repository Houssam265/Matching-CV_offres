package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CandidatDetailDto;
import com.gi3.matchingcv.dto.CandidatOffreDto;
import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.RecruteurDashboardDto;
import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.util.List;
import java.util.Optional;

/**
 * Gestion du cycle de vie des candidatures.
 *
 * Règles métier garanties par l'implémentation :
 * - Une seule candidature par couple (étudiant, offre).
 * - On ne peut postuler qu'à une offre ACTIVE.
 * - Transitions de statut autorisées :
 *     ENVOYEE → VUE | ACCEPTEE | REFUSEE
 *     VUE     → ACCEPTEE | REFUSEE
 *     ACCEPTEE, REFUSEE → définitifs (aucun changement possible)
 *
 * TODO sécurité : changerStatut doit être restreint au recruteur propriétaire de l'offre ;
 *                 listerParEtudiant doit être restreint à l'étudiant connecté ou un Admin.
 */
public interface CandidatureService {

    /**
     * Enregistre une candidature pour l'étudiant sur l'offre donnée.
     * Le score courant (calculé en temps réel) est snapshottté à ce moment.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param offreId    identifiant de l'offre
     * @return le DTO de la candidature créée
     * @throws jakarta.persistence.EntityNotFoundException si l'étudiant ou l'offre est introuvable
     * @throws IllegalStateException si l'offre n'est pas ACTIVE ou si la candidature existe déjà
     */
    CandidatureDto postuler(Long etudiantId, Long offreId);

    /**
     * Retourne toutes les candidatures d'un étudiant, triées par date décroissante.
     */
    List<CandidatureDto> listerParEtudiant(Long etudiantId);

    /**
     * Retourne toutes les candidatures reçues pour une offre, triées par score décroissant (null en dernier).
     */
    List<CandidatureDto> listerParOffre(Long offreId);

    /**
     * Change le statut d'une candidature selon les transitions autorisées.
     *
     * @throws IllegalStateException si la transition est interdite
     */
    CandidatureDto changerStatut(Long candidatureId, StatutCandidature nouveauStatut);

    /**
     * Retourne la candidature d'un étudiant pour une offre spécifique, si elle existe.
     */
    Optional<CandidatureDto> trouverParEtudiantEtOffre(Long etudiantId, Long offreId);

    /**
     * Nombre de candidatures d'un étudiant (pour le tableau de bord).
     */
    long compterParEtudiant(Long etudiantId);

    /**
     * Liste des candidats d'une offre pour son recruteur propriétaire, classée par score décroissant.
     * L'email est masqué (null) tant que la candidature n'est pas ACCEPTEE.
     *
     * @param offreId       identifiant de l'offre
     * @param recruteurId   identifiant du recruteur demandeur
     * @param filtreStatut  filtre optionnel sur le statut de la candidature
     * @throws org.springframework.web.server.ResponseStatusException 403 si l'offre appartient à un autre recruteur
     */
    List<CandidatOffreDto> listerCandidatsPourRecruteur(Long offreId, Long recruteurId, StatutCandidature filtreStatut);

    default List<CandidatOffreDto> listerCandidatsPourRecruteur(Long offreId, Long recruteurId) {
        return listerCandidatsPourRecruteur(offreId, recruteurId, null);
    }

    /**
     * Consultation détaillée d'un candidat pour une offre du recruteur.
     * Si la candidature était ENVOYEE, elle passe automatiquement à VUE.
     * L'email est affiché uniquement si la candidature est ACCEPTEE.
     *
     * @param candidatureId identifiant de la candidature
     * @param recruteurId   identifiant du recruteur demandeur
     * @throws org.springframework.web.server.ResponseStatusException 403 si l'offre n'appartient pas au recruteur
     */
    CandidatDetailDto obtenirDetailCandidat(Long candidatureId, Long recruteurId);

    /**
     * Modification sécurisée du statut par le recruteur propriétaire de l'offre.
     *
     * @throws org.springframework.web.server.ResponseStatusException 403 si l'offre n'appartient pas au recruteur
     * @throws IllegalStateException si la transition de statut est interdite
     */
    CandidatureDto changerStatutPourRecruteur(Long candidatureId, StatutCandidature nouveauStatut, Long recruteurId);

    /**
     * Données complètes du tableau de bord recruteur (offres, candidatures, répartition et récentes).
     */
    RecruteurDashboardDto obtenirDashboardRecruteur(Long recruteurId);
}
