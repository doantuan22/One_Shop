#!/usr/bin/env bash
set -euo pipefail
bucket="${1:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
jar_key="${2:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
version="${3:?usage: deploy-release.sh BUCKET JAR_KEY VERSION}"
[[ "$version" =~ ^[0-9a-f]{40}$ ]] || { echo "Invalid commit SHA" >&2; exit 2; }
[[ "$jar_key" == "artifacts/oneshop-${version}.jar" ]] || { echo "Unexpected artifact key" >&2; exit 2; }
umask 077
tmp_env="$(mktemp /etc/oneshop/oneshop.env.XXXXXX)"
link_tmp="/opt/oneshop/.oneshop.jar.new.$$"
rollback_link="/opt/oneshop/.oneshop.jar.rollback.$$"
cleanup() {
  rm -f -- "$tmp_env" "$link_tmp" "$rollback_link"
}
trap cleanup EXIT
aws ssm get-parameter --name /oneshop/demo/app-env --with-decryption --query Parameter.Value --output text > "$tmp_env"
# The JDBC URL pins the RDS listener certificate hostname. Drop legacy environment overrides,
# while retaining the truststore settings used to validate the RDS certificate chain.
if grep -q '^DB_JDBC_OPTIONS=.*hostNameInCertificate=' "$tmp_env"; then
  sed -i -E '/^DB_JDBC_OPTIONS=/ { s/;hostNameInCertificate=[^;]*//g; s/^DB_JDBC_OPTIONS=hostNameInCertificate=[^;]*;?/DB_JDBC_OPTIONS=/; s/;;+/;/g; s/^DB_JDBC_OPTIONS=([^;].*)$/DB_JDBC_OPTIONS=;\1/; }' "$tmp_env"
fi
grep -q '^DB_HOST=' "$tmp_env"
grep -q '^DB_USERNAME=' "$tmp_env"
grep -q '^DB_PASSWORD=' "$tmp_env"
grep -q '^JWT_SECRET=' "$tmp_env"
chown root:oneshop "$tmp_env"
chmod 0640 "$tmp_env"
mv -f -- "$tmp_env" /etc/oneshop/oneshop.env
release="/opt/oneshop/releases/${version}.jar"
aws s3 cp "s3://${bucket}/${jar_key}" "$release" --only-show-errors
chown oneshop:oneshop "$release"
chmod 0640 "$release"
[[ -s "$release" ]] || { echo "Downloaded release is empty." >&2; exit 1; }
current_jar="/opt/oneshop/oneshop.jar"
previous=""
if [[ -L "$current_jar" ]]; then  
candidate="$(readlink -e -- "$current_jar" 2>/dev/null || true)"  
if [[ -n "$candidate" && "$candidate" != "$current_jar" && -s "$candidate" ]]; then    
previous="$candidate"  
fi
fi
ln -s -- "$release" "$link_tmp"
mv -Tf -- "$link_tmp" "$current_jar"
active="$(readlink -e -- "$current_jar" 2>/dev/null || true)"
[[ "$active" == "$release" && -s "$current_jar" ]] || {
  echo "Active JAR link does not resolve to the downloaded release." >&2  
exit 1
}
systemctl restart oneshop
for attempt in $(seq 1 45); do  
if curl --connect-timeout 2 --max-time 5 --fail --silent http://127.0.0.1:8080/health >/dev/null; then
    find /opt/oneshop/releases -type f -name '*.jar' -mtime +7 -delete
    echo "OneShop ${version} is healthy."    
exit 0  
fi
  sleep 2
done
if [[ -n "$previous" && "$previous" != "$release" && -s "$previous" ]]; then
  ln -s -- "$previous" "$rollback_link"
  mv -Tf -- "$rollback_link" "$current_jar"
  systemctl restart oneshop || true
else
  systemctl stop oneshop || true
