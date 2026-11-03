#!/usr/bin/env sh
set -eu
cd "$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
docker info >/dev/null
secret() { od -An -N32 -tx1 /dev/urandom | tr -d ' \n'; }
if [ ! -f .env.demo ]; then
  umask 077
  printf '%s\n' 'DB_NAME=bishe' 'DB_USERNAME=bishe_app' "DB_PASSWORD=$(secret)" "MYSQL_ROOT_PASSWORD=$(secret)" "REDIS_PASSWORD=$(secret)" "TOKEN_SECRET=$(secret)" 'FRONTEND_PORT=8088' 'CORS_ALLOWED_ORIGINS=http://localhost:8088' 'CAPTCHA_EXPOSE_CODE=false' 'SWAGGER_ENABLED=false' > .env.demo
fi
docker compose --env-file .env.demo --project-name bishe-demo up -d --build --wait
printf '%s\n' 'Open http://localhost:8088 and register your own accounts. No local MySQL data is imported.'
