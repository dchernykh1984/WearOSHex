#!/usr/bin/env bash
# Turn a game's round Zepp OS app icon into an Android adaptive launcher icon.
#
#   tools/make-launcher-icons.sh <source.png> <res-dir> <background-hex> [--keep-disc]
#
# For this app:
#
#   tools/make-launcher-icons.sh ../AmazfitSerpent/assets/common.r/icon.png \
#     wear/src/main/res '#14181C'
#
# Needs ImageMagick 7 (`brew install imagemagick`).
#
# An adaptive icon is two layers the launcher masks itself. Of its 108dp canvas
# only the middle 72dp survives every mask, so the source - which is a full-bleed
# round icon - is scaled to exactly that circle. Scaling it to the whole canvas
# instead is what silently crops the outermost part of the picture: on the snake
# icon it took the food pellet clean off.
#
# By default the solid disc the glyph is drawn on is peeled away and becomes the
# background layer, so the launcher's own mask has something to cut instead of
# clipping a disc that is already part of the picture. --keep-disc leaves the
# source alone, for an icon whose disc IS the illustration.
set -euo pipefail

src="$1"
res="$2"
background="$3"
keep_disc="${4:-}"

# 72dp of 108dp: the circle every launcher mask is guaranteed to show. As a
# fraction rather than a decimal so the arithmetic is the shell's own and the
# script needs nothing installed to do it.
SAFE_NUMERATOR=2
SAFE_DENOMINATOR=3

adaptive="108 mdpi 162 hdpi 216 xhdpi 324 xxhdpi 432 xxxhdpi"

# The glyph, with the disc it is drawn on taken away.
#
# Flattened onto the disc colour first: the rim between the disc and the
# transparent outside is anti-aliased - half-transparent pixels of the disc
# colour, which no -transparent on the opaque colour would ever match - and it
# would otherwise survive as a faint ring around the glyph.
#
# macOS ships bash 3.2, where an empty array under `set -u` is an error rather
# than nothing at all, so the two cases are two commands and not one command with
# a list of arguments that is sometimes empty.
# A directory, not `mktemp -t x` with .png glued on the end: that leaves the file
# mktemp actually created sitting in /tmp on every run, because the name being
# cleaned up is not the name that was made.
work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
peeled="$work/peeled.png"
if [ "$keep_disc" = "--keep-disc" ]; then
  cp "$src" "$peeled"
else
  magick "$src" -background "$background" -alpha remove -alpha off \
    -fuzz 20% -transparent "$background" "$peeled"
fi

set -- $adaptive
while [ "$#" -gt 0 ]; do
  size="$1"; density="$2"; shift 2
  inner=$(( size * SAFE_NUMERATOR / SAFE_DENOMINATOR ))
  mkdir -p "$res/mipmap-$density"
  # -strip, because ImageMagick otherwise stamps the creation date into every
  # PNG it writes: without it, re-running this on an unchanged icon rewrites five
  # files and shows five modified assets that are pixel for pixel what was already
  # committed.
  magick "$peeled" \
    -resize "${inner}x${inner}" \
    -background none -gravity center -extent "${size}x${size}" \
    -strip \
    "$res/mipmap-$density/ic_launcher_foreground.png"
done

# values/colors.xml is written whole, so it must hold nothing but the launcher
# background. If the app ever needs a second colour there, that colour belongs in
# its own file rather than in the one this script owns.
if [ -f "$res/values/colors.xml" ] && ! grep -q 'ic_launcher_background' "$res/values/colors.xml"; then
  echo "$res/values/colors.xml holds something this script did not write; refusing to overwrite it." >&2
  exit 1
fi

mkdir -p "$res/mipmap-anydpi" "$res/values"
cat > "$res/mipmap-anydpi/ic_launcher.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_foreground" />
</adaptive-icon>
XML

cat > "$res/values/colors.xml" <<XML
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- The ground the Zepp OS icon is drawn on, moved into the adaptive icon's
         background layer so the launcher's own mask has something to cut instead
         of clipping a disc that is already part of the picture. -->
    <color name="ic_launcher_background">$background</color>
</resources>
XML

echo "Wrote launcher icons into $res"
