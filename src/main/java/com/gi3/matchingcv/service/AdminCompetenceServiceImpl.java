package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAModererDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.model.enums.TypeExigence;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation du service admin pour la gestion des compétences.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
@Service
@Transactional
public class AdminCompetenceServiceImpl implements AdminCompetenceService {

    private static final Logger log = LoggerFactory.getLogger(AdminCompetenceServiceImpl.class);

    private final CompetenceRepository competenceRepository;
    private final AdministrateurRepository administrateurRepository;
    private final AuditLogRepository auditLogRepository;
    private final NotificationService notificationService;
    private final CompetenceService competenceService;
    private final OffreCompetenceRepository offreCompetenceRepository;
    private final ProfilCompetenceRepository profilCompetenceRepository;
    private final EntityManager entityManager;

    @Value("${app.propositions.limite:10}")
    private int propositionsLimite;

    public AdminCompetenceServiceImpl(
            CompetenceRepository competenceRepository,
            AdministrateurRepository administrateurRepository,
            AuditLogRepository auditLogRepository,
            NotificationService notificationService,
            CompetenceService competenceService,
            OffreCompetenceRepository offreCompetenceRepository,
            ProfilCompetenceRepository profilCompetenceRepository,
            EntityManager entityManager
    ) {
        this.competenceRepository = competenceRepository;
        this.administrateurRepository = administrateurRepository;
        this.auditLogRepository = auditLogRepository;
        this.notificationService = notificationService;
        this.competenceService = competenceService;
        this.offreCompetenceRepository = offreCompetenceRepository;
        this.profilCompetenceRepository = profilCompetenceRepository;
        this.entityManager = entityManager;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FILE DE MODÉRATION
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<CompetenceAModererDto> listerAModerer() {
        List<Competence> enAttente = competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE);
        List<Competence> validees  = competenceRepository.findByStatut(StatutCompetence.VALIDEE);

        return enAttente.stream().map(c -> {
            long usages = compterUsages(c.getId());
            List<String> doublons = trouverDoublonsPotentiels(c, validees);
            return CompetenceAModererDto.from(c, usages, doublons);
        }).collect(Collectors.toList());
    }

    /** Compte toutes les utilisations d'une compétence dans les tables de jointure. */
    private long compterUsages(Long competenceId) {
        long n = 0;
        for (String table : List.of("etudiant_competence", "projet_competence",
                "experience_competence", "offre_competences", "profil_competences")) {
            Number r = (Number) entityManager
                    .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE competence_id = :id")
                    .setParameter("id", competenceId)
                    .getSingleResult();
            if (r != null) n += r.longValue();
        }
        return n;
    }

    /**
     * Détecte les compétences VALIDEE dont le nomNormalise contient ou est contenu
     * par le nomNormalise de la compétence candidate (doublon probable).
     */
    private List<String> trouverDoublonsPotentiels(Competence candidate, List<Competence> validees) {
        String cn = candidate.getNomNormalise();
        if (cn == null) return List.of();
        return validees.stream()
                .filter(v -> v.getNomNormalise() != null)
                .filter(v -> !v.getId().equals(candidate.getId()))
                .filter(v -> {
                    String vn = v.getNomNormalise();
                    return vn.equals(cn) || vn.contains(cn) || cn.contains(vn)
                            || levenshtein(cn, vn) <= 2;
                })
                .map(Competence::getNom)
                .limit(5)
                .collect(Collectors.toList());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // VALIDATION / REJET
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Competence validerAvecModifications(Long id, String nom, String categorie,
                                               List<String> synonymes, Long adminId) {
        Competence c = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable : " + id));
        if (nom != null && !nom.isBlank()) {
            c.setNom(nom.trim());
        }
        if (categorie != null) {
            c.setCategorie(categorie.isBlank() ? null : categorie.trim());
        }
        if (synonymes != null) {
            c.setSynonymes(synonymes.stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).collect(Collectors.toList()));
        }
        // Délègue à CompetenceService pour déclencher l'événement et la notification à l'auteur
        Competence saved = competenceService.changerStatut(id, StatutCompetence.VALIDEE);
        journaliser(adminId, "VALIDER_COMPETENCE", "Compétence #" + id + " '" + saved.getNom() + "'", null);
        return saved;
    }

