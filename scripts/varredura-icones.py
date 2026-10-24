# -*- coding: utf-8 -*-
# varredura-icones.py v1.0 (2026-10-24) — componente compartilhado: não editar no projeto; mudança só na origem.
"""Varredura estática dos rótulos sem ícone nos templates HTML de um projeto.

Percorre todo .html abaixo da raiz informada (pula node_modules, .git, target, build) e lista, para cada
label, th, caption, legend, button, h1-h6, summary e link que age como botão/menu/aba, se há ícone:
  - DENTRO do elemento: Material Symbols/Icons, <i>/<span> com classe de ícone (icon, ico, fic, icone, glifo,
    bi-, fa-, ms-, lucide, feather), <svg>, <img>, emoji no texto, ou atributo data-icon no próprio elemento;
  - ADJACENTE: ícone imediatamente antes do elemento, no mesmo bloco (<span class="x-icone"><svg/></span><label>).
O gatilho "?" de ajuda (classe dica__gatilho/dica__texto, glifo help) e o balão de tooltip NÃO contam como ícone.

Marcações no HTML que a varredura respeita:
  data-icone-excecao="motivo"   -> elemento fora da contagem (listado como exceção; o motivo tem de estar registrado)
  data-titulo-editorial         -> título (h1-h6) que é frase para ler, não nome de área; fica fora da contagem

Limitações declaradas: não enxerga o que o JavaScript monta em tempo de execução nem ícone posto só por CSS
(::before). Para isso existe a varredura no DOM real (icone-varredura.js + icone-rotas.spec.mjs).

Uso:
  python varredura-icones.py <raiz-dos-templates> [--csv saida.csv] [--nome rotulo] [--falhar]
  --falhar  termina com código 1 se houver elemento do escopo sem ícone (para usar como guarda).
Saída: tabela por tipo de elemento (total, com ícone dentro, adjacente, SEM, %), os 10 arquivos com mais faltas,
e um CSV com uma linha por elemento (arquivo;linha;elemento;icone;dica;texto).
"""
import sys, os, re, csv, argparse
from html.parser import HTMLParser

ALVOS = {'label', 'th', 'caption', 'legend', 'button', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'summary'}
RE_CLASSE_ICONE = re.compile(r'(material-symbols|material-icons|\bicon\b|\bico\b|\bfic\b|icone|glifo|\bbi-|\bfa-|\bms-|lucide|feather)', re.I)
RE_EMOJI = re.compile('[\U0001F300-\U0001FAFF\u2600-\u27BF\u2B00-\u2BFF\u2190-\u21FF]')
RE_DICA_TXT = re.compile(r'\{#dica(Ajuda)?\b', re.I)
# Só o gatilho e o balão da dica são "dica pura". O invólucro class="dica" envolve o controle e não exclui nada.
RE_CLASSE_DICA = re.compile(r'dica__gatilho|dica__texto|dica-gatilho|dica-texto|\btooltip\b', re.I)
RE_CLASSE_LINK = re.compile(r'(\bbtn\b|button|\bnav\b|menu|\btab\b|\baba\b|acao)', re.I)
GLIFOS_DICA = {'help', 'help_outline', 'question_mark', 'info_outline'}
ADJ = 250  # caracteres: distância máxima do ícone anterior para contar como adjacente


class Varredura(HTMLParser):
    def __init__(self, arq, txt):
        super().__init__(convert_charrefs=True)
        self.arq, self.txt = arq, txt
        self.pilha, self.linhas = [], []
        self.icone_aberto, self._dica_tags = [], []
        self.ult_icone_off, self.ult_fecha_off = -10**9, -10**9
        self.ini = [0]
        for x in txt.split('\n'):
            self.ini.append(self.ini[-1] + len(x) + 1)

    def off(self):
        l, c = self.getpos()
        return self.ini[l - 1] + c

    def em_dica(self):
        return bool(self._dica_tags)

    def marca_icone(self):
        if self.em_dica():
            for q in self.pilha:
                q['dica'] = True
            return
        for q in self.pilha:
            q['icone'] = 'sim'
        self.ult_icone_off = self.off()

    def handle_starttag(self, tag, attrs):
        a = dict(attrs)
        cls = a.get('class', '') or ''
        eh_dica = bool(RE_CLASSE_DICA.search(cls)) or a.get('role') == 'tooltip'
        if eh_dica:
            self._dica_tags.append(tag)
            for q in self.pilha:
                q['dica'] = True
        eh_icone = tag in ('svg', 'img') or (tag in ('span', 'i', 'em') and RE_CLASSE_ICONE.search(cls))
        if eh_icone:
            antes = [q['icone'] for q in self.pilha]
            self.marca_icone()
            if tag in ('span', 'i', 'em'):
                self.icone_aberto.append({'tag': tag, 'txt': '', 'antes': antes})
        alvo = (not eh_dica) and (tag in ALVOS or (tag == 'a' and RE_CLASSE_LINK.search(cls)))
        if alvo:
            ic = 'nao'
            if any(k.startswith('data-icon') or k in ('data-icone', 'data-glifo') for k in a):
                ic = 'sim'
            elif self.ult_icone_off > self.ult_fecha_off and self.off() - self.ult_icone_off <= ADJ:
                ic = 'adj'
            if 'data-icone-excecao' in a:
                ic = 'excecao'
            elif tag[0] == 'h' and 'data-titulo-editorial' in a:
                ic = 'editorial'
            dica = ('title' in a) or ('data-tooltip' in a) or ('data-dica' in a)
            self.pilha.append({'tag': tag, 'linha': self.getpos()[0], 'icone': ic, 'dica': dica, 'texto': ''})

    def handle_data(self, d):
        if self.icone_aberto:
            self.icone_aberto[-1]['txt'] += d
        if not self.pilha:
            return
        if RE_EMOJI.search(d) and not self.em_dica():
            for q in self.pilha:
                if q['icone'] in ('nao', 'adj'):
                    q['icone'] = 'sim'
        if RE_DICA_TXT.search(d):
            for q in self.pilha:
                q['dica'] = True
        self.pilha[-1]['texto'] += d

    def handle_endtag(self, tag):
        if self.icone_aberto and self.icone_aberto[-1]['tag'] == tag:
            ab = self.icone_aberto.pop()
            if ab['txt'].strip() in GLIFOS_DICA:  # era o "?" de ajuda, não ícone de rótulo: volta ao estado anterior
                for q, a0 in zip(self.pilha, ab['antes']):
                    q['icone'] = a0
                    q['dica'] = True
                self.ult_icone_off = -10**9
        if self._dica_tags and self._dica_tags[-1] == tag:
            self._dica_tags.pop()
        for i in range(len(self.pilha) - 1, -1, -1):
            if self.pilha[i]['tag'] == tag:
                q = self.pilha.pop(i)
                txt = re.sub(r'\{[^}]*\}', '', q['texto'])
                txt = re.sub(r'\s+', ' ', txt).strip()
                self.linhas.append((self.arq, q['linha'], q['tag'], q['icone'], 'sim' if q['dica'] else '', txt[:60]))
                self.ult_fecha_off = self.off()
                break


