package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AdminCreationRequest;
import com.gi3.matchingcv.dto.UtilisateurAdminDto;
import com.gi3.matchingcv.dto.UtilisateurDetailDto;
import com.gi3.matchingcv.model.Administrateur;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.Recruteur;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.security.Passwords;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Implémentation du service admin pour la gestion des utilisateurs.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
@Service
@Transactional
public class AdminUtilisateurServiceImpl implements AdminUtilisateurService {

    private final UtilisateurRepository utilisateurRepository;
    private final AdministrateurRepository administrateurRepository;
    private final EtudiantRepository etudiantRepository;
    private final RecruteurRepository recruteurRepository;
    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final ProjetRepository projetRepository;
    private final AuditService auditService;

    public AdminUtilisateurServiceImpl(
            UtilisateurRepository utilisateurRepository,
            AdministrateurRepository administrateurRepository,
            EtudiantRepository etudiantRepository,
            RecruteurRepository recruteurRepository,
            OffreRepository offreRepository,
            CandidatureRepository candidatureRepository,
            ProjetRepository projetRepository,
            AuditService auditService
    ) {
        this.utilisateurRepository = utilisateurRepository;
        this.administrateurRepository = administrateurRepository;
        this.etudiantRepository = etudiantRepository;
        this.recruteurRepository = recruteurRepository;
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.projetRepository = projetRepository;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UtilisateurAdminDto> listerUtilisateurs(Role role, Boolean actif, String query) {
        List<Utilisateur> tous = utilisateurRepository.findAll();

        return tous.stream()
                .filter(u -> role == null || u.getRole() == role)
                .filter(u -> actif == null || u.isActif() == actif)
                .filter(u -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.trim().toLowerCase(Locale.ROOT);
                    String nomComplet = ((u.getPrenom() != null ? u.getPrenom() : "") + " " +
                                         (u.getNom() != null ? u.getNom() : "")).toLowerCase(Locale.ROOT);
                    String email = u.getEmail() != null ? u.getEmail().toLowerCase(Locale.ROOT) : "";
                    String org = "";
                    if (u instanceof Etudiant e && e.getFiliere() != null) org = e.getFiliere().toLowerCase(Locale.ROOT);
                    if (u instanceof Recruteur r && r.getNomEntreprise() != null) org = r.getNomEntreprise().toLowerCase(Locale.ROOT);
                    return nomComplet.contains(q) || email.contains(q) || org.contains(q);
                })
                .sorted(Comparator.comparing(Utilisateur::getDateInscription, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(UtilisateurAdminDto::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UtilisateurDetailDto obtenirDetail(Long id) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + id));

        String filiere = null;
        String etablissement = null;
        long nbCompetences = 0;
        long nbProjets = 0;
        long nbCandidatures = 0;

        String nomEntreprise = null;
        String secteurActivite = null;
        long nbOffres = 0;
        long nbCandidaturesRecues = 0;

        if (u instanceof Etudiant e) {
            filiere = e.getFiliere();
            etablissement = e.getEtablissement();
            nbCompetences = e.getCompetences() != null ? e.getCompetences().size() : 0;
            nbProjets = projetRepository.findByEtudiantId(e.getId()).size();
            nbCandidatures = candidatureRepository.countByEtudiantId(e.getId());
        } else if (u instanceof Recruteur r) {
            nomEntreprise = r.getNomEntreprise();
            secteurActivite = r.getSecteurActivite();
            nbOffres = offreRepository.findByRecruteurIdOrderByDatePublicationDesc(r.getId()).size();
            nbCandidaturesRecues = candidatureRepository.countByRecruteurId(r.getId());
        }

        return new UtilisateurDetailDto(
                u.getId(),
                u.getNom(),
                u.getPrenom(),
                u.getEmail(),
                u.getRole(),
                u.isActif(),
                u.getDateInscription(),
                filiere,
                etablissement,
                nbCompetences,
                nbProjets,
                nbCandidatures,
                nomEntreprise,
                secteurActivite,
                nbOffres,
                nbCandidaturesRecues
        );
    }

    @Override
    public void changerStatut(Long id, boolean actif, Long adminId) {
        Utilisateur u = utilisateurRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Utilisateur introuvable : " + id));

        u.setActif(actif);
        u.setActive(actif);
        utilisateurRepository.save(u);

        String action = actif ? "REACTIVER_UTILISATEUR" : "SUSPENDRE_UTILISATEUR";
        String cible = u.getRole() + " #" + id + " (" + u.getPrenom() + " " + u.getNom() + " - " + u.getEmail() + ")";
        auditService.journaliser(adminId, action, cible, "Statut actif défini à " + actif);
    }

    @Override
    public Administrateur creerAdmin(AdminCreationRequest request, Long adminId) {
        if (request == null) {
            throw new IllegalArgumentException("Les données de l'administrateur sont obligatoires.");
        }
        String email = request.email() != null ? request.email().trim().toLowerCase(Locale.ROOT) : "";
        if (email.isBlank() || utilisateurRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Un utilisateur avec cet email existe déjà : " + request.email());
        }

        Administrateur admin = new Administrateur();
        admin.setPrenom(request.prenom().trim());
        admin.setNom(request.nom().trim());
        admin.setEmail(email);
        admin.setMotDePasse(Passwords.hash(request.motDePasse()));
        admin.setRole(Role.ADMIN);
        admin.setActif(true);
        admin.setActive(true);

        Administrateur saved = administrateurRepository.save(admin);
        auditService.journaliser(adminId, "CREER_ADMIN",
                "Admin #" + saved.getId() + " (" + saved.getPrenom() + " " + saved.getNom() + " - " + saved.getEmail() + ")",
                "Création manuelle par administrateur");

        return saved;
    }
}
