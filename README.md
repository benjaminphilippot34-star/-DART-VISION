# DART VISION

Application Android de comptage et d'entraînement aux fléchettes, conçue pour fonctionner en partie locale sans connexion Internet.

## État du projet

Le projet est en développement actif. La compilation Android et les tests unitaires passent sur GitHub Actions. La dernière compilation validée produit un APK de débogage installable.

Fonctions déjà présentes :
- Partie locale hors ligne et catalogue de 13 modes de jeu/entraînement.
- Moteur X01 avec règles d'entrée/sortie, bust, annulation, manches, sets et sauvegarde/restauration de la partie.
- Saisie manuelle de 1 à 3 fléchettes pour les moteurs de jeu.
- Caméra CameraX, aperçu et analyse des changements d'image.
- Calibration de la cible par sélection des quatre coins, correction perspective et sauvegarde locale.
- Affichage de scores candidats calculés à partir des régions modifiées, avec validation humaine obligatoire.
- Synchronisation locale de base entre téléphones sur le même réseau.

## Limites importantes à ce stade

- La caméra ne localise pas encore la pointe d'une fléchette. Les régions modifiées peuvent aussi provenir d'une ombre, d'un mouvement ou d'un changement de lumière.
- Les scores candidats ne sont donc pas des scores automatiques fiables et doivent être vérifiés.
- Le jumelage QR, la création automatique d'un hotspot, l'authentification réseau et la fusion de deux caméras ne sont pas encore intégrés.
- La sauvegarde complète des modes d'entraînement autres que X01 reste à faire.
- Aucune validation physique n'a encore été réalisée sur un Xiaomi 14T.

## Récupérer l'APK de développement

1. Ouvrir l'onglet [Actions](https://github.com/benjaminphilippot34-star/-DART-VISION/actions).
2. Ouvrir la dernière exécution verte de **Android build and tests** sur la branche `main`.
3. Dans **Artifacts**, télécharger `dart-vision-debug-apk`.
4. Extraire l'archive : elle contient `app-debug.apk` et le rapport des tests unitaires.
5. Transférer l'APK sur le téléphone Android et autoriser son installation si Android le demande.

Cet APK est une version de développement, pas encore une version finale publiée sur le Play Store.

## Documentation

- [Comparatif des applications et architecture](docs/COMPARATIF_APPLICATIONS_ET_ARCHITECTURE_FR.md)
- [Feuille de route](docs/ROADMAP_FR.txt)
- [Plan de tests terrain](docs/PLAN_TESTS_TERRAIN_FR.md)
- [Guide de première bêta Android](docs/GUIDE_PREMIERE_BETA_ANDROID_FR.md)

## Construction

- Android Gradle Plugin : 8.7.3
- Gradle : 8.9
- Kotlin : 2.0.21
- JDK : 17
- `minSdk` : 26
- `compileSdk` : 35

La compilation et les tests sont automatisés dans `.github/workflows/android-build.yml`.
