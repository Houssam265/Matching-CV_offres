package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.repository.RecruteurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
@Transactional
public class RecruteurServiceImpl implements RecruteurService {
    private final RecruteurRepository recruteurs;
    private final AuthService auth;
    private final CompetenceService competences;

    public RecruteurServiceImpl(RecruteurRepository recruteurs, AuthService auth, CompetenceService competences) {
        this.recruteurs = recruteurs; this.auth = auth; this.competences = competences;
    }

    @Override
    public AuthResponse inscrire(RecruteurInscriptionRequest request) {
        try {
            return auth.inscrire(request.toAuthRequest());
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.CONFLICT, e.getMessage());
        }
    }

    private Recruteur entite(Long id) {
        // TODO sécurité : vérifier le rôle RECRUTEUR et que l'identité authentifiée correspond à id.
        return recruteurs.findById(id).orElseThrow(() -> new EntityNotFoundException("Recruteur introuvable."));
    }

    @Override
    @Transactional(readOnly = true)
    public RecruteurDto trouver(Long id) { return RecruteurDto.from(entite(id)); }

    @Override
    public RecruteurDto modifier(Long id, RecruteurProfilRequest request) {
        Recruteur recruteur = entite(id);
        if (request.nomEntreprise() == null || request.nomEntreprise().isBlank()) {
            throw new IllegalArgumentException("Le nom de l'entreprise est obligatoire.");
        }
        if (request.prenom() != null) {
            recruteur.setPrenom(request.prenom().trim());
        }
        if (request.nom() != null) {
            recruteur.setNom(request.nom().trim());
        }
        recruteur.setNomEntreprise(request.nomEntreprise().trim());
        recruteur.setSecteurActivite(request.secteurActivite() != null ? request.secteurActivite().trim() : null);
        recruteur.setVille(request.ville() != null ? request.ville().trim() : null);
        recruteur.setSiteWeb(request.siteWeb() != null ? request.siteWeb().trim() : null);
        recruteur.setDescriptionEntreprise(request.descriptionEntreprise() != null ? request.descriptionEntreprise().trim() : null);

        Recruteur saved = recruteurs.save(recruteur);
        return RecruteurDto.from(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Competence> suggerer(Long id, String query) {
        entite(id);
        return competences.suggererCompetences(query, id);
    }

    @Override
    public Competence proposer(Long id, String nom, String categorie) {
        Recruteur recruteur = entite(id);
        if (nom == null || nom.isBlank() || Competence.normaliserNom(nom).isBlank())
            throw new IllegalArgumentException("Le nom de la compétence est obligatoire.");
        // Recherche avant proposer() : aucun doublon créé, même avec une orthographe différente.
        Competence competence = competences.trouverParNomNormalise(Competence.normaliserNom(nom.trim()))
                .orElseGet(() -> competences.proposer(nom, categorie, recruteur));
        CompetenceOffrePolicy.verifierUtilisable(competence, id);
        return competence;
    }
}
