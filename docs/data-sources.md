# 公开电影目录

## 数据来源与边界

- 中文名称、类型、导演、简介、评分及人数：[dengfuping/douban-movies-spider](https://github.com/dengfuping/douban-movies-spider/tree/671045f9470ed41394c635589e3b65388ff11e46/data)。固定提交 `671045f9470ed41394c635589e3b65388ff11e46`，2,065 条，发布者标注采集于 2025 年。评分是历史快照，不是实时评分。
- 首映年份与 ID 对应：[Wikidata](https://www.wikidata.org/)，使用 Douban ID（P4529）、IMDb ID（P345）、TMDB ID（P4947）及最早发行日期（P577）。仅接受唯一的跨来源匹配，不依赖模糊片名猜测。结构化数据采用 CC0。
- 原片名、海报路径：[Asaniczka / TMDB Movies Dataset](https://www.kaggle.com/datasets/asaniczka/tmdb-movies-dataset-2023-930k-movies)，读取公开 CSV 的有界前缀，保留完整记录，并逐片校验 ID 与图片。此次来源文件更新于 2026-10-13。完整文件约 668 MB，本次仅读取约 12 MiB，得到 16,555 条完整记录用于匹配，未下载整库。
- 海报：[TMDB 图片 CDN](https://developer.themoviedb.org/docs/image-basics)，下载到本地后显示。每张图片的原 URL 与 SHA-256 保存在本地报告中；电影图片权利归各自权利人。

中文来源的 Kaggle 元数据标注 CC0，但其数据说明限定教育和研究用途，本项目保留这一用途边界。当前选出的 923 部电影及其海报作为个人项目资料随仓库保存，来源、图片 URL、哈希和说明见 [片库声明](../data/catalog/NOTICE.md) 与 [清单](../data/catalog/manifest.json)。代码的 MIT 许可不覆盖第三方电影资料、简介和图片，不额外授予商业使用或再分发权限。

没有导入第三方用户账号、个人评论或评分明细。外部参考分与本站用户自己的评分分别保存。

## 仓库自带片库

`data/catalog/movies.json` 是从当前应用的公开电影 API 导出的 923 条资料，采用启动事务导入格式；没有复制数据库。`data/catalog/posters/` 包含实际引用的 905 张扩充海报（45,381,974 字节），其余 19 个图片引用复用前端资源。清单按来源 ID 和图片路径排序，保留元数据与图片的 SHA-256。未提交未使用图片、原始下载缓存、账号、片单、个人评分和模型训练文件。

```sh
python3 scripts/check_catalog_bundle.py
bash scripts/dev.sh --catalog
```

`--catalog` 校验全部文件后设置绝对导入和图片路径，首次空库得到 923 部电影；后续仍用相同命令启动。导入按来源更新，跳过人工编辑记录，不清空已有数据。仅需只读核对一个**隔离、未经编辑的导入实例**时，可执行 `python3 scripts/check_catalog_bundle.py --base-url http://127.0.0.1:8080`，它会逐字段比较资料并校验所有 924 个图片响应。真实个人库可有新增、删除或人工编辑，不要求与快照完全一致。

完整 JAR 和 Docker Compose 命令见 [部署指南](deployment.md#7-扩充电影目录)。下面的网络获取流程用于重新准备上游资料，不是克隆后体验的前置条件。

## 获取和整理

需要 Python 3 标准库和网络连接，不需要 API Key：

```sh
python3 scripts/fetch_movie_sources.py
python3 scripts/prepare_public_catalog.py --limit 1000
```

文件位置：

- `.cache/catalog/`：上游快照、跨来源 ID、校验报告和 `ready-catalog.json`。
- `backend/.data/catalog-posters/`：通过校验的图片及对应 URL / SHA-256。
- `.cache/catalog/preparation-report.json`：接受和排除数量、年份修正、失败图片和图片来源。

以上目录均被 Git 忽略。首次运行下载资料；重复运行复用快照和校验通过的图片。需要刷新 TMDB 快照时，单独执行 `python3 scripts/fetch_tmdb_snapshot.py` 再重新整理；中文资料固定为 2025 年快照，不会被误标为已自动更新。

筛选规则：字段完整、片长 60–600 分钟、唯一电影 ID 和可核验年份、IMDb/TMDB ID 一致、图片下载成功。对于重映日期，不直接使用中文快照的 `release_date` 推断首映年份。已有 18 部种子电影保留已确认的原名、年份和图片。

## 导入到本地

先停止正在使用数据库的应用，再备份 `backend/.data/cinematch.mv.db`。完成构建后从 `backend/` 目录启动：

```sh
java -jar target/cinematch-2.0.0-SNAPSHOT.jar \
  --app.catalog.import-file=../.cache/catalog/ready-catalog.json
```

启动时一次性事务导入。后续正常启动可省略导入参数。图片路径默认是 `backend/.data/catalog-posters/`（相对于进程工作目录），也可通过 `--app.catalog.poster-directory=/absolute/path` 指定。

导入先按来源 URL 匹配，再对未绑定来源的电影按中文标题和年份精确匹配。重复执行更新原条目；遇到歧义整批回滚。保留原电影 ID、首页甄选、用户、片单和个人评分；后续人工编辑过的电影会跳过，不被自动覆盖。删除电影仍按现有管理逻辑处理，导入本身不执行删除。

Flyway 仍内置 18 部种子电影，适用于轻量启动和隔离测试；仓库另附完整的 923 部资料快照，可按上方命令离线导入。两种方式均不依赖旧 `Movie_Recommend` 目录。重新抓取的资料仍写入忽略目录，需核对来源、字段和引用海报后再更新版本化片库及清单。
