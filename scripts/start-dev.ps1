# Windows-native orchestration. Importer and application execute inside Linux containers.
$ErrorActionPreference = 'Stop'
# Docker build progress must not request a Windows console handle when output
# is forwarded through PowerShell pipelines or this script runs as a child.
$env:BUILDKIT_PROGRESS = 'plain'
$env:COMPOSE_PROGRESS = 'plain'
$env:COMPOSE_ANSI = 'never'
$env:COMPOSE_BAKE = 'false'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $OutputEncoding
if ($args.Count -gt 1 -or ($args.Count -eq 1 -and $args[0] -cne '--fresh')) { throw 'Usage: start-dev.ps1 [--fresh]' }
$fresh = $args.Count -eq 1
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$project = 'backend-swe-demo'; $image = 'backend-swe-demo-bootstrap:local'
$source = Join-Path $repo 'data/Catalog-Data'
$compose = @('compose','--project-name',$project,'--env-file',(Join-Path $repo '.env.local'),'-f',(Join-Path $repo 'compose.dev.yaml'))
$full = $compose + @('-f',(Join-Path $repo 'compose.bootstrap.yaml'))
$token = [guid]::NewGuid().ToString(); $label = 'demo-bootstrap.invocation'
$lockName = "$project-bootstrap-lock"; $lockId = $null
$workers = New-Object 'System.Collections.Generic.HashSet[string]'
$status = 0
function Native-Arguments([string[]]$Values) {
    # Windows PowerShell's legacy native parser strips unescaped embedded quotes.
    if ($PSVersionTable.PSVersion -lt [version]'7.3' -or $PSNativeCommandArgumentPassing -eq 'Legacy') {
        foreach ($value in $Values) { $value.Replace('"','\"') }
    } else { $Values }
}
function Fail([string]$Message,[int]$Code = 4) { $e = New-Object System.Exception($Message); $e.Data['ExitCode'] = $Code; throw $e }
function D([string[]]$Argv) {
    $native = @(Native-Arguments $Argv)
    & docker @native | Out-Host
    if ($LASTEXITCODE -ne 0) { Fail "Docker command failed (exit $LASTEXITCODE); inspect the reported stage." 5 }
}
function O([string[]]$Argv) {
    $native = @(Native-Arguments $Argv)
    $out = & docker @native
    if ($LASTEXITCODE -ne 0) { Fail "Docker query failed (exit $LASTEXITCODE)." 5 }
    return (@($out) -join "`n").Trim()
}
function Pipe-D([string]$Data,[string[]]$Argv) {
    $native = @(Native-Arguments $Argv)
    $out = $Data | & docker @native
    if ($LASTEXITCODE -ne 0) { Fail 'Docker input verification failed.' 4 }
    return (@($out) -join "`n").Trim()
}
function Label([string]$Id,[string]$Key) { O @('inspect','--format',('{{index .Config.Labels "' + $Key + '"}}'),$Id) }
function Assert-Owned([string]$Id) { if ((Label $Id 'com.docker.compose.project') -cne $project) { Fail 'Container belongs to another project.' } }
function Service-Id([string]$Name) {
    $ids = O ($compose + @('ps','--all','--quiet',$Name))
    if ($ids -match "`n") { Fail 'More than one container for a dedicated service.' }
    return $ids
}
function Container-Absent([string]$Name) { return ($Name -cnotin ((O @('container','ls','-a','--format','{{.Names}}')) -split "`n")) }
function Volume-Owned([string]$Name) {
    if ($Name -cnotin ((O @('volume','ls','--format','{{.Name}}')) -split "`n")) { return $false }
    if ((O @('volume','inspect','--format','{{index .Labels "com.docker.compose.project"}}',$Name)) -cne $project) { Fail 'Stateful volume belongs to another project.' }
    return $true
}
function Check-Resources {
    foreach ($suffix in @('postgres-data','product-checkpoints')) { [void](Volume-Owned "${project}_$suffix") }
    foreach ($service in @('postgres','localstack','image-storage','mailpit')) {
        $cid = Service-Id $service
        if (-not $cid) { continue }
        Assert-Owned $cid
        if ($service -eq 'image-storage') {
            $parent = Service-Id 'localstack'; if (-not $parent) { Fail 'Storage gateway has no namespace parent.' }
            $parent = O @('inspect','--format','{{.Id}}',$parent)
            $mode = O @('inspect','--format','{{.HostConfig.NetworkMode}}',$cid)
            if ($mode -cne "container:$parent") { Fail 'Unexpected storage gateway namespace.' }
            $overlay = @{services=@{'image-storage'=@{network_mode=$mode}}} | ConvertTo-Json -Depth 5 -Compress
            $hashLine = Pipe-D $overlay ($compose + @('-f','-','config','--hash',$service))
        } else { $hashLine = O ($compose + @('config','--hash',$service)) }
        $parts = $hashLine -split '\s+'
        if ($parts.Count -ne 2 -or $parts[1] -notmatch '^[0-9a-f]{64}$') { Fail 'Invalid service configuration fingerprint.' 5 }
        if ((Label $cid 'com.docker.compose.config-hash') -cne $parts[1]) { Fail 'Stateful service configuration changed; automatic recreation forbidden.' }
    }
    $names = (O @('network','ls','--format','{{.Name}}')) -split "`n"
    foreach ($suffix in @('default','storage-private')) {
        $name = "${project}_$suffix"
        if ($name -cin $names) {
            if ((O @('network','inspect','--format','{{index .Labels "com.docker.compose.project"}}',$name)) -cne $project -or (O @('network','inspect','--format','{{.Driver}}',$name)) -cne 'bridge') { Fail 'Network is not an owned local project bridge.' }
        }
    }
}
function Wait-Service([string]$Name,[int]$Seconds) {
    $clock = [Diagnostics.Stopwatch]::StartNew()
    while ($clock.Elapsed.TotalSeconds -lt $Seconds) {
        $cid = Service-Id $Name
        if ($cid) {
            Assert-Owned $cid
            if ((O @('inspect','--format','{{.State.Status}}',$cid)) -in @('exited','dead')) { Fail "$Name exited before readiness." 5 }
            if ((O @('inspect','--format','{{if .State.Health}}{{.State.Health.Status}}{{else}}absent{{end}}',$cid)) -eq 'healthy') { return }
        }
        Start-Sleep -Seconds 1
    }
    Fail "Readiness deadline exceeded for $Name." 5
}
function Helper([string]$Stage,[switch]$Capture,[switch]$Confirmed) {
    if ((O @('image','inspect','--format','{{.Id}}',$image)) -cne $imageId) { Fail 'Helper image changed during startup.' }
    $name = "$project-bootstrap-$token"
    [void]$workers.Add($name)
    $argv = $full + @('run','-T','--interactive=false','--rm','--no-deps','--name',$name,'--label',"$label=$token")
    if ($Confirmed) { $argv += @('-e','BOOTSTRAP_FRESH_CONFIRMED=1') }
    $argv += @('--entrypoint','python','bootstrap','/app/runtime.py',$Stage)
     $native = @(Native-Arguments $argv)
    if ($Capture) {
        $out = & docker @native
        $rc = $LASTEXITCODE
    } else { & docker @native | Out-Host; $rc = $LASTEXITCODE }
    if (Container-Absent $name) { [void]$workers.Remove($name) }
    if ($rc -ne 0) { if ($Capture) { $out | Out-Host }; Fail "Bootstrap stage $Stage failed; preserve data and inspect the report." $rc }
    if ($Capture) { return (@($out) -join "`n").Trim() }
}
function Isolated([string[]]$Extra) {
    $name = "$project-isolated-$token"
    [void]$workers.Add($name)
    D (@('run','--rm','--name',$name,'--label',"$label=$token",'--network','none','--group-add','10001') + $Extra)
    if (Container-Absent $name) { [void]$workers.Remove($name) }
}
function Cleanup {
    $safe = $true
    foreach ($name in @($workers)) {
        try {
            if (Container-Absent $name) { continue }
            if ((Label $name $label) -cne $token) { $safe = $false; continue }
            D @('stop','--time','30',$name)
            if ((O @('inspect','--format','{{.State.Running}}',$name)) -cne 'false') { $safe = $false; continue }
            D @('rm',$name)
        } catch { $safe = $false }
    }
    if ($lockId -and $safe) {
        try { if ((Label $lockId $label) -ceq $token) { D @('rm',$lockId) } else { $safe = $false } } catch { $safe = $false }
    }
    if (-not $safe) { [Console]::Error.WriteLine('Could not prove workers stopped; daemon lock retained for manual recovery.') }
}
Push-Location $repo
try {
    foreach ($tool in @('git','docker')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { Fail "Missing $tool." 2 } }
    if (-not (Test-Path -LiteralPath '.env.local' -PathType Leaf)) { Fail 'Configure .env.local first.' 2 }
    foreach ($entry in Get-ChildItem Env:) { if ($entry.Value -and ($entry.Name -match '^PG' -or $entry.Name -in @('AWS_PROFILE','AWS_DEFAULT_PROFILE','AWS_ENDPOINT_URL','AWS_ENDPOINT_URL_S3','DB_URL','DATABASE_URL'))) { Fail 'Ambient database/cloud overrides forbidden.' 2 } }
    $endpoint = O @('context','inspect','--format','{{.Endpoints.docker.Host}}')
    foreach ($target in @($endpoint,$env:DOCKER_HOST)) { if ($target -and $target -notin @('npipe:////./pipe/docker_engine','npipe:////./pipe/dockerDesktopLinuxEngine')) { Fail 'Only local Windows Docker daemon permitted.' 2 } }
    if ((O @('info','--format','{{.OSType}}')) -ne 'linux') { Fail 'Linux containers required.' 2 }
    $env:BOOTSTRAP_DAEMON_ID = O @('info','--format','{{.ID}}')
    if ($env:BOOTSTRAP_DAEMON_ID -notmatch '^[A-Za-z0-9:-]+$') { Fail 'Cannot establish Docker daemon identity.' 5 }
    $env:BOOTSTRAP_HOST_GID = '10001'; $env:BOOTSTRAP_STARTUP_SCRIPT = './scripts/start-dev.ps1'
    Remove-Item Env:BOOTSTRAP_FRESH_CONFIRMED,Env:BOOTSTRAP_FAULT -ErrorAction SilentlyContinue
    $imageId = O @('image','inspect','--format','{{.Id}}',$image)
    $head = & git -C $source rev-parse HEAD
    if ($LASTEXITCODE -ne 0 -or $head -cne '89e3b6c5bc95b533386a349c65305613617d8709' -or -not (Test-Path -LiteralPath (Join-Path $source '.git') -PathType Container)) { Fail 'Source Git revision incompatible.' }
    $cfg = O ($compose + @('config','--format','json'))
    $timeouts = (Pipe-D $cfg @('run','--rm','-i','--network','none','--entrypoint','python',$imageId,'/app/bootstrap.py','validate-compose','--format','timeouts')) -split '\s+'
    if ($timeouts.Count -ne 3 -or ($timeouts | Where-Object { $_ -notmatch '^[0-9]+$' })) { Fail 'Invalid helper timeout configuration.' 2 }
    $infraTimeout = [int]$timeouts[0]; $backendTimeout = [int]$timeouts[1]
    $artifact = [IO.Path]::GetFullPath((Pipe-D (O ($full + @('config','--format','json'))) @('run','--rm','-i','--network','none','--entrypoint','python',$imageId,'/app/bootstrap.py','host-artifact')))
    $base = [IO.Path]::GetFullPath((Join-Path $repo '.private/demo-bootstrap/artifacts'))
    if ((Split-Path -Parent $artifact) -ne $base -or (Split-Path -Leaf $artifact) -notmatch '^demo-v[1-9][0-9]*$' -or -not (Test-Path -LiteralPath $artifact -PathType Container)) { Fail 'Invalid or missing private artifact.' 2 }
    foreach ($path in @($artifact,$base,(Split-Path -Parent $base),(Join-Path $repo '.private'),$source,(Join-Path $repo 'data'))) { if ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { Fail 'Reparse-point input refused.' } }
    if ($fresh -and (Read-Host 'Fresh deletes backend_demo and importer-owned assets. Type RESET backend_demo') -cne 'RESET backend_demo') { Fail 'Confirmation did not match; nothing was reset.' 2 }
    $lockId = & docker create --name $lockName --network none --label "$label=$token" --label "demo-bootstrap.project=$project" --label 'demo-bootstrap.database=backend_demo' --label 'demo-bootstrap.bucket=backend-demo-images' --label "demo-bootstrap.host=$env:COMPUTERNAME" --label "demo-bootstrap.pid=$PID" --label "demo-bootstrap.started=$([DateTime]::UtcNow.ToString('o'))" --entrypoint python $imageId -c pass
    if ($LASTEXITCODE -ne 0 -or -not $lockId) { $lockId = $null; Fail 'Bootstrap lock held or unavailable.' 3 }
    Check-Resources
    Write-Host '[startup] Verify approved artifact, source, importer and migrations'
    Isolated @('--mount',"type=bind,source=$artifact,target=/artifact,readonly",'--mount',"type=bind,source=$source,target=/source,readonly",'--mount',"type=bind,source=$repo/demo/src/main/resources/db/migration,target=/migrations,readonly",'--mount',"type=bind,source=$repo/scripts/start-dev.ps1,target=/startup-script,readonly",'--entrypoint','python',$imageId,'/app/runtime.py','artifact')
    $reports = Join-Path $repo '.private/demo-bootstrap/reports'
    [void][IO.Directory]::CreateDirectory($reports)
    if ((Get-Item -LiteralPath $reports -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { Fail 'Reports reparse point refused.' }
    Isolated @('--mount',"type=bind,source=$reports,target=/reports",'--entrypoint','python',$imageId,'-c','import os; assert os.access("/reports",os.W_OK)')
    $backend = Service-Id 'backend'; if ($backend) { Assert-Owned $backend; D ($compose + @('stop','backend')) }
    $volume = "${project}_product-checkpoints"
    if (-not (Volume-Owned $volume)) {
        D @('volume','create','--label',"com.docker.compose.project=$project",'--label','com.docker.compose.volume=product-checkpoints',$volume)
        Isolated @('--mount',"type=volume,source=$volume,target=/catalog-checkpoints",'--entrypoint','python',$imageId,'-c','import os; assert os.access("/catalog-checkpoints",os.W_OK)')
    }
    Isolated @('--mount',"type=volume,source=$volume,target=/catalog-checkpoints,readonly",'--entrypoint','python',$imageId,'-c','from pathlib import Path; from storage import inspect_checkpoints,OwnershipLedger; p=Path("/catalog-checkpoints"); files=inspect_checkpoints(p); OwnershipLedger(p,allow_new_empty=not files)')
    $localstack = Service-Id 'localstack'
    if ($localstack -and (O @('inspect','--format','{{.State.Running}}',$localstack)) -eq 'true') { Helper 'live-preflight' }
    foreach ($service in @('postgres','localstack','image-storage','mailpit')) {
        Write-Host "[startup] Start $service"
        D ($compose + @('up','-d','--no-deps','--no-recreate',$service)); Wait-Service $service $infraTimeout
        if ($service -eq 'postgres' -and -not $fresh) { Helper 'restore-plan' }
    }
    if ($fresh) { Helper 'reset' -Confirmed }
    $state = Helper 'classify' -Capture
    if ($state -ceq 'RESET_PENDING') { Helper 'reset'; $state = 'UNSEEDED' }
    if ($state -cne 'COMPLETED') {
        if ($state -cnotin @('UNSEEDED','INTERRUPTED')) { Fail 'Unexpected initialization state.' }
        Write-Host '[startup] Build backend and run isolated Flyway migration'
        D ($compose + @('build','backend'))
        $migrator = "$project-migrator-$token"; [void]$workers.Add($migrator)
        D ($compose + @('run','-T','--interactive=false','-d','--no-deps','--name',$migrator,'--label',"$label=$token",'-e','SERVER_ADDRESS=127.0.0.1','-e','SECURITY_IDENTITY_ENABLED=false','-e','SECURITY_VERIFICATION_ENABLED=false','-e','SECURITY_VERIFICATION_MAIL_WORKER_ENABLED=false','-e','SECURITY_AUTH_CLEANUP_ENABLED=false','-e','BEHAVIOR_RETENTION_ENABLED=false','backend'))
        $clock = [Diagnostics.Stopwatch]::StartNew(); $migrated = $false
        while ($clock.Elapsed.TotalSeconds -lt $backendTimeout) {
            if ((O @('inspect','--format','{{.State.Running}}',$migrator)) -ne 'true') { Fail 'Temporary migration instance exited.' 5 }
            & docker exec $migrator wget -q -O /dev/null 'http://127.0.0.1:8080/api/products?q=catalog-readiness-probe&page=0&size=1' | Out-Null
            if ($LASTEXITCODE -eq 0) { $migrated = $true; break }
            Start-Sleep -Seconds 1
        }
        if (-not $migrated) { Fail 'Migration readiness deadline exceeded.' 5 }
        D @('stop','--time','30',$migrator); D @('rm',$migrator); [void]$workers.Remove($migrator)
        Helper 'migrated'
    }
    Write-Host '[startup] Import or repair assets'
    Helper 'import'
    try { D ($compose + @('up','-d','--no-deps','backend')); Wait-Service 'backend' $backendTimeout; Helper 'report-ready' } catch { Fail 'Dataset committed; backend readiness/report failed. Do not reset.' 6 }
} catch {
    [Console]::Error.WriteLine($_.Exception.Message)
    $status = 5; if ($_.Exception.Data.Contains('ExitCode')) { $status = [int]$_.Exception.Data['ExitCode'] }
} finally { Cleanup; Pop-Location }
exit $status
