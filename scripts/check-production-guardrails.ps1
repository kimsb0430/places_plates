[CmdletBinding()]
param(
    [string]$Path = (Join-Path (Split-Path -Parent $PSScriptRoot) 'config\production-guardrails.json'),
    [string]$DocumentationPath = (Join-Path (Split-Path -Parent $PSScriptRoot) 'docs\PRODUCTION_GUARDRAILS.md')
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) {
    throw 'The production guardrails configuration does not exist.'
}

if (-not (Test-Path -LiteralPath $DocumentationPath -PathType Leaf)) {
    throw 'The production guardrails documentation does not exist.'
}

try {
    $guardrails = Get-Content -LiteralPath $Path -Raw | ConvertFrom-Json -Depth 20
}
catch {
    throw 'The production guardrails configuration is not valid JSON.'
}

function Assert-Equal {
    param(
        [Parameter(Mandatory)] $Actual,
        [Parameter(Mandatory)] $Expected,
        [Parameter(Mandatory)] [string] $Message
    )

    if ($Actual -ne $Expected) {
        throw $Message
    }
}

function Assert-Sequence {
    param(
        [Parameter(Mandatory)] [object[]] $Actual,
        [Parameter(Mandatory)] [object[]] $Expected,
        [Parameter(Mandatory)] [string] $Message
    )

    if (($Actual | ConvertTo-Json -Compress) -ne ($Expected | ConvertTo-Json -Compress)) {
        throw $Message
    }
}

Assert-Equal $guardrails.schemaVersion 1 'The production guardrails schema version must be 1.'
Assert-Equal $guardrails.domains.frontend 'https://placesplates.vercel.app' 'The canonical frontend domain changed without updating the production contract.'
Assert-Equal $guardrails.domains.api 'https://places-plates-api-481849639838.asia-northeast3.run.app' 'The canonical API domain changed without updating the production contract.'
Assert-Equal $guardrails.domains.httpsOnly $true 'Production domains must require HTTPS.'
Assert-Sequence @($guardrails.domains.allowedFrontendOrigins) @('https://placesplates.vercel.app') 'The API CORS origin must be the exact production frontend origin.'
Assert-Sequence @($guardrails.domains.googleMapsHttpReferrers) @('https://placesplates.vercel.app/*') 'The Maps browser key must be restricted to the production frontend referrer.'

Assert-Equal $guardrails.googleCloud.projectId 'placesplates' 'The Google Cloud project must remain explicit.'
Assert-Equal $guardrails.googleCloud.region 'asia-northeast3' 'The Cloud Run region must remain explicit.'
Assert-Equal $guardrails.googleCloud.monthlyBudget.amount 500 'The initial monthly Google Cloud alert budget must be JPY 500.'
Assert-Equal $guardrails.googleCloud.monthlyBudget.currency 'JPY' 'The Google Cloud alert budget currency must be JPY.'
Assert-Sequence @($guardrails.googleCloud.monthlyBudget.actualSpendThresholds) @(0.5, 0.8, 1.0) 'The Google Cloud actual spend thresholds must be 50, 80, and 100 percent.'
Assert-Equal $guardrails.googleCloud.monthlyBudget.forecastSpendThreshold 1.0 'The Google Cloud forecast threshold must be 100 percent.'
Assert-Equal $guardrails.googleCloud.monthlyBudget.automaticBillingDisable $false 'A budget alert must not automatically disable billing.'
Assert-Equal $guardrails.googleCloud.cloudRun.minInstances 0 'Cloud Run must be allowed to scale to zero.'
Assert-Equal $guardrails.googleCloud.cloudRun.maxInstances 1 'Cloud Run must initially be limited to one instance.'
Assert-Equal $guardrails.googleCloud.googleMaps.monthlyDynamicMapLoadTarget 9000 'The monthly Dynamic Maps operating target must remain 9,000 loads.'
Assert-Equal $guardrails.googleCloud.googleMaps.monthlyPlacesTextSearchTarget 1000 'The monthly Places Text Search operating target must remain 1,000 requests.'
Assert-Equal $guardrails.googleCloud.googleMaps.dynamicMapLoadsPerMinuteProviderLimit 30000 'The documented Dynamic Maps provider limit must remain 30,000 loads per minute.'
Assert-Equal $guardrails.googleCloud.googleMaps.dynamicMapLoadsPerMinuteAdjustable $false 'The Dynamic Maps provider limit must be documented as non-adjustable.'
Assert-Equal $guardrails.googleCloud.googleMaps.placesTextSearchRequestsPerDayProviderLimit 75000 'The documented Places Text Search provider limit must remain 75,000 requests per day.'
Assert-Equal $guardrails.googleCloud.googleMaps.placesTextSearchRequestsPerMinuteProviderLimit 600 'The documented Places Text Search provider limit must remain 600 requests per minute.'
Assert-Equal $guardrails.googleCloud.googleMaps.placesQuotaChangeStatus 'BLOCKED_BY_FREE_TRIAL' 'The current Places quota edit constraint must remain explicit until the billing trial changes.'
Assert-Equal $guardrails.googleCloud.googleMaps.browserKeyAllowedApi 'Maps JavaScript API' 'The browser key must allow only Maps JavaScript API.'
Assert-Equal $guardrails.googleCloud.googleMaps.serverKeyAllowedApi 'Places API (New)' 'The server key must allow only Places API (New).'

