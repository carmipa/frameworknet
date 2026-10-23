#!/usr/bin/env bash
# =====================================================================
# Gera o robots.txt que a BORDA (nginx do proxy) serve para este dominio,
# a partir da politica versionada no repositorio. Fonte unica: o deploy e o
# scripts/proxy-seo-frameworknet.sh chamam este script, e nenhum dos dois
# escreve o arquivo por conta propria.
#
# PROPOSITO: em producao o /robots.txt nao vem da aplicacao; o proxy o serve
# de um arquivo por host. Sem este passo o arquivo do repositorio mudava e o
# da borda ficava velho (medido em 23/10/2026: a borda ainda listava rotas da
# area autenticada que o repositorio ja tinha tirado).
#
# INVARIANTES: os comentarios saem (explicam a politica para quem mexe no
# codigo e citam caminho interno da VPS; o robo le regras, nao comentario);
# a linha Sitemap: e "Disallow: /api" tem de estar presentes; nenhum caminho
# da area autenticada pode aparecer. Arquivo anterior diferente ganha copia
# .bak com data antes de ser trocado.
#
# FALHA: qualquer guarda reprovada sai 1 SEM escrever nada (gera num
# temporario e so move no fim). Fonte ausente sai 2.
#
# Uso: scripts/gerar-robots-borda.sh <robots-do-repositorio> <arquivo-da-borda>
# =====================================================================
set -euo pipefail

FONTE="${1:?uso: $0 <fonte> <destino>}"
DESTINO="${2:?uso: $0 <fonte> <destino>}"

[ -f "$FONTE" ] || { echo "ERRO: politica versionada ausente em $FONTE" >&2; exit 2; }

TMP="$(mktemp "${DESTINO}.novo.XXXXXX")"
trap 'rm -f "$TMP"' EXIT
{
  echo "# Politica versionada em src/main/resources/META-INF/resources/robots.txt"
  echo "# do repositorio github.com/carmipa/frameworknet. Nao edite aqui: o deploy"
  echo "# (scripts/deploy.sh) regrava este arquivo a cada publicacao."
  echo
  tr -d '\r' < "$FONTE" | grep -vE '^[[:space:]]*#' | grep -vE '^[[:space:]]*$'
} > "$TMP"

grep -q "^Sitemap:" "$TMP" || { echo "ERRO: a politica perdeu a linha Sitemap:" >&2; exit 1; }
grep -q "^Disallow: /api$" "$TMP" || { echo "ERRO: a politica nao fecha /api" >&2; exit 1; }
if grep -qE "/(admin|login|telemetria)([^A-Za-z0-9_-]|$)" "$TMP"; then
  echo "ERRO: a politica lista caminho da area autenticada (vira mapa no arquivo publico)" >&2
  exit 1
fi

if [ -f "$DESTINO" ] && ! cmp -s "$TMP" "$DESTINO"; then
  cp -p "$DESTINO" "${DESTINO}.bak-$(date -u +%Y%m%dT%H%M%SZ)"
fi
chmod 644 "$TMP"
mv "$TMP" "$DESTINO"
trap - EXIT
echo "robots da borda gravado em $DESTINO ($(grep -c '^Disallow' "$DESTINO") Disallow, $(grep -c '^User-agent' "$DESTINO") grupos)"
