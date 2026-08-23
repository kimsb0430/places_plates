[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot

$blockedPathPatterns = @(
    '(^|/)(\.env($|\.)|application-local\.(yml|yaml|properties)$)',
    '(^|/)(secrets?|credentials?)/',
    '(?i)(service-account|firebase-adminsdk).*\.json$',
    '(?i)google-services\.json$',
    '(?i)\.(jks|keystore|p12|pfx|pem|key)$',
    '(?i)\.(db|sqlite|sqlite3)$',
    '(^|/)(private-photos?|raw-photos?|original-photos?)/',
    '(?i)\.(heic|dng|cr2|cr3|nef|arw)$'
)

$candidateFiles = @(git -C $repositoryRoot ls-files --cached --others --exclude-standard)
if ($LASTEXITCODE -ne 0) {
    throw 'Unable to list commit candidate files.'
}

$blockedFiles = @($candidateFiles | Where-Object {
    $path = $_ -replace '\\', '/'
    if ($path -match '(^|/)\.env\.example$') {
        return $false
    }
    foreach ($pattern in $blockedPathPatterns) {
        if ($path -match $pattern) {
            return $true
        }
    }
    return $false
})

if ($blockedFiles.Count -gt 0) {
    throw "Blocked sensitive files are commit candidates: $($blockedFiles -join ', ')"
}

$secretPatterns = [ordered]@{
    'Private key' = '-----BEGIN (RSA |EC |OPENSSH |DSA )?PRIVATE KEY-----'
    'OpenAI-style key' = '\bsk-[A-Za-z0-9_-]{20,}\b'
    'GitHub token' = '\bgh[pousr]_[A-Za-z0-9]{20,}\b'
    'AWS access key' = '\b(AKIA|ASIA)[A-Z0-9]{16}\b'
    'Google API key' = '\bAIza[A-Za-z0-9_-]{30,}\b'
    'Slack token' = '\bxox[baprs]-[A-Za-z0-9-]{20,}\b'
}

$findings = [System.Collections.Generic.List[string]]::new()
foreach ($relativePath in $candidateFiles) {
    $absolutePath = Join-Path $repositoryRoot $relativePath
    if (-not (Test-Path -LiteralPath $absolutePath -PathType Leaf)) {
        continue
    }
    try {
        $content = Get-Content -Raw -Encoding UTF8 -LiteralPath $absolutePath -ErrorAction Stop
    }
    catch {
        continue
    }
    foreach ($entry in $secretPatterns.GetEnumerator()) {
        if ($content -match $entry.Value) {
            $findings.Add("$relativePath ($($entry.Key))")
        }
    }
}

if ($findings.Count -gt 0) {
    throw "Potential secrets detected: $($findings -join ', ')"
}

Write-Host 'PASS: No blocked files or high-confidence secret patterns were found.' -ForegroundColor Green
