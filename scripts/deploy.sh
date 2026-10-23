#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-framework-net}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
DEFAULT_HTTP_PORT="${HTTP_PORT:-18080}"
SHOW_ADMIN_KEY="${SHOW_ADMIN_KEY:-0}"

for arg in "$@"; do
  case "$arg" in
    --show-admin-key)
      SHOW_ADMIN_KEY=1
      ;;
    *)
      echo "Uso: $0 [--show-admin-key]" >&2
      exit 2
      ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Comando obrigatório não encontrado: $1" >&2
    exit 1
  fi
}

get_env_value() {
  local key="$1"
  grep -E "^${key}=" "$ENV_FILE" | tail -n 1 | cut -d= -f2-
}

replace_or_append_env() {
  local key="$1"
  local value="$2"

  if grep -qE "^${key}=" "$ENV_FILE"; then
    sed -i "s|^${key}=.*|${key}=${value}|" "$ENV_FILE"
  else
    printf '%s=%s\n' "$key" "$value" >> "$ENV_FILE"
  fi
}

ensure_env_file() {
  local created=0

  if [ ! -f "$ENV_FILE" ]; then
    cat > "$ENV_FILE" <<EOF
HTTP_PORT=${DEFAULT_HTTP_PORT}
ADMIN_API_KEY=$(openssl rand -hex 32)
CSRF_SECRET=$(openssl rand -hex 32)
EOF
    chmod 600 "$ENV_FILE" || true
    created=1
  fi

  if ! grep -qE '^HTTP_PORT=' "$ENV_FILE"; then
    replace_or_append_env "HTTP_PORT" "$DEFAULT_HTTP_PORT"
  fi

  if ! grep -qE '^ADMIN_API_KEY=' "$ENV_FILE" || grep -qE '^ADMIN_API_KEY=troque-por-um-valor' "$ENV_FILE"; then
    replace_or_append_env "ADMIN_API_KEY" "$(openssl rand -hex 32)"
    created=1
  fi

  if ! grep -qE '^CSRF_SECRET=' "$ENV_FILE" || grep -qE '^CSRF_SECRET=troque-por-um-valor' "$ENV_FILE"; then
    replace_or_append_env "CSRF_SECRET" "$(openssl rand -hex 32)"
    created=1
  fi

  if [ "$created" -eq 1 ]; then
    SHOW_ADMIN_KEY=1
  fi
}

compose() {
  docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"
}

wait_for_health() {
  local container_id
  local status
  local deadline

  container_id="$(compose ps -q "$COMPOSE_SERVICE")"
  if [ -z "$container_id" ]; then
    echo "Container do serviço $COMPOSE_SERVICE não encontrado." >&2
    compose ps
    exit 1
  fi

  deadline=$((SECONDS + 150))
  while [ "$SECONDS" -lt "$deadline" ]; do
    status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id" 2>/dev/null || true)"

    case "$status" in
      healthy|running)
        echo "Healthcheck OK: $status"
        return 0
        ;;
      unhealthy|exited|dead)
        echo "Container ficou em estado $status. Logs recentes:" >&2
        compose logs --tail=80 "$COMPOSE_SERVICE" >&2
        exit 1
        ;;
      *)
        sleep 3
        ;;
    esac
  done

  echo "Timeout esperando healthcheck. Estado atual: ${status:-desconhecido}" >&2
  compose ps >&2
  compose logs --tail=80 "$COMPOSE_SERVICE" >&2
  exit 1
}

require_command git
require_command docker
require_command openssl

cd "$APP_DIR"
ensure_env_file

echo "Atualizando código em $APP_DIR"
git pull origin main

echo "Subindo container com Docker Compose"
compose up -d --build
compose ps
wait_for_health

echo "Logs recentes:"
compose logs --tail=20 "$COMPOSE_SERVICE"

# robots.txt da borda (23/10/2026). Em producao o /robots.txt NAO vem do jar: o nginx do proxy o
# serve de /opt/infra-proxy/data/robots/<host>.txt (map $host). Sem este passo o arquivo do
# repositorio mudava e o da borda ficava velho. Roda so depois da aplicacao saudavel e nao derruba
# o deploy (a aplicacao esta certa): no fim sai 3 para o operador ver.
DOMINIO="${DOMINIO:-frameworknet.carminati.dev.br}"
ROBOTS_BORDA="${ROBOTS_BORDA:-/opt/infra-proxy/data/robots/${DOMINIO}.txt}"
borda_ok="sim"
if [ -d "$(dirname "$ROBOTS_BORDA")" ]; then
  if bash "$APP_DIR/scripts/gerar-robots-borda.sh" \
      "$APP_DIR/src/main/resources/META-INF/resources/robots.txt" "$ROBOTS_BORDA"; then
    servido=$(curl -s -m 8 "https://${DOMINIO}/robots.txt" | sha256sum | cut -d' ' -f1 || true)
    esperado=$(sha256sum "$ROBOTS_BORDA" | cut -d' ' -f1)
    if [ "$servido" = "$esperado" ]; then
      echo "robots.txt servido = gerado do repositorio (sha ${esperado:0:12})"
    else
      echo "!! robots.txt servido em https://${DOMINIO}/robots.txt difere do arquivo gerado."
      borda_ok=""
    fi
  else
    echo "!! a geracao do robots da borda reprovou; o arquivo anterior foi mantido."
    borda_ok=""
  fi
else
  echo "!! pasta da borda $(dirname "$ROBOTS_BORDA") ausente: robots.txt NAO VERIFICADO."
  borda_ok=""
fi

echo
echo "Deploy concluído."
echo "Porta local: 127.0.0.1:$(get_env_value HTTP_PORT)"
if [ "$SHOW_ADMIN_KEY" = "1" ]; then
  # A chave NÃO é impressa na saída padrão: o deploy costuma rodar em CI, em
  # tmux ou com log redirecionado, e o segredo ficaria gravado em texto claro
  # num histórico que ninguém rotaciona. Quem precisa dela lê do arquivo.
  echo "ADMIN_API_KEY gravada em $ENV_FILE (permissão 600)."
  echo "Para lê-la:  sudo grep '^ADMIN_API_KEY=' $ENV_FILE | cut -d= -f2-"
else
  echo "ADMIN_API_KEY já existe em $ENV_FILE. Use --show-admin-key para ver como obtê-la."
fi

if [ -z "$borda_ok" ]; then
  echo "Aplicação no ar, mas o robots.txt da borda NÃO confere (sem rollback: a aplicação está certa)."
  exit 3
fi
