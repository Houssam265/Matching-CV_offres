package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.event.*;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.EtudiantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationService notificationService;
    private final EtudiantRepository etudiantRepository;
    private final MatchingService matchingService;

    @Value("${app.notifications.seuil-offre:70}")
    private double seuilOffre;

    public NotificationListener(
            NotificationService notificationService,
            EtudiantRepository etudiantRepository,
            MatchingService matchingService
    ) {
        this.notificationService = notificationService;
        this.etudiantRepository = etudiantRepository;
        this.matchingService = matchingService;
    }

    /**
     * Traitement asynchrone lors de la publication d'une offre :
     * calcule le score pour chaque étudiant et notifie ceux qui atteignent le seuil.
     */
    @Async
    @EventListener
    public void onOffrePubliee(OffrePublieeEvent event) {
        log.info("Traitement asynchrone de l'offre publiée [{}] : {}", event.offreId(), event.titre());

        List<Etudiant> etudiants = etudiantRepository.findAll();
        for (Etudiant etudiant : etudiants) {
            try {
                MatchingDetailDto matching = matchingService.calculerScore(etudiant.getId(), event.offreId());
                if (matching != null && matching.scoreDisponible() && matching.score() != null) {
                    double score = matching.score();
                    if (score >= seuilOffre) {
                        long scoreArrondi = Math.round(score);
                        String titre = "Nouvelle offre correspondante : " + event.titre();
                        String message = String.format("Une nouvelle offre « %s » correspond à votre profil à %d%%.",
                                event.titre(), scoreArrondi);
                        String lien = "/offres-recommandees.html";

                        notificationService.notifier(
                                etudiant.getId(),
                                TypeNotification.OFFRE_CORRESPONDANTE,
                                titre,
                                message,
                                lien,
                                event.offreId(),
                                null,
                                null,
                                score,
                                event.recruteurId()
                        );
                    }
                }
            } catch (Exception e) {
                log.warn("Erreur calcul matching pour notification étudiant {} / offre {} : {}",
                        etudiant.getId(), event.offreId(), e.getMessage());
            }
        }
    }

    /**
     * Notifie le recruteur lors d'une nouvelle candidature.
     */
    @EventListener
    public void onCandidatureCreee(CandidatureCreeeEvent event) {
        String nomCandidat = ((event.etudiantPrenom() != null ? event.etudiantPrenom() : "") + " "
                + (event.etudiantNom() != null ? event.etudiantNom() : "")).trim();
        if (nomCandidat.isEmpty()) {
            nomCandidat = "Un candidat";
        }

        String titre = "Nouvelle candidature reçue";
        String message;

        if (event.score() != null) {
            long scoreArrondi = Math.round(event.score());
            String mention = (event.score() >= 80.0) ? " (très pertinent)" : "";
            message = String.format("%s a postulé à l'offre « %s » (Score : %d%%%s).",
                    nomCandidat, event.offreTitre(), scoreArrondi, mention);
        } else {
            message = String.format("%s a postulé à l'offre « %s ».", nomCandidat, event.offreTitre());
        }

        String lien = String.format(ConstantesNotification.LIEN_RECRUTEUR_CANDIDAT, event.offreId(), event.candidatureId());

        notificationService.notifier(
                event.recruteurId(),
                TypeNotification.NOUVELLE_CANDIDATURE,
                titre,
                message,
                lien,
                event.offreId(),
                event.candidatureId(),
                null,
                event.score(),
                event.etudiantId()
        );
    }

    /**
     * Notifie l'étudiant lorsque le statut de sa candidature change.
     */
    @EventListener
    public void onCandidatureStatutChange(CandidatureStatutChangeEvent event) {
        StatutCandidature nouveauStatut = event.nouveauStatut();
        TypeNotification type;
        String titre;
        String message;

        if (nouveauStatut == StatutCandidature.VUE) {
            type = TypeNotification.CANDIDATURE_VUE;
            titre = "Candidature consultée";
            message = String.format("Votre candidature pour le poste « %s » a été consultée par le recruteur.",
                    event.offreTitre());
        } else if (nouveauStatut == StatutCandidature.ACCEPTEE) {
            type = TypeNotification.CANDIDATURE_ACCEPTEE;
            titre = "Candidature acceptée !";
            message = String.format("Félicitations ! Votre candidature pour le poste « %s » a été acceptée.",
                    event.offreTitre());
        } else if (nouveauStatut == StatutCandidature.REFUSEE) {
            type = TypeNotification.CANDIDATURE_REFUSEE;
            titre = "Candidature non retenue";
            message = String.format("Votre candidature pour le poste « %s » n'a pas été retenue.",
                    event.offreTitre());
        } else {
            return;
        }

        String lien = "/mes-candidatures.html";

        notificationService.notifier(
                event.etudiantId(),
                type,
                titre,
                message,
                lien,
                event.offreId(),
                event.candidatureId(),
                null,
                null,
                event.auteurId()
        );
    }

    /**
     * Notifie l'utilisateur qui a proposé une compétence lorsque son statut change.
     */
    @EventListener
    public void onCompetenceStatutChange(CompetenceStatutChangeEvent event) {
        if (event.proposeeParId() == null) {
            return;
        }

        StatutCompetence nouveauStatut = event.nouveauStatut();
        TypeNotification type;
        String titre;
        String message;

        if (nouveauStatut == StatutCompetence.VALIDEE) {
            type = TypeNotification.COMPETENCE_VALIDEE;
            titre = "Compétence validée : " + event.competenceNom();
            message = String.format("Votre compétence « %s » a été validée. Elle est désormais comptée dans votre score de matching.",
                    event.competenceNom());
        } else if (nouveauStatut == StatutCompetence.REJETEE) {
            type = TypeNotification.COMPETENCE_REJETEE;
            titre = "Compétence rejetée : " + event.competenceNom();
            message = String.format("Votre compétence « %s » a été rejetée. Elle sera désormais ignorée dans le calcul du score de matching.",
                    event.competenceNom());
        } else {
            return;
        }

        String lien = "/dashboard-etudiant.html";

        notificationService.notifier(
                event.proposeeParId(),
                type,
                titre,
                message,
                lien,
                null,
                null,
                event.competenceId(),
                null,
                null
        );
    }
}
