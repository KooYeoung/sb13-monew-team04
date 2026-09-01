param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('fanout', 'exclusion')]
    [string] $Overlay,
    [Parameter(Mandatory = $true)]
    [string] $PgContainer,
    [string] $DbUser = 'monew',
    [string] $DbName = 'monew',
    [ValidateRange(1, 20)]
    [int] $WarmupRuns = 3,
    [ValidateRange(1, 20)]
    [int] $MeasureRuns = 5,
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $ResultSet = 'mid4-227-rdb-explain-compare'
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$ResultSet\sql\$Overlay"
$queryNames = @(
    'recent-comments',
    'recent-comment-likes',
    'recent-article-views',
    'subscribed-interests'
)
$versions = @('before', 'after')

New-Item -ItemType Directory -Path $resultRoot -Force | Out-Null

function Invoke-Psql {
    param(
        [Parameter(Mandatory = $true)]
        [string] $Sql,
        [switch] $TuplesOnly
    )

    $arguments = @(
        'exec', '-i', $PgContainer,
        'psql', '-X', '-q',
        '-U', $DbUser,
        '-d', $DbName,
        '-v', 'ON_ERROR_STOP=1'
    )
    if ($TuplesOnly) {
        $arguments += @('-t', '-A')
    }

    $output = $Sql | & docker @arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "psql failed. container=$PgContainer output=$($output -join [Environment]::NewLine)"
    }

    return (($output | ForEach-Object { $_.ToString() }) -join [Environment]::NewLine).Trim()
}

function Get-Median {
    param([double[]] $Values)

    $sorted = @($Values | Sort-Object)
    $middle = [math]::Floor($sorted.Count / 2)
    if ($sorted.Count % 2 -eq 1) {
        return [double] $sorted[$middle]
    }

    return ([double] $sorted[$middle - 1] + [double] $sorted[$middle]) / 2
}

function Get-VersionOrder {
    param([int] $Run)

    if ($Run % 2 -eq 1) {
        return @('before', 'after')
    }
    return @('after', 'before')
}

function Get-QuerySql {
    param(
        [string] $Version,
        [string] $QueryName
    )

    $queryPath = Join-Path $PSScriptRoot "$Version\$QueryName.sql"
    return Get-Content -LiteralPath $queryPath -Raw -Encoding UTF8
}

function Invoke-Warmup {
    param(
        [string] $Version,
        [string] $QueryName,
        [string] $QuerySql
    )

    for ($run = 1; $run -le $WarmupRuns; $run++) {
        Write-Host "warm-up: overlay=$Overlay query=$QueryName version=$Version run=$run/$WarmupRuns"
        Invoke-Psql -Sql $QuerySql -TuplesOnly | Out-Null
    }
}

function Invoke-PlainTiming {
    param(
        [string] $Version,
        [string] $QueryName,
        [string] $QuerySql,
        [int] $Run
    )

    Write-Host "plain timing: overlay=$Overlay query=$QueryName version=$Version run=$Run/$MeasureRuns"
    $output = Invoke-Psql -Sql "\timing on`n$QuerySql"
    $matches = [regex]::Matches($output, 'Time:\s+([0-9]+(?:\.[0-9]+)?)\s+ms')
    if ($matches.Count -eq 0) {
        throw "Unable to parse psql timing. query=$QueryName version=$Version output=$output"
    }

    return [double]::Parse(
        $matches[$matches.Count - 1].Groups[1].Value,
        [Globalization.CultureInfo]::InvariantCulture
    )
}

function Invoke-Explain {
    param(
        [string] $Version,
        [string] $QueryName,
        [string] $QuerySql,
        [int] $Run
    )

    Write-Host "explain: overlay=$Overlay query=$QueryName version=$Version run=$Run/$MeasureRuns"
    $explainSql = @"
SET track_io_timing = on;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING, FORMAT JSON)
$QuerySql
"@
    $json = Invoke-Psql -Sql $explainSql -TuplesOnly
    $jsonPath = Join-Path $resultRoot "$Version-$QueryName-explain-run$Run.json"
    Set-Content -LiteralPath $jsonPath -Value $json -Encoding UTF8

    $parsed = $json | ConvertFrom-Json
    $root = $parsed[0]
    return [pscustomobject]@{
        run = $Run
        executionTimeMs = [double] $root.'Execution Time'
        planningTimeMs = [double] $root.'Planning Time'
        sharedHitBlocks = [long] $root.Plan.'Shared Hit Blocks'
        sharedReadBlocks = [long] $root.Plan.'Shared Read Blocks'
        ioReadTimeMs = if ($null -eq $root.Plan.'I/O Read Time') {
            0.0
        } else {
            [double] $root.Plan.'I/O Read Time'
        }
        file = $jsonPath.Substring($repoRoot.Length + 1)
    }
}

