package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAModererDto;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.exception.CompetenceDejaTraiteeException;
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
    private final AuditService auditService;
    private final NotificationService notificationService;
    private final CompetenceService competenceService;
    private final OffreCompetenceRepository offreCompetenceRepository;
    private final ProfilCompetenceRepository profilCompetenceRepository;
    private final EntityManager entityManager;

    @Value("${app.propositions.limite:10}")
    private int propositionsLimite;

    public AdminCompetenceServiceImpl(
            CompetenceRepository competenceRepository,
            AuditService auditService,
            NotificationService notificationService,
            CompetenceService competenceService,
            OffreCompetenceRepository offreCompetenceRepository,
            ProfilCompetenceRepository profilCompetenceRepository,
            EntityManager entityManager
    ) {
        this.competenceRepository = competenceRepository;
        this.auditService = auditService;
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
        return listerAModerer(null, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CompetenceAModererDto> listerAModerer(com.gi3.matchingcv.model.enums.Role role, String recherche) {
        List<Competence> enAttente = competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE);
        // Trier de la plus ancienne à la plus récente
        enAttente.sort(Comparator.comparing(
                (Competence c) -> c.getDateProposition() != null ? c.getDateProposition() : (c.getDateCreation() != null ? c.getDateCreation() : java.time.LocalDateTime.MIN)
        ).thenComparing(c -> c.getId() != null ? c.getId() : 0L));

        if (role != null) {
            enAttente = enAttente.stream()
                    .filter(c -> c.getProposeePar() != null && c.getProposeePar().getRole() == role)
                    .collect(Collectors.toList());
        }

        if (recherche != null && !recherche.isBlank()) {
            String q = recherche.trim().toLowerCase();
            String qNorm = Competence.normaliserNom(q);
            enAttente = enAttente.stream().filter(c -> {
                boolean nomMatch = c.getNom() != null && c.getNom().toLowerCase().contains(q);
                boolean catMatch = c.getCategorie() != null && c.getCategorie().toLowerCase().contains(q);
                boolean normMatch = qNorm != null && c.getNomNormalise() != null && c.getNomNormalise().contains(qNorm);
                boolean synMatch = c.getSynonymes() != null && c.getSynonymes().stream().anyMatch(s -> s.toLowerCase().contains(q));
                return nomMatch || catMatch || normMatch || synMatch;
            }).collect(Collectors.toList());
        }

        List<Competence> validees = competenceRepository.findByStatut(StatutCompetence.VALIDEE);

        return enAttente.stream().map(c -> {
            long usages = compterUsages(c.getId());
            List<String> doublons = trouverDoublonsPotentiels(c, validees);
            return CompetenceAModererDto.from(c, usages, doublons);
        }).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public long countAModerer() {
        return competenceRepository.countByStatut(StatutCompetence.EN_ATTENTE);
    }

    /** Compte toutes les utilisations d'une compétence dans les tables de jointure. */
    private long compterUsages(Long competenceId) {
        long n = 0;
        for (String table : List.of("etudiant_competence", "projet_competence",
                "experience_competence", "offre_competences", "profil_competences")) {
            try {
                Number r = (Number) entityManager
                        .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE competence_id = :id")
                        .setParameter("id", competenceId)
                        .getSingleResult();
                if (r != null) n += r.longValue();
            } catch (Exception e) {
                log.debug("Impossible de compter les usages dans {} : {}", table, e.getMessage());
            }
        }
        return n;
    }

    /**
     * Détecte les compétences VALIDEE dont le nomNormalise contient ou est contenu
     * par le nomNormalise de la compétence candidate, ou partagent le même début,
     * ou égalité avec un synonyme existant (doublon probable).
     */
    private List<String> trouverDoublonsPotentiels(Competence candidate, List<Competence> validees) {
        String cn = candidate.getNomNormalise();
        if (cn == null || cn.isBlank()) return List.of();
        String cNom = candidate.getNom() != null ? candidate.getNom().trim().toLowerCase() : "";

        return validees.stream()
                .filter(v -> v.getNomNormalise() != null)
                .filter(v -> !v.getId().equals(candidate.getId()))
                .filter(v -> {
                    String vn = v.getNomNormalise();
                    // 1. Égalité ou inclusion réciproque
                    if (vn.equals(cn) || vn.contains(cn) || cn.contains(vn)) {
                        return true;
                    }
                    // 2. Même début (préfixe commun d'au moins 3-4 caractères)
                    if (cn.length() >= 3 && vn.length() >= 3) {
                        String prefixC = cn.substring(0, Math.min(4, cn.length()));
                        String prefixV = vn.substring(0, Math.min(4, vn.length()));
                        if (cn.startsWith(prefixV) || vn.startsWith(prefixC)) {
                            return true;
                        }
                    }
                    // 3. Égalité avec un synonyme existant
                    if (v.getSynonymes() != null) {
                        for (String syn : v.getSynonymes()) {
                            if (syn != null && !syn.isBlank()) {
                                String synNorm = Competence.normaliserNom(syn);
                                if (synNorm != null && (synNorm.equals(cn) || syn.trim().equalsIgnoreCase(cNom))) {
                                    return true;
                                }
                            }
                        }
                    }
                    return levenshtein(cn, vn) <= 2;
                })
                .map(Competence::getNom)
                .distinct()
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

        if (c.getStatut() != StatutCompetence.EN_ATTENTE) {
            throw new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée.");
        }

        if (nom != null && !nom.isBlank()) {
            String nomTrim = nom.trim();
            String norm = Competence.normaliserNom(nomTrim);
            if (!nomTrim.equalsIgnoreCase(c.getNom())) {
                Optional<Competence> existante = competenceRepository.findByNomNormalise(norm);
                if (existante.isPresent() && !existante.get().getId().equals(id)) {
                    throw new CompetenceDejaExistanteException(
                            "Une compétence existe déjà avec ce nom (« " + existante.get().getNom() + " »). Vous pouvez fusionner cette proposition avec la compétence existante."
                    );
                }
            }
            c.setNom(nomTrim);
            c.setNomNormalise(norm);
        }
        if (categorie != null) {
            c.setCategorie(categorie.isBlank() ? null : categorie.trim());
        }
        if (synonymes != null) {
            c.setSynonymes(synonymes.stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).collect(Collectors.toList()));
        }
        competenceRepository.save(c);

        // Délègue à CompetenceService pour déclencher l'événement et la notification à l'auteur
        Competence saved = competenceService.changerStatut(id, StatutCompetence.VALIDEE);
        auditService.journaliser(adminId, "VALIDER_COMPETENCE", "Compétence #" + id + " '" + saved.getNom() + "'", null);
        return saved;
    }

    @Override
    public Competence rejeterAvecMotif(Long id, String motif, Long adminId) {
        Competence c = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence introuvable : " + id));

        if (c.getStatut() != StatutCompetence.EN_ATTENTE) {
            throw new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée.");
        }

        if (motif != null && !motif.isBlank()) {
            c.setMotifRejet(motif.trim());
            competenceRepository.save(c);
        }

        // Délègue à CompetenceService pour déclencher l'événement et la notification à l'auteur
        Competence saved = competenceService.changerStatut(id, StatutCompetence.REJETEE, motif);
        String details = (motif != null && !motif.isBlank()) ? motif : null;
        auditService.journaliser(adminId, "REJETER_COMPETENCE",
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

        if (source.getStatut() != StatutCompetence.EN_ATTENTE) {
            throw new CompetenceDejaTraiteeException("Cette compétence a déjà été traitée.");
        }
        if (cible.getStatut() != StatutCompetence.VALIDEE) {
            throw new IllegalArgumentException("La compétence cible doit être validée (ni en attente ni rejetée).");
        }

        // 1. Re-pointer les tables many-to-many (etudiant_competence, projet_competence, experience_competence)
        for (String table : List.of("etudiant_competence", "projet_competence", "experience_competence")) {
            String col = table.equals("etudiant_competence") ? "etudiant_id" :
                         table.equals("projet_competence")   ? "projet_id"   : "experience_id";

            // Supprimer les doublons pour la cible
            entityManager.createNativeQuery(
                    "DELETE FROM " + table + " WHERE competence_id = :source AND " + col + " IN (" +
                    "SELECT " + col + " FROM (SELECT " + col + " FROM " + table + " WHERE competence_id = :cible) t)"
            ).setParameter("source", sourceId).setParameter("cible", cibleId).executeUpdate();

            // Re-pointer les lignes restantes vers la cible
            entityManager.createNativeQuery(
                    "UPDATE " + table + " SET competence_id = :cible WHERE competence_id = :source"
            ).setParameter("cible", cibleId).setParameter("source", sourceId).executeUpdate();
        }

        // 2. Re-pointer profil_competences (supprime doublons via repo)
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

        // 3. Re-pointer offre_competences (REQUISE prioritaire sur ATOUT en cas de doublon, sans doublon)
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

        // 4. Ajouter l'ancien nom (et ses synonymes) dans les synonymes de la cible
        List<String> syns = new ArrayList<>(cible.getSynonymes() != null ? cible.getSynonymes() : List.of());
        if (!syns.contains(source.getNom())) {
            syns.add(source.getNom());
        }
        if (source.getSynonymes() != null) {
            for (String s : source.getSynonymes()) {
                if (s != null && !s.isBlank() && !syns.contains(s) && !s.equalsIgnoreCase(cible.getNom())) {
                    syns.add(s);
                }
            }
        }
        cible.setSynonymes(syns);
        competenceRepository.save(cible);

        // 5. Notifier l'auteur de la compétence source ("fusionnée avec X")
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
        entityManager.flush();

        // 7. Journaliser
        auditService.journaliser(adminId, "FUSIONNER_COMPETENCE",
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
            c.setNomNormalise(Competence.normaliserNom(nom.trim()));
        }
        if (categorie != null) {
            c.setCategorie(categorie.isBlank() ? null : categorie.trim());
        }
        if (synonymes != null) {
            c.setSynonymes(synonymes.stream().filter(s -> s != null && !s.isBlank())
                    .map(String::trim).collect(Collectors.toList()));
        }
        Competence saved = competenceRepository.save(c);
        auditService.journaliser(adminId, "MODIFIER_COMPETENCE",
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
        auditService.journaliser(adminId, "IMPORT_CSV", "CSV compétences",
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
