param(
    [string] $ResultSet = 'mid4-227-rdb-explain-compare',
    [switch] $Check
)

$ErrorActionPreference = 'Stop'

$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..\..\..')).Path
$resultRoot = Join-Path $repoRoot "scripts\performance\activity-history\k6\results\$ResultSet\sql"
$docRoot = Join-Path $repoRoot 'docs\mid4-227-rdb-bottleneck-remeasure'
$documents = [ordered]@{
    'recent-comments' = 'recent-comments.md'
    'recent-comment-likes' = 'recent-liked-comments.md'
    'recent-article-views' = 'recent-article-views.md'
    'subscribed-interests' = 'subscribed-interests.md'
}
$overlays = @('fanout', 'exclusion')
$versions = @('before', 'after')
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)

function Get-PlanBody {
    param([Parameter(Mandatory = $true)][string] $Path)

    if (-not (Test-Path -LiteralPath $Path)) {
        throw "Missing text plan: $Path"
    }

    $lines = @([IO.File]::ReadAllLines($Path, [Text.Encoding]::UTF8))
    if ($lines.Count -lt 4 -or $lines[0].Trim() -ne 'QUERY PLAN') {
        throw "Unexpected psql text plan format: $Path"
    }

    $start = 2
    $end = $lines.Count - 1
    while ($end -ge $start -and [string]::IsNullOrWhiteSpace($lines[$end])) {
        $end--
    }
    if ($lines[$end].Trim() -notmatch '^\(\d+ rows?\)$') {
        throw "Missing psql row count footer: $Path"
    }
    $end--

    $bodyLines = @($lines[$start..$end] | ForEach-Object {
        if ($_.StartsWith(' ')) { $_.Substring(1) } else { $_ }
    })
    $body = ($bodyLines -join "`n").TrimEnd()
    if ($body -notmatch '(?m)^Planning Time: [0-9.]+ ms$' -or
        $body -notmatch '(?m)^Execution Time: [0-9.]+ ms$' -or
        $body -notmatch '(?m)^\s+Buffers:') {
        throw "Incomplete text plan: $Path"
    }
    return $body
}

foreach ($queryName in $documents.Keys) {
    $docPath = Join-Path $docRoot $documents[$queryName]
    $document = [IO.File]::ReadAllText($docPath, [Text.Encoding]::UTF8)
    $startMatch = [regex]::Match($document, '(?m)^## .*?(?:plan|\uC2E4\uD589\uACC4\uD68D).*?\uC6D0\uBB38(?: \(QUERY PLAN\))?\r?$')
    if (-not $startMatch.Success) {
        throw "Unable to locate plan section: $docPath"
    }
    $startIndex = $startMatch.Index
    $firstPlanHeading = [regex]::Match(
        $document.Substring($startIndex + $startMatch.Length),
        '(?m)^### fanout before\r?$'
    )
    if (-not $firstPlanHeading.Success) {
        throw "Unable to locate first plan: $docPath"
    }
    $introStartIndex = $startIndex + $startMatch.Length
    $firstPlanIndex = $introStartIndex + $firstPlanHeading.Index
    $sectionIntro = $document.Substring($introStartIndex, $firstPlanIndex - $introStartIndex).Trim()
    $nextHeading = [regex]::Match(
        $document.Substring($startIndex + $startMatch.Length),
        '(?m)^## [^#].*\r?$'
    )
    if (-not $nextHeading.Success) {
        throw "Unable to locate section after plans: $docPath"
    }
    $endIndex = $startIndex + $startMatch.Length + $nextHeading.Index

    $section = New-Object Text.StringBuilder
    [void] $section.AppendLine($startMatch.Value.Replace(' (QUERY PLAN)', '').TrimEnd())
    [void] $section.AppendLine()
    if (-not [string]::IsNullOrWhiteSpace($sectionIntro)) {
        [void] $section.AppendLine($sectionIntro)
        [void] $section.AppendLine()
    }

    foreach ($overlay in $overlays) {
        foreach ($version in $versions) {
            $planPath = Join-Path $resultRoot "$overlay\$version-$queryName-explain-text.txt"
            $planBody = Get-PlanBody -Path $planPath
            [void] $section.AppendLine("### $overlay $version")
            [void] $section.AppendLine()
            [void] $section.AppendLine('```text')
            [void] $section.AppendLine($planBody)
            [void] $section.AppendLine('```')
            [void] $section.AppendLine()
        }
    }

    $updated = $document.Substring(0, $startIndex) + $section.ToString() + $document.Substring($endIndex)
    if ($Check) {
        $normalizedUpdated = $updated.Replace("`r`n", "`n")
        $normalizedDocument = $document.Replace("`r`n", "`n")
        if ($normalizedUpdated -cne $normalizedDocument) {
            throw "Document plans do not match captured text plans: $docPath"
        }
        Write-Output "Verified text plans: $docPath"
    } else {
        [IO.File]::WriteAllText($docPath, $updated, $utf8NoBom)
        Write-Output "Rendered text plans: $docPath"
    }
}
