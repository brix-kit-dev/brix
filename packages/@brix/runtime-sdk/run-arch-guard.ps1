<#
.SYNOPSIS
    Run Architecture Guard for runtime-sdk repository
#>
param([switch]$StopOnFirstFailure)

Import-Module "$PSScriptRoot\..\scripts\ArchGuard.psm1" -Force

$Modules = @(
    @{ Path = "runtime-sdk-api"; Profile = "SdkProfile"; Layer = "SDK" }
)

$Result = Invoke-ArchitectureGuard `
    -Modules $Modules `
    -BasePath $PSScriptRoot `
    -RepoName "runtime-sdk" `
    -ReportPath "$PSScriptRoot\arch-guard-report.md" `
    -StopOnFirstFailure:$StopOnFirstFailure

exit $Result.ExitCode
