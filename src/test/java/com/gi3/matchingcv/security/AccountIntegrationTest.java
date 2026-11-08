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
}
