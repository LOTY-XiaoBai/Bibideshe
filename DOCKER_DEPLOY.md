# Docker 部署与联调指南

本文说明如何在本机用 Docker 把**前端 + 后端 + MySQL + Redis**跑起来，并用脚本做全功能 API 冒烟测试。

项目根目录：`E:\bibideshe`  
- 后端：`E:\bibideshe\bishe`  
- 前端：`E:\bibideshe\bishe-qianduan`  
- Compose：`E:\bibideshe\docker-compose.yml`

---

## 当前状态说明（请先看）

若你执行过 `docker compose up -d --build` 但打不开页面，常见原因：

1. **没有 `.env` 文件**（密码、Redis 口令、`TOKEN_SECRET` 任一为空时 Compose 会拒绝启动）
2. 容器停在 `Created`，并未真正 `Running`
3. 前端入口端口 `80` 被占用

按下面步骤从干净状态拉起即可。

---

## 一、前置条件

- 已安装 [Docker Desktop](https://www.docker.com/products/docker-desktop/)（Windows 开 WSL2 后端）
- 终端能执行：`docker version`、`docker compose version`

---

## 二、配置环境变量

在 `E:\bibideshe` 下：

```powershell
cd E:\bibideshe
Copy-Item .env.example .env
```

用记事本或 VS Code 打开 `.env`，至少确认：

| 变量 | 作用 | 本地测试示例 |
|------|------|----------------|
| `DB_USERNAME` / `DB_PASSWORD` | MySQL 最小权限业务用户 | 用强密码 |
| `MYSQL_ROOT_PASSWORD` | MySQL root 独立密码 | 不得与业务用户相同 |
| `REDIS_PASSWORD` | Redis 认证口令 | 必填强密码 |
| `TOKEN_SECRET` | 登录 Token 签名密钥 | 至少 32 位高强度随机字符 |
| `CAPTCHA_EXPOSE_CODE` | 测试脚本可读验证码明文 | `false`（开发临时可开） |
| `FRONTEND_PORT` | 前端宿主机端口 | `80`（冲突可改成 `8088`） |
| `CORS_ALLOWED_ORIGINS` | 可访问后端/WebSocket 的前端来源 | 线上填 HTTPS 域名 |
| `SWAGGER_ENABLED` | API 文档开关 | 生产 `false` |

> 正式环境只暴露 Nginx 前端入口；MySQL、Redis 和后端容器无宿主机端口映射。

---

## 三、启动全栈

```powershell
cd E:\bibideshe
docker compose down
docker compose up -d --build
```

首次构建后端会跑 Maven，可能要几分钟。查看状态：

```powershell
docker compose ps
docker compose logs -f backend
```

期望看到四个服务均为 `running` / `healthy`：

| 容器名 | 作用 |
|--------|------|
| `bishe-mysql` | 数据库（首次启动会执行 `bishe/docker/mysql/init` 建表） |
| `bishe-redis` | Redis（验证码、登录失败锁定等短时状态） |
| `bishe-backend` | Spring Boot API |
| `bishe-frontend` | Nginx 静态前端 + 反代 |

---

## 四、如何验证已部署成功

在浏览器打开：

1. **前端**：http://localhost （若改了 `FRONTEND_PORT`，用对应端口）
2. **验证码接口**：http://localhost/api/captcha  
   - 正常返回 JSON，`code` 为 `200`  
   - 若开启了 `CAPTCHA_EXPOSE_CODE=true`，还会有字段 `captchaCode`

命令行快速探测：

```powershell
Invoke-RestMethod http://localhost/api/captcha
Invoke-RestMethod http://localhost/api/admin/register/available
```

---

## 五、首次使用（浏览器）

1. 打开 http://localhost → 进入登录页  
2. **创建首个管理员**：页面引导或 Swagger 调用  
   - `GET /api/admin/register/available` → `available: true`  
   - `POST /api/admin/register`，body：`{ "password": "Admin123!" }`  
3. 管理员登录：前端输入隐藏口令 **`LOVE-LOTY`** 切到管理员模式，再用生成的 `adminxxxxx` ID + 密码登录  
4. 另开标签注册教师、学生，按 `FEATURES.md` 演示流程走一遍  

学生/教师 ID 由后端生成（学生以 `1` 开头，教师以 `2` 开头），注册成功后请**立刻记下 ID**，之后用 ID + 密码登录。

---

## 六、跑全功能 API 测试脚本

脚本路径：`E:\bibideshe\scripts\api-full-test.ps1`

要求：

- Docker 四个服务已 `running`
- `.env` 中 `CAPTCHA_EXPOSE_CODE=true`（改过需重新 `up` 后端）

执行：

```powershell
cd E:\bibideshe
powershell -ExecutionPolicy Bypass -File .\scripts\api-full-test.ps1

# 若不是首次空库（已有管理员），需带上首次注册打印的管理员 ID：
powershell -ExecutionPolicy Bypass -File .\scripts\api-full-test.ps1 -BootstrapAdminId adminxxxxx
```

可选参数：

```powershell
# 指定 API / 前端根地址
.\scripts\api-full-test.ps1 -BaseUrl "http://localhost" -FrontUrl "http://localhost"

# 自定义测试账号密码
.\scripts\api-full-test.ps1 -Password "Test123456!" -AdminPassword "Admin123456!" -BootstrapAdminId adminxxxxx
```

脚本会自动覆盖：

- 验证码、管理员引导注册、教师/学生注册登录  
- 开课、选题库三类题、审核公开题  
- 考试 / 作业 / 签到 / 批改 / 公布成绩 / 错题本 / 教师评分  
- 好友、私聊、群聊、撤回、PK  
- 商城下单全流程（广告、物流、收货、评价、售后）  
- 管理员账号列表、敏感词、警告等读接口  

结束时打印 **PASS / FAIL** 汇总。失败项会带 HTTP 状态与业务 `message`。

---

## 七、常用运维命令

```powershell
cd E:\bibideshe

# 看日志
docker compose logs -f backend
docker compose logs -f frontend
docker compose logs -f mysql
docker compose logs -f redis

# 重启某个服务
docker compose restart backend
docker compose restart redis

# 改代码后重建
docker compose up -d --build

# 停止（保留数据卷）
docker compose down

# 停止并清空数据库数据（慎用：会丢库）
docker compose down -v
```

上传文件与日志在 Docker volume：`backend_uploads`、`backend_logs`。

---

## 八、端口冲突怎么办

编辑 `.env`：

```env
FRONTEND_PORT=8088
```

然后：

```powershell
docker compose up -d
```

前端/API 统一访问 http://localhost:8088 ；测试脚本加 `-BaseUrl http://localhost:8088 -FrontUrl http://localhost:8088`。如需从宿主机连接 MySQL/Redis 调试，请使用单独的本地开发 override，不要改弱生产 Compose。

---

## 九、本地开发（不用 Docker 跑前端）

若只想热更新前端：

1. 本场景建议在本机启动 MySQL + Redis + 后端，Docker 生产 Compose 保持内网隔离  
2. **本机 Redis（你已安装）**：目录 `D:\Redis-123\Redis`，客户端 `redis-cli 8.8.0`  
   - 双击 `start.bat`，或在该目录执行：`.\redis-server.exe redis.conf`  
   - 另开终端验证：`redis-cli ping` → 应返回 `PONG`  
3. 前端：

```powershell
cd E:\bibideshe\bishe-qianduan
npm install
npm run dev
```

Vite 在 `5173`，已把 `/api` 代理到 `localhost:8080`。

后端默认连 `localhost:6379`；可用环境变量覆盖：

| 变量 | 默认 | 说明 |
|------|------|------|
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | （空） | 有密码时填写 |
| `REDIS_DATABASE` | `0` | 逻辑库编号 |

Redis 用途：图形验证码（`captcha:*`，TTL 5 分钟）、登录失败锁定（`login:fail:*` / `login:lock:*` / `login:level:*`）。

---

## 十、功能说明文档

完整功能列表见同目录：**[FEATURES.md](./FEATURES.md)**。
