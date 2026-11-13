# 宏禹水利管理软件

公司内部使用的项目管理与资料管理系统：周计划 / 周完成情况填报、月度考评、年度汇总，以及内部资料查询、预览和下载审批。

## 目录结构

| 目录 | 说明 |
|---|---|
| `backend/` | 后端服务（Java 21 + Spring Boot 4.1 + PostgreSQL + Flyway） |
| `frontend/` | 前端（Vue 3 + TypeScript + Vite + Element Plus） |
| `docs/` | 需求规格说明书、需求确认问题及资料收集清单（Word + Markdown 源文件） |
| `prototype/` | 纯前端可演示原型，双击 `index.html` 打开（PC / 手机自适应） |
| `tools/md2docx.py` | Markdown 转 Word 工具：`python tools/md2docx.py <md> <docx> [--toc]` |

## 开发进度

| 模块 | 状态 |
|---|---|
| 系统管理：登录、用户、部门、角色权限、参数配置（考评指标 / 填报规则 / 安全设置）、日志审计、个人中心 | ✅ 已完成 |
| 项目台账（新建 / 编辑、成员、状态流转、存档申请与确认）、周计划 / 周完成情况、周报统计（点评 / 退回）、工作台待办 | ✅ 已完成 |
| 周报导出 Excel、填报提醒推送、节假日顺延 | ⏳ 待客户提供周报样表、确认消息渠道后开发 |
| 月度考评（存档自动进入考评、参考数据、按工时分配个人贡献、打分 → 发布 / 撤回）、年度汇总（总览、月度分布、个人排名、部门对比、CSV 导出） | ✅ 已完成（指标与权重可在参数配置中调整，待客户确认考核办法） |
| 资料中心（分类、查询、在线预览 + 水印、下载申请与两级审批、限时限次下载、访问日志、资料维护） | ✅ 已完成 |
| 内部网盘对接 | ✅ 挂载目录方式已可用；WebDAV / 厂商接口方式已预留扩展点（待客户提供网盘资料） |

## 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1、Spring Security 7（JWT，无状态）、Spring Data JPA（Hibernate 7）、Bean Validation |
| 数据库 | PostgreSQL 14+（开发环境验证：PostgreSQL 18） |
| 数据库脚本管理 | **Flyway**：`backend/src/main/resources/db/migration/V{n}__说明.sql`，应用启动时自动执行 |
| 前端 | Vue 3.5、TypeScript、Vite 5、Element Plus、Pinia、Vue Router |
| 测试 | JUnit 6 + MockMvc + 内嵌 PostgreSQL（zonky embedded-postgres，真实数据库 + Flyway 迁移） |

## 本地运行

### 1. 准备数据库

```sql
create database water_management encoding 'UTF8';
```

默认连接 `localhost:5432/water_management`，账号 `postgres` / `postgres`，可通过环境变量覆盖：

| 环境变量 | 默认值 |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/water_management` |
| `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
| `JWT_SECRET` | 开发用密钥，**生产环境必须修改**（至少 32 个字符） |
| `JWT_EXPIRE` | `12h` |
| `DOC_STORAGE_DIR` | `./data/docs`（系统上传资料的保存目录，生产环境请放数据盘并纳入备份） |
| `UPLOAD_MAX_SIZE` | `500MB`（单个上传文件大小上限） |
| `NAS_TYPE` | `none`（未对接）/ `mount`（以挂载目录方式访问网盘） |
| `NAS_ROOT` | 网盘根目录，如 `\\NAS01\资料库` 或 `/mnt/nas/资料库` |
| `NAS_MAX_FILES` | `20000`（单次同步最多处理的文件数） |
| `CORS_ORIGINS` | 空（前端经同源代理访问，无需跨域；前后端不同域部署时才需配置） |

表结构和初始数据由 Flyway 在启动时自动创建，无需手动执行 SQL。

### 2. 启动后端（需要 JDK 21 或以上）

```bash
cd backend
mvn spring-boot:run
```

本机没有 PostgreSQL 时，可以用内嵌数据库启动（数据保存在 `backend/target/dev-pgdata`，端口 15432）：

```bash
mvn spring-boot:test-run -Dspring-boot.run.main-class=com.hongyu.water.LocalDevApplication
```

### 3. 启动前端（需要 Node 18.18 或以上）

```bash
cd frontend
npm install
npm run dev
```

