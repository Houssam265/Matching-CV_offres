package com.gi3.matchingcv.service;

import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.OffreCompetenceRepository;
import com.gi3.matchingcv.repository.ProfilCompetenceRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
    public void supprimer(Long id) {
        Competence competence = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable avec l'identifiant : " + id));
        profilCompetenceRepository.deleteByCompetenceId(id);
        offreCompetenceRepository.deleteByCompetenceId(id);

        // Nettoyage manuel des tables de jointure pour éviter les erreurs de Foreign Key
        entityManager.createNativeQuery("DELETE FROM etudiant_competence WHERE competence_id = :id")
                .setParameter("id", id).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM projet_competence WHERE competence_id = :id")
                .setParameter("id", id).executeUpdate();
        entityManager.createNativeQuery("DELETE FROM experience_competence WHERE competence_id = :id")
                .setParameter("id", id).executeUpdate();

        competenceRepository.delete(competence);
    }

    @Override
    public Competence proposer(String nom, String categorie) {
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
        return competenceRepository.save(competence);
    }
}
