package com.gi3.matchingcv.config;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import com.gi3.matchingcv.security.Passwords;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
/** Idempotent upgrade; never logs passwords and never deletes an account. */
@Component
public class PasswordMigrationRunner implements ApplicationRunner {
    private final UtilisateurRepository users;
    public PasswordMigrationRunner(UtilisateurRepository users) { this.users=users; }
    public void run(ApplicationArguments args) {
        int page=0;
        while(true) {
            var batch=users.findAll(PageRequest.of(page++,100,Sort.by("id")));
            for(var user:batch) if(user.getMotDePasse()!=null && !Passwords.encoded(user.getMotDePasse())) {
                user.setMotDePasse(Passwords.hash(user.getMotDePasse())); users.save(user);
            }
            if(!batch.hasNext()) break;
        }
    }
}
