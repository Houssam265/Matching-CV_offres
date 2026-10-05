package com.gi3.matchingcv.service;

import com.gi3.matchingcv.model.Projet;
import com.gi3.matchingcv.repository.ProjetRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service métier pour la gestion des projets.
 * Respecte l'architecture en couches et le principe d'Inversion de Contrôle (IoC).
 */
@Service
@Transactional
public class ProjetServiceImpl implements ProjetService {

    private final ProjetRepository projetRepository;

    /**
     * Injection par constructeur (IoC).
     */
    public ProjetServiceImpl(ProjetRepository projetRepository) {
        this.projetRepository = projetRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Projet> listerParEtudiant(Long etudiantId) {
        return projetRepository.findByEtudiantId(etudiantId);
    }

    @Override
    public Projet creer(Projet projet) {
        return projetRepository.save(projet);
    }

    @Override
    public void supprimer(Long etudiantId, Long projetId) {
        Projet projet = projetRepository.findById(projetId)
                .orElseThrow(() -> new EntityNotFoundException("Projet introuvable avec l'identifiant : " + projetId));
        if (!projet.getEtudiant().getId().equals(etudiantId)) {
            throw new EntityNotFoundException("Ce projet n'appartient pas à l'étudiant " + etudiantId);
        }
        projetRepository.delete(projet);
    }
}
