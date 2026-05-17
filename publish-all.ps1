# Brix SDK npm Publish Script
# Run from: d:\1.Sources\brix
# Usage: .\publish-all.ps1

$ErrorActionPreference = "Stop"
$statusFile = "npm-publish-status.md"

function Update-Status {
    param([string]$package, [string]$status)
    $content = Get-Content $statusFile -Raw
    $escaped = [regex]::Escape($package)
    $content = $content -replace "($escaped.*?)\| �?\|", "`$1| $status |"
    $content = $content -replace "($escaped.*?)\| �?\|", "`$1| $status |"
    Set-Content $statusFile $content -NoNewline
}

function Publish-Package {
    param([string]$path, [string]$name)
    
    Write-Host "`n📦 Publishing $name..." -ForegroundColor Cyan
    Push-Location $path
    
    try {
        npm publish --access public
        if ($LASTEXITCODE -eq 0) {
            Write-Host "�?$name published successfully" -ForegroundColor Green
            Update-Status $name "�?
            Pop-Location
            return $true
        }
    } catch {
        Write-Host "�?Failed to publish $name : $_" -ForegroundColor Red
    }
    
    Update-Status $name "�?
    Pop-Location
    return $false
}

# Phase 1: Core Runtime SDK
$phase1 = @(
    @{ path = "packages/@brix/runtime-sdk/runtime-sdk-api-web"; name = "@brix-sdk/runtime-sdk-api-web" },
    @{ path = "packages/@brix/runtime-sdk/runtime-sdk-react"; name = "@brix-sdk/runtime-sdk-react" },
    @{ path = "packages/@brix/runtime-sdk/runtime-manifest-web"; name = "@brix-sdk/runtime-manifest-web" },
    @{ path = "packages/@brix/runtime-sdk/runtime-orchestrator-web"; name = "@brix-sdk/runtime-orchestrator-web" },
    @{ path = "packages/@brix/shared-runtime/shared-runtime-web"; name = "@brix-sdk/shared-runtime-web" }
)

# Phase 2: Infrastructure Adapters
$phase2 = @(
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-http-web"; name = "@brix-sdk/infra-adapter-http-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-iframe-web"; name = "@brix-sdk/infra-adapter-iframe-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-mf-web"; name = "@brix-sdk/infra-adapter-mf-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-native-web"; name = "@brix-sdk/infra-adapter-native-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-router-web"; name = "@brix-sdk/infra-adapter-router-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-state-web"; name = "@brix-sdk/infra-adapter-state-web" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-ui-mui"; name = "@brix-sdk/infra-adapter-ui-mui" },
    @{ path = "packages/@brix/infra-adapters/packages/web/infra-adapter-ui-native"; name = "@brix-sdk/infra-adapter-ui-native" }
)

# Phase 3: Platform Capabilities
$phase3 = @(
    @{ path = "packages/@brix/platform-commons/packages/client/platform-shared"; name = "@brix-sdk/platform-shared" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-config-web"; name = "@brix-sdk/platform-config-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-eventbus-web"; name = "@brix-sdk/platform-eventbus-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-i18n-web"; name = "@brix-sdk/platform-i18n-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-navigation-web"; name = "@brix-sdk/platform-navigation-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-router-web"; name = "@brix-sdk/platform-router-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-state-web"; name = "@brix-sdk/platform-state-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-auth-web"; name = "@brix-sdk/platform-auth-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-auth-service-web"; name = "@brix-sdk/platform-auth-service-web" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-auth-ui-web"; name = "@brix-sdk/platform-auth-ui-web" }
)

# Phase 4: Devtools
$phase4 = @(
    @{ path = "packages/@brix/platform-devtools/eslint-config-architecture"; name = "@brix-sdk/eslint-config-architecture" },
    @{ path = "packages/@brix/platform-commons/packages/client/platform-design-tokens"; name = "@brix-sdk/platform-design-tokens" },
    @{ path = "packages/@brix/platform-devtools/@brix/create-brix"; name = "@brix-sdk/create-brix" }
)

# Phase 5: Aggregate Package
$phase5 = @(
    @{ path = "packages/@brix/brix"; name = "@brix-sdk/brix" }
)

Write-Host "🚀 Brix SDK npm Publish Script" -ForegroundColor Yellow
Write-Host "================================`n"

$allPhases = @(
    @{ name = "Phase 1: Core Runtime SDK"; packages = $phase1 },
    @{ name = "Phase 2: Infrastructure Adapters"; packages = $phase2 },
    @{ name = "Phase 3: Platform Capabilities"; packages = $phase3 },
    @{ name = "Phase 4: Devtools"; packages = $phase4 },
    @{ name = "Phase 5: Aggregate Package"; packages = $phase5 }
)

$failed = @()

foreach ($phase in $allPhases) {
    Write-Host "`n==============================" -ForegroundColor Yellow
    Write-Host $phase.name -ForegroundColor Yellow
    Write-Host "==============================" -ForegroundColor Yellow
    
    foreach ($pkg in $phase.packages) {
        $result = Publish-Package -path $pkg.path -name $pkg.name
        if (-not $result) {
            $failed += $pkg.name
        }
        # Brief pause between publishes to avoid rate limiting
        Start-Sleep -Seconds 2
    }
}

Write-Host "`n================================" -ForegroundColor Yellow
Write-Host "📊 Publish Summary" -ForegroundColor Yellow
Write-Host "================================"

if ($failed.Count -eq 0) {
    Write-Host "�?All packages published successfully!" -ForegroundColor Green
} else {
    Write-Host "�?Failed packages ($($failed.Count)):" -ForegroundColor Red
    $failed | ForEach-Object { Write-Host "  - $_" -ForegroundColor Red }
}
