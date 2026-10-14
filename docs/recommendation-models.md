# 可训练推荐模型：部署、运行与维护

[返回 README](../README.md) · [算法公式](recommendation.md) · [独立模型评估](recommendation-model-evaluation.md)

这一版恢复了实际的矩阵分解训练能力：Java 21 实现显式评分 ALS-WR，离线交替学习用户和电影向量，持久化电影向量；推荐请求使用用户**最新评分**求解个人向量，再与内容、邻域协同和口碑融合。它是适合个人项目的单进程实现，没有部署 Spark / Kafka，也不是流式重训全局模型。

## 1. 先启动应用

按 [部署指南](deployment.md) 启动新版并初始化自己的管理员账号。Flyway V7 自动创建模型版本表和活动版本指针。已有电影、账号、片单和评分不变；升级前先停服务备份数据库。首次启动未训练模型时，原混合推荐仍可使用。

推荐页 `/picks` 提供观影指纹、真实模型状态、评分引导、精选与探索模式。观影指纹展示已评分电影的类型计数，按最大值归一化；它不是准确率或模型向量的可视化。至少为 3 部活动模型覆盖的电影评分后，个人向量参与推荐；新增、编辑、删除评分在下一次请求生效。未匹配电影仍保留内容 / 邻域推荐。

## 2. 准备训练源

### A. 本站评分

至少 5 位用户、5 部电影、20 条评分。管理员进入 `/admin` → **推荐模型** → 选择“本站观影评分”。该门槛用于阻止无意义的小数据训练，不是质量达标标准。数据稀疏时应保留原混合推荐或先使用外部历史评分。

### B. MovieLens 历史评分

历史评分保持独立用户命名空间，不创建本站账号，也不写入本站 reviews。当前作者本机采用旧项目已有 MovieLens 1M：1,000,209 条评分、6,040 位匿名用户、3,706 部被评分电影；与 923 部现有电影严格对应到 90 部。这个覆盖率是本机训练结果；仓库自带 923 部电影目录，但不附历史评分和已训练模型。

