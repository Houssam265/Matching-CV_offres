package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class OffreServiceImpl implements OffreService {
    private final OffreRepository offres;
    private final RecruteurRepository recruteurs;
    private final CandidatureRepository candidatures;
    private final CompetenceService competences;

    public OffreServiceImpl(OffreRepository offres, RecruteurRepository recruteurs,
                            CandidatureRepository candidatures, CompetenceService competences) {
        this.offres = offres; this.recruteurs = recruteurs;
        this.candidatures = candidatures; this.competences = competences;
    }

    private Recruteur recruteur(Long id) {
        if (id == null) throw new IllegalArgumentException("Le recruteur est obligatoire.");
        // TODO sécurité : vérifier le rôle RECRUTEUR et obtenir l'id depuis l'identité authentifiée.
        return recruteurs.findById(id).orElseThrow(() -> new EntityNotFoundException("Recruteur introuvable."));
    }

    private Offre offre(Long id) {
        return offres.findById(id).orElseThrow(() -> new EntityNotFoundException("Offre introuvable."));
    }

    private Offre possedee(Long id, Long recruteurId) {
        recruteur(recruteurId);
        Offre offre = offre(id);
        if (!Objects.equals(offre.getRecruteur().getId(), recruteurId))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cette offre appartient à un autre recruteur.");
        return offre;
    }

    @Override
    public OffreDto creer(OffreRequest request) {
        Offre offre = new Offre();
        offre.setRecruteur(recruteur(request.recruteurId()));
        appliquer(offre, request);
        offre.setDatePublication(LocalDateTime.now());
        return OffreDto.from(offres.save(offre));
    }

    @Override
    public OffreDto modifier(Long id, OffreRequest request) {
        Offre offre = possedee(id, request.recruteurId());
        appliquer(offre, request);
        return OffreDto.from(offres.save(offre));
    }

    private void appliquer(Offre offre, OffreRequest request) {
        if (request.titre() == null || request.titre().isBlank() || request.titre().length() > 255 ||
                request.description() == null || request.description().isBlank() || request.description().length() > 4000 ||
                request.domaine() == null || request.domaine().isBlank() || request.domaine().length() > 255 ||
                request.localisation() == null || request.localisation().isBlank() || request.localisation().length() > 255 ||
                request.typeContrat() == null)
            throw new IllegalArgumentException("Titre, description, domaine, localisation et type de contrat sont obligatoires (longueurs maximales : 255, description : 4000).");
        if (request.competences() == null || request.competences().stream()
                .noneMatch(c -> c != null && c.typeExigence() == TypeExigence.REQUISE))
            throw new IllegalArgumentException("Au moins une compétence REQUISE est obligatoire.");

        Map<Long, OffreCompetence> existantes = new HashMap<>();
        offre.getOffreCompetences().forEach(c -> existantes.put(c.getCompetence().getId(), c));
        Set<Long> ids = new HashSet<>();
        List<OffreCompetence> selections = new ArrayList<>();
        for (OffreRequest.Exigence exigence : request.competences()) {
            if (exigence == null || exigence.competenceId() == null || exigence.typeExigence() == null)
                throw new IllegalArgumentException("Compétence et type d'exigence obligatoires.");
            if (!ids.add(exigence.competenceId()))
                throw new IllegalArgumentException("Une compétence ne peut figurer deux fois dans une offre.");
            Competence competence = competences.trouverParId(exigence.competenceId());
            OffreCompetence lien = existantes.get(competence.getId());
            // Une compétence rejetée après publication reste consultable et peut être conservée ou remplacée.
            if (!(competence.getStatut() == StatutCompetence.REJETEE && lien != null))
                CompetenceOffrePolicy.verifierUtilisable(competence, request.recruteurId());
            if (lien == null) {
                lien = new OffreCompetence(); lien.setOffre(offre); lien.setCompetence(competence);
            }
            selections.add(lien);
        }
        // Réutiliser les liens évite un conflit d'unicité lors de la mise à jour JPA.
        for (int i = 0; i < selections.size(); i++)
            selections.get(i).setTypeExigence(request.competences().get(i).typeExigence());
        offre.getOffreCompetences().removeIf(c -> !ids.contains(c.getCompetence().getId()));
        selections.stream().filter(c -> !offre.getOffreCompetences().contains(c)).forEach(offre.getOffreCompetences()::add);
        offre.setTitre(request.titre().trim()); offre.setDescription(request.description().trim());
        offre.setDomaine(request.domaine().trim()); offre.setLocalisation(request.localisation().trim());
        offre.setTypeContrat(request.typeContrat());
    }

    @Override
    public OffreDto cloturer(Long id, Long recruteurId) {
        Offre offre = possedee(id, recruteurId);
        offre.setStatut(StatutOffre.CLOTUREE);
        return OffreDto.from(offres.save(offre));
    }

    @Override
    public void supprimer(Long id, Long recruteurId) {
        Offre offre = possedee(id, recruteurId);
        if (candidatures.existsByOffreId(id))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cette offre a reçu des candidatures. Clôturez-la pour la retirer des listes publiques.");
        offres.delete(offre);
    }

    @Override
    @Transactional(readOnly = true)
    public OffreDto trouver(Long id, Long recruteurId) {
        Offre offre = recruteurId == null ? offre(id) : possedee(id, recruteurId);
        if (recruteurId == null && offre.getStatut() != StatutOffre.ACTIVE)
            throw new EntityNotFoundException("Offre introuvable.");
        return OffreDto.from(offre);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OffreDto> listerParRecruteur(Long recruteurId) {
        recruteur(recruteurId);
        return offres.findByRecruteurIdOrderByDatePublicationDesc(recruteurId).stream().map(OffreDto::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<OffreDto> listerPubliques(String domaine, String localisation, TypeContrat typeContrat) {
        return offres.findByStatut(StatutOffre.ACTIVE).stream()
                .filter(o -> contient(o.getDomaine(), domaine) && contient(o.getLocalisation(), localisation))
                .filter(o -> typeContrat == null || o.getTypeContrat() == typeContrat)
                .sorted(Comparator.comparing(Offre::getDatePublication, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(OffreDto::from).toList();
    }

    private boolean contient(String valeur, String filtre) {
        return filtre == null || filtre.isBlank() || (valeur != null &&
                valeur.toLowerCase(Locale.ROOT).contains(filtre.trim().toLowerCase(Locale.ROOT)));
    }
}
