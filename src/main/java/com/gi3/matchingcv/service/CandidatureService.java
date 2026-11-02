package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CandidatDetailDto;
import com.gi3.matchingcv.dto.CandidatOffreDto;
import com.gi3.matchingcv.dto.CandidatureDto;
import com.gi3.matchingcv.dto.OffreStatistiquesDto;
import com.gi3.matchingcv.dto.RecruteurDashboardDto;
import com.gi3.matchingcv.model.enums.StatutCandidature;

import java.util.List;
import java.util.Optional;

/**
 * Gestion du cycle de vie des candidatures.
 *
 * Règles métier garanties par l'implémentation :
 * - Une seule candidature par couple (étudiant, offre).
 * - On ne peut postuler qu'à une offre ACTIVE et non échue (date limite respectée).
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
     * @throws IllegalStateException si l'offre n'est pas ACTIVE, si la date limite est dépassée ou si la candidature existe déjà
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
     * Liste filtrée et triée des candidats d'une offre pour son recruteur propriétaire.
     * L'email est masqué (null) tant que la candidature n'est pas ACCEPTEE.
     */
    List<CandidatOffreDto> listerCandidatsPourRecruteur(
            Long offreId, Long recruteurId,
            StatutCandidature filtreStatut,
            Boolean favorisOnly,
            Double scoreMin,
            String tri
    );

    default List<CandidatOffreDto> listerCandidatsPourRecruteur(Long offreId, Long recruteurId, StatutCandidature filtreStatut) {
        return listerCandidatsPourRecruteur(offreId, recruteurId, filtreStatut, false, null, "score");
    }

    default List<CandidatOffreDto> listerCandidatsPourRecruteur(Long offreId, Long recruteurId) {
        return listerCandidatsPourRecruteur(offreId, recruteurId, null, false, null, "score");
    }

    /**
     * Consultation détaillée d'un candidat pour une offre du recruteur.
     * Si la candidature était ENVOYEE, elle passe automatiquement à VUE.
     * L'email est affiché uniquement si la candidature est ACCEPTEE.
     */
    CandidatDetailDto obtenirDetailCandidat(Long candidatureId, Long recruteurId);

    /**
     * Modification sécurisée du statut par le recruteur propriétaire de l'offre, avec message optionnel.
     */
    CandidatureDto changerStatutPourRecruteur(Long candidatureId, StatutCandidature nouveauStatut, Long recruteurId, String message);

    default CandidatureDto changerStatutPourRecruteur(Long candidatureId, StatutCandidature nouveauStatut, Long recruteurId) {
        return changerStatutPourRecruteur(candidatureId, nouveauStatut, recruteurId, null);
    }

    /**
     * Bascule l'état favori (étoile) d'une candidature pour le recruteur propriétaire.
     */
    CandidatureDto basculerFavori(Long candidatureId, Long recruteurId);

    /**
     * Enregistre une note interne privée par candidature, visible uniquement du recruteur propriétaire.
     */
    CandidatureDto definirNoteInterne(Long candidatureId, Long recruteurId, String note);

    /**
     * Statistiques d'une offre (entonnoir, score moyen, tranches, délai moyen).
     */
    OffreStatistiquesDto obtenirStatistiquesOffre(Long offreId, Long recruteurId);

    /**
     * Données complètes du tableau de bord recruteur (offres, candidatures, répartition, récentes, 7j/30j, top compétences, offres inactives 14j).
     */
    RecruteurDashboardDto obtenirDashboardRecruteur(Long recruteurId);
}
