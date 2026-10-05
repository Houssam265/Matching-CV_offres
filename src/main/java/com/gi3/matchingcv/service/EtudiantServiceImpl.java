package com.gi3.matchingcv.service;

import com.gi3.matchingcv.exception.EmailDejaUtiliseException;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implémentation du service métier pour la gestion des étudiants.
 * Respecte l'architecture en couches et le principe d'Inversion de Contrôle (IoC).
 */
@Service
@Transactional
public class EtudiantServiceImpl implements EtudiantService {

    private final EtudiantRepository etudiantRepository;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Injection par constructeur (IoC) : la classe déclare explicitement ses dépendances,
     * et le conteneur Spring IoC les instancie et les injecte automatiquement.
     */
    public EtudiantServiceImpl(
            EtudiantRepository etudiantRepository,
            UtilisateurRepository utilisateurRepository
    ) {
        this.etudiantRepository = etudiantRepository;
        this.utilisateurRepository = utilisateurRepository;
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
}
