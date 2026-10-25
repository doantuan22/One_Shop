#!/usr/bin/env bash
set -euo pipefail

bucket="${1:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
jar_key="${2:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
version="${3:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
[[ "$version" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit SHA" >&2; exit 2; }
[[ "$jar_key" == "artifacts/oneshop-${version}.jar" ]] || { echo "Unexpected artifact key" >&2; exit 2; }

umask 077
tmp_env="$(mktemp /etc/oneshop/oneshop.env.XXXXXX)"
trap 'rm -f "$tmp_env"' EXIT
aws ssm get-parameter --name /oneshop/demo/app-env --with-decryption --query Parameter.Value --output text > "$tmp_env"
grep -q '^DB_HOST=' "$tmp_env"
grep -q '^DB_USERNAME=' "$tmp_env"
grep -q '^DB_PASSWORD=' "$tmp_env"
grep -q '^JWT_SECRET=' "$tmp_env"
chown root:oneshop "$tmp_env"
chmod 0640 "$tmp_env"
mv -f "$tmp_env" /etc/oneshop/oneshop.env
trap - EXIT

release="/opt/oneshop/releases/${version}.jar"
aws s3 cp "s3://${bucket}/${jar_key}" "$release" --only-show-errors
chown oneshop:oneshop "$release"
chmod 0640 "$release"
previous="$(readlink -f /opt/oneshop/oneshop.jar 2>/dev/null || true)"
ln -sfn "$release" /opt/oneshop/oneshop.jar.new
mv -Tf /opt/oneshop/oneshop.jar.new /opt/oneshop/oneshop.jar
systemctl restart oneshop
for attempt in $(seq 1 45); do
  if curl --fail --silent --show-error http://127.0.0.1:8080/health >/dev/null; then
    find /opt/oneshop/releases -type f -name '*.jar' -mtime +7 -delete
    echo "OneShop ${version} is healthy."
    exit 0
  fi
  sleep 2
done
if [[ -n "$previous" && -s "$previous" ]]; then
  ln -sfn "$previous" /opt/oneshop/oneshop.jar.new
  mv -Tf /opt/oneshop/oneshop.jar.new /opt/oneshop/oneshop.jar
  systemctl restart oneshop || true
else
  systemctl stop oneshop || true
fi
echo "Health check failed; previous release restored when one was available." >&2
exit 1
