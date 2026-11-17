<#
.SYNOPSIS
  宏禹水利管理平台 —— Windows 服务管理脚本（位于安装目录，双击 manage.bat 显示菜单）

.EXAMPLE
  .\manage.ps1 status
  .\manage.ps1 backup
  .\manage.ps1 restore D:\HongyuWater\backups\20261117-023000
  .\manage.ps1 backup-task 02:30
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('', 'start', 'stop', 'restart', 'status', 'logs', 'backup', 'restore', 'backup-task', 'run', 'uninstall')]
    [string]$Command = '',
    [Parameter(Position = 1)]
    [string]$Arg,
    # restore / uninstall 时跳过确认
    [switch]$Yes
)

$ErrorActionPreference = 'Stop'
$InstallDir = $PSScriptRoot
. (Join-Path $InstallDir 'common.ps1')
$Info = Read-DeployInfo $InstallDir
$Port = [int]$Info.Port
$BackupRoot = Join-Path $InstallDir 'backups'

function Assert-Admin {
    if (-not (Test-Admin)) { Stop-WithError '该操作需要管理员权限（右键 manage.bat →「以管理员身份运行」）' }
}

function Get-Svc { Get-Service -Name $ServiceId -ErrorAction SilentlyContinue }

function Get-LatestLog {
    Get-ChildItem (Join-Path $InstallDir 'logs') -Filter '*.out.log' -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
}

function Invoke-Start {
    Assert-Admin
    $s = Get-Svc
    if (-not $s) { Stop-WithError '服务未安装，请先运行发布包中的 install.bat' }
    if ($s.Status -ne 'Running') { Start-Service -Name $ServiceId }
    Write-Info '等待服务就绪...'
    if (Wait-Health $Port) { Write-Ok "服务已启动：http://$(Get-LanAddress):$Port" }
    else { Stop-WithError "服务未在预期时间内就绪，请执行 manage.bat logs 查看日志" }
}

function Invoke-Stop {
    Assert-Admin
    $s = Get-Svc
    if ($s -and $s.Status -ne 'Stopped') {
        Stop-Service -Name $ServiceId -Force
        (Get-Svc).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
    }
    Write-Ok '服务已停止'
}

function Show-Status {
    $s = Get-Svc
    if (-not $s) { Write-Warn2 '服务未安装'; return }
    $healthy = $false
    try { $healthy = (Invoke-WebRequest "http://127.0.0.1:$Port/actuator/health" -UseBasicParsing -TimeoutSec 5).Content -match 'UP' } catch { }
    $cim = Get-CimInstance Win32_Service -Filter "Name='$ServiceId'"
    Write-Host "服务名称：$ServiceName（$ServiceId）"
    Write-Host "运行状态：$($s.Status)    启动方式：$($cim.StartMode)    运行账号：$($cim.StartName)"
    Write-Host "健康检查：$(if ($healthy) { '正常' } else { '不可用' })"
    Write-Host "访问地址：http://$(Get-LanAddress):$Port"
    Write-Host "安装目录：$InstallDir"
    $last = Get-ChildItem $BackupRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 1
    Write-Host "最近备份：$(if ($last) { $last.Name } else { '无' })"
    $task = Get-ScheduledTask -TaskName "$ServiceId-Backup" -ErrorAction SilentlyContinue
    Write-Host "自动备份：$(if ($task) { '已启用' } else { '未启用（manage.bat backup-task）' })"
}

function Show-Logs {
    $log = Get-LatestLog
    if (-not $log) { Stop-WithError '暂无日志' }
    Write-Info "$($log.FullName)（Ctrl+C 退出）"
    Get-Content $log.FullName -Tail 200 -Wait -Encoding UTF8
}

function Get-PgTool([string]$Name) {
    $bin = Find-PgBin $Info.PgBin
    if (-not $bin) { Stop-WithError '未找到 PostgreSQL 客户端（pg_dump / psql），请安装 PostgreSQL 或在 config\deploy.json 中设置 PgBin' }
    return (Join-Path $bin $Name)
}

function Get-Tar {
    $tar = Join-Path $env:SystemRoot 'System32\tar.exe'
    if (Test-Path $tar) { return $tar }
    return $null
}

function Write-BackupLog([string]$msg) {
    New-Item -ItemType Directory -Path $BackupRoot -Force | Out-Null
    Add-Content -Path (Join-Path $BackupRoot 'backup.log') -Value "$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss') $msg" -Encoding UTF8
}

