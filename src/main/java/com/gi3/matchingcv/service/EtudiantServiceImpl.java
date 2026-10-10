package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAttachRequest;
import com.gi3.matchingcv.dto.EtudiantCompetenceDTO;
import com.gi3.matchingcv.exception.CompetenceDejaDansListeException;
import com.gi3.matchingcv.exception.EmailDejaUtiliseException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.model.Projet;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import com.gi3.matchingcv.repository.ProjetRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implémentation du service métier pour la gestion des étudiants.
 * Respecte l'architecture en couches et le principe d'Inversion de Contrôle (IoC).
 */
@Service
@Transactional
public class EtudiantServiceImpl implements EtudiantService {

    private final EtudiantRepository etudiantRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final CompetenceRepository competenceRepository;
    private final CompetenceService competenceService;
    private final ProjetRepository projetRepository;
    private final ExperienceProfessionnelleRepository experienceRepository;

    /**
     * Injection par constructeur (IoC) : la classe déclare explicitement ses dépendances,
     * et le conteneur Spring IoC les instancie et les injecte automatiquement.
     */
    public EtudiantServiceImpl(
            EtudiantRepository etudiantRepository,
            UtilisateurRepository utilisateurRepository,
            CompetenceRepository competenceRepository,
            CompetenceService competenceService,
            ProjetRepository projetRepository,
            ExperienceProfessionnelleRepository experienceRepository
    ) {
        this.etudiantRepository = etudiantRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.competenceRepository = competenceRepository;
        this.competenceService = competenceService;
        this.projetRepository = projetRepository;
        this.experienceRepository = experienceRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Etudiant> listerTous() {
        return etudiantRepository.findAll();
    }

    @Override
    public Etudiant creer(Etudiant etudiant) {
        if (utilisateurRepository.existsByEmail(etudiant.getEmail())) {
            throw new EmailDejaUtiliseException(
                "Un compte existe déjà avec l'adresse e-mail : " + etudiant.getEmail()
            );
        }
        // TODO sécurité : le hachage BCrypt du mot de passe sera ajouté avec Spring Security
        etudiant.setRole(Role.ETUDIANT);
        return etudiantRepository.save(etudiant);
    }

    @Override
    @Transactional(readOnly = true)
    public Etudiant trouverParId(Long id) {
        return etudiantRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Étudiant introuvable avec l'identifiant : " + id));
    }

    @Override
    public Etudiant sauvegarder(Etudiant etudiant) {
        return etudiantRepository.save(etudiant);
    }

    @Override
    @Transactional(readOnly = true)
    public List<EtudiantCompetenceDTO> listerCompetencesAvecStats(Long etudiantId) {
        Etudiant etudiant = trouverParId(etudiantId);
        List<Projet> projets = projetRepository.findByEtudiantId(etudiantId);
        List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiantId);

        List<EtudiantCompetenceDTO> dtos = new ArrayList<>();
        for (Competence comp : etudiant.getCompetences()) {
            int nbProjets = (int) projets.stream()
                    .filter(p -> p.getCompetences().stream().anyMatch(c -> c.getId().equals(comp.getId())))
                    .count();
            int nbExperiences = (int) experiences.stream()
                    .filter(e -> e.getCompetences().stream().anyMatch(c -> c.getId().equals(comp.getId())))
                    .count();
            dtos.add(new EtudiantCompetenceDTO(
                    comp.getId(),
                    comp.getNom(),
                    comp.getCategorie(),
                    comp.getStatut(),
                    nbProjets,
                    nbExperiences
            ));
        }
        return dtos;
    }

    @Override
    public Competence ajouterCompetence(Long etudiantId, CompetenceAttachRequest request) {
        Etudiant etudiant = trouverParId(etudiantId);
        Competence competence;

        if (request.getCompetenceId() != null) {
            competence = competenceService.trouverParId(request.getCompetenceId());
        } else if (request.getNouvelleCompetence() != null && !request.getNouvelleCompetence().isBlank()) {
            String nomNettoye = request.getNouvelleCompetence().trim();
            String nomNormalise = Competence.normaliserNom(nomNettoye);

            // Vérifier immédiatement si l'étudiant possède déjà une compétence équivalente
            boolean dejaDansListe = etudiant.getCompetences().stream().anyMatch(c -> {
                String cNorm = c.getNomNormalise() != null ? c.getNomNormalise() : Competence.normaliserNom(c.getNom());
                return nomNormalise.equalsIgnoreCase(cNorm);
            });
            if (dejaDansListe) {
                throw new CompetenceDejaDansListeException("Cette compétence est déjà dans votre liste");
            }

            Optional<Competence> existanteOpt = competenceRepository.findByNomNormalise(nomNormalise);
            if (existanteOpt.isPresent()) {
                competence = existanteOpt.get();
            } else {
                competence = competenceService.proposer(nomNettoye, request.getCategorie(), etudiant);
            }
        } else {
            throw new IllegalArgumentException("Veuillez fournir un competenceId ou une nouvelleCompetence.");
        }

        boolean dejaPresente = etudiant.getCompetences().stream()
                .anyMatch(c -> (competence.getId() != null && competence.getId().equals(c.getId()))
                        || (competence.getNomNormalise() != null && competence.getNomNormalise().equalsIgnoreCase(c.getNomNormalise())));
        if (dejaPresente) {
            throw new CompetenceDejaDansListeException("Cette compétence est déjà dans votre liste");
        }

        etudiant.getCompetences().add(competence);
        etudiantRepository.save(etudiant);
        return competence;
    }

    @Override
    public void retirerCompetence(Long etudiantId, Long competenceId) {
        Etudiant etudiant = trouverParId(etudiantId);

        // 1. Retirer de la liste de l'étudiant
        etudiant.getCompetences().removeIf(c -> c.getId().equals(competenceId));
        etudiantRepository.save(etudiant);

        // 2. Détacher des projets de l'étudiant
        List<Projet> projets = projetRepository.findByEtudiantId(etudiantId);
        for (Projet p : projets) {
            if (p.getCompetences().removeIf(c -> c.getId().equals(competenceId))) {
                projetRepository.save(p);
            }
        }

        // 3. Détacher des expériences de l'étudiant
        List<ExperienceProfessionnelle> experiences = experienceRepository.findByEtudiantId(etudiantId);
        for (ExperienceProfessionnelle exp : experiences) {
            if (exp.getCompetences().removeIf(c -> c.getId().equals(competenceId))) {
                experienceRepository.save(exp);
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competence> suggererCompetences(Long etudiantId, String query) {
        Etudiant etudiant = trouverParId(etudiantId);
        if (query == null || query.isBlank()) {
            return Collections.emptyList();
        }
        String qNormalise = Competence.normaliserNom(query.trim());

        Set<Long> idsDansListe = etudiant.getCompetences().stream()
                .map(Competence::getId)
                .collect(Collectors.toSet());

        List<Competence> toutes = competenceRepository.findAll();
        return toutes.stream()
                .filter(c -> !idsDansListe.contains(c.getId()))
                .filter(c -> c.getStatut() != StatutCompetence.REJETEE)
                .filter(c -> c.getStatut() == StatutCompetence.VALIDEE ||
                        (c.getStatut() == StatutCompetence.EN_ATTENTE &&
                         c.getProposeePar() != null &&
                         c.getProposeePar().getId().equals(etudiantId)))
                .filter(c -> c.getNomNormalise() != null && c.getNomNormalise().contains(qNormalise))
                .limit(8)
                .collect(Collectors.toList());
    }
}
