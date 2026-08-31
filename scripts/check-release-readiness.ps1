[CmdletBinding()]
param(
    [string]$Tag = '',
    [switch]$RequireTaggedHead
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$contractPath = Join-Path $repositoryRoot 'config\release.json'
$frontendPackagePath = Join-Path $repositoryRoot 'frontend\package.json'
$backendBuildPath = Join-Path $repositoryRoot 'backend\build.gradle.kts'
$releaseGuidePath = Join-Path $repositoryRoot 'docs\RELEASE_V1.md'
$releaseNotesPath = Join-Path $repositoryRoot 'docs\releases\v1.0.0.md'
$releaseWorkflowPath = Join-Path $repositoryRoot '.github\workflows\release.yml'
$guardrailsPath = Join-Path $repositoryRoot 'config\production-guardrails.json'

$requiredFiles = @(
    $contractPath,
    $frontendPackagePath,
    $backendBuildPath,
    $releaseGuidePath,
    $releaseNotesPath,
    $releaseWorkflowPath,
    $guardrailsPath
)

foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Release readiness file is missing: $requiredFile"
    }
}

try {
    $contract = Get-Content -LiteralPath $contractPath -Raw | ConvertFrom-Json -Depth 20
    $frontendPackage = Get-Content -LiteralPath $frontendPackagePath -Raw | ConvertFrom-Json -Depth 20
    $guardrails = Get-Content -LiteralPath $guardrailsPath -Raw | ConvertFrom-Json -Depth 20
}
catch {
    throw 'A release readiness JSON file is invalid.'
}

if ($contract.schemaVersion -ne 1) {
    throw 'The release contract schema version must be 1.'
}
if ($contract.productName -ne 'Places & Plates') {
    throw 'The release product name changed unexpectedly.'
}
if ($contract.version -notmatch '^\d+\.\d+\.\d+$') {
    throw 'The release version must use semantic versioning.'
}
if ($contract.tag -ne "v$($contract.version)") {
    throw 'The release tag must match the configured semantic version.'
}
if ($contract.releaseChannel -ne 'STABLE') {
    throw 'The v1 release channel must remain stable.'
}
if ($contract.releaseName -ne 'Places & Plates v1.0.0') {
    throw 'The v1 release name changed unexpectedly.'
}
if ($contract.tagPolicy -ne 'ANNOTATED_TAG_ON_VERIFIED_MAIN_COMMIT') {
    throw 'The release must use an annotated tag on a verified main commit.'
}
if ($contract.rollbackPolicy -ne 'PROVIDER_PREVIOUS_DEPLOYMENT_AND_DATABASE_FORWARD_FIX') {
    throw 'The release rollback policy changed unexpectedly.'
}
if ($frontendPackage.version -ne $contract.version) {
    throw 'The frontend package version does not match the release contract.'
}

$backendBuild = Get-Content -LiteralPath $backendBuildPath -Raw
if (-not $backendBuild.Contains("version = `"$($contract.version)`"")) {
    throw 'The backend version does not match the release contract.'
}

if ($contract.canonicalFrontendUrl -ne $guardrails.domains.frontend -or
    $contract.canonicalApiUrl -ne $guardrails.domains.api) {
    throw 'Release domains do not match the production guardrails.'
}
if ($contract.minimumDatabaseMigration -ne 16) {
    throw 'The v1 release must require database migration V16.'
}
if ($contract.watermarkPolicyVersion -ne 'places-plates-corner-v1') {
    throw 'The v1 release must require the current watermark policy.'
}

$requiredChecks = @($contract.requiredMainChecks)
$expectedChecks = @('Secret protection', 'Verify', 'Production smoke')
if (($requiredChecks | ConvertTo-Json -Compress) -ne ($expectedChecks | ConvertTo-Json -Compress)) {
    throw 'The release contract must require all main deployment checks.'
}

$releaseGuide = Get-Content -LiteralPath $releaseGuidePath -Raw
$releaseNotes = Get-Content -LiteralPath $releaseNotesPath -Raw
$releaseWorkflow = Get-Content -LiteralPath $releaseWorkflowPath -Raw
$requiredPatterns = @(
    'v1.0.0',
    'Production smoke',
    'V16',
    'places-plates-corner-v1',
    'ROLLBACK',
    'https://placesplates.vercel.app',
    'https://places-plates-api-481849639838.asia-northeast3.run.app'
)

foreach ($pattern in $requiredPatterns) {
    if (-not $releaseGuide.Contains($pattern, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "The release guide is missing a required contract: $pattern"
    }
}

if (-not $releaseNotes.Contains('Places & Plates v1.0.0')) {
    throw 'The v1 release notes are incomplete.'
}

$requiredWorkflowPatterns = @(
    "- 'v*.*.*'",
    'fetch-depth: 0',
    'check-release-readiness.ps1',
    '-RequireTaggedHead',
    'git merge-base --is-ancestor',
    'gh run list',
    "@('Secret protection', 'Verify', 'Production smoke')",
    'test-production-smoke.ps1',
    'gh release create',
    '--verify-tag',
    '--notes-file docs/releases/v1.0.0.md'
)

foreach ($pattern in $requiredWorkflowPatterns) {
    if (-not $releaseWorkflow.Contains($pattern)) {
        throw "The release workflow is missing a required gate: $pattern"
    }
}

$normalizedTag = $Tag.Trim()
if ($normalizedTag -and $normalizedTag -ne $contract.tag) {
    throw 'The supplied tag does not match the release contract.'
}

if ($RequireTaggedHead) {
    if (-not $normalizedTag) {
        throw 'Tag is required when validating a tagged release commit.'
    }
    $headTags = @(git -C $repositoryRoot tag --points-at HEAD)
    if ($LASTEXITCODE -ne 0 -or $headTags -notcontains $normalizedTag) {
        throw 'The configured release tag does not point at HEAD.'
    }
}

Write-Host "PASS: Places & Plates $($contract.version) release readiness contract verified." -ForegroundColor Green
