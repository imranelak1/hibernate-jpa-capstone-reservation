# Capstone — Réservation de salles avec JPA et Hibernate

Solution du lab 151 du cours **Hibernate & JPA** de MLIAEdu. Application Java SE/JEE avec transactions `RESOURCE_LOCAL`, sans Spring : données H2, services métier, migrations SQL, cache Ehcache et tests fonctionnels.

## Exécution

Prérequis : JDK 17 et Maven 3.9 ou supérieur.

```sh
mvn clean verify
mvn exec:java
```

Les deux commandes utilisent une base H2 en mémoire et ne demandent aucun serveur ni compte externe. Les tests créent une base isolée par scénario. La démonstration crée quatre utilisateurs, trois salles et six réservations avec des dates relatives au lendemain de l'exécution.

## Couverture du TP

| Étape | Réalisation |
|---|---|
| Modèle | `Utilisateur`, `Salle`, `Reservation`, clés étrangères, email unique, dates obligatoires, `@Version`, associations `LAZY` |
| Cache | Hibernate JCache, Ehcache 3, cache d'entités `READ_WRITE` et cache de requêtes |
| Données | `DataInitializer`, plusieurs personnes et salles, créneaux distincts et dates dynamiques |
| Tests | Création, modification, suppression, invalidité, chevauchement, intégrité SQL, conflits concurrents et caches |
| Migrations | V1 crée le schéma; V2 ajoute `salles.description` et l'index unique des emails |
| Exécuteur | `MigrationExecutor`, lecture UTF-8 des ressources, exécution JDBC de toutes les instructions, historique et réexécution |
| Performance | Mesures réelles de `Statistics`, SQL préparés, hits/misses/puts, transactions et durée cumulée des requêtes |
| Assemblage | `MainApp` applique les migrations avant la validation Hibernate, puis exécute les scénarios et le rapport |
| Requêtes | JPQL paramétré, Criteria avec filtres optionnels, `JOIN FETCH`, entity graph et tri stable |
| Transactions | Commit/rollback et fermeture des contextes; vérification de version lors des modifications |
| Journalisation | `java.util.logging` pour les opérations métier et les refus de création |

## Cohérence des réservations

Les créneaux sont des intervalles `[début, fin)` : deux réservations contiguës sont permises. Les chevauchements sur la même salle sont refusés. Un verrou pessimiste sur la **ligne de la salle** sérialise la vérification et l'insertion; ce verrou couvre aussi les modifications du créneau. Le verrouillage optimiste `@Version` protège les modifications des réservations existantes. Ces mécanismes répondent à deux risques différents, vérifiés séparément par des tests utilisant deux threads ou deux `EntityManager`.

Les migrations sont appliquées sur la même base que l'application. `hibernate.hbm2ddl.auto=validate` vérifie le résultat; Hibernate ne recrée pas silencieusement le schéma. H2 valide implicitement ses instructions DDL : les scripts utilisent donc des instructions idempotentes et l'historique est enregistré après leur exécution. Ce petit exécuteur pédagogique suppose un seul processus de migration; pour des déploiements concurrents, un outil comme Flyway constitue une extension possible.

## Résultats et captures

Le dossier [evidence](evidence/) conserve les sorties réelles de Maven et de la démonstration. Les [captures](screenshots/) montrent le rapport navigateur de ces résultats, généré à partir des journaux et des fichiers XML Surefire; elles ne simulent pas un terminal. `evidence/report.html` permet de consulter le même rapport localement.

La démonstration vérifie notamment : une lecture de salle dans un premier contexte exécute **1 SQL**, la lecture dans un second contexte exécute **0 SQL** avec un hit L2; un deuxième appel de la requête cachée exécute **0 SQL** avec un hit du cache de requêtes. Les valeurs détaillées et le nombre de tests sont dans le rapport de l'exécution enregistrée.

Les versions retenues sont compatibles avec le parcours utilisant `javax.persistence` : Hibernate 5.4.32.Final, JPA 2.2, H2 1.4.200, Ehcache 3.8.1 et JUnit 5.11.4. Le pont `hibernate-jcache` remplace l'ancien module Ehcache 2. JAXB 2.3.8 est déclaré pour Java 17. Le calcul de durée cumulée additionne les statistiques de chaque requête, car l'API Hibernate 5.4 n'expose pas la méthode globale montrée dans le pseudo-code du cours.

Références : [guide Hibernate 5.4](https://docs.jboss.org/hibernate/orm/5.4/userguide/html_single/Hibernate_User_Guide.html), [Ehcache JSR-107](https://www.ehcache.org/documentation/3.8/107.html), [cours MLIAEdu](https://mliaedu.toubkalit.com/student/courses/28).

## Régénérer le rapport et les captures

Les images sont des captures navigateur de résultats réellement exécutés, conservés dans `evidence/`. Les tests proviennent des XML Surefire, également archivés dans `evidence/junit/`. Les empreintes SHA-256 des sources figurent dans `evidence/checksums.json`.

Avec Python 3 et Node.js disponibles, depuis la racine du dépôt :

```sh
mvn clean verify > evidence/test-output.txt 2>&1
mvn exec:java > evidence/demo-output.txt 2>&1
python scripts/generate_report.py
npm install --no-save playwright
npx playwright install chromium
node scripts/capture-results.cjs
```

Le rapport se consulte aussi directement dans `evidence/report.html`. Les captures générées sont `screenshots/results.png` et `screenshots/tests.png`.

![Résultats de la démonstration](screenshots/results.png)

![Résultats des tests](screenshots/tests.png)
