# 启动 server-test 的 Forge 服务端，等世界加载完成后按脚本发指令，最后 stop。
# 用法：powershell -File tools/run_server_test.ps1 [-DoneTimeoutSec 900] [-SkipStop]
param(
    [int]$DoneTimeoutSec = 900,
    [switch]$SkipStop
)
$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$srv = Join-Path $root "server-test"
$env:JAVA_HOME = Join-Path $root "_build\tools\jdk-17.0.20.1+1"
$java = Join-Path $env:JAVA_HOME "bin\java.exe"
$log = Join-Path $srv "console.log"
$latest = Join-Path $srv "logs\latest.log"
$marker = Join-Path $srv "wc_test_marker.txt"
foreach ($f in @($log, $marker)) { if (Test-Path $f) { Remove-Item $f -Force } }

Push-Location $srv
try {
    & {
        $deadline = (Get-Date).AddSeconds($DoneTimeoutSec)
        $phase = "waiting"
        while ((Get-Date) -lt $deadline) {
            Start-Sleep -Seconds 3
            if (Test-Path $latest) {
                $tail = Get-Content $latest -Tail 300 -ErrorAction SilentlyContinue
                if ($tail -match 'Done \(') { $phase = "loaded"; break }
                if ($tail -match 'Failed to start the minecraft server|Exception in thread "main"|A fatal error') { $phase = "failed"; break }
            }
        }
        "WC_PHASE=$phase" | Out-File -FilePath $marker -Encoding utf8 -Append
        if ($phase -ne "loaded") {
            Start-Sleep -Seconds 5
            "stop"
            Start-Sleep -Seconds 20
            return
        }
        Start-Sleep -Seconds 5

        "say WC_SMOKE_START" | Out-File -FilePath $marker -Encoding utf8 -Append
        "say WC_SMOKE_START"

        Start-Sleep -Seconds 2
        "summon withering_away_reborn:the_mangled 12 70 0 {NoAI:1b}" | Out-File -FilePath $marker -Encoding utf8 -Append
        "summon withering_away_reborn:the_mangled 12 70 0 {NoAI:1b}"
        Start-Sleep -Seconds 2
        "execute if entity @e[type=withering_away_reborn:the_mangled]" | Out-File -FilePath $marker -Encoding utf8 -Append
        "execute if entity @e[type=withering_away_reborn:the_mangled]"
        Start-Sleep -Seconds 2

        "summon witherstormmod:wither_storm 0 70 0" | Out-File -FilePath $marker -Encoding utf8 -Append
        "summon witherstormmod:wither_storm 0 70 0"
        Start-Sleep -Seconds 5
        "execute if entity @e[type=witherstormmod:wither_storm]" | Out-File -FilePath $marker -Encoding utf8 -Append
        "execute if entity @e[type=witherstormmod:wither_storm]"
        Start-Sleep -Seconds 2

        "summon minecraft:wither 0 80 0" | Out-File -FilePath $marker -Encoding utf8 -Append
        "summon minecraft:wither 0 80 0"
        Start-Sleep -Seconds 6
        "kill @e[type=minecraft:wither]" | Out-File -FilePath $marker -Encoding utf8 -Append
        "kill @e[type=minecraft:wither]"
        Start-Sleep -Seconds 8
        "execute if entity @e[type=withering_away_reborn:the_corpse_of_the_wither]" | Out-File -FilePath $marker -Encoding utf8 -Append
        "execute if entity @e[type=withering_away_reborn:the_corpse_of_the_wither]"
        Start-Sleep -Seconds 3

        "say WC_SMOKE_END" | Out-File -FilePath $marker -Encoding utf8 -Append
        "say WC_SMOKE_END"
        Start-Sleep -Seconds 3
        if (-not $SkipStop) {
            "kill @e[type=witherstormmod:wither_storm]"
            Start-Sleep -Seconds 3
            "stop"
            Start-Sleep -Seconds 25
        }
    } | & $java -Xms2G -Xmx6G "@$srv\user_jvm_args.txt" "@$srv\libraries\net\minecraftforge\forge\1.20.1-47.4.28\win_args.txt" nogui *> $log
}
finally {
    Pop-Location
}
Write-Output "server run finished; log: $log"
