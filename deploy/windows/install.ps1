<#
.SYNOPSIS
  宏禹水利管理平台 —— Windows 安装 / 升级脚本

.DESCRIPTION
  在发布包目录中以管理员身份运行（或双击 install.bat）：
    - 首次安装：检查 Java 21 与 PostgreSQL，创建数据库，生成配置，注册为 Windows 服务（开机自启、异常自动重启），开放防火墙端口
    - 再次运行：识别为升级，自动备份后替换程序与页面，保留配置和数据
  后端直接提供前端页面，无需另装 Nginx / IIS。

.EXAMPLE
  .\install.ps1
  .\install.ps1 -InstallDir D:\HongyuWater -Port 8080 -DbPassword 'xxx'
  .\install.ps1 -NasRoot '\\192.168.1.10\资料' -ServiceUser 'HONGYU\svc-water' -ServicePassword 'xxx'
#>
[CmdletBinding()]
param(
    # 安装目录（程序、配置、上传资料、日志、备份）
    [string]$InstallDir,
    # 访问端口：浏览器访问 http://服务器IP:端口
    [int]$Port = 8080,
    [string]$DbHost = 'localhost',
    [int]$DbPort = 5432,
    [string]$DbName = 'water_management',
    [string]$DbUser = 'postgres',
    [string]$DbPassword,
    # PostgreSQL bin 目录（包含 psql.exe / pg_dump.exe），留空自动查找
    [string]$PgBin,
    # Java 21 安装目录，留空自动查找（发布包自带 jre 时优先使用）
    [string]$JavaHome,
    [string]$JvmOptions = '-Xms512m -Xmx2g',
    # 内部网盘目录（共享路径如 \\192.168.1.10\资料，或本地/映射目录），留空表示不对接
    [string]$NasRoot,
    # 服务运行账号（访问网盘共享时需要有权限的账号，如 DOMAIN\user 或 .\user），留空使用 LocalSystem
    [string]$ServiceUser,
    [string]$ServicePassword,
    [int]$BackupKeepDays = 30,
    # 只部署文件与配置，不注册服务（调试用）
    [switch]$NoService,
    [switch]$NoFirewall
)

$ErrorActionPreference = 'Stop'
$PkgDir = $PSScriptRoot
. (Join-Path $PkgDir 'common.ps1')

Write-Host ''
Write-Host '==== 宏禹水利管理平台 安装程序 ====' -ForegroundColor Cyan
Write-Host ''

if (-not $NoService -and -not (Test-Admin)) { Stop-WithError '请以管理员身份运行（右键 install.bat →「以管理员身份运行」）' }

# ---------- 发布包检查 ----------
$PkgJar = Join-Path $PkgDir 'app\water-management.jar'
$PkgWeb = Join-Path $PkgDir 'web'
$PkgWinSW = Join-Path $PkgDir 'service\WinSW-x64.exe'
if (-not (Test-Path $PkgJar)) { Stop-WithError "发布包不完整：缺少 $PkgJar（请先在开发机执行 build-release.ps1）" }
if (-not (Test-Path (Join-Path $PkgWeb 'index.html'))) { Stop-WithError "发布包不完整：缺少 web\index.html" }
if (-not $NoService -and -not (Test-Path $PkgWinSW)) {
    Stop-WithError "缺少 $PkgWinSW。请从 https://github.com/winsw/winsw/releases 下载 WinSW-x64.exe（v2.12.0）放到 service 目录"
}

