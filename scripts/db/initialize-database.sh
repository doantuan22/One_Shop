#!/usr/bin/env bash
# Creates a NEW OneShop database on SQL Server: schema + reference data + (optional) first admin or demo data.
# Linux/macOS counterpart of Initialize-OneShopDatabase.ps1. Needs sqlcmd (mssql-tools18) on PATH.
#
#   export SQLCMDPASSWORD='...'          # never on the command line
#   scripts/db/initialize-database.sh <server> <user> <database> empty
#   scripts/db/initialize-database.sh <server> <user> <database> production <admin-email> <admin-username> "<full name>" '<bcrypt-hash>'
#   scripts/db/initialize-database.sh <server> <user> <database> demo '<bcrypt-hash>'
#   scripts/db/initialize-database.sh <server> <user> <database> test     # automated-test fixture; database name must be oneshop*_test
# Set SQLCMD_EXTRA=-C only if the server certificate cannot be validated from this machine. DB_PORT overrides 1433.
set -euo pipefail
[ $# -ge 4 ] || { sed -n '2,10p' "$0"; exit 2; }
SERVER=$1; USER_=$2; DB=$3; MODE=$4; shift 4
: "${SQLCMDPASSWORD:?Set SQLCMDPASSWORD first}"
[[ "$DB" =~ ^[A-Za-z0-9_]+$ ]] || { echo "Database name: letters, digits, underscore only" >&2; exit 2; }
cd "$(dirname "$0")/../.."   # repository root: sqlcmd gets relative paths (also works with Git Bash + Windows sqlcmd)
run() { sqlcmd -S "${SERVER},${DB_PORT:-1433}" -U "$USER_" -b -f 65001 ${SQLCMD_EXTRA:-} "$@"; }

echo "1/3 schema -> $DB";  run -v DatabaseName="$DB" -i "database/schema/01_schema.sql"
if [ "$MODE" != test ]; then echo "2/3 reference data"; run -d "$DB" -i "database/seed/01_reference_data.sql"; fi
case "$MODE" in
  production) [ $# -eq 4 ] || { echo "production needs: <admin-email> <admin-username> <full name> <bcrypt-hash>" >&2; exit 2; }
    echo "3/3 first administrator"
    # values with spaces do not survive sqlcmd -v on Windows: sqlcmd also reads $(NAME) from the environment
    AdminEmail="$1" AdminUserName="$2" AdminFullName="$3" AdminPasswordHash="$4" run -d "$DB" -i "database/seed/02_bootstrap_admin.sql" ;;
  demo) [ $# -eq 1 ] || { echo "demo needs: <bcrypt-hash>" >&2; exit 2; }
    echo "3/3 demo data"
    run -d "$DB" -v SeedPasswordHash="$1" -i "database/seed/20_demo_data.sql"
    run -d "$DB" -i "database/seed/99_validate_demo_data.sql" ;;
  test) [[ "$DB" =~ ^oneshop[A-Za-z0-9_]*_test$ ]] || { echo "test mode needs a database named oneshop*_test" >&2; exit 2; }
    echo "2/2 test fixture (6 branches, 8 sample orders, roles, accounts)"; run -v DatabaseName="$DB" -i "database/test/test_fixture_seed.sql" ;;
  empty) echo "3/3 nothing more" ;;
  *) echo "mode must be empty|production|demo|test" >&2; exit 2 ;;
esac
echo Done.
