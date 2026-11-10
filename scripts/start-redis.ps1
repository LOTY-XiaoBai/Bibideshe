param(
    [string]$RedisDirectory = ''
)

# Local development helper. No service installation or administrator rights needed.
$ErrorActionPreference = 'Stop'
$projectDirectory = Split-Path -Parent $PSScriptRoot
$settings = @{}
$envPath = Join-Path $projectDirectory '.env'
if (Test-Path -LiteralPath $envPath) {
    foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
        if ($line -match '^\s*(REDIS_HOST|REDIS_PORT|REDIS_PASSWORD|REDIS_DIRECTORY)\s*=(.*)$') {
            $settings[$matches[1]] = $matches[2].Trim()
        }
    }
}

$redisHost = if ($env:REDIS_HOST) { $env:REDIS_HOST } elseif ($settings.ContainsKey('REDIS_HOST')) { $settings['REDIS_HOST'] } else { 'localhost' }
$redisPort = if ($env:REDIS_PORT) { [int]$env:REDIS_PORT } elseif ($settings.ContainsKey('REDIS_PORT')) { [int]$settings['REDIS_PORT'] } else { 6379 }
$redisPassword = if (Test-Path Env:REDIS_PASSWORD) { $env:REDIS_PASSWORD } elseif ($settings.ContainsKey('REDIS_PASSWORD')) { $settings['REDIS_PASSWORD'] } else { '' }

function Send-RedisCommand($Stream, $Reader, [string[]]$Command) {
    $wire = '*' + $Command.Length + "`r`n"
    foreach ($part in $Command) {
        $wire += '$' + [Text.Encoding]::UTF8.GetByteCount($part) + "`r`n" + $part + "`r`n"
    }
    $bytes = [Text.Encoding]::UTF8.GetBytes($wire)
    $Stream.Write($bytes, 0, $bytes.Length)
    $Stream.Flush()
    return $Reader.ReadLine()
}

function Get-RedisStatus {
    $client = New-Object Net.Sockets.TcpClient
    try {
        $connecting = $client.ConnectAsync($redisHost, $redisPort)
        if (-not $connecting.Wait(700)) { return 'offline' }
        $connecting.GetAwaiter().GetResult()
        $stream = $client.GetStream()
        $stream.ReadTimeout = 1000
        $stream.WriteTimeout = 1000
        $reader = New-Object IO.StreamReader($stream, [Text.Encoding]::UTF8)
        if (-not [string]::IsNullOrEmpty($redisPassword)) {
            $auth = Send-RedisCommand $stream $reader @('AUTH', $redisPassword)
            if ($auth -ne '+OK') { return 'auth-error' }
        }
        $reply = Send-RedisCommand $stream $reader @('PING')
        if ($reply -eq '+PONG') { return 'ready' }
        if ($reply -like '-NOAUTH*') { return 'auth-error' }
        return 'port-in-use'
    } catch {
        if ($client.Connected) { return 'port-in-use' }
        return 'offline'
    } finally {
        $client.Dispose()
    }
}

try {
    if ($redisPort -lt 1 -or $redisPort -gt 65535) { throw 'REDIS_PORT must be between 1 and 65535.' }
    $status = Get-RedisStatus
    if ($status -eq 'ready') {
        Write-Host "Redis is already running on port $redisPort. PING = PONG."
        exit 0
    }
    if ($status -eq 'auth-error') {
        throw 'Redis is running, but REDIS_PASSWORD does not match its configuration. Check .env and redis.conf.'
    }
    if ($status -eq 'port-in-use') {
        throw "Port $redisPort is occupied but Redis did not answer PING. No new process was started."
    }
    if ($redisHost -notin @('localhost', '127.0.0.1', '::1')) {
        throw 'Remote Redis is unavailable. Check REDIS_HOST and the remote service; no local instance was started.'
    }
    if (-not $RedisDirectory) {
        if ($env:REDIS_DIRECTORY) { $RedisDirectory = $env:REDIS_DIRECTORY }
        elseif ($settings.ContainsKey('REDIS_DIRECTORY')) { $RedisDirectory = $settings['REDIS_DIRECTORY'] }
        else {
            $installed = Get-Command redis-server.exe -ErrorAction SilentlyContinue
            $RedisDirectory = if ($installed) { Split-Path -Parent $installed.Source } else { 'D:\Redis-123\Redis' }
        }
    }

    $serverPath = Join-Path $RedisDirectory 'redis-server.exe'
    $configPath = Join-Path $RedisDirectory 'redis.conf'
    if (-not (Test-Path -LiteralPath $serverPath -PathType Leaf) -or
        -not (Test-Path -LiteralPath $configPath -PathType Leaf)) {
        throw "Cannot find redis-server.exe or redis.conf in $RedisDirectory. Set REDIS_DIRECTORY in the project .env."
    }
    $logDirectory = Join-Path $projectDirectory 'bishe\logs'
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null
    $runStamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $stdoutPath = Join-Path $logDirectory "redis-$runStamp.log"
    $stderrPath = Join-Path $logDirectory "redis-$runStamp-error.log"
    # This Redis build uses MSYS: pass the config relative to its working directory.
    $process = Start-Process -FilePath $serverPath -ArgumentList 'redis.conf' `
        -WorkingDirectory $RedisDirectory -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath

    for ($attempt = 0; $attempt -lt 20; $attempt++) {
        Start-Sleep -Milliseconds 300
        $status = Get-RedisStatus
        if ($status -eq 'ready') {
            Write-Host "Redis started in background. PID = $($process.Id), port = $redisPort, PING = PONG."
            Write-Host "Startup log: $stdoutPath"
            exit 0
        }
        if ($status -eq 'auth-error') {
            throw 'Redis started, but REDIS_PASSWORD does not match redis.conf. Check the password settings.'
        }
        $process.Refresh()
        if ($process.HasExited) { break }
    }
    throw "Redis was not ready. Check logs: $stdoutPath and $stderrPath"
} catch {
    # A failed startup must not leave behind the process created by this call.
    # An already-running service is never assigned to this variable.
    if ($process) {
        $process.Refresh()
        if (-not $process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue }
    }
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
