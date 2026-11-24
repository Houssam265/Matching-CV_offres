
# Matching CV-Offres

Application web de mise en relation entre des utilisateurs et des offres de stage ou d'emploi. Le projet comprend une interface web statique, une API REST Java avec Spring Boot et une base MySQL.

## Technologies

- Java 17 et Spring Boot 3.3.5
- Spring Web pour les pages et l'API REST
- Spring Data JPA et Hibernate pour l'accès aux données
- MySQL pour la base de données
- HTML, CSS et JavaScript pour l'interface
- Maven pour la compilation et la gestion des dépendances

## Démarrer le projet

1. Installer Java 17, Maven et MySQL.
2. Démarrer MySQL. Vérifier la configuration MySQL locale avant le lancement. Adapter ces valeurs dans `src/main/resources/application.properties` si nécessaire.
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

## Module recruteur — phase 1

Le parcours existant `register.html` / `login.html` conserve le rôle `RECRUTEUR`
dans `matchingcv_user`. L'inscription recruteur utilise `POST /api/recruteurs`
et la connexion réutilise `POST /api/auth/connexion`. L'entreprise et le secteur
sont obligatoires. La branche étudiante du formulaire partagé est inchangée.

Pages : `dashboard-recruteur.html`, `mes-offres.html`, `publier-offre.html`
(ajouter `?id=…` pour modifier), `offre-recruteur.html?id=…`.

Le corps de création/modification d'une offre est :

```json
{
  "recruteurId": 1,
  "titre": "Développeur Java",
  "description": "Description du poste",
  "domaine": "Informatique",
  "localisation": "Rabat",
  "typeContrat": "STAGE",
  "competences": [{"competenceId": 10, "typeExigence": "REQUISE"}]
}
```

| Action | Endpoint |
| --- | --- |
| Publier / modifier | `POST /api/offres`, `PUT /api/offres/{id}` |
| Clôturer | `PATCH /api/offres/{id}/cloturer?recruteurId=…` |
| Supprimer sans candidatures | `DELETE /api/offres/{id}?recruteurId=…` |
| Toutes les offres du recruteur | `GET /api/recruteurs/{id}/offres` |
| Profil recruteur | `GET /api/recruteurs/{id}` |
| Suggestions (8 maximum) | `GET /api/recruteurs/{id}/competences/suggestions?q=…` |
| Proposer/réutiliser une compétence | `POST /api/recruteurs/{id}/competences/propositions` avec `{"nom":"…"}` |

Une suppression avec candidatures renvoie **409**, sans suppression des
candidatures ; le recruteur utilise alors l'action Clôturer. La clôture est
idempotente. Une compétence rejetée après publication reste visible et peut être
conservée lors d'une modification ou remplacée ; aucun nouveau rattachement
d'une compétence rejetée n'est accepté.

**Contrat étudiant inchangé :** `GET /api/offres` liste les offres actives avec les
filtres optionnels `domaine`, `localisation` (recherche partielle, sans distinction
de casse) et `typeContrat` (`STAGE`, `CDI`, `CDD`, `ALTERNANCE`).
`GET /api/offres/{id}` renvoie une offre active. Son propriétaire peut également
consulter une offre clôturée en ajoutant `?recruteurId=…`.
Chaque `OffreDto` contient exclusivement `id`, `titre`, `description`, `domaine`,
`localisation`, `typeContrat`, `statut`, `datePublication`, `entreprise` et
`competences[{competenceId,nom,statut,typeExigence}]`.

Le moteur de matching (`MatchingServiceImpl`) ignore toute compétence non `VALIDEE`,
ne pénalise jamais un atout absent et laisse le score indisponible sans compétence
requise validée. Pour chaque compétence de l'offre, le niveau N vaut
0,8 + 0,2 × min(1, mois / 60) si elle provient d'une expérience, 0,7 d'un projet,
0,4 de la liste personnelle et 0 sinon. Score = moyenne des N requises × 80 (× 100 sans atout)
+ somme des N atouts possédés / nombre d'atouts × 20. Deux compétences correspondent par
identifiant, nom normalisé ou synonyme.

