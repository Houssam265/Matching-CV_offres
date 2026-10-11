package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.MatchingDetailDto;
import com.gi3.matchingcv.dto.OffreRecommandeeDto;
import com.gi3.matchingcv.model.*;
import com.gi3.matchingcv.model.enums.*;
import com.gi3.matchingcv.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchingServiceTest {

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private OffreRepository offreRepository;

    @Mock
    private ProjetRepository projetRepository;

    @Mock
    private ExperienceProfessionnelleRepository experienceRepository;

    @InjectMocks
    private MatchingServiceImpl matchingService;

    private Etudiant etudiant;
    private Offre offre;
    private Recruteur recruteur;

    private Competence springBoot;
    private Competence react;
    private Competence git;
    private Competence docker;
    private Competence kotlinEnAttente;

    @BeforeEach
    void setUp() {
        recruteur = new Recruteur();
        recruteur.setId(100L);
        recruteur.setNomEntreprise("Tech Solutions");

        etudiant = new Etudiant();
        etudiant.setId(1L);
        etudiant.setPrenom("Jean");
        etudiant.setNom("Dupont");
        etudiant.setCompetences(new ArrayList<>());

        offre = new Offre();
        offre.setId(10L);
        offre.setTitre("Développeur Full Stack");
        offre.setRecruteur(recruteur);
        offre.setStatut(StatutOffre.ACTIVE);
        offre.setOffreCompetences(new ArrayList<>());

        springBoot = new Competence();
        springBoot.setId(101L);
        springBoot.setNom("Spring Boot");
        springBoot.setNomNormalise("springboot");
        springBoot.setStatut(StatutCompetence.VALIDEE);

        react = new Competence();
        react.setId(102L);
        react.setNom("React");
        react.setNomNormalise("react");
        react.setStatut(StatutCompetence.VALIDEE);

        git = new Competence();
        git.setId(103L);
        git.setNom("Git");
        git.setNomNormalise("git");
        git.setStatut(StatutCompetence.VALIDEE);

        docker = new Competence();
        docker.setId(104L);
        docker.setNom("Docker");
        docker.setNomNormalise("docker");
        docker.setStatut(StatutCompetence.VALIDEE);

        kotlinEnAttente = new Competence();
        kotlinEnAttente.setId(105L);
        kotlinEnAttente.setNom("Kotlin");
        kotlinEnAttente.setNomNormalise("kotlin");
        kotlinEnAttente.setStatut(StatutCompetence.EN_ATTENTE);
    }

    private OffreCompetence lien(Offre o, Competence c, TypeExigence type) {
        OffreCompetence oc = new OffreCompetence();
        oc.setOffre(o);
        oc.setCompetence(c);
        oc.setTypeExigence(type);
        o.getOffreCompetences().add(oc);
        return oc;
    }

    @Test
    @DisplayName("Cas chiffré spécifié : REQUISE Spring Boot (60 mois, N=1.0) + React (projet, N=0.7) + ATOUT Git (liste, N=0.4) donne score = 76")
    void testCalculerScore_casChiffre_springBootReactGit_donne76() {
        // Offre : REQUISE Spring Boot, REQUISE React, ATOUT Git
        lien(offre, springBoot, TypeExigence.REQUISE);
        lien(offre, react, TypeExigence.REQUISE);
        lien(offre, git, TypeExigence.ATOUT);

        // Étudiant :
        // 1. Expérience de 60 mois avec Spring Boot (N = 0.8 + 0.2 * (60/60) = 1.0)
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setId(201L);
        exp.setPoste("Ingénieur Java");
        exp.setEntreprise("Entreprise X");
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        // 2. Projet avec React (N = 0.7)
        Projet projet = new Projet();
        projet.setId(301L);
        projet.setTitre("Portfolio");
        projet.setCompetences(new ArrayList<>(List.of(react)));

        // 3. Liste personnelle seule avec Git (N = 0.4)
        etudiant.getCompetences().add(git);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of(projet));

        // Exécution
        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        // Vérifications
        // Score = (1.0 + 0.7) / 2 * 80 + 0.4 * 20 = 0.85 * 80 + 8 = 68 + 8 = 76
        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.score()).isEqualTo(76.0);
        assertThat(detail.scoreRequises()).isEqualTo(68.0);
        assertThat(detail.scoreAtouts()).isEqualTo(8.0);
        assertThat(detail.partRequisesMax()).isEqualTo(80.0);
        assertThat(detail.partAtoutsMax()).isEqualTo(20.0);
        assertThat(detail.nbRequisesValidees()).isEqualTo(2);
        assertThat(detail.nbAtoutsValides()).isEqualTo(1);
    }

    @Test
    @DisplayName("Cas sans ATOUT : un seul REQUISE parfait donne score = 100 (part REQUISE compte pour 100)")
    void testCalculerScore_offreSansAtout_unSeulRequiseParfait_donne100() {
        // Offre avec uniquement REQUISE Spring Boot (aucun ATOUT)
        lien(offre, springBoot, TypeExigence.REQUISE);

        // Étudiant : expérience de 60 mois (N = 1.0)
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of());

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.score()).isEqualTo(100.0);
        assertThat(detail.scoreRequises()).isEqualTo(100.0);
        assertThat(detail.scoreAtouts()).isEqualTo(0.0);
        assertThat(detail.partRequisesMax()).isEqualTo(100.0);
        assertThat(detail.partAtoutsMax()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Compétence EN_ATTENTE ignorée : ne pénalise pas et est signalée comme non comptabilisée")
    void testCalculerScore_competenceEnAttenteIgnoree() {
        // Offre avec REQUISE Spring Boot (VALIDEE) et REQUISE Kotlin (EN_ATTENTE)
        lien(offre, springBoot, TypeExigence.REQUISE);
        lien(offre, kotlinEnAttente, TypeExigence.REQUISE);

        // Étudiant a Spring Boot avec 60 mois
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of());

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        // Kotlin étant EN_ATTENTE, seule Spring Boot est requise validée (nb = 1).
        // L'offre n'a aucun ATOUT validé -> la part requise compte pour 100. Score = 1.0 * 100 = 100.
        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.score()).isEqualTo(100.0);
        assertThat(detail.nbRequisesValidees()).isEqualTo(1);

        // Dans le détail, Kotlin doit être marquée comme non comptée
        var detailKotlin = detail.competences().stream()
                .filter(c -> c.nom().equals("Kotlin"))
                .findFirst()
                .orElseThrow();
        assertThat(detailKotlin.comptee()).isFalse();
        assertThat(detailKotlin.raison()).contains("attente");
    }

    @Test
    @DisplayName("Offre sans REQUISE validée : le score est indisponible")
    void testCalculerScore_offreSansRequiseValidee_scoreIndisponible() {
        // Offre ne contenant que Kotlin (EN_ATTENTE) comme REQUISE
        lien(offre, kotlinEnAttente, TypeExigence.REQUISE);

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of());
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of());

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        assertThat(detail.scoreDisponible()).isFalse();
        assertThat(detail.score()).isNull();
        assertThat(detail.message()).contains("Score indisponible");
    }

    @Test
    @DisplayName("ATOUT absent non pénalisant : bonus proportionnel au total VALIDEE (2 atouts, 1 possédé)")
    void testCalculerScore_atoutAbsent_nonPenalisant() {
        // Offre : REQUISE Spring Boot (VALIDEE) + 2 ATOUTs VALIDEE : Git et Docker
        lien(offre, springBoot, TypeExigence.REQUISE);
        lien(offre, git, TypeExigence.ATOUT);
        lien(offre, docker, TypeExigence.ATOUT);

        // Étudiant :
        // Spring Boot parfait (exp ~60 mois, N ≈ 1.0) -> REQUISE ≈ 1.0 * 80 = 80
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        // Git en liste seule (N = 0.4)
        etudiant.getCompetences().add(git);
        // Docker n'est PAS possédé par l'étudiant

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of());

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        // Nouvelle formule : bonus = (somme N possédés / total VALIDEE) * 20
        // Score_requises ≈ 1.0 * 80 = 80.
        // Score_atouts = (0.4 / 2) * 20 = 4.0.  Docker absent ne pénalise pas mais dilue le bonus.
        // Score final ≈ 80 + 4 = 84. (non 88 comme avec l'ancienne moyenne sur possédés).
        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.scoreRequises()).isEqualTo(80.0);
        assertThat(detail.scoreAtouts()).isEqualTo(4.0);
        assertThat(detail.score()).isEqualTo(84.0);
    }

    @Test
    @DisplayName("3 Atouts VALIDEE dont 1 seul possédé (N=0.7) : bonus ≈ 4.7, score ≈ 85")
    void testCalculerScore_troisAtouts_unSeulPossede() {
        // Offre : REQUISE Spring Boot + 3 ATOUTs VALIDEE : Git, Docker, React
        lien(offre, springBoot, TypeExigence.REQUISE);
        lien(offre, git,    TypeExigence.ATOUT);
        lien(offre, docker, TypeExigence.ATOUT);
        lien(offre, react,  TypeExigence.ATOUT);

        // Spring Boot parfait (~60 mois, N ≈ 1.0)
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        // Seul Git est possédé, via un projet (N = 0.7)
        Projet projet = new Projet();
        projet.setId(302L);
        projet.setTitre("Projet Git");
        projet.setCompetences(new ArrayList<>(List.of(git)));
        // Docker et React ne sont pas possédés

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of(projet));

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        // Score_requises ≈ 1.0 * 80 = 80.
        // bonus = (0.7 / 3) * 20 ≈ 4.667 → arrondi à 1 décimale = 4.7
        // Score final ≈ 80 + 4.7 = 84.7 → arrondi entier = 85
        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.nbAtoutsValides()).isEqualTo(3);
        assertThat(detail.scoreAtouts()).isEqualTo(4.7);
        assertThat(detail.score()).isEqualTo(84.7);
    }

    @Test
    @DisplayName("Plusieurs expériences : sélectionne l'expérience la plus longue (dateFin null = en cours)")
    void testCalculerScore_choixExperienceLaPlusLongue() {
        lien(offre, springBoot, TypeExigence.REQUISE);

        // Expérience 1 : 12 mois
        ExperienceProfessionnelle exp1 = new ExperienceProfessionnelle();
        exp1.setDateDebut(LocalDate.now().minusMonths(12));
        exp1.setDateFin(LocalDate.now());
        exp1.setCompetences(new ArrayList<>(List.of(springBoot)));

        // Expérience 2 en cours : 36 mois (dateFin null)
        ExperienceProfessionnelle exp2 = new ExperienceProfessionnelle();
        exp2.setDateDebut(LocalDate.now().minusMonths(36));
        exp2.setDateFin(null); // en cours
        exp2.setCompetences(new ArrayList<>(List.of(springBoot)));

        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(offre));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp1, exp2));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of());

        MatchingDetailDto detail = matchingService.calculerScore(1L, 10L);

        // 36 mois : N = 0.8 + 0.2 * (36 / 60) = 0.8 + 0.12 = 0.92
        // Offre sans atout -> 0.92 * 100 = 92.0
        assertThat(detail.scoreDisponible()).isTrue();
        assertThat(detail.score()).isEqualTo(92.0);
    }

    @Test
    @DisplayName("Recommandation d'offres : filtre les offres actives et les trie par score décroissant")
    void testRecommanderOffres_triDecroissant() {
        // Offre 1 : REQUISE Spring Boot parfait -> 100
        Offre o1 = new Offre();
        o1.setId(10L);
        o1.setTitre("Offre A");
        o1.setStatut(StatutOffre.ACTIVE);
        o1.setRecruteur(recruteur);
        o1.setDatePublication(LocalDateTime.now().minusDays(1));
        lien(o1, springBoot, TypeExigence.REQUISE);

        // Offre 2 : REQUISE React (projet, N=0.7) -> 70
        Offre o2 = new Offre();
        o2.setId(20L);
        o2.setTitre("Offre B");
        o2.setStatut(StatutOffre.ACTIVE);
        o2.setRecruteur(recruteur);
        o2.setDatePublication(LocalDateTime.now());
        lien(o2, react, TypeExigence.REQUISE);

        // Offre 3 : CLOTUREE -> ne doit pas apparaître
        Offre o3 = new Offre();
        o3.setId(30L);
        o3.setTitre("Offre C Clôturée");
        o3.setStatut(StatutOffre.CLOTUREE);
        o3.setRecruteur(recruteur);

        // Étudiant avec Spring Boot (60 mois) et React (projet)
        ExperienceProfessionnelle exp = new ExperienceProfessionnelle();
        exp.setDateDebut(LocalDate.now().minusMonths(60));
        exp.setDateFin(LocalDate.now());
        exp.setCompetences(new ArrayList<>(List.of(springBoot)));

        Projet proj = new Projet();
        proj.setCompetences(new ArrayList<>(List.of(react)));

        when(etudiantRepository.existsById(1L)).thenReturn(true);
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(offreRepository.findByStatut(StatutOffre.ACTIVE)).thenReturn(List.of(o1, o2));
        when(offreRepository.findById(10L)).thenReturn(Optional.of(o1));
        when(offreRepository.findById(20L)).thenReturn(Optional.of(o2));
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(List.of(exp));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(List.of(proj));

        List<OffreRecommandeeDto> recos = matchingService.recommanderOffres(1L, null, null, null);

        assertThat(recos).hasSize(2);
        // O1 a un score de 100.0, O2 a un score de 70.0 -> O1 doit être première
        assertThat(recos.get(0).id()).isEqualTo(10L);
        assertThat(recos.get(0).score()).isEqualTo(100.0);
        assertThat(recos.get(1).id()).isEqualTo(20L);
        assertThat(recos.get(1).score()).isEqualTo(70.0);
    }
}
