package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
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

    /**
     * Corrige au démarrage toute expérience historique dont la date de fin est antérieure à la date de début.
     */
    @PostConstruct
    public void corrigerExperiencesHistoriques() {
        try {
            List<ExperienceProfessionnelle> all = experienceRepository.findAll();
            for (ExperienceProfessionnelle exp : all) {
                if (exp.getDateDebut() != null && exp.getDateFin() != null && exp.getDateFin().isBefore(exp.getDateDebut())) {
                    LocalDate tmp = exp.getDateDebut();
                    exp.setDateDebut(exp.getDateFin());
                    exp.setDateFin(tmp);
                    experienceRepository.save(exp);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    @Transactional
    public List<ExperienceProfessionnelle> listerParEtudiant(Long etudiantId) {
        List<ExperienceProfessionnelle> list = experienceRepository.findByEtudiantId(etudiantId);
        for (ExperienceProfessionnelle exp : list) {
            if (exp.getDateDebut() != null && exp.getDateFin() != null && exp.getDateFin().isBefore(exp.getDateDebut())) {
                LocalDate tmp = exp.getDateDebut();
                exp.setDateDebut(exp.getDateFin());
                exp.setDateFin(tmp);
                experienceRepository.save(exp);
            }
        }
        return list;
    }

    @Override
    public ExperienceProfessionnelle creer(ExperienceProfessionnelle experience) {
        if (experience.getDateDebut() == null) {
            throw new IllegalArgumentException("La date de début est obligatoire.");
        }
        if (experience.getDateFin() != null && experience.getDateFin().isBefore(experience.getDateDebut())) {
            throw new IllegalArgumentException("La date de fin ne peut pas être antérieure à la date de début.");
        }
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
