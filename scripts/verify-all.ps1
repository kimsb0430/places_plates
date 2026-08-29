$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repositoryRoot 'frontend'
$backendRoot = Join-Path $repositoryRoot 'backend'

& (Join-Path $PSScriptRoot 'check-secrets.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Secret scan failed.' }

& (Join-Path $PSScriptRoot 'verify-cloud-run-container.ps1')
if ($LASTEXITCODE -ne 0) { throw 'Cloud Run container contract verification failed.' }

Push-Location $frontendRoot
try {
    pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    pnpm test
    if ($LASTEXITCODE -ne 0) { throw 'Frontend tests failed.' }
    pnpm lint
    if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed.' }
    pnpm typecheck
    if ($LASTEXITCODE -ne 0) { throw 'Frontend type check failed.' }
    pnpm build:vercel
    if ($LASTEXITCODE -ne 0) { throw 'Vercel Next.js build failed.' }
    & (Join-Path $PSScriptRoot 'check-map-lazy-load-artifact.ps1') -Path (Join-Path $frontendRoot '.next')
    if ($LASTEXITCODE -ne 0) { throw 'Map lazy-load artifact verification failed.' }
    & (Join-Path $PSScriptRoot 'check-public-artifact.ps1') -Path (Join-Path $frontendRoot '.next')
    if ($LASTEXITCODE -ne 0) { throw 'Vercel artifact scan failed.' }
    pnpm build
    if ($LASTEXITCODE -ne 0) { throw 'Sites Vinext build failed.' }
    & (Join-Path $PSScriptRoot 'check-public-artifact.ps1') -Path (Join-Path $frontendRoot 'dist')
    if ($LASTEXITCODE -ne 0) { throw 'Sites artifact scan failed.' }
}
finally {
    Pop-Location
}

Push-Location $backendRoot
try {
    .\gradlew.bat test bootJar --no-daemon
    if ($LASTEXITCODE -ne 0) { throw 'Backend verification failed.' }
}
finally {
    Pop-Location
}
