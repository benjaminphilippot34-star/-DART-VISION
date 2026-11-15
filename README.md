# DART VISION

Application Android de comptage et d'entraînement aux fléchettes, conçue pour fonctionner en partie locale sans connexion Internet.

## Connexion Supabase (préparation 0.9.7)

- URL du projet et clé publishable configurées dans `BuildConfig`.
- Adaptateur Kotlin d'inscription/connexion Supabase ajouté (`SupabaseAuthClient`). Les appels doivent être exécutés sur un thread de fond.
- Schéma SQL initial avec profils, amitiés, parties, participants et tours disponible dans `supabase/migrations/0001_initial_schema.sql`.
- **La connexion n'est pas encore intégrée à l'interface et la synchronisation/multijoueur ne sont pas encore actifs.** Il faut exécuter la migration dans Supabase, puis intégrer l'écran de compte et les dépôts de données avant d'activer ces fonctions.

## État du projet

Version Android déclarée : **0.8.0** (versionCode 9). Le projet est en développement actif.

Fonctions déjà présentes :
- Partie locale hors ligne et catalogue de 13 modes de jeu/entraînement.
- Moteur X01 avec règles d'entrée/sortie, bust, annulation, manches, sets et sauvegarde/restauration de la partie.
- Saisie manuelle de 1 à 3 fléchettes pour les moteurs de jeu.
- Caméra CameraX, aperçu et analyse des changements d'image ; aperçu et cadrage vérifiés sur Xiaomi 14T.
- Calibration de la cible par sélection des quatre coins, correction perspective et sauvegarde locale.
- Affichage de scores candidats calculés à partir des régions modifiées, avec validation humaine obligatoire.
- Synchronisation locale de base entre téléphones sur le même réseau.

## Limites importantes à ce stade

- La caméra ne localise pas encore la pointe d'une fléchette. Les régions modifiées peuvent aussi provenir d'une ombre, d'un mouvement ou d'un changement de lumière.
- Les scores candidats ne sont donc pas des scores automatiques fiables et doivent être vérifiés.
- Le jumelage QR, la création automatique d'un hotspot, l'authentification réseau et la fusion de deux caméras ne sont pas encore intégrés.
- La sauvegarde complète des modes d'entraînement autres que X01 reste à faire.
- Le fonctionnement de l'aperçu et du cadrage caméra a été vérifié sur Xiaomi 14T ; la détection des pointes et la précision du score ne sont pas encore validées sur le terrain.


## Cahier des charges global DART VISION

Le projet doit rester une seule application, utilisable sur téléphone sans ordinateur ni logiciel caméra tiers.

- IA mixte (vision classique + IA embarquée), avec validation automatique seulement lorsque la confiance est suffisamment élevée et mesurée.
- 1, 2 ou 4 caméras : commencer en mono-caméra, puis ajouter des vues indépendantes.
- Sources vidéo visées : caméra du téléphone, USB compatible, RTSP et ONVIF selon compatibilité réelle.
- Positionnement de préférence face à la cible, cible entière visible avec une marge, zoom optique privilégié ; recalibration après tout changement de position ou de zoom.
- Téléphones joueurs avec affichage et commandes partagés : partie, joueurs, modes, scores, annulation, statistiques.
- Solo sans réseau ; multi-téléphones via Wi-Fi local ou hotspot sans Internet.
- Assistant intégré pour installation, connexion, cadrage, calibration et diagnostic.
- PC Windows facultatif pour développement et annotation, jamais requis pour jouer.

Les exigences complètes et l'ordre de réalisation sont décrits dans [la feuille de route](docs/ROADMAP_FR.txt) et [l'architecture détaillée](docs/COMPARATIF_APPLICATIONS_ET_ARCHITECTURE_FR.md).

**Attention :** USB, RTSP, ONVIF, multi-caméras, localisation réelle des pointes et validation automatique des scores restent des développements à réaliser. L'analyse actuelle de changements d'image ne suffit pas à annoncer un score fiable.

## Récupérer les APK de développement

Après une exécution réussie du workflow **Android build, tests and signed release** :

1. Ouvrir l'onglet [Actions](https://github.com/benjaminphilippot34-star/-DART-VISION/actions).
2. Ouvrir la dernière exécution verte sur la branche `main`.
3. Dans **Artifacts**, télécharger `dart-vision-debug-apk` pour l'APK de test.
4. Si les quatre secrets de signature sont configurés, télécharger aussi `dart-vision-signed-release-apk`.
5. Extraire l'archive : elle contient l'APK correspondant.

L'APK signé est destiné aux essais et à la distribution directe. Ce n'est pas une publication sur le Play Store.

## Secrets GitHub pour la signature Android

Dans **Settings → Secrets and variables → Actions**, le workflow utilise :
- `DARTVISION_KEYSTORE_BASE64` : keystore Android encodé en Base64.
- `DARTVISION_STORE_PASSWORD` : mot de passe du keystore.
- `DARTVISION_KEY_ALIAS` : alias de la clé.
- `DARTVISION_KEY_PASSWORD` : mot de passe de la clé.

Le workflow décode le keystore uniquement sur le runner GitHub, construit l'APK signé puis supprime le fichier temporaire. Ne jamais ajouter le keystore ni les mots de passe au dépôt.

## Construction

- Android Gradle Plugin : 8.7.3
- Gradle : 8.9
- Kotlin : 2.0.21
- JDK : 17
- `minSdk` : 26
- `compileSdk` : 35

La compilation, les tests unitaires et la génération des APK sont automatisés dans `.github/workflows/android-build.yml`.

## Documentation

- [Comparatif des applications et architecture](docs/COMPARATIF_APPLICATIONS_ET_ARCHITECTURE_FR.md)
- [Feuille de route](docs/ROADMAP_FR.txt)
- [Plan de tests terrain](docs/PLAN_TESTS_TERRAIN_FR.md)
- [Guide de première bêta Android](docs/GUIDE_PREMIERE_BETA_ANDROID_FR.md)
