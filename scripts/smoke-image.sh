#!/usr/bin/env bash
# Prueba de humo de la imagen: MySQL temporal, arranque con Flyway, salud, login de cuenta ficticia,
# ruta protegida con y sin credencial y reinicio conservando el esquema. Limpia los contenedores al final.
set -euo pipefail

IMAGE="${IMAGE:-backend-uni:smoke}"
MYSQL_IMAGE="${MYSQL_IMAGE:-mysql:8.4.13}"
PORT="${PORT:-18080}"
SUFFIX="${SUFFIX:-$$}"
NET="backend-smoke-$SUFFIX"
DB="backend-smoke-mysql-$SUFFIX"
APP="backend-smoke-app-$SUFFIX"
BASE="http://127.0.0.1:$PORT"

cleanup() {
  docker rm -f "$APP" "$DB" >/dev/null 2>&1 || true
  docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

fail() {
  echo "ERROR: $1" >&2
  echo "---- logs del backend ----" >&2
  docker logs "$APP" 2>&1 | tail -n 120 >&2 || true
  exit 1
}

# Código HTTP sin escribir el cuerpo en disco (portable también a curl.exe de Windows).
http_status() {
  local url="$1"
  shift
  curl --silent --write-out '\n%{http_code}' "$@" "$url" | tail -n 1
}

wait_health() {
  for _ in $(seq 1 90); do
    if curl --silent --fail "$BASE/actuator/health" | grep -q '"UP"'; then
      return 0
    fi
    sleep 2
  done
  fail "el backend no reportó salud UP en 180 s"
}

if [ "${SKIP_BUILD:-0}" != "1" ]; then
  docker build --tag "$IMAGE" .
fi

docker network create "$NET" >/dev/null
docker run --detach --name "$DB" --network "$NET" \
  -e MYSQL_DATABASE=uni_services -e MYSQL_USER=uni_app -e MYSQL_PASSWORD=smoke_app_pw \
  -e MYSQL_ROOT_PASSWORD=smoke_root_pw "$MYSQL_IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if docker exec "$DB" mysql --protocol=TCP -h127.0.0.1 -uuni_app -psmoke_app_pw -e "SELECT 1" uni_services >/dev/null 2>&1; then
    break
  fi
  sleep 2
done

start_app() {
  docker run --detach --name "$APP" --network "$NET" -p "127.0.0.1:$PORT:8080" \
    -e "SPRING_DATASOURCE_URL=jdbc:mysql://$DB:3306/uni_services?allowPublicKeyRetrieval=true&sslMode=DISABLED" \
    -e SPRING_DATASOURCE_USERNAME=uni_app -e SPRING_DATASOURCE_PASSWORD=smoke_app_pw \
    -e APP_JWT_SECRET=smoke-secret-solo-para-pruebas-de-imagen-2026 \
    -e APP_FLYWAY_LOCATIONS=classpath:db/migration,classpath:db/demo \
    "$IMAGE" >/dev/null
}

start_app
wait_health
echo "OK salud"

status=$(http_status "$BASE/api/v1/admin/users")
[ "$status" = "401" ] || fail "la ruta protegida sin credencial devolvió $status"
echo "OK ruta protegida sin credencial -> 401"

login=$(curl --silent --fail -H 'Content-Type: application/json' \
  -d '{"correo":"admin.demo@universidad.example","contrasena":"Demo2026!"}' "$BASE/api/v1/auth/login") \
  || fail "el login de la cuenta ficticia falló"
token=$(printf '%s' "$login" | sed -n 's/.*"tokenAcceso":"\([^"]*\)".*/\1/p')
[ -n "$token" ] || fail "la respuesta de login no trae tokenAcceso"
echo "OK login de cuenta ficticia"

status=$(http_status "$BASE/api/v1/admin/users" -H "Authorization: Bearer $token")
[ "$status" = "200" ] || fail "la ruta protegida con credencial devolvió $status"
echo "OK ruta protegida con credencial -> 200"

status=$(http_status "$BASE/api/v1/auth/register" -H 'Content-Type: application/json' \
  -d '{"nombreCompleto":"Humo Demo","correo":"humo.demo@unmsm.edu.pe","contrasena":"Humo2026x","confirmacionContrasena":"Humo2026x"}')
[ "$status" = "201" ] || fail "el auto-registro devolvió $status"
pending=$(curl --silent -H 'Content-Type: application/json' \
  -d '{"correo":"humo.demo@unmsm.edu.pe","contrasena":"Humo2026x"}' "$BASE/api/v1/auth/login")
printf '%s' "$pending" | grep -q '"CUENTA_PENDIENTE"' || fail "el login de la cuenta pendiente no devolvió CUENTA_PENDIENTE"
echo "OK auto-registro pendiente de aprobación"

migrations=$(docker exec "$DB" mysql -N -uuni_app -psmoke_app_pw uni_services \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1" 2>/dev/null)
docker rm -f "$APP" >/dev/null
start_app
wait_health
again=$(docker exec "$DB" mysql -N -uuni_app -psmoke_app_pw uni_services \
  -e "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1" 2>/dev/null)
[ "$migrations" = "$again" ] || fail "el segundo arranque aplicó migraciones nuevas ($migrations -> $again)"
echo "OK segundo arranque conserva el esquema ($again migraciones)"

if docker logs "$APP" 2>&1 | grep -E -q 'Demo2026!|Humo2026x|tokenAcceso|smoke_app_pw'; then
  fail "los logs contienen credenciales"
fi
echo "OK logs sin credenciales"
echo "Prueba de humo superada"
