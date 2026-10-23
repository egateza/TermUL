#!/usr/bin/env bash
#
# Build, tandatangani, dan terbitkan rilis TermUL ke GitHub Releases dari macOS (padanan tools/release.ps1).
#
#   tools/release-mac.sh --notes docs/release-notes/v0.2.md [--draft]   rilis penuh dari Mac
#   tools/release-mac.sh --dmg-only                                       lampirkan DMG ke rilis/draft yang sudah ada
#   tambahkan --dry-run untuk build dan cek semuanya tanpa mengunggah apa pun ke GitHub
#
# Rilis penuh: jar + manifest.json + manifest.json.sig (Ed25519, update lewat menu untuk Windows DAN Mac, karena jar-nya
# sama di semua OS), zip JAR portable, dan DMG macOS. Zip app-image / .msi Windows dibuat di PC Windows dengan
# tools/release.ps1 -AssetsOnly (jpackage tidak bisa cross-build). Alur lengkap: docs/RELEASE-CROSS-PLATFORM.md.
#
# Butuh: JDK 25 (java di PATH atau JAVA_HOME), GitHub CLI (gh auth login, akses tulis ke repo), dan untuk rilis penuh
# private key di ~/.termul-release/update-signing.key (atau env TERMUL_UPDATE_KEY), mode 600. Lihat docs/RELEASE.md.

set -euo pipefail

NOTES=""
DRAFT=0
DMG_ONLY=0
DRY_RUN=0
ALLOW_DIRTY=0

fail() {
    printf '\033[31mGAGAL: %s\033[0m\n' "$1" >&2
    exit 1
}

info() {
    printf '\033[36m==> %s\033[0m\n' "$1"
}

usage() {
    sed -n '3,7p' "$0" | sed 's/^# \{0,1\}//'
    exit "${1:-0}"
}

while [ $# -gt 0 ]; do
    case "$1" in
        --notes) [ $# -ge 2 ] || fail "--notes butuh nama file"; NOTES="$2"; shift 2 ;;
        --draft) DRAFT=1; shift ;;
        --dmg-only) DMG_ONLY=1; shift ;;
        --dry-run) DRY_RUN=1; shift ;;
        --allow-dirty) ALLOW_DIRTY=1; shift ;;
        -h|--help) usage 0 ;;
        *) printf 'Argumen tidak dikenal: %s\n\n' "$1" >&2; usage 1 ;;
    esac
done

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

[ "$(uname -s)" = "Darwin" ] || fail "Skrip ini untuk macOS. Di Windows pakai tools/release.ps1."
if [ "$DMG_ONLY" -eq 0 ]; then
    [ -n "$NOTES" ] || fail "--notes FILE wajib untuk rilis penuh (atau --dmg-only)"
    [ -f "$NOTES" ] || fail "File catatan rilis tidak ada: $NOTES"
    NOTES="$(cd "$(dirname "$NOTES")" && pwd)/$(basename "$NOTES")"
fi

# --- prasyarat -------------------------------------------------------------------------------------------------------
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
"$JAVA" -version 2>&1 | head -1 | grep -q '"25' || fail "Butuh JDK 25 ($JAVA -version)"
command -v gh >/dev/null || fail "GitHub CLI (gh) belum terpasang: brew install gh, lalu gh auth login"
if [ "$DRY_RUN" -eq 0 ]; then
    gh auth status >/dev/null 2>&1 || fail "gh belum login: gh auth login"
    [ "$(gh api repos/:owner/:repo --jq .permissions.push 2>/dev/null)" = "true" ] \
        || fail "Akun gh tidak punya akses tulis ke repo ini (minta ditambahkan sebagai collaborator)"
fi

if [ "$DMG_ONLY" -eq 0 ]; then
    KEY="${TERMUL_UPDATE_KEY:-$HOME/.termul-release/update-signing.key}"
    [ -f "$KEY" ] || fail "Private key tidak ada: $KEY (salin dari mesin rilis lewat jalur aman, atau set TERMUL_UPDATE_KEY)"
    MODE="$(stat -f '%Lp' "$KEY")"
    case "$MODE" in
        600|400) ;;
        *) fail "Private key bisa dibaca user lain (mode $MODE). Jalankan: chmod 600 \"$KEY\"" ;;
    esac
fi

# Versi = jumlah commit: rilis harus dari commit yang bersih dan sudah di-push
if [ -n "$(git status --porcelain --untracked-files=no)" ] && [ "$ALLOW_DIRTY" -eq 0 ]; then
    fail "Working tree belum bersih. Commit dulu (atau --allow-dirty untuk uji)."
fi
COMMIT="$(git rev-parse HEAD)"
git fetch -q origin || fail "git fetch gagal"
[ -n "$(git branch -r --contains "$COMMIT")" ] || fail "Commit $COMMIT belum ada di remote. git push dulu."
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
if [ "$BRANCH" != "master" ]; then
    printf '\033[33mPeringatan: rilis dari branch %s, bukan master. Versi (jumlah commit) bisa lebih kecil dari rilis sebelumnya dan ditolak sebagai downgrade.\033[0m\n' "$BRANCH"
fi

