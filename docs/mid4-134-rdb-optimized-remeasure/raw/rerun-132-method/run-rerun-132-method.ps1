param(
    [string[]] $Scales = @('100k', '1m', '10m'),
    [string] $DbProject = 'monew-perf-134-rerun',
    [string] $K6Project = 'monew-k6-134-rerun',
    [string] $PgContainer = 'monew-perf-134-rerun-postgres-1',
    [string] $DbPort = '15434'
)

$ErrorActionPreference = 'Stop'

$RawDir = $PSScriptRoot
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$K6ResultDir = Join-Path $RepoRoot 'scripts\performance\activity-history\k6\results\mid4-134-rerun-132-method'
$SqlTemplate = Join-Path $RawDir 'sql-template.sql'

New-Item -ItemType Directory -Force -Path $RawDir | Out-Null
New-Item -ItemType Directory -Force -Path $K6ResultDir | Out-Null
Set-Location -LiteralPath $RepoRoot

function Set-DbEnv {
    $env:MONEW_DB_PORT = $DbPort
    $env:MONEW_DB_NAME = 'monew'
    $env:MONEW_DB_USERNAME = 'monew'
    $env:MONEW_DB_PASSWORD = 'change-me'
}

function Invoke-Psql {
    param(
        [string] $Sql,
        [string] $OutputPath
    )

    Set-DbEnv
    $output = $Sql | docker compose -p $DbProject exec -T postgres psql `
        -U $env:MONEW_DB_USERNAME `
        -d $env:MONEW_DB_NAME `
        -v ON_ERROR_STOP=1 2>&1
    $exitCode = $LASTEXITCODE

    $output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $OutputPath -Encoding UTF8

    if ($exitCode -ne 0) {
        throw "psql failed. output=$OutputPath"
    }
}

function Invoke-DockerStats {
    param(
        [string] $Scale,
        [string] $Phase
    )

    $outputPath = Join-Path $RawDir "docker-stats-$Phase-$Scale.txt"
    $output = docker stats --no-stream $PgContainer 2>&1
    $exitCode = $LASTEXITCODE

    $output | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $outputPath -Encoding UTF8

    if ($exitCode -ne 0) {
        throw "docker stats failed. output=$outputPath"
    }
}

function Invoke-Seed {
    param([string] $Scale)

    Write-Host "== seed $Scale =="
    Set-DbEnv
    $seedOutputPath = Join-Path $RawDir "seed-$Scale.out"

    $script:LastSeedOutput = $null
    $script:LastSeedExitCode = 0
    $duration = Measure-Command {
        $script:LastSeedOutput = docker compose -p $DbProject --profile perf-seed run --rm `
            -e "SEED_SCALE=$Scale" `
            postgres-seed 2>&1
        $script:LastSeedExitCode = $LASTEXITCODE
    }

    $script:LastSeedOutput | ForEach-Object { $_.ToString() } | Set-Content -LiteralPath $seedOutputPath -Encoding UTF8
    Add-Content -LiteralPath $seedOutputPath -Encoding UTF8 -Value ("seed_duration_seconds={0:N3}" -f $duration.TotalSeconds)

    if ($script:LastSeedExitCode -ne 0) {
        throw "seed failed. output=$seedOutputPath"
    }
}

function Write-Snapshot {
    param([string] $Scale)

    $targetUserId = '00000001-0000-4000-8000-000000000001'
    $snapshotSql = @"
\pset pager off
\echo scale=$Scale
SELECT now() AS measured_at;
SELECT pg_size_pretty(pg_database_size(current_database())) AS db_size;
SELECT relname, n_live_tup, pg_size_pretty(pg_total_relation_size(relid)) AS total_size
FROM pg_stat_user_tables
WHERE relname IN ('users', 'interests', 'keywords', 'subscriptions', 'articles', 'comments', 'comment_likes', 'article_views')
ORDER BY relname;
SELECT 'target_subscriptions' AS metric, COUNT(*) AS value
FROM subscriptions
WHERE user_id = '$targetUserId';
SELECT 'target_keywords' AS metric, COUNT(*) AS value
FROM keywords
WHERE interest_id IN (
    SELECT interest_id
    FROM subscriptions
    WHERE user_id = '$targetUserId'
);
"@

    Invoke-Psql $snapshotSql (Join-Path $RawDir "snapshot-$Scale.txt")
}

function Write-AppliedIndexes {
    $indexSql = @"
\pset pager off
SELECT tablename, indexname, indexdef
FROM pg_indexes
WHERE schemaname = 'public'
  AND indexname IN (
      'idx_comments_user_created_id',
      'idx_comments_article',
      'idx_comment_likes_liked_by_created_id',
      'idx_article_views_user_viewed_id',
      'idx_subscriptions_user_created_id'
  )
ORDER BY tablename, indexname;
"@

    Invoke-Psql $indexSql (Join-Path $RawDir 'applied-indexes.txt')
}

function Invoke-K6Once {
    param(
        [string] $Scale,
        [string] $Scenario
    )

    $summaryName = if ($Scenario -eq 'smoke') {
        "activity-history-$Scale-smoke-summary.json"
    } else {
        "activity-history-$Scale-optimized-summary.json"
    }

    $scriptBlock = {
        param(
            [string] $Root,
            [string] $Project,
            [string] $ScenarioValue,
            [string] $SummaryName
        )

        Set-Location -LiteralPath $Root
        $env:K6_SCENARIO = $ScenarioValue
        $env:K6_BASE_URL = 'http://host.docker.internal:8080'
        $env:K6_ACTIVITY_HISTORY_PATH_TEMPLATE = '/api/user-activities/{userId}'
        $env:K6_TARGET_USER_ID = '00000001-0000-4000-8000-000000000001'
        $env:K6_USER_ID_HEADER_NAME = 'Monew-Request-User-ID'
        $env:K6_EXPECTED_STATUS = '200'
        $env:K6_BASELINE_RATE = '20'
        $env:K6_BASELINE_TIME_UNIT = '1s'
        $env:K6_BASELINE_DURATION = '1m'
        $env:K6_BASELINE_PRE_ALLOCATED_VUS = '20'
        $env:K6_BASELINE_MAX_VUS = '100'
        $env:K6_SUMMARY_PATH = "/results/mid4-134-rerun-132-method/$SummaryName"

        $output = docker compose -f compose.k6.yaml -p $Project run --rm k6 2>&1
        [pscustomobject]@{
            ExitCode = $LASTEXITCODE
            Output = (($output | ForEach-Object { $_.ToString() }) -join "`n")
        }
    }

    Write-Host "== k6 $Scenario $Scale =="
    $job = Start-Job -ScriptBlock $scriptBlock -ArgumentList $RepoRoot, $K6Project, $Scenario, $summaryName
    return [pscustomobject]@{
        Job = $job
        SummaryName = $summaryName
        Scenario = $Scenario
        Scale = $Scale
    }
}

