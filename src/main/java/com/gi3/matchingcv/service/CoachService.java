package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CoachResumeDto;
import com.gi3.matchingcv.dto.ConseilCoachDto;
import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.dto.SimulationResultDto;

import java.util.List;
import java.util.Map;

/**
 * Service Coach CV : produit des conseils concrets, calculés par règles,
 * pour aider l'étudiant à améliorer son profil et ses scores de matching.
 */
public interface CoachService {

    /**
     * Simule le score de matching entre un étudiant et une offre si l'étudiant possédait
     * une ou plusieurs compétences à des niveaux simulés donnés.
     * NE MODIFIE ET N'ENREGISTRE STRICTEMENT RIEN EN BASE DE DONNÉES.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param offreId    identifiant de l'offre
     * @param hypotheses map associant l'identifiant de la compétence au niveau simulé (ex: competenceId -> 0.7)
     * @return le détail complet du matching recalculé sous ces hypothèses
     */
    MatchingDetailDto simulerScore(Long etudiantId, Long offreId, Map<Long, Double> hypotheses);

    /**
     * Surcharge pratique de simulation pour une seule compétence à un niveau donné.
     *
     * @param etudiantId   identifiant de l'étudiant
     * @param offreId      identifiant de l'offre
     * @param competenceId identifiant de la compétence à simuler
     * @param niveauSimule niveau simulé (0.7 pour projet, 0.8+ pour expérience)
     * @return le détail complet du matching recalculé
     */
    MatchingDetailDto simulerScore(Long etudiantId, Long offreId, Long competenceId, double niveauSimule);

    /**
     * Calcule le gain exact entre le score actuel et le score simulé.
     */
    SimulationResultDto simulerGain(Long etudiantId, Long offreId, Long competenceId, double niveauSimule);

    /**
     * Génère la liste des conseils d'amélioration pour une offre spécifique.
     * Triés par gain décroissant, sans doublon, 5 à 8 au maximum.
     *
     * @param etudiantId identifiant de l'étudiant
     * @param offreId    identifiant de l'offre
     * @return liste des conseils pour cette offre
     */
    List<ConseilCoachDto> genererConseilsPourOffre(Long etudiantId, Long offreId);

    /**
     * Génère l'ensemble des conseils d'amélioration globaux pour un étudiant
     * (par offre, valorisation de compétences déclaratives, compétences du marché les plus demandées, hygiène).
     * Triés par gain décroissant, sans doublon, 5 à 8 au maximum.
     *
     * @param etudiantId identifiant de l'étudiant
     * @return liste des meilleurs conseils globaux
     */
    List<ConseilCoachDto> genererConseilsEtudiant(Long etudiantId);

    /**
     * Fournit une synthèse complète du Coach CV (taux de complétion, gain potentiel, top 3, liste complète).
     *
     * @param etudiantId identifiant de l'étudiant
     * @return synthèse du coach
     */
    CoachResumeDto obtenirResumeCoach(Long etudiantId);
}
