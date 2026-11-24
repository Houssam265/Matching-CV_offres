package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.ConversationDetailDto;
import com.gi3.matchingcv.dto.ConversationDto;
import com.gi3.matchingcv.dto.MessageDto;
import com.gi3.matchingcv.model.Candidature;
import com.gi3.matchingcv.model.Message;
import com.gi3.matchingcv.model.Utilisateur;
import com.gi3.matchingcv.model.enums.StatutCandidature;
import com.gi3.matchingcv.model.enums.TypeNotification;
import com.gi3.matchingcv.repository.CandidatureRepository;
import com.gi3.matchingcv.repository.MessageRepository;
import com.gi3.matchingcv.repository.NotificationRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Messagerie recruteur ↔ candidat, sur le modèle des plateformes d'emploi :
 * <ul>
 *   <li>une conversation par candidature, limitée à ses deux participants ;</li>
 *   <li>le recruteur ouvre la conversation, le candidat répond ensuite ;</li>
 *   <li>la conversation passe en lecture seule si la candidature est refusée ou retirée ;</li>
 *   <li>aucune adresse email n'est échangée : tout passe par la plateforme.</li>
 * </ul>
 */
@Service
@Transactional
public class MessagerieService {

    public static final int LONGUEUR_MAX = 2000;

    private final MessageRepository messages;
    private final CandidatureRepository candidatures;
    private final UtilisateurRepository utilisateurs;
    private final NotificationRepository notifications;
    private final NotificationService notificationService;

