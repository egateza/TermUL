#!/bin/sh
# Launcher TermUL untuk Linux (atau macOS dari terminal). Butuh JDK 25+; pakai $JAVA_HOME kalau di-set.
DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
    JAVA="$JAVA_HOME/bin/java"
else
    JAVA=java
fi
exec "$JAVA" --enable-native-access=ALL-UNNAMED -jar "$DIR/TermUL.jar" "$@"
