$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$frontendRoot = Join-Path $repositoryRoot 'frontend'
$backendRoot = Join-Path $repositoryRoot 'backend'

Push-Location $frontendRoot
try {
    pnpm install --frozen-lockfile
    if ($LASTEXITCODE -ne 0) { throw 'Frontend dependency installation failed.' }
    pnpm lint
    if ($LASTEXITCODE -ne 0) { throw 'Frontend lint failed.' }
    pnpm typecheck
    if ($LASTEXITCODE -ne 0) { throw 'Frontend type check failed.' }
    pnpm build
    if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
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
