package com.gi3.matchingcv.service;

import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.CompetenceUtiliseeException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.OffreCompetenceRepository;
import com.gi3.matchingcv.repository.ProfilCompetenceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implémentation du service métier pour la gestion des compétences.
 * Respecte l'architecture en couches et le principe d'Inversion de Contrôle (IoC).
 */
@Service
@Transactional
public class CompetenceServiceImpl implements CompetenceService {

    private final CompetenceRepository competenceRepository;
    private final ProfilCompetenceRepository profilCompetenceRepository;
    private final OffreCompetenceRepository offreCompetenceRepository;
    private final EntityManager entityManager;

    /**
     * Injection par constructeur (IoC) : la classe déclare explicitement ses dépendances,
     * et le conteneur Spring IoC les instancie et les injecte automatiquement.
     */
    public CompetenceServiceImpl(
            CompetenceRepository competenceRepository,
            ProfilCompetenceRepository profilCompetenceRepository,
            OffreCompetenceRepository offreCompetenceRepository,
            EntityManager entityManager
    ) {
        this.competenceRepository = competenceRepository;
        this.profilCompetenceRepository = profilCompetenceRepository;
        this.offreCompetenceRepository = offreCompetenceRepository;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competence> listerToutes() {
        return competenceRepository.findAll();
    }

    @Override
    public Competence creer(Competence competence) {
        String nomNettoye = competence.getNom() != null ? competence.getNom().trim() : "";
        competence.setNom(nomNettoye);

        String nomNormalise = Competence.normaliserNom(nomNettoye);
        competence.setNomNormalise(nomNormalise);

        competenceRepository.findByNomNormalise(nomNormalise).ifPresent(existante -> {
            throw new CompetenceDejaExistanteException("Cette compétence existe déjà : " + existante.getNom());
        });

        competence.setStatut(StatutCompetence.VALIDEE);
        return competenceRepository.save(competence);
    }

    @Override
    @Transactional(readOnly = true)
    public Competence trouverParId(Long id) {
        return competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable avec l'identifiant : " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Competence> trouverParNomNormalise(String nomNormalise) {
        return competenceRepository.findByNomNormalise(nomNormalise);
    }

    @Override
    public void supprimer(Long id) {
        Competence competence = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable avec l'identifiant : " + id));

        Number nbEtudiants = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM etudiant_competence WHERE competence_id = :id")
                .setParameter("id", id).getSingleResult();
        Number nbProjets = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM projet_competence WHERE competence_id = :id")
                .setParameter("id", id).getSingleResult();
        Number nbExperiences = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM experience_competence WHERE competence_id = :id")
                .setParameter("id", id).getSingleResult();
        Number nbOffres = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM offre_competences WHERE competence_id = :id")
                .setParameter("id", id).getSingleResult();
        Number nbProfils = (Number) entityManager.createNativeQuery(
                "SELECT COUNT(*) FROM profil_competences WHERE competence_id = :id")
                .setParameter("id", id).getSingleResult();

        long totalUtilisations = (nbEtudiants != null ? nbEtudiants.longValue() : 0)
                + (nbProjets != null ? nbProjets.longValue() : 0)
                + (nbExperiences != null ? nbExperiences.longValue() : 0)
                + (nbOffres != null ? nbOffres.longValue() : 0)
                + (nbProfils != null ? nbProfils.longValue() : 0);

        if (totalUtilisations > 0) {
            throw new CompetenceUtiliseeException(
                    "Cette compétence ne peut pas être supprimée car elle est utilisée dans des profils, projets, expériences ou offres."
            );
        }

        competenceRepository.delete(competence);
    }

    @Override
    public Competence proposer(String nom, String categorie, Utilisateur proposeePar) {
        String nomNettoye = nom != null ? nom.trim() : "";

        String nomNormalise = Competence.normaliserNom(nomNettoye);
        competenceRepository.findByNomNormalise(nomNormalise).ifPresent(existante -> {
            throw new CompetenceDejaExistanteException("Cette compétence existe déjà : " + existante.getNom());
        });

        Competence competence = new Competence();
        competence.setNom(nomNettoye);
        competence.setNomNormalise(nomNormalise);
        competence.setCategorie(categorie);
        competence.setStatut(StatutCompetence.EN_ATTENTE);
        competence.setProposeePar(proposeePar);
        return competenceRepository.save(competence);
    }

    @Override
    public Competence proposer(String nom, String categorie) {
        return proposer(nom, categorie, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competence> suggererCompetences(String query, Long utilisateurId) {
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String qNormalise = Competence.normaliserNom(query.trim());
        if (qNormalise == null || qNormalise.isEmpty()) {
            return Collections.emptyList();
        }

        List<Competence> toutes = competenceRepository.findAll();
        return toutes.stream()
                .filter(c -> c.getStatut() != StatutCompetence.REJETEE)
                .filter(c -> c.getStatut() == StatutCompetence.VALIDEE ||
                        (c.getStatut() == StatutCompetence.EN_ATTENTE &&
                         c.getProposeePar() != null &&
                         utilisateurId != null &&
                         c.getProposeePar().getId().equals(utilisateurId)))
                .filter(c -> matchQuery(c, qNormalise))
                .sorted(Comparator.comparingInt(c -> scoreMatch(c, qNormalise)))
                .limit(8)
                .collect(Collectors.toList());
    }

    public static boolean matchQuery(Competence c, String qNormalise) {
        if (c.getNomNormalise() != null && c.getNomNormalise().contains(qNormalise)) {
            return true;
        }
        if (c.getSynonymes() != null) {
            for (String syn : c.getSynonymes()) {
                if (syn != null) {
                    String synNorm = Competence.normaliserNom(syn);
                    if (synNorm != null && synNorm.contains(qNormalise)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static int scoreMatch(Competence c, String qNormalise) {
        if (c.getNomNormalise() != null && c.getNomNormalise().equals(qNormalise)) {
            return 0;
        }
        if (c.getSynonymes() != null) {
            for (String syn : c.getSynonymes()) {
                if (syn != null && qNormalise.equals(Competence.normaliserNom(syn))) {
                    return 0;
                }
            }
        }
        if (c.getNomNormalise() != null && c.getNomNormalise().startsWith(qNormalise)) {
            return 1;
        }
        if (c.getSynonymes() != null) {
            for (String syn : c.getSynonymes()) {
                if (syn != null) {
                    String synNorm = Competence.normaliserNom(syn);
                    if (synNorm != null && synNorm.startsWith(qNormalise)) {
                        return 1;
                    }
                }
            }
        }
        return 2;
    }
}
