$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$workflowPath = Join-Path $repositoryRoot '.github\workflows\production-smoke.yml'
$smokeScriptPath = Join-Path $repositoryRoot 'scripts\test-production-smoke.ps1'
$frontendDeploymentPath = Join-Path $repositoryRoot 'frontend\src\app\api\deployment\route.ts'
$healthControllerPath = Join-Path $repositoryRoot 'backend\src\main\java\com\placesplates\domain\health\controller\HealthController.java'
$cloudBuildPath = Join-Path $repositoryRoot 'backend\cloudbuild.yaml'

$requiredFiles = @($workflowPath, $smokeScriptPath, $frontendDeploymentPath, $healthControllerPath, $cloudBuildPath)
foreach ($requiredFile in $requiredFiles) {
    if (-not (Test-Path -LiteralPath $requiredFile -PathType Leaf)) {
        throw "Production deployment contract file is missing: $requiredFile"
    }
}

$workflow = Get-Content -Raw -LiteralPath $workflowPath
$workflowPatterns = @(
    'workflow_run:',
    'workflows: [Verify]',
    "github.event.workflow_run.conclusion == 'success'",
    "github.event.workflow_run.head_branch == 'main'",
    'test-production-smoke.ps1',
    '-ExpectedCommitSha $env:EXPECTED_COMMIT_SHA'
)

foreach ($workflowPattern in $workflowPatterns) {
    if (-not $workflow.Contains($workflowPattern)) {
        throw "Production smoke workflow contract is missing: $workflowPattern"
    }
}

$frontendDeployment = Get-Content -Raw -LiteralPath $frontendDeploymentPath
if (-not $frontendDeployment.Contains('VERCEL_GIT_COMMIT_SHA') -or
    -not $frontendDeployment.Contains('X-Places-Plates-Commit')) {
    throw 'Frontend deployment status does not expose the Vercel commit header.'
}

$healthController = Get-Content -Raw -LiteralPath $healthControllerPath
if (-not $healthController.Contains('X-Places-Plates-Commit')) {
    throw 'Cloud Run health status does not expose the deployment commit header.'
}

$cloudBuild = Get-Content -Raw -LiteralPath $cloudBuildPath
if (-not $cloudBuild.Contains('--update-env-vars=APP_COMMIT_SHA=${COMMIT_SHA}')) {
    throw 'Cloud Build does not attach the source commit to the Cloud Run revision.'
}

Write-Host 'PASS: Production deployment and smoke contract verified.' -ForegroundColor Green
