# CineMatch 部署与运行指南

[返回 README](../README.md) · [页面操作步骤](usage.md) · [推荐算法与评估入口](recommendation.md)

本文对应 2026-10-14 的实现。命令默认在仓库根目录、Bash 终端执行；需要切换目录的地方会明确标注。Windows 可使用 WSL。新版使用独立数据库，不连接旧毕业设计的数据库或运行环境。

## 1. 选择运行方式

| 方式 | 用途 | 页面地址 | 数据位置 | 验证情况 |
|---|---|---|---|---|
| Vite + Spring Boot | 修改前端、后端，查看开发效果 | `http://127.0.0.1:5173` | 默认 `backend/.data/` | 已本地验证 |
| 单个 JAR | 完整预览、个人服务器运行 | `http://127.0.0.1:8080` | 本文统一从 `backend/` 启动，沿用开发库 | 已本地验证 |
| Docker Compose + MySQL | 隔离应用与数据库环境 | `http://127.0.0.1:8080` | MySQL 命名卷；默认只读挂载外部海报 | CI 执行真实容器验收，结果见验收记录 |

同一 H2 文件库只运行一个应用进程。切换开发模式和 JAR 前，先在原终端按 `Ctrl+C` 停止。初次启动由 Flyway 建表并写入 **18 部种子电影**；此前本机扩充到 923 部的目录和海报不随仓库分发。

## 2. 准备环境与代码

本机开发 / 打包需要 Node.js 22.12+（项目 CI 使用 Node 22）、npm、JDK 21、Maven 3.9+ 和 Git。数据获取、导入与图表检查另外需要 Python 3。仅走容器方式时，宿主机准备 Git、Docker Engine / Desktop 和 Compose v2 即可。

```sh
git clone git@github.com:CodeRayZhang/cinematch-recommender.git
cd cinematch-recommender
node --version
npm --version
java -version
mvn -version
```

私有仓库需要 GitHub 访问权限和可用 SSH 密钥。已下载代码则直接进入该目录，不必重新克隆。`mvn -version` 中 Java 版本也应为 21；只安装 JRE 无法编译后端。

`scripts/mvn.sh` 会进入 `backend/`，将依赖缓存放入项目 `.cache/m2/`，优先使用系统 Maven，也兼容 `.tools/apache-maven-*/bin/mvn` 与 macOS 的 `.tools/jdk-*/Contents/Home`。这些工具和缓存不在 Git 中；新环境应自行安装依赖，不能依赖作者电脑的路径。普通 `java -jar` 使用当前终端的 Java，不会自动读取脚本中的隔离 JDK 设置。

## 3. 本地开发

在仓库根目录执行：

```sh
bash scripts/dev.sh
```

脚本首次自动安装前端锁文件依赖，同时启动后端与 Vite。等待后端出现启动完成日志、前端显示访问地址后，打开 `http://127.0.0.1:5173`。后端监听 `127.0.0.1:8080`，前端将 `/api` 和 `/images/catalog` 请求代理过去。按 `Ctrl+C` 停止两端。

如果需要分别查看日志，使用两个终端，均先进入仓库根目录：

```sh
# 终端 A：后端
bash scripts/mvn.sh spring-boot:run
```

```sh
# 终端 B：前端
npm --prefix frontend ci
npm --prefix frontend run dev
```

前端修改会热更新；当前后端没有配置 DevTools，修改 Java 后需要重启。切换分支或更新依赖锁文件后重新执行 `npm --prefix frontend ci`；`dev.sh` 只在 `node_modules` 不存在时安装依赖。

[Vite 配置](../frontend/vite.config.ts) 同时代理 `/api` 与 `/images/catalog`，扩充海报可以直接在 5173 预览。后端不在 8080 时，启动 Vite 前设置 `CINEMATCH_API_TARGET=http://127.0.0.1:其他端口`；种子图片仍随前端提供。

## 4. 构建并执行完整程序

先停止使用相同端口和 H2 文件库的旧进程。在仓库根目录执行：

```sh
bash scripts/build.sh
```

该脚本按锁文件安装前端依赖，执行 TypeScript 检查与生产构建，再运行 Maven `clean verify`，将页面打入 `backend/target/cinematch-2.0.0-SNAPSHOT.jar`。

