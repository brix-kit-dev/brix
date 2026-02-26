<#
.SYNOPSIS
    Run Architecture Guard for infra-adapters repository
#>
param([switch]$StopOnFirstFailure)

Import-Module "$PSScriptRoot\..\scripts\ArchGuard.psm1" -Force

$Modules = @(
    @{ Path = "packages\server\infra-adapter-kafka"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-redis"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-outbox"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-idgen"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-minio"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-otel"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-simple"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-fallback"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-webhook"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-dataaccess"; Profile = "AdapterProfile"; Layer = "Adapters" }
    @{ Path = "packages\server\infra-adapter-database"; Profile = "AdapterProfile"; Layer = "Adapters" }
)

$Result = Invoke-ArchitectureGuard `
    -Modules $Modules `
    -BasePath $PSScriptRoot `
    -RepoName "infra-adapters" `
    -ReportPath "$PSScriptRoot\arch-guard-report.md" `
    -StopOnFirstFailure:$StopOnFirstFailure

exit $Result.ExitCode
