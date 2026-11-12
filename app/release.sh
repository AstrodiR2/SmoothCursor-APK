#!/usr/bin/env bash
set -e
VER="v1.2.0"

git push

cat > /tmp/notes.md <<'EOF'
## What's new
- **Image cursor.** Pick any image via the system file picker and crop it with a built-in editor that matches the selected cursor shape (drag, pinch, zoom slider, cancel/apply buttons on top). The image is drawn directly inside the cursor geometry using canvas clipping.
- **Private storage.** The chosen image is copied into the app's private storage. Its average color is calculated once on import and used for the colored trail.
- **Line highlight.** A smooth line highlight that follows the cursor, active only in the alternate screen buffer. Configurable: on/off, color, opacity, and speed.
- **Activity pulse.** The cursor's opacity pulses when the visible terminal content changes, then returns to breathing after a short pause. This is a heuristic based on output activity, not an exact detection of process state.
- **Settings.** New features live in their own sections of the main settings menu. English and Russian strings added.

## Known limitations
- Activity pulse is output-based and may not reflect actual process state.
- Stages 3-5 are still in progress.
EOF

gh release create "$VER" --target main \
  --title "$VER — Image cursor & line highlight" \
  --notes-file /tmp/notes.md --latest
