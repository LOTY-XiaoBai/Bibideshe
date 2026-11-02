# 毕业设计项目

这是一个前后端同仓库（monorepo）项目：

- `bishe/`：Spring Boot + MyBatis 后端
- `bishe-qianduan/`：Vue 3 + Vite 前端
- `docker-compose.yml`：MySQL、Redis、前后端容器编排
- `scripts/`：接口测试脚本
- `bishe/docker/mysql/init/01_schema.sql`：数据库建表脚本

## 本机开发环境

建议使用 Java 17 LTS、Node.js 20+、MySQL 8 和 Redis。不要用系统当前的 Java 8 启动后端。

1. 复制环境变量示例并填入本机真实值：

   ```powershell
   Copy-Item .env.example .env
   ```

   `.env` 已被 Git 忽略，不要将密码或 `TOKEN_SECRET` 提交到仓库。

2. 确保 Navicat 中的 `bishe` 数据库已经存在。建议为应用创建独立的 `bishe_app` 账号，不要让后端长期使用 MySQL `root`。

3. 启动本机 Redis：

   ```powershell
   Set-Location D:\Redis-123\Redis
   .\redis-server.exe redis.conf
   ```

4. 启动后端：

   ```powershell
   Set-Location E:\bibideshe\bishe
   .\mvnw.cmd spring-boot:run
   ```

   默认地址是 `http://localhost:8080`。

5. 启动前端：

   ```powershell
   Set-Location E:\bibideshe\bishe-qianduan
   npm install
   npm run dev
   ```

   默认地址是 `http://localhost:5173`。

## 验证命令

```powershell
# 后端编译/测试
Set-Location E:\bibideshe\bishe
.\mvnw.cmd test

# 前端生产构建
Set-Location E:\bibideshe\bishe-qianduan
npm run build
```

## GitHub 提交原则

- 在 `E:\bibideshe` 根目录执行 Git 命令，不要再分别管理前后端。
- 提交前先执行 `git status`，确认没有 `.env`、日志、上传文件、`node_modules` 或 `target`。
- 功能完成后使用有意义的提交信息，例如 `fix: load local environment configuration`。

更多部署说明见 `DOCKER_DEPLOY.md`，功能列表见 `FEATURES.md` 和 `项目功能介绍.md`。
