<#
.SYNOPSIS
  宏禹水利管理平台 —— 打包 Windows 发布包（在开发机运行）

.DESCRIPTION
  编译后端 jar、打包前端页面，连同安装/管理脚本与 WinSW 服务程序组装为发布包（目录 + zip）。
  需要：JDK 21+、Maven、Node.js 18.18+。把生成的 zip 复制到服务器解压，运行 install.bat 即可。

.EXAMPLE
  .\build-release.ps1
  .\build-release.ps1 -JrePath 'C:\Program Files\Eclipse Adoptium\jre-21' # 同时打包 Java 运行环境，服务器无需安装 Java
  .\build-release.ps1 -NpmRegistry https://registry.npmmirror.com
#>
[CmdletBinding()]
param(
    # 输出目录，默认 deploy\windows\release
    [string]$OutDir,
    # 可选：一并打包的 Java 21 运行环境目录（包含 bin\java.exe）
    [string]$JrePath,
    # 打包前运行后端测试
    [switch]$RunTests,
    # 不执行 mvn clean（增量编译，开发机本地后端正在运行时使用）
    [switch]$NoClean,
    [string]$NpmRegistry,
    [switch]$NoZip
)

$ErrorActionPreference = 'Stop'
. (Join-Path $PSScriptRoot 'common.ps1')
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
if (-not $OutDir) { $OutDir = Join-Path $PSScriptRoot 'release' }
$WinSWVersion = 'v2.12.0'
$WinSWUrl = "https://github.com/winsw/winsw/releases/download/$WinSWVersion/WinSW-x64.exe"
$WinSWCache = Join-Path $PSScriptRoot "tools\WinSW-x64-$WinSWVersion.exe"

function Assert-Command([string]$Name, [string]$Hint) {
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) { Stop-WithError "未找到 $Name：$Hint" }
}

[xml]$pom = Get-Content (Join-Path $Root 'backend\pom.xml') -Raw -Encoding UTF8
$Version = $pom.project.version
$Name = "water-management-$Version-$(Get-Date -Format 'yyyyMMdd')"
Write-Info "打包 $Name"

# ---------- 后端 ----------
Assert-Command 'mvn' '请安装 Maven 并加入 PATH'
if ($JrePath -and (Get-JavaMajor (Join-Path $JrePath 'bin\java.exe')) -lt 21) { Stop-WithError "-JrePath 不是 Java 21+：$JrePath" }
Write-Info '编译后端（需要 JDK 21+，JAVA_HOME 指向 JDK 21 或以上）...'
Push-Location (Join-Path $Root 'backend')
try {
    $mvnArgs = @('-B', '-q', 'package')
    if (-not $NoClean) { $mvnArgs = @('-B', '-q', 'clean', 'package') }
    if (-not $RunTests) { $mvnArgs += '-DskipTests' }
    & mvn @mvnArgs
    if ($LASTEXITCODE -ne 0) { Stop-WithError '后端编译失败（若提示 release version 21 not supported，请将 JAVA_HOME 设为 JDK 21+）' }
} finally { Pop-Location }
$Jar = Get-ChildItem (Join-Path $Root 'backend\target') -Filter 'water-management-*.jar' |
    Where-Object { $_.Name -notlike '*.original' } | Select-Object -First 1
if (-not $Jar) { Stop-WithError '未找到编译后的 jar' }

# ---------- 前端 ----------
Assert-Command 'npm' '请安装 Node.js 18.18+'
Write-Info '打包前端...'
Push-Location (Join-Path $Root 'frontend')
try {
    if ($NpmRegistry) { $env:npm_config_registry = $NpmRegistry }
    if (-not (Test-Path 'node_modules')) {
        & npm ci --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { Stop-WithError '安装前端依赖失败' }
    }
    & npm run build
    if ($LASTEXITCODE -ne 0) { Stop-WithError '前端打包失败' }
} finally { Pop-Location }

# ---------- WinSW ----------
if (-not (Test-Path $WinSWCache)) {
    Write-Info "下载 WinSW $WinSWVersion ..."
    New-Item -ItemType Directory -Path (Split-Path $WinSWCache) -Force | Out-Null
    try {
        [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
        Invoke-WebRequest -Uri $WinSWUrl -OutFile "$WinSWCache.part" -UseBasicParsing
        Move-Item "$WinSWCache.part" $WinSWCache -Force
    } catch {
        Remove-Item "$WinSWCache.part" -ErrorAction SilentlyContinue
        Write-Warn2 "WinSW 下载失败：$($_.Exception.Message)"
        Write-Warn2 "请手动下载 $WinSWUrl 保存为 $WinSWCache 后重新打包（或放到发布包 service\WinSW-x64.exe）"
    }
}

# ---------- 组装 ----------
$Dst = Join-Path $OutDir $Name
if (Test-Path $Dst) { Remove-Item $Dst -Recurse -Force }
foreach ($d in 'app', 'web', 'service') { New-Item -ItemType Directory -Path (Join-Path $Dst $d) -Force | Out-Null }
Copy-Item $Jar.FullName (Join-Path $Dst 'app\water-management.jar')
Copy-Item (Join-Path $Root 'frontend\dist\*') (Join-Path $Dst 'web') -Recurse
foreach ($f in 'install.ps1', 'install.bat', 'manage.ps1', 'manage.bat', 'common.ps1') {
    Copy-Item (Join-Path $PSScriptRoot $f) (Join-Path $Dst $f)
}
Copy-Item (Join-Path $Root 'deploy\README.md') (Join-Path $Dst '部署说明.md')
if (Test-Path $WinSWCache) { Copy-Item $WinSWCache (Join-Path $Dst 'service\WinSW-x64.exe') }
if ($JrePath) {
    Write-Info '复制 Java 运行环境...'
    $null = robocopy $JrePath (Join-Path $Dst 'jre') /MIR /NFL /NDL /NJH /NJS /NP
    if ($LASTEXITCODE -ge 8) { Stop-WithError '复制 jre 失败' }
}

if (-not $NoZip) {
    $zip = "$Dst.zip"
    if (Test-Path $zip) { Remove-Item $zip -Force }
    Write-Info '压缩发布包...'
    Compress-Archive -Path $Dst -DestinationPath $zip -CompressionLevel Optimal
    Write-Ok "发布包：$zip（$('{0:N1}' -f ((Get-Item $zip).Length / 1MB)) MB）"
} else {
    Write-Ok "发布包目录：$Dst"
}
Write-Host '       复制到服务器解压后，右键 install.bat「以管理员身份运行」完成安装；再次运行即为升级'