function Complete-K6 {
    param([object] $K6Run)

    $outputPath = Join-Path $RawDir ("k6-{0}-{1}.out" -f $K6Run.Scale, $K6Run.Scenario)
    Wait-Job $K6Run.Job | Out-Null
    $result = Receive-Job $K6Run.Job
    Remove-Job $K6Run.Job

    $result.Output | Set-Content -LiteralPath $outputPath -Encoding UTF8

    if ($result.ExitCode -ne 0) {
        throw "k6 $($K6Run.Scenario) failed for $($K6Run.Scale). output=$outputPath"
    }

    $sourceSummary = Join-Path $K6ResultDir $K6Run.SummaryName
    $targetSummary = Join-Path $RawDir $K6Run.SummaryName
    Copy-Item -LiteralPath $sourceSummary -Destination $targetSummary -Force
}

function Invoke-K6BaselineWithStats {
    param([string] $Scale)

    Invoke-Psql 'SELECT pg_stat_reset();' (Join-Path $RawDir "pg-stat-reset-$Scale.txt")

    $k6Run = Invoke-K6Once $Scale 'baseline'
    Start-Sleep -Seconds 30

    Invoke-DockerStats $Scale 'mid'

    $activitySql = @"
\pset pager off
SELECT COALESCE(state, '') AS state,
       COALESCE(wait_event_type, '') AS wait_event_type,
       COALESCE(wait_event, '') AS wait_event,
       COUNT(*) AS count
FROM pg_stat_activity
WHERE datname = current_database()
GROUP BY state, wait_event_type, wait_event
ORDER BY state, wait_event_type, wait_event;
"@
    Invoke-Psql $activitySql (Join-Path $RawDir "pg-stat-activity-mid-$Scale.txt")

    Complete-K6 $k6Run

    $databaseSql = @"
\pset pager off
SELECT datname,
       xact_commit,
       xact_rollback,
       blks_read,
       blks_hit,
       ROUND(100.0 * blks_hit / NULLIF(blks_hit + blks_read, 0), 2) AS cache_hit_pct,
       tup_returned,
       tup_fetched,
       temp_files,
       temp_bytes,
       deadlocks
FROM pg_stat_database
WHERE datname = current_database();
"@
    Invoke-Psql $databaseSql (Join-Path $RawDir "pg-stat-database-after-$Scale.txt")
    Invoke-DockerStats $Scale 'after'
}

function Invoke-SqlMeasure {
    param([string] $Scale)

    Write-Host "== sql $Scale =="
    $sql = (Get-Content -LiteralPath $SqlTemplate -Raw).Replace('__SCALE__', $Scale)
    Invoke-Psql $sql (Join-Path $RawDir "sql-$Scale.out")
}

Write-Host "repo=$RepoRoot"
Write-Host "raw=$RawDir"
Write-Host "scales=$($Scales -join ',')"
Write-AppliedIndexes

foreach ($scale in $Scales) {
    Invoke-Seed $scale
    Write-Snapshot $scale

    $smoke = Invoke-K6Once $scale 'smoke'
    Complete-K6 $smoke

    Invoke-K6BaselineWithStats $scale
    Invoke-SqlMeasure $scale
}
