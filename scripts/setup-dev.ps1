# Windows PowerShell 5.1+ / PowerShell 7. No host Python or Bash.
$ErrorActionPreference = 'Stop'
$env:BUILDKIT_PROGRESS = 'plain'
$env:COMPOSE_PROGRESS = 'plain'
$env:COMPOSE_ANSI = 'never'
$env:COMPOSE_BAKE = 'false'
$OutputEncoding = New-Object System.Text.UTF8Encoding($false)
[Console]::OutputEncoding = $OutputEncoding
$forward = @($args)
if ($forward.Count -gt 1 -or ($forward.Count -eq 1 -and $forward[0] -cne '--fresh')) { throw 'Usage: setup-dev.ps1 [--fresh]' }
$repo = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$source = Join-Path $repo 'data/Catalog-Data'
$base = Join-Path $repo '.private/demo-bootstrap/artifacts'
$remote = 'https://github.com/Adiff-git/Catalog-Data.git'
$revision = '89e3b6c5bc95b533386a349c65305613617d8709'
$image = 'backend-swe-demo-bootstrap:local'
function Native-Arguments([string[]]$Values) {
    # Windows PowerShell's legacy native parser strips unescaped embedded quotes.
    if ($PSVersionTable.PSVersion -lt [version]'7.3' -or $PSNativeCommandArgumentPassing -eq 'Legacy') {
        foreach ($value in $Values) { $value.Replace('"','\"') }
    } else { $Values }
}
function Run-Native([string]$Exe, [string[]]$Argv) {
    $native = @(Native-Arguments $Argv)
    & $Exe @native | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed (exit $LASTEXITCODE)." }
}
function Get-Native([string]$Exe, [string[]]$Argv) {
    $native = @(Native-Arguments $Argv)
    $out = & $Exe @native
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed (exit $LASTEXITCODE)." }
    return (@($out) -join "`n").Trim()
}
function Assert-NoLink([string]$Path) {
    if (Test-Path -LiteralPath $Path) {
        if ((Get-Item -LiteralPath $Path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint) { throw "Reparse point refused: $Path" }
    }
}
function Verify-Artifact([string]$Path) {
    Run-Native docker @('run','--rm','--network','none','--group-add','10001',
        '--mount',"type=bind,source=$Path,target=/artifact,readonly",
        '--mount',"type=bind,source=$source,target=/source,readonly",
        '--mount',"type=bind,source=$repo/demo/src/main/resources/db/migration,target=/migrations,readonly",
        '--mount',"type=bind,source=$repo/scripts/start-dev.ps1,target=/startup-script,readonly",
        '--entrypoint','python',$image,'/app/runtime.py','artifact')
}
$lock = $null; $sourceTemp = $null; $artifactTemp = $null; $status = 0
Push-Location $repo
try {
    foreach ($tool in @('git','docker','tar.exe')) { if (-not (Get-Command $tool -ErrorAction SilentlyContinue)) { throw "Required command missing: $tool" } }
    if (-not (Test-Path -LiteralPath '.env.local' -PathType Leaf)) { throw 'Create and configure .env.local first.' }
    foreach ($entry in Get-ChildItem Env:) {
        if ($entry.Value -and ($entry.Name -match '^PG' -or $entry.Name -in @('AWS_PROFILE','AWS_DEFAULT_PROFILE','AWS_ENDPOINT_URL','AWS_ENDPOINT_URL_S3','DB_URL','DATABASE_URL'))) { throw 'Ambient cloud/database overrides forbidden.' }
    }
    $endpoint = Get-Native docker @('context','inspect','--format','{{.Endpoints.docker.Host}}')
    foreach ($target in @($endpoint,$env:DOCKER_HOST)) { if ($target -and $target -notin @('npipe:////./pipe/docker_engine','npipe:////./pipe/dockerDesktopLinuxEngine')) { throw 'Only the local Windows Docker daemon is permitted.' } }
    if ((Get-Native docker @('info','--format','{{.OSType}}')) -ne 'linux') { throw 'Switch Docker Desktop to Linux containers.' }
    Run-Native docker @('compose','version')
    Remove-Item Env:BOOTSTRAP_FRESH_CONFIRMED,Env:BOOTSTRAP_FAULT -ErrorAction SilentlyContinue
    $env:BOOTSTRAP_STARTUP_SCRIPT = './scripts/start-dev.ps1'
    $env:BOOTSTRAP_HOST_GID = '10001'
    $env:BOOTSTRAP_DAEMON_ID = Get-Native docker @('info','--format','{{.ID}}')
    foreach ($path in @('data','.private','.private/demo-bootstrap','.private/demo-bootstrap/artifacts','data/Catalog-Data')) { Assert-NoLink (Join-Path $repo $path) }
    [void][IO.Directory]::CreateDirectory($base)
    [void][IO.Directory]::CreateDirectory((Join-Path $repo 'data'))
    $lockPath = Join-Path $repo '.private/demo-bootstrap/setup.lock'
    try { New-Item -ItemType Directory -Path $lockPath -ErrorAction Stop | Out-Null; $lock = $lockPath } catch { throw 'Setup lock exists or cannot be created; do not remove until all launchers have stopped.' }
    Write-Host '[setup] Build helper (Docker cache enabled)'
    Run-Native docker @('build','--progress=plain','-t',$image,'tools/demo-bootstrap')
    $compose = @('compose','--project-name','backend-swe-demo','--env-file',(Join-Path $repo '.env.local'),'-f',(Join-Path $repo 'compose.dev.yaml'),'-f',(Join-Path $repo 'compose.bootstrap.yaml'))
    $config = Get-Native docker ($compose + @('config','--format','json'))
    $resolved = $config | & docker run --rm -i --network none --entrypoint python $image /app/bootstrap.py host-artifact
    if ($LASTEXITCODE -ne 0) { throw 'Invalid effective Compose configuration.' }
    $artifact = [IO.Path]::GetFullPath((@($resolved) -join "`n").Trim())
    $version = Split-Path -Leaf $artifact
    if ($version -notmatch '^demo-v[1-9][0-9]*$' -or $artifact -ne [IO.Path]::GetFullPath((Join-Path $base $version))) { throw 'Configure a versioned direct child of .private/demo-bootstrap/artifacts.' }
    Assert-NoLink $artifact
    Write-Host '[setup] Prepare pinned Catalog-Data source'
    if (-not (Test-Path -LiteralPath $source)) {
        $sourceTemp = Join-Path (Join-Path $repo 'data') ('.catalog-setup.' + [guid]::NewGuid())
        [void][IO.Directory]::CreateDirectory($sourceTemp)
        $checkout = Join-Path $sourceTemp 'checkout'
        Run-Native git @('-c','core.autocrlf=false','clone','--no-checkout','--no-tags','--single-branch','--branch','main','--depth=1','--filter=blob:none',$remote,$checkout)
        Run-Native git @('-C',$checkout,'config','core.autocrlf','false')
        $present = $false
        try { & git -C $checkout cat-file -e "${revision}^{commit}" 2>$null; $present = $LASTEXITCODE -eq 0 } catch { $present = $false }
        if (-not $present) { Run-Native git @('-C',$checkout,'fetch','--no-tags','--depth=1','origin',$revision) }
        Run-Native git @('-C',$checkout,'checkout','--detach',$revision)
        [IO.Directory]::Move($checkout,$source)
    }
    Assert-NoLink (Join-Path $source '.git')
    if (-not (Test-Path -LiteralPath (Join-Path $source '.git') -PathType Container) -or (Get-Native git @('-C',$source,'rev-parse','HEAD')) -cne $revision) { throw 'Existing Catalog-Data checkout incompatible; preserve it and correct manually.' }
    Write-Host '[setup] Fetch demo artifacts from Catalog-Data bootstrap'
    Run-Native git @('-C',$source,'fetch','--no-tags',$remote,'refs/heads/bootstrap')
    $artifactCommit = Get-Native git @('-C',$source,'rev-parse','FETCH_HEAD^{commit}')
    $member = 'demo-bootstrap/artifacts'
    $versions = @((Get-Native git @('-C',$source,'ls-tree','--name-only',"${artifactCommit}:$member")) -split "`n")
    if (-not $versions.Count -or $version -cnotin $versions) { throw "Artifact $version is not published on Catalog-Data bootstrap." }
    foreach ($release in $versions) {
        if ($release -cnotmatch '^demo-v[1-9][0-9]*$') { throw 'Unexpected artifact directory in bootstrap branch.' }
        Assert-NoLink (Join-Path $base $release)
    }
    $missing = @($versions | Where-Object { -not (Test-Path -LiteralPath (Join-Path $base $_)) })
    if ($missing.Count) {
        $artifactTemp = Join-Path $base ('.artifact-setup.' + [guid]::NewGuid())
        [void][IO.Directory]::CreateDirectory($artifactTemp)
        $payload = Join-Path $artifactTemp 'payload'
        [void][IO.Directory]::CreateDirectory($payload)
        foreach ($release in $missing) {
            $releaseMember = "$member/$release"
            $inventory = Get-Native git @('-C',$source,'ls-tree','-r',$artifactCommit,'--',$releaseMember)
            if (-not $inventory) { throw 'Empty artifact tree.' }
            foreach ($line in ($inventory -split "`n")) {
                if ($line -notmatch '^100(644|755) blob [0-9a-f]+\t([A-Za-z0-9._/-]+)$') { throw 'Unsafe artifact Git tree.' }
                $relative = $Matches[2]
                if (-not $relative.StartsWith("$releaseMember/") -or ($relative -split '/' | Where-Object { $_ -in @('.','..') -or $_ -match '[. ]$' -or $_ -match '^(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\.|$)' })) { throw 'Unsafe artifact path.' }
            }
            $archive = Join-Path $artifactTemp "$release.tar"
            # Binary archive files avoid PowerShell text conversion and Git checkout EOL conversion.
            Run-Native git @('-C',$source,'-c','core.autocrlf=false','archive','--format=tar',"--output=$archive",$artifactCommit,$releaseMember)
            Run-Native tar.exe @('-xf',$archive,'--strip-components=2','-C',$payload)
        }
        if ($version -cin $missing) { Verify-Artifact (Join-Path $payload $version) } else { Verify-Artifact $artifact }
        foreach ($release in $missing) {
            [IO.Directory]::Move((Join-Path $payload $release),(Join-Path $base $release))
        }
    } else {
        Write-Host '[setup] Existing artifact versions preserved'
    }
    Verify-Artifact $artifact
    Write-Host '[setup] Starting backend'
    # Child process keeps launcher finally/lock cleanup independent of exit in orchestrator.
    $hostExe = (Get-Process -Id $PID).Path
    & $hostExe -NoProfile -File (Join-Path $PSScriptRoot 'start-dev.ps1') @forward
    $status = $LASTEXITCODE
} catch { [Console]::Error.WriteLine($_.Exception.Message); $status = 2 }
finally {
    if ($artifactTemp -and (Test-Path -LiteralPath $artifactTemp)) {
        $resolvedTemp = [IO.Path]::GetFullPath($artifactTemp)
        if ((Split-Path -Parent $resolvedTemp) -eq $base -and (Split-Path -Leaf $resolvedTemp) -match '^\.artifact-setup\.[0-9a-f-]+$') { Remove-Item -LiteralPath $resolvedTemp -Recurse -Force -ErrorAction SilentlyContinue }
    }
    if ($sourceTemp -and (Test-Path -LiteralPath $sourceTemp)) { Remove-Item -LiteralPath $sourceTemp -Recurse -Force -ErrorAction SilentlyContinue }
    if ($lock) { Remove-Item -LiteralPath $lock -Force -ErrorAction SilentlyContinue }
    Pop-Location
}
exit $status