    @Override
    public Competence rejeterAvecMotif(Long id, String motif, Long adminId) {
        Competence c = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable : " + id));
        // Délègue à CompetenceService pour déclencher l'événement et la notification à l'auteur
        Competence saved = competenceService.changerStatut(id, StatutCompetence.REJETEE);
        String details = (motif != null && !motif.isBlank()) ? motif : null;
        journaliser(adminId, "REJETER_COMPETENCE",
                "Compétence #" + id + " '" + c.getNom() + "'", details);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // FUSION
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void fusionner(Long sourceId, Long cibleId, Long adminId) {
        if (sourceId.equals(cibleId)) {
            throw new IllegalArgumentException("La source et la cible sont identiques.");
        }
        Competence source = competenceRepository.findById(sourceId)
                .orElseThrow(() -> new EntityNotFoundException("Compétence source introuvable : " + sourceId));
        Competence cible  = competenceRepository.findById(cibleId)
                .orElseThrow(() -> new EntityNotFoundException("Compétence cible introuvable : " + cibleId));

        // 1. Re-pointer les tables many-to-many simples (sans contrainte unique problématique)
        for (String table : List.of("etudiant_competence", "projet_competence", "experience_competence")) {
            // Supprimer les doublons éventuels avant la mise à jour
            entityManager.createNativeQuery(
                    "DELETE FROM " + table + " WHERE competence_id = :source " +
                    "AND EXISTS (SELECT 1 FROM (SELECT * FROM " + table + ") t2 " +
                    "WHERE t2.competence_id = :cible AND t2." +
                    (table.equals("etudiant_competence") ? "etudiant_id" :
                     table.equals("projet_competence")   ? "projet_id"   : "experience_id") +
                    " = " + table + "." +
                    (table.equals("etudiant_competence") ? "etudiant_id" :
                     table.equals("projet_competence")   ? "projet_id"   : "experience_id") + ")"
            ).setParameter("source", sourceId).setParameter("cible", cibleId).executeUpdate();

            entityManager.createNativeQuery(
                    "UPDATE " + table + " SET competence_id = :cible WHERE competence_id = :source"
            ).setParameter("cible", cibleId).setParameter("source", sourceId).executeUpdate();
        }

        // 2. Re-pointer profil_competences (entité JPA — supprime doublons via repo)
        List<ProfilCompetence> profilsSrc = profilCompetenceRepository.findByCompetenceId(sourceId);
        for (ProfilCompetence pc : profilsSrc) {
            boolean existeDeja = profilCompetenceRepository.findByCompetenceId(cibleId).stream()
                    .anyMatch(p -> p.getEtudiant().getId().equals(pc.getEtudiant().getId()));
            if (!existeDeja) {
                pc.setCompetence(cible);
                profilCompetenceRepository.save(pc);
            } else {
                profilCompetenceRepository.delete(pc);
            }
        }

        // 3. Re-pointer offre_competences (REQUISE prioritaire sur ATOUT en cas de doublon)
        List<OffreCompetence> offresSrc = offreCompetenceRepository.findByCompetenceId(sourceId);
        List<OffreCompetence> offresCible = offreCompetenceRepository.findByCompetenceId(cibleId);
        for (OffreCompetence oc : offresSrc) {
            Optional<OffreCompetence> doublon = offresCible.stream()
                    .filter(x -> x.getOffre().getId().equals(oc.getOffre().getId()))
                    .findFirst();
            if (doublon.isPresent()) {
                // Garde REQUISE si l'une des deux l'est
                if (oc.getTypeExigence() == TypeExigence.REQUISE
                        && doublon.get().getTypeExigence() != TypeExigence.REQUISE) {
                    doublon.get().setTypeExigence(TypeExigence.REQUISE);
                    offreCompetenceRepository.save(doublon.get());
                }
                offreCompetenceRepository.delete(oc);
            } else {
                oc.setCompetence(cible);
                offreCompetenceRepository.save(oc);
            }
        }

        // 4. Ajouter l'ancien nom dans les synonymes de la cible
        List<String> syns = new ArrayList<>(cible.getSynonymes() != null ? cible.getSynonymes() : List.of());
        if (!syns.contains(source.getNom())) {
            syns.add(source.getNom());
        }
        cible.setSynonymes(syns);
        competenceRepository.save(cible);

        // 5. Notifier l'auteur de la compétence source (si elle en a un)
        if (source.getProposeePar() != null) {
            notificationService.notifier(
                    source.getProposeePar().getId(),
                    TypeNotification.COMPETENCE_VALIDEE,
                    "Compétence fusionnée",
                    "Votre compétence « " + source.getNom() + " » a été fusionnée avec « " + cible.getNom() + " ».",
                    "/competences",
                    null, null, cibleId, null, adminId
            );
        }

        // 6. Supprimer la source
        competenceRepository.delete(source);

        // 7. Journaliser
        journaliser(adminId, "FUSIONNER_COMPETENCE",
                "Compétence #" + sourceId + " '" + source.getNom() + "' → #" + cibleId + " '" + cible.getNom() + "'",
                null);
        log.info("Compétence #{} '{}' fusionnée dans #{} '{}'", sourceId, source.getNom(), cibleId, cible.getNom());
    }

    // ──────────────────────────────────────────────────────────────────────────
    // ACTIONS GROUPÉES
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public void validerGroupes(List<Long> ids, Long adminId) {
        for (Long id : ids) {
            try {
                validerAvecModifications(id, null, null, null, adminId);
            } catch (Exception e) {
                log.warn("Impossible de valider la compétence #{} : {}", id, e.getMessage());
            }
        }
    }

    @Override
    public void rejeterGroupes(List<Long> ids, String motif, Long adminId) {
        for (Long id : ids) {
            try {
                rejeterAvecMotif(id, motif, adminId);
            } catch (Exception e) {
                log.warn("Impossible de rejeter la compétence #{} : {}", id, e.getMessage());
            }
        }
    }

    // ──────────────────────────────────────────────────────────────────────────
    // MODIFICATION
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Competence modifier(Long id, String nom, String categorie, List<String> synonymes, Long adminId) {
        Competence c = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable : " + id));
        String ancienNom = c.getNom();
        if (nom != null && !nom.isBlank()) {
            c.setNom(nom.trim());
        }
        if (categorie != null) {
            c.setCategorie(categorie.isBlank() ? null : categorie.trim());
        }
        if (synonymes != null) {
            c.setSynonymes(synonymes.stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).collect(Collectors.toList()));
        }
        Competence saved = competenceRepository.save(c);
        journaliser(adminId, "MODIFIER_COMPETENCE",
                "Compétence #" + id + " '" + ancienNom + "'",
                nom != null ? "Nouveau nom : " + nom : null);
        return saved;
    }

