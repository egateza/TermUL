#!/bin/bash
# Launcher TermUL untuk macOS (double-click di Finder). Butuh JDK 25+ terpasang, mis. Temurin 25:
#   brew install --cask temurin@25    atau unduh dari https://adoptium.net
set -u
DIR="$(cd "$(dirname "$0")" && pwd)"

fail() {
    /usr/bin/osascript -e "display dialog \"$1\" with title \"TermUL\" buttons {\"OK\"} with icon stop" >/dev/null 2>&1
    echo "TermUL: $1" >&2
    exit 1
}

java_major() {
    "$1" -XshowSettings:properties -version 2>&1 | awk -F'= ' '/java.specification.version/ {print $2; exit}'
}

find_java() {
    local candidates=()
    [ -n "${JAVA_HOME:-}" ] && candidates+=("$JAVA_HOME/bin/java")
    if [ -x /usr/libexec/java_home ]; then
        local home
        home="$(/usr/libexec/java_home -v 25+ 2>/dev/null)" && candidates+=("$home/bin/java")
    fi
    command -v java >/dev/null 2>&1 && candidates+=("$(command -v java)")
    [ "${#candidates[@]}" -eq 0 ] && return 1 # bash 3.2 bawaan macOS: array kosong + set -u = error
    for c in "${candidates[@]}"; do
        if [ -x "$c" ]; then
            local v
            v="$(java_major "$c")"
            if [ -n "$v" ] && [ "${v%%.*}" -ge 25 ] 2>/dev/null; then
                echo "$c"
                return 0
            fi
        fi
    done
    return 1
}

JAVA="$(find_java)" || fail "Java 25 belum terpasang. Pasang JDK 25 (mis. Temurin 25 dari adoptium.net), lalu buka TermUL lagi."

# Dijalankan terpisah dari Terminal: window Terminal ini boleh ditutup setelah TermUL terbuka.
nohup "$JAVA" \
    --enable-native-access=ALL-UNNAMED \
    -Xdock:name=TermUL \
    -Xdock:icon="$DIR/termul.png" \
    -Dapple.awt.application.name=TermUL \
    -jar "$DIR/TermUL.jar" "$@" >/dev/null 2>&1 &

echo "TermUL dijalankan. Window Terminal ini boleh ditutup."
