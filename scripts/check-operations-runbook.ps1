[CmdletBinding()]
param(
    [string]$Path = (Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\OPERATIONS_RUNBOOK.md')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw 'The operations runbook does not exist.'
}

$content = Get-Content -LiteralPath $Path -Raw
$requiredPatterns = @(
    'RPO',
    'RTO',
    'spring_session',
    'flyway_schema_history',
    'sanitized/',
    'variants/',
    'temporary/',
    'provision-supabase-database.ps1',
    'test-production-smoke.ps1',
    'vercel rollback',
    'gcloud run services update-traffic',
    'https://supabase.com/docs/guides/platform/backups',
    'https://cloud.google.com/run/docs/rollouts-rollbacks-traffic-migration',
    'https://vercel.com/docs/deployments/rollback-production-deployment'
)

foreach ($pattern in $requiredPatterns) {
    if (-not $content.Contains($pattern, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The operations runbook is missing a required recovery contract: $pattern"
    }
}

if ($content -match '(?i)(password|secret|api[_ -]?key)\s*[=:]\s*[A-Za-z0-9_\-]{16,}') {
    throw 'The operations runbook appears to contain a credential value.'
}

Write-Host 'PASS: Operations backup, restore, rollback, and incident contracts verified.' -ForegroundColor Green
