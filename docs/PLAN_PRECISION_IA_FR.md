DART VISION — PLAN TECHNIQUE DE PRECISION
============================================

OBJECTIF
Obtenir un score fiable à partir d'une caméra de smartphone, sans annoncer un résultat incertain comme s'il était certain. Le mode à une caméra est prioritaire. Une deuxième caméra restera une extension facultative.

1. ACQUISITION IMAGE
- Garder la cible entière dans le champ, avec une petite marge autour.
- Préférer une caméra aussi frontale que possible.
- Verrouiller autant que possible mise au point, exposition et balance des blancs après installation.
- Éviter les changements de zoom pendant une volée.
- Enregistrer une image de référence après stabilisation de la cible.
- Détecter les changements de cadrage, vibrations ou déplacement du téléphone et demander une nouvelle calibration si nécessaire.

2. CALIBRATION
- Définir le centre du bull et les limites des anneaux.
- Définir l'orientation du secteur 20 et les limites angulaires des 20 secteurs.
- Utiliser une transformation géométrique pour corriger perspective et inclinaison.
- Enregistrer une calibration par appareil, caméra, orientation et niveau de zoom.
- Fournir une correction manuelle guidée et un contrôle visuel de la calibration.
- Ne pas déduire une calibration fiable d'une simple détection approximative du cercle.

3. DÉTECTION D'UNE NOUVELLE FLÉCHETTE
- Comparer les images avant et après le lancer.
- Aligner les images pour compenser les petites vibrations avant de calculer les différences.
- Séparer les changements persistants des ombres, reflets et mouvements temporaires.
- Suivre les fléchettes déjà présentes pour éviter de les compter une deuxième fois.
- Détecter les retraits et les déplacements de fléchettes.
- Analyser plusieurs images stabilisées plutôt qu'une seule image isolée.

4. LOCALISATION DE LA POINTE
- Estimer l'axe de la fléchette et rechercher son extrémité côté cible.
- Ne jamais utiliser le centre du corps ou de l'empennage comme point d'impact.
- Tenir compte des occultations par les autres fléchettes.
- Si la pointe est cachée ou si plusieurs hypothèses sont plausibles, signaler une incertitude et demander une validation.
- Prévoir une correction manuelle de la pointe sur l'image.

5. CALCUL DU SCORE
- Convertir la position de la pointe dans le repère calibré de la cible.
- Déterminer le secteur angulaire, puis la zone radiale : simple, double, triple, bull extérieur ou bull intérieur.
- Traiter explicitement les limites entre deux zones : ne pas inventer une certitude lorsqu'un impact est exactement sur une séparation.
- Tester les règles de jeu séparément de la vision : la vision fournit une zone, le moteur de jeu applique les règles.

6. DEUXIÈME CAMÉRA
- Le deuxième téléphone fournit un angle indépendant, pas une simple copie de la première image.
- Chaque caméra possède sa propre calibration.
- Associer les observations des deux vues uniquement lorsqu'elles concernent le même lancer.
- Utiliser la seconde vue pour lever les occultations et réduire l'incertitude, sans rendre le mode simple caméra dépendant du second appareil.

7. MESURE DE LA QUALITÉ
Créer un jeu d'essai annoté avec des photos et séquences réelles, couvrant :
- les 20 secteurs, simples, doubles et triples ;
- bull extérieur et bull intérieur ;
- impacts proches des fils et séparations ;
- trois fléchettes groupées et pointes masquées ;
- fléchettes retirées, déplacées ou rebondies ;
- éclairages, ombres, reflets et cadrages variés.

Mesures obligatoires :
- taux de détection d'une nouvelle fléchette ;
- exactitude du point d'impact ;
- exactitude du secteur et de la zone ;
- taux de faux lancers et de doubles comptages ;
- taux de demandes de validation humaine.

Aucun pourcentage de précision ne doit être annoncé avant mesure sur un jeu d'essai indépendant. Les essais doivent être séparés entre données de développement et données de validation.

8. ORDRE D'IMPLÉMENTATION
A. Stabiliser acquisition et calibration.
B. Construire un outil de visualisation des images avant/après et des points détectés.
C. Implémenter le suivi des fléchettes présentes et la détection des changements persistants.
D. Ajouter la localisation de la pointe et le calcul géométrique du score.
E. Ajouter le niveau de confiance et la validation manuelle.
F. Tester sur séquences réelles, puis seulement optimiser vitesse et consommation.
G. Ajouter la fusion multi-caméras.

ÉTAT ACTUEL
Ce document décrit la cible technique et le plan de validation. Le moteur de vision automatique n'est pas encore implémenté et aucune précision n'a encore été mesurée. Le dépôt ne contient pas encore d'APK compilé ou validé.