将构建结果复制为独立运行文件，再从 `backend/` 启动，以保持数据路径一致：

```sh
mkdir -p .cache/runtime
release_id="$(date +%Y%m%d-%H%M%S)"
cp backend/target/cinematch-2.0.0-SNAPSHOT.jar ".cache/runtime/cinematch-${release_id}.jar"
cd backend
java -jar "../.cache/runtime/cinematch-${release_id}.jar"
```

访问 `http://127.0.0.1:8080`。这个模式不再需要 Vite、Node 或独立前端服务；运行机器只需 Java 21 和数据目录。前台执行时日志输出到当前终端，`Ctrl+C` 正常停止。服务器持续运行可交给 systemd 等进程管理器，保持工作目录、环境变量和数据目录权限一致；仓库目前没有现成的 systemd 单元文件。

每次发布使用新的运行 JAR 文件名，不要覆盖正在运行的文件，也不要直接运行下一次 `clean` 会删除的构建产物。`.cache/runtime` 只是本机副本，正式服务器可使用自己的版本目录。

在另一个终端执行只读检查：

```sh
curl --fail --silent --show-error 'http://127.0.0.1:8080/api/movies?size=1'
curl --fail --silent --show-error 'http://127.0.0.1:8080/api/recommendations'
curl --fail --silent --show-error 'http://127.0.0.1:8080/api/session'
```

预期分别返回带 `items/total/page/size` 的电影页、最多 12 条推荐的数组、带 `user/csrfToken` 的会话对象；未登录时 `user` 为 `null`。再打开首页及任意详情页，刷新详情地址应仍可显示。当前没有 `/actuator/health`，不要把该地址的 404 当成启动失败。

## 5. 初始化管理员

普通账号可在页面注册。管理员必须在应用启动前通过环境变量创建，没有默认管理员密码。先停止应用，在 **Bash** 终端、仓库根目录执行：

```sh
read -r -p '管理员账号：' ADMIN_USERNAME
read -r -s -p '管理员密码：' ADMIN_PASSWORD
printf '\n'
export ADMIN_USERNAME ADMIN_PASSWORD
bash scripts/dev.sh
# 停止应用后清理当前终端中的变量
unset ADMIN_USERNAME ADMIN_PASSWORD
```

需要 JAR 模式时，将 `bash scripts/dev.sh` 换成上一节的启动命令；环境变量也会传给 Java 进程。macOS 默认 zsh 用户可先执行 `bash` 再运行此段。

账号需为 3–32 位小写字母、数字或下划线；密码至少 12 个字符且不超过 72 个 UTF-8 字节。两项都留空时不创建管理员，只设置其中一项或格式不合法会导致启动失败。**仅当用户名不存在时创建**；同名普通用户不会自动提权，已有管理员的密码也不会被该设置重置。应选择尚未注册过的管理员账号。

启动后用该账号登录，头像菜单进入“内容管理”或访问 `/admin`。当前后台支持电影维护、用户概览和统计，没有用户角色修改或密码重置页面。

## 6. 配置项与数据目录

本地脚本和 `java -jar` **不会自动读取 `.env`**；用终端 `export` 设置环境变量。`.env` 仅由本项目的 Docker Compose 配置读取。

| 配置 | 默认值 / 含义 |
|---|---|
| `PORT` | `8080`；改后端端口时也需同步 Vite 代理目标 |
| `BIND_ADDRESS` | 本机运行 `127.0.0.1`；Docker 镜像内为 `0.0.0.0` |
| `DATABASE_URL` | H2 文件 `./.data/cinematch`，含 MySQL 兼容配置，见下方完整例子 |
| `DATABASE_USERNAME` / `DATABASE_PASSWORD` | H2 默认 `sa` / 空密码；MySQL 使用独立数据库账号 |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | 可选，仅用于首次创建指定管理员 |
| `COOKIE_SECURE` | 默认 `false`；HTTPS 对外服务设 `true`，本机 HTTP 保持 `false` |
| `--app.catalog.poster-directory=...` | 默认相对目录 `./.data/catalog-posters`，映射 `/images/catalog/**` |
| `--app.catalog.import-file=...` | 默认不导入；指定后在启动时执行一次目录事务导入 |

