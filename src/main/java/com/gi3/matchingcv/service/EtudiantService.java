package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAttachRequest;
import com.gi3.matchingcv.dto.EtudiantCompetenceDTO;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Etudiant;

import java.util.List;

/**
 * Interface du service gérant les opérations métier sur les étudiants.
 * Permet l'inversion de contrôle (IoC) en découplant les consommateurs du service
 * de son implémentation concrète.
 */
public interface EtudiantService {

    /**
     * Récupère la liste de tous les étudiants enregistrés.
     *
     * @return la liste des étudiants
     */
    List<Etudiant> listerTous();

    /**
     * Enregistre un nouvel étudiant dans le système.
     * Valide l'unicité de l'e-mail avant la sauvegarde.
     *
     * @param etudiant l'entité étudiant à créer
     * @return l'étudiant persisté avec son identifiant généré
     * @throws com.gi3.matchingcv.exception.EmailDejaUtiliseException si l'e-mail est déjà utilisé
     */
    Etudiant creer(Etudiant etudiant);

    /**
     * Recherche un étudiant par son identifiant unique.
     *
     * @param id l'identifiant de l'étudiant
     * @return l'étudiant trouvé
     * @throws jakarta.persistence.EntityNotFoundException si aucun étudiant n'existe avec cet identifiant
     */
    Etudiant trouverParId(Long id);

    /**
     * Sauvegarde (met à jour) un étudiant déjà persisté sans effectuer la vérification d'unicité de l'e-mail.
     * À utiliser uniquement pour les modifications de profil (ajout/retrait de compétences, projets, expériences).
     *
     * @param etudiant l'entité étudiant à mettre à jour
     * @return l'étudiant mis à jour
     */
    Etudiant sauvegarder(Etudiant etudiant);

    /**
     * Récupère la liste personnelle de compétences d'un étudiant avec leurs statistiques d'utilisation
     * (nombre de ses projets et nombre de ses expériences qui les mobilisent).
     *
     * @param etudiantId identifiant de l'étudiant
     * @return liste des DTO de compétences enrichies
     */
    List<EtudiantCompetenceDTO> listerCompetencesAvecStats(Long etudiantId);

    /**
     * Ajoute une compétence à la liste personnelle de l'étudiant.
     * Si competenceId est fourni, l'ajoute.
     * Si nouvelleCompetence est fournie, recherche par nomNormalise (insensible à la casse, espaces, tirets) :
     * si elle existe, la rattache ; sinon la propose (EN_ATTENTE, proposeePar = etudiant).
     * Refuse avec 409 si la compétence est déjà dans sa liste.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param request    requête contenant soit competenceId soit (nouvelleCompetence, categorie)
     * @return la compétence rattachée
     */
    Competence ajouterCompetence(Long etudiantId, CompetenceAttachRequest request);

    /**
     * Retire une compétence de la liste personnelle de l'étudiant ET la détache de tous ses projets
     * et expériences dans une même transaction. Ne supprime jamais la compétence du dictionnaire.
     *
     * @param etudiantId   identifiant de l'étudiant
     * @param competenceId identifiant de la compétence à retirer
     */
    void retirerCompetence(Long etudiantId, Long competenceId);

    /**
     * Suggère des compétences pour l'autocomplétion (8 résultats max).
     * Correspondance partielle sur le nom normalisé.
     * Renvoie les VALIDEE + les EN_ATTENTE de l'étudiant.
     * Exclut celles déjà dans sa liste et toutes les REJETEE ainsi que les EN_ATTENTE d'autrui.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param query      terme de recherche
     * @return liste des suggestions
     */
    List<Competence> suggererCompetences(Long etudiantId, String query);
}
