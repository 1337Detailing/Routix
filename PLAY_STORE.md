# Publication Google Play — Routix

## Avant l’envoi
1. Créer un compte Play Console, vérifier le profil développeur et réserver `com.routix.app`.
2. Préparer les images de présentation sans annotations : icône 512 × 512 disponible dans `branding/play-store-512.png`; une image promotionnelle 1024 × 500 et au moins deux vraies captures d’écran seront encore nécessaires.
3. Publier et relire la politique de confidentialité : https://1337detailing.github.io/Routix/privacy.html.
4. Dans Play Console, remplir **App content**, classification, public cible, données et sécurité, confidentialité, sécurité des enfants et accès à l’application.
5. Justifier les permissions de localisation précise et en arrière-plan, ainsi que le service premier plan `location`, avec captures et démonstration de la fonction de suivi actif. Ne pas demander une permission que le scénario publié ne nécessite pas.
6. Tester sur Android 8, 12, 13, 14, 15 et 16+, y compris batterie faible, écran éteint, interruption GPS, refus d’autorisation, sauvegardes et restauration.
7. Choisir et **conserver** une clé d’upload privée. Google Play App Signing est recommandé. Ne jamais déposer de clé privée dans GitHub.
8. Dans GitHub Actions, définir les quatre secrets `ROUTIX_UPLOAD_KEYSTORE_BASE64`, `ROUTIX_UPLOAD_STORE_PASSWORD`, `ROUTIX_UPLOAD_KEY_ALIAS`, `ROUTIX_UPLOAD_KEY_PASSWORD` puis lancer **Build Play Store bundle**. Récupérer le bundle AAB signé.
9. Déposer le fichier sur une piste de test Play. Les nouveaux comptes personnels peuvent être soumis aux exigences de test fermé avant accès à la production.

## Fiche Play — brouillon FR
**Nom :** Routix

**Description courte :** Enregistrez, organisez et suivez vos tournées GPS sur une carte.

**Description :** Routix accompagne vos parcours terrain. Enregistrez vos tournées et vos repères, importez et exportez vos tracés GPX, consultez les cartes et retrouvez vos itinéraires pour les suivre à nouveau. Les enregistrements sont conservés sur l’appareil. Des cartes et données routières peuvent être chargées depuis des services externes lorsque vous utilisez la connexion.

## Play listing — EN draft
**Name:** Routix

**Short description:** Record, organise and follow GPS routes on a map.

**Full description:** Routix helps with field routes. Record your routes and markers, import and export GPX files, browse maps and follow saved journeys. Route recordings are stored on your device. Online maps and road information require external services.

## Notes
- Configuration Android actuelle : API cible 36 (exigence Play depuis août 2026), minSdk 26.
- La disponibilité d’un AAB signé ne vaut pas acceptation du Google Play Store.
- Une APK de debug installée sous `com.routix.app.debug` ne sera pas mise à jour directement par la version Play `com.routix.app`.
- La politique de confidentialité et les déclarations Google Play doivent refléter les appels aux services externes et les éventuelles sauvegardes Android.
- Le workflow de publication exige une signature et n’envoie rien automatiquement sur Play.
