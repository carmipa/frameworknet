#!/usr/bin/env bash
# =====================================================================
# Liga as paginas 502/503/504 no proxy reverso (NGINX do NPM) da VPS.
#
# O QUE RESOLVE (medido em 08/09/2026):
#   O server_proxy.conf do proxy tinha "error_page 400 494" e NADA para os
#   erros de upstream. Quando o nginx nao alcanca a aplicacao — a CADA deploy,
#   na janela em que o container e recriado, e em qualquer queda — o visitante
#   recebia a tela embutida do openresty: cinza, em ingles, sem caminho de volta.
#
#   Que o mecanismo funciona ja estava provado: um cabecalho de 40 KB dispara o
#   "error_page 400" existente e serve 8935 bytes ("400 — Sessao grande demais").
#   Faltava apenas o equivalente para 502/503/504.
#
# AS PAGINAS SAO GERADAS, NAO ESCRITAS A MAO.
#   scripts/erro-proxy/{502,503,504}.html sao produzidas por
#   PaginaErroProxyGeneratorTest a partir de templates/paginaErros/erro.html com
#   o CatalogoErros real, com CSS e JS EMBUTIDOS — elas aparecem justamente
#   quando a aplicacao esta fora, entao nada nelas pode depender dela.
#   Para atualizar depois de mexer no visual do site:
#       ./gradlew test --tests "*PaginaErroProxyGenerator*"   (na maquina de dev)
#       git commit && git push
#       na VPS: cd /opt/framework-net-java-quarkus && git pull && bash scripts/proxy-paginas-erro.sh
#
# ESCOPO: o server_proxy.conf e incluido no server{} de TODO proxy host da VPS,
#   entao estas paginas respondem pelos QUATRO dominios. Elas levam a marca
#   "Framework de Redes" no topo; se isso incomodar nos vizinhos, o caminho e um
#   error_page por host, e nao um texto generico aqui.
#
# SEGURANCA: nao recarrega nada sem "nginx -t" passar; se reprovar, restaura o
#   backup e sai sem recarregar. Idempotente: rodar duas vezes nao duplica nada.
#
# USO (na VPS, como root):  bash scripts/proxy-paginas-erro.sh
# =====================================================================
set -euo pipefail

PROXY=${PROXY_CONTAINER:-infra-proxy-app-1}
CUSTOM=${PROXY_CUSTOM_CONF:-/opt/infra-proxy/data/nginx/custom/server_proxy.conf}
DESTINO=${PROXY_ERRO_DIR:-/opt/infra-proxy/data/erro-pride/_erro}
ORIGEM="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/erro-proxy"
D=$(date +%Y%m%d-%H%M%S)

erro() { echo "ERRO: $*" >&2; exit 1; }

command -v docker >/dev/null 2>&1 || erro "docker nao encontrado"
[ -f "$CUSTOM" ] || erro "nao achei $CUSTOM (este script roda NA VPS)"
[ -d "$DESTINO" ] || erro "nao achei $DESTINO"
for c in 502 503 504; do
  [ -f "$ORIGEM/$c.html" ] || erro "falta $ORIGEM/$c.html — rode a suite para gerar"
done

echo "== backup =="
cp "$CUSTOM" "$CUSTOM.bak-$D"
echo "   $CUSTOM.bak-$D"

echo "== instalando as tres paginas =="
install -m 644 "$ORIGEM/502.html" "$ORIGEM/503.html" "$ORIGEM/504.html" "$DESTINO/"
ls -la "$DESTINO/"502.html "$DESTINO/"503.html "$DESTINO/"504.html

echo "== ligando os error_page =="
if grep -q "error_page 502" "$CUSTOM"; then
  echo "   ja configurado (idempotente)"
else
  python3 - "$CUSTOM" <<'PY'
import io, sys
caminho = sys.argv[1]
c = io.open(caminho, encoding="utf-8").read()
ancora = "error_page 400 494 /_erro/cabecalho-grande.html;"
if ancora not in c:
    sys.exit("ancora nao encontrada -- abortado sem escrever nada")

bloco = ancora + """

# --- 08/09/2026: 502/503/504 mostravam a tela embutida do openresty ----------------
# Medido: nao havia error_page nenhum para erro de upstream. A cada deploy, e em
# qualquer queda de container, o visitante via "502 Bad Gateway" em ingles, cinza,
# sem caminho de volta.
#
# As paginas sao GERADAS do template do proprio site (paginaErros/erro.html +
# CatalogoErros) pela suite do framework-net, com CSS e JS embutidos: elas aparecem
# justamente quando a aplicacao esta fora, entao um <link> para o CSS do site daria
# 502 tambem e a pagina de erro apareceria quebrada. Fonte versionada em
# scripts/erro-proxy/ daquele repositorio; atualizar = git pull + rodar o script.
#
# proxy_intercept_errors continua DESLIGADO de proposito. Liga-lo faria o nginx
# capturar tambem o que a aplicacao ja trata bem (o 404 em HTML de 12 KB) e, pior,
# os 400 em JSON das rotas /api/, que virariam HTML e quebrariam todo fetch() do
# frontend em silencio. Por isso so entram aqui os codigos que a aplicacao NUNCA
# chega a responder: se ela responde, ela esta viva.
error_page 502 /_erro/502.html;
error_page 503 /_erro/503.html;
error_page 504 /_erro/504.html;

location ~ ^/_erro/(502|503|504)\\.html$ {
    internal;
    root /data/erro-pride;
    default_type text/html;
    add_header Cache-Control "no-store" always;
    add_header X-Robots-Tag "noindex" always;
}"""

io.open(caminho, "w", encoding="utf-8").write(c.replace(ancora, bloco, 1))
print("   error_page 502/503/504 acrescentados")
PY
fi

echo "== validando ANTES de recarregar =="
if ! docker exec "$PROXY" nginx -t; then
  echo "!! nginx -t reprovou — restaurando e NAO recarregando" >&2
  cp "$CUSTOM.bak-$D" "$CUSTOM"
  exit 1
fi

echo "== recarregando =="
docker exec "$PROXY" nginx -s reload
sleep 2

echo
echo "== os quatro dominios continuam de pe =="
falhou=0
for d in frameworknet aspm binmapper challengepride; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 12 "https://$d.carminati.dev.br/" || echo 000)
  printf '  %-16s https=%s\n' "$d" "$code"
  case "$code" in 200|301|302|303) ;; *) falhou=1 ;; esac
done
[ "$falhou" -eq 0 ] || erro "algum dominio parou de responder — rode o ROLLBACK abaixo"

cat <<FIM

== PROVA REAL (derruba o site por ~20 segundos) ==
  docker stop framework-net-java
  curl -s -o /tmp/p502.html -w 'status=%{http_code} bytes=%{size_download}\\n' https://frameworknet.carminati.dev.br/
  grep -o '<title>[^<]*</title>' /tmp/p502.html
  docker start framework-net-java

  Esperado: status=502, bytes na casa dos 33 mil e
  "<title>502 — Framework de Redes</title>".
  Se vier ~150 bytes, e a tela do openresty: o error_page nao pegou.

== ROLLBACK ==
  cp $CUSTOM.bak-$D $CUSTOM
  rm -f $DESTINO/502.html $DESTINO/503.html $DESTINO/504.html
  docker exec $PROXY nginx -t && docker exec $PROXY nginx -s reload
FIM
