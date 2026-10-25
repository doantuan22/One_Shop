#!/usr/bin/env bash
# Prints the BCrypt hash the application accepts. Needs target/oneshop.jar and Java 21.
# Password: $ONESHOP_PASSWORD, else typed at the prompt (not echoed). Never printed.
set -euo pipefail
JAR="$(cd "$(dirname "$0")/../.." && pwd)/target/oneshop.jar"
[ -f "$JAR" ] || { echo "Build first: ./mvnw -DskipTests package" >&2; exit 1; }
if [ -z "${ONESHOP_PASSWORD:-}" ]; then read -r -s -p "Password (8-72 characters): " ONESHOP_PASSWORD; echo >&2; export ONESHOP_PASSWORD; fi
exec java -Dloader.main=com.oneshop.tools.PasswordHashTool -cp "$JAR" org.springframework.boot.loader.launch.PropertiesLauncher
