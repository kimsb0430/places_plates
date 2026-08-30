[CmdletBinding()]
param(
    [ValidatePattern('^https://')]
    [string]$FrontendBaseUrl = 'https://placesplates.vercel.app',

    [ValidatePattern('^https://')]
    [string]$ApiBaseUrl = 'https://places-plates-api-481849639838.asia-northeast3.run.app',

    [string]$ExpectedCommitSha = '',

    [ValidateRange(1, 60)]
    [int]$RetryCount = 30,

    [ValidateRange(0, 60)]
    [int]$RetryDelaySeconds = 20
)

$ErrorActionPreference = 'Stop'
$normalizedCommitSha = $ExpectedCommitSha.Trim().ToLowerInvariant()

if ($normalizedCommitSha -and $normalizedCommitSha -notmatch '^[0-9a-f]{40}$') {
    throw 'ExpectedCommitSha must be an empty value or a full 40-character Git commit SHA.'
}

function Get-SmokeResponse {
    param([Parameter(Mandatory = $true)][string]$Uri)

    $response = Invoke-WebRequest -UseBasicParsing -Uri $Uri -TimeoutSec 30
    if ($response.StatusCode -ne 200) {
        throw "Expected HTTP 200 from $Uri but received $($response.StatusCode)."
    }
    return $response
}

function Assert-HeaderEquals {
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedValue
    )

    $actualValue = [string]$Response.Headers[$Name]
    if (-not $actualValue.Equals($ExpectedValue, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Header $Name did not match the expected value."
    }
}

function Assert-HeaderContains {
    param(
        [Parameter(Mandatory = $true)]$Response,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$ExpectedFragment
    )

    $actualValue = [string]$Response.Headers[$Name]
    if (-not $actualValue.Contains($ExpectedFragment, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "Header $Name did not contain the required policy."
    }
}

function Assert-FrontendSecurityHeaders {
    param([Parameter(Mandatory = $true)]$Response)

    Assert-HeaderContains -Response $Response -Name 'Content-Security-Policy' -ExpectedFragment "frame-ancestors 'none'"
    Assert-HeaderContains -Response $Response -Name 'Content-Security-Policy' -ExpectedFragment "object-src 'none'"
    Assert-HeaderContains -Response $Response -Name 'Permissions-Policy' -ExpectedFragment 'camera=()'
    Assert-HeaderEquals -Response $Response -Name 'X-Frame-Options' -ExpectedValue 'DENY'
    Assert-HeaderEquals -Response $Response -Name 'X-Content-Type-Options' -ExpectedValue 'nosniff'
}

function Assert-ApiSecurityHeaders {
    param([Parameter(Mandatory = $true)]$Response)

    Assert-HeaderContains -Response $Response -Name 'Content-Security-Policy' -ExpectedFragment "default-src 'none'"
    Assert-HeaderContains -Response $Response -Name 'Content-Security-Policy' -ExpectedFragment "frame-ancestors 'none'"
    Assert-HeaderEquals -Response $Response -Name 'Referrer-Policy' -ExpectedValue 'no-referrer'
    Assert-HeaderEquals -Response $Response -Name 'X-Frame-Options' -ExpectedValue 'DENY'
    Assert-HeaderEquals -Response $Response -Name 'X-Content-Type-Options' -ExpectedValue 'nosniff'
}

function Assert-StatusBody {
    param([Parameter(Mandatory = $true)]$Response)

    $payload = $Response.Content | ConvertFrom-Json
    if ($payload.status -ne 'UP') {
        throw 'Deployment status response was not UP.'
    }
}

function Assert-ExpectedCommit {
    param([Parameter(Mandatory = $true)]$Response)

    if (-not $normalizedCommitSha) {
        return
    }

    $actualCommitSha = ([string]$Response.Headers['X-Places-Plates-Commit']).Trim().ToLowerInvariant()
    if ($actualCommitSha -ne $normalizedCommitSha) {
        throw 'The deployed commit does not match the expected merge commit.'
    }
}

function Assert-NoPrivateStoragePath {
    param([Parameter(Mandatory = $true)]$Response)

    $blockedPatterns = @('storageKey', 'temporaryStorageKey', 'temporary/', 'sanitized/', 'variants/')
    foreach ($blockedPattern in $blockedPatterns) {
        if ($Response.Content.Contains($blockedPattern, [System.StringComparison]::OrdinalIgnoreCase)) {
            throw 'A public API response contains a private storage path or key field.'
        }
    }
}

function Invoke-ProductionSmoke {
    $frontendRoot = $FrontendBaseUrl.TrimEnd('/')
    $apiRoot = $ApiBaseUrl.TrimEnd('/')

    foreach ($path in @('/', '/posts', '/map')) {
        $response = Get-SmokeResponse -Uri "$frontendRoot$path"
        Assert-FrontendSecurityHeaders -Response $response
    }

    $frontendDeployment = Get-SmokeResponse -Uri "$frontendRoot/api/deployment"
    Assert-FrontendSecurityHeaders -Response $frontendDeployment
    Assert-StatusBody -Response $frontendDeployment
    Assert-ExpectedCommit -Response $frontendDeployment

    $apiHealth = Get-SmokeResponse -Uri "$apiRoot/api/v1/health"
    Assert-ApiSecurityHeaders -Response $apiHealth
    Assert-HeaderContains -Response $apiHealth -Name 'Cache-Control' -ExpectedFragment 'no-store'
    Assert-StatusBody -Response $apiHealth
    Assert-ExpectedCommit -Response $apiHealth

    foreach ($path in @('/api/v1/public/posts', '/api/v1/map/posts')) {
        $response = Get-SmokeResponse -Uri "$apiRoot$path"
        Assert-ApiSecurityHeaders -Response $response
        Assert-NoPrivateStoragePath -Response $response
    }
}

$lastFailure = $null
for ($attempt = 1; $attempt -le $RetryCount; $attempt++) {
    try {
        Invoke-ProductionSmoke
        Write-Host "PASS: Production smoke succeeded for frontend, API, and commit $($normalizedCommitSha ? $normalizedCommitSha : 'not-enforced')." -ForegroundColor Green
        exit 0
    }
    catch {
        $lastFailure = $_.Exception.Message
        Write-Warning "Production smoke attempt $attempt of $RetryCount failed: $lastFailure"
        if ($attempt -lt $RetryCount -and $RetryDelaySeconds -gt 0) {
            Start-Sleep -Seconds $RetryDelaySeconds
        }
    }
}

throw "Production smoke failed after $RetryCount attempt(s). Last failure: $lastFailure"
