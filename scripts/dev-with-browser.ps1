param(
    [int]$Port = 8080
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$url = "http://127.0.0.1:$Port/"
$waiter = Join-Path $PSScriptRoot "wait-and-open-browser.ps1"
$staleConfig = Join-Path $root "build\resources\main\application-dev.properties"

if (Test-Path $staleConfig) {
    Remove-Item $staleConfig -Force
    Write-Host "Removido application-dev.properties obsoleto do build." -ForegroundColor Yellow
}

# Encerra daemons Gradle e libera o JAR de dev preso no build/
& "$root\gradlew.bat" --stop 2>$null | Out-Null

Start-Process -FilePath "powershell.exe" -WindowStyle Hidden -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-File", $waiter,
    "-Port", $Port,
    "-Url", $url
) | Out-Null

Write-Host "Iniciando Quarkus dev em $url (navegador abre automaticamente quando a porta responder)..." -ForegroundColor Cyan

& "$root\gradlew.bat" --console=plain quarkusDev `
    "-Dquarkus.http.port=$Port" `
    "-Dframework.dev.open-browser=false" `
    "-Djava.awt.headless=false"

exit $LASTEXITCODE
