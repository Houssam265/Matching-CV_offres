package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service métier pour la gestion des expériences professionnelles.
 * Respecte l'architecture en couches et le principe d'Inversion de Contrôle (IoC).
 */
@Service
@Transactional
public class ExperienceProfessionnelleServiceImpl implements ExperienceProfessionnelleService {

    private final ExperienceProfessionnelleRepository experienceRepository;

    /**
     * Injection par constructeur (IoC).
     */
    public ExperienceProfessionnelleServiceImpl(ExperienceProfessionnelleRepository experienceRepository) {
        this.experienceRepository = experienceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ExperienceProfessionnelle> listerParEtudiant(Long etudiantId) {
        return experienceRepository.findByEtudiantId(etudiantId);
    }

    @Override
    public ExperienceProfessionnelle creer(ExperienceProfessionnelle experience) {
        return experienceRepository.save(experience);
    }

    @Override
    public void supprimer(Long etudiantId, Long experienceId) {
        ExperienceProfessionnelle experience = experienceRepository.findById(experienceId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Expérience introuvable avec l'identifiant : " + experienceId));
        if (!experience.getEtudiant().getId().equals(etudiantId)) {
            throw new EntityNotFoundException("Cette expérience n'appartient pas à l'étudiant " + etudiantId);
        }
        experienceRepository.delete(experience);
    }
}