数据库和图片的相对路径都以**进程工作目录**为准。`scripts/mvn.sh` 固定进入 `backend/`，本文的 JAR 命令也如此；换了启动目录可能看到一个新建的 18 部电影库，而原数据仍在原位置。服务器建议使用绝对路径，下面的 `/absolute/path` 必须替换为已创建、应用用户可写的真实目录：

```sh
export DATABASE_URL='jdbc:h2:file:/absolute/path/cinematch-data/cinematch;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_ON_EXIT=FALSE'
# 从 backend/ 启动，替换为上文实际生成的运行 JAR 文件名
java -jar ../.cache/runtime/cinematch-RELEASE.jar \
  --app.catalog.poster-directory=/absolute/path/cinematch-data/catalog-posters
```

数据库文件不能放在公开静态目录中；海报目录只包含可公开访问的图片。应用和 Flyway 自动创建 / 迁移表，不必手工执行旧项目 SQL。

## 7. 扩充电影目录

### 7.1 获取公开目录并事务导入

在仓库根目录执行，需要网络和 Python 3 标准库：

```sh
python3 scripts/fetch_movie_sources.py
python3 scripts/prepare_public_catalog.py --limit 1000
```

生成 `.cache/catalog/ready-catalog.json`、校验报告及 `backend/.data/catalog-posters/` 海报。`--limit 1000` 是处理上限，最终数量受资料匹配、字段和图片校验影响，不保证导入 1,000 部。来源及使用范围见 [公开电影目录](data-sources.md)。

停止应用并按第 9 节备份数据，确保已构建最新 JAR，然后从仓库根目录执行下面的一次性导入启动；替换 `RELEASE` 为现有运行文件的时间戳：

```sh
cd backend
java -jar ../.cache/runtime/cinematch-RELEASE.jar \
  --app.catalog.import-file=../.cache/catalog/ready-catalog.json
```

日志应出现 `Catalog import complete` 和创建 / 更新 / 跳过数量。读取 `/api/movies?size=1` 的 `total` 并抽查详情、海报和来源页。下次普通启动去掉导入参数。

此路径整批事务导入，歧义或校验异常会回滚；重复执行按来源匹配更新，保留电影 ID、片单和个人评分，跳过人工编辑过的电影，不自动删除电影。文件缺失或来源请求失败时先检查报告，不必删除数据库重来。

### 7.2 通过管理员 API 导入自己的 JSON

程序运行时，在仓库根目录先验证文件：

```sh
python3 scripts/import_catalog.py docs/demo-catalog.json
```

这一步不写数据。准备自己的 `catalog.json` 后，用第 5 节的交互输入方式设置**已存在管理员**的环境变量，再执行：

```sh
python3 scripts/import_catalog.py catalog.json --apply --base-url http://127.0.0.1:8080
unset ADMIN_USERNAME ADMIN_PASSWORD
```

脚本负责登录和 CSRF，按原片名与年份精确匹配。它是逐条 API 写入，失败时之前成功的条目仍保留，**不具备 7.1 的整批回滚语义**。远程地址必须使用 HTTPS。

## 8. Docker Compose 与 MySQL

CI 使用真实 MySQL 8.4 运行 API 契约，再构建并启动下面的 Compose 镜像，检查重启后的数据保留。具体通过记录见 [验收记录](verification.md)。先停止占用本机 8080 的应用，在仓库根目录执行：

```sh
docker --version
docker compose version
cp .env.example .env
```

编辑 `.env`，填写 `DATABASE_PASSWORD`、`MYSQL_ROOT_PASSWORD`，可选填写管理员账号和密码。不要覆盖已有 `.env`。然后执行：

```sh
mkdir -p backend/.data/catalog-posters
docker compose config --quiet
docker compose up --build -d
docker compose ps
docker compose logs --tail=100 app
```

构建会安装依赖、打包前端并运行后端验证，因此需要网络。等待 MySQL 健康检查通过、应用启动完成后，按第 4 节访问页面和 API。应用通过 Compose 内网连接 MySQL 8.4 的独立 `cinematch` 数据库，宿主机只暴露 `127.0.0.1:8080`。

停止使用 `docker compose down`；再次运行使用 `docker compose up -d`。普通 `down` 保留 `movie-data` 命名卷，`down -v` 会删除数据库卷，不能作为常规停止或升级命令。已有 MySQL 卷不会因修改 `.env` 自动修改数据库密码；需要先在数据库中执行账号密码变更并同步应用配置。