下面适用于刚克隆代码的本机开发环境，不需要作者的旧项目、数据库或训练文件。先按 [环境准备](deployment.md#2-准备环境与代码) 安装 Java 21、Maven 3.9+、Node.js 22.12+、Python 3.9+，准备 `curl`、`unzip`；Windows 使用 WSL。ALS 在 CPU 上运行，无需 GPU 或 Spark，建议为 JVM 预留至少 1 GB 堆空间。

**终端 A：初始化管理员并启动片库。** 进入仓库根目录，在 Bash 中执行；账号为 3–32 位小写字母、数字或下划线，密码至少 12 个字符。已有管理员可省略输入部分；同名普通账号不会自动提权。

```sh
read -r -p '管理员账号：' ADMIN_USERNAME
read -r -s -p '管理员密码：' ADMIN_PASSWORD
printf '\n'
export ADMIN_USERNAME ADMIN_PASSWORD
export JAVA_TOOL_OPTIONS='-Xmx1g'
bash scripts/dev.sh --catalog
# 服务停止后清理当前终端的设置
unset ADMIN_USERNAME ADMIN_PASSWORD JAVA_TOOL_OPTIONS
```

保持终端 A 运行，页面地址为 `http://127.0.0.1:5173`，后端地址为 `http://127.0.0.1:8080`。已有自定义 JVM 配置时合并内存设置，不要直接覆盖。

**终端 B：下载官方训练文件和电影 ID 对照资料。** 同样进入仓库根目录执行以下首次准备步骤。MovieLens 1M 提供实际训练评分；latest-small 此处只取电影表和 ID 链接表，不用它的评分替代 1M 数据。官方数据通过 [GroupLens 下载页](https://grouplens.org/datasets/movielens/1m/) 获取，使用范围见数据包 README。

`python3 scripts/fetch_movie_sources.py` 获取 Wikidata 对照资料，并缓存中文及 TMDB 元数据；需要能够访问这些外部来源，可能受网络或限流影响。该步骤不写应用数据库，也不重新下载海报。仓库自带的 `data/catalog/movies.json` 可直接用于匹配。

```sh
mkdir -p .cache/recommender
curl --fail --location --retry 3 --output .cache/recommender/ml-1m.zip \
  https://files.grouplens.org/datasets/movielens/ml-1m.zip
unzip -n .cache/recommender/ml-1m.zip \
  'ml-1m/movies.dat' 'ml-1m/ratings.dat' 'ml-1m/README' \
  -d .cache/recommender

curl --fail --location --retry 3 --output .cache/recommender/ml-latest-small.zip \
  https://files.grouplens.org/datasets/movielens/ml-latest-small.zip
unzip -n -j .cache/recommender/ml-latest-small.zip \
  'ml-latest-small/links.csv' 'ml-latest-small/movies.csv' \
  'ml-latest-small/README.txt' -d .cache/recommender

python3 scripts/fetch_movie_sources.py
```

**生成可训练数据集。** 后端保持运行，脚本读取当前数据库对应的真实电影 ID；它不写数据库。以下路径均相对于仓库根目录：

```sh
python3 scripts/prepare_recommender.py \
  --movielens .cache/recommender/ml-1m \
  --links .cache/recommender \
  --wikidata .cache/catalog/wikidata-links.json \
  --catalog-metadata data/catalog/movies.json \
  --output backend/.data/recommender/datasets/movielens-1m \
  --name movielens-1m
```

成功时打印清单，包括 `ratings`、`users`、`items`、`mappedItems` 和文件哈希。官方 1M 含 1,000,209 条评分；`mappedItems` 是能与当前片库严格匹配的电影数量，不等于全部 923 部。映射数需至少 3 才能启用模型；实际数取决于对照资料及当前片库，以输出报告为准。

**进入页面训练并启用。** 用终端 A 创建的管理员登录 → `/admin` → 推荐模型 → 刷新状态 → 选择 `movielens-1m` → 开始训练。先保持默认 16 维、15 轮、正则 0.1、种子 42；状态为 READY 后点击“启用此版本”。新用户到 `/picks` 给至少 3 部评分引导中的模型覆盖电影评分，即可让个人 ALS 向量参与推荐。READY 表示计算成功，效果评估按 [独立评估文档](recommendation-model-evaluation.md) 执行。

如果后台找不到数据集，检查应用的工作目录和 `app.recommender.data-directory`；生成文件必须位于该目录的 `datasets/movielens-1m/`。Docker 用户还需按第 4 节把准备好的目录复制进模型卷。重复准备时更换 `--output` 末级目录和 `--name`，例如都改成 `movielens-1m-v2`；不需要删除现有模型或数据库。

支持官方 `::` 分隔的 1M 文件、旧项目逗号分隔文件，以及 MovieLens CSV。下载数据前阅读并遵守 [GroupLens 使用条件](https://grouplens.org/datasets/movielens/1m/)；链接表格式见 [官方说明](https://files.grouplens.org/datasets/movielens/ml-latest-small-README.html)。原始文件、匿名评分与训练快照不再分发。

映射链为：本站豆瓣来源 ID → Wikidata IMDb ID → MovieLens links → MovieLens 电影 ID。不同 MovieLens 文件间还必须符合标题标准化和上映年份，本站年份也必须一致。手工种子只有在现有目录中“标题 + 年份”唯一对应时才进入这条链。重复、歧义、年份冲突、不在评分文件中的电影跳过；不会把两个系统的数字 ID 直接当作同一部电影。

输出 `ratings.csv`、`mappings.json`、`manifest.json`（行数及 SHA-256）、`mapping-report.json`（匹配依据与排除原因）。输出目录必须是新目录；更新时使用新版本名，例如 `movielens-1m-v2`，不覆盖旧训练源。训练开始时再次校验文件、实际行数和当前目录身份，过期映射会跳过。

## 3. 训练、启用、回退

1. 管理后台 → 推荐模型 → 刷新状态，选择数据集。
2. 默认 16 维、15 轮、正则 0.1、随机种子 42。点击“开始训练”；状态经过 QUEUED → RUNNING → READY / FAILED，页面显示迭代进度。
3. 训练使用独立后台线程；当前活动模型继续服务。外部训练源会追加已映射的本站评分，本地用户使用负 ID，与正 ID 的外部匿名用户隔离。
4. 按 [评估步骤](recommendation-model-evaluation.md) 检查方案。READY 仅表示训练成功，`summary.evaluated=false` 不会被伪装成通过效果评估。
5. 点击“启用此版本”。事务更新单条活动指针，下一次推荐读取新版本。至少 3 部有效电影映射才能启用。
6. 回退时启用保留的旧版本，或者点击“停用模型”恢复原混合推荐。活动模型与训练中任务不允许删除；非活动版本需点击二次确认。最多保留 10 个任务记录。
7. “导出模型”保存电影向量、映射、参数和摘要，不包含历史用户向量或原始评分。当前没有网页导入导出文件的入口；灾难恢复通过数据库备份，导出用于审查和外部使用。

也可在一次性的启动中训练并启用默认参数模型（工作目录为 `backend`）：

```sh
java -jar /absolute/path/to/cinematch.jar \
  --app.recommender.train=movielens-1m \
  --app.recommender.activate=true
```

训练结束后后续启动应移除这两个参数，否则每次启动会创建新任务。`activate` 默认 false；只有明确设置 true 才在训练成功后启用。训练源清单缺失会导致该启动命令失败；后台校验或训练失败会记录 FAILED，普通启动不依赖外部训练数据。

## 4. 数据与持久化

默认训练根目录为 `backend/.data/recommender`（从 backend 启动时）。可用 `--app.recommender.data-directory=/absolute/path` 指定：

```text
recommender/
  datasets/<dataset-name>/     # 训练源，只能按合法目录名选择
  runs/<model-uuid>/           # 该次实际评分、映射、摘要和校验值
```

模型权重与映射保存在数据库 `recommendation_models`，活动版本在 `recommendation_model_state`。重启会重新加载活动模型；中断中的训练任务标记 FAILED，需重新创建。已创建快照采用 CREATE_NEW 写入，删除后台版本不自动删除快照；清理前确认不再需要复现，限制目录访问并与数据库一起备份。数据库恢复后只要电影身份仍匹配即可继续服务，推理不依赖 datasets 目录。runs 记录实际训练输入用于审计和程序化复现，含站内负 ID 命名空间时不能直接作为外部数据集导入；后台重新训练仍从已准备的 datasets 或本站评分读取。

Compose 已加入 `model-data` 命名卷，保留训练源和快照。外部数据可在准备后导入卷：

```sh
docker compose exec app mkdir -p /app/.data/recommender/datasets
docker compose cp backend/.data/recommender/datasets/movielens-1m app:/app/.data/recommender/datasets/
# app 只读训练源；runs 的卷目录由运行用户拥有。
docker compose exec app ls /app/.data/recommender/datasets/movielens-1m
```

准备映射时必须连接将部署的数据库对应应用；不同数据库的电影 ID 不一定相同。`docker compose down -v` 会删除数据库与模型卷，普通重启不要使用它。生产备份包括数据库、模型卷与海报目录。

## 5. 接口与规模边界

| 接口 | 用途 |
|---|---|
| `GET /api/recommendations/status` | 实际引擎、活动模型、已映射评分数、覆盖数、维度 |
| `GET /api/me/recommendations/starters` | 登录用户尚未评分、未隐藏的候选；活动模型存在时只给有效映射 |
| `GET /api/admin/recommender/datasets` | 服务器已准备的数据集和门槛 |
| `GET /api/admin/recommender/models` | 版本、状态、进度、参数和摘要 |
| `POST /api/admin/recommender/models` | JSON：dataset、rank、iterations、regularization、seed；202 返回任务 ID |
| `POST /api/admin/recommender/models/{id}/activate` | 启用或回退 |
| `POST /api/admin/recommender/disable` | 停用模型 |
| `GET /api/admin/recommender/models/{id}/export` | 导出无原始评分的模型 |
| `DELETE /api/admin/recommender/models/{id}` | 删除非活动、非训练中版本 |

所有管理接口需要 ADMIN，写操作需要 Session CSRF；训练 API 不接受任意文件路径或远程 URL。单机上限：200 万评分、10 万用户、1 万训练电影、32 维、30 轮；默认值用于个人项目，允许上限不代表已完成最大规模并发测试。当前只支持一个应用实例管理训练，不支持多实例训练协调。建议至少预留 1 GB JVM 堆并依据实际数据监控内存；全量百万评分本机实测见评估记录。

推荐模型属于评分的派生数据。编辑、删除评分会立即改变个人 fold-in，但旧全局电影向量和历史训练快照仍反映训练时的数据；需要清除其全局影响时，重新准备数据并训练，停用并删除旧版本及相关快照、备份。个人中心“停止推荐观察”只控制曝光等观察记录，不会停止使用评分进行推荐。
