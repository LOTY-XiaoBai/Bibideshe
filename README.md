# 校园教学与社区系统

项目包含 Spring Boot 后端（`bishe/`）和 Vue 前端（`bishe-qianduan/`）。本文件是当前运行说明。更新记录、功能说明和排错文档统一放在 `C:\Users\30881\Desktop\毕设文档`，旧版模板 README 已删除。

## 克隆后启动：不需要 Navicat，不需要手工导入 SQL

先安装并启动 Docker Desktop（Linux/macOS 使用 Docker Engine 和 Compose），在项目根目录执行：

```powershell
# Windows：也可以双击“启动演示.cmd”
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-demo.ps1
```

```sh
# Linux / macOS
sh scripts/start-demo.sh
```

首次构建需要下载镜像和依赖。启动后访问 `http://localhost:8088`，注册自己的账号；管理员通过登录页的首次初始化入口创建。

启动脚本自动生成被 Git 忽略的 `.env.demo`，其中数据库密码、Redis 密码和 Token 密钥均为随机值；已有配置会保留。使用独立的 `bishe-demo` Docker 项目和数据卷，不连接原作者本机 MySQL，不附带真实用户、聊天、帖子、订单，也没有公用默认账号密码。

停止运行并保留数据：

```sh
docker compose --env-file .env.demo --project-name bishe-demo down
```

不要额外加 `-v`，该参数会删除演示数据库等数据卷。

## 项目里保留的 11 个 SQL 都参与自动启动

它们是程序运行所需的建表/迁移代码，**不是数据库数据备份，需要提交到 Git**。

| 文件 | 实际用途 |
| --- | --- |
| `bishe/docker/mysql/init/01_schema.sql` | 创建基础业务表；Docker 初始化 MySQL 时读取，后端也会检查应用 |
| `bishe/sql/07_community_shop_profile_manual.sql` | 创建社区、扩展资料、商品详情等结构，补齐订单字段 |
| `bishe/sql/09_community_reply_likes.sql` | 回复点赞和帖子转发计数字段 |
| `bishe/sql/10_community_safety.sql` | 个人屏蔽和举报审核结构 |
| `bishe/sql/11_community_management.sql` | 管理操作日志、好友建立时间和词库合并 |
| `bishe/sql/12_navigation_appeals.sql` | 警告衰减、申诉和聊天已读游标 |
| `bishe/sql/13_direct_moderation_appeals.sql` | 管理员直接处置帖子的申诉支持 |
| `bishe/sql/14_course_classroom.sql` | 固定课表、审批和容量设置、入课申请与课堂会话 |
| `bishe/sql/15_course_query_indexes.sql` | 旧库课程、选课、签到、申请及在线课堂查询索引优化 |
| `bishe/sql/16_classroom_quizzes.sql` | 预备小测、课堂发布、题目快照、名单、作答与成绩索引 |
| `bishe/sql/17_course_workspace.sql` | 课程封面、成绩可见性、成员加入时间、申请隐藏状态与私有课程资料 |

`07` 文件名里的 `manual` 是历史命名，现在也由程序自动执行；保留原文件名是为了兼容已经记录的迁移版本。编号有间隔正常，已经合并或被替代的旧脚本已清理，不需要补回。

后端的 `DatabaseInitializer` 按顺序执行这些文件，并通过 `app_schema_migration` 记录已完成版本，后续启动不重复执行。Maven 将 SQL 打包进后端，Docker 构建也会带上它们。不能把这 11 个文件当成无用备份删除，否则首次部署或数据库升级会失败。

Navicat 只是连接 MySQL 的客户端，界面里看到的是 MySQL 中实际保存的表和数据。项目 SQL 是创建或升级这些表的指令，与 Navicat 不自动同步。当前受控 SQL 只含表结构、通用预置词和迁移逻辑，不含原作者的业务数据。

已清理未使用的旧 SQL、根目录 Navicat 导出和项目内历史 SQL 备份。本机 `.env`、`.env.demo`、日志、上传文件和数据库导出不应提交；忽略规则不能清除历史上已经提交的内容，提交前仍需检查暂存区。

## 本机开发

需要 Java 17+、Node.js 20+、MySQL 8 和 Redis。

