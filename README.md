
# Matching CV-Offres

Application web de mise en relation entre des étudiants et des offres de stage ou d'emploi. Le projet comprend une interface web statique, une API REST Java avec Spring Boot et une base MySQL.

## Technologies

- Java 17 et Spring Boot 3.3.5
- Spring Web pour les pages et l'API REST
- Spring Data JPA et Hibernate pour l'accès aux données
- MySQL pour la base de données
- HTML, CSS et JavaScript pour l'interface
- Maven pour la compilation et la gestion des dépendances

## Démarrer le projet

1. Installer Java 17, Maven et MySQL.
2. Démarrer MySQL. La configuration par défaut utilise `localhost:3306`, la base `matching_cv_offres`, l'utilisateur `root` et un mot de passe vide. Adapter ces valeurs dans `src/main/resources/application.properties` si nécessaire.
3. Depuis le dossier du projet, lancer `mvn spring-boot:run`.
4. Ouvrir `http://localhost:8080/` dans un navigateur.

Pour exécuter les tests automatisés : `mvn test`.

## Organisation des dossiers et fichiers

Les chemins ci-dessous sont relatifs à la racine du dépôt.

```text
Matching-CV_offres/
├── .git/                                      Métadonnées et historique Git du dépôt
├── .gitignore                                 Fichiers locaux et générés que Git doit ignorer
├── DiagrammeClasses_MatchingCV.puml           Diagramme UML des classes métier
├── pom.xml                                     Dépendances, version Java et configuration Maven
├── README.md                                   Présentation et guide de lecture du projet
├── docs/
│   └── diagrams/                              Dossier des diagrammes de documentation
│       └── DiagrammeClasses_MatchingCV.puml   Diagramme UML rangé dans la documentation
├── src/
│   ├── main/                                   Code et ressources de l'application
│   │   ├── java/com/gi3/matchingcv/             Code Java du serveur Spring Boot
│   │   │   ├── MatchingCvOffresApplication.java Point d'entrée : démarre Spring Boot
│   │   │   ├── config/                          Configuration et initialisation des données
│   │   │   ├── controller/                      Routes HTTP de l'API REST
│   │   │   ├── dto/                             Objets d'entrée et de réponse de l'API
│   │   │   ├── exception/                       Exceptions métier dédiées
│   │   │   ├── model/                           Entités métier enregistrées en base
│   │   │   │   └── enums/                       Valeurs fixes utilisées par le modèle
│   │   │   ├── repository/                      Accès aux entités avec Spring Data JPA
│   │   │   └── service/                          Règles métier et opérations applicatives
│   │   └── resources/
│   │       ├── application.properties           Port, base de données, Hibernate et journaux
│   │       └── static/                           Pages et ressources web servies par Spring
│   │           ├── css/                          Feuilles de style
│   │           ├── js/                           Scripts exécutés dans le navigateur
│   │           └── *.html                        Pages de l'application
│   └── test/java/com/gi3/matchingcv/             Tests automatisés Java
│       ├── controller/                          Tests des routes REST
│       └── service/                             Tests des services métier
└── target/                                      Résultats temporaires produits par Maven (ignorés par Git)
```

## Détail des fichiers Java

### Démarrage, configuration et API

- `MatchingCvOffresApplication.java` : lance l'application Spring Boot.
- `config/CompetenceDataMigrationRunner.java` : initialise ou met à jour le référentiel de compétences au démarrage.
- `controller/AuthController.java` : expose l'inscription, la connexion et la vérification de disponibilité d'une adresse e-mail (`/api/auth`).
- `controller/CompetenceController.java` : expose la consultation, la création, la suppression et la proposition de compétences (`/api/competences`).
- `controller/EtudiantController.java` : expose les opérations sur les étudiants, leurs compétences, projets et expériences (`/api/etudiants`).
- `controller/HealthController.java` : fournit un point de contrôle de disponibilité (`/api/health`).

### Objets échangés avec l'API (`dto/`)

- `AuthResponse.java` : informations publiques retournées après une opération d'authentification.
- `ConnexionRequest.java` : données attendues pour la connexion.
- `InscriptionRequest.java` : données attendues pour créer un compte étudiant ou recruteur, avec validation des champs.
- `CompetenceAttachRequest.java` : données pour rattacher une compétence existante ou en proposer une nouvelle.

### Règles métier (`service/`)

Les interfaces décrivent les opérations disponibles ; les classes `Impl` en fournissent l'implémentation.

- `AuthService.java` et `AuthServiceImpl.java` : contrat et traitement de l'inscription et de la connexion.
- `CompetenceService.java` et `CompetenceServiceImpl.java` : contrat et règles du référentiel de compétences, notamment recherche de doublons et normalisation.
- `EtudiantService.java` et `EtudiantServiceImpl.java` : contrat et opérations de création, consultation et mise à jour des étudiants.
- `ExperienceProfessionnelleService.java` et `ExperienceProfessionnelleServiceImpl.java` : contrat et opérations sur les expériences professionnelles.
- `ProjetService.java` et `ProjetServiceImpl.java` : contrat et opérations sur les projets d'étudiants.

### Entités métier (`model/`)