浏览器打开 http://localhost:5173 ，`/api` 请求由 Vite 代理到 `http://localhost:8080`。

### 4. 登录

内置超级管理员：**admin / admin123**，拥有全部权限，首次登录强制修改密码。
新增用户的初始密码为 `Hy123456`（可在"参数配置 › 安全设置"中修改），用户首次登录同样需要改密。

## 运行测试

```bash
cd backend
mvn test
```

测试会自动下载并启动内嵌 PostgreSQL，不依赖本机数据库。

## 数据库脚本约定（Flyway）

- 脚本位置：`backend/src/main/resources/db/migration/`
- 命名：`V{版本号}__{说明}.sql`，例如 `V3__weekly_plan.sql`；版本号递增
- **已经执行过的脚本不能再修改**（Flyway 会校验 checksum，修改后启动会失败），变更一律新增脚本
- Hibernate 设置为 `ddl-auto: validate`，只校验实体与表结构是否一致，不会自动改表
- 新增菜单 / 按钮权限：在迁移脚本中向 `sys_permission` 插入记录，并给需要的角色授权；超级管理员自动拥有新权限

## 内部网盘对接

资料中心支持两种来源：**系统上传**（保存在 `DOC_STORAGE_DIR`）和**内部网盘**（系统只建立索引，文件仍在网盘中，见需求说明书 5.2 方案 A）。

1. 把网盘共享目录挂载到服务器：
   - Windows：直接使用 UNC 路径（如 `\NAS01\资料库`），后端服务的运行账号需要有读取权限；
   - Linux：`mount -t cifs //NAS01/资料库 /mnt/nas -o username=...,ro` 或 NFS 挂载。
2. 设置环境变量 `NAS_TYPE=mount`、`NAS_ROOT=挂载路径`，重启后端。
3. 用资料管理员账号进入"资料维护 › 网盘对接"，点击"立即同步"。

同步规则：一级 / 二级目录对应一级 / 二级分类；文件名以标准号开头（如 `SL 252-2017 ...`）时自动识别编号和年份；网盘中删除的文件自动下架，恢复后自动上架。新文件默认"内部 / 需审批"，可在资料列表中调整。

**扩展其他网盘**：网盘只能通过 WebDAV 或厂商 API 访问时，实现 `com.hongyu.water.doc.storage.NasClient` 接口（列出文件、读取文件两个方法），并在 `NasConfig` 中按 `NAS_TYPE` 注册即可，业务代码无需修改。

## 权限设计

- **RBAC**：用户 ↔ 角色（多对多）↔ 权限点（菜单 `MENU` / 按钮 `BUTTON`）
- **数据范围**：角色上配置 `ALL` 全公司 / `DEPT` 本部门（含下级）/ `SELF` 本人，多个角色取最大范围
- **内置角色**：系统管理员（超级管理员）、公司领导、部门负责人、资料管理员、普通员工
- 后端接口使用 `@PreAuthorize("hasAuthority('权限点')")`；前端菜单按权限过滤，按钮使用 `v-perm` 指令
- 每次请求都会校验用户状态和令牌版本：停用账号、重置密码、修改密码后，旧令牌立即失效
- 安全规则：连续输错密码 5 次锁定 15 分钟；只有超级管理员能分配超级管理员角色、操作管理员账号；内置管理员不可删除 / 停用
- 关键操作通过 `@OperLog` 注解自动记录操作日志，密码等敏感参数自动脱敏

## 部署（建议）

```bash
# 后端打包
cd backend && mvn -DskipTests package      # 生成 target/water-management-0.1.0-SNAPSHOT.jar
java -jar water-management-0.1.0-SNAPSHOT.jar   # 通过环境变量配置数据库和 JWT_SECRET

# 前端打包
cd frontend && npm run build               # 生成 dist/，由 Nginx 托管
```

Nginx 示例：

```nginx
server {
    listen 80;
    root /opt/water-management/dist;
    location / { try_files $uri $uri/ /index.html; }
    client_max_body_size 500m;              # 与 UPLOAD_MAX_SIZE 保持一致
    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_read_timeout 600s;            # 大文件上传 / 下载
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## 演示原型账号

原型（`prototype/index.html`）为纯前端演示，数据保存在浏览器本地，与正式系统无关：
zhangsan（员工）/ lisi（部门负责人）/ chenzong（公司领导）/ admin（管理员），密码均为 123456。