默认镜像只有种子目录，`.data/` 不进入镜像。Compose 已只读挂载 `./backend/.data/catalog-posters` 到 `/app/.data/catalog-posters`；可在 `.env` 设置 `CATALOG_POSTER_DIRECTORY` 更换宿主机目录。需要扩充目录时，先在宿主机按 7.1 生成资料，再创建仅挂载导入 JSON 的 `compose.catalog.yml`（本地配置，不包含密码）：

```yaml
services:
  app:
    volumes:
      - ./.cache/catalog:/app/catalog-import:ro
```

另建只用于导入的 `compose.import.yml`：

```yaml
services:
  app:
    command: ["--app.catalog.import-file=/app/catalog-import/ready-catalog.json"]
```

先备份 MySQL，再用两个覆盖文件启动一次；确认导入日志后，去掉导入覆盖文件并重建应用容器，**保留海报挂载**：

```sh
docker compose -f compose.yml -f compose.catalog.yml -f compose.import.yml up -d app
docker compose -f compose.yml -f compose.catalog.yml -f compose.import.yml logs --tail=100 app
# 确认导入完成后执行
docker compose -f compose.yml -f compose.catalog.yml up -d --force-recreate app
```

CI 验证默认海报挂载；此可选的外部目录批量导入覆盖流程没有纳入容器验收。宿主机目录必须存在且容器用户可读。导入完成后可移除 JSON 挂载覆盖文件；默认海报挂载由 `compose.yml` 保持。

已有独立 MySQL 服务也可直接供 JAR 使用：将 `DATABASE_URL` 设为 `jdbc:mysql://数据库主机:3306/cinematch?characterEncoding=utf8&connectionTimeZone=UTC`，配置用户名、密码，预先创建独立空库并授予应用用户迁移所需权限，再启动 JAR。不要使用旧项目 schema。H2 与 MySQL 之间目前没有一键数据迁移脚本，切换连接并不会搬迁已有账号、片单和评分。

对外服务需要 HTTPS 入口，将页面、`/api` 和 `/images` 一并转发到应用，并设置 `COOKIE_SECURE=true`。本机 HTTP 开启 Secure Cookie 会导致登录状态无法正常保持。当前使用服务端内存 Session，部署按单实例使用，重启后应重新登录；尚未配置共享会话和多实例部署。

## 9. 备份、更新与回退

**H2：** 先正常停止应用，确认没有其他进程使用同一文件库，再从仓库根目录复制整个数据目录。这样会同时保留数据库和外部海报：

```sh
backup_id="$(date +%Y%m%d-%H%M%S)"
backup_dir=".cache/backups/${backup_id}"
mkdir -p "$backup_dir"
cp -R backend/.data "$backup_dir/data"
git rev-parse HEAD > "$backup_dir/source-commit.txt"
git status --short > "$backup_dir/source-status.txt"
```

若使用绝对数据目录，应替换 `backend/.data` 为实际位置。`.cache` 内备份只是本机临时保障，还需另存到独立磁盘或备份位置。代码有未提交改动时，提交号不足以还原版本，需同时保留实际运行 JAR 和对应源码。

**MySQL：** 停止应用写入，使用数据库备份工具导出，并同时保存外部海报。默认 Compose 示例（若启用了覆盖文件，后续启动仍需带上它们）：

```sh
docker compose stop app
backup_id="$(date +%Y%m%d-%H%M%S)"
mkdir -p .cache/backups
docker compose exec -T db sh -c 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysqldump -uroot --single-transaction --no-tablespaces --set-gtid-purged=OFF cinematch' > ".cache/backups/mysql-${backup_id}.sql"
```

确认命令成功并在独立测试数据库验证可恢复后，再将文件当作有效备份。该示例未在当前环境实机执行；失败时不要继续删除卷或覆盖数据库。

更新顺序：记录当前版本 → 备份 → 构建新版本 → 停止旧应用 → 使用新运行文件 / 镜像启动 → 检查迁移日志、页面、登录和个人记录。已发布 Flyway 迁移不要改写，结构调整新增 `V...__...sql`。数据库已升级时不能仅换回旧 JAR 就假定兼容；回退应停机，保留失败现场，恢复配套数据库备份和旧版本程序。

