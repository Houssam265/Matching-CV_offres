package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
import com.gi3.matchingcv.event.CompetenceStatutChangeEvent;
import com.gi3.matchingcv.exception.CompetenceDejaExistanteException;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires complets de l'espace administrateur (Lots 1 à 4).
 * Couvre la fusion, validation, rejet, notification admin, limite de propositions,
 * suspension des comptes, journalisation d'audit et calcul des statistiques.
 */
@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    // ── Mocks communs ──
    @Mock private CompetenceRepository competenceRepository;
    @Mock private AuditService auditService;
    @Mock private NotificationService notificationService;
    @Mock private CompetenceService competenceService;
    @Mock private OffreCompetenceRepository offreCompetenceRepository;
    @Mock private ProfilCompetenceRepository profilCompetenceRepository;
    @Mock private EntityManager entityManager;
    @Mock private Query nativeQuery;

    @Mock private UtilisateurRepository utilisateurRepository;
    @Mock private AdministrateurRepository administrateurRepository;
    @Mock private EtudiantRepository etudiantRepository;
    @Mock private RecruteurRepository recruteurRepository;
    @Mock private OffreRepository offreRepository;
    @Mock private CandidatureRepository candidatureRepository;
    @Mock private ProjetRepository projetRepository;
    @Mock private AuditLogRepository auditLogRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AdminCompetenceServiceImpl adminCompetenceService;
    private AdminUtilisateurServiceImpl adminUtilisateurService;
    private AdminStatistiquesServiceImpl adminStatistiquesService;
    private AuditServiceImpl auditServiceImpl;
    private CompetenceServiceImpl competenceServiceImpl;
    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        adminCompetenceService = new AdminCompetenceServiceImpl(
                competenceRepository,
                auditService,
                notificationService,
                competenceService,
                offreCompetenceRepository,
                profilCompetenceRepository,
                entityManager
        );

        adminUtilisateurService = new AdminUtilisateurServiceImpl(
                utilisateurRepository,
                administrateurRepository,
                etudiantRepository,
                recruteurRepository,
                offreRepository,
                candidatureRepository,
                projetRepository,
                auditService
        );

        adminStatistiquesService = new AdminStatistiquesServiceImpl(
                utilisateurRepository,
                offreRepository,
                candidatureRepository,
                competenceRepository,
                offreCompetenceRepository,
                etudiantRepository
        );

        auditServiceImpl = new AuditServiceImpl(
                auditLogRepository,
                administrateurRepository
        );

        competenceServiceImpl = new CompetenceServiceImpl(
                competenceRepository,
                profilCompetenceRepository,
                offreCompetenceRepository,
                entityManager,
                eventPublisher,
                notificationService,
                administrateurRepository
        );
        ReflectionTestUtils.setField(competenceServiceImpl, "propositionsLimite", 10);

        authService = new AuthServiceImpl(
                utilisateurRepository,
                etudiantRepository,
                recruteurRepository
        );
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOT 1 : FUSION, VALIDATION, REJET, LIMITES & NOTIFICATIONS
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("LOT 1 — Modération & Fusion des compétences")
    class Lot1ModerationTests {

        @Test
        @DisplayName("Fusion : Re-pointage OffreCompetence avec REQUISE prioritaire sur ATOUT, synonymes enrichis, suppression source et notification auteur")
        void testFusionnerCompetences() {
            Long sourceId = 10L;
            Long cibleId = 20L;
            Long adminId = 99L;

            Etudiant auteur = new Etudiant();
            auteur.setId(5L);
            auteur.setEmail("auteur@example.com");

            Competence source = new Competence();
            source.setId(sourceId);
            source.setNom("Springboot");
            source.setStatut(StatutCompetence.EN_ATTENTE);
            source.setProposeePar(auteur);
            source.setSynonymes(new ArrayList<>(List.of("SpringBoot 3")));

            Competence cible = new Competence();
            cible.setId(cibleId);
            cible.setNom("Spring Boot");
            cible.setStatut(StatutCompetence.VALIDEE);
            cible.setSynonymes(new ArrayList<>(List.of("Spring")));

            when(competenceRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(competenceRepository.findById(cibleId)).thenReturn(Optional.of(cible));

            when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.executeUpdate()).thenReturn(1);

            // Mock OffreCompetence : une offre ayant à la fois source (REQUISE) et cible (ATOUT)
            Offre offre1 = new Offre();
            offre1.setId(100L);

            OffreCompetence ocSource1 = new OffreCompetence();
            ocSource1.setId(1L);
            ocSource1.setOffre(offre1);
            ocSource1.setCompetence(source);
            ocSource1.setTypeExigence(TypeExigence.REQUISE);

            OffreCompetence ocCible1 = new OffreCompetence();
            ocCible1.setId(2L);
            ocCible1.setOffre(offre1);
            ocCible1.setCompetence(cible);
            ocCible1.setTypeExigence(TypeExigence.ATOUT);

            // Deuxième offre ayant uniquement source (ATOUT)
            Offre offre2 = new Offre();
            offre2.setId(200L);

            OffreCompetence ocSource2 = new OffreCompetence();
            ocSource2.setId(3L);
            ocSource2.setOffre(offre2);
            ocSource2.setCompetence(source);
            ocSource2.setTypeExigence(TypeExigence.ATOUT);

            when(offreCompetenceRepository.findByCompetenceId(sourceId)).thenReturn(List.of(ocSource1, ocSource2));
            when(offreCompetenceRepository.findByCompetenceId(cibleId)).thenReturn(new ArrayList<>(List.of(ocCible1)));
            when(profilCompetenceRepository.findByCompetenceId(sourceId)).thenReturn(Collections.emptyList());

            // Exécution
            adminCompetenceService.fusionner(sourceId, cibleId, adminId);

            // 1. Vérification : ocCible1 doit devenir REQUISE (car ocSource1 était REQUISE)
            assertThat(ocCible1.getTypeExigence()).isEqualTo(TypeExigence.REQUISE);
            verify(offreCompetenceRepository).save(ocCible1);
            verify(offreCompetenceRepository).delete(ocSource1);

            // 2. Vérification : ocSource2 est re-pointé vers la cible
            assertThat(ocSource2.getCompetence()).isEqualTo(cible);
            verify(offreCompetenceRepository).save(ocSource2);

            // 3. Synonymes de la cible doivent contenir "Springboot" et "SpringBoot 3"
            ArgumentCaptor<Competence> cibleCaptor = ArgumentCaptor.forClass(Competence.class);
            verify(competenceRepository, atLeastOnce()).save(cibleCaptor.capture());
            Competence cibleSauvee = cibleCaptor.getValue();
            assertThat(cibleSauvee.getSynonymes()).contains("Spring", "Springboot", "SpringBoot 3");

            // 4. Source supprimée
            verify(competenceRepository).delete(source);

            // 5. Notification envoyée à l'auteur (avec lien vers cibleId)
            verify(notificationService).notifier(
                    eq(5L),
                    eq(TypeNotification.COMPETENCE_VALIDEE),
                    eq("Compétence fusionnée"),
                    contains("fusionnée"),
                    anyString(),
                    isNull(), isNull(), eq(cibleId), isNull(), eq(adminId)
            );

            // 6. Action journalisée
            verify(auditService).journaliser(eq(adminId), eq("FUSIONNER_COMPETENCE"), anyString(), any());
        }

        @Test
        @DisplayName("Validation : Délègue à competenceService.changerStatut() et journalise sans duplication de notification")
        void testValiderCompetence() {
            Long compId = 15L;
            Long adminId = 1L;

            Competence comp = new Competence();
            comp.setId(compId);
            comp.setNom("Docker");
            comp.setStatut(StatutCompetence.EN_ATTENTE);

            when(competenceRepository.findById(compId)).thenReturn(Optional.of(comp));
            when(competenceService.changerStatut(compId, StatutCompetence.VALIDEE)).thenReturn(comp);

            adminCompetenceService.validerAvecModifications(compId, "Docker Pro", "DevOps", List.of("Containers"), adminId);

            // Vérifie les modifications
            assertThat(comp.getNom()).isEqualTo("Docker Pro");
            assertThat(comp.getCategorie()).isEqualTo("DevOps");
            assertThat(comp.getSynonymes()).contains("Containers");

            verify(competenceService).changerStatut(compId, StatutCompetence.VALIDEE);
            // Vérifie qu'on n'a pas appelé notificationService directement (car géré par l'événement de changerStatut)
            verify(notificationService, never()).notifier(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(auditService).journaliser(eq(adminId), eq("VALIDER_COMPETENCE"), anyString(), isNull());
        }

        @Test
        @DisplayName("Rejet : Délègue à competenceService.changerStatut() avec motif dans le journal")
        void testRejeterCompetence() {
            Long compId = 16L;
            Long adminId = 1L;
            String motif = "Nom trop imprécis";

            Competence comp = new Competence();
            comp.setId(compId);
            comp.setNom("Truc");
            comp.setStatut(StatutCompetence.EN_ATTENTE);

            when(competenceRepository.findById(compId)).thenReturn(Optional.of(comp));
            when(competenceService.changerStatut(compId, StatutCompetence.REJETEE, motif)).thenReturn(comp);

            adminCompetenceService.rejeterAvecMotif(compId, motif, adminId);

            verify(competenceService).changerStatut(compId, StatutCompetence.REJETEE, motif);
            verify(notificationService, never()).notifier(any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
            verify(auditService).journaliser(eq(adminId), eq("REJETER_COMPETENCE"), anyString(), eq(motif));
        }

        @Test
        @DisplayName("Validation tel quel : conserve le nom, la catégorie et délègue à competenceService.changerStatut()")
        void testValiderTelQuel() {
            Long compId = 18L;
            Long adminId = 1L;

            Competence comp = new Competence();
            comp.setId(compId);
            comp.setNom("PostgreSQL");
            comp.setCategorie("Base de données");
            comp.setStatut(StatutCompetence.EN_ATTENTE);

            when(competenceRepository.findById(compId)).thenReturn(Optional.of(comp));
            when(competenceService.changerStatut(compId, StatutCompetence.VALIDEE)).thenReturn(comp);

            adminCompetenceService.validerAvecModifications(compId, null, null, null, adminId);

            assertThat(comp.getNom()).isEqualTo("PostgreSQL");
            assertThat(comp.getCategorie()).isEqualTo("Base de données");
            verify(competenceService).changerStatut(compId, StatutCompetence.VALIDEE);
            verify(auditService).journaliser(eq(adminId), eq("VALIDER_COMPETENCE"), anyString(), isNull());
        }

        @Test
        @DisplayName("Validation avec modification : collision de nom refusée avec 409 (CompetenceDejaExistanteException)")
        void testValiderAvecCollisionNom_Refuse409() {
            Long compId = 19L;
            Long adminId = 1L;

            Competence comp = new Competence();
            comp.setId(compId);
            comp.setNom("Spring Boot 3");
            comp.setStatut(StatutCompetence.EN_ATTENTE);

            Competence existante = new Competence();
            existante.setId(25L);
            existante.setNom("Spring Boot");
            existante.setNomNormalise("springboot");

            when(competenceRepository.findById(compId)).thenReturn(Optional.of(comp));
            when(competenceRepository.findByNomNormalise("springboot")).thenReturn(Optional.of(existante));

            assertThatThrownBy(() -> adminCompetenceService.validerAvecModifications(
                    compId, "Spring Boot", "Backend", null, adminId))
                    .isInstanceOf(CompetenceDejaExistanteException.class)
                    .hasMessageContaining("Une compétence existe déjà avec ce nom (« Spring Boot »)");

            verify(competenceService, never()).changerStatut(anyLong(), any());
        }

        @Test
        @DisplayName("Décision déjà prise : retourne 409 (CompetenceDejaTraiteeException) si la compétence n'est plus EN_ATTENTE")
        void testDecisionDejaPrise_Refuse409() {
            Long compId = 21L;
            Long cibleId = 22L;
            Long adminId = 1L;

            Competence comp = new Competence();
            comp.setId(compId);
            comp.setNom("Vue.js");
            comp.setStatut(StatutCompetence.VALIDEE); // déjà validée

            // La cible doit aussi être mockée pour que la fusion atteigne le check de statut de la source
            Competence cible = new Competence();
            cible.setId(cibleId);
            cible.setNom("Vue");
            cible.setStatut(StatutCompetence.VALIDEE);

            when(competenceRepository.findById(compId)).thenReturn(Optional.of(comp));
            when(competenceRepository.findById(cibleId)).thenReturn(Optional.of(cible));

            // 1. Validation impossible
            assertThatThrownBy(() -> adminCompetenceService.validerAvecModifications(compId, null, null, null, adminId))
                    .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaTraiteeException.class)
                    .hasMessageContaining("déjà été traitée");

            // 2. Rejet impossible
            assertThatThrownBy(() -> adminCompetenceService.rejeterAvecMotif(compId, "motif", adminId))
                    .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaTraiteeException.class)
                    .hasMessageContaining("déjà été traitée");

            // 3. Fusion impossible (source déjà traitée)
            assertThatThrownBy(() -> adminCompetenceService.fusionner(compId, cibleId, adminId))
                    .isInstanceOf(com.gi3.matchingcv.exception.CompetenceDejaTraiteeException.class)
                    .hasMessageContaining("déjà été traitée");
        }

        @Test
        @DisplayName("Fusion : la cible ne peut être ni EN_ATTENTE ni REJETEE")
        void testFusionCibleInvalide() {
            Long sourceId = 30L;
            Long cibleId = 31L;
            Long adminId = 1L;

            Competence source = new Competence();
            source.setId(sourceId);
            source.setNom("ReactJS");
            source.setStatut(StatutCompetence.EN_ATTENTE);

            Competence cible = new Competence();
            cible.setId(cibleId);
            cible.setNom("React");
            cible.setStatut(StatutCompetence.EN_ATTENTE); // cible non validée !

            when(competenceRepository.findById(sourceId)).thenReturn(Optional.of(source));
            when(competenceRepository.findById(cibleId)).thenReturn(Optional.of(cible));

            assertThatThrownBy(() -> adminCompetenceService.fusionner(sourceId, cibleId, adminId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("La compétence cible doit être validée");

            verify(competenceRepository, never()).delete(source);
        }

        @Test
        @DisplayName("Actions groupées : valider-lot et rejeter-lot traitent chaque élément")
        void testActionsGroupees() {
            Long adminId = 1L;

            Competence c1 = new Competence();
            c1.setId(101L);
            c1.setNom("Git");
            c1.setStatut(StatutCompetence.EN_ATTENTE);

            Competence c2 = new Competence();
            c2.setId(102L);
            c2.setNom("GitLab");
            c2.setStatut(StatutCompetence.EN_ATTENTE);

            when(competenceRepository.findById(101L)).thenReturn(Optional.of(c1));
            when(competenceRepository.findById(102L)).thenReturn(Optional.of(c2));
            when(competenceService.changerStatut(eq(101L), eq(StatutCompetence.VALIDEE))).thenReturn(c1);
            when(competenceService.changerStatut(eq(102L), eq(StatutCompetence.VALIDEE))).thenReturn(c2);

            adminCompetenceService.validerGroupes(List.of(101L, 102L), adminId);

            verify(competenceService).changerStatut(101L, StatutCompetence.VALIDEE);
            verify(competenceService).changerStatut(102L, StatutCompetence.VALIDEE);

            // Rejet en lot
            Competence c3 = new Competence();
            c3.setId(103L);
            c3.setNom("Invalide1");
            c3.setStatut(StatutCompetence.EN_ATTENTE);

            Competence c4 = new Competence();
            c4.setId(104L);
            c4.setNom("Invalide2");
            c4.setStatut(StatutCompetence.EN_ATTENTE);

            when(competenceRepository.findById(103L)).thenReturn(Optional.of(c3));
            when(competenceRepository.findById(104L)).thenReturn(Optional.of(c4));
            when(competenceService.changerStatut(eq(103L), eq(StatutCompetence.REJETEE), eq("Hors sujet"))).thenReturn(c3);
            when(competenceService.changerStatut(eq(104L), eq(StatutCompetence.REJETEE), eq("Hors sujet"))).thenReturn(c4);

            adminCompetenceService.rejeterGroupes(List.of(103L, 104L), "Hors sujet", adminId);

            verify(competenceService).changerStatut(103L, StatutCompetence.REJETEE, "Hors sujet");
            verify(competenceService).changerStatut(104L, StatutCompetence.REJETEE, "Hors sujet");
        }

        @Test
        @DisplayName("File de modération : ordre du plus ancien au plus récent, filtres et détection des doublons probables")
        void testFileDeModerationEtDoublonsProbables() {
            LocalDateTime t0 = LocalDateTime.of(2026, 1, 1, 10, 0);
            LocalDateTime t1 = LocalDateTime.of(2026, 1, 2, 10, 0);
            LocalDateTime t2 = LocalDateTime.of(2026, 1, 3, 10, 0);

            Etudiant etu = new Etudiant();
            etu.setId(1L);
            etu.setRole(Role.ETUDIANT);
            etu.setNom("Martin");
            etu.setPrenom("Paul");

            Recruteur rec = new Recruteur();
            rec.setId(2L);
            rec.setRole(Role.RECRUTEUR);
            rec.setNom("Dupont");
            rec.setPrenom("Claire");

            Competence c1 = new Competence();
            c1.setId(1L);
            c1.setNom("ReactJS");
            c1.setNomNormalise("reactjs");
            c1.setCategorie("Frontend");
            c1.setStatut(StatutCompetence.EN_ATTENTE);
            c1.setDateProposition(t2); // la plus récente
            c1.setProposeePar(etu);

            Competence c2 = new Competence();
            c2.setId(2L);
            c2.setNom("TypeScrip");
            c2.setNomNormalise("typescrip");
            c2.setCategorie("Langage");
            c2.setStatut(StatutCompetence.EN_ATTENTE);
            c2.setDateProposition(t0); // la plus ancienne
            c2.setProposeePar(rec);

            Competence c3 = new Competence();
            c3.setId(3L);
            c3.setNom("Vue 3");
            c3.setNomNormalise("vue 3");
            c3.setCategorie("Frontend");
            c3.setStatut(StatutCompetence.EN_ATTENTE);
            c3.setDateProposition(t1); // intermédiaire
            c3.setProposeePar(etu);

            // Compétences validées pour la détection de doublons
            Competence val1 = new Competence();
            val1.setId(50L);
            val1.setNom("React");
            val1.setNomNormalise("react");
            val1.setStatut(StatutCompetence.VALIDEE);
            val1.setSynonymes(List.of("React.js"));

            Competence val2 = new Competence();
            val2.setId(51L);
            val2.setNom("TypeScript");
            val2.setNomNormalise("typescript");
            val2.setStatut(StatutCompetence.VALIDEE);

            when(competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE)).thenReturn(new ArrayList<>(List.of(c1, c2, c3)));
            when(competenceRepository.findByStatut(StatutCompetence.VALIDEE)).thenReturn(List.of(val1, val2));

            when(entityManager.createNativeQuery(anyString())).thenReturn(nativeQuery);
            when(nativeQuery.setParameter(anyString(), any())).thenReturn(nativeQuery);
            when(nativeQuery.getSingleResult()).thenReturn(0L);

            // 1. Liste complète sans filtre : tri de la plus ancienne à la plus récente (c2, c3, c1)
            List<CompetenceAModererDto> file = adminCompetenceService.listerAModerer(null, null);
            assertThat(file).hasSize(3);
            assertThat(file.get(0).id()).isEqualTo(2L); // t0
            assertThat(file.get(1).id()).isEqualTo(3L); // t1
            assertThat(file.get(2).id()).isEqualTo(1L); // t2

            // Vérification des doublons probables détectés pour "ReactJS" (doit détecter "React" par inclusion/préfixe)
            CompetenceAModererDto reactDto = file.stream().filter(d -> d.id().equals(1L)).findFirst().orElseThrow();
            assertThat(reactDto.doublonsProbables()).contains("React");

            // Vérification des doublons probables pour "TypeScrip" (doit détecter "TypeScript" par distance/préfixe)
            CompetenceAModererDto tsDto = file.stream().filter(d -> d.id().equals(2L)).findFirst().orElseThrow();
            assertThat(tsDto.doublonsProbables()).contains("TypeScript");

            // 2. Filtre par rôle ETUDIANT (doit exclure c2 qui est proposé par un recruteur)
            when(competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE)).thenReturn(new ArrayList<>(List.of(c1, c2, c3)));
            List<CompetenceAModererDto> etuFile = adminCompetenceService.listerAModerer(Role.ETUDIANT, null);
            assertThat(etuFile).hasSize(2);
            assertThat(etuFile).extracting(CompetenceAModererDto::id).containsExactly(3L, 1L);

            // 3. Filtre par recherche "react"
            when(competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE)).thenReturn(new ArrayList<>(List.of(c1, c2, c3)));
            List<CompetenceAModererDto> searchFile = adminCompetenceService.listerAModerer(null, "react");
            assertThat(searchFile).hasSize(1);
            assertThat(searchFile.get(0).nom()).isEqualTo("ReactJS");
        }

        @Test
        @DisplayName("Re-proposition d'une compétence rejetée : refuse avec message clair contenant le motif de rejet")
        void testRepropositionCompetenceRejeteeAfficheMotif() {
            Etudiant etudiant = new Etudiant();
            etudiant.setId(10L);

            Competence rejetee = new Competence();
            rejetee.setId(90L);
            rejetee.setNom("Fortran");
            rejetee.setNomNormalise("fortran");
            rejetee.setStatut(StatutCompetence.REJETEE);
            rejetee.setMotifRejet("Technologie obsolète pour ce cursus");

            when(competenceRepository.countByProposeePar_IdAndStatut(eq(10L), eq(StatutCompetence.EN_ATTENTE))).thenReturn(0L);
            when(competenceRepository.findByNomNormalise("fortran")).thenReturn(Optional.of(rejetee));

            assertThatThrownBy(() -> competenceServiceImpl.proposer("Fortran", "Langage", etudiant))
                    .isInstanceOf(CompetenceDejaExistanteException.class)
                    .hasMessageContaining("Cette compétence a été rejetée pour le motif suivant : Technologie obsolète pour ce cursus");

            verify(competenceRepository, never()).save(any());
        }

        @Test
        @DisplayName("Proposition de compétence : notifie tous les administrateurs avec COMPETENCE_A_MODERER")
        void testProposerCompetenceNotifieAdmins() {
            Etudiant etudiant = new Etudiant();
            etudiant.setId(10L);

            Administrateur admin1 = new Administrateur();
            admin1.setId(101L);
            Administrateur admin2 = new Administrateur();
            admin2.setId(102L);

            when(competenceRepository.findByNomNormalise(anyString())).thenReturn(Optional.empty());
            when(competenceRepository.countByProposeePar_IdAndStatut(eq(10L), eq(StatutCompetence.EN_ATTENTE))).thenReturn(2L);
            when(administrateurRepository.findAll()).thenReturn(List.of(admin1, admin2));
            when(competenceRepository.save(any(Competence.class))).thenAnswer(i -> {
                Competence c = i.getArgument(0);
                c.setId(50L);
                return c;
            });

            competenceServiceImpl.proposer("Kubernetes", "DevOps", etudiant);

            // Vérifie que les 2 administrateurs ont bien été notifiés
            verify(notificationService).notifier(
                    eq(101L),
                    eq(TypeNotification.COMPETENCE_A_MODERER),
                    anyString(),
                    contains("Kubernetes"),
                    eq("/admin-moderation.html"),
                    isNull(), isNull(), eq(50L), isNull(), eq(10L)
            );
            verify(notificationService).notifier(
                    eq(102L),
                    eq(TypeNotification.COMPETENCE_A_MODERER),
                    anyString(),
                    contains("Kubernetes"),
                    eq("/admin-moderation.html"),
                    isNull(), isNull(), eq(50L), isNull(), eq(10L)
            );
        }

        @Test
        @DisplayName("Limite de 10 propositions : la 11e proposition lève une IllegalStateException")
        void testLimiteDixPropositions() {
            Etudiant etudiant = new Etudiant();
            etudiant.setId(12L);

            when(competenceRepository.countByProposeePar_IdAndStatut(eq(12L), eq(StatutCompetence.EN_ATTENTE))).thenReturn(10L);

            assertThatThrownBy(() -> competenceServiceImpl.proposer("Python", "Langage", etudiant))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Vous avez atteint la limite de 10 propositions");

            verify(competenceRepository, never()).save(any());
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOT 3 : SUSPENSION QUI BLOQUE LA CONNEXION & GESTION UTILISATEURS
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("LOT 3 — Suspension & Connexion")
    class Lot3UtilisateursTests {

        @Test
        @DisplayName("Suspension par l'admin : met actif=false et journalise")
        void testSuspendreUtilisateur() {
            Long userId = 42L;
            Long adminId = 1L;

            Etudiant u = new Etudiant();
            u.setId(userId);
            u.setActif(true);
            u.setActive(true);
            u.setNom("Dupont");
            u.setPrenom("Jean");
            u.setEmail("jean@test.com");
            u.setRole(Role.ETUDIANT);

            when(utilisateurRepository.findById(userId)).thenReturn(Optional.of(u));

            adminUtilisateurService.changerStatut(userId, false, adminId);

            assertThat(u.isActif()).isFalse();
            assertThat(u.isActive()).isFalse();
            verify(utilisateurRepository).save(u);
            verify(auditService).journaliser(eq(adminId), eq("SUSPENDRE_UTILISATEUR"), anyString(), anyString());
        }

        @Test
        @DisplayName("Connexion bloquée : un compte suspendu (actif=false) ne peut plus se connecter")
        void testCompteSuspenduNePeutPasSeConnecter() {
            Etudiant suspendu = new Etudiant();
            suspendu.setId(77L);
            suspendu.setEmail("suspendu@example.com");
            suspendu.setMotDePasse(com.gi3.matchingcv.security.Passwords.hash("Secret123!"));
            suspendu.setActif(false);
            suspendu.setActive(false);
            suspendu.setRole(Role.ETUDIANT);

            when(utilisateurRepository.findByEmail("suspendu@example.com")).thenReturn(Optional.of(suspendu));

            ConnexionRequest req = new ConnexionRequest();
            req.setEmail("suspendu@example.com");
            req.setMotDePasse("Secret123!");
            req.setRole(Role.ETUDIANT);

            assertThatThrownBy(() -> authService.connecter(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Compte désactivé. Contactez l'administrateur.");
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    // LOT 4 : JOURNAL D'AUDIT & STATISTIQUES
    // ══════════════════════════════════════════════════════════════════════════
    @Nested
    @DisplayName("LOT 4 — Journal d'audit & Statistiques")
    class Lot4JournalEtStatsTests {

        @Test
        @DisplayName("Journal d'audit : enregistre bien l'action avec le nom de l'admin")
        void testJournaliserAction() {
            Long adminId = 1L;
            Administrateur admin = new Administrateur();
            admin.setId(adminId);
            admin.setPrenom("Alice");
            admin.setNom("Admin");

            when(administrateurRepository.findById(adminId)).thenReturn(Optional.of(admin));
            when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

            AuditLog log = auditServiceImpl.journaliser(adminId, "CLOTURER_OFFRE", "Offre #12 'Dev'", "Poste pourvu");

            assertThat(log).isNotNull();
            assertThat(log.getAdminNom()).isEqualTo("Alice Admin");
            assertThat(log.getAction()).isEqualTo("CLOTURER_OFFRE");
            assertThat(log.getCible()).isEqualTo("Offre #12 'Dev'");
            assertThat(log.getDetails()).isEqualTo("Poste pourvu");
            verify(auditLogRepository).save(any(AuditLog.class));
        }

        @Test
        @DisplayName("Statistiques sur jeu de données connu : calculs exacts des indicateurs")
        void testCalculStatistiquesGlobales() {
            // 1. Utilisateurs
            Etudiant etu1 = new Etudiant(); etu1.setRole(Role.ETUDIANT); etu1.setDateInscription(LocalDateTime.now());
            Etudiant etu2 = new Etudiant(); etu2.setRole(Role.ETUDIANT); etu2.setDateInscription(LocalDateTime.now());
            Recruteur rec1 = new Recruteur(); rec1.setRole(Role.RECRUTEUR); rec1.setDateInscription(LocalDateTime.now());
            Administrateur adm1 = new Administrateur(); adm1.setRole(Role.ADMIN); adm1.setDateInscription(LocalDateTime.now());
            when(utilisateurRepository.findAll()).thenReturn(List.of(etu1, etu2, rec1, adm1));

            // 2. Offres
            Offre off1 = new Offre(); off1.setDomaine("Informatique"); off1.setStatut(StatutOffre.ACTIVE);
            Offre off2 = new Offre(); off2.setDomaine("Informatique"); off2.setStatut(StatutOffre.CLOTUREE);
            Offre off3 = new Offre(); off3.setDomaine("Finance"); off3.setStatut(StatutOffre.ACTIVE);
            when(offreRepository.findAll()).thenReturn(List.of(off1, off2, off3));

            // 3. Candidatures (1 acceptée, 1 refusée, 1 envoyée, 1 vue) => score moyen = (80 + 60) / 2 = 70.0
            Candidature c1 = new Candidature(); c1.setStatut(StatutCandidature.ACCEPTEE); c1.setScore(80.0);
            Candidature c2 = new Candidature(); c2.setStatut(StatutCandidature.REFUSEE); c2.setScore(60.0);
            Candidature c3 = new Candidature(); c3.setStatut(StatutCandidature.ENVOYEE); c3.setScore(null);
            Candidature c4 = new Candidature(); c4.setStatut(StatutCandidature.VUE); c4.setScore(null);
            when(candidatureRepository.findAll()).thenReturn(List.of(c1, c2, c3, c4));

            // 4. Compétences en attente
            when(competenceRepository.findByStatut(StatutCompetence.EN_ATTENTE)).thenReturn(List.of(new Competence(), new Competence()));

            // 5. Compétences demandées vs possédées
            Competence compJava = new Competence(); compJava.setNom("Java");
            OffreCompetence oc1 = new OffreCompetence(); oc1.setCompetence(compJava);
            when(offreCompetenceRepository.findAll()).thenReturn(List.of(oc1));

            Etudiant etudiant = new Etudiant();
            etudiant.setCompetences(new ArrayList<>(List.of(compJava)));
            when(etudiantRepository.findAll()).thenReturn(List.of(etudiant));

            // Exécution
            AdminStatsDto stats = adminStatistiquesService.obtenirStatistiques();

            // Vérifications
            assertThat(stats.utilisateursParRole()).containsEntry("ETUDIANT", 2L)
                                                  .containsEntry("RECRUTEUR", 1L)
                                                  .containsEntry("ADMIN", 1L);

            assertThat(stats.offresParDomaine().get("Informatique").actives()).isEqualTo(1L);
            assertThat(stats.offresParDomaine().get("Informatique").cloturees()).isEqualTo(1L);
            assertThat(stats.offresParDomaine().get("Finance").actives()).isEqualTo(1L);

            assertThat(stats.entonnoir().total()).isEqualTo(4L);
            assertThat(stats.entonnoir().acceptees()).isEqualTo(1L);
            assertThat(stats.entonnoir().refusees()).isEqualTo(1L);
            assertThat(stats.entonnoir().envoyees()).isEqualTo(1L);
            assertThat(stats.entonnoir().enCours()).isEqualTo(1L);

            // Taux acceptation = 1 / 4 = 25.0 %
            assertThat(stats.tauxAcceptation()).isEqualTo(25.0);
            // Score moyen = (80 + 60) / 2 = 70.0
            assertThat(stats.scoreMoyen()).isEqualTo(70.0);

            assertThat(stats.competencesEnAttente()).isEqualTo(2L);

            assertThat(stats.ecartMarche()).hasSize(1);
            CompetenceMarcheDto m = stats.ecartMarche().get(0);
            assertThat(m.nom()).isEqualTo("Java");
            assertThat(m.demandees()).isEqualTo(1L);
            assertThat(m.possedees()).isEqualTo(1L);
            assertThat(m.ecart()).isEqualTo(0L); // équilibre
        }
    }
}