    public MessagerieService(MessageRepository messages, CandidatureRepository candidatures,
                             UtilisateurRepository utilisateurs, NotificationRepository notifications,
                             NotificationService notificationService) {
        this.messages = messages;
        this.candidatures = candidatures;
        this.utilisateurs = utilisateurs;
        this.notifications = notifications;
        this.notificationService = notificationService;
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> lister(Long userId) {
        Map<Long, List<Message>> parCandidature = new LinkedHashMap<>();
        for (Message m : messages.findAllForParticipant(userId)) {
            parCandidature.computeIfAbsent(m.getCandidature().getId(), k -> new ArrayList<>()).add(m);
        }
        List<ConversationDto> result = new ArrayList<>();
        for (List<Message> fil : parCandidature.values()) {
            Message dernier = fil.get(0); // tri décroissant : le premier est le plus récent
            Candidature c = dernier.getCandidature();
            long nonLus = fil.stream().filter(m -> !m.isLu() && !m.getExpediteur().getId().equals(userId)).count();
            boolean recruteurAEcrit = fil.stream().anyMatch(m -> estRecruteur(c, m.getExpediteur().getId()));
            result.add(resume(c, userId, dernier, nonLus, recruteurAEcrit));
        }
        return result;
    }

    public ConversationDetailDto ouvrir(Long candidatureId, Long userId) {
        Candidature c = candidaturePourParticipant(candidatureId, userId);
        List<Message> fil = messages.findByCandidatureIdOrderByDateEnvoiAsc(candidatureId);
        Message dernier = fil.isEmpty() ? null : fil.get(fil.size() - 1);
        boolean recruteurAEcrit = fil.stream().anyMatch(m -> estRecruteur(c, m.getExpediteur().getId()));
        List<MessageDto> dtos = fil.stream().map(m -> versDto(m, userId)).toList();
        ConversationDetailDto detail = new ConversationDetailDto(resume(c, userId, dernier, 0, recruteurAEcrit), dtos);
        // Marquage en dernier : la mise à jour groupée ne doit pas détacher les entités utilisées ci-dessus.
        messages.marquerLus(candidatureId, userId);
        return detail;
    }

    public MessageDto envoyer(Long candidatureId, Long userId, String contenu) {
        String texte = contenu == null ? "" : contenu.strip();
        if (texte.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Écrivez un message avant de l'envoyer.");
        if (texte.length() > LONGUEUR_MAX)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le message ne peut pas dépasser " + LONGUEUR_MAX + " caractères.");

        Candidature c = candidaturePourParticipant(candidatureId, userId);
        boolean recruteurAEcrit = messages.existsByCandidatureIdAndExpediteurId(candidatureId, c.getOffre().getRecruteur().getId());
        String raison = raisonFermeture(c, userId, recruteurAEcrit);
        if (raison != null) throw new ResponseStatusException(HttpStatus.CONFLICT, raison);

        Utilisateur expediteur = utilisateurs.findById(userId).orElseThrow();
        Message m = new Message();
        m.setCandidature(c);
        m.setExpediteur(expediteur);
        m.setContenu(texte);
        Message saved = messages.save(m);

        boolean parRecruteur = estRecruteur(c, userId);
        // Un recruteur qui écrit au candidat a forcément consulté sa candidature.
        if (parRecruteur && c.getStatut() == StatutCandidature.ENVOYEE) {
            c.setStatut(StatutCandidature.VUE);
            if (c.getDateTraitement() == null) c.setDateTraitement(java.time.LocalDateTime.now());
            candidatures.save(c);
        }
        Long destinataireId = parRecruteur ? c.getEtudiant().getId() : c.getOffre().getRecruteur().getId();
        // Une seule notification non lue par conversation, pour ne pas inonder le destinataire.
        if (!notifications.existsNonLueParCandidature(destinataireId, TypeNotification.NOUVEAU_MESSAGE, c.getId())) {
            String auteur = parRecruteur
                    ? Optional.ofNullable(c.getOffre().getRecruteur().getNomEntreprise()).filter(s -> !s.isBlank()).orElse("le recruteur")
                    : nomComplet(expediteur);
            notificationService.notifier(destinataireId, TypeNotification.NOUVEAU_MESSAGE,
                    "Nouveau message de " + auteur,
                    "À propos de l'offre « " + c.getOffre().getTitre() + " » : " + apercu(texte, 140),
                    "/messages.html?c=" + c.getId(), c.getOffre().getId(), c.getId(), null, null, userId);
        }
        return versDto(saved, userId);
    }

    @Transactional(readOnly = true)
    public long compterNonLus(Long userId) {
        return messages.countNonLus(userId);
    }

    // ------------------------------------------------------------------

    private Candidature candidaturePourParticipant(Long candidatureId, Long userId) {
        Candidature c = candidatures.findById(candidatureId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation introuvable."));
        boolean participant = c.getEtudiant().getId().equals(userId) || estRecruteur(c, userId);
        if (!participant) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Vous ne participez pas à cette conversation.");
        return c;
    }

    private static boolean estRecruteur(Candidature c, Long userId) {
        return c.getOffre().getRecruteur() != null && c.getOffre().getRecruteur().getId().equals(userId);
    }

    private static String raisonFermeture(Candidature c, Long userId, boolean recruteurAEcrit) {
        if (c.getStatut() == StatutCandidature.RETIREE) return "Le candidat a retiré sa candidature : la conversation est fermée.";
        if (c.getStatut() == StatutCandidature.REFUSEE) return "Cette candidature n'a pas été retenue : la conversation est fermée.";
        Utilisateur autre = estRecruteur(c, userId) ? c.getEtudiant() : c.getOffre().getRecruteur();
        if (autre == null || !autre.isActive()) return "Ce compte n'est plus actif.";
        if (!estRecruteur(c, userId) && !recruteurAEcrit)
            return "Le recruteur n'a pas encore pris contact. Vous pourrez lui répondre dès son premier message.";
        return null;
    }

    private ConversationDto resume(Candidature c, Long userId, Message dernier, long nonLus, boolean recruteurAEcrit) {
        boolean recruteur = estRecruteur(c, userId);
        var r = c.getOffre().getRecruteur();
        String entreprise = r != null && r.getNomEntreprise() != null ? r.getNomEntreprise() : "";
        Utilisateur autre = recruteur ? c.getEtudiant() : r;
        String detail = recruteur
                ? Optional.ofNullable(c.getEtudiant().getFiliere()).filter(s -> !s.isBlank()).orElse("Candidat")
                : (entreprise.isBlank() ? "Recruteur" : "Recruteur · " + entreprise);
        String raison = raisonFermeture(c, userId, recruteurAEcrit);
        return new ConversationDto(
                c.getId(), c.getOffre().getId(), c.getOffre().getTitre(), entreprise,
                autre != null ? autre.getId() : null, autre != null ? nomComplet(autre) : "Compte supprimé", detail,
                c.getStatut(),
                dernier != null ? apercu(dernier.getContenu(), 120) : null,
                dernier != null ? dernier.getDateEnvoi() : null,
                dernier != null && dernier.getExpediteur().getId().equals(userId),
                nonLus, raison == null, raison);
    }

    private static MessageDto versDto(Message m, Long userId) {
        return new MessageDto(m.getId(), m.getExpediteur().getId(), m.getExpediteur().getId().equals(userId),
                m.getContenu(), m.getDateEnvoi(), m.isLu());
    }

    private static String nomComplet(Utilisateur u) {
        String nom = ((u.getPrenom() == null ? "" : u.getPrenom()) + " " + (u.getNom() == null ? "" : u.getNom())).strip();
        return nom.isEmpty() ? "Utilisateur" : nom;
    }

    private static String apercu(String texte, int max) {
        String t = texte.replaceAll("\\s+", " ").strip();
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }
}
