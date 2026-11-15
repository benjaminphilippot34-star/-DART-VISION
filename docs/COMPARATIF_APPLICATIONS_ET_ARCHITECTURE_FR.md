DART VISION — SYNTHÈSE DES MEILLEURES APPROCHES DU MARCHÉ
=============================================================

BUT
Construire une application Android de comptage automatique pour cible traditionnelle, en utilisant un ou plusieurs téléphones. Cette synthèse combine des idées de plusieurs produits publics. Elle ne copie ni leur code ni leurs interfaces propriétaires.

RÉFÉRENCES ÉTUDIÉES
- Dartsmind : comptage à partir de la caméra arrière d'un iPhone/iPad, avec possibilité d'un second appareil selon le comparatif consulté.
- Autodarts Lens : transforme un téléphone ou une tablette en compteur caméra.
- Darts Score Handsfree : approche Android avec caméra et usage mains libres.
- Scolia : système de vision multi-caméras, suivi des fléchettes et gestion des retraits/rebonds.
- Target Omni : ensemble de caméras HD et éclairage intégré, associé à DartCounter.
- GRAN EYE : approche compacte à deux caméras.
Sources publiques consultées le 15 novembre 2026 :
- https://dolfdarts.com/fr/electronic-darts/auto-scoring
- https://dartsautoscoring.com/
- https://www.flameappsdevelopment.com/fr/flechettes/application-compteur-flechettes

IMPORTANT
Les chiffres de précision et les promesses commerciales publiés par les fabricants ne sont pas des mesures indépendantes. DART VISION ne revendiquera aucun taux de précision avant des essais reproductibles sur des séquences annotées. Les applications comparées n'ont pas toutes les mêmes plateformes ni les mêmes exigences matérielles.

CE QU'ON RETIENT
1. Téléphone comme caméra : commencer avec la caméra arrière du smartphone, sur support fixe, cadrée sur toute la cible.
2. Deuxième téléphone facultatif : ajouter une seconde vue indépendante pour les fléchettes masquées ou les impacts ambigus. Le mode mono-caméra doit rester fonctionnel.
3. Éclairage intégré ou maîtrisé : viser une lumière uniforme, diffuse et stable. Un anneau LED peut aider, mais ne doit pas créer de reflets sur les pointes ou les fils.
4. Calibration obligatoire : centre du bull, rayon des anneaux, orientation du 20, perspective et zoom. Vérifier visuellement la calibration avant de jouer.
5. Suivi d'état de la cible : mémoriser les fléchettes déjà présentes, détecter les nouvelles, les retraits, les déplacements et les rebonds. Ne pas recompter une fléchette déjà suivie.
6. Validation humaine : afficher l'impact supposé et son score ; si la pointe est cachée ou proche d'un fil, demander confirmation et permettre de déplacer le point d'impact.
7. Jeu et vision séparés : le module caméra renvoie une position/zone avec un niveau de confiance ; le moteur de règles applique 501, 301, Cricket et les autres modes.
8. Synchronisation locale : le téléphone caméra est maître de la partie ; les autres téléphones rejoignent via appairage sécurisé et affichent/commandent la même partie sur le Wi-Fi local, sans Internet obligatoire.
9. Expérience mains libres : annonces vocales paramétrables, commandes vocales prudentes, correction/annulation facile et écran lisible à distance.
10. Historique et statistiques : conserver les visites, moyennes, doubles, triples, checkout et progression, avec export/sauvegarde facultatifs.

ARCHITECTURE CIBLE
- Téléphone A (maître) : caméra arrière, CameraX, calibration propre à l'appareil, détection, calcul du niveau de confiance, état de partie.
- Téléphone B (optionnel) : seconde caméra synchronisée, avec calibration indépendante ; sert à lever une occultation ou une ambiguïté.
- Téléphones joueurs : tableau de score, sélection des joueurs/modes, correction et commandes vocales.
- Réseau : Wi-Fi local ; appairage par QR code avec jeton temporaire, authentification des commandes, reconnexion et découverte réseau.
- Cloud : strictement facultatif pour sauvegardes, profils et statistiques distantes.
- PC : facultatif pour développement et entraînement ; jamais nécessaire pour jouer.

PIPELINE DE DÉTECTION À CONSTRUIRE
A. Capturer des images stables avant et après le lancer.
B. Vérifier que la caméra et la cible n'ont pas bougé ; réaligner les images.
C. Détecter les changements persistants, en filtrant ombres, reflets et variations d'exposition.
D. Associer les nouvelles formes aux fléchettes déjà présentes et détecter les retraits.
E. Estimer l'axe de chaque fléchette puis localiser l'extrémité côté cible : la pointe, jamais le centre du fût ou l'empennage.
F. Convertir le point dans le repère de cible calibré, puis déterminer secteur et anneau.
G. Estimer l'incertitude ; si plusieurs impacts sont plausibles, ne pas valider automatiquement.
H. Si une seconde vue existe, fusionner uniquement les observations du même lancer après calibration séparée.
I. Montrer le résultat, attendre la validation si nécessaire, puis transmettre le score au moteur de jeu.

PLAN DE VALIDATION
Créer un corpus de photos et vidéos réelles, annotées manuellement, avec :
- les 20 secteurs et les zones simple/double/triple ;
- bull extérieur et bull intérieur ;
- impacts proches des fils et sur les séparations ;
- fléchettes serrées, croisées et pointes occultées ;
- retrait, rebond, déplacement et cible momentanément masquée ;
- différents éclairages, ombres, reflets, distances, téléphones et niveaux de zoom.
Mesurer séparément :
- détection d'une nouvelle fléchette ;
- localisation de la pointe en pixels et dans le repère de cible ;
- exactitude secteur/zone ;
- faux positifs et doubles comptages ;
- retraits/rebonds correctement reconnus ;
- fréquence des validations manuelles ;
- latence et consommation.
Séparer les séquences de développement et de validation. Publier les résultats et les conditions de test ; ne pas extrapoler un score marketing.

ORDRE DE DÉVELOPPEMENT RECOMMANDÉ
1. Stabiliser caméra, cadrage et calibration.
2. Construire un écran de diagnostic image avant/après.
3. Suivre les fléchettes déjà présentes et identifier nouvelles fléchettes/retraits.
4. Localiser réellement la pointe et calculer la zone.
5. Ajouter confiance, correction et validation.
6. Constituer le corpus de tests et mesurer les résultats.
7. Ajouter la deuxième caméra et la fusion multi-vues.
8. Finaliser appairage sécurisé, moteur de jeu complet, voix et statistiques.

ÉTAT HONNÊTE AU 15/11/2026
Le dépôt contient une base Android CameraX, un analyseur simple de changements d'image et un calculateur géométrique de score avec tests unitaires. Cela ne constitue pas encore une IA qui reconnaît les pointes de fléchettes. Le pipeline décrit ici est le plan de travail, pas une fonctionnalité déjà opérationnelle.
