package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.CompetenceAttachRequest;
import com.gi3.matchingcv.dto.EtudiantCompetenceDTO;
import com.gi3.matchingcv.exception.CompetenceDejaDansListeException;
import com.gi3.matchingcv.exception.EmailDejaUtiliseException;
import com.gi3.matchingcv.model.Competence;
import com.gi3.matchingcv.model.Etudiant;
import com.gi3.matchingcv.model.ExperienceProfessionnelle;
import com.gi3.matchingcv.model.Projet;
import com.gi3.matchingcv.model.enums.Role;
import com.gi3.matchingcv.model.enums.StatutCompetence;
import com.gi3.matchingcv.repository.CompetenceRepository;
import com.gi3.matchingcv.repository.EtudiantRepository;
import com.gi3.matchingcv.repository.ExperienceProfessionnelleRepository;
import com.gi3.matchingcv.repository.ProjetRepository;
import com.gi3.matchingcv.repository.UtilisateurRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EtudiantServiceTest {

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private UtilisateurRepository utilisateurRepository;

    @Mock
    private CompetenceRepository competenceRepository;

    @Mock
    private CompetenceService competenceService;

    @Mock
    private ProjetRepository projetRepository;

    @Mock
    private ExperienceProfessionnelleRepository experienceRepository;

    @InjectMocks
    private EtudiantServiceImpl etudiantService;

    private Etudiant etudiant1;
    private Etudiant etudiant2;

    @BeforeEach
    void setUp() {
        etudiant1 = new Etudiant();
        etudiant1.setId(1L);
        etudiant1.setNom("Martin");
        etudiant1.setPrenom("Alice");
        etudiant1.setEmail("alice@example.com");
        etudiant1.setMotDePasse("secret");
        etudiant1.setRole(Role.ETUDIANT);
        etudiant1.setCompetences(new ArrayList<>());

        etudiant2 = new Etudiant();
        etudiant2.setId(2L);
        etudiant2.setNom("Dupont");
        etudiant2.setPrenom("Bob");
        etudiant2.setEmail("bob@example.com");
        etudiant2.setMotDePasse("pass");
        etudiant2.setRole(Role.ETUDIANT);
        etudiant2.setCompetences(new ArrayList<>());
    }

    @Test
    @DisplayName("listerTous() doit retourner tous les étudiants")
    void testListerTous() {
        when(etudiantRepository.findAll()).thenReturn(Arrays.asList(etudiant1, etudiant2));

        List<Etudiant> result = etudiantService.listerTous();

        assertThat(result).hasSize(2).contains(etudiant1, etudiant2);
        verify(etudiantRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("creer() doit enregistrer et retourner l'étudiant quand l'e-mail est libre")
    void testCreerEmailLibre() {
        Etudiant aCreer = new Etudiant();
        aCreer.setNom("Claire");
        aCreer.setPrenom("Leclerc");
        aCreer.setEmail("claire@example.com");
        aCreer.setMotDePasse("mdp123");

        when(utilisateurRepository.existsByEmail("claire@example.com")).thenReturn(false);
        when(etudiantRepository.save(any(Etudiant.class))).thenAnswer(inv -> {
            Etudiant e = inv.getArgument(0);
            e.setId(3L);
            return e;
        });

        Etudiant saved = etudiantService.creer(aCreer);

        assertThat(saved.getId()).isEqualTo(3L);
        assertThat(saved.getRole()).isEqualTo(Role.ETUDIANT);
        assertThat(saved.getEmail()).isEqualTo("claire@example.com");
        verify(utilisateurRepository, times(1)).existsByEmail("claire@example.com");
        verify(etudiantRepository, times(1)).save(aCreer);
    }

    @Test
    @DisplayName("creer() doit lever EmailDejaUtiliseException quand l'e-mail est déjà utilisé")
    void testCreerEmailDejaUtilise() {
        Etudiant aCreer = new Etudiant();
        aCreer.setNom("Alice");
        aCreer.setPrenom("Bis");
        aCreer.setEmail("alice@example.com");
        aCreer.setMotDePasse("autre");

        when(utilisateurRepository.existsByEmail("alice@example.com")).thenReturn(true);

        assertThatThrownBy(() -> etudiantService.creer(aCreer))
                .isInstanceOf(EmailDejaUtiliseException.class)
                .hasMessageContaining("alice@example.com");

        verify(utilisateurRepository, times(1)).existsByEmail("alice@example.com");
        verify(etudiantRepository, never()).save(any());
    }

    @Test
    @DisplayName("trouverParId() doit retourner l'étudiant lorsqu'il existe")
    void testTrouverParIdExistant() {
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));

        Etudiant found = etudiantService.trouverParId(1L);

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo(1L);
        assertThat(found.getEmail()).isEqualTo("alice@example.com");
        verify(etudiantRepository, times(1)).findById(1L);
    }

    @Test
    @DisplayName("trouverParId() doit lever EntityNotFoundException si l'étudiant est absent")
    void testTrouverParIdInexistant() {
        when(etudiantRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> etudiantService.trouverParId(99L))
                .isInstanceOf(EntityNotFoundException.class)
                .hasMessageContaining("99");

        verify(etudiantRepository, times(1)).findById(99L);
    }

    @Test
    @DisplayName("sauvegarder() doit déléguer directement au repository sans vérification d'e-mail")
    void testSauvegarder() {
        when(etudiantRepository.save(etudiant1)).thenReturn(etudiant1);

        Etudiant result = etudiantService.sauvegarder(etudiant1);

        assertThat(result).isEqualTo(etudiant1);
        verify(utilisateurRepository, never()).existsByEmail(any());
        verify(etudiantRepository, times(1)).save(etudiant1);
    }

    // -------------------------------------------------------------------------
    // Tests de gestion des compétences personnelles de l'étudiant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("ajouterCompetence() avec compétence existante (VALIDEE) doit la rattacher à l'étudiant")
    void testAjouterCompetenceExistanteValidee() {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Spring Boot");
        c.setStatut(StatutCompetence.VALIDEE);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(competenceService.trouverParId(10L)).thenReturn(c);
        when(etudiantRepository.save(etudiant1)).thenReturn(etudiant1);

        CompetenceAttachRequest req = new CompetenceAttachRequest();
        req.setCompetenceId(10L);

        Competence ajoutee = etudiantService.ajouterCompetence(1L, req);

        assertThat(ajoutee).isEqualTo(c);
        assertThat(etudiant1.getCompetences()).contains(c);
        verify(etudiantRepository, times(1)).save(etudiant1);
    }

    @Test
    @DisplayName("ajouterCompetence() avec nouvelle compétence doit la créer (EN_ATTENTE, proposeePar) et la rattacher")
    void testAjouterNouvelleCompetenceEnAttenteRattachee() {
        Competence c = new Competence();
        c.setId(20L);
        c.setNom("Rust");
        c.setNomNormalise("rust");
        c.setStatut(StatutCompetence.EN_ATTENTE);
        c.setProposeePar(etudiant1);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(competenceRepository.findByNomNormalise("rust")).thenReturn(Optional.empty());
        when(competenceService.proposer("Rust", "Backend", etudiant1)).thenReturn(c);
        when(etudiantRepository.save(etudiant1)).thenReturn(etudiant1);

        CompetenceAttachRequest req = new CompetenceAttachRequest();
        req.setNouvelleCompetence("Rust");
        req.setCategorie("Backend");

        Competence ajoutee = etudiantService.ajouterCompetence(1L, req);

        assertThat(ajoutee).isEqualTo(c);
        assertThat(ajoutee.getStatut()).isEqualTo(StatutCompetence.EN_ATTENTE);
        assertThat(ajoutee.getProposeePar()).isEqualTo(etudiant1);
        assertThat(etudiant1.getCompetences()).contains(c);
        verify(competenceService, times(1)).proposer("Rust", "Backend", etudiant1);
        verify(etudiantRepository, times(1)).save(etudiant1);
    }

    @Test
    @DisplayName("ajouterCompetence() avec variante 'SpringBoot' doit rattacher 'Spring Boot' existante sans doublon")
    void testAjouterVarianteSpringBootRattacheeASpringBootSansDoublon() {
        Competence existante = new Competence();
        existante.setId(10L);
        existante.setNom("Spring Boot");
        existante.setNomNormalise("springboot");
        existante.setStatut(StatutCompetence.VALIDEE);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));
        when(etudiantRepository.save(etudiant1)).thenReturn(etudiant1);

        CompetenceAttachRequest req = new CompetenceAttachRequest();
        req.setNouvelleCompetence("SpringBoot");
        req.setCategorie("Backend");

        Competence ajoutee = etudiantService.ajouterCompetence(1L, req);

        assertThat(ajoutee.getId()).isEqualTo(10L);
        assertThat(ajoutee.getNom()).isEqualTo("Spring Boot");
        assertThat(etudiant1.getCompetences()).contains(existante);
        verify(competenceService, never()).proposer(any(), any(), any());
        verify(etudiantRepository, times(1)).save(etudiant1);
    }

    @Test
    @DisplayName("ajouterCompetence() déjà dans sa liste doit lever CompetenceDejaDansListeException (409)")
    void testAjouterCompetenceEnDoubleDoitLeverExceptionConflit() {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Spring Boot");
        etudiant1.getCompetences().add(c);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(competenceService.trouverParId(10L)).thenReturn(c);

        CompetenceAttachRequest req = new CompetenceAttachRequest();
        req.setCompetenceId(10L);

        assertThatThrownBy(() -> etudiantService.ajouterCompetence(1L, req))
                .isInstanceOf(CompetenceDejaDansListeException.class)
                .hasMessage("Cette compétence est déjà dans votre liste");

        verify(etudiantRepository, never()).save(any());
    }

    @Test
    @DisplayName("ajouterCompetence() avec variante point 'spring.boot' quand 'Spring Boot' est déjà dans sa liste doit lever 409")
    void testAjouterVariantePointSpringBootDejaDansListeDoitLever409() {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Spring Boot");
        c.setNomNormalise("springboot");
        etudiant1.getCompetences().add(c);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));

        CompetenceAttachRequest req = new CompetenceAttachRequest();
        req.setNouvelleCompetence("spring.boot");
        req.setCategorie("DevOps");

        assertThatThrownBy(() -> etudiantService.ajouterCompetence(1L, req))
                .isInstanceOf(CompetenceDejaDansListeException.class)
                .hasMessage("Cette compétence est déjà dans votre liste");

        verify(competenceService, never()).proposer(any(), any(), any());
        verify(competenceRepository, never()).findByNomNormalise(any());
        verify(etudiantRepository, never()).save(any());
    }

    @Test
    @DisplayName("retirerCompetence() doit retirer de la liste et détacher de projets et expériences sans toucher au dictionnaire")
    void testRetirerCompetenceDetacheProjetsEtExperiencesEtLaisseDictionnaireIntact() {
        Competence c = new Competence();
        c.setId(10L);
        c.setNom("Spring Boot");
        etudiant1.getCompetences().add(c);

        Projet p = new Projet();
        p.setId(100L);
        p.setTitre("Mon Projet");
        p.getCompetences().add(c);

        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setId(200L);
        exp.setPoste("Stagiaire");
        exp.getCompetences().add(c);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of(p));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));

        etudiantService.retirerCompetence(1L, 10L);

        assertThat(etudiant1.getCompetences()).doesNotContain(c);
        assertThat(p.getCompetences()).doesNotContain(c);
        assertThat(exp.getCompetences()).doesNotContain(c);

        verify(etudiantRepository, times(1)).save(etudiant1);
        verify(projetRepository, times(1)).save(p);
        verify(experienceRepository, times(1)).save(exp);
        verify(competenceRepository, never()).delete(any());
        verify(competenceRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("suggererCompetences() doit exclure les REJETEE, les EN_ATTENTE d'autrui, et celles déjà dans sa liste")
    void testSuggererCompetencesExclutRejeteeEtEnAttenteDautruiEtDejaDansSaListe() {
        // c1: VALIDEE, pas dans la liste -> doit être incluse
        Competence c1 = new Competence();
        c1.setId(1L);
        c1.setNom("Spring Boot");
        c1.setNomNormalise("springboot");
        c1.setStatut(StatutCompetence.VALIDEE);

        // c2: VALIDEE, déjà dans sa liste -> doit être exclue
        Competence c2 = new Competence();
        c2.setId(2L);
        c2.setNom("Spring Cloud");
        c2.setNomNormalise("springcloud");
        c2.setStatut(StatutCompetence.VALIDEE);
        etudiant1.getCompetences().add(c2);

        // c3: REJETEE -> doit être exclue
        Competence c3 = new Competence();
        c3.setId(3L);
        c3.setNom("Spring Bad");
        c3.setNomNormalise("springbad");
        c3.setStatut(StatutCompetence.REJETEE);

        // c4: EN_ATTENTE proposée par autrui (etudiant2) -> doit être exclue
        Competence c4 = new Competence();
        c4.setId(4L);
        c4.setNom("Spring Other");
        c4.setNomNormalise("springother");
        c4.setStatut(StatutCompetence.EN_ATTENTE);
        c4.setProposeePar(etudiant2);

        // c5: EN_ATTENTE proposée par l'étudiant lui-même (etudiant1) -> doit être incluse
        Competence c5 = new Competence();
        c5.setId(5L);
        c5.setNom("Spring Mine");
        c5.setNomNormalise("springmine");
        c5.setStatut(StatutCompetence.EN_ATTENTE);
        c5.setProposeePar(etudiant1);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant1));
        when(competenceRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4, c5));

        List<Competence> suggestions = etudiantService.suggererCompetences(1L, "spring");

        assertThat(suggestions).hasSize(2)
                .extracting(Competence::getId)
                .containsExactly(1L, 5L);
    }
}
