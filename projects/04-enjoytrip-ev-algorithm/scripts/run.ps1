# 사용법: powershell -ExecutionPolicy Bypass -File scripts\run.ps1 [test|server|demo|web|bench|bench2]   (JDK 17 이상)
param([ValidateSet("test", "server", "demo", "web", "bench", "bench2")][string]$Task = "test")
$ErrorActionPreference = "Stop"
Set-Location (Join-Path $PSScriptRoot "..")
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

function Invoke-Mvn([string[]]$MvnArgs) {
    & .\mvnw.cmd @MvnArgs
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
}

$cp = "algorithm-core\target\classes;algorithm-bench\target\classes"
$main = @{
    demo   = "com.ssafy.enjoytrip.algorithm.demo.AlgorithmDemo"
    web    = "com.ssafy.enjoytrip.algorithm.demo.AlgorithmDemo"
    bench  = "com.ssafy.enjoytrip.algorithm.demo.AlgorithmBenchmark"
    bench2 = "com.ssafy.enjoytrip.algorithm.demo.ExtendedBenchmark"
}

switch ($Task) {
    "test" { Invoke-Mvn @("-q", "verify"); Write-Output "전체 테스트 통과" }
    "server" {
        Invoke-Mvn @("-q", "-DskipTests", "package")
        java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -jar backend\target\backend-0.1.0-SNAPSHOT.jar
    }
    default {
        Invoke-Mvn @("-q", "-pl", "algorithm-core,algorithm-bench", "-am", "-DskipTests", "package")
        $extra = @("data")
        if ($Task -eq "web") { $extra += "backend/src/main/resources/static/demo-result.js" }
        java "-Dfile.encoding=UTF-8" "-Dstdout.encoding=UTF-8" -cp $cp $main[$Task] @extra
    }
}
exit $LASTEXITCODE
