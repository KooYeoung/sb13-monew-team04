param(
    [ValidateSet('smoke', 'baseline', 'average', 'high-load', 'stress', 'throughput')]
    [string] $Scenario = 'smoke',
    [ValidateSet('rdb', 'mongo')]
    [string] $Variant = 'rdb',
    [ValidateSet('baseline', 'mixed')]
    [string] $K6Script = 'baseline',
    [int[]] $Rates = @(50, 100, 150, 200, 250),
    [string] $Duration = '',
    [string] $BaseUrl = 'http://host.docker.internal:8080',
    [string] $PathTemplate = '/api/user-activities/{userId}',
    [string[]] $TargetUserIds = @('00000001-0000-4000-8000-000000000001'),
    [ValidateSet('single', 'round-robin', 'random')]
    [string] $UserPickStrategy = 'round-robin',
    [string] $UserIdHeaderName = 'Monew-Request-User-ID',
    [string] $Authorization = '',
    [ValidateSet('80/20', '50/50')]
    [string] $MixRatio = '80/20',
    [string[]] $MixArticleIds = @(),
    [string[]] $MixCommentIds = @(),
    [string[]] $MixInterestIds = @(),
    [string[]] $MixWriteUserIds = @(),
    [int] $SmokeVus = 1,
    [int] $BaselineVus = 20,
    [int] $AverageVus = 50,
    [int] $HighLoadVus = 100,
    [int] $StressStartVus = 0,
    [string] $StressStages = '3m:50,3m:100,3m:200,3m:400',
    [string] $StressGracefulRampDown = '30s',
    [int] $PreAllocatedVUs = 500,
    [int] $MaxVUs = 500,
    [string] $PgContainer = 'sb13-monew-team04-postgres-1',
    [string] $MongoContainer = '',
    [ValidatePattern('^MID4-[0-9]+$')]
    [string] $Ticket = 'MID4-206',
    [ValidatePattern('^[A-Za-z0-9][A-Za-z0-9._-]*$')]
    [string] $ResultSet = 'mid4-206-mongodb-k6-compare',
    [ValidateRange(1, 20)]
    [int] $RepeatCount = 1,
    [ValidateRange(0, 3600)]
    [int] $StabilizationSeconds = 0,
    [int] $StatsDelaySeconds = 30,
    [switch] $AllowFailure
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$ResultSet"
$rawRoot = Join-Path $resultRoot 'raw'

if (-not (Test-Path -LiteralPath $resultRoot)) {
    New-Item -ItemType Directory -Path $resultRoot | Out-Null
}
if (-not (Test-Path -LiteralPath $rawRoot)) {
    New-Item -ItemType Directory -Path $rawRoot | Out-Null
}

function Write-TextOutput {
    param(
        [string] $Path,
        [object[]] $Output
    )

    $Output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $Path -Encoding UTF8
}

function Get-StatsContainers {
    $containers = @()
    if (-not [string]::IsNullOrWhiteSpace($PgContainer)) {
        $containers += $PgContainer
    }
    if (-not [string]::IsNullOrWhiteSpace($MongoContainer)) {
        $containers += $MongoContainer
    }
    return $containers
}

function Resolve-K6ScriptPath {
    if ($K6Script -eq 'mixed') {
        return '/scripts/activity-history-mixed.js'
    }

    return '/scripts/activity-history-baseline.js'
}

function Resolve-RunVariantLabel {
    if ($K6Script -eq 'mixed') {
        return "$Variant-mixed-$($MixRatio -replace '/', '-')"
    }

    return $Variant
}

function Resolve-SummaryPrefix {
    if ($K6Script -eq 'mixed') {
        return 'activity-history-mixed'
    }

    return 'activity-history'
}

function Invoke-DockerStats {
    param(
        [string] $Phase,
        [string] $RunLabel
    )

    $containers = @(Get-StatsContainers)
    if ($containers.Count -eq 0) {
        return
    }

    $outputPath = Join-Path $rawRoot "docker-stats-$Phase-$RunLabel.txt"
    $output = docker stats --no-stream $containers 2>&1
    $exitCode = $LASTEXITCODE
    Write-TextOutput $outputPath $output
    if ($exitCode -ne 0) {
        Write-Warning "docker stats failed. phase=$Phase exitCode=$exitCode output=$outputPath"
    }
}

function Invoke-PgActivitySnapshot {
    param(
        [string] $Phase,
        [string] $RunLabel
    )

    if ([string]::IsNullOrWhiteSpace($PgContainer)) {
        return
    }

    $outputPath = Join-Path $rawRoot "pg-stat-activity-$Phase-$RunLabel.txt"
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
        Write-Warning "docker exec psql failed. phase=$Phase exitCode=$exitCode output=$outputPath"
    }
}

