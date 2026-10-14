# CineMatch — Movie Recommendation System

**Find your next favorite film.**

CineMatch 是一个可持续维护的个人电影推荐系统，围绕电影发现、私人片单和观影感受设计。新版采用 Vue 3 + TypeScript + Spring Boot 4 + Java 21，用户站与管理后台共用一套 API。

![CineMatch desktop preview](docs/screenshots/desktop.jpg)

## 文档导航

| 想了解什么 | 文档 |
|---|---|
| 环境准备、开发启动、JAR / MySQL 部署、管理员、备份与升级 | [详细部署与运行指南](docs/deployment.md) |
| 注册、个人中心、片单、推荐与管理后台的操作顺序 | [页面操作与执行步骤](docs/usage.md) |
| 当前算法、信号权重、冷启动、探索模式及评估复现 | [推荐算法与评估指南](docs/recommendation.md) |
| 推荐效果、对照实验与局限 | [基础混合推荐评估](docs/recommendation-evaluation.md) · [探索模式评估](docs/recommendation-discovery-evaluation.md) |
| 公开目录获取、资料校验与来源 | [电影数据来源](docs/data-sources.md) |
| 模块边界、图表维护与已完成验收 | [架构说明](docs/architecture.md) · [图表维护](docs/diagrams/README.md) · [验收记录](docs/verification.md) |

## 功能

- 发现：电影搜索、类型筛选、排序、分页、编辑甄选与心情入口。
- 详情：电影资料、相似电影、个人评分、短评；独立想看 / 看过按钮，移出可撤销。
- 账号：注册、登录、退出；密码使用 BCrypt；服务端 Session、CSRF 校验、管理员角色控制。
- 个人中心：昵称、简介、偏好类型、观影统计、评分短评管理及隐藏推荐恢复。
- 片单：想看 / 看过切换、搜索排序、分页、多选批量操作及撤销；移出片单保留独立评分。
- 推荐：内容与相似用户评分融合；精选 / 探索模式、临时类型与时长筛选、不重复换一组；支持游客冷启动，排除已收藏、评分及隐藏的影片。
- 管理：电影增删改查、首页甄选、用户概览及统计。无默认管理员账号。
- 数据：Flyway 数据库迁移；本地 H2 文件库；MySQL 配置和 Docker Compose 配套。

仓库自带 18 部界面种子样例，也提供公开电影资料获取、ID 校验、海报缓存与事务导入工具，见 [公开电影目录](docs/data-sources.md)。页面区分演示评分、外部数据集历史参考分和个人评分；不会将历史快照冒充实时评分。

## 架构与功能图

以下图示对应当前代码实现。点击图片可打开 SVG 放大查看；PNG、SVG 与 [生成脚本](scripts/generate_architecture.py) 一起保存在仓库中，更新方法及代码对应关系见 [图表维护说明](docs/diagrams/README.md)。数据导入与离线评估图收录于独立算法文档，见下方入口。

### 1. 系统技术架构

Vue 单页应用与 Spring Boot API 组成一个可独立运行的应用，生产构建将页面打入同一 JAR。H2 本地运行已验证；MySQL / Docker Compose 为已提供、尚未实机验证的部署配置。

[![CineMatch 系统技术架构：Vue 3、Spring Boot 4、Spring Security、MyBatis、H2 与本地图片存储](docs/diagrams/01-system-architecture.png)](docs/diagrams/01-system-architecture.svg)

### 2. 用户功能结构

游客可以浏览和按口味获取推荐；登录后可维护个人资料、片单、评分短评与推荐反馈。

[![CineMatch 用户功能结构：游客发现与推荐、登录用户的个人中心和私人观影记录](docs/diagrams/02-user-features.png)](docs/diagrams/02-user-features.svg)

### 3. 管理后台与权限边界

当前使用 USER / ADMIN 两种角色。管理员可以维护电影、查看用户概览与统计；用户概览只读，多级管理员和用户增删改尚未实现。

[![CineMatch 管理功能：ADMIN 鉴权、电影管理、只读用户概览和统计](docs/diagrams/03-admin-features.png)](docs/diagrams/03-admin-features.svg)

### 4. 混合推荐计算流程

请求时融合内容偏好、平滑口碑与邻域协同评分，再进行多样性重排；最多返回 12 部电影及推荐理由。信号、公式与模式说明见 [推荐算法指南](docs/recommendation.md)。

[![CineMatch 推荐流程：请求与召回、排除筛选、三路评分、融合重排和推荐解释](docs/diagrams/04-recommendation-flow.png)](docs/diagrams/04-recommendation-flow.svg)

### 5. 数据与评估图入口

[查看独立文档中的数据导入与离线评估图](docs/recommendation.md#8-评估与复现) · [PNG 图片](docs/diagrams/05-data-and-evaluation.png) · [SVG 矢量图](docs/diagrams/05-data-and-evaluation.svg)。评估方法、结果、局限与复现命令统一放在独立文档中。

## 快速开始

在仓库根目录执行，需要 Node.js 22.12+、Java 21、Maven 3.9+：

```sh
bash scripts/dev.sh
```

首次自动安装前端依赖。打开 `http://127.0.0.1:5173`，后端监听 `http://127.0.0.1:8080`，按 Ctrl+C 停止。H2 数据默认保存在 `backend/.data/`；普通账号在页面注册，管理员初始化见 [部署指南](docs/deployment.md#5-初始化管理员)。

完整页面与 API 可打包为一个 JAR。停止开发服务后，在仓库根目录执行：

```sh
bash scripts/build.sh
mkdir -p .cache/runtime
release_id="$(date +%Y%m%d-%H%M%S)"
cp backend/target/cinematch-2.0.0-SNAPSHOT.jar ".cache/runtime/cinematch-${release_id}.jar"
cd backend
java -jar "../.cache/runtime/cinematch-${release_id}.jar"
```

打开 `http://127.0.0.1:8080`。从 `backend/` 启动以沿用开发数据；每次使用新的运行 JAR 文件名。其他部署方式、扩充片库海报的开发代理限制及故障排查见 [详细部署与运行指南](docs/deployment.md)，初次体验可按 [页面操作步骤](docs/usage.md) 执行。

## 目录

```text
frontend/        Vue 页面、组件、API 客户端与本地海报
backend/         Spring Boot API、权限、推荐、Flyway 和测试
scripts/         开发、构建及目录导入工具
docs/            部署操作、架构图、算法评估、数据来源与验收记录
```

本项目从毕业设计 [Movie_Recommend](https://github.com/CodeRayZhang/Movie_Recommend) 的现代化重写独立而来，使用独立 Git 历史、数据库及部署配置，保留原仓库。新程序不依赖旧模块，旧数据库账号、历史评分和 ALS 模型尚未自动迁移。

## License

Code is available under the [MIT License](LICENSE). Movie artwork remains the property of its respective rights holders; see [artwork sources](docs/artwork.md).
