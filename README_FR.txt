DART VISION 0.6.1 — SOURCE ANDROID
===================================

Cette archive de dépôt contient le code source Android Kotlin de DART VISION 0.6.1.

CONTENU
- Application Android native Kotlin.
- Prévisualisation caméra CameraX et zoom.
- Serveur local sur le port 8765 pour partager l'état et les commandes de partie sur le même Wi-Fi.
- Contrôle OTA préparé : vérification de version, téléchargement APK, contrôle SHA-256 et ouverture de l'installateur Android.
- Icônes et identité visuelle DART VISION (les images lourdes sont conservées dans l'archive source d'origine, non copiées individuellement dans ce dépôt).

LIMITES À CONNAÎTRE
- Le moteur de jeu reste un prototype : règles complètes 501/301, double-out, bust, historique/annulation réelle et modes Cricket/Around the Clock/Shanghai/Killer restent à développer.
- La détection automatique des fléchettes et le calcul fiable des impacts ne sont pas intégrés.
- L'appairage se fait par adresse IP locale ; QR code et sécurité d'appairage restent à développer.
- Le projet n'a pas été compilé ni testé sur un Xiaomi dans cet environnement. Aucun APK prêt à installer n'est fourni.
- Le serveur est destiné à un réseau local de confiance ; ne pas l'exposer à Internet.

MISES À JOUR OTA
Le mécanisme est préparé, mais il ne fonctionnera qu'après configuration de latest.json, publication d'un APK signé et remplacement de OWNER/REPO dans MainActivity.kt.
Les mises à jour Android doivent utiliser la même clé de signature que l'application installée. Android demandera toujours une confirmation avant installation.

Projet Android : dossier android/.
