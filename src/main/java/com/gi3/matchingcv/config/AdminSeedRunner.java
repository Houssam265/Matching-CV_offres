package com.gi3.matchingcv.config;

import com.gi3.matchingcv.model.Administrateur;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.AdministrateurRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Crée les comptes administrateur au démarrage depuis la configuration.
 * Les comptes admin ne sont JAMAIS créés par inscription publique.
 * Idempotent : ignore les emails déjà enregistrés.
 * Désactivable via app.seed.admins=false.
 *
 * Configuration dans application.properties :
 *   app.admin.emails=admin@matchingcv.fr,superadmin@matchingcv.fr
 *   app.admin.prenoms=Admin,Super
 *   app.admin.noms=Système,Admin
 *   app.admin.passwords=admin123,superadmin123
 */
@Component
@Order(3)
public class AdminSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeedRunner.class);

    private final AdministrateurRepository administrateurRepository;
    private final UtilisateurRepository utilisateurRepository;

    @Value("${app.seed.admins:true}")
    private boolean enabled;

    @Value("#{'${app.admin.emails:admin@matchingcv.fr}'.split(',')}")
    private List<String> emails;

    @Value("#{'${app.admin.prenoms:Admin}'.split(',')}")
    private List<String> prenoms;

    @Value("#{'${app.admin.noms:Système}'.split(',')}")
    private List<String> noms;

    @Value("#{'${app.admin.passwords:admin123}'.split(',')}")
    private List<String> passwords;

    public AdminSeedRunner(AdministrateurRepository administrateurRepository,
                           UtilisateurRepository utilisateurRepository) {
        this.administrateurRepository = administrateurRepository;
        this.utilisateurRepository = utilisateurRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) {
            log.info("Création des comptes admin désactivée via app.seed.admins=false");
            return;
        }

        int crees = 0;
        for (int i = 0; i < emails.size(); i++) {
            String email = emails.get(i).trim().toLowerCase();
            if (email.isBlank() || utilisateurRepository.existsByEmail(email)) {
                continue;
            }
            Administrateur admin = new Administrateur();
            admin.setEmail(email);
            admin.setPrenom(i < prenoms.size() ? prenoms.get(i).trim() : "Admin");
            admin.setNom(i < noms.size() ? noms.get(i).trim() : "Système");
            admin.setMotDePasse(i < passwords.size() ? passwords.get(i).trim() : "admin123");
            admin.setRole(Role.ADMIN);
            admin.setActif(true);
            administrateurRepository.save(admin);
            crees++;
            log.info("Compte admin créé : {} ({})", admin.getPrenom() + " " + admin.getNom(), email);
        }

        if (crees == 0) {
            log.info("Tous les comptes admin existent déjà.");
        }
    }
}
