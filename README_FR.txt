DART VISION 0.6.1 — SOURCE ANDROID
===================================

Cette archive de dépôt contient le code source Android Kotlin de DART VISION 0.6.1.

CONTENU
- Application Android native Kotlin.
- Prévisualisation caméra CameraX et zoom.
- Mode solo hors ligne : le jeu reste utilisable sans Wi-Fi ni Internet, et l'état de la partie est sauvegardé localement.
- Serveur local sur le port 8765 pour partager l'état et les commandes de partie lorsque plusieurs téléphones sont sur le même réseau local.
- Contrôle OTA préparé : vérification de version, téléchargement APK, contrôle SHA-256 et ouverture de l'installateur Android.
- Icônes et identité visuelle DART VISION (les images lourdes sont conservées dans l'archive source d'origine, non copiées individuellement dans ce dépôt).

ÉTAT RÉEL ET LIMITES
- Le mode X01 dispose d'un moteur local avec double-out, bust, manches, sets et alternance du joueur qui commence. Les règles avancées doivent encore être validées en situation réelle.
- Plusieurs moteurs d'entraînement et de jeu (Cricket, Tactics, Shanghai, Bob's 27, Halve-It, etc.) sont présents et reliés à une saisie manuelle. Ils ne remplacent pas encore des écrans dédiés complets ; leurs règles doivent être testées.
- La géométrie de la cible et la transformation perspective sont codées et testées sur des coordonnées synthétiques. La calibration n'est pas encore pilotée depuis l'écran caméra.
- La caméra fournit un aperçu et une analyse des changements de luminance. Cela ne détecte pas encore la pointe d'une fléchette et ne calcule pas automatiquement un score fiable.
- Le solo manuel fonctionne sans Internet. Le partage actuel utilise un serveur HTTP local sur le port 8765 et nécessite un réseau local commun ; l'appairage QR, l'authentification et la création automatique d'un hotspot ne sont pas intégrés.
- Aucune validation sur cible physique ni sur Xiaomi 14T n'a encore été effectuée. La version debug produite par GitHub Actions est destinée aux essais, pas à une diffusion publique.
- Le serveur local n'est pas sécurisé pour une exposition à Internet.

MISES À JOUR OTA
Le mécanisme est préparé, mais il ne fonctionnera qu'après configuration de latest.json, publication d'un APK signé et remplacement de OWNER/REPO dans MainActivity.kt.
Les mises à jour Android doivent utiliser la même clé de signature que l'application installée. Android demandera toujours une confirmation avant installation.

Projet Android : dossier android/.
