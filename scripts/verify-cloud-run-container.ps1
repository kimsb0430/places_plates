$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$dockerfilePath = Join-Path $repositoryRoot 'backend\Dockerfile'
$dockerignorePath = Join-Path $repositoryRoot 'backend\.dockerignore'
$cloudBuildPath = Join-Path $repositoryRoot 'backend\cloudbuild.yaml'
$legacyBuildpackPath = Join-Path $repositoryRoot 'backend\project.toml'

if (-not (Test-Path -LiteralPath $dockerfilePath -PathType Leaf)) {
    throw 'Cloud Run Dockerfile is missing.'
}

if (Test-Path -LiteralPath $legacyBuildpackPath) {
    throw 'Legacy Buildpack configuration must not coexist with the Cloud Run Dockerfile.'
}

if (-not (Test-Path -LiteralPath $dockerignorePath -PathType Leaf)) {
    throw 'Cloud Run .dockerignore is missing.'
}

if (-not (Test-Path -LiteralPath $cloudBuildPath -PathType Leaf)) {
    throw 'Cloud Run Cloud Build configuration is missing.'
}

$dockerfile = Get-Content -LiteralPath $dockerfilePath -Raw
$requiredPatterns = @(
    'FROM eclipse-temurin:21-jdk-jammy AS builder',
    'FROM eclipse-temurin:21-jre-jammy AS runtime',
    'apt-get install --yes --no-install-recommends liblcms2-2',
    'USER placesplates',
    'ENTRYPOINT ["java", "-jar", "/app/app.jar"]'
)

foreach ($requiredPattern in $requiredPatterns) {
    if (-not $dockerfile.Contains($requiredPattern)) {
        throw "Cloud Run Dockerfile contract is missing: $requiredPattern"
    }
}

$dockerignore = Get-Content -LiteralPath $dockerignorePath -Raw
$requiredIgnorePatterns = @(
    '.env',
    'src/test',
    'src/main/resources/application-local.*',
    '*.pem',
    '*.key'
)

foreach ($requiredIgnorePattern in $requiredIgnorePatterns) {
    if (-not (($dockerignore -split "`r?`n") -contains $requiredIgnorePattern)) {
        throw "Cloud Run .dockerignore contract is missing: $requiredIgnorePattern"
    }
}

$cloudBuild = Get-Content -LiteralPath $cloudBuildPath -Raw
$requiredCloudBuildPatterns = @(
    'name: gcr.io/cloud-builders/docker',
    'dir: backend',
    '${_AR_HOSTNAME}/${_AR_PROJECT_ID}/${_AR_REPOSITORY}/${REPO_NAME}/${_SERVICE_NAME}:${COMMIT_SHA}',
    'name: gcr.io/google.com/cloudsdktool/cloud-sdk:slim',
    '--image=${_AR_HOSTNAME}/${_AR_PROJECT_ID}/${_AR_REPOSITORY}/${REPO_NAME}/${_SERVICE_NAME}:${COMMIT_SHA}',
    '--update-env-vars=APP_COMMIT_SHA=${COMMIT_SHA}',
    '--region=${_DEPLOY_REGION}'
)

foreach ($requiredCloudBuildPattern in $requiredCloudBuildPatterns) {
    if (-not $cloudBuild.Contains($requiredCloudBuildPattern)) {
        throw "Cloud Run Cloud Build contract is missing: $requiredCloudBuildPattern"
    }
}

Write-Host 'PASS: Cloud Run container contract verified.'
