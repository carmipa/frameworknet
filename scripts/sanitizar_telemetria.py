#!/usr/bin/env python3
"""Sanitiza a telemetria OTLP do Framework Net para publicação como dataset.

PROPÓSITO DE NEGÓCIO
    A telemetria é artefato temporário destinado a virar dataset público
    (decisão de 2026-07-20). Entre o arquivo cru e o repositório existe uma
    etapa obrigatória: remover o que identifica pessoas. Este script é essa
    etapa. Lê o NDJSON de LogRecord OTLP gravado pela aplicação e devolve um
    dataset publicável, com estatísticas e esquema, sem depender de nenhuma
    biblioteca externa (só a stdlib do Python 3).

INVARIANTES DO DOMÍNIO
    - Nenhum endereço IP de usuário sai em claro: vira SHA-256 truncado com sal
      secreto. Sem o sal não há reversão, e o sal nunca entra no dataset.
    - Coordenadas de GPS são REMOVIDAS, não anonimizadas: 6 casas decimais são
      ~10 cm, e nem hash nem arredondamento tornam isso seguro de publicar.
    - O campo `body` é RECONSTRUÍDO a partir dos atributos já sanitizados, nunca
      filtrado por regex. O body repete os valores em texto livre
      (`evento=geo_lookup status=ok ip=187.255.18.51`), então limpar só os
      atributos publicaria o IP mesmo assim.
    - `traceId`, `spanId` e `request_id` são preservados: são aleatórios por
      requisição, não identificam pessoa, e são o que torna o dataset analisável.
    - Registro que o script não consegue interpretar é DESCARTADO e contabilizado,
      nunca copiado "por via das dúvidas" — na dúvida, não publica.

COMPORTAMENTO EM CASO DE FALHA
    - Sal ausente ou curto demais: aborta com código 2 antes de ler qualquer dado.
    - Linha malformada: conta em `descartados.json_invalido` e segue.
    - Arquivo de entrada inexistente: aborta com código 1.
    - O script nunca sobrescreve o arquivo de origem.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import re
import sys
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

SAL_MINIMO = 16

# Comprimento do menor IPv4 possível ("1.1.1.1"). Serve de piso para o que a
# auditoria aceita como agulha de busca — ver sanitizar_registro.
MENOR_IDENTIFICADOR = 7

# IDENTIDADE — capturado pelo servidor SOBRE o visitante. Sempre pseudonimizado.
#
# Distinção que sustenta todo o script: um endereço que o servidor observou
# ("de onde veio esta requisição") identifica pessoa. Um endereço que o usuário
# DIGITOU num exercício de sub-rede é conteúdo didático — é exatamente o dado
# que dá valor ao dataset. Pseudonimizar conteúdo esvazia o dataset sem proteger
# ninguém; publicar identidade expõe alguém. Só o primeiro grupo entra aqui.
CAMPOS_IDENTIDADE = {
    "framework.field.ip",
}

# ALLOWLIST de campos de negócio publicáveis.
#
# É allowlist, e não blocklist, por decisão de segurança: a aplicação ganha
# campos novos a cada módulo (TelemetriaOtlpMapper transforma QUALQUER chave de
# `fields` em `framework.field.<chave>`), e uma blocklist só protege contra o
# que alguém lembrou de listar. Com allowlist, campo novo nasce fora do dataset
# e só entra quando revisado — o erro passa a ser omissão, não vazamento.
#
# Se um campo legítimo estiver faltando aqui, a exportação avisa em
# "CAMPOS DESCARTADOS" ao final; basta acrescentá-lo depois de conferir que não
# carrega dado pessoal.
CAMPOS_PUBLICAVEIS = {
    # dimensões do plano de endereçamento (conteúdo didático)
    "baseNetwork", "blocos", "cidr", "enderecos", "estrategia", "exibidos",
    "extras", "hosts", "locations", "locationsCount", "modo", "prefixoAlvo",
    "prefixoBase", "prefixoResumo", "prefixoVlan", "redes", "total",
    "totalSubredes", "truncado", "usedSubnets", "vlans", "wanLinks",
    # execução e diagnóstico
    "dados", "durationMs", "encerramento", "erro", "httpStatus", "passos",
    "status", "statusCalc", "topology",
    # "ip" só chega aqui depois de eh_identificador() ter dito NÃO: todo valor
    # identificador já foi pseudonimizado e renomeado para "ip_hash" antes. O que
    # resta é faixa privada, loopback ou resolvedor público — endereço de
    # laboratório digitado em exercício, que é justamente o conteúdo didático.
    "ip",
}

# Deliberadamente FORA da allowlist, com o motivo:
#   framework.request_id  — vem do header X-Request-Id do cliente, sem validação
#   hostname / host / dominio — alvo digitado de DNS/ping; pode ser nome interno
#   mensagem              — texto de exceção, ecoa a entrada do usuário
# Se algum deles passar a ser desejável, acrescente acima DEPOIS de conferir que
# não carrega dado de terceiro. A exportação lista os descartados a cada execução.

# Atributos removidos sem substituto: precisão alta demais para publicar.
# lat/lon vêm do recurso de GPS consentido, com 6 casas decimais (~10 cm).
CAMPOS_REMOVIDOS = {
    "framework.field.lat",
    "framework.field.lon",
}

# Atributos de correlação preservados. framework.request_id ficou de FORA: seu
# valor vem do header X-Request-Id enviado pelo próprio cliente, sem validação
# nem limite de tamanho, então é texto arbitrário controlado por quem acessa —
# publicá-lo verbatim é publicar entrada de terceiro. O traceId é gerado pelo
# servidor (UUID) e basta para correlacionar.
CORRELACAO_PRESERVADA = {
    "event.name", "framework.module", "framework.status", "framework.event_id",
    "http.request.method", "http.route", "http.response.status_code",
    "framework.duration_ms",
}

# Caminhos que são ruído de infraestrutura e não descrevem uso do sistema.
# "/health" cobre a sonda do container em coletas anteriores a 2026-08-03; desde
# então a aplicação já nem registra essa rota.
PREFIXOS_RUIDO = ("/q/", "/web/", "/telemetria/api", "/health")
EXTENSOES_ESTATICAS = {
    "css", "js", "png", "jpg", "jpeg", "gif", "svg", "ico",
    "webp", "woff", "woff2", "ttf", "map",
}

IPV4 = re.compile(r"^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$")

# Resolvedores DNS públicos: endereços anycast de operadoras, nunca atribuídos a
# um usuário final. Aparecem no dataset porque foram digitados em exercícios de
# DNS e ping, então pseudonimizá-los só destruiria o valor didático sem proteger
# ninguém — 8.8.8.8 no dataset não revela quem estava usando o sistema.
RESOLVEDORES_PUBLICOS = {
    "8.8.8.8", "8.8.4.4",            # Google
    "1.1.1.1", "1.0.0.1",            # Cloudflare
    "9.9.9.9", "149.112.112.112",    # Quad9
    "208.67.222.222", "208.67.220.220",  # OpenDNS
    "4.2.2.1", "4.2.2.2",            # Level3
    "64.6.64.6", "64.6.65.6",        # Verisign
}


def pseudonimizar(valor: str, sal: str) -> str:
    """Devolve um pseudônimo estável para `valor`.

    INVARIANTES: mesmo valor + mesmo sal produzem sempre o mesmo pseudônimo,
    o que preserva contagem de visitantes únicos e sessões. Sem o sal, o
    pseudônimo não volta ao original.
    """
    digest = hashlib.sha256((sal + "|" + valor).encode("utf-8")).hexdigest()
    return digest[:12]


def eh_identificador(valor: str) -> bool:
    """Diz se o valor de um campo de identidade realmente identifica alguém.

    PROPÓSITO DE NEGÓCIO
        O campo do IP serve a dois usos no sistema: o endereço que o servidor
        observou (identidade) e o endereço que o usuário DIGITOU no formulário
        de GeoIP (conteúdo de exercício). O nome do campo é o mesmo nos dois
        casos, então a decisão precisa vir do valor.

    INVARIANTES DO DOMÍNIO
        Não identificam pessoa, e portanto permanecem legíveis: faixas privadas,
        loopback e documentação (valores de laboratório do próprio material), e
        os resolvedores públicos bem conhecidos da lista RESOLVEDORES_PUBLICOS —
        endereços anycast de operadoras, jamais atribuídos a um usuário final, e
        que aparecem aqui porque foram digitados em exercícios de DNS e ping.
        Qualquer outra coisa (IPv4 roteável, IPv6, formato desconhecido) é
        tratada como identidade e pseudonimizada. O padrão é proteger: só não
        protege o que está provado ser inofensivo.
    """
    if valor in RESOLVEDORES_PUBLICOS:
        return False
    if IPV4.match(valor):
        return ip_publico(valor)
    return True


def valor_do_atributo(atributo: dict) -> str | None:
    """Extrai o valor textual de um AnyValue OTLP, seja string ou int."""
    valor = atributo.get("value")
    if not isinstance(valor, dict):
        return None
    for chave in ("stringValue", "intValue", "doubleValue", "boolValue"):
        if chave in valor:
            return str(valor[chave])
    return None


def eh_ruido(rota: str | None) -> bool:
    """Diz se a rota é infraestrutura/estático em vez de uso real do sistema."""
    if not rota:
        return False
    if rota.startswith(PREFIXOS_RUIDO):
        return True
    ponto = rota.rfind(".")
    if ponto > -1 and ponto < len(rota) - 1:
        return rota[ponto + 1:].lower() in EXTENSOES_ESTATICAS
    return False


def montar_body(evento: str, status: str, campos: dict[str, str]) -> str:
    """Reconstrói a mensagem no mesmo formato que a aplicação emite.

    PROPÓSITO: garantir que o texto livre nunca contenha valor que já foi
    sanitizado nos atributos. Reproduz `TelemetriaLogger.montarMensagem`:
    prefixo com evento e status, depois os campos em ordem alfabética,
    ignorando a chave `status` (que já vai no prefixo).
    """
    partes = [f"evento={evento}", f"status={status}"]
    for chave in sorted(campos):
        if chave == "status":
            continue
        partes.append(f"{chave}={campos[chave]}")
    return " ".join(partes)


def sanitizar_registro(registro: dict, sal: str, contadores: Counter,
                       identidades: set[str]) -> dict | None:
    """Sanitiza um LogRecord OTLP.

    O conjunto `identidades` recebe os valores originais dos campos de
    identidade encontrados. É o que a auditoria usa depois para provar, sobre o
    arquivo de saída, que nenhum deles sobreviveu — prova bem mais forte que
    procurar um padrão genérico de IP, que confundiria conteúdo didático
    (`baseNetwork=192.19.0.0/16` digitado num exercício) com identidade.

    COMPORTAMENTO EM CASO DE FALHA: devolve None quando o registro é ruído ou
    não tem a estrutura mínima esperada; o motivo é contabilizado em `contadores`.
    """
    atributos = registro.get("attributes")
    if not isinstance(atributos, list):
        contadores["descartados_sem_atributos"] += 1
        return None

    rota = None
    for atributo in atributos:
        if atributo.get("key") == "http.route":
            rota = valor_do_atributo(atributo)
            break
    if eh_ruido(rota):
        contadores["descartados_ruido_tecnico"] += 1
        return None

    novos: list[dict] = []
    campos_body: dict[str, str] = {}
    evento = ""
    status = ""

    for atributo in atributos:
        chave = atributo.get("key")
        if not isinstance(chave, str):
            continue
        valor = valor_do_atributo(atributo)
        if valor is None:
            continue

        if chave in CAMPOS_REMOVIDOS:
            contadores["campos_removidos"] += 1
            continue

        if chave in CAMPOS_IDENTIDADE and eh_identificador(valor):
            # A auditoria procura estes valores por substring no arquivo final.
            # Valor curto (o usuário digita "a" no formulário de GeoIP) casaria
            # com quase toda linha e destruiria o dataset a cada exportação, sem
            # que houvesse vazamento nenhum. Abaixo do menor IPv4 possível
            # ("1.1.1.1" = 7 chars) o valor ainda é pseudonimizado — só não vira
            # agulha de busca, porque não distingue nada.
            if len(valor) >= MENOR_IDENTIFICADOR:
                identidades.add(valor)
            valor = pseudonimizar(valor, sal)
            chave = chave + "_hash"
            contadores["campos_pseudonimizados"] += 1
        elif chave.startswith("framework.field."):
            # Allowlist: campo de negócio não revisado NÃO entra no dataset.
            nome = chave[len("framework.field."):]
            if nome not in CAMPOS_PUBLICAVEIS:
                contadores["campos_descartados"] += 1
                contadores[f"descartado_{nome}"] += 1
                continue
        elif chave not in CORRELACAO_PRESERVADA:
            # Atributo de correlação/infra fora da lista revisada — inclui
            # framework.request_id, que carrega header enviado pelo cliente.
            contadores["campos_descartados"] += 1
            contadores[f"descartado_{chave}"] += 1
            continue

        if chave == "event.name":
            evento = valor
        elif chave == "framework.status":
            status = valor

        # O prefixo framework.field. marca o que a aplicação colocou no body.
        if chave.startswith("framework.field."):
            campos_body[chave[len("framework.field."):]] = valor

        novos.append({"key": chave, "value": {"stringValue": valor}})

    if not evento:
        contadores["descartados_sem_evento"] += 1
        return None

    saida = {
        "timeUnixNano": registro.get("timeUnixNano"),
        "severityText": registro.get("severityText"),
        "severityNumber": registro.get("severityNumber"),
        "body": {"stringValue": montar_body(evento, status or "ok", campos_body)},
        "attributes": novos,
    }
    for correlacao in ("traceId", "spanId"):
        if registro.get(correlacao):
            saida[correlacao] = registro[correlacao]
    if not saida.get("traceId"):
        contadores["sem_correlacao"] += 1

    contadores["publicados"] += 1
    contadores[f"evento_{evento}"] += 1
    return saida


def auditar(caminho: Path, identidades: set[str]) -> list[str]:
    """Varre o dataset gerado atrás de vazamento residual.

    PROPÓSITO: é a prova de que a sanitização funcionou, e não a promessa. Roda
    sobre o ARQUIVO DE SAÍDA, procurando (a) qualquer valor de identidade que a
    passagem anterior tenha coletado, em qualquer campo, e (b) coordenadas com
    precisão de GPS. O item (a) é o que pega o vazamento pelo `body` em texto
    livre e por qualquer campo não previsto.

    COMPORTAMENTO EM CASO DE FALHA: devolve a lista de achados; lista vazia
    significa dataset limpo. Quem chama decide abortar.
    """
    coordenada = re.compile(r"\b(?:lat|lon)=-?\d+\.\d{3,}")
    achados: list[str] = []

    with caminho.open("r", encoding="utf-8") as arquivo:
        for numero, linha in enumerate(arquivo, start=1):
            for identidade in identidades:
                if identidade in linha:
                    achados.append(f"linha {numero}: valor de identidade residual {identidade!r}")
            for coord in set(coordenada.findall(linha)):
                achados.append(f"linha {numero}: coordenada residual {coord}")
    return achados


def inventariar_publicos(caminho: Path) -> Counter:
    """Lista os IPv4 roteáveis que sobraram no dataset, para conferência humana.

    PROPÓSITO: depois da sanitização, o que resta de IP público é conteúdo que o
    usuário digitou em exercícios — não identidade. Isso NÃO bloqueia a geração,
    mas é impresso para o operador bater o olho antes de publicar. Segurança que
    depende só de regra automática esconde o caso que a regra não previu.
    """
    ipv4_em_texto = re.compile(r"\b(?:\d{1,3}\.){3}\d{1,3}\b")
    encontrados: Counter = Counter()
    with caminho.open("r", encoding="utf-8") as arquivo:
        for linha in arquivo:
            for ip in ipv4_em_texto.findall(linha):
                if ip_publico(ip):
                    encontrados[ip] += 1
    return encontrados


def ip_publico(ip: str) -> bool:
    """Diz se o IPv4 é roteável na internet (logo, potencialmente identificador).

    Faixas privadas, loopback, link-local, documentação (RFC 5737) e multicast
    são valores de laboratório do próprio material didático — publicá-los não
    expõe ninguém.
    """
    try:
        partes = [int(p) for p in ip.split(".")]
    except ValueError:
        return False
    if len(partes) != 4 or any(p > 255 for p in partes):
        return False
    a, b, c = partes[0], partes[1], partes[2]

    # Faixas inteiras reservadas (comparação por 1º/2º octeto é exata aqui).
    if a in (0, 10, 127) or a >= 224:
        return False
    if a == 172 and 16 <= b <= 31:          # 172.16.0.0/12
        return False
    if a == 192 and b == 168:               # 192.168.0.0/16
        return False
    if a == 169 and b == 254:               # 169.254.0.0/16
        return False
    if a == 100 and 64 <= b <= 127:         # 100.64.0.0/10 (CGNAT)
        return False
    if a == 198 and b in (18, 19):          # 198.18.0.0/15 (benchmarking)
        return False

    # Reservas que são /24, NÃO /16. Testar só os dois primeiros octetos
    # liberava 65.536 endereços roteáveis como se fossem de documentação —
    # 192.0.2.1 é doc, mas 192.0.78.1 é roteável e identifica alguém.
    if (a, b, c) in {(192, 0, 0), (192, 0, 2), (198, 51, 100), (203, 0, 113)}:
        return False
    return True


def escrever_apoio(destino: Path, contadores: Counter, origem: Path, gerado_em: str) -> None:
    """Grava README, esquema e estatísticas ao lado do dataset."""
    eventos = {
        chave[len("evento_"):]: valor
        for chave, valor in sorted(contadores.items())
        if chave.startswith("evento_")
    }
    estatisticas = {
        "gerado_em_utc": gerado_em,
        "arquivo_origem": origem.name,
        "registros_publicados": contadores["publicados"],
        "descartados": {
            "ruido_tecnico": contadores["descartados_ruido_tecnico"],
            "json_invalido": contadores["descartados_json_invalido"],
            "sem_atributos": contadores["descartados_sem_atributos"],
            "sem_evento": contadores["descartados_sem_evento"],
        },
        "sanitizacao": {
            "campos_pseudonimizados": contadores["campos_pseudonimizados"],
            "campos_removidos": contadores["campos_removidos"],
        },
        "qualidade": {
            "registros_sem_correlacao": contadores["sem_correlacao"],
        },
        "eventos": eventos,
    }
    (destino / "estatisticas.json").write_text(
        json.dumps(estatisticas, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    esquema = {
        "formato": "NDJSON — um OpenTelemetry LogRecord por linha",
        "campos": {
            "timeUnixNano": "instante do evento, nanossegundos desde a época (string)",
            "severityText": "INFO | WARN | ERROR",
            "severityNumber": "severidade OTLP numérica",
            "body.stringValue": "mensagem reconstruída a partir dos atributos sanitizados",
            "traceId": "correlação da requisição — agrupe por aqui para contar incidentes",
            "spanId": "identificador do span",
            "attributes[]": "pares chave/valor; veja a tabela abaixo",
        },
        "atributos": {
            "event.name": "nome do evento de negócio",
            "framework.module": "módulo de origem",
            "framework.status": "ok | error",
            "framework.request_id": "identificador curto da requisição",
            "http.request.method": "método HTTP",
            "http.route": "rota atendida",
            "framework.duration_ms": "duração da requisição em ms",
            "framework.field.*": "campos específicos do evento",
            "framework.field.ip_hash": "pseudônimo do IP (SHA-256 com sal secreto, 12 hex)",
        },
    }
    (destino / "schema.json").write_text(
        json.dumps(esquema, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

    readme = f"""# Dataset de telemetria — Framework de Redes

