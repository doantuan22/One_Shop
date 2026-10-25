#!/usr/bin/env bash
# Run as root on the EC2 instance through SSM after sqlcmd18 is installed and its EULA was accepted by the operator.
set -euo pipefail

repo_root=/opt/oneshop-source
command -v sqlcmd >/dev/null || { echo 'sqlcmd is not installed; follow the Microsoft installation step in docs/AWS_DEMO_RUNBOOK.md.' >&2; exit 2; }
if [[ ! -f "$repo_root/scripts/db/initialize-database.sh" ]]; then
  install -d -o root -g root -m 0755 "$repo_root"
  git clone --depth 1 --branch main https://github.com/doantuan22/One_Shop.git "$repo_root"
else
  git -C "$repo_root" pull --ff-only origin main
fi
get_parameter() {
  aws ssm get-parameter --name "$1" --with-decryption --query Parameter.Value --output text
}

endpoint="$(get_parameter /oneshop/demo/rds-endpoint)"
master_user="$(get_parameter /oneshop/demo/rds-master-username)"
master_password="$(get_parameter /oneshop/demo/rds-master-password)"
seed_hash="$(get_parameter /oneshop/demo/seed-password-hash)"
app_password="$(get_parameter /oneshop/demo/app-db-password)"
[[ "$seed_hash" == '$2a$'* || "$seed_hash" == '$2b$'* || "$seed_hash" == '$2y$'* ]] || { echo 'Seed password must be a BCrypt hash.' >&2; exit 2; }
[[ "$app_password" =~ ^[A-Za-z0-9]{24,128}$ ]] || { echo 'Application DB password must be 24+ alphanumeric characters.' >&2; exit 2; }

db_exists="$(SQLCMDPASSWORD="$master_password" sqlcmd -S "${endpoint},1433" -U "$master_user" -h -1 -W -b -Q "SET NOCOUNT ON; SELECT CASE WHEN DB_ID(N'oneshop') IS NULL THEN 0 ELSE 1 END;" | tr -d '[:space:]')"
if [[ "$db_exists" == 0 ]]; then
  # Create schema and reference rows once. If a later seed step fails, a rerun can continue
  # against the existing database without trying to recreate its schema.
  SQLCMDPASSWORD="$master_password" "$repo_root/scripts/db/initialize-database.sh" "$endpoint" "$master_user" oneshop empty
fi
# The current seed is transactional and recognizes its already-loaded exact catalog.
SQLCMDPASSWORD="$master_password" SeedPasswordHash="$seed_hash" sqlcmd -S "${endpoint},1433" -U "$master_user" -d oneshop -b -f 65001 -i "$repo_root/database/seed/20_demo_data.sql"
SQLCMDPASSWORD="$master_password" sqlcmd -S "${endpoint},1433" -U "$master_user" -d oneshop -b -f 65001 -i "$repo_root/database/seed/99_validate_demo_data.sql"
# sqlcmd reads this scripting variable from its environment; the app password is not in argv.
SQLCMDPASSWORD="$master_password" AppDbPassword="$app_password" sqlcmd -S "${endpoint},1433" -U "$master_user" -d oneshop -b -f 65001 -i "$repo_root/deploy/aws/create-app-login.sql"
unset SQLCMDPASSWORD AppDbPassword app_password seed_hash master_password
printf 'Database oneshop initialized with the current demo seed and least-privilege app login.\n'
