<#
.SYNOPSIS
    Sobe o Framework de Redes localmente, sempre via Docker.

.DESCRIPTION
    PROPOSITO: ter UM jeito de rodar o projeto, igual ao de producao. Rodar
    `gradlew quarkusDev` na maquina usa outro caminho — sem Redis, sem as
    variaveis do container, com outro perfil — e esconde justamente os defeitos
    que so aparecem em producao. Este projeto ja pagou duas vezes por isso: a
    variavel do OAuth que nao chegava ao container, e a telemetria sem Redis.

    INVARIANTES: usa a mesma imagem e o mesmo compose que a producao usa; nunca
    inventa segredo de producao (os defaults de dev sao explicitamente de dev);
    espera o healthcheck antes de dizer que subiu.

    COMPORTAMENTO EM CASO DE FALHA: se o Docker nao estiver rodando, avisa e
    para — nao tenta caminho alternativo, porque cair para `quarkusDev` em
    silencio seria voltar ao problema que este script existe para resolver. Se
    o healthcheck nao passar no prazo, mostra os ultimos logs e sai com codigo 1.

.PARAMETER Porta
    Porta no host. Padrao 8081.

.PARAMETER SemBrowser
    Nao abre o navegador ao final.

.PARAMETER Recriar
    Reconstroi a imagem do zero (--no-cache) e recria os containers.

.EXAMPLE
    .\scripts\subir.ps1
    .\scripts\subir.ps1 -Porta 8090 -Recriar
#>
[CmdletBinding()]
param(
    [int]$Porta = 8081,
    [switch]$SemBrowser,
    [switch]$Recriar
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

$compose = 'docker-compose.dev.yml'
$env:HTTP_PORT = "$Porta"

Write-Host ''
Write-Host '  Framework de Redes — subindo via Docker' -ForegroundColor Cyan
Write-Host '  ---------------------------------------' -ForegroundColor DarkGray

# --- 1. Docker esta de pe? -------------------------------------------------
try {
    docker info 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'daemon nao respondeu' }
} catch {
    Write-Host ''
    Write-Host '  Docker nao esta rodando.' -ForegroundColor Red
    Write-Host '  Abra o Docker Desktop, espere ficar verde e rode de novo.' -ForegroundColor Yellow
    Write-Host ''
    Write-Host '  Nao vou cair para gradlew quarkusDev: ambiente diferente do de' -ForegroundColor DarkGray
    Write-Host '  producao esconde defeito, e e por isso que este script existe.' -ForegroundColor DarkGray
    exit 1
}
Write-Host '  [ok] Docker respondendo' -ForegroundColor Green

# --- 1.5. Segredos locais --------------------------------------------------
# O compose de dev roda com QUARKUS_PROFILE=prod de proposito (espelhar producao),
# e o SegredosObrigatoriosVerificador RECUSA iniciar em prod com o valor de dev
# `dev-admin-key-local`, que e publico no repositorio. Sem este bloco o container
# morre no boot e este script fica esperando um healthcheck que nunca vem — foi
# exatamente o que aconteceu numa maquina limpa em 2026-08-11.
#
# A saida NAO e afrouxar a guarda nem versionar outro literal (seria o mesmo
# buraco com outro nome): e gerar segredo forte por maquina num `.env` local, que
# o .gitignore ja mantem fora do git. Mesmo mecanismo do scripts/deploy.sh.
$envFile = Join-Path $raiz '.env'
function New-SegredoForte {
    $bytes = [byte[]]::new(32)
    [System.Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}
if (-not (Test-Path $envFile)) {
    @(
        "# Gerado por scripts/subir.ps1 — segredos LOCAIS desta maquina.",
        "# Fora do git (.gitignore). Nao sao os segredos da VPS.",
        "ADMIN_API_KEY=$(New-SegredoForte)",
        "CSRF_SECRET=$(New-SegredoForte)"
    ) | Set-Content -Path $envFile -Encoding UTF8
    Write-Host '  [ok] .env local criado com segredos fortes' -ForegroundColor Green
} else {
    $conteudo = Get-Content $envFile -Raw
    $faltando = @()
    foreach ($chave in @('ADMIN_API_KEY', 'CSRF_SECRET')) {
        if ($conteudo -notmatch "(?m)^$chave=\S") { $faltando += $chave }
    }
    if ($faltando.Count -gt 0) {
        foreach ($chave in $faltando) { Add-Content $envFile "$chave=$(New-SegredoForte)" }
        Write-Host "  [ok] .env completado ($($faltando -join ', '))" -ForegroundColor Green
    } else {
        Write-Host '  [ok] .env local presente' -ForegroundColor Green
    }
}

# --- 2. Subir --------------------------------------------------------------
if ($Recriar) {
    Write-Host '  [..] Reconstruindo do zero (--no-cache)' -ForegroundColor DarkGray
    docker compose -f $compose build --no-cache
    if ($LASTEXITCODE -ne 0) { Write-Host '  Falha no build.' -ForegroundColor Red; exit 1 }
    docker compose -f $compose up -d --force-recreate
} else {
    Write-Host '  [..] Construindo e subindo' -ForegroundColor DarkGray
    docker compose -f $compose up -d --build
}
if ($LASTEXITCODE -ne 0) { Write-Host '  Falha ao subir os containers.' -ForegroundColor Red; exit 1 }

# --- 3. Esperar o healthcheck ---------------------------------------------
Write-Host '  [..] Aguardando a aplicacao responder' -ForegroundColor DarkGray
# /health: fora da telemetria (bater em / a cada 3 s enchia o painel no arranque).
$url = "http://127.0.0.1:$Porta/health"
$limite = (Get-Date).AddSeconds(150)
$pronto = $false

while ((Get-Date) -lt $limite) {
    try {
        $r = Invoke-WebRequest -Uri $url -UseBasicParsing -TimeoutSec 5 -ErrorAction Stop
        if ($r.StatusCode -eq 200) { $pronto = $true; break }
    } catch {
        Start-Sleep -Seconds 3
    }
}

if (-not $pronto) {
    Write-Host ''
    Write-Host '  A aplicacao nao respondeu no prazo. Ultimos logs:' -ForegroundColor Red
    docker compose -f $compose logs --tail=40 framework-net
    exit 1
}

# --- 4. Pronto -------------------------------------------------------------
Write-Host '  [ok] Aplicacao no ar' -ForegroundColor Green
Write-Host ''
docker compose -f $compose ps --format 'table {{.Name}}\t{{.Status}}\t{{.Ports}}'
Write-Host ''
Write-Host "  Aplicacao ....... $url" -ForegroundColor Cyan
$chaveLocal = ((Get-Content $envFile | Select-String -Pattern '^ADMIN_API_KEY=(.+)$').Matches.Groups[1].Value)
Write-Host "  Telemetria ...... ${url}telemetria  (login: contingencia)" -ForegroundColor Cyan
Write-Host "  Chave local ..... $chaveLocal" -ForegroundColor DarkGray
Write-Host '                    (esta no .env desta maquina; nao e a da VPS)' -ForegroundColor DarkGray
Write-Host ''
Write-Host '  Parar ........... .\scripts\parar.ps1' -ForegroundColor DarkGray
Write-Host '  Logs ao vivo .... docker compose -f docker-compose.dev.yml logs -f framework-net' -ForegroundColor DarkGray
Write-Host ''

if (-not $SemBrowser) {
    Start-Process $url
}
