param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GradleArguments = @("testDebugUnitTest")
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$javaCandidates = @(@(
    $env:JAVA_HOME,
    "C:\Program Files\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs\Android Studio\jbr"
) | Where-Object { $_ -and (Test-Path (Join-Path $_ "bin\java.exe")) })

if ($javaCandidates.Count -eq 0) {
    throw "No JDK found. Install Android Studio or set JAVA_HOME to JDK 17+."
}

$env:JAVA_HOME = $javaCandidates[0]
& (Join-Path $projectRoot "gradlew.bat") @GradleArguments
exit $LASTEXITCODE