# --- build -----------------------------------------------------------------------------------------------------------
DIST="$ROOT/app/target/dist"
rm -f "$DIST"/*.dmg "$DIST"/*-jar.zip
info "Build (jar, zip JAR portable, DMG)"
PROFILES="package-win,package-jar"
[ "$DMG_ONLY" -eq 1 ] && PROFILES="package-win"
sh ./mvnw -q "-P$PROFILES" -DskipTests -Djpackage.type=dmg package || fail "Build Maven gagal"

INPUT="$ROOT/app/target/jpackage-input"
MAIN_JAR="$(ls "$INPUT"/termul-app-*.jar)"
VERSION="$(unzip -p "$MAIN_JAR" dev/egateza/termul/app/build.properties | sed -n 's/^version=//p' | tr -d '\r')"
case "$VERSION" in
    [0-9]*.[0-9]*.[0-9]*) ;;
    *) fail "Versi build tidak valid: '$VERSION'" ;;
esac
TAG="v$VERSION"

BUILT_DMG="$(ls "$DIST"/*.dmg 2>/dev/null | head -1)"
[ -n "$BUILT_DMG" ] || fail "DMG tidak terbentuk di $DIST"
DMG="$DIST/TermUL-$VERSION-macos.dmg"
mv "$BUILT_DMG" "$DMG"

# --- hanya DMG: lampirkan ke rilis yang sudah ada ---------------------------------------------------------------------
if [ "$DMG_ONLY" -eq 1 ]; then
    TARGET="$(gh release view "$TAG" --json targetCommitish --jq .targetCommitish 2>/dev/null)" \
        || fail "Rilis $TAG (juga draft) belum ada. Terbitkan dulu (Windows: tools/release.ps1 -Draft, atau skrip ini tanpa --dmg-only)."
    # Nomor versi = jumlah commit, jadi commit lain (mis. di branch lain) bisa bernomor sama: DMG harus dari commit rilis
    if [[ "$TARGET" =~ ^[0-9a-f]{40}$ ]]; then
        EXPECTED="$TARGET"
    else
        git fetch -q --tags origin
        EXPECTED="$(git rev-parse --verify -q "$TAG^{commit}" || true)"
    fi
    [ -n "$EXPECTED" ] || fail "Commit rilis $TAG tidak bisa ditentukan (target: $TARGET)"
    [ "$EXPECTED" = "$COMMIT" ] || fail "Rilis $TAG dibuat dari commit ${EXPECTED:0:7}, sedangkan HEAD ${COMMIT:0:7}. Jalankan: git checkout ${EXPECTED:0:7}"
    if [ "$DRY_RUN" -eq 1 ]; then
        info "Dry run: akan mengunggah $(basename "$DMG") ke $TAG (commit ${COMMIT:0:7} cocok)"
        exit 0
    fi
    info "Mengunggah $(basename "$DMG") ke $TAG"
    gh release upload "$TAG" "$DMG" --clobber || fail "gh release upload gagal"
    printf '\033[32mSelesai: DMG terlampir di %s\033[0m\n' "$TAG"
    exit 0
fi

# --- rilis penuh -----------------------------------------------------------------------------------------------------
if [ "$DRY_RUN" -eq 0 ] && gh release view "$TAG" >/dev/null 2>&1; then
    fail "Rilis $TAG sudah ada. Untuk menambahkan DMG saja: tools/release-mac.sh --dmg-only"
fi

OUT="$ROOT/app/target/release"
rm -rf "$OUT"
info "Membuat dan menandatangani manifest $TAG"
TOOL_ARGS=(publish --input "$INPUT" --out "$OUT" --notes "$NOTES" --key "$KEY")
[ "$ALLOW_DIRTY" -eq 1 ] && TOOL_ARGS+=(--allow-dirty)
"$JAVA" -cp "$INPUT/libs/*" dev.egateza.termul.update.release.ReleaseTool "${TOOL_ARGS[@]}" || fail "ReleaseTool gagal"

# Generation naik = pengguna butuh installer baru, dan installer Windows tidak bisa dibuat di Mac
generation() {
    grep -o '"generation"[[:space:]]*:[[:space:]]*[0-9]*' | head -1 | grep -o '[0-9]*$'  # field sebelum notes
}
NEW_GEN="$(generation < "$OUT/manifest.json")"
LATEST_TAG="$(gh release view --json tagName --jq .tagName 2>/dev/null || true)"
if [ -n "$LATEST_TAG" ]; then
    OLD_GEN="$(gh release download "$LATEST_TAG" -p manifest.json -O - 2>/dev/null | generation || true)"
    if [ -n "$OLD_GEN" ] && [ -n "$NEW_GEN" ] && [ "$NEW_GEN" -gt "$OLD_GEN" ]; then
        fail "Generation naik ($OLD_GEN -> $NEW_GEN): pengguna butuh installer baru, dan zip/msi Windows hanya bisa dibuat di Windows. Terbitkan rilis ini dengan tools/release.ps1."
    fi
fi

ASSETS=()
for f in "$OUT"/*; do
    ASSETS+=("$f")
done
JAR_ZIP="$DIST/TermUL-$VERSION-jar.zip"
[ -f "$JAR_ZIP" ] && ASSETS+=("$JAR_ZIP")
ASSETS+=("$DMG")

if [ "$DRY_RUN" -eq 1 ]; then
    info "Dry run: akan menerbitkan $TAG (${#ASSETS[@]} aset) dari commit $COMMIT"
    for f in "${ASSETS[@]}"; do
        echo "  $(basename "$f")"
    done
    exit 0
fi

info "Menerbitkan $TAG (${#ASSETS[@]} aset) dari commit $COMMIT"
GH_ARGS=(release create "$TAG" "${ASSETS[@]}" --title "TermUL $VERSION" --notes-file "$NOTES" --target "$COMMIT")
[ "$DRAFT" -eq 1 ] && GH_ARGS+=(--draft)
gh "${GH_ARGS[@]}" || fail "gh release create gagal"
printf '\033[32mSelesai: %s\033[0m\n' "$TAG"
echo "Zip Windows untuk rilis yang sama: di PC Windows, git checkout ${COMMIT:0:7} lalu .\\tools\\release.ps1 -AssetsOnly"
echo "Pengguna Windows yang sudah terpasang tetap bisa update lewat menu tanpa zip itu."
