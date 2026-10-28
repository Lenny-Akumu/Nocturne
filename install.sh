#!/usr/bin/env sh
# Nocturne for Linux: curl -fsSL https://raw.githubusercontent.com/Lenny-Akumu/Nocturne/main/install.sh | sh
# Installs (or updates) into ~/.local/share/nocturne, adds the `nocturne` command and a menu entry.
# Uninstall: curl -fsSL https://raw.githubusercontent.com/Lenny-Akumu/Nocturne/main/install.sh | sh -s -- --uninstall
set -eu

REPO="Lenny-Akumu/Nocturne"
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
DIR="$DATA/nocturne"
BIN="$HOME/.local/bin"
APPS="$DATA/applications"
ICONS="$DATA/icons/hicolor/512x512/apps"

say() { printf '\033[1;35m▸\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m✗\033[0m %s\n' "$*" >&2; exit 1; }

if [ "${1:-}" = "--uninstall" ]; then
  rm -rf "$DIR" "$BIN/nocturne" "$APPS/nocturne.desktop" "$ICONS/nocturne.png"
  say "Nocturne removed (your settings and library are kept)."
  exit 0
fi

[ "$(uname -s)" = "Linux" ] || die "This installer is for Linux."
case "$(uname -m)" in x86_64|amd64) ;; *) die "Only x86_64 builds are published for now." ;; esac
command -v curl >/dev/null || die "curl is required."
command -v tar >/dev/null || die "tar is required."

# libVLC plays the audio
if ! ldconfig -p 2>/dev/null | grep -q 'libvlc\.so'; then
  say "Installing VLC (libVLC plays the audio)…"
  SUDO=""; [ "$(id -u)" -ne 0 ] && SUDO="sudo"
  if command -v apt-get >/dev/null; then $SUDO apt-get update -qq && $SUDO apt-get install -y -qq vlc
  elif command -v dnf >/dev/null; then $SUDO dnf install -y vlc
  elif command -v pacman >/dev/null; then $SUDO pacman -S --noconfirm --needed vlc
  elif command -v zypper >/dev/null; then $SUDO zypper -n install vlc
  else say "Couldn't install VLC automatically — install the 'vlc' package yourself."; fi
fi

say "Looking for the latest release…"
URL=$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | grep -o '"browser_download_url": *"[^"]*-linux-x64\.tar\.gz"' | head -1 | sed 's/.*"\(http[^"]*\)"/\1/')
[ -n "$URL" ] || die "No Linux build in the latest release yet."

TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
say "Downloading $(basename "$URL")…"
curl -fL --progress-bar "$URL" -o "$TMP/nocturne.tar.gz"
tar -xzf "$TMP/nocturne.tar.gz" -C "$TMP"
SRC=$(find "$TMP" -mindepth 1 -maxdepth 1 -type d | head -1)
[ -x "$SRC/bin/Nocturne" ] || die "Unexpected archive layout."

rm -rf "$DIR"
mkdir -p "$(dirname "$DIR")" "$BIN" "$APPS" "$ICONS"
mv "$SRC" "$DIR"
ln -sf "$DIR/bin/Nocturne" "$BIN/nocturne"
[ -f "$DIR/lib/Nocturne.png" ] && cp "$DIR/lib/Nocturne.png" "$ICONS/nocturne.png"

cat > "$APPS/nocturne.desktop" <<DESKTOP
[Desktop Entry]
Type=Application
Name=Nocturne
Comment=Music from YouTube Music, Spotify and SoundCloud
Exec=$DIR/bin/Nocturne %u
Icon=nocturne
Terminal=false
Categories=AudioVideo;Audio;Player;
MimeType=x-scheme-handler/nocturne;
StartupWMClass=app-nocturne-MainKt
DESKTOP
command -v update-desktop-database >/dev/null && update-desktop-database "$APPS" >/dev/null 2>&1 || true
command -v xdg-mime >/dev/null && xdg-mime default nocturne.desktop x-scheme-handler/nocturne >/dev/null 2>&1 || true

say "Nocturne installed. Open it from your app menu or run: nocturne"
case ":$PATH:" in *":$BIN:"*) ;; *) say "Add $BIN to PATH to use the 'nocturne' command." ;; esac