function Resolve-Duration {
    param(
        [string] $DefaultDuration
    )

    if ([string]::IsNullOrWhiteSpace($Duration)) {
        return $DefaultDuration
    }

    return $Duration
}

function Get-ScenarioVus {
    if ($Scenario -eq 'smoke') {
        return $SmokeVus
    }
    if ($Scenario -eq 'baseline') {
        return $BaselineVus
    }
    if ($Scenario -eq 'average') {
        return $AverageVus
    }
    if ($Scenario -eq 'high-load') {
        return $HighLoadVus
    }

    return 0
}

function New-K6Environment {
    param(
        [int] $RunRate,
        [string] $SummaryName,
        [int] $RunIndex
    )

    $environment = @{
        K6_SCRIPT = Resolve-K6ScriptPath
        K6_SCENARIO = $Scenario
        K6_VARIANT = $Variant
        K6_TICKET = $Ticket
        K6_RUN_INDEX = [string] $RunIndex
        K6_BASE_URL = $BaseUrl
        K6_ACTIVITY_HISTORY_PATH_TEMPLATE = $PathTemplate
        K6_TARGET_USER_ID = $TargetUserIds[0]
        K6_TARGET_USER_IDS = ($TargetUserIds -join ',')
        K6_USER_PICK_STRATEGY = $UserPickStrategy
        K6_USER_ID_HEADER_NAME = $UserIdHeaderName
        K6_AUTHORIZATION = $Authorization
        K6_MIX_RATIO = $MixRatio
        K6_MIX_ARTICLE_IDS = ($MixArticleIds -join ',')
        K6_MIX_COMMENT_IDS = ($MixCommentIds -join ',')
        K6_MIX_INTEREST_IDS = ($MixInterestIds -join ',')
        K6_MIX_WRITE_USER_IDS = ($MixWriteUserIds -join ',')
        K6_SUMMARY_PATH = "/results/$ResultSet/$SummaryName"
        K6_HTTP_REQ_FAILED_RATE_THRESHOLD = '0.01'
        K6_HTTP_REQ_DURATION_P95_THRESHOLD = '200'
        K6_HTTP_REQ_DURATION_P99_THRESHOLD = '500'
        K6_CHECK_RATE_THRESHOLD = '0.99'
        K6_DROPPED_ITERATIONS_COUNT_THRESHOLD = '1'
    }

    if ($Scenario -eq 'smoke') {
        $environment['K6_SMOKE_VUS'] = [string] $SmokeVus
        $environment['K6_SMOKE_DURATION'] = Resolve-Duration '1m'
    } elseif ($Scenario -eq 'baseline') {
        $environment['K6_BASELINE_VUS'] = [string] $BaselineVus
        $environment['K6_BASELINE_DURATION'] = Resolve-Duration '5m'
    } elseif ($Scenario -eq 'average') {
        $environment['K6_AVERAGE_VUS'] = [string] $AverageVus
        $environment['K6_AVERAGE_DURATION'] = Resolve-Duration '10m'
    } elseif ($Scenario -eq 'high-load') {
        $environment['K6_HIGH_LOAD_VUS'] = [string] $HighLoadVus
        $environment['K6_HIGH_LOAD_DURATION'] = Resolve-Duration '10m'
    } elseif ($Scenario -eq 'throughput') {
        $environment['K6_THROUGHPUT_RATE'] = [string] $RunRate
        $environment['K6_THROUGHPUT_TIME_UNIT'] = '1s'
        $environment['K6_THROUGHPUT_DURATION'] = Resolve-Duration '1m'
        $environment['K6_THROUGHPUT_PRE_ALLOCATED_VUS'] = [string] $PreAllocatedVUs
        $environment['K6_THROUGHPUT_MAX_VUS'] = [string] $MaxVUs
    } else {
        $environment['K6_STRESS_START_VUS'] = [string] $StressStartVus
        $environment['K6_STRESS_STAGES'] = $StressStages
        $environment['K6_STRESS_GRACEFUL_RAMP_DOWN'] = $StressGracefulRampDown
    }

    return $environment
}