function Invoke-ExplainText {
    param(
        [string] $Version,
        [string] $QueryName,
        [string] $QuerySql
    )

    $explainSql = @"
SET track_io_timing = on;
EXPLAIN (ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING)
$QuerySql
"@
    $text = Invoke-Psql -Sql $explainSql
    $textPath = Join-Path $resultRoot "$Version-$QueryName-explain-text.txt"
    Set-Content -LiteralPath $textPath -Value $text -Encoding UTF8
    return [pscustomobject]@{
        file = $textPath.Substring($repoRoot.Length + 1)
    }
}

$runningContainer = docker inspect --format '{{.State.Running}}' $PgContainer 2>$null
if ($LASTEXITCODE -ne 0 -or $runningContainer -ne 'true') {
    throw "PostgreSQL container is not running: $PgContainer"
}

$settingsSql = @"
SELECT json_build_object(
    'version', version(),
    'shared_buffers', current_setting('shared_buffers'),
    'work_mem', current_setting('work_mem'),
    'effective_cache_size', current_setting('effective_cache_size'),
    'max_connections', current_setting('max_connections')
)::text;
"@
$settings = Invoke-Psql -Sql $settingsSql -TuplesOnly | ConvertFrom-Json
$summary = [ordered]@{
    ticket = 'MID4-227'
    overlay = $Overlay
    beforeCommit = '44dcf82'
    afterCommit = '9c195bd'
    warmupRuns = $WarmupRuns
    measureRuns = $MeasureRuns
    explainOptions = 'ANALYZE, BUFFERS, VERBOSE, SETTINGS, TIMING, FORMAT JSON'
    trackIoTiming = $true
    measuredAt = (Get-Date).ToString('o')
    postgres = $settings
    queries = [ordered]@{}
}

foreach ($queryName in $queryNames) {
    $querySql = @{}
    foreach ($version in $versions) {
        $querySql[$version] = Get-QuerySql -Version $version -QueryName $queryName
        Invoke-Warmup -Version $version -QueryName $queryName -QuerySql $querySql[$version]
    }

    $plainTimings = @{ before = @(); after = @() }
    for ($run = 1; $run -le $MeasureRuns; $run++) {
        foreach ($version in @(Get-VersionOrder -Run $run)) {
            $plainTimings[$version] += Invoke-PlainTiming `
                -Version $version `
                -QueryName $queryName `
                -QuerySql $querySql[$version] `
                -Run $run
        }
    }

    $explainRuns = @{ before = @(); after = @() }
    for ($run = 1; $run -le $MeasureRuns; $run++) {
        foreach ($version in @(Get-VersionOrder -Run $run)) {
            $explainRuns[$version] += Invoke-Explain `
                -Version $version `
                -QueryName $queryName `
                -QuerySql $querySql[$version] `
                -Run $run
        }
    }

    $querySummary = [ordered]@{}
    foreach ($version in $versions) {
        $executionTimes = @($explainRuns[$version] | ForEach-Object { $_.executionTimeMs })
        $executionMedian = Get-Median -Values $executionTimes
        $representative = $explainRuns[$version] |
            Sort-Object @{ Expression = { [math]::Abs($_.executionTimeMs - $executionMedian) } }, run |
            Select-Object -First 1

        $querySummary[$version] = [ordered]@{
            plainTimesMs = @($plainTimings[$version])
            plainMedianMs = Get-Median -Values @($plainTimings[$version])
            explainRuns = @($explainRuns[$version])
            explainMedianMs = $executionMedian
            representativeRun = $representative.run
            representativeFile = $representative.file
        }
        $textPlan = Invoke-ExplainText -Version $version -QueryName $queryName -QuerySql $querySql[$version]
        $querySummary[$version].textPlanFile = $textPlan.file
    }
    $summary.queries[$queryName] = $querySummary
}

$summaryPath = Join-Path $resultRoot 'summary.json'
$summary | ConvertTo-Json -Depth 12 | Set-Content -LiteralPath $summaryPath -Encoding UTF8
Write-Output "SQL comparison complete: $summaryPath"
