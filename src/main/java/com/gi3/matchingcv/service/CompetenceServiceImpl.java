package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.OffreCompetenceRepository;
import com.gi3.matchingcv.repository.ProfilCompetenceRepository;
import jakarta.persistence.EntityNotFoundException;
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

    /**
     * Injection par constructeur (IoC) : la classe déclare explicitement ses dépendances,
     * et le conteneur Spring IoC les instancie et les injecte automatiquement.
     */
    public CompetenceServiceImpl(
            CompetenceRepository competenceRepository,
            ProfilCompetenceRepository profilCompetenceRepository,
            OffreCompetenceRepository offreCompetenceRepository
    ) {
        this.competenceRepository = competenceRepository;
        this.profilCompetenceRepository = profilCompetenceRepository;
        this.offreCompetenceRepository = offreCompetenceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competence> listerToutes() {
        return competenceRepository.findAll();
    }

    @Override
    public Competence creer(Competence competence) {
        if (competence.getStatut() == null) {
            competence.setStatut(StatutCompetence.VALIDEE);
        }
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
        competenceRepository.delete(competence);
    }
}
