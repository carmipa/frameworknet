#!/usr/bin/env bash
# =====================================================================
# Dois ajustes de SEO que moram no PROXY, nao na aplicacao.
#
#   ITEM 1 — Force SSL.
#     Medido em 08/09/2026: http://frameworknet.carminati.dev.br/ responde 200
#     com o corpo IDENTICO ao https:// (mesmo md5), sem redirecionar. O
#     Strict-Transport-Security que a resposta carrega e IGNORADO pelo navegador
#     quando chega fora de TLS (RFC 6797 secao 7.2), entao ele nao desempata.
#     No banco do NPM: ssl_forced=0 no host 1 (frameworknet) e =1 no host 3
#     (binmapper) — que e justamente o que responde 301.
#
#     O canonical ja resolve o lado da indexacao; o redirect resolve o lado de
#     quem digita o endereco sem https e trafega em claro.
#
#   ITEM 2 — robots.txt proprio deste dominio, com a linha Sitemap:.
#     Hoje o frameworknet cai no arquivo generico do proxy, compartilhado. Foi por
#     isso que a linha "Sitemap:" ficou de fora em 02/09: apontar o sitemap de um
#     dominio num arquivo que responde por outro e submissao cruzada, e o buscador
#     descarta. Desde 03/09 a politica e POR HOST (map $host com lista branca), e
#     medido em 08/09 os dominios ja servem robots com md5 diferentes — entao a
#     objecao caiu; falta este dominio ter o seu.
#
# POR QUE O ITEM 1 MEXE NO BANCO E NO ARQUIVO:
#   O 1.conf e GERADO pelo NPM a partir do banco dele. Editar so o arquivo
#   funciona ate alguem salvar esse host no painel — aí o NPM regrava e apaga o
#   redirect. Com ssl_forced=1 no banco, ele regrava JA COM o redirect. As duas
#   pontas passam a concordar.
#
#   Alternativa sem script: ligar "Force SSL" no painel do NPM (porta 81, exposta
#   so em 127.0.0.1 — chegue por  ssh -L 8181:127.0.0.1:81 vps-paulo). O painel
#   faz exatamente o que este script faz, e e o caminho preferido se estiver a mao.
#
# NUNCA use try_files /$host.txt: $host vem do cabecalho Host, que o CLIENTE
# escolhe, e interpola-lo num caminho de arquivo e path traversal esperando
# acontecer. Por isso o map e lista branca.
#
# SEGURANCA: nao recarrega sem "nginx -t" passar; se reprovar, restaura e sai.
# Idempotente.
#
# USO (na VPS, como root):  bash scripts/proxy-seo-frameworknet.sh
# =====================================================================
set -euo pipefail

PROXY=${PROXY_CONTAINER:-infra-proxy-app-1}
BASE=${PROXY_DATA:-/opt/infra-proxy/data}
CONF="$BASE/nginx/proxy_host/1.conf"
MAPA="$BASE/nginx/custom/http.conf"
BANCO="$BASE/database.sqlite"
ROBOTS="$BASE/robots"
FONTE_ROBOTS="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/src/main/resources/META-INF/resources/robots.txt"
DOM=frameworknet.carminati.dev.br
D=$(date +%Y%m%d-%H%M%S)

erro() { echo "ERRO: $*" >&2; exit 1; }

command -v docker  >/dev/null 2>&1 || erro "docker nao encontrado"
command -v python3 >/dev/null 2>&1 || erro "python3 nao encontrado"
[ -f "$CONF" ]  || erro "nao achei $CONF (este script roda NA VPS)"
[ -f "$MAPA" ]  || erro "nao achei $MAPA"
[ -f "$BANCO" ] || erro "nao achei $BANCO"

grep -q "$DOM" "$CONF" || erro "$CONF nao e o host do $DOM — o NPM pode ter renumerado; confira antes"

echo "== backup =="
cp "$CONF"  "$CONF.bak-$D"
cp "$MAPA"  "$MAPA.bak-$D"
cp "$BANCO" "$BANCO.bak-$D"
echo "   $CONF.bak-$D"
echo "   $MAPA.bak-$D"
echo "   $BANCO.bak-$D"

restaurar() {
  echo "!! restaurando e NAO recarregando" >&2
  cp "$CONF.bak-$D" "$CONF"
  cp "$MAPA.bak-$D" "$MAPA"
  exit 1
}

# ---------------------------------------------------------------------
echo "== item 1: Force SSL =="
if grep -q "force-ssl.conf" "$CONF"; then
  echo "   arquivo ja tem o include (idempotente)"
else
  python3 - "$CONF" <<'PY' || exit 1
import io, sys
conf = sys.argv[1]
c = io.open(conf, encoding="utf-8").read()
ancora = "  access_log /data/logs/proxy-host-1_access.log proxy;"
if ancora not in c:
    sys.exit("ancora do access_log nao encontrada -- abortado sem escrever")
# identico ao que o NPM gera no 3.conf (binmapper, ssl_forced=1)
bloco = ('    # Force SSL\n'
         '    set $trust_forwarded_proto "F";\n'
         '    include conf.d/include/force-ssl.conf;\n\n')
