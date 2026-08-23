[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [ValidatePattern('^[a-z0-9]{20}$')]
    [string]$ProjectReference,

    [Parameter(Mandatory)]
    [ValidatePattern('^[A-Za-z0-9.-]+$')]
    [string]$PoolerHost
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = Split-Path -Parent $PSScriptRoot
$backendRoot = Join-Path $repositoryRoot 'backend'

function ConvertTo-PlainText {
    param(
        [Parameter(Mandatory)]
        [Security.SecureString]$SecureValue
    )

    $valuePointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($valuePointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($valuePointer)
    }
}

$adminSecurePassword = Read-Host 'Supabase database administrator password' -AsSecureString
$runtimeSecurePassword = Read-Host 'New placesplates_app runtime password (20+ characters)' -AsSecureString
$runtimeSecurePasswordConfirmation = Read-Host 'Confirm placesplates_app runtime password' -AsSecureString

$adminPassword = ConvertTo-PlainText $adminSecurePassword
$runtimePassword = ConvertTo-PlainText $runtimeSecurePassword
$runtimePasswordConfirmation = ConvertTo-PlainText $runtimeSecurePasswordConfirmation

if ($runtimePassword.Length -lt 20) {
    throw 'The runtime database password must contain at least 20 characters.'
}
if ($runtimePassword -cne $runtimePasswordConfirmation) {
    throw 'The runtime database password confirmation does not match.'
}

try {
    $env:SUPABASE_DATABASE_URL = "jdbc:postgresql://${PoolerHost}:5432/postgres?sslmode=require"
    $env:SUPABASE_ADMIN_DATABASE_USERNAME = "postgres.${ProjectReference}"
    $env:SUPABASE_ADMIN_DATABASE_PASSWORD = $adminPassword
    $env:SUPABASE_RUNTIME_DATABASE_USERNAME = "placesplates_app.${ProjectReference}"
    $env:SUPABASE_RUNTIME_DATABASE_PASSWORD = $runtimePassword

    Push-Location $backendRoot
    try {
        .\gradlew.bat provisionSupabaseDatabase --no-daemon
        if ($LASTEXITCODE -ne 0) {
            throw 'Supabase database provisioning failed.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    Remove-Item Env:SUPABASE_DATABASE_URL -ErrorAction SilentlyContinue
    Remove-Item Env:SUPABASE_ADMIN_DATABASE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SUPABASE_ADMIN_DATABASE_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:SUPABASE_RUNTIME_DATABASE_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:SUPABASE_RUNTIME_DATABASE_PASSWORD -ErrorAction SilentlyContinue
    $adminPassword = $null
    $runtimePassword = $null
    $runtimePasswordConfirmation = $null
}
