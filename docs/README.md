# CineVault website

The official CineVault download landing page source. Publish it with GitHub Pages from
`main` → `/docs` (enable Pages in the repository settings); the intended URL is
**https://itzsnsr.github.io/CineVault/**.

GitHub Pages is not enabled yet, so that URL still returns 404.

Plain HTML, CSS and JavaScript — no build step, no framework, no API keys.

## Files

```text
docs/
├── .nojekyll                 # serve files as-is (no Jekyll processing)
├── index.html                # page structure, SEO and Open Graph metadata
├── styles.css                # Midnight Amethyst theme, layout and breakpoints
├── script.js                 # version badge, mobile nav, FAQ accordion
├── README.md                 # this file
└── assets/
    ├── cv-mark.svg           # CV monogram, vectorized from the app's launcher icon
    ├── favicon.svg           # rounded tile favicon built from the same mark
    ├── apple-touch-icon.png  # 180 × 180 iOS home-screen icon
    ├── cinevault-icon.png    # 320 × 320 authentic app icon for the install panel
    └── og-image.png          # 1200 × 630 social preview card
```

Every path in `index.html` is relative, so the page works both at
`https://itzsnsr.github.io/CineVault/` and from any other subdirectory.

## Local preview

From the repository root:

```bash
python -m http.server 8000
```

Then open <http://localhost:8000/docs/>. A server is needed because the page fetches
`version.json` over HTTPS; opening `index.html` directly still renders, but the version
badge stays at its built-in fallback.

To mirror the production subpath exactly, serve a directory in which `docs` is mounted
as `CineVault` (GitHub Pages serves `/CineVault/`). Any static server with that mount
works.

## Version badge and download link

- All Android buttons link to
  `https://github.com/itzSNSR/CineVault/releases/latest/download/CineVault.apk`.
  GitHub resolves `latest` to the newest stable (non-prerelease) release, so the website
  never needs a version bump.
- `script.js` reads the app's own `version.json` from the repository root
  (`raw.githubusercontent.com`, with `cdn.jsdelivr.net` as fallback) and shows the
  current `versionName`. If both requests fail, the static `v4.7` markup stays visible.
- The page never reports a download as successful; it only links to GitHub Releases.

## Android TV and web streaming

Both are intentionally static **Coming Soon** cards. There is no TV download, no mobile
APK relabelled as a TV build, and no browser player.

## Motion

The page carries a small, dependency-free motion system applied from the
[LottieFiles motion-design-skill](https://github.com/lottiefiles/motion-design-skill):
a staggered hero entrance (40–320 ms), scroll reveals with 50–70 ms stagger, gentle
ambient drift on the glows and floating cards, and press/hover feedback. Personality is
Premium — signature easing `cubic-bezier(.4,0,.2,1)`, durations 140/380/620 ms.

Only `translate`, `scale` and `opacity` animate, so nothing shifts layout. Reveal classes
are added by JavaScript alone and all motion sits behind
`@media (prefers-reduced-motion: no-preference)`, so reduced-motion and no-JS visitors get
the static page.

## Publishing a new app version

1. Publish the GitHub Release with the asset named exactly `CineVault.apk`.
2. Update `version.json` in the repository root.
3. Refresh the fallback version shown in `index.html` (`#hero-version` and
   `#latest-version`) and, if branding changes, regenerate the assets.

## GitHub Pages settings

Repository **Settings → Pages**:

- **Source:** Deploy from a branch
- **Branch:** `main`
- **Folder:** `/docs`

No custom domain and no Actions workflow are required. Do not change the repository's
visibility, release workflow or signing configuration for the website.

## Theme

`styles.css` uses the app's Midnight Amethyst palette in `:root`:
`#090A10` background, `#181422` cards, `#8B5CF6` primary purple, `#C4B5FD` glass
highlight, `#F5F5F7` text and `#B8ABC9` secondary text (from
`app/src/main/java/com/example/ui/theme/Color.kt`).
