param(
    [ValidateSet('status', 'stop')]
    [string]$Mode = 'status'
)

$ErrorActionPreference = 'Stop'
$projectDirectory = [IO.Path]::GetFullPath((Split-Path -Parent $PSScriptRoot))
$projectPrefix = $projectDirectory.Replace('\', '/').TrimEnd('/') + '/'
$frontendPrefix = $projectPrefix + 'bishe-qianduan/'
$backendPrefix = $projectPrefix + 'bishe/'

function Get-ProjectRole($Process) {
    $command = [string]$Process.CommandLine
    # IDEA may put the classpath in a Java argument file. Read it only for
    # identification; never print command lines, which may contain credentials.
    if ($Process.Name -match '^javaw?\.exe$') {
        foreach ($match in [regex]::Matches($command, '(?:^|\s)(?:"@([^"]+)"|@"([^"]+)"|@([^\s"]+))')) {
            $argumentFile = if ($match.Groups[1].Success) { $match.Groups[1].Value } elseif ($match.Groups[2].Success) { $match.Groups[2].Value } else { $match.Groups[3].Value }
            try {
                if ((Test-Path -LiteralPath $argumentFile -PathType Leaf) -and (Get-Item -LiteralPath $argumentFile).Length -lt 1MB) {
                    $command += ' ' + [IO.File]::ReadAllText($argumentFile)
                }
            } catch {
                # Another user's Java argfile may be private or already removed.
                # Leave that process unidentified rather than aborting the scan.
            }
        }
    }
    $normalized = $command.Replace('\', '/').Replace('//', '/').ToLowerInvariant()
    if ($Process.Name -ieq 'node.exe' -and $normalized.Contains($frontendPrefix.ToLowerInvariant()) -and $normalized -match '(?:/vite/|/vite\.|\bvite\b)') {
        return 'frontend'
    }
    if ($Process.Name -match '^javaw?\.exe$' -and $normalized.Contains($backendPrefix.ToLowerInvariant()) -and
        $normalized -match '(com\.example\.bishe\.bisheapplication|/target/classes|bishe[^/\s"]*\.jar|spring-boot:run)') {
        return 'backend'
    }
    return $null
}

try {
    $processes = @(Get-CimInstance Win32_Process)
    $listeners = @(Get-NetTCPConnection -State Listen -ErrorAction SilentlyContinue)
    $knownPorts = @(5173, 5174, 8080)
    $envPath = Join-Path $projectDirectory '.env'
    if (Test-Path -LiteralPath $envPath) {
        foreach ($line in Get-Content -LiteralPath $envPath -Encoding UTF8) {
            if ($line -match '^\s*(SERVER_PORT|VITE_DEV_PORT)\s*=\s*(\d+)\s*$') { $knownPorts += [int]$matches[2] }
        }
    }
    foreach ($key in @('SERVER_PORT', 'VITE_DEV_PORT')) {
        $value = [Environment]::GetEnvironmentVariable($key)
        if ($value -match '^\d+$') { $knownPorts += [int]$value }
    }
    $targets = @()
    foreach ($process in $processes) {
        $ports = @($listeners | Where-Object OwningProcess -eq $process.ProcessId | Select-Object -ExpandProperty LocalPort -Unique)
        $role = Get-ProjectRole $process
        if ($role -and $ports.Count -gt 0) {
            $targets += [pscustomobject]@{ PID = $process.ProcessId; Role = $role; Ports = ($ports -join ','); Created = $process.CreationDate }
        } elseif (@($ports | Where-Object { $_ -in $knownPorts }).Count -gt 0) {
            Write-Host "SKIP: PID $($process.ProcessId) ($($process.Name)), ports $($ports -join ','): ownership could not be verified."
        }
    }
    if ($targets.Count -eq 0) {
        Write-Host 'No running frontend/backend listener belonging to this checkout was found.'
        exit 0
    }
    $targets | Select-Object PID, Role, Ports | Format-Table -AutoSize | Out-Host
    if ($Mode -eq 'status') { exit 0 }
    $failed = $false
    foreach ($target in $targets) {
        # Recheck identity immediately before stopping to avoid a recycled PID.
        $current = Get-CimInstance Win32_Process -Filter "ProcessId=$($target.PID)"
        if (-not $current) { continue }
        if ($current.CreationDate -ne $target.Created -or (Get-ProjectRole $current) -ne $target.Role) {
            Write-Host "SKIP: PID $($target.PID) changed identity."
            $failed = $true
            continue
        }
        try {
            # Stop only the listener, not an IDE, Maven, or unrelated process tree.
            Stop-Process -Id $target.PID -Force -ErrorAction Stop
            Wait-Process -Id $target.PID -Timeout 10 -ErrorAction SilentlyContinue
            $remaining = Get-Process -Id $target.PID -ErrorAction SilentlyContinue
            if ($remaining -and -not $remaining.HasExited) { throw 'Process did not exit.' }
            Write-Host "Stopped $($target.Role): PID $($target.PID), ports $($target.Ports)."
        } catch {
            Write-Host "ERROR stopping PID $($target.PID): $($_.Exception.Message)"
            $failed = $true
        }
    }
    if ($failed) { exit 1 }
    Write-Host 'Project frontend/backend stopped. Redis and MySQL were kept running.'
    exit 0
} catch {
    Write-Host ('ERROR: ' + $_.Exception.Message) -ForegroundColor Red
    exit 1
}
