FROM mcr.microsoft.com/devcontainers/java:1-17-bookworm

# The upstream Java image ships a Yarn apt repository whose signing key is
# missing, so "apt-get update" fails during devcontainer feature installation
# (the sshd feature exits 100 and the codespace falls back to recovery mode).
# Drop that source before features run.
RUN rm -f /etc/apt/sources.list.d/*yarn* /etc/apt/sources.list.d/yarnpkg* \
    && apt-get update