## 10. 常见问题

| 现象 | 检查与处理 |
|---|---|
| Maven 找不到或 Java 版本不对 | 检查 `mvn -version`、`JAVA_HOME`；脚本启动和手工 Java 启动的 JDK 可能不同 |
| 8080 / 5173 被占用 | 检查是否还运行预览、另一个开发终端或容器；先停止对应服务 |
| H2 提示文件被占用 | 同一库只允许一个应用实例；不要同时启动 `dev.sh` 和 JAR |
| 启动后只剩 18 部电影 / 账号消失 | 检查工作目录与 `DATABASE_URL`，可能连到了另一个新建数据库 |
| 5173 的扩充海报加载失败 | 检查后端是否启动、`CINEMATCH_API_TARGET` 是否正确，以及海报文件是否存在 |
| 8080 海报 404 | 检查 `poster-directory`、文件是否存在；容器检查海报卷是否仍挂载 |
| 注册 / 保存返回 403 | 过期会话会清理私人状态并弹出登录，重新登录后主动重试；手工请求须先获取会话 CSRF token 并携带 Cookie 和 `X-XSRF-TOKEN` |
| 登录后立即变成游客 | HTTP 本地运行确认 `COOKIE_SECURE=false`；固定使用同一个主机名，勿混用 localhost 与 127.0.0.1 |
| 管理员未创建 | 检查账号格式、密码字节数、环境变量是否传入；同名账号已存在时不会自动提权或改密码 |
| Compose 新密码无效 | `.env` 初始化密码只作用于新数据库；已有卷需要显式修改 MySQL 账号密码 |
| 推荐不足 12 部或为空 | 清除临时筛选；候选可能都被收藏、评分或隐藏，参考 [算法说明](recommendation.md) |

## 11. 开发检查与验收

在仓库根目录按所改内容执行；完整发布构建用 `bash scripts/build.sh`：

```sh
npm --prefix frontend run typecheck
npm --prefix frontend run format:check
bash scripts/mvn.sh test
python3 -m unittest discover -s scripts -p 'test_*.py'
python3 scripts/import_catalog.py docs/demo-catalog.json
python3 scripts/generate_architecture.py --check
```

普通后端测试使用隔离测试库。没有配置 MovieLens 路径时，大型离线评估会跳过，不影响普通测试；专门评估的命令、输出及指标解释见 [算法文档](recommendation.md)。页面操作可按 [使用与验收步骤](usage.md) 执行，历史验收记录见 [verification.md](verification.md)。


### 自动浏览器和真实数据库验收

先生成供测试使用的完整 JAR，确保 `JAVA_HOME` 指向 JDK 21，18080 / 18081 未被占用：

```sh
bash scripts/build.sh
(cd frontend && npx playwright install chromium)
npm --prefix frontend run test:e2e
```

测试自动启动 18080 的 Vite 和 18081 的独立内存 H2 后端，结束后停止；不连接 `backend/.data/cinematch`。覆盖桌面与 390px 手机的筛选、注册、评分撤销、命名片单、曝光设置、会话过期与账号切换、管理员专题；报告在 `frontend/playwright-report/`。Linux CI 可用 `npx playwright install --with-deps chromium` 安装系统依赖。已有兼容 Chromium 时可以显式设置 `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`，默认使用 Playwright 安装的版本。

已安装并运行 Docker 的环境，用 Testcontainers 启动临时 MySQL 8.4：

```sh
bash scripts/mvn.sh -Dcinematch.mysql=true verify
```

它对 H2 与真实 MySQL 运行相同 HTTP 契约，包括全部 Flyway 迁移、权限、分页、合集、观察及事务回滚。没有这个参数时 MySQL 契约跳过，不代表已经验证 MySQL。CI 另外执行 `scripts/verify_container.py`，对实际 Compose 镜像检查深层路由、健康状态、海报挂载，并重启数据库及应用检查用户、评分和合集持久化；该脚本写入测试账号，专供隔离验收环境，不要指向个人数据服务。

`GET /api/health` 执行轻量数据库连通检查，成功为 `200 {"status":"UP"}`，不可用为 503。它不返回连接串或异常详情。当前仍为单实例内存会话，重启后重新登录即可读取持久化记录。