    // ──────────────────────────────────────────────────────────────────────────
    // IMPORT / EXPORT CSV
    // ──────────────────────────────────────────────────────────────────────────

    @Override
    public Map<String, Integer> importerCsv(InputStream csv, Long adminId) throws Exception {
        int creees = 0, ignorees = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(csv, StandardCharsets.UTF_8))) {
            String line;
            boolean firstLine = true;
            while ((line = reader.readLine()) != null) {
                if (firstLine) { firstLine = false; continue; }
                if (line.isBlank()) continue;
                String[] parts = line.split(";", -1);
                if (parts.length < 1) continue;
                String nom = parts[0].trim();
                if (nom.isEmpty()) continue;
                String categorie = parts.length > 1 && !parts[1].trim().isEmpty() ? parts[1].trim() : null;
                List<String> syns = new ArrayList<>();
                if (parts.length > 2 && !parts[2].trim().isEmpty()) {
                    syns = Arrays.stream(parts[2].split("\\|"))
                            .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
                }
                String nomNorm = Competence.normaliserNom(nom);
                if (nomNorm == null || nomNorm.isBlank()) continue;
                if (competenceRepository.findByNomNormalise(nomNorm).isPresent()) {
                    ignorees++;
                } else {
                    Competence c = new Competence();
                    c.setNom(nom);
                    c.setNomNormalise(nomNorm);
                    c.setCategorie(categorie);
                    c.setStatut(StatutCompetence.VALIDEE);
                    c.setSynonymes(syns);
                    competenceRepository.save(c);
                    creees++;
                }
            }
        }
        journaliser(adminId, "IMPORT_CSV", "CSV compétences",
                creees + " créées, " + ignorees + " ignorées");
        return Map.of("creees", creees, "ignorees", ignorees);
    }

    @Override
    @Transactional(readOnly = true)
    public String exporterCsv() {
        StringBuilder sb = new StringBuilder("nom;categorie;synonymes\n");
        competenceRepository.findByStatut(StatutCompetence.VALIDEE).forEach(c -> {
            sb.append(escapeCsv(c.getNom())).append(";")
              .append(escapeCsv(c.getCategorie())).append(";")
              .append(c.getSynonymes() != null ? String.join("|", c.getSynonymes()) : "")
              .append("\n");
        });
        return sb.toString();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // UTILITAIRES PRIVÉS
    // ──────────────────────────────────────────────────────────────────────────

    private void journaliser(Long adminId, String action, String cible, String details) {
        Administrateur admin = adminId != null
                ? administrateurRepository.findById(adminId).orElse(null) : null;
        String adminNom = admin != null ? admin.getPrenom() + " " + admin.getNom() : "Système";
        AuditLog log = new AuditLog();
        log.setAdminId(adminId);
        log.setAdminNom(adminNom);
        log.setAction(action);
        log.setCible(cible);
        log.setDetails(details);
        auditLogRepository.save(log);
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.contains(";") ? "\"" + val.replace("\"", "\"\"") + "\"" : val;
    }

    /** Distance de Levenshtein bornée à 3 pour la détection de doublons proches. */
    private static int levenshtein(String a, String b) {
        if (Math.abs(a.length() - b.length()) > 3) return 99;
        int[] prev = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            int[] curr = new int[b.length() + 1];
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                curr[j] = a.charAt(i - 1) == b.charAt(j - 1) ? prev[j - 1]
                        : 1 + Math.min(prev[j - 1], Math.min(prev[j], curr[j - 1]));
            }
            prev = curr;
        }
        return prev[b.length()];
    }
}
