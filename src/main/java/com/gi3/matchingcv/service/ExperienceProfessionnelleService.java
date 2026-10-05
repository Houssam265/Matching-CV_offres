package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.ExperienceProfessionnelle;

import java.util.List;

/**
 * Interface du service gérant les opérations métier sur les expériences professionnelles.
 */
public interface ExperienceProfessionnelleService {

    /**
     * Récupère les expériences professionnelles d'un étudiant donné.
     *
     * @param etudiantId identifiant de l'étudiant
     * @return liste de ses expériences
     */
    List<ExperienceProfessionnelle> listerParEtudiant(Long etudiantId);

    /**
     * Crée et persiste une expérience rattachée à un étudiant.
     *
     * @param experience l'expérience à créer (l'etudiant doit déjà être défini)
     * @return l'expérience persistée avec son identifiant
     */
    ExperienceProfessionnelle creer(ExperienceProfessionnelle experience);

    /**
     * Supprime une expérience professionnelle.
     *
     * @param etudiantId    identifiant de l'étudiant propriétaire
     * @param experienceId  identifiant de l'expérience à supprimer
     * @throws jakarta.persistence.EntityNotFoundException si l'expérience est introuvable ou n'appartient pas à cet étudiant
     */
    void supprimer(Long etudiantId, Long experienceId);
}
