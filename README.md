# Routix

Application Android de suivi de parcours et de tournées GPS : enregistrement, repères, navigation sur une trace et import/export GPX, KML et CSV.

**Site :** https://1337detailing.github.io/Routix/  
**Confidentialité :** [Politique de confidentialité](PRIVACY.md)

## Compiler

JDK 17 et SDK Android 36 sont requis.

```bash
cd RoutixStandalone
gradle :app:testDebugUnitTest :app:assembleDebug
```

Les builds de test sont disponibles dans [GitHub Actions](https://github.com/1337Detailing/Routix/actions/workflows/build-debug-apk.yml).

Pour publier sur Google Play, consulter [PLAY_STORE.md](PLAY_STORE.md).

## Licence

Code du dépôt : [GPL-3.0](LICENSE). Les dépendances et données cartographiques gardent leurs licences et attributions respectives ; voir [AUTHORS.md](AUTHORS.md).
