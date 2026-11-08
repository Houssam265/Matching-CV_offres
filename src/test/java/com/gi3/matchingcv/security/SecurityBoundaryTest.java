package com.gi3.matchingcv.security;
import com.gi3.matchingcv.controller.*;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.repository.*;
import com.gi3.matchingcv.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest({AuthController.class,OffreController.class})
@Import({SecurityConfig.class,ResourceAccess.class,AuthServiceImpl.class})
class SecurityBoundaryTest {
    @Autowired MockMvc mvc;
    @MockBean UtilisateurRepository users;
    @MockBean EtudiantRepository students;
    @MockBean RecruteurRepository recruiters;
    @MockBean OffreRepository offers;
    @MockBean CandidatureRepository applications;
    @MockBean NotificationRepository notifications;
    @MockBean OffreService service;
    private Etudiant user;
    @BeforeEach void init(){user=new Etudiant();user.setId(1L);user.setEmail("user@example.com");user.setRole(Role.ETUDIANT);user.setMotDePasse(Passwords.hash("a-long-password"));when(users.findById(1L)).thenReturn(Optional.of(user));when(users.findByEmail("user@example.com")).thenReturn(Optional.of(user));}
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(Role role){return authentication(UsernamePasswordAuthenticationToken.authenticated(new AccountPrincipal(1L,role,0),null,List.of(new SimpleGrantedAuthority("ROLE_"+role))));}
    @Test void anonymousApiIsDenied()throws Exception{mvc.perform(get("/api/offres")).andExpect(status().isUnauthorized());}
    @Test void csrfRequiredEvenForLogin()throws Exception{mvc.perform(post("/api/auth/connexion").contentType("application/json").content("{\"email\":\"user@example.com\",\"motDePasse\":\"a-long-password\"}")).andExpect(status().isForbidden());}
    @Test void roleMismatchCreatesNoSession()throws Exception{mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content("{\"email\":\"user@example.com\",\"motDePasse\":\"a-long-password\",\"role\":\"RECRUTEUR\"}")).andExpect(status().isUnauthorized()).andExpect(result->Assertions.assertNull(result.getRequest().getSession(false)));}
    @Test void loginCreatesServerSession()throws Exception{var result=mvc.perform(post("/api/auth/connexion").with(csrf()).contentType("application/json").content("{\"email\":\"user@example.com\",\"motDePasse\":\"a-long-password\",\"role\":\"ETUDIANT\"}")).andExpect(status().isOk()).andReturn();var session=(org.springframework.mock.web.MockHttpSession)result.getRequest().getSession(false);Assertions.assertNotNull(session);mvc.perform(get("/api/auth/me").session(session)).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(1));}
    @Test void cannotImpersonateOtherStudent()throws Exception{mvc.perform(get("/api/etudiants/2").with(as(Role.ETUDIANT))).andExpect(status().isForbidden());}
    @Test void cannotEnterRecruiterApi()throws Exception{mvc.perform(get("/api/recruteurs/1/offres").with(as(Role.ETUDIANT))).andExpect(status().isForbidden());}
    @Test void versionChangeRevokesSession()throws Exception{user.setSecurityVersion(1L);mvc.perform(get("/api/auth/me").with(as(Role.ETUDIANT))).andExpect(status().isForbidden());}
    @Test void wrongOwnerParameterIsDenied()throws Exception{user.setRole(Role.RECRUTEUR);mvc.perform(get("/api/offres/9").param("recruteurId","2").with(as(Role.RECRUTEUR))).andExpect(status().isForbidden());}
    @Test void forgedBodyOwnerIsDenied()throws Exception{user.setRole(Role.RECRUTEUR);mvc.perform(post("/api/offres").with(as(Role.RECRUTEUR)).with(csrf()).contentType("application/json").content("""
      {"recruteurId":2,"titre":"Poste","description":"Description","domaine":"IT","localisation":"Rabat","typeContrat":"CDI","competences":[{"competenceId":1,"typeExigence":"REQUISE"}]}
      """)).andExpect(status().isForbidden());verifyNoInteractions(service);}
    @Test void passwordHashAndLegacyUpgrade(){Assertions.assertTrue(Passwords.matches("a-long-password",user.getMotDePasse()));Assertions.assertFalse(Passwords.matches("wrong",user.getMotDePasse()));Assertions.assertTrue(Passwords.matches("legacy","legacy"));}
}
