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
    private final AccountPhotoRepository photos; private final com.gi3.matchingcv.service.CvService cvs;
    private final CandidatureRepository applications; private final OffreRepository offers;
    private final org.springframework.context.ApplicationEventPublisher events;
    public AccountController(UtilisateurRepository users,AccountProfileRepository profiles,AccountDocumentRepository documents,AccountRecoveryService recovery,
                             AccountPhotoRepository photos,com.gi3.matchingcv.service.CvService cvs,CandidatureRepository applications,OffreRepository offers,
                             org.springframework.context.ApplicationEventPublisher events){this.users=users;this.profiles=profiles;this.documents=documents;this.recovery=recovery;this.photos=photos;this.cvs=cvs;this.applications=applications;this.offers=offers;this.events=events;}
    private Utilisateur user(){return users.findById(Sessions.current().id()).orElseThrow();}
    private AccountProfile profile(){return profiles.findById(Sessions.current().id()).orElseGet(()->{var p=new AccountProfile();p.setUserId(Sessions.current().id());return p;});}
    @GetMapping public Map<String,Object> get(){var u=user();var result=new LinkedHashMap<String,Object>();result.put("id",u.getId());result.put("prenom",u.getPrenom());result.put("nom",u.getNom());result.put("email",u.getEmail());result.put("emailVerified",Boolean.TRUE.equals(u.getEmailVerified()));result.put("role",u.getRole());result.put("profile",profile());result.put("hasCv",documents.existsById(u.getId()));result.put("hasPhoto",photos.existsById(u.getId()));return result;}
    public record ProfileRequest(@NotBlank @Size(max=100) String prenom,@NotBlank @Size(max=100) String nom,
        @Size(max=150) String headline,@Size(max=2000) String biography,@Size(max=150) String availability,
        @Pattern(regexp="^(https://[^\\s]+|)$") @Size(max=500) String professionalUrl,
        @Pattern(regexp="^(https://[^\\s]+|)$") @Size(max=500) String portfolioUrl,boolean notificationsEnabled,
        // Champs du CV : null = inchangé (les écrans qui ne les connaissent pas ne les effacent pas)
        @Email(message="Email de contact invalide.") @Size(max=254) String contactEmail,
        @Pattern(regexp="^[+0-9 ().-]{0,25}$",message="Numéro de téléphone invalide.") String telephone,
        @Size(max=100) String ville,
        @Pattern(regexp="^(https://[^\\s]+|)$",message="Le lien GitHub doit commencer par https://") @Size(max=500) String githubUrl,
        @Size(max=100) String formationPeriode,@Size(max=500) String langues,@Size(max=500) String interets){}
    private static String clean(String v){return v==null?null:v.strip();}
    @PutMapping public Map<String,Object> update(@Valid @RequestBody ProfileRequest r){var u=user();u.setPrenom(r.prenom().trim());u.setNom(r.nom().trim());u.setNotificationsEnabled(r.notificationsEnabled());users.save(u);var p=profile();p.setHeadline(r.headline());p.setBiography(r.biography());p.setAvailability(r.availability());p.setProfessionalUrl(r.professionalUrl());p.setPortfolioUrl(r.portfolioUrl());p.setNotificationsEnabled(r.notificationsEnabled());
        if(r.contactEmail()!=null)p.setContactEmail(clean(r.contactEmail()));if(r.telephone()!=null)p.setTelephone(clean(r.telephone()));if(r.ville()!=null)p.setVille(clean(r.ville()));
        if(r.githubUrl()!=null)p.setGithubUrl(clean(r.githubUrl()));if(r.formationPeriode()!=null)p.setFormationPeriode(clean(r.formationPeriode()));
        if(r.langues()!=null)p.setLangues(clean(r.langues()));if(r.interets()!=null)p.setInterets(clean(r.interets()));
        profiles.save(p);return get();}
    public record PasswordRequest(@NotBlank String currentPassword,@NotBlank @Size(max=72) String newPassword){}
    @PostMapping("/password") public Map<String,String> password(@Valid @RequestBody PasswordRequest r){var u=user();if(!Passwords.matches(r.currentPassword(),u.getMotDePasse()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Mot de passe actuel incorrect.");AccountRecoveryService.validatePassword(r.newPassword());u.setMotDePasse(Passwords.hash(r.newPassword()));u.setSecurityVersion(u.getSecurityVersion()+1);users.save(u);return Map.of("message","Mot de passe modifié. Reconnectez-vous.");}
    @PostMapping("/verify-email") public Map<String,String> verify(){recovery.send(user().getEmail(),"VERIFY");return Map.of("message","Un lien de vérification a été envoyé.");}
    public record DeleteRequest(@NotBlank String motDePasse,@NotBlank String confirmation){}
    @DeleteMapping public Map<String,String> delete(@Valid @RequestBody DeleteRequest r){var u=user();if(!"SUPPRIMER".equals(r.confirmation())||!Passwords.matches(r.motDePasse(),u.getMotDePasse()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Confirmation ou mot de passe incorrect.");fermerActivite(u);u.setActive(false);u.setSecurityVersion(u.getSecurityVersion()+1);u.setNom("Compte supprimé");u.setPrenom("");u.setEmail("deleted-"+UUID.randomUUID()+"@invalid.local");u.setMotDePasse(Passwords.hash(UUID.randomUUID().toString()));profiles.deleteById(u.getId());documents.deleteById(u.getId());photos.deleteById(u.getId());users.save(u);return Map.of("message","Compte désactivé et coordonnées anonymisées. L'historique des candidatures est conservé.");}
    @PostMapping(value="/cv",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> upload(@RequestParam MultipartFile file)throws java.io.IOException {
        if(user().getRole()!=com.gi3.matchingcv.model.enums.Role.ETUDIANT)throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        byte[] bytes=file.getBytes();
        if(bytes.length<5||bytes.length>5242880||!new String(bytes,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Sélectionnez un PDF de 5 Mo maximum.");
        var d=new AccountDocument();d.setUserId(Sessions.current().id());d.setContent(bytes);d.setFilename("cv.pdf");documents.save(d);return Map.of("message","CV enregistré.");
    }
    @GetMapping("/cv") public ResponseEntity<byte[]> download(){var d=documents.findById(Sessions.current().id()).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF).header(HttpHeaders.CONTENT_DISPOSITION,"attachment; filename=\"cv.pdf\"").header("X-Content-Type-Options","nosniff").body(d.getContent());}
    @DeleteMapping("/cv") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteCv(){documents.deleteById(Sessions.current().id());}

    private static final java.util.Set<com.gi3.matchingcv.model.enums.StatutCandidature> EN_COURS=java.util.EnumSet.of(
        com.gi3.matchingcv.model.enums.StatutCandidature.ENVOYEE,com.gi3.matchingcv.model.enums.StatutCandidature.VUE,com.gi3.matchingcv.model.enums.StatutCandidature.ENTRETIEN);

    /**
     * Un compte supprimé ne laisse rien « en suspens » : les candidatures en cours d'un étudiant sont retirées,
     * les offres actives d'un recruteur sont clôturées et leurs candidats prévenus.
     */
    private void fermerActivite(Utilisateur u){
        var now=java.time.LocalDateTime.now();
        if(u.getRole()==com.gi3.matchingcv.model.enums.Role.ETUDIANT){
            for(var c:applications.findByEtudiantIdAndStatutIn(u.getId(),EN_COURS)){c.setStatut(com.gi3.matchingcv.model.enums.StatutCandidature.RETIREE);if(c.getDateTraitement()==null)c.setDateTraitement(now);}
        }else if(u.getRole()==com.gi3.matchingcv.model.enums.Role.RECRUTEUR){
            for(var o:offers.findByRecruteurIdOrderByDatePublicationDesc(u.getId())){
                if(o.getStatut()!=com.gi3.matchingcv.model.enums.StatutOffre.ACTIVE)continue;
                o.setStatut(com.gi3.matchingcv.model.enums.StatutOffre.CLOTUREE);
                var ids=applications.findByOffreIdAndStatutIn(o.getId(),EN_COURS).stream().map(c->c.getEtudiant().getId()).distinct().toList();
                if(!ids.isEmpty())events.publishEvent(new com.gi3.matchingcv.event.OffreClotureeEvent(o.getId(),o.getTitre(),ids));
            }
        }
    }

    /** Données structurées du CV de l'étudiant connecté (page d'export / impression). */
    @GetMapping("/cv-data") public com.gi3.matchingcv.dto.CvDto cvData(){return cvs.pourEtudiant(Sessions.current().id());}

    // ---------------------------------------------------------------- Photo de profil (tous rôles)

    private static final int PHOTO_MAX = 2 * 1024 * 1024;

    @PostMapping(value="/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,String> uploadPhoto(@RequestParam MultipartFile file) throws java.io.IOException {
        byte[] bytes=file.getBytes();
        String type=imageType(bytes);
        if(bytes.length>PHOTO_MAX||type==null)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choisissez une image JPEG, PNG ou WebP de 2 Mo maximum.");
        var photo=photos.findById(Sessions.current().id()).orElseGet(AccountPhoto::new);
        photo.setUserId(Sessions.current().id());photo.setContent(bytes);photo.setContentType(type);photo.setUpdatedAt(java.time.LocalDateTime.now());
        photos.save(photo);
        return Map.of("message","Photo de profil mise à jour.");
    }

    @DeleteMapping("/photo") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePhoto(){photos.deleteById(Sessions.current().id());}

    /**
     * Photo de profil d'un utilisateur, visible par tout compte connecté.
     * Sans photo, renvoie un avatar SVG à ses initiales : l'image est donc toujours valide.
     */
    @GetMapping("/photo/{userId}") @Transactional(readOnly=true)
    public ResponseEntity<byte[]> photo(@PathVariable Long userId,@RequestHeader(value=HttpHeaders.IF_NONE_MATCH,required=false) String ifNoneMatch){
        var photo=photos.findById(userId).orElse(null);
        byte[] body;String type;String etag;
        if(photo!=null){
            body=photo.getContent();type=photo.getContentType();
            etag="\"p"+userId+"-"+(photo.getUpdatedAt()==null?0:photo.getUpdatedAt().toEpochSecond(java.time.ZoneOffset.UTC))+"\"";
        }else{
            var u=users.findById(userId).orElse(null);
            String initials=u==null||!u.isActive()?"?":initiale(u.getPrenom())+initiale(u.getNom());
            if(initials.isEmpty())initials="?";
            String[] palette={"#22744b","#1d5f8a","#7a4fa3","#b0582b","#2b7a78","#8a6d1d"};
            String bg=palette[(int)(Math.abs(userId)%palette.length)];
            body=("<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 96 96\"><rect width=\"96\" height=\"96\" fill=\""+bg+"\"/>"
                +"<text x=\"48\" y=\"50\" dy=\".35em\" text-anchor=\"middle\" font-family=\"Inter,Segoe UI,Arial,sans-serif\" font-size=\"38\" font-weight=\"600\" fill=\"#fff\">"
                +initials+"</text></svg>").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            type="image/svg+xml";etag="\"i"+userId+"-"+Integer.toHexString(initials.hashCode())+"\"";
        }
        var headers=ResponseEntity.ok().eTag(etag).header(HttpHeaders.CACHE_CONTROL,"private, no-cache")
                .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","default-src 'none'; style-src 'unsafe-inline'");
        if(etag.equals(ifNoneMatch))return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        return headers.contentType(MediaType.parseMediaType(type)).body(body);
    }

    /** Première lettre (lettre Unicode uniquement), en majuscule : jamais de caractère interprétable en SVG. */
    private static String initiale(String s){
        if(s==null)return "";
        for(int i=0;i<s.length();){int cp=s.codePointAt(i);if(Character.isLetter(cp))return new String(Character.toChars(Character.toUpperCase(cp)));i+=Character.charCount(cp);}
        return "";
    }

    private static String imageType(byte[] b){
        if(b.length>3&&(b[0]&0xFF)==0xFF&&(b[1]&0xFF)==0xD8&&(b[2]&0xFF)==0xFF)return "image/jpeg";
        if(b.length>8&&(b[0]&0xFF)==0x89&&b[1]=='P'&&b[2]=='N'&&b[3]=='G')return "image/png";
        if(b.length>12&&b[0]=='R'&&b[1]=='I'&&b[2]=='F'&&b[3]=='F'&&b[8]=='W'&&b[9]=='E'&&b[10]=='B'&&b[11]=='P')return "image/webp";
        return null;
    }
}
