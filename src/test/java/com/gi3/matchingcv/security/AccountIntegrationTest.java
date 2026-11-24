package com.gi3.matchingcv.security;

import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties={"spring.datasource.url=jdbc:h2:mem:accounts;MODE=MySQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.jpa.show-sql=false", "app.seed.competences=false"})
@AutoConfigureMockMvc @Transactional
class AccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired EtudiantRepository students;
    @Autowired RecruteurRepository recruiters;
    @Autowired OffreRepository offers;
    @Autowired CandidatureRepository applications;
    @Autowired NotificationRepository interviewNotifications;
    @MockBean JavaMailSender mail;
    @Autowired jakarta.persistence.EntityManager em;
    Etudiant student;
    @BeforeEach void setup(){student=new Etudiant();student.setEmail("integration@example.com");student.setNom("Test");student.setPrenom("Compte");student.setRole(Role.ETUDIANT);student.setMotDePasse(Passwords.hash("password-long-123"));student=students.saveAndFlush(student);}
    MockHttpSession login()throws Exception{return (MockHttpSession)mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content("{\"email\":\"integration@example.com\",\"motDePasse\":\"password-long-123\",\"role\":\"ETUDIANT\"}")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);}
    @Test void acceptedCandidateCanAttendScheduledInterview()throws Exception {
        var recruiter=new Recruteur();recruiter.setRole(Role.RECRUTEUR);recruiter.setEmail("interview@example.com");recruiter.setMotDePasse(Passwords.hash("password-long-123"));recruiter=recruiters.saveAndFlush(recruiter);
        var offer=new Offre();offer.setRecruteur(recruiter);offer.setTitre("Poste");offer=offers.saveAndFlush(offer);
        var c=new Candidature();c.setEtudiant(student);c.setOffre(offer);c.setStatut(StatutCandidature.ACCEPTEE);c=applications.saveAndFlush(c);
        var principal=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(recruiter.getId(),Role.RECRUTEUR,0),null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RECRUTEUR")));
        String body="{\"startsAt\":\""+java.time.OffsetDateTime.now().plusDays(3)+"\",\"location\":\"https://example.com/meeting\",\"instructions\":\"Préparer votre CV\"}";
        mvc.perform(put("/api/candidatures/"+c.getId()+"/entretien").with(authentication(principal)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertEquals(StatutCandidature.ACCEPTEE,applications.findById(c.getId()).orElseThrow().getStatut());
        long invitations=interviewNotifications.findAll().stream().filter(n->n.getType()==TypeNotification.ENTRETIEN_PLANIFIE).count();
        assertEquals(1L,invitations);
        var invitation=interviewNotifications.findAll().stream().filter(n->n.getType()==TypeNotification.ENTRETIEN_PLANIFIE).findFirst().orElseThrow();
        assertEquals(student.getId(),invitation.getDestinataire().getId());
        assertEquals("/mes-entretiens.html#candidature-"+c.getId(),invitation.getLien());
        mvc.perform(put("/api/candidatures/"+c.getId()+"/entretien").with(authentication(principal)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertEquals(1L,interviewNotifications.findAll().stream().filter(n->n.getType()==TypeNotification.ENTRETIEN_PLANIFIE).count());
        mvc.perform(put("/api/candidatures/"+c.getId()+"/entretien").with(authentication(principal)).with(csrf()).contentType("application/json").content(body.replace("Préparer votre CV","Préparer un portfolio"))).andExpect(status().isOk());
        assertEquals(1L,interviewNotifications.findAll().stream().filter(n->n.getType()==TypeNotification.ENTRETIEN_MODIFIE).count());
        mvc.perform(get("/api/candidatures/"+c.getId()+"/entretien").session(login())).andExpect(status().isOk()).andExpect(jsonPath("$.location").value("https://example.com/meeting"));
        c.setStatut(StatutCandidature.REFUSEE);applications.saveAndFlush(c);
        mvc.perform(put("/api/candidatures/"+c.getId()+"/entretien").with(authentication(principal)).with(csrf()).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void cvIsPrivateAndCanBeDeleted()throws Exception {
        var session=login();var pdf=new MockMultipartFile("file","cv.pdf","application/pdf","%PDF-1.7 test".getBytes());
        mvc.perform(multipart("/api/account/cv").file(pdf).session(session).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/account/cv").session(session)).andExpect(status().isOk()).andExpect(content().bytes(pdf.getBytes()));
        mvc.perform(get("/api/account/cv")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/account/cv").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/cv").session(session)).andExpect(status().isNotFound());
    }
    @Test void resetTokenIsSingleUseAndRevokesSessions()throws Exception {
        var session=login();
        mvc.perform(post("/api/auth/password-reset/request").with(csrf()).contentType("application/json").content("{\"email\":\"integration@example.com\"}")).andExpect(status().isOk());
        var capture=org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);verify(mail).send(capture.capture());
        String token=capture.getValue().getText().split("#token=")[1].split("&")[0];
        String body="{\"token\":\""+token+"\",\"motDePasse\":\"changed-long-123\"}";
        mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType("application/json").content(body)).andExpect(status().isOk());
        assertTrue(Passwords.matches("changed-long-123",students.findById(student.getId()).orElseThrow().getMotDePasse()));
        mvc.perform(get("/api/account").session(session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/password-reset/confirm").with(csrf()).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void candidateWithdrawsOnlyOwnApplication()throws Exception {
        var recruiter=new Recruteur();recruiter.setRole(Role.RECRUTEUR);recruiter.setEmail("recruit@example.com");recruiter.setMotDePasse(Passwords.hash("password-long-123"));recruiter=recruiters.saveAndFlush(recruiter);
        var offer=new Offre();offer.setRecruteur(recruiter);offer.setTitre("Poste");offer=offers.saveAndFlush(offer);
        var c=new Candidature();c.setOffre(offer);c.setEtudiant(student);c.setStatut(StatutCandidature.ENVOYEE);c=applications.saveAndFlush(c);
        var session=login();
        mvc.perform(patch("/api/candidatures/"+c.getId()+"/retirer").session(session).with(csrf())).andExpect(status().isOk());
        assertEquals(StatutCandidature.RETIREE,applications.findById(c.getId()).orElseThrow().getStatut());
        mvc.perform(get("/api/candidatures/"+c.getId()+"/detail").session(session).param("recruteurId",recruiter.getId().toString())).andExpect(status().isForbidden());
    }
    @Test void recruiterOwnershipWorksOutsideRequestTransaction()throws Exception {
        // Reproduce the real filter boundary: no test transaction may keep proxies alive.
        org.springframework.test.context.transaction.TestTransaction.end();
        setup();
        var recruiter=new Recruteur();recruiter.setRole(Role.RECRUTEUR);recruiter.setEmail("ownership@example.com");recruiter.setMotDePasse(Passwords.hash("password-long-123"));recruiter=recruiters.saveAndFlush(recruiter);
        var offer=new Offre();offer.setRecruteur(recruiter);offer.setTitre("Poste");offer=offers.saveAndFlush(offer);
        var c=new Candidature();c.setEtudiant(student);c.setOffre(offer);c.setStatut(StatutCandidature.ENVOYEE);c=applications.saveAndFlush(c);
        try {
            var session=(MockHttpSession)mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content("{\"email\":\"ownership@example.com\",\"motDePasse\":\"password-long-123\",\"role\":\"RECRUTEUR\"}")).andExpect(status().isOk()).andReturn().getRequest().getSession(false);
            mvc.perform(get("/api/candidatures/"+c.getId()+"/entretien").session(session)).andExpect(status().isOk());
            mvc.perform(get("/api/candidatures/"+c.getId()+"/entretien").session(session).param("recruteurId","999999")).andExpect(status().isForbidden());
        } finally {applications.deleteById(c.getId());offers.deleteById(offer.getId());recruiters.deleteById(recruiter.getId());students.deleteById(student.getId());}
    }
    org.springframework.security.authentication.UsernamePasswordAuthenticationToken asRecruiter(Recruteur r){
        return org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(r.getId(),Role.RECRUTEUR,0),null,java.util.List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_RECRUTEUR")));
    }
    @Test void profilePhotoFallsBackToInitialsAndOnlyAcceptsImages()throws Exception {
        var session=login();
        mvc.perform(get("/api/account/photo/"+student.getId())).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/account/photo/"+student.getId()).session(session)).andExpect(status().isOk())
            .andExpect(content().contentType("image/svg+xml")).andExpect(content().string(org.hamcrest.Matchers.containsString(">CT</text>")));
        var fake=new MockMultipartFile("file","photo.png","image/png","<svg onload=alert(1)>".getBytes());
        mvc.perform(multipart("/api/account/photo").file(fake).session(session).with(csrf())).andExpect(status().isBadRequest());
        byte[] png={(byte)0x89,'P','N','G',0x0D,0x0A,0x1A,0x0A,0,0,0,0x0D};
        mvc.perform(multipart("/api/account/photo").file(new MockMultipartFile("file","p.png","image/png",png)).session(session).with(csrf())).andExpect(status().isOk());
        var res=mvc.perform(get("/api/account/photo/"+student.getId()).session(session)).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(png)).andReturn().getResponse();
        mvc.perform(get("/api/account/photo/"+student.getId()).session(session).header("If-None-Match",res.getHeader("ETag"))).andExpect(status().isNotModified());
        mvc.perform(get("/api/account").session(session)).andExpect(jsonPath("$.hasPhoto").value(true));
        mvc.perform(delete("/api/account/photo").session(session).with(csrf())).andExpect(status().isNoContent());
        mvc.perform(get("/api/account/photo/"+student.getId()).session(session)).andExpect(content().contentType("image/svg+xml"));
    }
    @Test void recruiterOpensConversationThenCandidateRepliesWithoutEmailExchange()throws Exception {
        var recruiter=new Recruteur();recruiter.setRole(Role.RECRUTEUR);recruiter.setEmail("msg-recruiter@example.com");recruiter.setNomEntreprise("Acme");recruiter.setMotDePasse(Passwords.hash("password-long-123"));recruiter=recruiters.saveAndFlush(recruiter);
        var other=new Recruteur();other.setRole(Role.RECRUTEUR);other.setEmail("msg-other@example.com");other.setMotDePasse(Passwords.hash("password-long-123"));other=recruiters.saveAndFlush(other);
        var offer=new Offre();offer.setRecruteur(recruiter);offer.setTitre("Dev");offer=offers.saveAndFlush(offer);
        var c=new Candidature();c.setEtudiant(student);c.setOffre(offer);c.setStatut(StatutCandidature.VUE);c=applications.saveAndFlush(c);
        em.flush();em.clear(); // conditions réelles : aucune entité déjà chargée en mémoire
        var session=login();
        String url="/api/messages/conversations/"+c.getId();
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content("{\"contenu\":\"Bonjour\"}")).andExpect(status().isConflict());
        mvc.perform(post(url).with(authentication(asRecruiter(recruiter))).with(csrf()).contentType("application/json").content("{\"contenu\":\"Bonjour, un échange cette semaine ?\"}")).andExpect(status().isCreated());
        mvc.perform(get("/api/messages/non-lus").session(session)).andExpect(jsonPath("$.count").value(1));
        mvc.perform(get("/api/messages/conversations").session(session)).andExpect(jsonPath("$[0].interlocuteurDetail").value("Recruteur · Acme"))
            .andExpect(jsonPath("$[0].nonLus").value(1)).andExpect(jsonPath("$[0].peutEcrire").value(true));
        mvc.perform(get(url).session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.messages[0].moi").value(false));
        mvc.perform(get("/api/messages/non-lus").session(session)).andExpect(jsonPath("$.count").value(0));
        mvc.perform(post(url).session(session).with(csrf()).contentType("application/json").content("{\"contenu\":\"Avec plaisir\"}")).andExpect(status().isCreated());
        mvc.perform(get(url).with(authentication(asRecruiter(other)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/candidatures/"+c.getId()+"/cv").with(authentication(asRecruiter(recruiter)))).andExpect(status().isOk())
            .andExpect(jsonPath("$.nom").value("Test")).andExpect(jsonPath("$.email").doesNotExist());
        mvc.perform(get("/api/candidatures/"+c.getId()+"/cv").with(authentication(asRecruiter(other)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/account/cv-data").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value("integration@example.com"));
        assertEquals(1L,interviewNotifications.findAll().stream().filter(n->n.getType()==TypeNotification.NOUVEAU_MESSAGE&&n.getDestinataire().getId().equals(student.getId())).count());
    }
    @Test void accountIsTemporarilyLockedAfterRepeatedFailures()throws Exception {
        var other=new Etudiant();other.setEmail("lockout@example.com");other.setNom("Lock");other.setPrenom("Out");other.setRole(Role.ETUDIANT);other.setMotDePasse(Passwords.hash("password-long-123"));students.saveAndFlush(other);
        String bad="{\"email\":\"lockout@example.com\",\"motDePasse\":\"wrong-password-xx\"}";
        for(int i=0;i<5;i++) mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content(bad)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content("{\"email\":\"lockout@example.com\",\"motDePasse\":\"password-long-123\"}"))
            .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
        // Les autres comptes ne sont pas affectés.
        login();
    }
    @Test void pagesSendSecurityHeadersAndLegacyAdminLoginRedirects()throws Exception {
        mvc.perform(get("/login.html")).andExpect(header().string("X-Frame-Options","DENY"))
            .andExpect(header().string("Content-Security-Policy",org.hamcrest.Matchers.containsString("frame-ancestors 'none'")))
            .andExpect(header().string("Referrer-Policy","strict-origin-when-cross-origin"))
            .andExpect(header().exists("Permissions-Policy"));
        mvc.perform(get("/admin-login.html")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login.html"));
    }
    @Test void cvCanBeDownloadedAsRealPdf()throws Exception {
        var recruiter=new Recruteur();recruiter.setRole(Role.RECRUTEUR);recruiter.setEmail("pdf-recruiter@example.com");recruiter.setNomEntreprise("Acme");recruiter.setMotDePasse(Passwords.hash("password-long-123"));recruiter=recruiters.saveAndFlush(recruiter);
        var other=new Recruteur();other.setRole(Role.RECRUTEUR);other.setEmail("pdf-other@example.com");other.setMotDePasse(Passwords.hash("password-long-123"));other=recruiters.saveAndFlush(other);
        var offer=new Offre();offer.setRecruteur(recruiter);offer.setTitre("Dev");offer=offers.saveAndFlush(offer);
        var c=new Candidature();c.setEtudiant(student);c.setOffre(offer);c.setStatut(StatutCandidature.VUE);c=applications.saveAndFlush(c);
        var session=login();
        mvc.perform(put("/api/account").session(session).with(csrf()).contentType("application/json")
            .content("{\"prenom\":\"Compte\",\"nom\":\"Test\",\"notificationsEnabled\":true,\"headline\":\"Développeuse <Java> & Spring\",\"telephone\":\"+212 600000000\",\"githubUrl\":\"https://github.com/test\",\"langues\":\"Français — courant\\nAnglais — B2\"}"))
            .andExpect(status().isOk());
        var res=mvc.perform(get("/api/account/cv-pdf").session(session)).andExpect(status().isOk())
            .andExpect(content().contentType("application/pdf"))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("attachment")))
            .andExpect(header().string("Content-Disposition",org.hamcrest.Matchers.containsString("CV-Compte-Test.pdf"))).andReturn().getResponse();
        String texte=pdfText(res.getContentAsByteArray());
        assertTrue(texte.contains("Développeuse <Java> & Spring"),texte);
        assertTrue(texte.contains("integration@example.com"));
        assertTrue(texte.contains("FORMATION")||texte.contains("LANGUES"));
        var rec=mvc.perform(get("/api/candidatures/"+c.getId()+"/cv-pdf").with(authentication(asRecruiter(recruiter)))).andExpect(status().isOk()).andReturn().getResponse();
        String texteRecruteur=pdfText(rec.getContentAsByteArray());
        assertFalse(texteRecruteur.contains("integration@example.com"),"le recruteur ne doit pas voir l'email");
        assertFalse(texteRecruteur.contains("600000000"),"le recruteur ne doit pas voir le téléphone");
        assertTrue(texteRecruteur.contains("github.com/test"));
        mvc.perform(get("/api/candidatures/"+c.getId()+"/cv-pdf").with(authentication(asRecruiter(other)))).andExpect(status().isForbidden());
    }
    static String pdfText(byte[] pdf)throws Exception {
        assertTrue(new String(pdf,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-"));
        try(var doc=org.apache.pdfbox.Loader.loadPDF(pdf)){return new org.apache.pdfbox.text.PDFTextStripper().getText(doc);}
    }
}
