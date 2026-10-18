# Site vitrine Routix

Le site est publié sur **https://1337detailing.github.io/Routix/** par GitHub Pages à partir de `docs/`. Une copie synchronisée des sources est conservée dans `site/` pour édition et archivage.

## Développement local

```sh
cd docs
python3 -m http.server 8000
```

Ouvrir http://localhost:8000/

Le site utilise HTML/CSS/JS natifs sans compilation, une scène Three.js chargée à la demande, GSAP et Lenis comme améliorations facultatives. Il garde un visuel de secours si WebGL ou un CDN est indisponible. La variable CSS `--sand` dans `styles.css` contrôle l'accent.

La page renvoie vers le dépôt Routix et le workflow de compilation de l'APK. Le téléchargement direct remplace automatiquement le lien lorsque GitHub publie une vraie release comprenant un fichier .apk. Une release brouillon n'est pas présentée comme publique.

## Publication

Modifier et publier `docs/` sur `master`. Le workflow `verify-routix-site.yml` vérifie les sources et GitHub Pages déploie automatiquement. Les chemins `site/` et `docs/` doivent rester synchronisés.

Le code source Android reste dans `RoutixStandalone/` et n'est pas modifié par cette refonte.
