# Building the APK in GitHub Codespaces

The dev container in `.devcontainer/` gives a Codespace JDK 17, the Android
SDK (platform 36.1, build-tools 36.1.0) and a Linux shell that can run the
Gradle wrapper. Builds run on GitHub's machine, not your laptop.

## 1. Push the branch first

Codespaces reads `.devcontainer/devcontainer.json` from the branch you open, so
the branch must be on GitHub:

```bash
git push -u origin fix/episode-subtitle-sync
```

## 2. Add release-signing secrets (needed to update your phone)

The APK installed on the phone is signed with `release-key.jks`. A debug APK
cannot update it (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). To build a release APK
that installs over it, give the Codespace the same keystore through secrets.
The keystore and `key.properties` are already gitignored, so nothing secret is
committed.

In GitHub: **repository → Settings → Secrets and variables → Codespaces → New
repository secret** (or **github.com/settings/codespaces** for all your repos).
Add four secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of `release-key.jks` |
| `ANDROID_KEYSTORE_PASSWORD` | `storePassword` from `key.properties` |
| `ANDROID_KEY_ALIAS` | `keyAlias` from `key.properties` |
| `ANDROID_KEY_PASSWORD` | `keyPassword` from `key.properties` |

On Windows, create the base64 value with PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("release-key.jks")) | Set-Clipboard
```

Secrets are injected when a Codespace **starts**. After adding or changing
them, stop and restart the Codespace, then run `bash .devcontainer/setup.sh`
once so it writes `release-key.jks` and `key.properties` into the workspace.

If you skip the secrets you can still build and test the debug APK, but do not
install it over the existing release-signed app (uninstalling first erases the
app's data).

## 3. Create the Codespace

1. Open the repository on GitHub.
2. **Code → Codespaces → Create codespace on fix/episode-subtitle-sync**.
3. Pick the closest region and the **4-core** machine (the dev container asks
   for 4 CPUs / 8 GB, so Codespaces preselects a suitable size).
4. Wait for the setup to finish. The terminal prints
   `==> Android SDK ready at /home/vscode/android-sdk`. The first run takes a
   few minutes; later starts are faster, especially with a prebuild (step 6).

If setup was interrupted, run it again:

```bash
bash .devcontainer/setup.sh
```

## 4. Build

```bash
# Release APK (signed when the secrets above are configured)
./gradlew assembleRelease

# Debug APK for quick testing
./gradlew assembleDebug
```

The first build downloads Gradle and all dependencies (roughly 10–20 minutes
on 4 cores); later builds reuse the caches and are much faster.

Outputs:

- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`

## 5. Download and install

In the Codespace Explorer, right-click the APK → **Download**. Then, with the
phone connected to your PC:

```bash
adb install -r app-release.apk
```

Or copy it from the Codespace with GitHub CLI (run this on your PC):

```bash
gh codespace cp -e remote:/workspaces/CineVault/app/build/outputs/apk/release/app-release.apk ./app-release.apk
```

The workspace folder is normally `/workspaces/<repository-name>`; check with
`pwd` in the Codespace terminal if the path differs. You can also upload the
APK to the phone and tap it to install.

## 6. Optional: prebuild for instant startup

Prebuilds run the dev container setup ahead of time and cache the Android SDK:

1. Repository **Settings → Codespaces → Prebuilds → Set up prebuild**.
2. Select the branch and the closest region.
3. Leave the default configuration (`.devcontainer/devcontainer.json`) and save.

Prebuilds rebuild when `.devcontainer/` changes. They consume Codespaces
storage/Actions minutes, so they are optional.

## Stopping the meter

Codespaces bills per core-hour while running. Stop it when you are done:
**github.com/codespaces → ⋯ → Stop**, or `gh codespace stop`. The default idle
timeout also stops it after 30 minutes without activity.

## Troubleshooting

- **`./gradlew: Permission denied`** — run `bash .devcontainer/setup.sh`, or use
  `bash gradlew assembleRelease`.
- **Missing Android SDK / `compileSdk 36.1`** — re-run
  `bash .devcontainer/setup.sh`; it installs `platforms;android-36.1`.
- **Release signing fails** — one of the four secrets is missing, or the
  Codespace was not restarted after adding them. Restart, run
  `bash .devcontainer/setup.sh`, and check `ls release-key.jks key.properties`.
- **Out of memory** — choose the 4-core / 16 GB machine type.
- **`local.properties`** — not needed in Codespaces. Gradle finds the SDK via
  `ANDROID_HOME`; the file is gitignored and only exists on your PC.

## Building from Windows with the helper script (default workflow)

Builds run in this Codespace by default; the PC only edits source and downloads
the result. Run the helper **from PowerShell** — Git Bash (MSYS) corrupts the
`gh codespace ssh` tunnel on Windows:

```powershell
.\scripts\cloud-build.ps1 -BuildType Debug
.\scripts\cloud-build.ps1 -BuildType Release
.\scripts\cloud-build.ps1 -BuildType Release -StopCodespace
```

What it does: verifies `gh`, starts the Codespace if it is stopped, snapshots the
local worktree (no git push), copies it with `gh codespace cp`, applies it to an
isolated `cloudbuild/...` branch, proves the remote tree hash equals the local
tree hash, runs `:app:assembleDebug` / `:app:assembleRelease`, verifies the APK
(application id, version code/name, `apksigner` signature, certificate), copies
it to `..\CineVault-CloudBuilds\<version>\`, and writes `build-info.txt`.

It stops with an error when the sync, the build or the signature check fails,
and it never publishes a release or edits `version.json`.

Release signing uses the Codespaces secrets; `.devcontainer/setup.sh` recreates
`release-key.jks` and `key.properties` inside the container. The expected release
certificate is pinned in `..\CineVault-CloudBuilds\expected-signing-cert.txt`.

### SSH requirement

`gh codespace ssh` and `gh codespace cp` need an SSH server in the container.
`.devcontainer/devcontainer.json` now declares
`ghcr.io/devcontainers/features/sshd:1`, so a rebuild includes it. In a container
created before that change, start it once in the Codespace terminal:

```bash
apt-get install -y openssh-server && service ssh start
```

(prefix both commands with privilege elevation, i.e. run them as the container's
administrator)