io.open(conf, "w", encoding="utf-8").write(c.replace(ancora, bloco + ancora, 1))
print("   include force-ssl.conf inserido no 1.conf")
PY
fi

python3 - "$BANCO" <<'PY'
import sqlite3, sys
con = sqlite3.connect(sys.argv[1])
antes = con.execute("select ssl_forced from proxy_host where id=1").fetchone()[0]
if antes == 1:
    print("   banco: ssl_forced ja era 1")
else:
    con.execute("update proxy_host set ssl_forced=1 where id=1")
    con.commit()
    print("   banco: ssl_forced do host 1: %s -> 1" % antes)
con.close()
PY

# ---------------------------------------------------------------------
echo "== item 2: robots.txt proprio =="

# O ARQUIVO e regerado SEMPRE — assim "git pull + rodar o script" leva a politica
# nova a producao. So a entrada no map e que acontece uma vez.
#
# A politica servida e a VERSIONADA no repositorio: a mesma que o
# RobotsTxtHttpTest valida a cada build, e que ja traz a linha Sitemap:.
#
# A primeira versao deste script copiava o robots GENERICO do proxy, e o resultado
# foi divergencia silenciosa, medida em 08/09: producao ficou com "Disallow: /api/"
# (com barra, que pela regra de prefixo NAO cobre "/api") enquanto o repo tem
# "Disallow: /api", e ainda herdou "/q/" e "/oauth2/", que sao rotas de OUTRO
# projeto. Guarda que valida um arquivo enquanto producao serve outro nao guarda
# coisa nenhuma.
#
# Os comentarios saem: explicam a politica para quem mexe no codigo e citam caminho
# interno da VPS. O robots.txt e publico, e o robo le regras, nao comentario.
[ -f "$FONTE_ROBOTS" ] || erro "nao achei a politica versionada em $FONTE_ROBOTS"
{
  echo "# Politica versionada em src/main/resources/META-INF/resources/robots.txt"
  echo "# do repositorio framework-net-java-quarkus. Nao edite aqui: rode"
  echo "# scripts/proxy-seo-frameworknet.sh depois de um git pull."
  echo
  grep -vE '^[[:space:]]*#' "$FONTE_ROBOTS" | grep -vE '^[[:space:]]*$'
} > "$ROBOTS/$DOM.txt"
grep -q "^Sitemap:" "$ROBOTS/$DOM.txt" || erro "a politica versionada perdeu a linha Sitemap:"
grep -q "^Disallow: /api$" "$ROBOTS/$DOM.txt" || erro "a politica gerada nao fecha /api — abortado"
# Area autenticada fica FORA do arquivo publico (sai do indice pelo X-Robots-Tag da aplicacao).
! grep -qE "/(admin|login|telemetria)([^A-Za-z0-9_-]|$)" "$ROBOTS/$DOM.txt" || erro "a politica gerada lista caminho da area autenticada — abortado"
echo "   $ROBOTS/$DOM.txt gerado da politica VERSIONADA ($(grep -c '^Disallow' "$ROBOTS/$DOM.txt") regras Disallow, $(grep -c '^User-agent' "$ROBOTS/$DOM.txt") grupos)"

if grep -q "$DOM" "$MAPA"; then
  echo "   ja mapeado no map \$host (idempotente)"
else
  python3 - "$MAPA" "$DOM" <<'PY' || exit 1
import io, sys
mapa, dom = sys.argv[1], sys.argv[2]
c = io.open(mapa, encoding="utf-8").read()
ancora = "    challengepride.carminati.dev.br  /challengepride.carminati.dev.br.txt;"
if ancora not in c:
    sys.exit("ancora do map nao encontrada -- abortado sem escrever")
io.open(mapa, "w", encoding="utf-8").write(
    c.replace(ancora, ancora + "\n    %-32s /%s.txt;" % (dom, dom), 1))
print("   entrada acrescentada ao map $host (lista branca)")
PY
fi

# ---------------------------------------------------------------------
echo "== validando ANTES de recarregar =="
docker exec "$PROXY" nginx -t || restaurar

echo "== recarregando =="
docker exec "$PROXY" nginx -s reload
sleep 2

echo
echo "== verificacao =="
http=$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 "http://$DOM/"  || echo 000)
https=$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 "https://$DOM/" || echo 000)
printf '  http  -> %s   (esperado 301)\n' "$http"
printf '  https -> %s   (esperado 200)\n' "$https"
printf '  Sitemap no robots: %s\n' "$(curl -s --max-time 12 "https://$DOM/robots.txt" | grep -i '^sitemap' || echo 'AUSENTE')"
echo "  vizinhos (nenhum pode cair):"
for v in aspm binmapper challengepride; do
  printf '    %-16s https=%s\n' "$v" "$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 "https://$v.carminati.dev.br/" || echo 000)"
done

cat <<FIM

== ROLLBACK ==
  cp $CONF.bak-$D $CONF
  cp $MAPA.bak-$D $MAPA
  cp $BANCO.bak-$D $BANCO
  rm -f $ROBOTS/$DOM.txt
  docker exec $PROXY nginx -t && docker exec $PROXY nginx -s reload
FIM
