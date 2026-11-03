param([int]$Port = 8088)
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$demoEnvPath = Join-Path $projectDirectory '.env.demo'
if ($Port -lt 1 -or $Port -gt 65535) { throw 'Invalid port.' }
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { throw 'Install Docker Desktop and start it first.' }
& docker info --format '{{.ServerVersion}}'
if ($LASTEXITCODE -ne 0) { throw 'Start Docker Desktop, then run this script again.' }
function New-Secret {
    $bytes = New-Object byte[] 32
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($bytes) } finally { $rng.Dispose() }
    return ([BitConverter]::ToString($bytes)).Replace('-', '').ToLowerInvariant()
}
if (-not (Test-Path -LiteralPath $demoEnvPath)) {
    $lines = @('DB_NAME=bishe', 'DB_USERNAME=bishe_app', ('DB_PASSWORD=' + (New-Secret)), ('MYSQL_ROOT_PASSWORD=' + (New-Secret)), ('REDIS_PASSWORD=' + (New-Secret)), ('TOKEN_SECRET=' + (New-Secret)), ('FRONTEND_PORT=' + $Port), ('CORS_ALLOWED_ORIGINS=http://localhost:' + $Port), 'CAPTCHA_EXPOSE_CODE=false', 'SWAGGER_ENABLED=false')
    [IO.File]::WriteAllLines($demoEnvPath, $lines, (New-Object Text.UTF8Encoding($false)))
    Write-Host 'Created private .env.demo with fresh random secrets.'
} else {
    Write-Host 'Using existing .env.demo; secrets and saved data are preserved.'
}
Push-Location $projectDirectory
try {
    & docker compose --env-file $demoEnvPath --project-name bishe-demo up -d --build --wait
    if ($LASTEXITCODE -ne 0) { throw 'Docker startup failed; see the output above.' }
    $savedPort = ((Get-Content -LiteralPath $demoEnvPath | Where-Object { $_ -match '^FRONTEND_PORT=' }) -split '=', 2)[1]
    Write-Host "Open http://localhost:$savedPort and register your own accounts. No local MySQL data is imported."
} finally { Pop-Location }
