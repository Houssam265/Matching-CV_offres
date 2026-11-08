package com.gi3.matchingcv.controller;

import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.security.*;
import com.gi3.matchingcv.service.AccountRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@RestController @RequestMapping("/api/account") @Transactional
public class AccountController {
    private final UtilisateurRepository users; private final AccountProfileRepository profiles;
    private final AccountDocumentRepository documents; private final AccountRecoveryService recovery;
    public AccountController(UtilisateurRepository users,AccountProfileRepository profiles,AccountDocumentRepository documents,AccountRecoveryService recovery){this.users=users;this.profiles=profiles;this.documents=documents;this.recovery=recovery;}
    private Utilisateur user(){return users.findById(Sessions.current().id()).orElseThrow();}
    private AccountProfile profile(){return profiles.findById(Sessions.current().id()).orElseGet(()->{var p=new AccountProfile();p.setUserId(Sessions.current().id());return p;});}
    @GetMapping public Map<String,Object> get(){var u=user();var result=new LinkedHashMap<String,Object>();result.put("id",u.getId());result.put("prenom",u.getPrenom());result.put("nom",u.getNom());result.put("email",u.getEmail());result.put("emailVerified",Boolean.TRUE.equals(u.getEmailVerified()));result.put("role",u.getRole());result.put("profile",profile());result.put("hasCv",documents.existsById(u.getId()));return result;}
    public record ProfileRequest(@NotBlank @Size(max=100) String prenom,@NotBlank @Size(max=100) String nom,
        @Size(max=150) String headline,@Size(max=2000) String biography,@Size(max=150) String availability,
        @Pattern(regexp="^(https://[^\\s]+|)$") @Size(max=500) String professionalUrl,
        @Pattern(regexp="^(https://[^\\s]+|)$") @Size(max=500) String portfolioUrl,boolean notificationsEnabled){}
    @PutMapping public Map<String,Object> update(@Valid @RequestBody ProfileRequest r){var u=user();u.setPrenom(r.prenom().trim());u.setNom(r.nom().trim());u.setNotificationsEnabled(r.notificationsEnabled());users.save(u);var p=profile();p.setHeadline(r.headline());p.setBiography(r.biography());p.setAvailability(r.availability());p.setProfessionalUrl(r.professionalUrl());p.setPortfolioUrl(r.portfolioUrl());p.setNotificationsEnabled(r.notificationsEnabled());profiles.save(p);return get();}
    public record PasswordRequest(@NotBlank String currentPassword,@NotBlank @Size(max=72) String newPassword){}
    @PostMapping("/password") public Map<String,String> password(@Valid @RequestBody PasswordRequest r){var u=user();if(!Passwords.matches(r.currentPassword(),u.getMotDePasse()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mot de passe actuel incorrect.");AccountRecoveryService.validatePassword(r.newPassword());u.setMotDePasse(Passwords.hash(r.newPassword()));u.setSecurityVersion(u.getSecurityVersion()+1);users.save(u);return Map.of("message","Mot de passe modifié. Reconnectez-vous.");}
    @PostMapping("/verify-email") public Map<String,String> verify(){recovery.send(user().getEmail(),"VERIFY");return Map.of("message","Un lien de vérification a été envoyé.");}
    public record DeleteRequest(@NotBlank String motDePasse,@NotBlank String confirmation){}
    @DeleteMapping public Map<String,String> delete(@Valid @RequestBody DeleteRequest r){var u=user();if(!"SUPPRIMER".equals(r.confirmation())||!Passwords.matches(r.motDePasse(),u.getMotDePasse()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirmation ou mot de passe incorrect.");u.setActive(false);u.setSecurityVersion(u.getSecurityVersion()+1);u.setNom("Compte supprimé");u.setPrenom("");u.setEmail("deleted-"+UUID.randomUUID()+"@invalid.local");u.setMotDePasse(Passwords.hash(UUID.randomUUID().toString()));profiles.deleteById(u.getId());documents.deleteById(u.getId());users.save(u);return Map.of("message","Compte désactivé et coordonnées anonymisées. L'historique des candidatures est conservé.");}
    @PostMapping(value="/cv",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> upload(@RequestParam MultipartFile file)throws java.io.IOException {
        if(user().getRole()!=com.gi3.matchingcv.model.enums.Role.ETUDIANT)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        byte[] bytes=file.getBytes();
        if(bytes.length<5||bytes.length>5242880||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sélectionnez un PDF de 5 Mo maximum.");
        var d=new AccountDocument();d.setUserId(Sessions.current().id());d.setContent(bytes);d.setFilename("cv.pdf");documents.save(d);return Map.of("message","CV enregistré.");
    }
    @GetMapping("/cv") public ResponseEntity<byte[]> download(){var d=documents.findById(Sessions.current().id()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"cv.pdf\"").header("X-Content-Type-Options","nosniff").body(d.getContent());}
    @DeleteMapping("/cv") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCv(){documents.deleteById(Sessions.current().id());}
}
