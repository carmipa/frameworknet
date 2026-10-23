<#
.SYNOPSIS
    Para o ambiente local do Framework de Redes.

.DESCRIPTION
    PROPOSITO: encerrar os containers sem apagar dados por acidente.

    INVARIANTES: por padrao PRESERVA os volumes. O `-Limpar` apaga tambem os
    dados — e ai vai junto a janela de telemetria guardada no Redis Stream, que
    e exatamente o que ela existe para nao perder. Por isso a remocao de volume
    exige a intencao explicita, nunca acontece de graca.

    COMPORTAMENTO EM CASO DE FALHA: se o Docker nao estiver rodando, informa e
    sai com 0 — nao ha nada para parar.
#>
[CmdletBinding()]
param([switch]$Limpar)

$ErrorActionPreference = 'Stop'
Set-Location (Split-Path -Parent $PSScriptRoot)
$compose = 'docker-compose.dev.yml'

try { docker info 2>&1 | Out-Null } catch {
    Write-Host '  Docker nao esta rodando — nada a parar.' -ForegroundColor Yellow
    exit 0
}

if ($Limpar) {
    Write-Host '  ATENCAO: isto apaga os volumes (dados e janela de telemetria).' -ForegroundColor Yellow
    $r = Read-Host '  Digite APAGAR para confirmar'
    if ($r -ne 'APAGAR') { Write-Host '  Cancelado.' -ForegroundColor DarkGray; exit 0 }
    docker compose -f $compose down -v
    Write-Host '  Containers e volumes removidos.' -ForegroundColor Green
} else {
    docker compose -f $compose down
    Write-Host '  Containers parados. Os dados foram preservados.' -ForegroundColor Green
}
