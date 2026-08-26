param(
    [ValidateSet('smoke', 'baseline')]
    [string] $Scenario = 'baseline',
    [int] $Rate = 50,
    [string] $Duration = '1m',
    [int] $PreAllocatedVUs = 500,
    [int] $MaxVUs = 500,
    [string] $SummaryName,
    [string] $PgContainer = 'sb13-monew-team04-postgres-1',
    [int] $StatsDelaySeconds = 30,
    [switch] $AllowFailure
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..')).Path
$rawRoot = $PSScriptRoot
$resultRoot = Join-Path $repoRoot 'scripts\performance\activity-history\k6\results\mid4-179-rdb-throughput-limit'

if (-not (Test-Path -LiteralPath $resultRoot)) {
    New-Item -ItemType Directory -Path $resultRoot | Out-Null
}

$runLabel = if ($Scenario -eq 'smoke') {
    'smoke'
} else {
    "${Rate}rps"
}

if ([string]::IsNullOrWhiteSpace($SummaryName)) {
    if ($Scenario -eq 'smoke') {
        $SummaryName = 'activity-history-smoke-summary.json'
    } else {
        $SummaryName = "activity-history-${Rate}rps-summary.json"
    }
}

$env:K6_SCENARIO = $Scenario
$env:K6_BASE_URL = 'http://host.docker.internal:8080'
$env:K6_ACTIVITY_HISTORY_PATH_TEMPLATE = '/api/user-activities/{userId}'
$env:K6_TARGET_USER_ID = '00000001-0000-4000-8000-000000000001'
$env:K6_USER_ID_HEADER_NAME = 'Monew-Request-User-ID'
$env:K6_SUMMARY_PATH = "/results/mid4-179-rdb-throughput-limit/$SummaryName"
$env:K6_BASELINE_RATE = [string] $Rate
$env:K6_BASELINE_TIME_UNIT = '1s'
$env:K6_BASELINE_DURATION = $Duration
$env:K6_BASELINE_PRE_ALLOCATED_VUS = [string] $PreAllocatedVUs
$env:K6_BASELINE_MAX_VUS = [string] $MaxVUs

function Write-TextOutput {
    param(
        [string] $Path,
        [object[]] $Output
    )

    $Output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Invoke-DockerStats {
    param(
        [string] $Phase
    )

    $outputPath = Join-Path $rawRoot "docker-stats-$Phase-$runLabel.txt"
    $output = docker stats --no-stream $PgContainer 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextOutput $outputPath $output
    if ($exitCode -ne 0) {
        throw "docker stats failed. phase=$Phase exitCode=$exitCode output=$outputPath"
    }
}

function Invoke-PgActivitySnapshot {
    param(
        [string] $Phase
    )

    $outputPath = Join-Path $rawRoot "pg-stat-activity-$Phase-$runLabel.txt"
    $sql = @"
SELECT COALESCE(state, '') AS state,
       COALESCE(wait_event_type, '') AS wait_event_type,
       COALESCE(wait_event, '') AS wait_event,
       COUNT(*) AS count
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state, wait_event_type, wait_event
ORDER BY state, wait_event_type, wait_event;
"@

    $output = docker exec $PgContainer psql -U monew -d monew -v ON_ERROR_STOP=1 -c $sql 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextOutput $outputPath $output
    if ($exitCode -ne 0) {
        throw "docker exec psql failed. phase=$Phase exitCode=$exitCode output=$outputPath"
    }
}

$k6Environment = @{
    K6_SCENARIO = $env:K6_SCENARIO
    K6_BASE_URL = $env:K6_BASE_URL
    K6_ACTIVITY_HISTORY_PATH_TEMPLATE = $env:K6_ACTIVITY_HISTORY_PATH_TEMPLATE
    K6_TARGET_USER_ID = $env:K6_TARGET_USER_ID
    K6_USER_ID_HEADER_NAME = $env:K6_USER_ID_HEADER_NAME
    K6_SUMMARY_PATH = $env:K6_SUMMARY_PATH
    K6_BASELINE_RATE = $env:K6_BASELINE_RATE
    K6_BASELINE_TIME_UNIT = $env:K6_BASELINE_TIME_UNIT
    K6_BASELINE_DURATION = $env:K6_BASELINE_DURATION
    K6_BASELINE_PRE_ALLOCATED_VUS = $env:K6_BASELINE_PRE_ALLOCATED_VUS
    K6_BASELINE_MAX_VUS = $env:K6_BASELINE_MAX_VUS
}

$k6Job = Start-Job -ScriptBlock {
    param(
        [string] $Root,
        [hashtable] $Environment
    )

    Set-Location -LiteralPath $Root

    foreach ($key in $Environment.Keys) {
        [Environment]::SetEnvironmentVariable($key, [string] $Environment[$key], 'Process')
    }

    $output = docker compose -f compose.k6.yaml run --rm k6 2>&1

    [pscustomobject]@{
        ExitCode = $LASTEXITCODE
        Output = $output
    }
} -ArgumentList $repoRoot, $k6Environment

if ($Scenario -eq 'baseline' -and $StatsDelaySeconds -gt 0) {
    Start-Sleep -Seconds $StatsDelaySeconds
    Invoke-DockerStats 'mid'
    Invoke-PgActivitySnapshot 'mid'
}

Wait-Job $k6Job | Out-Null
$k6Result = Receive-Job $k6Job
Remove-Job $k6Job

$outputPath = Join-Path $rawRoot "k6-$runLabel.out"
Write-TextOutput $outputPath $k6Result.Output
$k6Result.Output | ForEach-Object { $_.ToString() }

if ($Scenario -eq 'baseline') {
    Invoke-DockerStats 'after'
    Invoke-PgActivitySnapshot 'after'
}

$sourceSummary = Join-Path $resultRoot $SummaryName
if (Test-Path -LiteralPath $sourceSummary) {
    Copy-Item -LiteralPath $sourceSummary -Destination (Join-Path $rawRoot $SummaryName) -Force
}

if ($k6Result.ExitCode -ne 0 -and -not $AllowFailure) {
    throw "k6 $runLabel failed. output=$outputPath"
}
