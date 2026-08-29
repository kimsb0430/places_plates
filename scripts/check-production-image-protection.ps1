param(
    [string]$FrontendBaseUrl = 'https://placesplates.vercel.app',
    [string]$BackendBaseUrl = 'https://places-plates-api-481849639838.asia-northeast3.run.app'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$frontend = $FrontendBaseUrl.TrimEnd('/')
$backend = $BackendBaseUrl.TrimEnd('/')
$client = [System.Net.Http.HttpClient]::new()
$client.Timeout = [TimeSpan]::FromSeconds(30)

function Get-HeaderValue {
    param(
        [System.Net.Http.HttpResponseMessage]$Response,
        [string]$Name
    )

    try {
        if ($Response.Headers.Contains($Name)) {
            return [string]::Join(', ', $Response.Headers.GetValues($Name))
        }
    }
    catch {
    }

    try {
        if ($Response.Content.Headers.Contains($Name)) {
            return [string]::Join(', ', $Response.Content.Headers.GetValues($Name))
        }
    }
    catch {
    }
    return $null
}

function Assert-ProtectedImageResponse {
    param(
        [string]$Label,
        [string]$Uri
    )

    $response = $client.GetAsync(
        $Uri,
        [System.Net.Http.HttpCompletionOption]::ResponseHeadersRead
    ).GetAwaiter().GetResult()
    try {
        if (-not $response.IsSuccessStatusCode) {
            throw "$Label returned HTTP $([int]$response.StatusCode)."
        }

        $contentType = Get-HeaderValue -Response $response -Name 'Content-Type'
        if (-not $contentType.StartsWith('image/')) {
            throw "$Label did not return an image Content-Type. Actual: $contentType"
        }

        $expectedHeaders = @{
            'Cross-Origin-Resource-Policy' = 'same-origin'
            'X-Frame-Options' = 'DENY'
            'X-Content-Type-Options' = 'nosniff'
        }
        foreach ($entry in $expectedHeaders.GetEnumerator()) {
            $actual = Get-HeaderValue -Response $response -Name $entry.Key
            if ($actual -ne $entry.Value) {
                throw "$Label header $($entry.Key) expected '$($entry.Value)' but received '$actual'."
            }
        }

        $contentSecurityPolicy = Get-HeaderValue -Response $response -Name 'Content-Security-Policy'
        if ($contentSecurityPolicy -notmatch "frame-ancestors 'none'") {
            throw "$Label Content-Security-Policy does not block frame ancestors."
        }
    }
    finally {
        $response.Dispose()
    }
}

try {
    $listJson = $client.GetStringAsync("$backend/api/v1/public/posts?sort=LATEST").GetAwaiter().GetResult()
    $list = $listJson | ConvertFrom-Json
    $post = @($list.posts) | Where-Object { $null -ne $_.cover } | Select-Object -First 1
    if ($null -eq $post) {
        throw 'A PUBLIC + PUBLISHED post with a cover is required for the production image smoke test.'
    }

    $backendImageUrl = "$backend$($post.cover.path)"
    $frontendImagePath = $post.cover.path -replace '^/api/v1/public/posts/', '/api/public-images/posts/'
    $frontendImageUrl = "$frontend$frontendImagePath"

    Assert-ProtectedImageResponse -Label 'Cloud Run public image' -Uri $backendImageUrl
    Assert-ProtectedImageResponse -Label 'Vercel same-origin image proxy' -Uri $frontendImageUrl

    Write-Host 'PASS: Production public image protection headers verified.'
}
finally {
    $client.Dispose()
}