function Invoke-K6Run {
    param(
        [int] $RunRate,
        [int] $RunIndex
    )

    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $runVariantLabel = Resolve-RunVariantLabel
    $repeatLabel = if ($RepeatCount -gt 1) { "-run$RunIndex" } else { '' }
    $runLabel = if ($Scenario -eq 'throughput') {
        "$runVariantLabel-$Scenario-${RunRate}rps$repeatLabel-$timestamp"
    } elseif ($Scenario -eq 'stress') {
        "$runVariantLabel-$Scenario$repeatLabel-$timestamp"
    } else {
        $vus = Get-ScenarioVus
        "$runVariantLabel-$Scenario-${vus}vus$repeatLabel-$timestamp"
    }
    $summaryName = "$(Resolve-SummaryPrefix)-$runLabel-summary.json"
    $k6Environment = New-K6Environment $RunRate $summaryName $RunIndex

    Write-Output "k6 run start: $runLabel script=$K6Script"
    Invoke-DockerStats 'before' $runLabel
    Invoke-PgActivitySnapshot 'before' $runLabel

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

    if ($Scenario -ne 'smoke' -and $StatsDelaySeconds -gt 0) {
        $completed = Wait-Job $k6Job -Timeout $StatsDelaySeconds
        if ($null -eq $completed) {
            Invoke-DockerStats 'mid' $runLabel
            Invoke-PgActivitySnapshot 'mid' $runLabel
        }
    }

    Wait-Job $k6Job | Out-Null
    $k6Result = Receive-Job $k6Job
    Remove-Job $k6Job

    $outputPath = Join-Path $rawRoot "k6-$runLabel.out"
    Write-TextOutput $outputPath $k6Result.Output
    $k6Result.Output | ForEach-Object { $_.ToString() }

    Invoke-DockerStats 'after' $runLabel
    Invoke-PgActivitySnapshot 'after' $runLabel

    if ($k6Result.ExitCode -ne 0 -and -not $AllowFailure) {
        throw "k6 $runLabel failed. output=$outputPath"
    }
}

function Wait-Stabilization {
    if ($StabilizationSeconds -le 0) {
        return
    }

    Write-Output "stabilization wait: ${StabilizationSeconds}s"
    Start-Sleep -Seconds $StabilizationSeconds
}

$hasCompletedRun = $false
if ($Scenario -eq 'throughput') {
    foreach ($rate in $Rates) {
        for ($runIndex = 1; $runIndex -le $RepeatCount; $runIndex++) {
            if ($hasCompletedRun) {
                Wait-Stabilization
            }
            Invoke-K6Run $rate $runIndex
            $hasCompletedRun = $true
        }
    }
} else {
    for ($runIndex = 1; $runIndex -le $RepeatCount; $runIndex++) {
        if ($hasCompletedRun) {
            Wait-Stabilization
        }
        Invoke-K6Run 0 $runIndex
        $hasCompletedRun = $true
    }
}
