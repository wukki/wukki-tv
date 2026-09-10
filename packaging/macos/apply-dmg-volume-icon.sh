#!/usr/bin/env bash

set -euo pipefail

usage() {
  echo "Usage: $0 <dmg> <volume-icon.icns> [--signing-identity <identity>] [--keychain <path>]" >&2
  exit 2
}

[[ $# -ge 2 ]] || usage

dmg_path="$1"
icon_path="$2"
shift 2

signing_identity=""
signing_keychain=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --signing-identity)
      [[ $# -ge 2 ]] || usage
      signing_identity="$2"
      shift 2
      ;;
    --keychain)
      [[ $# -ge 2 ]] || usage
      signing_keychain="$2"
      shift 2
      ;;
    *)
      usage
      ;;
  esac
done

[[ -f "$dmg_path" ]] || { echo "DMG does not exist: $dmg_path" >&2; exit 1; }
[[ -f "$icon_path" ]] || { echo "Volume icon does not exist: $icon_path" >&2; exit 1; }

for command in hdiutil iconutil SetFile GetFileInfo; do
  command -v "$command" >/dev/null || { echo "Required macOS tool is missing: $command" >&2; exit 1; }
done

dmg_path="$(cd "$(dirname "$dmg_path")" && pwd)/$(basename "$dmg_path")"
icon_path="$(cd "$(dirname "$icon_path")" && pwd)/$(basename "$icon_path")"
workspace="$(mktemp -d "$(dirname "$dmg_path")/.wukki-dmg-icon.XXXXXX")"
mount_path="$workspace/mount"
writable_base="$workspace/writable"
final_base="$workspace/final"
mounted=false

cleanup() {
  if [[ "$mounted" == true ]]; then
    hdiutil detach "$mount_path" -force >/dev/null 2>&1 || true
  fi
  rm -rf "$workspace"
}
trap cleanup EXIT INT TERM

# iconutil rejects malformed ICNS files and validates all embedded representations.
iconutil -c iconset "$icon_path" -o "$workspace/validated.iconset"

hdiutil convert "$dmg_path" -quiet -format UDRW -o "$writable_base"
mkdir -p "$mount_path"
hdiutil attach "$writable_base.dmg" -quiet -nobrowse -noverify -owners off -mountpoint "$mount_path"
mounted=true

if [[ -e "$mount_path/.VolumeIcon.icns" ]]; then
  chmod u+w "$mount_path/.VolumeIcon.icns"
fi
cp "$icon_path" "$mount_path/.VolumeIcon.icns"
SetFile -a V "$mount_path/.VolumeIcon.icns"
SetFile -a C "$mount_path"
sync

hdiutil detach "$mount_path" -quiet
mounted=false

hdiutil convert "$writable_base.dmg" -quiet -format UDZO -imagekey zlib-level=9 -o "$final_base"

verification_mount="$workspace/verification-mount"
mkdir -p "$verification_mount"
hdiutil attach "$final_base.dmg" -quiet -readonly -nobrowse -noverify -mountpoint "$verification_mount"
mount_path="$verification_mount"
mounted=true

[[ -f "$verification_mount/.VolumeIcon.icns" ]] || { echo "The generated DMG has no volume icon" >&2; exit 1; }
cmp -s "$icon_path" "$verification_mount/.VolumeIcon.icns" || { echo "The embedded volume icon differs from the source icon" >&2; exit 1; }
volume_attributes="$(GetFileInfo -a "$verification_mount")"
[[ "$volume_attributes" == *C* ]] || { echo "The generated DMG has no Finder custom-icon attribute" >&2; exit 1; }

hdiutil detach "$verification_mount" -quiet
mounted=false

if [[ -n "$signing_identity" ]]; then
  codesign_args=(--force --timestamp --sign "$signing_identity")
  if [[ -n "$signing_keychain" ]]; then
    codesign_args+=(--keychain "$signing_keychain")
  fi
  codesign "${codesign_args[@]}" "$final_base.dmg"
  codesign --verify --strict --verbose=2 "$final_base.dmg"
fi

hdiutil verify "$final_base.dmg" >/dev/null
mv -f "$final_base.dmg" "$dmg_path"

echo "Applied Wukki TV volume icon to: $dmg_path"