# ---------- 安装目录 ----------
if (-not $InstallDir) {
    $default = 'D:\HongyuWater'
    if (-not (Test-Path 'D:\')) { $default = 'C:\HongyuWater' }
    $existing = Get-CimInstance Win32_Service -Filter "Name='$ServiceId'" -ErrorAction SilentlyContinue
    if ($existing -and $existing.PathName -match '^"?(.+?)\\service\\') { $default = $Matches[1] }
    $answer = Read-Host "安装目录 [$default]"
    $InstallDir = if ($answer) { $answer.Trim('"', ' ') } else { $default }
}
$InstallDir = [IO.Path]::GetFullPath($InstallDir).TrimEnd('\')
if ($PkgDir -eq $InstallDir) { Stop-WithError '请在发布包目录中运行 install.ps1，而不是在安装目录中运行' }
$ConfigFile = Join-Path $InstallDir 'config\application.yml'
$IsUpgrade = Test-Path $ConfigFile

foreach ($d in 'app', 'web', 'config', 'service', 'data\docs', 'logs', 'backups') {
    New-Item -ItemType Directory -Path (Join-Path $InstallDir $d) -Force | Out-Null
}

# ---------- Java ----------
$PkgJre = Join-Path $PkgDir 'jre'
$InstJre = Join-Path $InstallDir 'jre'
if ((Test-Path (Join-Path $PkgJre 'bin\java.exe')) -and ($PkgJre -ne $InstJre)) {
    Write-Info '复制发布包自带的 Java 运行环境...'
    $null = robocopy $PkgJre $InstJre /MIR /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) { Stop-WithError '复制 jre 失败' }
}
$JavaExe = Find-Java @($JavaHome, $InstJre)
if (-not $JavaExe) {
    Stop-WithError '未找到 Java 21 或以上版本。请安装 JDK/JRE 21（如 https://adoptium.net/），或用 -JavaHome 指定目录，或在发布包中放入 jre 目录'
}
Write-Info "Java：$JavaExe（版本 $(Get-JavaMajor $JavaExe)）"

# ---------- PostgreSQL ----------
$PgBin = Find-PgBin $PgBin
if ($PgBin) { Write-Info "PostgreSQL 客户端：$PgBin" } else { Write-Warn2 '未找到 PostgreSQL 客户端（psql / pg_dump），将跳过建库检查，备份功能不可用' }

if (-not $IsUpgrade) {
    if (-not $DbPassword) {
        $sec = Read-Host "数据库用户 $DbUser 的密码" -AsSecureString
        $DbPassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    }
    if ($PgBin) {
        $psql = Join-Path $PgBin 'psql.exe'
        $env:PGPASSWORD = $DbPassword
        $env:PGCLIENTENCODING = 'UTF8'
        try {
            $r = Invoke-Native $psql @('-h', $DbHost, '-p', "$DbPort", '-U', $DbUser, '-d', 'postgres', '-tAc', "SELECT 1 FROM pg_database WHERE datname = '$DbName'")
            if ($r.ExitCode -ne 0) { Stop-WithError "无法连接数据库 ${DbHost}:${DbPort}（用户 $DbUser）：$($r.Output)" }
            if ($r.Output.Trim() -eq '1') {
                Write-Info "数据库 $DbName 已存在，将直接使用（已有数据保留，表结构自动升级）"
            } else {
                Write-Info "创建数据库 $DbName ..."
                $r = Invoke-Native $psql @('-h', $DbHost, '-p', "$DbPort", '-U', $DbUser, '-d', 'postgres', '-c', "CREATE DATABASE `"$DbName`"")
                if ($r.ExitCode -ne 0) { Stop-WithError "创建数据库失败：$($r.Output)" }
            }
        } finally {
            Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
        }
    } else {
        Write-Warn2 "请确认数据库 $DbName 已在 ${DbHost}:${DbPort} 上创建，否则服务无法启动"
    }
}

# ---------- 升级：停止服务并备份 ----------
$Svc = Get-Service -Name $ServiceId -ErrorAction SilentlyContinue
if ($IsUpgrade) {
    Write-Info "检测到已有安装（$InstallDir），执行升级：保留配置与数据"
    $info = Read-DeployInfo $InstallDir
    if ($info.Port) { $Port = [int]$info.Port }
    if ($PgBin -and (Test-Path (Join-Path $InstallDir 'manage.ps1'))) {
        Write-Info '升级前备份数据...'
        & powershell -NoProfile -ExecutionPolicy Bypass -File (Join-Path $InstallDir 'manage.ps1') backup
        if ($LASTEXITCODE -ne 0) { Stop-WithError '升级前备份失败，已中止升级（可检查后重试）' }
    }
    if ($Svc -and $Svc.Status -ne 'Stopped') {
        Write-Info '停止服务...'
        Stop-Service -Name $ServiceId -Force
        (Get-Service -Name $ServiceId).WaitForStatus('Stopped', [TimeSpan]::FromSeconds(60))
    }
    $oldJar = Join-Path $InstallDir 'app\water-management.jar'
    if (Test-Path $oldJar) { Copy-Item $oldJar "$oldJar.bak" -Force }
}

# ---------- 复制程序与页面 ----------
Write-Info '复制程序文件...'
Copy-Item $PkgJar (Join-Path $InstallDir 'app\water-management.jar') -Force
$null = robocopy $PkgWeb (Join-Path $InstallDir 'web') /MIR /NFL /NDL /NJH /NJS /NP
if ($LASTEXITCODE -ge 8) { Stop-WithError '复制前端页面失败' }
foreach ($f in 'common.ps1', 'manage.ps1', 'manage.bat') {
    Copy-Item (Join-Path $PkgDir $f) (Join-Path $InstallDir $f) -Force
}

# ---------- 配置文件（仅首次生成） ----------
if (-not $IsUpgrade) {
    $bytes = New-Object byte[] 48
    [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwt = [Convert]::ToBase64String($bytes)
    $nasType = if ($NasRoot) { 'mount' } else { 'none' }
    $docs = (Join-Path $InstallDir 'data\docs') -replace '\\', '/'
    $web = (Join-Path $InstallDir 'web') -replace '\\', '/'
    $yml = @"
# 宏禹水利管理平台配置（由安装脚本生成于 $(Get-Date -Format 'yyyy-MM-dd HH:mm')）
# 修改后执行 manage.bat restart 生效。本文件包含数据库密码与密钥，请勿外传。
server:
  port: $Port

spring:
  datasource:
    url: jdbc:postgresql://${DbHost}:${DbPort}/${DbName}
    username: $(ConvertTo-YamlString $DbUser)
    password: $(ConvertTo-YamlString $DbPassword)
  servlet:
    multipart:
      # 单个文件上传上限
      max-file-size: 500MB
      max-request-size: 500MB

app:
  jwt:
    # 登录令牌签名密钥（修改后所有用户需重新登录）
    secret: $(ConvertTo-YamlString $jwt)
    expire: 12h
  storage:
    # 系统上传资料目录（请纳入备份）
    local-dir: $(ConvertTo-YamlString $docs)
  web:
    # 前端页面目录（由后端直接提供页面）
    static-dir: $(ConvertTo-YamlString $web)
  nas:
    # none 未对接；mount 以目录方式访问网盘（共享路径需服务账号有读权限）
    type: $nasType
    root: $(ConvertTo-YamlString $NasRoot)
    max-files: 20000
"@
    Write-Utf8File $ConfigFile $yml
    # 配置文件仅管理员与系统可读（-NoService 调试模式下保持默认权限，便于当前用户前台运行）
    if (-not $NoService) {
        $acl = @("*S-1-5-32-544:(F)", "*S-1-5-18:(F)")
        if ($ServiceUser) { $acl += "${ServiceUser}:(R)" }
        $null = icacls $ConfigFile /inheritance:r /grant:r @acl
    }
    Write-Info "已生成配置文件：$ConfigFile"
}

$deployInfo = [ordered]@{
    Port           = $Port
    PgBin          = $PgBin
    JavaExe        = $JavaExe
    BackupKeepDays = $BackupKeepDays
    InstalledAt    = (Get-Date -Format 'yyyy-MM-dd HH:mm:ss')
}
Write-Utf8File (Join-Path $InstallDir 'config\deploy.json') ($deployInfo | ConvertTo-Json)

if ($NoService) {
    Write-Ok "文件部署完成（未注册服务）。前台运行：$InstallDir\manage.bat run"
    exit 0
}

# ---------- Windows 服务（WinSW） ----------
$SvcDir = Join-Path $InstallDir 'service'
$SvcExe = Join-Path $SvcDir "$ServiceId.exe"
$SvcXml = Join-Path $SvcDir "$ServiceId.xml"
Copy-Item $PkgWinSW $SvcExe -Force

$jar = Join-Path $InstallDir 'app\water-management.jar'
$depend = ''
if ($DbHost -in @('localhost', '127.0.0.1')) {
    $pgSvc = Get-Service -Name 'postgresql*' -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($pgSvc) { $depend = "  <depend>$($pgSvc.Name)</depend>" }
}
function New-ServiceXml([string]$Account) {
    return @"
<?xml version="1.0" encoding="UTF-8"?>
<service>
  <id>$ServiceId</id>
  <name>$ServiceName</name>
  <description>宏禹水利管理平台（项目台账、周计划周报、月度考评、资料中心）</description>
  <executable>$([Security.SecurityElement]::Escape($JavaExe))</executable>
  <arguments>$([Security.SecurityElement]::Escape("$JvmOptions -Dfile.encoding=UTF-8 -Duser.timezone=Asia/Shanghai -jar `"$jar`""))</arguments>
  <workingdirectory>$([Security.SecurityElement]::Escape($InstallDir))</workingdirectory>
  <startmode>Automatic</startmode>
  <delayedAutoStart>true</delayedAutoStart>
$depend
  <onfailure action="restart" delay="10 sec"/>
  <onfailure action="restart" delay="30 sec"/>
  <onfailure action="restart" delay="60 sec"/>
  <resetfailure>1 hour</resetfailure>
  <stoptimeout>30 sec</stoptimeout>
  <logpath>$([Security.SecurityElement]::Escape((Join-Path $InstallDir 'logs')))</logpath>
  <log mode="roll-by-size">
    <sizeThreshold>10240</sizeThreshold>
    <keepFiles>10</keepFiles>
  </log>
$Account
</service>
"@
}

$account = ''
if ($ServiceUser) {
    if (-not $ServicePassword) {
        $sec = Read-Host "服务账号 $ServiceUser 的密码" -AsSecureString
        $ServicePassword = [Runtime.InteropServices.Marshal]::PtrToStringAuto([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec))
    }
    $domain = '.'; $user = $ServiceUser
    if ($ServiceUser -match '^(.+?)\\(.+)$') { $domain = $Matches[1]; $user = $Matches[2] }
    $account = @"
  <serviceaccount>
    <domain>$([Security.SecurityElement]::Escape($domain))</domain>
    <user>$([Security.SecurityElement]::Escape($user))</user>
    <password>$([Security.SecurityElement]::Escape($ServicePassword))</password>
    <allowservicelogon>true</allowservicelogon>
  </serviceaccount>
"@
}

if ($Svc) {
    # 升级：服务已存在，只更新配置（运行账号保持不变）
    Write-Utf8File $SvcXml (New-ServiceXml '')
} else {
    Write-Info '注册 Windows 服务...'
    Write-Utf8File $SvcXml (New-ServiceXml $account)
    $r = Invoke-Native $SvcExe @('install')
    # 账号密码只在注册时需要，注册后从文件中移除
    Write-Utf8File $SvcXml (New-ServiceXml '')
    if ($r.ExitCode -ne 0) { Stop-WithError "注册服务失败：$($r.Output)" }
    # 用于访问运行目录：服务账号需要对安装目录有修改权限
    if ($ServiceUser) { $null = icacls $InstallDir /grant "${ServiceUser}:(OI)(CI)M" /T /Q }
}

# ---------- 防火墙 ----------
if (-not $NoFirewall) {
    $ruleName = "$ServiceId-$Port"
    if (-not (Get-NetFirewallRule -Name $ruleName -ErrorAction SilentlyContinue)) {
        New-NetFirewallRule -Name $ruleName -DisplayName "$ServiceName (TCP $Port)" -Direction Inbound -Protocol TCP -LocalPort $Port -Action Allow | Out-Null
        Write-Info "已开放防火墙端口 $Port"
    }
}

# ---------- 启动 ----------
Write-Info '启动服务（首次启动会自动创建数据库表，约需 30~60 秒）...'
Start-Service -Name $ServiceId
if (-not (Wait-Health $Port)) {
    Write-Warn2 "服务未在预期时间内就绪，最近日志："
    $log = Get-ChildItem (Join-Path $InstallDir 'logs') -Filter '*.out.log' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($log) { Get-Content $log.FullName -Tail 60 -Encoding UTF8 }
    Stop-WithError "启动失败，请根据日志排查（$InstallDir\logs）"
}

$ip = Get-LanAddress
Write-Host ''
Write-Ok "$(if ($IsUpgrade) { '升级' } else { '安装' })完成！访问地址：http://${ip}:$Port"
if (-not $IsUpgrade) {
    Write-Host '       首次部署默认管理员：admin / admin123（首次登录强制修改密码）'
    Write-Host "       建议执行：$InstallDir\manage.bat backup-task   安装每日自动备份"
}
Write-Host "       日常管理：$InstallDir\manage.bat（启动/停止/备份/恢复/日志）"
