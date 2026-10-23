param(
    [Parameter(Mandatory = $true)]
    [int]$Port,
    [Parameter(Mandatory = $true)]
    [string]$Url
)

$ErrorActionPreference = "SilentlyContinue"

for ($i = 0; $i -lt 120; $i++) {
  try {
    $client = New-Object System.Net.Sockets.TcpClient
    $client.Connect("127.0.0.1", $Port)
    $client.Close()
    Start-Process $Url
    exit 0
  } catch {
    Start-Sleep -Seconds 1
  }
}

exit 1
