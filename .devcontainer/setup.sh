#!/usr/bin/env bash
# Prepares a GitHub Codespace for building CineVault.
# Safe to re-run: it only downloads the SDK when it is missing.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$HOME/android-sdk}"
export ANDROID_SDK_ROOT
export ANDROID_HOME="$ANDROID_SDK_ROOT"

# The repository was created on Windows, so gradlew is stored without the
# executable bit. Restore it so "./gradlew" works in the container.
chmod +x "$REPO_ROOT/gradlew" 2>/dev/null || true

# Pinned Android command-line tools (Linux). Keep the checksum in sync.
CMDLINE_TOOLS_BUILD="15859902"
CMDLINE_TOOLS_SHA256="4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_BUILD}_latest.zip"

SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

if [ ! -x "$SDKMANAGER" ] || [ ! -d "$ANDROID_SDK_ROOT/platforms/android-36.1" ]; then
  echo "==> Installing Android command-line tools ${CMDLINE_TOOLS_BUILD}..."
  mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools"

  tmp_dir="$(mktemp -d)"
  trap 'rm -rf "$tmp_dir"' EXIT

  curl -fsSL "$CMDLINE_TOOLS_URL" -o "$tmp_dir/cmdline-tools.zip"
  echo "${CMDLINE_TOOLS_SHA256}  $tmp_dir/cmdline-tools.zip" | sha256sum --check --status

  rm -rf "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  unzip -q "$tmp_dir/cmdline-tools.zip" -d "$tmp_dir"
  mv "$tmp_dir/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest"
  SDKMANAGER="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"

  echo "==> Accepting Android SDK licenses..."
  yes | "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" --licenses >/dev/null 2>&1 || true

  echo "==> Installing platform-tools, Android 36.1 and build-tools..."
  "$SDKMANAGER" --sdk_root="$ANDROID_SDK_ROOT" \
    "platform-tools" \
    "platforms;android-36.1" \
    "build-tools;36.0.0" \
    "build-tools;36.1.0"
fi

echo "==> Android SDK ready at $ANDROID_SDK_ROOT"

# Release signing is optional. Configure these Codespaces secrets to build an
# APK that can update an existing (release-signed) install:
#   ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD,
#   ANDROID_KEY_ALIAS, ANDROID_KEY_PASSWORD
if [ -n "${ANDROID_KEYSTORE_BASE64:-}" ]; then
  : "${ANDROID_KEYSTORE_PASSWORD:?ANDROID_KEYSTORE_PASSWORD is not set}"
  : "${ANDROID_KEY_ALIAS:?ANDROID_KEY_ALIAS is not set}"
  : "${ANDROID_KEY_PASSWORD:?ANDROID_KEY_PASSWORD is not set}"

  printf '%s' "$ANDROID_KEYSTORE_BASE64" | base64 --decode > "$REPO_ROOT/release-key.jks"
  cat > "$REPO_ROOT/key.properties" <<EOF
storeFile=../release-key.jks
storePassword=${ANDROID_KEYSTORE_PASSWORD}
keyAlias=${ANDROID_KEY_ALIAS}
keyPassword=${ANDROID_KEY_PASSWORD}
EOF
  chmod 600 "$REPO_ROOT/release-key.jks" "$REPO_ROOT/key.properties"
  echo "==> Release signing configured from Codespaces secrets."
else
  echo "==> No ANDROID_KEYSTORE_BASE64 secret: assembleRelease will not be signed."
  echo "    See CODESPACES.md to add the signing secrets."
fi

echo "==> Build with: ./gradlew assembleRelease"
