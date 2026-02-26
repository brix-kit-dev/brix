<#
.SYNOPSIS
    Run Architecture Guard for platform-commons repository
#>
param([switch]$StopOnFirstFailure)

Import-Module "$PSScriptRoot\..\scripts\ArchGuard.psm1" -Force

$Modules = @(
    @{ Path = "packages\server\platform-common"; Profile = "CommonsProfile"; Layer = "Commons" }
    @{ Path = "packages\server\platform-auth"; Profile = "CommonsProfile"; Layer = "Commons" }
    @{ Path = "packages\server\platform-common-starter"; Profile = "CommonsProfile"; Layer = "Commons" }
    @{ Path = "packages\server\platform-config"; Profile = "CommonsProfile"; Layer = "Commons" }
    @{ Path = "packages\server\platform-gateway"; Profile = "CommonsProfile"; Layer = "Commons" }
    @{ Path = "packages\server\platform-observability"; Profile = "CommonsProfile"; Layer = "Commons" }
)

$Result = Invoke-ArchitectureGuard `
    -Modules $Modules `
    -BasePath $PSScriptRoot `
    -RepoName "platform-commons" `
    -ReportPath "$PSScriptRoot\arch-guard-report.md" `
    -StopOnFirstFailure:$StopOnFirstFailure

exit $Result.ExitCode
