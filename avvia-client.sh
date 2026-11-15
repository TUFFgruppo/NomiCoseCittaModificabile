#!/usr/bin/env bash
#
# avvia-client.sh - script per avviare il Client con un video di introduzione.
#
# Uso:
#   ./avvia-client.sh                    -> gioca il file intro.mp4 nella cartella corrente
#   ./avvia-client.sh percorso/video.mp4 -> gioca il video indicato come intro
#
# Lo script:
#   1. cerca un player video installato (ffplay, mpv, vlc);
#   2. imposta la variabile d'ambiente INTRO_PLAYER che il Client usa
#      per riprodurre l'intro (vedi Client.java, metodo riproduciIntro);
#   3. compila i sorgenti Java e avvia il Client.
#
set -euo pipefail

CARTELLA="$(cd "$(dirname "$0")" && pwd)"
VIDEO="${1:-$CARTELLA/intro.mp4}"

# --- 1. cerca un player video disponibile nel sistema -------------------
PLAYER=""
for candidato in ffplay mpv vlc cvlc; do
    if command -v "$candidato" >/dev/null 2>&1; then
        PLAYER="$(command -v "$candidato")"
        break
    fi
done

if [ -n "$PLAYER" ]; then
    export INTRO_PLAYER="$PLAYER"
    echo "Player intro trovato: $PLAYER"
else
    echo "AVVISO: nessun player video installato (ffplay/mpv/vlc)."
    echo "        Il client partira senza video di introduzione."
    echo "        Installa ffmpeg (sudo apt install ffmpeg) per l'intro."
fi

# --- 2. avvisa se il video non esiste ------------------------------------
if [ ! -f "$VIDEO" ]; then
    echo "AVVISO: video intro non trovato: $VIDEO"
    echo "        Aggiungi un file (es. intro.mp4) oppure passalo come argomento:"
    echo "        ./avvia-client.sh percorso-del-tuo-video.mp4"
fi

# --- 3. compila e avvia il client ----------------------------------------
echo "Compilazione dei sorgenti..."
javac -d "$CARTELLA/build" "$CARTELLA"/*.java

echo "Avvio del client..."
java -cp "$CARTELLA/build" Client
