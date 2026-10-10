package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.model.Projet;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import com.gi3.matchingcv.repository.ProjetRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Migration unique et idempotente au démarrage :
 * Pour chaque étudiant, ajoute à sa liste personnelle (etudiant_competence)
 * toutes les compétences déjà attachées à ses projets et à ses expériences.
 */
@Component
@Order(2)
public class EtudiantCompetenceMigrationRunner implements CommandLineRunner {

    private final EtudiantRepository etudiantRepository;
    private final ProjetRepository projetRepository;
    private final ExperienceProfessionnelleRepository experienceRepository;

    public EtudiantCompetenceMigrationRunner(
            EtudiantRepository etudiantRepository,
            ProjetRepository projetRepository,
            ExperienceProfessionnelleRepository experienceRepository) {
        this.etudiantRepository = etudiantRepository;
        this.projetRepository = projetRepository;
        this.experienceRepository = experienceRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        List<Etudiant> etudiants = etudiantRepository.findAll();
        for (Etudiant etudiant : etudiants) {
            Set<Long> idsExistants = new HashSet<>();
            for (Competence c : etudiant.getCompetences()) {
                idsExistants.add(c.getId());
            }

            boolean modifie = false;

            List<Projet> projets = projetRepository.findByEtudiantId(etudiant.getId());
            for (Projet p : projets) {
                for (Competence c : p.getCompetences()) {
                    if (!idsExistants.contains(c.getId())) {
                        etudiant.getCompetences().add(c);
                        idsExistants.add(c.getId());
                        modifie = true;
                    }
                }
            }

            List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiant.getId());
            for (ExperienceProfessionnelle exp : experiences) {
                for (Competence c : exp.getCompetences()) {
                    if (!idsExistants.contains(c.getId())) {
                        etudiant.getCompetences().add(c);
                        idsExistants.add(c.getId());
                        modifie = true;
                    }
                }
            }

            if (modifie) {
                etudiantRepository.save(etudiant);
            }
        }
    }
}
