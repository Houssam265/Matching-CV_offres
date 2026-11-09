package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.AdminOffreDto;
import com.gi3.matchingcv.model.Offre;
import com.gi3.matchingcv.model.enums.StatutOffre;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.CandidatureRepository;
import com.gi3.matchingcv.repository.OffreRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Implémentation du service admin pour la gestion des offres.
 * // TODO sécurité : toutes les méthodes sont réservées ADMIN.
 */
@Service
@Transactional
public class AdminOffreServiceImpl implements AdminOffreService {

    private final OffreRepository offreRepository;
    private final CandidatureRepository candidatureRepository;
    private final NotificationService notificationService;
    private final AuditService auditService;

    public AdminOffreServiceImpl(
            OffreRepository offreRepository,
            CandidatureRepository candidatureRepository,
            NotificationService notificationService,
            AuditService auditService
    ) {
        this.offreRepository = offreRepository;
        this.candidatureRepository = candidatureRepository;
        this.notificationService = notificationService;
        this.auditService = auditService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AdminOffreDto> listerOffres(StatutOffre statut, String domaine, String query) {
        List<Offre> toutes = offreRepository.findAll();

        return toutes.stream()
                .filter(o -> statut == null || o.getStatut() == statut)
                .filter(o -> domaine == null || domaine.isBlank() || (o.getDomaine() != null && o.getDomaine().equalsIgnoreCase(domaine.trim())))
                .filter(o -> {
                    if (query == null || query.isBlank()) return true;
                    String q = query.trim().toLowerCase(Locale.ROOT);
                    String titre = o.getTitre() != null ? o.getTitre().toLowerCase(Locale.ROOT) : "";
                    String desc = o.getDescription() != null ? o.getDescription().toLowerCase(Locale.ROOT) : "";
                    String loc = o.getLocalisation() != null ? o.getLocalisation().toLowerCase(Locale.ROOT) : "";
                    String entr = (o.getRecruteur() != null && o.getRecruteur().getNomEntreprise() != null)
                            ? o.getRecruteur().getNomEntreprise().toLowerCase(Locale.ROOT) : "";
                    return titre.contains(q) || desc.contains(q) || loc.contains(q) || entr.contains(q);
                })
                .sorted(Comparator.comparing(Offre::getDatePublication, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(o -> {
                    long nbCand = candidatureRepository.countByOffreId(o.getId());
                    return AdminOffreDto.from(o, nbCand);
                })
                .toList();
    }

    @Override
    public void cloturerOffre(Long offreId, String motif, Long adminId) {
        Offre offre = offreRepository.findById(offreId)
                .orElseThrow(() -> new EntityNotFoundException("Offre introuvable : " + offreId));

        offre.setStatut(StatutOffre.CLOTUREE);
        offreRepository.save(offre);

        // Notifier le recruteur
        if (offre.getRecruteur() != null) {
            String message = "Votre offre « " + offre.getTitre() + " » a été clôturée par l'administrateur.";
            if (motif != null && !motif.isBlank()) {
                message += " Motif : " + motif.trim();
            }
            notificationService.notifier(
                    offre.getRecruteur().getId(),
                    TypeNotification.CANDIDATURE_REFUSEE, // notification d'alerte
                    "Offre clôturée",
                    message,
                    "/mes-offres.html",
                    null, offreId, null, null, adminId
            );
        }

        auditService.journaliser(adminId, "CLOTURER_OFFRE",
                "Offre #" + offreId + " '" + offre.getTitre() + "'",
                motif != null && !motif.isBlank() ? motif.trim() : null);
    }
}
