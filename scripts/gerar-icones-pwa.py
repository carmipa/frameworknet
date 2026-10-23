#!/usr/bin/env python3
"""Gera os icones quadrados do PWA a partir do banner do projeto.

PROPOSITO DE NEGOCIO
    O `icone.png` do projeto e um banner 1376x768. O Chrome so oferece instalar
    o site (o botao na barra de endereco) quando o manifest aponta icones
    QUADRADOS de 192 e 512 px. Sem eles nao ha instalacao nem atalho na area de
    trabalho.

INVARIANTES DO DOMINIO
    - O banner nunca e cortado: e reduzido e centralizado sobre um fundo quadrado
      na cor do tema escuro da aplicacao, preservando a arte inteira.
    - Gera duas familias: `any` (arte ocupando a tela toda) e `maskable`, esta com
      20% de margem de seguranca, porque Android recorta o icone em circulo ou
      squircle e comeria a arte sem essa folga.

COMPORTAMENTO EM CASO DE FALHA
    Aborta com codigo 1 se o banner de origem nao existir. Reexecutar sobrescreve
    os arquivos gerados; e idempotente.

USO
    python scripts/gerar-icones-pwa.py
"""

import sys
from pathlib import Path

from PIL import Image

RAIZ = Path(__file__).resolve().parent.parent
ORIGEM = RAIZ / "src/main/resources/META-INF/resources/icone.png"
DESTINO = RAIZ / "src/main/resources/META-INF/resources/pwa"

# Fundo igual ao --aed-bg do design system, para o icone nao destoar do app.
FUNDO = (8, 11, 20, 255)

TAMANHOS = [192, 512]
MARGEM_MASKABLE = 0.20  # 20% de folga: Android recorta o icone


def gerar(banner: Image.Image, lado: int, margem: float, saida: Path) -> None:
    """Centraliza o banner num quadrado de `lado`px com a margem pedida."""
    tela = Image.new("RGBA", (lado, lado), FUNDO)
    util = int(lado * (1 - 2 * margem))

    copia = banner.copy()
    copia.thumbnail((util, util), Image.LANCZOS)

    tela.paste(copia, ((lado - copia.width) // 2, (lado - copia.height) // 2), copia)
    tela.save(saida, "PNG", optimize=True)
    print(f"  {saida.name:<28} {lado}x{lado}  {saida.stat().st_size // 1024} KB")


def main() -> int:
    if not ORIGEM.is_file():
        print(f"ERRO: banner de origem nao encontrado: {ORIGEM}", file=sys.stderr)
        return 1

    DESTINO.mkdir(parents=True, exist_ok=True)
    banner = Image.open(ORIGEM).convert("RGBA")
    print(f"Origem: {ORIGEM.name} ({banner.width}x{banner.height})")

    for lado in TAMANHOS:
        gerar(banner, lado, 0.06, DESTINO / f"icone-{lado}.png")
        gerar(banner, lado, MARGEM_MASKABLE, DESTINO / f"icone-{lado}-maskable.png")

    return 0


if __name__ == "__main__":
    sys.exit(main())