- `Administrateur.java` : compte administrateur, spécialisé à partir d'un utilisateur.
- `Candidature.java` : candidature d'un étudiant à une offre, avec son statut et son score.
- `Competence.java` : compétence du référentiel, sa catégorie, son statut et ses synonymes ; contient aussi la normalisation de son nom.
- `Etudiant.java` : profil étudiant et relations avec ses compétences, projets, expériences, candidatures et notifications.
- `ExperienceProfessionnelle.java` : expérience d'un étudiant et compétences associées.
- `Notification.java` : notification destinée à un étudiant, notamment en lien avec une offre et un score.
- `Offre.java` : offre de stage ou d'emploi publiée par un recruteur.
- `OffreCompetence.java` : lien entre une offre et une compétence, avec le type d'exigence.
- `ProfilCompetence.java` : compétence du profil d'un étudiant, avec sa provenance et sa durée d'expérience.
- `Projet.java` : projet réalisé par un étudiant et compétences associées.
- `Recruteur.java` : compte recruteur, entreprise et offres publiées.
- `Utilisateur.java` : données communes aux comptes et classe de base des rôles spécialisés.

### Valeurs contrôlées (`model/enums/`)

- `Provenance.java` : origine d'une compétence dans le profil (déclarée, projet ou expérience).
- `Role.java` : rôle d'un compte (étudiant, recruteur ou administrateur).
- `StatutCandidature.java` : état d'une candidature.
- `StatutCompetence.java` : état de validation d'une compétence.
- `StatutOffre.java` : état de publication d'une offre.
- `TypeContrat.java` : catégorie de contrat (stage, CDI, CDD ou alternance).
- `TypeExigence.java` : indique si une compétence est requise ou constitue un atout pour une offre.

### Accès aux données (`repository/`)

Chaque interface étend Spring Data JPA pour lire et enregistrer son type d'entité. Les méthodes de recherche particulières sont définies dans l'interface concernée.

- `AdministrateurRepository.java` : accès aux administrateurs.
- `CandidatureRepository.java` : accès aux candidatures.
- `CompetenceRepository.java` : accès au référentiel des compétences.
- `EtudiantRepository.java` : accès aux étudiants.
- `ExperienceProfessionnelleRepository.java` : accès aux expériences professionnelles.
- `NotificationRepository.java` : accès aux notifications.
- `OffreCompetenceRepository.java` : accès aux associations entre offres et compétences.
- `OffreRepository.java` : accès aux offres.
- `ProfilCompetenceRepository.java` : accès aux compétences des profils étudiants.
- `ProjetRepository.java` : accès aux projets.
- `RecruteurRepository.java` : accès aux recruteurs.
- `UtilisateurRepository.java` : accès aux comptes utilisateurs.

### Exceptions métier (`exception/`)

- `CompetenceDejaExistanteException.java` : signale qu'une compétence existe déjà.
- `EmailDejaUtiliseException.java` : signale qu'une adresse e-mail est déjà liée à un compte.

## Pages et ressources web (`src/main/resources/static/`)

### Pages HTML

- `index.html` : page d'accueil et présentation de la plateforme.
- `register.html` : formulaire de création de compte étudiant ou recruteur.
- `login.html` : formulaire de connexion.
- `competences.html` : consultation, recherche et gestion du référentiel des compétences.
- `dashboard-etudiant.html` : tableau de bord de l'étudiant.
- `profil-etudiant.html` : consultation et modification du profil étudiant.
- `dashboard-recruteur.html` : espace du recruteur.

### Styles (`css/`)

- `style.css` : styles généraux, navigation et composants communs.
- `auth.css` : styles propres aux pages de connexion et d'inscription.

### Scripts navigateur (`js/`)

- `main.js` : comportements de la page d'accueil et éléments communs comme la navigation et le contrôle de disponibilité de l'API.
- `auth.js` : formulaires de connexion et d'inscription, validation côté navigateur et indicateur de force du mot de passe.
- `session.js` : contrôle la session et le rôle avant l'accès aux pages privées ; gère la déconnexion, le retour arrière et les changements de session dans un autre onglet.
- `competences.js` : chargement, recherche, création et affichage du référentiel de compétences.
- `dashboard-etudiant.js` : chargement et interactions du tableau de bord étudiant.
- `profil-etudiant.js` : affichage et modification des compétences, projets et expériences du profil étudiant.

## Tests (`src/test/java/`)

- `MatchingCvOffresApplicationTests.java` : vérifie que le contexte Spring Boot démarre.
- `controller/CompetenceControllerTest.java` : tests HTTP du contrôleur des compétences.
- `service/CompetenceServiceTest.java` : tests des règles métier du référentiel de compétences.
- `service/EtudiantServiceTest.java` : tests des opérations métier sur les étudiants.

## Documentation et fichiers générés

- `DiagrammeClasses_MatchingCV.puml` et `docs/diagrams/DiagrammeClasses_MatchingCV.puml` : diagrammes PlantUML des classes et de leurs relations.
- `.gitignore` : empêche notamment l'ajout de `target/`, des fichiers d'IDE et de fichiers système dans Git.
- `src/main/java/com/gi3/matchingcv/config/.gitkeep`, `dto/.gitkeep`, `model/.gitkeep`, `repository/.gitkeep` et `service/.gitkeep` : marqueurs conservés par Git pour suivre ces dossiers même s'ils deviennent vides. Ils ne contiennent pas de code.
- `.git/` : données internes utilisées par Git pour l'historique et les branches ; ne pas modifier manuellement.
- `target/` : fichiers compilés, ressources copiées et résultats de tests générés par Maven. Ce dossier peut être recréé et n'est pas versionné.
