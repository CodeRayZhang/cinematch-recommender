# CineMatch 架构图维护

这五张图以 2026-10-14 的当前实现为依据，涵盖技术架构、用户功能、管理权限、在线推荐和离线数据流程。它们是对旧版毕业设计图示主题的重新整理，不是旧版 Hadoop / Spark / Kafka 架构的复用。图中不展示尚未实现的能力为现有能力。

## 文件与代码依据

| 图 | 主要代码依据 | 说明 |
|---|---|---|
| [01 系统架构](01-system-architecture.svg) | [前端依赖](../../frontend/package.json)、[Maven 配置](../../backend/pom.xml)、[安全配置](../../backend/src/main/java/com/cinematch/config/SecurityConfig.java)、[图片与目录配置](../../backend/src/main/java/com/cinematch/catalog/CatalogConfiguration.java)、[Compose](../../compose.yml) | 单体应用与单 JAR 交付，H2 本地验收，CI 增加真实 MySQL / Compose 验收。模块为概念分组，推荐查询与排序位于 `movie` 包，训练、版本管理与个人 fold-in 位于 `recommender` 包。 |
| [02 用户功能](02-user-features.svg) | [前端路由](../../frontend/src/main.ts)、[个人资料](../../backend/src/main/java/com/cinematch/auth/ProfileController.java)、[片单与评分](../../backend/src/main/java/com/cinematch/library/LibraryController.java)、[命名片单](../../backend/src/main/java/com/cinematch/collection/CollectionService.java)、[推荐页](../../frontend/src/views/PicksView.vue) | 登录用户包含游客浏览能力；临时筛选与长期偏好分别保存。 |
| [03 管理功能](03-admin-features.svg) | [管理 API](../../backend/src/main/java/com/cinematch/admin/AdminController.java)、[管理员初始化](../../backend/src/main/java/com/cinematch/config/AdminBootstrap.java)、[管理页面](../../frontend/src/views/AdminView.vue) | 当前只有 USER / ADMIN；用户概览是只读查询，不能据此推断有用户管理 CRUD。 |
| [04 推荐流程](04-recommendation-flow.svg) | [推荐 API](../../backend/src/main/java/com/cinematch/movie/RecommendationController.java)、[排序器](../../backend/src/main/java/com/cinematch/movie/RecommendationService.java)、[邻居召回](../../backend/src/main/java/com/cinematch/library/LibraryMapper.java) | 图展示逻辑步骤；内容特征统计使用完整召回目录与历史电影，而非仅筛选后的子集。模型无效、映射或评分不足时回到原混合分支；有效模型使用最新个人评分即时 fold-in。 |
| [05 数据与评估](05-data-and-evaluation.svg) | [获取脚本](../../scripts/fetch_movie_sources.py)、[资料整理](../../scripts/prepare_public_catalog.py)、[事务导入](../../backend/src/main/java/com/cinematch/catalog/CatalogImportService.java)、[版本化片库](../../data/catalog/NOTICE.md)、[片库校验](../../scripts/check_catalog_bundle.py)、[模型评估](../../backend/src/test/java/com/cinematch/AlsEvaluationTest.java)、[训练准备](../../scripts/prepare_recommender.py) | 展示正式公开目录启动导入路径；另一种手工目录工具 `import_catalog.py` 经管理 API 逐条写入，不具有整批回滚语义。 |

所有 PNG 都是 2 倍分辨率（宽 3,200 像素），供 README、文档和汇报使用。对应 SVG 保留文字、连线和分组形状，可无损缩放；根 README 展示前四张图，并提供第五张图的入口；[算法文档](../recommendation.md#8-评估与复现) 展示数据与评估图。文档使用相对路径嵌入 PNG 并链接到 SVG，不依赖外部图床或生成服务。

## 更新与重新生成

唯一生成入口是 [scripts/generate_architecture.py](../../scripts/generate_architecture.py)。修改相应图的内容或布局，再重新生成图片。不要只改导出的图片，否则下次生成会覆盖手工修改。更新涉及模块、权限、推荐参数或数据流程的代码时，也应同步核对这里的图及 [架构说明](../architecture.md)。图中文字、日期与数据规模为人工维护的实现快照，不会自动从源码推断。

只生成 SVG，或检查已提交图片是否与源文件一致，使用 Python 3 标准库即可：

```sh
python3 scripts/generate_architecture.py
python3 scripts/generate_architecture.py --check
```

SVG 更新后，必须同步生成 PNG，再执行 `--check`。PNG 导出使用 Pillow 与本机中文字体，建议在独立虚拟环境安装依赖：

```sh
python3 -m venv .cache/diagram-venv
.cache/diagram-venv/bin/python -m pip install -r scripts/requirements-diagrams.txt

# macOS：脚本默认使用系统 Heiti SC，TTC 索引 1
.cache/diagram-venv/bin/python scripts/generate_architecture.py --png

# 其他系统：指定已安装、可显示简体中文的字体文件；单字体通常索引为 0
.cache/diagram-venv/bin/python scripts/generate_architecture.py --png \
  --font /absolute/path/to/NotoSansCJKsc-Regular.otf \
  --bold-font /absolute/path/to/NotoSansCJKsc-Bold.otf \
  --font-index 0

python3 scripts/generate_architecture.py --check
```

字体不打包进仓库。PNG 中保存其对应 SVG 的 SHA-256；检查命令会验证 SVG 可重建、PNG 尺寸与来源摘要匹配、README 引用存在。该检查不需要 Pillow 或中文字体，已接入 CI。使用不同系统字体导出的 PNG 像素可能不同，文字和布局仍来自同一生成源。

## 提交范围

后续与功能代码一起提交以下文件：

- `README.md`、`docs/recommendation.md` 中的图示与图片引用，以及部署、使用和评估文档。
- `docs/diagrams/` 下的五组 SVG / PNG 及本说明。
- `scripts/generate_architecture.py`、`scripts/requirements-diagrams.txt`。
- `.github/workflows/verify.yml` 中的图表一致性检查，以及相关架构说明。

第三方原始片库、电影海报、数据库、账号、运行日志和本地临时文件不属于这组图表资源。图表本身不包含密钥或用户数据。
