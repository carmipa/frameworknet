#!/usr/bin/env bash
#
# Gera o dataset público de telemetria a partir do que está rodando na VPS.
#
# PROPÓSITO DE NEGÓCIO
#     Executa o ciclo decidido em 2026-07-20: coletar -> excluir ruído técnico ->
#     sanitizar -> publicar. Este script cobre da coleta à sanitização e deixa o
#     dataset pronto numa pasta datada; a publicação no repositório é passo
#     manual, para ninguém publicar sem olhar.
#
# INVARIANTES DO DOMÍNIO
#     - Não altera a aplicação nem exige redeploy: lê o volume do container.
#     - O sal de pseudonimização vive no .env da VPS e NUNCA entra no dataset.
#       Trocar o sal quebra a continuidade dos pseudônimos entre datasets já
#       publicados, então o script gera uma única vez e depois só reutiliza.
#     - O arquivo de origem no container não é modificado nem rotacionado aqui.
#
# COMPORTAMENTO EM CASO DE FALHA
#     - python3, docker ou container ausentes: aborta com mensagem e código != 0.
#     - Auditoria do sanitizador reprovando: o dataset é apagado e o script
#       termina em erro, sem deixar arquivo publicável pela metade.
#
# USO
#     ./scripts/exportar-dataset.sh [--saida DIR]
#
set -euo pipefail

APP_DIR="${APP_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
COMPOSE_FILE="${COMPOSE_FILE:-docker-compose.yml}"
COMPOSE_SERVICE="${COMPOSE_SERVICE:-framework-net}"
ENV_FILE="${ENV_FILE:-$APP_DIR/.env}"
ARQUIVO_REMOTO="${ARQUIVO_REMOTO:-/deployments/data/logs/framework-net-eventos.jsonl}"
SAIDA_BASE="${SAIDA_BASE:-$APP_DIR/dataset}"

for arg in "$@"; do
  case "$arg" in
    --saida=*) SAIDA_BASE="${arg#*=}" ;;
    *) echo "Uso: $0 [--saida=DIR]" >&2; exit 2 ;;
  esac
done

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Comando obrigatório não encontrado: $1" >&2
    exit 1
  fi
}

require_command docker
require_command python3
require_command openssl

cd "$APP_DIR"

# ---- sal de pseudonimização --------------------------------------------------
# SUPERADO. O caminho vivo de publicação é o botão "Sincronizar dataset" no painel
# de Telemetria, que sanitiza com pseudônimo sequencial POR PACOTE e não usa sal
# nenhum. Este script continua existindo por um motivo só: ele lê o arquivo JSONL
# inteiro, enquanto o botão recorta a janela corrente.
#
# Falha fechada de propósito: sem o sal, ele PARA em vez de gerar um novo. Gerar
# um sal novo produziria hashes silenciosamente incompatíveis com o snapshot
# 2026-08-04 já publicado — dois pseudônimos diferentes para a mesma pessoa,
# dentro do mesmo repositório público, sem nenhum aviso.
garantir_sal() {
  if grep -qE '^TELEMETRIA_DATASET_SALT=.+' "$ENV_FILE" 2>/dev/null; then
    return
  fi

  cat >&2 <<'AVISO'
ABORTADO: nao existe TELEMETRIA_DATASET_SALT no .env.

Este e o caminho ANTIGO de publicacao e depende de um sal fixo. O sal foi
removido de proposito quando a sanitizacao passou para dentro da aplicacao, que
usa pseudonimo por pacote e nao guarda segredo nenhum.

Nao vou gerar um sal novo: isso produziria pseudonimos incompativeis com o
snapshot ja publicado, sem aviso, no mesmo repositorio publico.

Use o botao "Sincronizar dataset" em /telemetria. Se voce precisa
especificamente do historico completo do JSONL (que o botao nao cobre), exporte
TELEMETRIA_DATASET_SALT na sessao antes de rodar e assuma conscientemente a
divergencia entre os dois modelos de sanitizacao.
AVISO
  exit 1
}

garantir_sal
SAL="$(grep -E '^TELEMETRIA_DATASET_SALT=' "$ENV_FILE" | tail -n 1 | cut -d= -f2-)"

# ---- extrai a telemetria do container ---------------------------------------
CONTAINER_ID="$(docker compose -f "$COMPOSE_FILE" ps -q "$COMPOSE_SERVICE" 2>/dev/null || true)"
if [ -z "$CONTAINER_ID" ]; then
  echo "Container do serviço $COMPOSE_SERVICE não está em execução." >&2
  docker compose -f "$COMPOSE_FILE" ps >&2
  exit 1
fi

TEMPDIR="$(mktemp -d)"
trap 'rm -rf "$TEMPDIR"' EXIT
ORIGEM="$TEMPDIR/eventos-crus.jsonl"

echo "Extraindo telemetria de $COMPOSE_SERVICE:$ARQUIVO_REMOTO"
if ! docker cp "$CONTAINER_ID:$ARQUIVO_REMOTO" "$ORIGEM" 2>/dev/null; then
  echo "Não foi possível ler $ARQUIVO_REMOTO no container." >&2
  echo "Confira o caminho com: docker exec $CONTAINER_ID ls -la /deployments/data/logs" >&2
  exit 1
fi

LINHAS="$(wc -l < "$ORIGEM" | tr -d ' ')"
BYTES="$(wc -c < "$ORIGEM" | tr -d ' ')"
echo "Coletado: $LINHAS registro(s), $BYTES byte(s)."

# ---- sanitiza ---------------------------------------------------------------
CARIMBO="$(date -u +%Y-%m-%d)"
DESTINO="$SAIDA_BASE/$CARIMBO"

TELEMETRIA_DATASET_SALT="$SAL" python3 "$APP_DIR/scripts/sanitizar_telemetria.py" "$ORIGEM" "$DESTINO"

echo
echo "Dataset pronto em: $DESTINO"
echo
echo "Antes de publicar:"
echo "  1. Leia a lista 'CONFIRA ANTES DE PUBLICAR' acima, se apareceu."
echo "  2. Confira o README gerado: $DESTINO/README.md"
echo "  3. Publique manualmente. O script NÃO faz git push — publicação é"
echo "     irreversível assim que indexada, então é decisão sua e não do cron."
