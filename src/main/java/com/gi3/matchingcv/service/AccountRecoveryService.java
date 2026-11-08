package com.gi3.matchingcv.service;
import com.gi3.matchingcv.model.AccountToken;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.security.Passwords;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.util.*;

@Service @Transactional
public class AccountRecoveryService {
    private final AccountTokenRepository tokens;
    private final UtilisateurRepository users;
    private final ObjectProvider<JavaMailSender> mail;
    private final String origin,from;
    public AccountRecoveryService(AccountTokenRepository tokens,UtilisateurRepository users,ObjectProvider<JavaMailSender> mail,
            @Value("${app.public-url:http://localhost:8080}") String origin,@Value("${app.mail.from:noreply@localhost}") String from) {
        this.tokens=tokens;this.users=users;this.mail=mail;this.origin=origin;this.from=from;
    }
    public void send(String email,String purpose) {
        var sender=mail.getIfAvailable();
        if(sender==null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Le service email n'est pas configuré.");
        var u=users.findByEmail(email.trim().toLowerCase(Locale.ROOT)).orElse(null);
        if(u==null || !u.isActive()) return;
        byte[] random=new byte[32];new SecureRandom().nextBytes(random);
        String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(random);
        tokens.deleteByUserIdAndPurpose(u.getId(),purpose);
        AccountToken t=new AccountToken();t.setHash(hash(raw));t.setUserId(u.getId());t.setPurpose(purpose);t.setExpiresAt(Instant.now().plusSeconds(1800));tokens.save(t);
        SimpleMailMessage message=new SimpleMailMessage();message.setTo(u.getEmail());message.setFrom(from);
        message.setSubject(purpose.equals("RESET")?"Réinitialiser votre mot de passe MatchingCV":"Vérifier votre adresse email MatchingCV");
        message.setText("Ouvrez ce lien dans les 30 minutes :\n"+origin+"/recuperation.html#token="+raw+"&action="+purpose+"\nSi vous n'êtes pas à l'origine de cette demande, ignorez ce message.");
        sender.send(message);
    }
    public void consume(String raw,String purpose,String password) {
        var t=tokens.findByHash(hash(raw)).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lien invalide ou expiré."));
        if(!purpose.equals(t.getPurpose()) || !t.getExpiresAt().isAfter(Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lien invalide ou expiré.");
        var u=users.findById(t.getUserId()).orElseThrow();
        if(!u.isActive())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lien invalide ou expiré.");
        if(purpose.equals("RESET")) {validatePassword(password);u.setMotDePasse(Passwords.hash(password));u.setSecurityVersion(u.getSecurityVersion()+1);}
        else u.setEmailVerified(true);
        users.save(u);tokens.delete(t);
    }
    public static void validatePassword(String value) {
        if(value==null || value.length()<10 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Utilisez au moins 10 caractères (72 octets maximum).");
    }
    private static String hash(String value) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
        catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}
    }
}
