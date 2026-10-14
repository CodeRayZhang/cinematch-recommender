# 公开电影目录

## 数据来源与边界

- 中文名称、类型、导演、简介、评分及人数：[dengfuping/douban-movies-spider](https://github.com/dengfuping/douban-movies-spider/tree/671045f9470ed41394c635589e3b65388ff11e46/data)。固定提交 `671045f9470ed41394c635589e3b65388ff11e46`，2,065 条，发布者标注采集于 2025 年。评分是历史快照，不是实时评分。
- 首映年份与 ID 对应：[Wikidata](https://www.wikidata.org/)，使用 Douban ID（P4529）、IMDb ID（P345）、TMDB ID（P4947）及最早发行日期（P577）。仅接受唯一的跨来源匹配，不依赖模糊片名猜测。结构化数据采用 CC0。
- 原片名、海报路径：[Asaniczka / TMDB Movies Dataset](https://www.kaggle.com/datasets/asaniczka/tmdb-movies-dataset-2023-930k-movies)，读取公开 CSV 的有界前缀，保留完整记录，并逐片校验 ID 与图片。此次来源文件更新于 2026-10-13。完整文件约 668 MB，本次仅读取约 12 MiB，得到 16,555 条完整记录用于匹配，未下载整库。
- 海报：[TMDB 图片 CDN](https://developer.themoviedb.org/docs/image-basics)，下载到本地后显示。每张图片的原 URL 与 SHA-256 保存在本地报告中；电影图片权利归各自权利人。

中文来源的 Kaggle 元数据标注 CC0，但其数据说明限定教育和研究用途，因此这里按本地学习项目使用，不将第三方原始目录、简介及海报打包发布到 GitHub。项目代码的 MIT 许可不代表第三方图片授权。正式对外运营前，应按实际用途确认数据来源授权，可改用自己的 TMDB 官方 API 凭据维护目录。

没有导入第三方用户账号、个人评论或评分明细。外部参考分与本站用户自己的评分分别保存。

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

新仓库仍携带 18 部种子电影，完整公开目录需运行上述命令获取，不依赖旧 `Movie_Recommend` 目录。