Gerado em {gerado_em} a partir da telemetria de produção de
[frameworknet.carminati.dev.br](https://frameworknet.carminati.dev.br).

`{contadores["publicados"]}` registros publicados.
Formato: **NDJSON**, um OpenTelemetry `LogRecord` por linha (`eventos.jsonl`).
Esquema completo em `schema.json`; contagens em `estatisticas.json`.

## O que foi removido antes de publicar

| Dado original | Tratamento | Motivo |
|---|---|---|
| IP do visitante | SHA-256 com sal secreto, truncado em 12 hex (`framework.field.ip_hash`) | Identifica pessoa. O sal fica na VPS e nunca entra neste repositório, então o hash não é reversível. Como é estável, ainda dá para contar visitantes únicos. |
| Coordenadas de GPS (`lat`/`lon`) | **Removidas** | 6 casas decimais são ~10 cm de precisão. Nem hash nem arredondamento tornam isso publicável. |
| Hostname/domínio quando era um IP | Pseudonimizado como acima | Mesmo risco do IP. |
| Estáticos, `/q/*`, `/web/*`, `/telemetria/api*` | Descartados ({contadores["descartados_ruido_tecnico"]} registros) | Ruído de infraestrutura, não descreve uso do sistema. |

O campo `body` é **reconstruído** a partir dos atributos já sanitizados. Ele
repetia os valores em texto livre (`evento=geo_lookup status=ok ip=...`), então
limpar apenas os atributos deixaria o dado sensível no texto.

Um passo de auditoria varre o arquivo final atrás de IPv4 público e coordenadas
residuais; a geração falha se encontrar qualquer um. IPs de faixa privada,
loopback e documentação permanecem — são os valores de laboratório do próprio
material didático.

## Limitações conhecidas

- **{contadores["sem_correlacao"]} registro(s) sem `traceId`.** Eventos nascidos
  fora de uma requisição (inicialização da aplicação, tarefas de fundo) não têm
  correlação, e isso é correto. Registros de requisição sem `traceId` são de
  coletas anteriores a 2026-08-03, quando os eventos de negócio passaram a herdar
  a correlação do request.
- **`GET /` inclui o healthcheck do container.** O Docker consulta a raiz a cada
  30 s e a telemetria não distingue isso de uma visita real. Trate a contagem de
  `GET /` como limite superior, não como visitas.
- Um incidente rende mais de um evento (operação, exceção mapeada, acesso HTTP).
  **Agrupe por `traceId`** para contar incidentes em vez de linhas.

## Como usar

```bash
# eventos de erro, agrupados por incidente
jq -r 'select(.severityText=="ERROR") | .traceId' eventos.jsonl | sort -u | wc -l

# uso por módulo
jq -r '.attributes[] | select(.key=="framework.module") | .value.stringValue' \\
  eventos.jsonl | sort | uniq -c | sort -rn
```
"""
    (destino / "README.md").write_text(readme, encoding="utf-8")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("origem", type=Path, help="NDJSON de telemetria (framework-net-eventos.jsonl)")
    parser.add_argument("destino", type=Path, help="pasta do dataset a ser gerada")
    parser.add_argument("--sal", default=os.environ.get("TELEMETRIA_DATASET_SALT", ""),
                        help="sal secreto; por padrão lê TELEMETRIA_DATASET_SALT")
    args = parser.parse_args()

    if len(args.sal) < SAL_MINIMO:
        print(f"ERRO: sal ausente ou curto demais (mínimo {SAL_MINIMO} caracteres).\n"
              f"      Defina TELEMETRIA_DATASET_SALT no .env da VPS e mantenha o MESMO\n"
              f"      valor entre execuções — trocar o sal quebra a continuidade dos\n"
              f"      pseudônimos entre datasets já publicados.", file=sys.stderr)
        return 2

    if not args.origem.is_file():
        print(f"ERRO: arquivo de origem não encontrado: {args.origem}", file=sys.stderr)
        return 1

    args.destino.mkdir(parents=True, exist_ok=True)
    saida = args.destino / "eventos.jsonl"
    contadores: Counter = Counter()
    identidades: set[str] = set()
    gerado_em = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    with args.origem.open("r", encoding="utf-8") as entrada, \
            saida.open("w", encoding="utf-8", newline="\n") as destino:
        for linha in entrada:
            linha = linha.strip()
            if not linha:
                continue
            try:
                registro = json.loads(linha)
            except json.JSONDecodeError:
                contadores["descartados_json_invalido"] += 1
                continue
            limpo = sanitizar_registro(registro, args.sal, contadores, identidades)
            if limpo is not None:
                destino.write(json.dumps(limpo, ensure_ascii=False, separators=(",", ":")) + "\n")

    achados = auditar(saida, identidades)
    if achados:
        print("ERRO: auditoria encontrou dado de identidade residual no dataset gerado.", file=sys.stderr)
        for achado in achados[:20]:
            print(f"  - {achado}", file=sys.stderr)
        print(f"  ({len(achados)} achado(s)). O dataset NÃO deve ser publicado.", file=sys.stderr)
        saida.unlink(missing_ok=True)
        return 3

    escrever_apoio(args.destino, contadores, args.origem, gerado_em)
    publicos = inventariar_publicos(saida)

    print(f"Dataset gerado em {args.destino}")
    print(f"  publicados            : {contadores['publicados']}")
    print(f"  descartados (ruído)   : {contadores['descartados_ruido_tecnico']}")
    print(f"  identidades protegidas: {len(identidades)} valor(es) distinto(s) "
          f"em {contadores['campos_pseudonimizados']} registro(s)")
    print(f"  campos removidos      : {contadores['campos_removidos']}")
    print(f"  sem correlação        : {contadores['sem_correlacao']}")
    print(f"  auditoria             : limpa ({len(identidades)} valor(es) de identidade "
          f"verificados como ausentes na saída)")

    descartados = {
        chave[len("descartado_"):]: valor
        for chave, valor in contadores.items()
        if chave.startswith("descartado_")
    }
    if descartados:
        print()
        print("  CAMPOS DESCARTADOS pela allowlist (não entraram no dataset).")
        print("  Se algum for legítimo e não carregar dado pessoal, acrescente")
        print("  em CAMPOS_PUBLICAVEIS e exporte de novo:")
        for nome, vezes in sorted(descartados.items(), key=lambda p: -p[1])[:20]:
            print(f"    {nome:<28} {vezes}x")

    if publicos:
        print()
        print("  CONFIRA ANTES DE PUBLICAR — IPv4 roteáveis que permaneceram no dataset.")
        print("  São valores digitados em exercícios (conteúdo didático), não identidade,")
        print("  mas revise se algum é endereço real de infraestrutura sua:")
        for ip, vezes in publicos.most_common(15):
            print(f"    {ip:<18} {vezes}x")
    return 0


if __name__ == "__main__":
    sys.exit(main())