**Sécurité :** l'identité vient de la session serveur. `ResourceAccess` vérifie le rôle et la
propriété (un `recruteurId` fourni doit être celui du recruteur connecté et propriétaire de
l'offre), et `/api/admin/**` est réservé au rôle ADMIN. L'administrateur enregistré dans le
journal d'audit est lu depuis la session, jamais depuis la requête.

Tests ciblés (sans base de données) :

```sh
mvn -Dtest=OffreServiceTest,RecruteurServiceTest,RecruteurOffreControllerTest test
```

Le test existant `MatchingCvOffresApplicationTests` nécessite le MySQL configuré
dans `application.properties`.

## Documentation et fichiers générés

- `DiagrammeClasses_MatchingCV.puml` et `docs/diagrams/DiagrammeClasses_MatchingCV.puml` : diagrammes PlantUML des classes et de leurs relations.
- `.gitignore` : empêche notamment l'ajout de `target/`, des fichiers d'IDE et de fichiers système dans Git.
- `src/main/java/com/gi3/matchingcv/config/.gitkeep`, `dto/.gitkeep`, `model/.gitkeep`, `repository/.gitkeep` et `service/.gitkeep` : marqueurs conservés par Git pour suivre ces dossiers même s'ils deviennent vides. Ils ne contiennent pas de code.
- `.git/` : données internes utilisées par Git pour l'historique et les branches ; ne pas modifier manuellement.
- `target/` : fichiers compilés, ressources copiées et résultats de tests générés par Maven. Ce dossier peut être recréé et n'est pas versionné.


## Portails et compte

L’accueil propose les espaces utilisateur et recruteur. Les API utilisent une session serveur, un contrôle du rôle et de la propriété des ressources, ainsi qu’un jeton CSRF. Le stockage local sert uniquement à l’affichage. Les mots de passe sont hachés avec BCrypt ; les anciens mots de passe sont migrés au démarrage. Sauvegarder la base avant la première mise à jour.

Les pages « Mon compte », « Favoris et suivi » / « Entretiens » complètent les portails. Le CV PDF est privé, limité à 5 Mo. La désactivation anonymise les coordonnées du compte et conserve l’historique, comme indiqué dans l’interface. Les identifiants techniques ETUDIANT et les anciennes URL restent compatibles.

## Messagerie, CV et photo de profil

- **Messagerie** (`messages.html`, `/api/messages/**`) : une conversation par candidature, entre l’étudiant et le recruteur propriétaire de l’offre uniquement. Le recruteur écrit en premier, puis le candidat peut répondre. Le premier message fait passer une candidature `ENVOYEE` à `VUE`. Une candidature refusée ou retirée passe la conversation en lecture seule. Le destinataire reçoit une seule notification `NOUVEAU_MESSAGE` tant qu’il ne l’a pas lue.
- **Coordonnées privées** : l’email du candidat n’est jamais transmis au recruteur, ni dans les listes, ni dans le détail, ni dans l’export CSV. Le contact passe par la messagerie.
- **CV** (`cv.html`) : CV A4 à une colonne, généré depuis le profil. Ordre des rubriques : profil, formation, expériences, projets, compétences validées, langues, centres d’intérêt. Les coordonnées sont affichées une par ligne : email de contact, téléphone, ville, LinkedIn, GitHub, portfolio.
- **Téléchargement du CV** : le PDF est généré par le serveur (openhtmltopdf, police Inter embarquée). Le texte est sélectionnable et les liens sont cliquables. Le fichier se nomme `CV-Prenom-Nom.pdf`.
  - `GET /api/account/cv-pdf?photo=true|false` : CV de l’étudiant connecté.
  - `GET /api/candidatures/{id}/cv-pdf` : CV d’un candidat, pour le recruteur de l’offre. L’email et le téléphone n’y figurent jamais.
- **Photo de profil** (tous les rôles) : JPEG, PNG ou WebP de 2 Mo maximum, avec contrôle des octets de signature. `/api/account/photo/{id}` renvoie toujours une image : la photo, sinon un avatar SVG à partir des initiales. Le cache est revalidé par ETag.

## Connexion et sécurité

- **Une seule page de connexion** (`login.html`) pour les candidats, les recruteurs et les administrateurs. L'espace s'ouvre selon le rôle du compte, et l'ancienne adresse `admin-login.html` redirige vers cette page.
- **Verrouillage temporaire** : après 5 échecs de connexion, le compte est bloqué pendant 15 minutes. Cette limite s'ajoute à celle par adresse IP. Le temps de réponse est le même que l'email existe ou non.
- **En-têtes HTTP** : CSP limitée au site lui-même (`frame-ancestors 'none'`), `X-Frame-Options: DENY`, `Referrer-Policy` et `Permissions-Policy`.
- **Ressources locales** : la police Inter (licence OFL, dossier `static/fonts/`) et SockJS/STOMP (`static/js/vendor/`) sont servies par le site. Aucune requête n'est envoyée à un CDN.
- **Erreurs** : toute erreur d'API renvoie `{"message": …}` avec le bon code HTTP, sans trace technique.

## Configuration et validation

- `mvn verify` : tests métier, contrôles d’accès et intégration H2, sans utiliser la base MySQL de développement. La CI exécute également la vérification syntaxique JavaScript.
- Comptes admin : `app.admin.emails` liste les comptes créés au démarrage. Leurs mots de passe se
  définissent par la variable d'environnement `ADMIN_PASSWORDS` (séparés par des virgules, même ordre).
  Sans elle, un compte existant garde son mot de passe et un nouveau compte reçoit un mot de passe
  aléatoire affiché une seule fois dans les journaux de démarrage.
- Profil `mail` : définir SMTP_HOST, SMTP_PORT, SMTP_USER, SMTP_PASSWORD, MAIL_FROM et PUBLIC_URL. Sans SMTP, la récupération et la vérification d’email signalent leur indisponibilité.
- Profil `prod` : définir DB_URL, DB_USER, DB_PASSWORD, PUBLIC_URL et MAIL_FROM ; HTTPS est nécessaire pour le cookie sécurisé. Le schéma est validé, jamais créé automatiquement en production. Préparer et vérifier sa migration sur une copie de la base avant déploiement. Les migrations SQL versionnées restent à établir pour votre base existante.
- Compléter l’identité de l’exploitant, son contact et les durées de conservation dans la page de confidentialité avant publication.

Les fonctions d’équipe multi-recruteurs, les champs structurés de rémunération et la pagination générale restent des évolutions à réaliser. Le PDF déposé n’est pas analysé automatiquement ; le matching utilise les compétences et expériences renseignées.