Assert-Equal $guardrails.supabase.plan 'FREE' 'The initial Supabase cost model must remain Free.'
Assert-Equal $guardrails.supabase.paidOverage $false 'Supabase paid overage must not be enabled on the initial plan.'
Assert-Sequence @($guardrails.supabase.reviewThresholds) @(0.5, 0.8, 1.0) 'Supabase usage reviews must use 50, 80, and 100 percent thresholds.'
Assert-Equal $guardrails.vercel.plan 'HOBBY' 'The initial Vercel cost model must remain Hobby.'
Assert-Equal $guardrails.vercel.paidOverage $false 'Vercel paid overage must not be enabled on the initial plan.'
Assert-Sequence @($guardrails.vercel.reviewThresholds) @(0.5, 0.8, 1.0) 'Vercel usage reviews must use 50, 80, and 100 percent thresholds.'
Assert-Sequence @($guardrails.vercel.notificationChannels) @('WEB', 'EMAIL') 'Vercel web and email usage notifications must remain enabled.'

$raw = Get-Content -LiteralPath $Path -Raw
if ($raw -match '(?i)(password|secret|api[_ -]?key)\s*[=:]\s*[A-Za-z0-9_\-]{16,}') {
    throw 'The production guardrails configuration appears to contain a credential value.'
}

$documentation = Get-Content -LiteralPath $DocumentationPath -Raw
$requiredDocumentationPatterns = @(
    'PRODUCTION_FRONTEND_URL',
    'PRODUCTION_API_URL',
    'Places Plates',
    'billing budgets create',
    '--min=0',
    '--max=1',
    'Maps JavaScript API',
    'Places API (New)',
    'Supabase',
    'Vercel',
    'https://cloud.google.com/billing/docs/how-to/budgets',
    'https://supabase.com/docs/guides/platform/cost-control',
    'https://vercel.com/docs/plans/hobby'
)

foreach ($pattern in $requiredDocumentationPatterns) {
    if (-not $documentation.Contains($pattern, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The production guardrails documentation is missing a required contract: $pattern"
    }
}

if ($documentation -match '(?i)(password|secret|api[_ -]?key)\s*[=:]\s*[A-Za-z0-9_\-]{16,}') {
    throw 'The production guardrails documentation appears to contain a credential value.'
}

Write-Host 'PASS: Production domains, budgets, quotas, and usage guardrails verified.' -ForegroundColor Green
