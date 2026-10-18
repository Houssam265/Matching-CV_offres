package com.gi3.matchingcv.service;

import com.gi3.matchingcv.dto.*;
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
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CoachServiceTest {

    @Mock
    private MatchingService matchingService;

    @Mock
    private EtudiantRepository etudiantRepository;

    @Mock
    private OffreRepository offreRepository;

    @Mock
    private CompetenceRepository competenceRepository;

    @Mock
    private ProjetRepository projetRepository;

    @Mock
    private ExperienceProfessionnelleRepository experienceRepository;

    @InjectMocks
    private CoachServiceImpl coachService;

    private Etudiant etudiant;
    private Offre offre;
    private Recruteur recruteur;

    private Competence javaComp;
    private Competence dockerComp;
    private Competence reactComp;
    private Competence awsComp;

    @BeforeEach
    void setUp() {
        recruteur = new Recruteur();
        recruteur.setId(10L);
        recruteur.setNomEntreprise("Tech Corp");

        etudiant = new Etudiant();
        etudiant.setId(1L);
        etudiant.setPrenom("Houssam");
        etudiant.setNom("Hariss");
        etudiant.setCompetences(new ArrayList<>());

        offre = new Offre();
        offre.setId(20L);
        offre.setTitre("Développeur Full Stack");
        offre.setRecruteur(recruteur);
        offre.setStatut(StatutOffre.ACTIVE);
        offre.setOffreCompetences(new ArrayList<>());

        javaComp = new Competence();
        javaComp.setId(101L);
        javaComp.setNom("Java");
        javaComp.setNomNormalise("java");
        javaComp.setStatut(StatutCompetence.VALIDEE);

        dockerComp = new Competence();
        dockerComp.setId(102L);
        dockerComp.setNom("Docker");
        dockerComp.setNomNormalise("docker");
        dockerComp.setStatut(StatutCompetence.VALIDEE);

        reactComp = new Competence();
        reactComp.setId(103L);
        reactComp.setNom("React");
        reactComp.setNomNormalise("react");
        reactComp.setStatut(StatutCompetence.VALIDEE);

        awsComp = new Competence();
        awsComp.setId(104L);
        awsComp.setNom("AWS");
        awsComp.setNomNormalise("aws");
        awsComp.setStatut(StatutCompetence.VALIDEE);
    }

    @Test
    @DisplayName("Cas chiffré 1 : Une compétence REQUISE manquante, gain = score simulé - score actuel")
    void testCasChiffreRequiseManquante() {
        // Offre avec 2 requises : Java et Docker, aucun atout (partRequisesMax = 100)
        // Etudiant a Java à 0.7 (projet) et Docker manquant (0.0).
        // Score actuel = (0.7 + 0.0) / 2 * 100 = 35.0
        List<CompetenceDetailScoreDto> compsActuel = List.of(
                new CompetenceDetailScoreDto(101L, "Java", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "PROJET", "Projet", 0.7, true, "OK", true),
                new CompetenceDetailScoreDto(102L, "Docker", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "AUCUNE", "Absente", 0.0, true, "Absente", false)
        );

        MatchingDetailDto actuel = new MatchingDetailDto(
                1L, 20L, "Développeur Full Stack", "Tech Corp",
                35.0, true, "Score calculé", 35.0, 0.0, 100.0, 0.0, 2, 0, compsActuel
        );

        when(matchingService.calculerScore(1L, 20L)).thenReturn(actuel);

        // Simulation 'et si' : Docker ajouté à un projet (niveau 0.7)
        // Score simulé attendu = (0.7 + 0.7) / 2 * 100 = 70.0
        // Gain attendu = 70.0 - 35.0 = 35.0 points
        SimulationResultDto sim = coachService.simulerGain(1L, 20L, 102L, 0.7);

        assertThat(sim.scoreActuel()).isEqualTo(35.0);
        assertThat(sim.scoreSimule()).isEqualTo(70.0);
        assertThat(sim.gain()).isEqualTo(35.0);
        assertThat(sim.gain()).isEqualTo(sim.scoreSimule() - sim.scoreActuel());
    }

    @Test
    @DisplayName("Cas chiffré 2 : Lier une compétence déclarée (0.4) à un projet (0.7), gain calculé")
    void testCasChiffreLierCompetenceDeclareeAProjet() {
        // Offre avec 2 requises : Java (0.7) et Docker en déclaratif (0.4), aucun atout
        // Score actuel = (0.7 + 0.4) / 2 * 100 = 55.0
        List<CompetenceDetailScoreDto> compsActuel = List.of(
                new CompetenceDetailScoreDto(101L, "Java", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "PROJET", "Projet", 0.7, true, "OK", true),
                new CompetenceDetailScoreDto(102L, "Docker", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "DECLARATIF", "Liste personnelle seule", 0.4, true, "Déclaratif", true)
        );

        MatchingDetailDto actuel = new MatchingDetailDto(
                1L, 20L, "Développeur Full Stack", "Tech Corp",
                55.0, true, "Score calculé", 55.0, 0.0, 100.0, 0.0, 2, 0, compsActuel
        );

        when(matchingService.calculerScore(1L, 20L)).thenReturn(actuel);

        // Simulation : Docker lié à un projet (0.7)
        // Score simulé attendu = (0.7 + 0.7) / 2 * 100 = 70.0
        // Gain attendu = 70.0 - 55.0 = 15.0 points
        SimulationResultDto sim = coachService.simulerGain(1L, 20L, 102L, 0.7);

        assertThat(sim.scoreActuel()).isEqualTo(55.0);
        assertThat(sim.scoreSimule()).isEqualTo(70.0);
        assertThat(sim.gain()).isEqualTo(15.0);
    }

    @Test
    @DisplayName("Aucun conseil généré pour une compétence déjà au niveau maximal")
    void testAucunConseilPourCompetenceAuNiveauMaximal() {
        // Java est déjà à niveau maximal (1.0 ou >= 0.8 en expérience)
        List<CompetenceDetailScoreDto> compsActuel = List.of(
                new CompetenceDetailScoreDto(101L, "Java", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "EXPERIENCE", "Expérience (60 mois)", 1.0, true, "Maximal", true)
        );

        MatchingDetailDto actuel = new MatchingDetailDto(
                1L, 20L, "Développeur Full Stack", "Tech Corp",
                100.0, true, "Score calculé", 100.0, 0.0, 100.0, 0.0, 1, 0, compsActuel
        );

        when(matchingService.calculerScore(1L, 20L)).thenReturn(actuel);

        List<ConseilCoachDto> conseils = coachService.genererConseilsPourOffre(1L, 20L);

        // Aucun conseil ne doit être produit pour Java car déjà au niveau maximal
        assertThat(conseils).isEmpty();
    }

    @Test
    @DisplayName("Tri par gain décroissant et plafond à 8 conseils maximum sans doublon")
    void testTriEtPlafondSansDoublon() {
        etudiant.getCompetences().add(dockerComp);
        when(etudiantRepository.findById(1L)).thenReturn(Optional.of(etudiant));
        when(projetRepository.findByEtudiantId(1L)).thenReturn(Collections.emptyList());
        when(experienceRepository.findByEtudiantId(1L)).thenReturn(Collections.emptyList());

        // Création de 12 offres avec des gains variables pour tester le plafond
        List<Offre> offres = new ArrayList<>();
        List<OffreRecommandeeDto> recs = new ArrayList<>();

        for (int i = 1; i <= 10; i++) {
            Offre o = new Offre();
            o.setId((long) (100 + i));
            o.setTitre("Offre Test " + i);
            o.setStatut(StatutOffre.ACTIVE);
            o.setOffreCompetences(new ArrayList<>());
            offres.add(o);

            List<CompetenceDetailScoreDto> cList = List.of(
                    new CompetenceDetailScoreDto((long) (200 + i), "Skill " + i, TypeExigence.REQUISE, StatutCompetence.VALIDEE, "AUCUNE", "Absente", 0.0, true, "Absente", false)
            );
            MatchingDetailDto det = new MatchingDetailDto(
                    1L, o.getId(), o.getTitre(), "Entreprise " + i,
                    10.0, true, "Score", 10.0, 0.0, 100.0, 0.0, 1, 0, cList
            );
            lenient().when(matchingService.calculerScore(1L, o.getId())).thenReturn(det);

            recs.add(new OffreRecommandeeDto(
                    o.getId(), o.getTitre(), "Desc", "IT", "Casablanca",
                    TypeContrat.CDI, StatutOffre.ACTIVE, java.time.LocalDateTime.now(), "Entreprise " + i,
                    10.0, true, det, Collections.emptyList()
            ));
        }

        when(offreRepository.findByStatut(StatutOffre.ACTIVE)).thenReturn(offres);
        when(matchingService.recommanderOffres(1L, null)).thenReturn(recs);

        List<ConseilCoachDto> result = coachService.genererConseilsEtudiant(1L);

        // Vérification 1 : Plafond entre 5 et 8 au maximum
        assertThat(result.size()).isLessThanOrEqualTo(8);

        // Vérification 2 : Tri par gain décroissant
        for (int i = 0; i < result.size() - 1; i++) {
            Double gain1 = result.get(i).gainPoints();
            Double gain2 = result.get(i + 1).gainPoints();
            if (gain1 != null && gain2 != null) {
                assertThat(gain1).isGreaterThanOrEqualTo(gain2);
            }
        }

        // Vérification 3 : Aucun doublon d'identifiant / clé
        Set<String> ids = new HashSet<>();
        for (ConseilCoachDto c : result) {
            assertThat(ids.add(c.id())).isTrue();
        }
    }

    @Test
    @DisplayName("La simulation 'et si' n'enregistre rien en base de données")
    void testSimulationNEnregistreRien() {
        List<CompetenceDetailScoreDto> compsActuel = List.of(
                new CompetenceDetailScoreDto(101L, "Java", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "PROJET", "Projet", 0.7, true, "OK", true),
                new CompetenceDetailScoreDto(102L, "Docker", TypeExigence.REQUISE, StatutCompetence.VALIDEE, "AUCUNE", "Absente", 0.0, true, "Absente", false)
        );

        MatchingDetailDto actuel = new MatchingDetailDto(
                1L, 20L, "Développeur Full Stack", "Tech Corp",
                35.0, true, "Score calculé", 35.0, 0.0, 100.0, 0.0, 2, 0, compsActuel
        );

        when(matchingService.calculerScore(1L, 20L)).thenReturn(actuel);

        // Exécution de la simulation
        MatchingDetailDto simule = coachService.simulerScore(1L, 20L, 102L, 0.7);

        assertThat(simule.score()).isEqualTo(70.0);

        // Vérification formelle qu'aucun repository save ou delete n'a été appelé
        verify(etudiantRepository, never()).save(any());
        verify(offreRepository, never()).save(any());
        verify(competenceRepository, never()).save(any());
        verify(projetRepository, never()).save(any());
        verify(experienceRepository, never()).save(any());
    }
}
