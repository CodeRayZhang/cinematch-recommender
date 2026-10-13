# CineMatch — Movie Recommendation System

**Find your next favorite film.**

CineMatch 是一个可持续维护的个人电影推荐系统，围绕电影发现、私人片单和观影感受设计。新版采用 Vue 3 + TypeScript + Spring Boot 4 + Java 21，用户站与管理后台共用一套 API。

![CineMatch desktop preview](docs/screenshots/desktop.jpg)

## 功能

- 发现：电影搜索、类型筛选、排序、分页、编辑甄选与心情入口。
- 详情：电影资料、相似电影、个人评分、短评。
- 账号：注册、登录、退出；密码使用 BCrypt；服务端 Session、CSRF 校验、管理员角色控制。
- 片单：想看 / 看过；评分自动标记看过；数据在服务端持久化并按用户隔离。
- 推荐：基于收藏与评分的类型偏好排序，提供推荐理由，排除已收藏或已评分的电影。
- 管理：电影增删改查、首页甄选、用户概览及统计。无默认管理员账号。
- 数据：Flyway 数据库迁移；本地 H2 文件库；MySQL 配置和 Docker Compose 配套。

当前有 18 部用于界面展示的编辑样例。海报来自 TMDB 图片 CDN，来源记录在 `docs/demo-catalog.json`。参考评分与人数是演示值，不是实时社区评分；用户写下的评分和短评独立存储。

## 本地开发

需要 Node.js 22.12+、Java 21、Maven 3.9+。执行：

```sh
bash scripts/dev.sh
```

首次自动安装前端锁文件中的依赖。前端为 http://127.0.0.1:5173，后端为 http://127.0.0.1:8080。Ctrl+C 停止。

也可以在两个终端分别运行：

```sh
bash scripts/mvn.sh spring-boot:run
npm --prefix frontend ci
npm --prefix frontend run dev
```

`scripts/mvn.sh` 优先使用系统 Maven，也支持放在 `.tools/` 下的隔离工具链。`.tools/` 和 `.cache/` 不进入 Git，其他开发者使用自己的 Java / Maven 即可。

H2 数据存放在 `backend/.data/`，无需启动旧项目、爬虫、Kafka 或 Spark。账号可以直接在页面注册。切勿将新版直接连接到旧版 `movie` 数据库；新版使用独立 schema。

创建管理员时，在首次启动前设置 `ADMIN_USERNAME` 和 `ADMIN_PASSWORD` 环境变量。账号为 3–32 位小写字母、数字或下划线，密码至少 12 个字符且不超过 72 个 UTF-8 字节。仅在账号不存在时创建；不会将已有用户自动升级为管理员。登录后，头像菜单中出现“内容管理”。

本地开发脚本不自动读取 `.env`；`.env` 由 Docker Compose 使用。

## 构建与校验

```sh
npm --prefix frontend run build
bash scripts/mvn.sh test
bash scripts/build.sh
```

`build.sh` 完成前端类型检查、生产构建及后端测试，将前端一起打进 `backend/target/cinematch-2.0.0-SNAPSHOT.jar`。运行：

```sh
java -jar backend/target/cinematch-2.0.0-SNAPSHOT.jar
```

访问 http://127.0.0.1:8080。数据目录相对于进程工作目录；如果希望沿用开发库，请在 `backend/` 目录启动 JAR，或设置绝对路径的 `DATABASE_URL`。

后端测试使用独立内存数据库，覆盖注册、密码哈希、登录退出、CSRF、角色隔离、查询白名单、片单用户隔离、幂等更新、评分与推荐、管理 CRUD。人工浏览器验收记录见 `docs/verification.md`。

## MySQL 与容器

```sh
cp .env.example .env
# 编辑 .env，设置数据库密码及可选的管理员账号

docker compose up --build
```

Compose 使用独立 MySQL 8.4 数据卷，仅将应用端口绑定到本机。对外部署时通过 HTTPS 反向代理接入，并设置 `COOKIE_SECURE=true`。当前环境没有 Docker，因此容器与 MySQL 路径尚未实机验证；已验证 H2 本地开发与打包运行路径。

也可直接设置 `DATABASE_URL`、`DATABASE_USERNAME`、`DATABASE_PASSWORD` 连接独立的 MySQL 数据库。数据库迁移自动执行，已发布迁移文件不要修改，结构调整请新增版本。

## 目录导入

可以使用 `scripts/import_catalog.py` 导入包含电影资料的 JSON 数组，支持 `docs/demo-catalog.json` 的字段格式和 API 的 camelCase 格式：

```sh
python3 scripts/import_catalog.py catalog.json
# 上一步只检查文件。设置 ADMIN_USERNAME / ADMIN_PASSWORD 后实际导入：
python3 scripts/import_catalog.py catalog.json --apply
```

脚本通过受保护的管理 API 写入，按原片名与年份匹配已有条目，多次执行更新匹配条目。遇到歧义即停止；成功条目会保留，修正文件后可重试。图片 URL 可以是 `/images/` 下的静态文件或 HTTPS 地址。

## 目录

```text
frontend/        Vue 页面、组件、API 客户端与本地海报
backend/         Spring Boot API、权限、推荐、Flyway 和测试
scripts/         开发、构建及目录导入工具
docs/            架构说明、样例来源和验证记录
```

本项目从毕业设计 [Movie_Recommend](https://github.com/CodeRayZhang/Movie_Recommend) 的现代化重写独立而来，使用独立 Git 历史、数据库及部署配置；原仓库保持保留。新程序不包含或引用旧模块，旧数据库用户、历史评分和 ALS 模型尚未自动迁移。新版推荐器是可测试的内容偏好基线，不等同于旧版 ALS，也未声称完成离线推荐质量评估。

## License

Code is available under the [MIT License](LICENSE). Movie artwork remains the property of its respective rights holders; see [artwork sources](docs/artwork.md).
