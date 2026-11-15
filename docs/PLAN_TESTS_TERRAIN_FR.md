PLAN DE VALIDATION TERRAIN — DART VISION
===========================================

Objectif
--------
Livrer une version Android installable pour effectuer des essais structurés sur une vraie cible. Les essais servent à identifier les défauts ; ils ne doivent pas être présentés comme une validation de précision avant mesure.

PHASE 1 — INSTALLATION ET STABILITÉ
----------------------------------
- Installer l'APK debug sur le Xiaomi 14T.
- Vérifier le lancement, l'autorisation caméra, l'aperçu, le zoom et la fermeture/réouverture.
- Vérifier que le mode solo démarre sans Wi-Fi ni Internet.
- Vérifier qu'une partie manuelle reste cohérente après fermeture et redémarrage.
- Noter tout plantage, écran bloqué, ralentissement ou surchauffe.

PHASE 2 — PARTIES ET RÈGLES
--------------------------
- X01 : tester 301 et 501, simple-in, double-in, double-out, bust, checkout, annulation, nouvelle manche, sets et alternance du joueur qui commence.
- Cricket/Tactics : tester les marques, fermeture, points et fin de partie.
- Shanghai : vérifier le passage de chaque joueur, la progression des manches et le Shanghai.
- Entraînements : vérifier la progression des cibles, les tentatives, les scores et les fins de parcours.
- Comparer chaque résultat avec le calcul manuel et noter les divergences.

PHASE 3 — CAMÉRA ET CALIBRATION
------------------------------
- Stabiliser le téléphone sur un support ; cadrer toute la cible.
- Vérifier la netteté, l'exposition, les reflets et les ombres.
- Refaire plusieurs prises de vue sans fléchette puis avec une fléchette.
- Mesurer les changements d'image et noter les faux changements causés par la lumière, les mouvements ou les ombres.
- La localisation de pointe et le score automatique ne seront considérés testables qu'après leur intégration complète à l'écran caméra.

PHASE 4 — MULTI-TÉLÉPHONES
--------------------------
- Vérifier la connexion de deux téléphones sur le même réseau local.
- Envoyer plusieurs commandes depuis le téléphone secondaire.
- Couper le réseau secondaire, vérifier que le téléphone principal continue la partie, puis rétablir la connexion.
- Vérifier la resynchronisation et l'absence de perte de score.
- Ne pas exposer le serveur local à Internet : l'authentification n'est pas encore en place.

FICHE DE RETOUR
---------------
Pour chaque anomalie, relever :
1. Version de l'application et modèle du téléphone.
2. Mode de jeu et situation exacte.
3. Étapes pour reproduire le problème.
4. Résultat attendu.
5. Résultat observé.
6. Capture d'écran ou courte vidéo si possible.

CRITÈRE DE VALIDATION
---------------------
Une fonction est déclarée validée uniquement après reproduction réussie sur appareil réel et contrôle du résultat attendu. La détection automatique des pointes doit être évaluée sur des séquences annotées ; aucune précision ne sera annoncée sans mesure.