1. 在项目根目录将 `.env.example` 复制为 `.env`，填写自己的数据库、Redis 和密钥配置。已有 `.env` 不要覆盖。
2. 在 MySQL 中创建配置所指向的空数据库，授予应用账号建表和结构升级权限。后端会自动初始化；已有数据库保留数据并按版本升级。
3. Windows 本机 Redis 可双击“启动Redis.cmd”。默认安装位置由 `scripts/start-redis.ps1` 的 `RedisDirectory` 参数指定，连接配置读取 `.env`。
4. 使用 Java 17+ 启动后端：

   ```powershell
   cd bishe
   .\mvnw.cmd spring-boot:run
   ```

5. 在另一个终端启动前端：

   ```powershell
   cd bishe-qianduan
   npm install
   npm run dev
   ```

本机开发默认前端 `http://localhost:5173`，后端 `http://localhost:8080`。

## 网络授课运行条件

摄像头、麦克风和屏幕共享需要 HTTPS 或 localhost。教师在“我的授课 → 上课”中选择授课方式，开启设备后点击“开始上课”；学生确认通知后进入直播间。教师离开课堂时结束本次上课，浏览器意外关闭后课堂心跳在 60 秒后失效。固定课表按 Asia/Shanghai 时区提前 5 分钟提醒在线教师。

直播采用 WebRTC 点对点传输。局域网可直接连接；跨受限网络时在 `.env` 中填写 `RTC_TURN_URL`、`RTC_TURN_USERNAME`、`RTC_TURN_CREDENTIAL`，Docker 配置已透传这些变量。教师上行带宽随观看人数增加。

## 课堂小测

教师在“预备小测”中命名小测，选择自己的题目或已审核的公共题目，每份 1 至 20 题。可设置 1 至 180 分钟的固定作答时长；不设置时由教师手动结束。课堂的“发布小测”模块负责发布与结束，同一课堂同时进行一份小测。

网络课堂直接弹窗作答，本地课堂通过提醒进入独立小测页；学生也可从“考试/作业/小测”查看历史记录。作答自动暂存，到时、教师结束小测或课堂结束时交卷。勾选“学生做完后不公布答案”后，结束前服务端隐藏答案和得分；只有勾选显示排名的小测才向学生开放排名。解答题由教师在成绩面板批阅，错题可自行加入错题本。

只有勾选计分且已结束的小测并入现有考试考核部分；其他小测不影响课程总分。小测独立保存发布时的题目快照，修改或删除预备小测不会改变已经发布的试题。数据库在启动时自动增量升级，保留旧课程、题库、成员和成绩。

## 课程页面与学习资料

教师最多同时开设 5 门进行中的课程；课程卡片通过“编辑”进入课程页，在页内上课、管理成员、审核入课申请、上传资料和修改课程设置。人数上限不能低于现有成员数。学生通过“详情”进入课程页，可查看资料、成员和个人学习总结，结课后可给教师评分；已提交的评分以数字展示。

成员排名默认开放，按课程分数排序。教师关闭“允许学生查看排名”后，学生只能查看自己的分数，成员列表按加入时间排序。旧库升级前没有记录加入时间的成员使用迁移时间，时间相同时按学生 ID 保持稳定顺序。

课程资料文件实际保存在后端配置 `app.upload.dir` 下的 `private/course-materials` 目录；本机默认是 `E:\bibideshe\bishe\uploads\private\course-materials`。数据库保存文件名、大小、所属课程等元数据，文件内容保存在磁盘。下载接口校验课程成员身份，静态 `/uploads/` 路径禁止直接访问私有资料。迁移部署或备份时，数据库和上传目录需要一起保留；删除资料或课程会清理对应文件。

考试、作业使用独立作答与复盘页面，课程筛选包含“全部课程”。小测、作业和考试的作答复盘共用“加入错题本”选择工具，可逐题圆形勾选或全选，再确认加入；“取消加入”收起选择工具。教师暂未公布答案时禁止通过错题本提前取得答案，公布后即可加入。

## 检查命令

后端测试在 `bishe/` 下运行 `.\mvnw.cmd test`，前端构建在 `bishe-qianduan/` 下运行 `npm run build`。数据库集成测试需显式设置 `RUN_DB_INTEGRATION=true`；空库初始化测试另需 `RUN_SCHEMA_INTEGRATION=true`，测试账号必须有创建临时数据库的权限。

Git 操作统一在仓库根目录执行。不要提交真实配置、用户上传文件、构建目录和数据库备份。