function Invoke-Backup {
    $db = Read-DbConfig $InstallDir
    $dir = Join-Path $BackupRoot (Get-Date -Format 'yyyyMMdd-HHmmss')
    New-Item -ItemType Directory -Path $dir -Force | Out-Null
    try {
        Write-Info '备份数据库...'
        $env:PGPASSWORD = $db.Password
        $r = Invoke-Native (Get-PgTool 'pg_dump.exe') @('-h', $db.Host, '-p', "$($db.Port)", '-U', $db.User, '-Fc', '-f', (Join-Path $dir 'db.dump'), $db.Name)
        if ($r.ExitCode -ne 0) { throw "数据库备份失败：$($r.Output)" }

        Write-Info '备份上传资料...'
        $tar = Get-Tar
        if ($tar) {
            $r = Invoke-Native $tar @('-czf', (Join-Path $dir 'docs.tar.gz'), '-C', (Join-Path $InstallDir 'data'), 'docs')
            if ($r.ExitCode -ne 0) { throw "资料备份失败：$($r.Output)" }
        } else {
            $null = robocopy (Join-Path $InstallDir 'data\docs') (Join-Path $dir 'docs') /E /NFL /NDL /NJH /NJS /NP
            if ($LASTEXITCODE -ge 8) { throw '资料备份失败（robocopy）' }
        }
        Copy-Item (Join-Path $InstallDir 'config\application.yml') (Join-Path $dir 'application.yml')
    } catch {
        Write-BackupLog "失败 $dir：$_"
        Stop-WithError "$_"
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    $size = '{0:N1} MB' -f ((Get-ChildItem $dir -Recurse -File | Measure-Object Length -Sum).Sum / 1MB)
    Write-BackupLog "成功 $dir（$size）"
    Write-Ok "备份完成：$dir（$size）"

    $keep = [int]$Info.BackupKeepDays
    if ($keep -gt 0) {
        Get-ChildItem $BackupRoot -Directory | Where-Object { $_.Name -match '^\d{8}-\d{6}$' -and $_.CreationTime -lt (Get-Date).AddDays(-$keep) } |
            ForEach-Object { Remove-Item $_.FullName -Recurse -Force; Write-Info "清理过期备份：$($_.Name)" }
    }
}

function Invoke-Restore([string]$Dir) {
    Assert-Admin
    if (-not $Dir) {
        $list = Get-ChildItem $BackupRoot -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | Select-Object -First 10
        if (-not $list) { Stop-WithError "没有可用的备份（$BackupRoot）" }
        Write-Host '最近的备份：'
        for ($i = 0; $i -lt $list.Count; $i++) { Write-Host "  [$($i + 1)] $($list[$i].Name)" }
        $n = Read-Host '选择要恢复的备份序号'
        if ($n -notmatch '^\d+$' -or [int]$n -lt 1 -or [int]$n -gt $list.Count) { Stop-WithError '已取消' }
        $Dir = $list[[int]$n - 1].FullName
    }
    $dump = Join-Path $Dir 'db.dump'
    if (-not (Test-Path $dump)) { Stop-WithError "备份目录中没有 db.dump：$Dir" }
    if (-not $Yes) {
        Write-Warn2 '恢复会覆盖当前数据库和上传资料！'
        if ((Read-Host "确认从 $Dir 恢复？输入 yes 继续") -ne 'yes') { Stop-WithError '已取消' }
    }
    $db = Read-DbConfig $InstallDir
    Invoke-Stop
    try {
        $env:PGPASSWORD = $db.Password
        $env:PGCLIENTENCODING = 'UTF8'
        $conn = @('-h', $db.Host, '-p', "$($db.Port)", '-U', $db.User)
        Write-Info '恢复数据库...'
        $r = Invoke-Native (Get-PgTool 'psql.exe') ($conn + @('-d', 'postgres', '-v', 'ON_ERROR_STOP=1',
                '-c', "DROP DATABASE IF EXISTS `"$($db.Name)`" WITH (FORCE)", '-c', "CREATE DATABASE `"$($db.Name)`""))
        if ($r.ExitCode -ne 0) { throw "重建数据库失败：$($r.Output)" }
        $r = Invoke-Native (Get-PgTool 'pg_restore.exe') ($conn + @('-d', $db.Name, '--no-owner', $dump))
        if ($r.ExitCode -ne 0) { throw "恢复数据库失败：$($r.Output)" }

        $docs = Join-Path $InstallDir 'data\docs'
        $tgz = Join-Path $Dir 'docs.tar.gz'
        $folder = Join-Path $Dir 'docs'
        if ((Test-Path $tgz) -or (Test-Path $folder)) {
            Write-Info '恢复上传资料...'
            if (Test-Path $docs) { Remove-Item $docs -Recurse -Force }
            New-Item -ItemType Directory -Path $docs -Force | Out-Null
            if (Test-Path $tgz) {
                $r = Invoke-Native (Get-Tar) @('-xzf', $tgz, '-C', (Join-Path $InstallDir 'data'))
                if ($r.ExitCode -ne 0) { throw "恢复资料失败：$($r.Output)" }
            } else {
                $null = robocopy $folder $docs /E /NFL /NDL /NJH /NJS /NP
            }
        }
    } catch {
        Stop-WithError "$_"
    } finally {
        Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
    }
    Invoke-Start
    Write-Ok '恢复完成'
}

function Register-BackupTask([string]$At) {
    Assert-Admin
    if (-not $At) { $At = '02:30' }
    if ($At -notmatch '^\d{1,2}:\d{2}$') { Stop-WithError '时间格式应为 HH:mm，如 02:30' }
    $ps = Join-Path $env:SystemRoot 'System32\WindowsPowerShell\v1.0\powershell.exe'
    $action = New-ScheduledTaskAction -Execute $ps -Argument "-NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $InstallDir 'manage.ps1')`" backup"
    $trigger = New-ScheduledTaskTrigger -Daily -At $At
    $principal = New-ScheduledTaskPrincipal -UserId 'S-1-5-18' -LogonType ServiceAccount -RunLevel Highest
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -ExecutionTimeLimit (New-TimeSpan -Hours 4)
    Register-ScheduledTask -TaskName "$ServiceId-Backup" -Action $action -Trigger $trigger -Principal $principal -Settings $settings `
        -Description "$ServiceName 每日备份（数据库 + 上传资料），保留 $($Info.BackupKeepDays) 天" -Force | Out-Null
    Write-Ok "已设置每日 $At 自动备份到 $BackupRoot（日志：backups\backup.log）"
    Write-Host '       建议定期把备份目录复制到其他磁盘或服务器'
}

function Invoke-Run {
    # 前台运行（调试用，Ctrl+C 停止）；需先停止服务以免端口冲突
    $java = $Info.JavaExe
    if (-not $java -or -not (Test-Path $java)) { $java = Find-Java @((Join-Path $InstallDir 'jre')) }
    if (-not $java) { Stop-WithError '未找到 Java 21' }
    Set-Location $InstallDir
    & $java '-Dfile.encoding=UTF-8' '-Duser.timezone=Asia/Shanghai' '-jar' (Join-Path $InstallDir 'app\water-management.jar')
}

function Invoke-Uninstall {
    Assert-Admin
    if (-not $Yes) {
        Write-Warn2 '将删除 Windows 服务、防火墙规则和自动备份任务；数据库、上传资料、备份文件不会删除。'
        if ((Read-Host '确认卸载？输入 yes 继续') -ne 'yes') { Stop-WithError '已取消' }
    }
    if (Get-Svc) {
        Invoke-Stop
        $r = Invoke-Native (Join-Path $InstallDir "service\$ServiceId.exe") @('uninstall')
        if ($r.ExitCode -ne 0) { & sc.exe delete $ServiceId | Out-Null }
    }
    Get-NetFirewallRule -Name "$ServiceId-*" -ErrorAction SilentlyContinue | Remove-NetFirewallRule
    Unregister-ScheduledTask -TaskName "$ServiceId-Backup" -Confirm:$false -ErrorAction SilentlyContinue
    Write-Ok "已卸载服务。如需彻底清除，可手动删除 $InstallDir 及数据库"
}

function Show-Menu {
    while ($true) {
        Write-Host ''
        Write-Host "==== $ServiceName 管理 ====" -ForegroundColor Cyan
        Write-Host '  1. 查看状态      2. 启动      3. 停止      4. 重启'
        Write-Host '  5. 查看日志      6. 立即备份  7. 从备份恢复'
        Write-Host '  8. 设置每日自动备份           0. 退出'
        switch (Read-Host '请选择') {
            '1' { Show-Status }
            '2' { Invoke-Start }
            '3' { Invoke-Stop }
            '4' { Invoke-Stop; Invoke-Start }
            '5' { Show-Logs }
            '6' { Invoke-Backup }
            '7' { Invoke-Restore '' }
            '8' { Register-BackupTask (Read-Host '每天备份时间 [02:30]') }
            '0' { return }
        }
    }
}

switch ($Command) {
    'start' { Invoke-Start }
    'stop' { Invoke-Stop }
    'restart' { Invoke-Stop; Invoke-Start }
    'status' { Show-Status }
    'logs' { Show-Logs }
    'backup' { Invoke-Backup }
    'restore' { Invoke-Restore $Arg }
    'backup-task' { Register-BackupTask $Arg }
    'run' { Invoke-Run }
    'uninstall' { Invoke-Uninstall }
    default { Show-Menu }
}
