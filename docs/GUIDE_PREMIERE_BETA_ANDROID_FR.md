# Guide de première prise en main — DART VISION

Ce guide concerne l'APK de développement Android. Il sert à vérifier l'installation, les commandes de jeu et le fonctionnement caméra avant d'engager les essais de reconnaissance automatique.

## 1. Récupérer et installer l'APK

1. Ouvrir [les exécutions Android](https://github.com/benjaminphilippot34-star/-DART-VISION/actions/workflows/android-build.yml).
2. Choisir la dernière exécution terminée avec le statut **Success** sur la branche `main`.
3. Dans la section **Artifacts**, télécharger `dart-vision-debug-apk`.
4. Décompresser l'archive et récupérer `app-debug.apk`.
5. Copier le fichier sur le téléphone Android, l'ouvrir et autoriser l'installation de cette application si Android le demande.

Si Android indique qu'une application du même nom est déjà installée avec une autre signature, désinstaller l'ancienne version de développement avant de réinstaller. Attention : cela peut effacer les données locales de l'application.

## 2. Première ouverture

- Autoriser l'accès à la caméra.
- Poser le téléphone sur un support stable, face à la cible autant que possible.
- Cadrer la cible entière avec une petite marge autour ; éviter de couper le double extérieur.
- Éviter les reflets, les ombres mobiles et les changements d'éclairage.
- Utiliser le zoom optique si le téléphone en dispose. Le zoom numérique ne crée pas de détail supplémentaire.
- Appuyer sur **Calibrer la cible**, puis toucher les quatre coins extérieurs dans l'ordre : haut gauche, haut droite, bas droite, bas gauche.
- Si la cible bouge ou si la caméra change de position ou de zoom, refaire la calibration.

## 3. Vérifications fonctionnelles

### Partie et saisie manuelle
- Démarrer une partie X01.
- Vérifier le score initial et le nom du joueur.
- Appuyer sur **Saisir un tour** et entrer par exemple `T20, S20, D16`.
- Vérifier que le score diminue correctement.
- Tester **Annuler**, puis vérifier que le score précédent revient.
- Tester une saisie invalide comme `T25` : elle doit être refusée.
- Fermer puis rouvrir l'application et vérifier que la partie X01 est restaurée.

### Caméra et calibration
- Vérifier que l'aperçu reste fluide et que l'application ne se ferme pas.
- Enregistrer une **Référence avant lancer**.
- Lancer une fléchette, attendre que la cible soit immobile, puis appuyer sur **Mesurer changement**.
- Vérifier que l'analyse affiche des zones ou indique qu'aucune zone exploitable n'a été trouvée.
- Ne pas considérer un score candidat comme validé : il s'agit uniquement d'une estimation issue d'un changement d'image.

## 4. Ce qui n'est pas encore validé

- La détection fiable de la pointe d'une fléchette et son score automatique.
- La distinction fiable entre une fléchette, une ombre, une main et un changement d'éclairage.
- Le traitement des fléchettes qui se chevauchent ou masquent une autre pointe.
- Le fonctionnement sur tous les modèles et toutes les orientations de caméra.
- La synchronisation multi-téléphones dans des conditions réelles.
- La validation physique sur Xiaomi 14T.

Ne pas utiliser les scores candidats pour arbitrer une partie officielle. La saisie manuelle reste la méthode de score fiable à ce stade.

## 5. Informations utiles en cas de problème

Noter :
- le modèle du téléphone et la version Android ;
- l'étape exacte où le problème apparaît ;
- le message affiché, si présent ;
- si le problème concerne l'installation, la caméra, la calibration, le score ou la sauvegarde ;
- si possible, une capture d'écran sans données personnelles.

Cette liste permettra de reproduire le problème et d'éviter de confondre une limite connue de la reconnaissance d'image avec un défaut de l'application.