def varrer(raiz):
    tudo = []
    for dp, dn, fn in os.walk(raiz):
        dn[:] = [d for d in dn if d not in ('node_modules', '.git', 'target', 'build')]
        for f in sorted(fn):
            if not f.endswith('.html'):
                continue
            p = os.path.join(dp, f)
            try:
                txt = open(p, encoding='utf-8', errors='replace').read()
            except OSError:
                continue
            v = Varredura(os.path.relpath(p, raiz), txt)
            try:
                v.feed(txt)
            except Exception as e:  # HTML quebrado não derruba a varredura inteira
                print('erro ao ler', p, e, file=sys.stderr)
            tudo.extend(v.linhas)
    return tudo


def resumo(nome, tudo):
    grupos = {}
    for arq, ln, tag, ic, dc, tx in tudo:
        g = 'h1-h6' if tag[0] == 'h' and tag != 'header' else tag
        t = grupos.setdefault(g, {'total': 0, 'sim': 0, 'adj': 0, 'nao': 0, 'excecao': 0, 'editorial': 0, 'dica': 0})
        t['total'] += 1
        t[ic] += 1
        t['dica'] += (dc == 'sim')
    print(f'\n## {nome}')
    print(f"{'elemento':9} {'total':>6} {'dentro':>7} {'adjac':>6} {'SEM':>6} {'%sem':>5} {'exceção':>8} {'editorial':>10} {'c/dica':>7}")
    tot = {'total': 0, 'sim': 0, 'adj': 0, 'nao': 0, 'excecao': 0, 'editorial': 0, 'dica': 0}
    for k in ['label', 'th', 'caption', 'legend', 'button', 'a', 'h1-h6', 'summary']:
        if k in grupos:
            t = grupos[k]
            for c in tot:
                tot[c] += t[c]
            base = t['total'] - t['excecao'] - t['editorial']
            print(f"{k:9} {t['total']:6} {t['sim']:7} {t['adj']:6} {t['nao']:6} {100 * t['nao'] // base if base else 0:4}% {t['excecao']:8} {t['editorial']:10} {t['dica']:7}")
    base = tot['total'] - tot['excecao'] - tot['editorial']
    print(f"{'TOTAL':9} {tot['total']:6} {tot['sim']:7} {tot['adj']:6} {tot['nao']:6} {100 * tot['nao'] // base if base else 0:4}% {tot['excecao']:8} {tot['editorial']:10} {tot['dica']:7}")
    por_arq = {}
    for arq, ln, tag, ic, dc, tx in tudo:
        if ic == 'nao':
            por_arq[arq] = por_arq.get(arq, 0) + 1
    if por_arq:
        print('\narquivos com mais rótulos sem ícone:')
        for arq, n in sorted(por_arq.items(), key=lambda x: -x[1])[:10]:
            print(f'  {n:4}  {arq}')
    return tot['nao']


if __name__ == '__main__':
    ap = argparse.ArgumentParser(description='Rótulos sem ícone nos templates HTML de um projeto.')
    ap.add_argument('raiz', help='pasta dos templates (ex.: src/main/resources/templates)')
    ap.add_argument('--csv', help='arquivo CSV de saída (uma linha por elemento)')
    ap.add_argument('--nome', default=None, help='nome do projeto no cabeçalho do resumo')
    ap.add_argument('--falhar', action='store_true', help='código de saída 1 se houver rótulo sem ícone')
    args = ap.parse_args()
    linhas = varrer(args.raiz)
    if args.csv:
        with open(args.csv, 'w', newline='', encoding='utf-8') as fh:
            w = csv.writer(fh, delimiter=';')
            w.writerow(['arquivo', 'linha', 'elemento', 'icone', 'tem_dica', 'texto'])
            w.writerows(linhas)
    faltam = resumo(args.nome or os.path.basename(os.path.abspath(args.raiz)), linhas)
    sys.exit(1 if (args.falhar and faltam) else 0)
