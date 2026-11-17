# 宏禹水利管理平台 部署说明

生产环境只需部署 **一套**（内网服务器一台）。提供两种方式，任选其一：

| 方式 | 适用 | 组成 |
|---|---|---|
| [A. Linux + Docker Compose](#a-linux--docker-compose) | Linux 服务器（推荐） | PostgreSQL、后端、Nginx 三个容器，一条命令启动 |
| [B. Windows 服务](#b-windows-服务) | Windows Server | 后端注册为 Windows 服务，直接提供页面，无需 Nginx/IIS |

**服务器建议配置**：4 核 / 8 GB 内存 / 系统盘 100 GB，外加数据盘（上传资料与备份按实际容量估算）。
**注意**：后端只能运行 **一个实例**（文件下载凭证保存在内存中），不要做多实例负载均衡。

首次部署后用默认管理员 `admin / admin123` 登录，系统会强制修改密码。

---

## A. Linux + Docker Compose

### 1. 环境要求

- Docker 20.10+ 与 Docker Compose v2（`docker compose`）或 docker-compose 1.29+
- 服务器可访问外网下载镜像与依赖。无法访问外网时见 [离线部署](#离线部署)

### 2. 部署

```bash
# 获取代码（或把仓库打包上传到服务器）
git clone https://github.com/767281226/water-management.git
cd water-management/deploy/docker
chmod +x deploy.sh

./deploy.sh init     # 生成 .env：随机数据库密码与 JWT 密钥，创建数据目录
vi .env              # 按需修改：HTTP_PORT、DATA_DIR（建议数据盘）、网盘等
./deploy.sh up       # 构建镜像并启动，完成后显示访问地址
./deploy.sh cron     # 安装每日 02:30 自动备份
```

### 3. 常用命令

| 命令 | 说明 |
|---|---|
| `./deploy.sh status` | 查看容器状态 |
| `./deploy.sh logs [backend\|web\|db]` | 查看日志 |
| `./deploy.sh restart [服务]` | 重启 |
| `./deploy.sh down` | 停止（数据保留） |
| `./deploy.sh backup` | 立即备份（数据库 + 上传资料 + .env），保存在 `backups/时间戳/` |
| `./deploy.sh restore backups/20261117-023000` | 从备份恢复（覆盖当前数据） |
| `./deploy.sh update` | 升级：`git pull` 后执行，自动备份 → 重新构建 → 重启；数据库结构自动升级 |

### 4. 目录与数据

```
deploy/docker/
├── .env               配置（含密码，权限 600，勿提交）
├── data/postgres/     数据库文件
├── data/docs/         系统上传的资料
└── backups/           备份（默认保留 30 天，建议定期复制到其他机器）
```

### 5. 对接内部网盘

先在宿主机以只读方式挂载网盘，再在 `.env` 中设置：

```bash
# 示例：SMB/CIFS 共享（写入 /etc/fstab 可开机自动挂载）
sudo mount -t cifs //192.168.1.10/资料 /mnt/nas -o ro,username=xxx,password=xxx,iocharset=utf8,vers=3.0

# .env
NAS_TYPE=mount
NAS_HOST_DIR=/mnt/nas
```

然后执行 `./deploy.sh up`。网盘会以只读方式挂入容器，在「资料中心 → 网盘同步」中同步索引。

### 离线部署

在能联网的机器上构建并导出镜像，再拷贝到服务器：

```bash
./deploy.sh init && ./deploy.sh save-images      # 生成 water-management-images-latest.tar.gz
# 把 deploy/docker 目录和镜像文件复制到服务器后：
./deploy.sh load-images water-management-images-latest.tar.gz
./deploy.sh init && ./deploy.sh up
```

### HTTPS（可选）

内网一般使用 HTTP。如需 HTTPS，把证书挂入 web 容器，并在 `nginx.conf` 中增加 `listen 443 ssl` 与证书配置，或在前面再加一层单位已有的网关/反向代理。

---

## B. Windows 服务

### 1. 打包（开发机）

需要 JDK 21+、Maven、Node.js 18.18+：

```powershell
cd deploy\windows
.\build-release.ps1
# 服务器不想安装 Java 时，可把 JRE 21 一起打包：
.\build-release.ps1 -JrePath "C:\Program Files\Eclipse Adoptium\jre-21.0.5.11-hotspot"
```

生成 `deploy\windows\release\water-management-<版本>-<日期>.zip`。

### 2. 服务器准备

- Windows Server 2016 及以上（或 Windows 10/11）
- PostgreSQL 14 及以上（可装在本机，[下载](https://www.postgresql.org/download/windows/)）
- Java 21（发布包自带 jre 时无需安装，[下载 Temurin 21](https://adoptium.net/)）

### 3. 安装

解压发布包，**右键 `install.bat` →「以管理员身份运行」**，按提示输入安装目录与数据库密码即可。脚本会：

1. 检查 Java 21 和 PostgreSQL，自动创建数据库 `water_management`
2. 复制程序到安装目录（默认 `D:\HongyuWater`），生成配置 `config\application.yml`（随机 JWT 密钥）
3. 注册 Windows 服务「宏禹水利管理平台」（开机自启，异常退出自动重启）
4. 开放防火墙端口（默认 8080），启动并检查健康状态，显示访问地址

也可以用参数安装（PowerShell 管理员窗口）：

```powershell
.\install.ps1 -InstallDir D:\HongyuWater -Port 8080 -DbHost localhost -DbUser postgres -DbPassword '数据库密码'
```

| 参数 | 默认 | 说明 |
|---|---|---|
| `-InstallDir` | `D:\HongyuWater` | 安装目录（程序、配置、资料、日志、备份） |
| `-Port` | 8080 | 访问端口 |
| `-DbHost` / `-DbPort` / `-DbName` | localhost / 5432 / water_management | 数据库 |
| `-DbUser` / `-DbPassword` | postgres / 提示输入 | 数据库账号 |
| `-NasRoot` | 空 | 网盘目录，如 `\\192.168.1.10\资料` |
| `-ServiceUser` / `-ServicePassword` | LocalSystem | 服务运行账号（访问网盘共享需要有权限的账号） |
| `-JvmOptions` | `-Xms512m -Xmx2g` | JVM 内存 |
| `-PgBin` / `-JavaHome` | 自动查找 | 指定 PostgreSQL bin 目录 / Java 目录 |

### 4. 日常管理

安装目录下双击 **`manage.bat`** 打开管理菜单，或使用命令：

| 命令 | 说明 |
|---|---|
| `manage.bat status` | 服务状态、健康检查、最近备份 |
| `manage.bat start` / `stop` / `restart` | 启动 / 停止 / 重启 |
| `manage.bat logs` | 实时查看日志（`logs\HongyuWater.out.log`） |
| `manage.bat backup` | 立即备份到 `backups\时间戳\`（数据库 + 上传资料 + 配置） |
| `manage.bat backup-task 02:30` | 设置每日自动备份（计划任务，保留 30 天） |
| `manage.bat restore [备份目录]` | 从备份恢复（不带目录时列出最近备份供选择） |
| `manage.bat uninstall` | 卸载服务（不删除数据库与资料） |

修改配置（端口、数据库、网盘等）：编辑 `config\application.yml` 后执行 `manage.bat restart`。

### 5. 升级

用新版本发布包再次运行 `install.bat`，选择相同的安装目录。脚本会自动备份 → 停止服务 → 替换程序与页面 → 启动，配置和数据保留，数据库结构自动升级。旧版 jar 保留为 `app\water-management.jar.bak`。

### 6. 对接内部网盘

Windows 服务默认以 LocalSystem 运行，**无法访问映射盘符，也通常没有共享目录权限**。对接网盘时：

```powershell
.\install.ps1 -NasRoot '\\192.168.1.10\资料' -ServiceUser 'HONGYU\svc-water' -ServicePassword '***'
```

服务账号需要有共享目录的读权限。已安装的系统可修改 `config\application.yml` 中 `app.nas.type: mount`、`app.nas.root`，并在「服务」管理器中把运行账号改为有权限的账号。

---

## 备份与恢复建议

- 两种方式的备份都包含：数据库（`db.dump`，pg_dump 自定义格式）+ 上传资料（`docs.tar.gz`）+ 配置
- 网盘里的文件不在本系统备份范围内（系统只保存索引），由网盘自身备份
- 备份目录建议定期复制到另一台机器或移动硬盘；至少每季度做一次恢复演练
