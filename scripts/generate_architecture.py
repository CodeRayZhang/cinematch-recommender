#!/usr/bin/env python3
"""Generate CineMatch's code-backed architecture diagrams (SVG + optional PNG).

SVG generation and --check use only Python's standard library. PNG export needs
Pillow and a local CJK font; fonts are never copied into the repository.
"""

import argparse
import hashlib
import math
from pathlib import Path
import struct
import unicodedata
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs" / "diagrams"
WIDTH, SCALE = 1600, 2
BG, INK, MUTED = "#f4f5ef", "#1b3028", "#53665d"
LINE, BORDER, WHITE = "#83998c", "#d6dfd7", "#ffffff"
SAGE, CORAL, PALE, WARM = "#426955", "#d66b4e", "#e8efe5", "#faeee5"
FONT = "'PingFang SC','Noto Sans CJK SC','Microsoft YaHei','Heiti SC',sans-serif"


class Diagram:
    def __init__(self, name, number, title, subtitle, height, description):
        self.name, self.height, self.ops = name, height, []
        self.svg = ET.Element("svg", {
            "xmlns": "http://www.w3.org/2000/svg", "width": str(WIDTH),
            "height": str(height), "viewBox": f"0 0 {WIDTH} {height}",
            "role": "img", "aria-labelledby": "title desc",
        })
        ET.SubElement(self.svg, "title", {"id": "title"}).text = "CineMatch · " + title
        ET.SubElement(self.svg, "desc", {"id": "desc"}).text = description
        self.rect(0, 0, WIDTH, height, BG, radius=0)
        self.rect(0, 0, WIDTH, 140, INK, radius=0)
        self.text(64, 46, "CINEMATCH  /  MOVIE RECOMMENDATION SYSTEM", 17, "#c9d8c8", bold=True)
        self.text(64, 104, title, 38, WHITE, bold=True)
        self.text(1536, 49, f"{number:02d} / 05", 20, "#b7c9b9", anchor="end")
        self.text(1536, 106, subtitle, 17, "#b7c9b9", anchor="end")
        self.route([(64, height - 53), (1536, height - 53)], arrow=False, color=BORDER, width=1)
        self.text(64, height - 23, "CineMatch  ·  当前实现 / Code-backed diagrams", 16, MUTED)
        self.text(1536, height - 23, "2026-10-14  ·  可编辑源文件随代码维护", 16, MUTED, anchor="end")

    def rect(self, x, y, w, h, fill=WHITE, stroke=None, radius=16, dashed=False):
        attrs = {"x": str(x), "y": str(y), "width": str(w), "height": str(h),
                 "rx": str(radius), "fill": fill}
        if stroke:
            attrs.update({"stroke": stroke, "stroke-width": "1.5"})
        if dashed:
            attrs["stroke-dasharray"] = "8 6"
        ET.SubElement(self.svg, "rect", attrs)
        self.ops.append(("rect", x, y, w, h, fill, stroke, radius, dashed))

    def text(self, x, y, value, size=22, color=INK, bold=False, anchor="start", max_width=None):
        # Conservative width estimate: protects SVG text without a platform font dependency.
        estimate = sum(1 if unicodedata.east_asian_width(c) in "WF" else .60 for c in value) * size
        if max_width is not None and estimate > max_width:
            raise ValueError(f"{self.name}: text exceeds budget ({estimate:.0f}>{max_width}): {value}")
        attrs = {"x": str(x), "y": str(y), "font-family": FONT, "font-size": str(size),
                 "fill": color, "text-anchor": anchor, "font-weight": "600" if bold else "400"}
        ET.SubElement(self.svg, "text", attrs).text = value
        self.ops.append(("text", x, y, value, size, color, bold, anchor, max_width))

    def route(self, points, arrow=True, color=LINE, width=2.5, dashed=False):
        attrs = {"points": " ".join(f"{x},{y}" for x, y in points), "fill": "none",
                 "stroke": color, "stroke-width": str(width), "stroke-linejoin": "round"}
        if dashed:
            attrs["stroke-dasharray"] = "8 6"
        ET.SubElement(self.svg, "polyline", attrs)
        self.ops.append(("line", points, color, width, dashed))
        if arrow:
            (x0, y0), (x, y) = points[-2:]
            angle = math.atan2(y - y0, x - x0)
            shape = [(x, y)] + [(x - 12 * math.cos(angle) + side * 5.5 * math.sin(angle),
                                  y - 12 * math.sin(angle) - side * 5.5 * math.cos(angle)) for side in (-1, 1)]
            ET.SubElement(self.svg, "polygon", {"points": " ".join(f"{a:.2f},{b:.2f}" for a, b in shape), "fill": color})
            self.ops.append(("polygon", shape, color))

    def card(self, x, y, w, h, title, lines=(), color=SAGE, fill=WHITE, title_size=25, body_size=21):
        self.rect(x, y, w, h, fill, BORDER)
        self.rect(x + 22, y + 25, 5, 25, color, radius=2)
        self.text(x + 42, y + 46, title, title_size, color, bold=True, max_width=w - 66)
        for i, line in enumerate(lines):
            self.text(x + 24, y + 84 + i * 30, line, body_size, MUTED, max_width=w - 48)
        if lines and 84 + (len(lines) - 1) * 30 > h - 16:
            raise ValueError(f"{self.name}: card is too short: {title}")

    def badge(self, x, y, w, label, warm=False):
        self.rect(x, y, w, 32, WARM if warm else PALE, radius=8)
        self.text(x + w / 2, y + 23, label, 16, CORAL if warm else SAGE, bold=True, anchor="middle", max_width=w - 16)

    def bytes(self):
        ET.indent(self.svg, space="  ")
        return ET.tostring(self.svg, encoding="utf-8", xml_declaration=True) + b"\n"


def architecture():
    d = Diagram("01-system-architecture", 1, "系统技术架构", "SYSTEM ARCHITECTURE", 1080,
                "Vue 3 单页应用通过同源 HTTP/JSON 访问 Spring Boot 4 单体应用。Spring Security 保护业务 API，MyBatis 与少量 JdbcTemplate 访问 H2；MySQL 与 Compose 由 CI 运行真实实例验收。Vite 产物打入同一 JAR，海报从独立本地目录提供。")
    d.card(64, 174, 1472, 114, "浏览器 · Vue 3 单页应用", ["TypeScript 5.9  /  Vue Router 4  /  Vite 8  /  Lucide 图标  ·  电影发现、推荐、片单、个人中心与管理页面"], title_size=27, body_size=21)
    d.route([(632, 288), (632, 344)])
    d.text(658, 324, "同源 HTTP / JSON  ·  Cookie Session", 19, MUTED)
    d.rect(64, 344, 1136, 456, WHITE, BORDER)
    d.text(88, 386, "Spring Boot 4.0.8  /  Java 21  /  Spring MVC", 28, INK, bold=True)
    d.rect(88, 410, 1088, 66, PALE, radius=10)
    d.text(110, 451, "Spring Security  ·  Session 登录态  ·  CSRF 校验  ·  USER / ADMIN 授权  ·  BCrypt", 22, SAGE, bold=True)
    d.text(88, 514, "按业务组织的 Controller / Service 模块", 20, MUTED)
    modules = [
        ("账号与资料", "auth", "登录 / 个人偏好"), ("电影目录", "movie", "查询 / 详情"),
        ("私人片单", "collection", "合集 / 观影记录"), ("推荐排序", "movie", "内容 / 邻域"),
        ("内容管理", "admin", "专题 / 观察"), ("目录导入", "catalog", "显式启动导入"),
    ]
    for i, (title, package, description) in enumerate(modules):
        x = 88 + i * 184
        d.rect(x, 534, 168, 126, BG, BORDER, radius=10)
        d.text(x + 16, 569, title, 22, INK, bold=True, max_width=136)
        d.text(x + 16, 600, package, 18, SAGE)
        d.text(x + 16, 634, description, 17, MUTED, max_width=140)
    d.route([(632, 660), (632, 694)])
    d.rect(88, 694, 1088, 76, PALE, radius=10)
    d.text(110, 726, "持久化 · MyBatis Mapper / 参数化 SQL", 24, SAGE, bold=True)
    d.text(110, 754, "MyBatis + JdbcTemplate；事务保护片单、合集、评分与导入；公共候选 / 特征使用有界缓存。", 20, MUTED)
    d.card(1232, 344, 304, 212, "构建与交付", ["Vite → frontend/dist", "Maven → Boot JAR", "页面与 API 同进程提供", "开发代理：5173 → 8080"], title_size=24, body_size=18)
    d.card(1232, 580, 304, 220, "维护与校验", ["Flyway 版本化迁移", "GitHub Actions CI", "H2 / MySQL 契约测试", "桌面 / 手机 E2E 验收"], title_size=24, body_size=19)
    d.route([(432, 800), (432, 854)])
    d.route([(984, 800), (984, 826), (1168, 826), (1168, 854)])
    d.card(64, 854, 712, 154, "关系数据库", ["H2 文件库：本地已验证", "MySQL 8.4 / Compose：CI 真实实例验收"], title_size=26, body_size=21)
    d.card(800, 854, 736, 154, "图片与本地文件", ["JAR 内静态资源 + 独立 catalog-posters 目录", "来源快照、图片校验记录、备份保存在本地"], title_size=26, body_size=21)
    return d


def user_features():
    d = Diagram("02-user-features", 2, "用户功能结构", "USER EXPERIENCE", 1120,
                "游客可发现、搜索、浏览详情和使用类型时长推荐。登录用户继承游客功能，并可维护资料、片单、评分短评和隐藏推荐。图只包含当前已实现能力。")
    d.card(520, 170, 560, 110, "CineMatch 用户", ["游客可直接使用；支持注册、登录与退出"], title_size=29, body_size=20)
    d.route([(800, 280), (800, 310), (422, 310), (422, 344)])
    d.route([(800, 310), (1178, 310), (1178, 344)])
    for x, title, subtitle, accent in [(64, "游客 · 无需登录", "发现电影与即时推荐", SAGE), (820, "登录用户 · USER", "包含游客功能，增加私人观影记录", CORAL)]:
        d.rect(x, 344, 716, 654, PALE if x == 64 else WARM, BORDER)
        d.text(x + 28, 392, title, 29, accent, bold=True)
        d.text(x + 28, 425, subtitle, 21, MUTED)
    cards = [
        (64, 450, "发现与搜索", ["电影 / 导演搜索，完整类型、年份与时长", "排序、分页、首页甄选与可配置专题"], SAGE),
        (64, 626, "电影详情", ["简介、导演、年份、片长与外部参考评分", "相似电影、公开短评和资料来源"], SAGE),
        (64, 802, "即时推荐", ["精选 / 探索，最多三种类型与观影时长", "不重复换一组，返回上一组；页面内累计 120 部"], SAGE),
        (820, 450, "个人中心", ["修改昵称与简介，设置最多八种长期偏好", "评分分页、隐藏恢复、观察记录暂停 / 清除"], CORAL),
        (820, 626, "私人片单", ["想看 / 看过、服务端分页、批量操作与撤销", "自定义合集：封面、备注、排序和 JSON 导出"], CORAL),
        (820, 802, "评分与推荐反馈", ["1–5 星评分；评分与短评可编辑或删除", "收藏和评分参与推荐；不感兴趣可隐藏"], CORAL),
    ]
    for x, y, title, lines, color in cards:
        d.card(x + 24, y, 668, 154, title, lines, color=color, title_size=26, body_size=21)
    d.text(800, 1044, "发现电影  →  想看 / 看过  →  记录感受  →  调整个人推荐", 24, INK, bold=True, anchor="middle")
    return d


def admin_features():
    d = Diagram("03-admin-features", 3, "管理后台与权限边界", "ADMIN CAPABILITIES", 1000,
                "当前仅 USER 与 ADMIN 两种角色。ADMIN 通过 Spring Security 访问管理 API，可增删改电影、设置首页甄选、查看最近100位用户公开字段及运营计数；没有用户增删改、封禁、角色管理或多级管理员功能。")
    d.card(64, 178, 432, 142, "管理员登录", ["使用已初始化的 ADMIN 账号", "与用户站共用登录入口"], title_size=27, body_size=21)
    d.route([(496, 249), (560, 249)])
    d.card(560, 178, 480, 142, "服务端权限校验", ["Spring Security + CSRF", "/api/admin/** 要求 ADMIN"], title_size=27, body_size=21)
    d.route([(1040, 249), (1104, 249)])
    d.card(1104, 178, 432, 142, "内容管理页面", ["头像菜单进入 /admin", "前端入口与后端授权配合"], title_size=27, body_size=21)
    d.route([(1320, 320), (1320, 354), (800, 354), (800, 388)])
    d.rect(64, 388, 1472, 388, PALE, BORDER)
    d.text(88, 431, "ADMIN 可用能力", 24, SAGE, bold=True)
    items = [
        (88, "电影与首页专题", ["搜索分页、新增 / 编辑 / 删除", "电影资料、海报与首页甄选", "专题类型 / 年份 / 时长", "专题启用、顺序与删除"], "Movie CRUD", SAGE),
        (576, "用户概览 · 只读", ["查看最近 100 位用户", "账号、昵称、角色", "注册时间", "不返回密码或密码哈希"], "Read-only users", CORAL),
        (1064, "统计与推荐观察", ["电影 / 用户 / 评分 / 片单计数", "按版本、模式、评分历史分组", "曝光 / 点击 / 收藏 / 评分", "7 / 30 / 90 天真实保留记录"], "Observed feedback", SAGE),
    ]
    for x, title, lines, tag, accent in items:
        d.card(x, 460, 448, 282, title, lines, color=accent, title_size=27, body_size=22)
        d.badge(x + 24, 688, 220, tag, warm=accent == CORAL)
    d.card(64, 812, 712, 116, "管理员初始化", ["通过环境变量创建账号；无默认管理员和默认密码。"], title_size=24, body_size=20)
    d.card(800, 812, 736, 116, "当前边界", ["未实现用户增删改、封禁、角色编辑或多级管理员。"], color=CORAL, fill=WARM, title_size=24, body_size=20)
    return d


def recommendation_flow():
    d = Diagram("04-recommendation-flow", 4, "混合推荐计算流程", "ONLINE RANKING", 1440,
                "请求时读取候选、个人历史与邻居评分，排除已交互与隐藏影片并应用类型时长约束。内容偏好、平滑口碑与收缩的Pearson邻域分融合，再进行多样性及导演约束重排。精选模式不强制探索，探索模式最多六个探索位置，输出最多12部电影及解释。")
    d.card(64, 174, 1472, 106, "01  接收推荐请求", ["GET /api/recommendations  ·  mode / genres / maxRuntime / exclude  ·  参数白名单校验，游客可访问"], title_size=27, body_size=22)
    for x in (300, 800, 1300):
        d.route([(800, 280), (800, 308), (x, 308), (x, 336)])
    d.card(64, 336, 472, 150, "02  电影候选", ["最多 5,000 部；公共缓存 5 分钟", "变更提交失效；内容特征复用快照"], title_size=25, body_size=21)
    d.card(564, 336, 472, 150, "个人历史与偏好", ["片单、评分、长期类型与隐藏记录", "游客使用空历史；本次类型可覆盖长期类型"], title_size=25, body_size=19)
    d.card(1064, 336, 472, 150, "邻居评分召回", ["共同评分筛选，最多 200 位用户", "读取最多 50,000 条评分"], title_size=25, body_size=21)
    for x in (300, 800, 1300):
        d.route([(x, 486), (x, 516), (800, 516)], arrow=False)
    d.route([(800, 516), (800, 548)])
    d.card(64, 548, 1472, 106, "03  排除与硬筛选", ["排除已入片单、已评分、已隐藏、本轮已展示电影；匹配任一所选类型，并满足片长上限（未知片长不通过限时筛选）。"], title_size=27, body_size=20)
    for x in (300, 800, 1300):
        d.route([(800, 654), (800, 682), (x, 682), (x, 712)])
    d.card(64, 712, 472, 176, "04  内容偏好", ["类型逆频率 + 导演 / 国家 / 年代", "4–5 星正向，1–2 星负向", "3 星中性，片单提供较弱信号"], title_size=25, body_size=20)
    d.card(564, 712, 472, 176, "平滑口碑", ["参考分与评分人数共同计算", "使用 7.5 分 / 1,000 票先验平滑", "与本站用户的个人评分区分"], title_size=25, body_size=20)
    d.card(1064, 712, 472, 176, "邻域协同分", ["至少三部共同评分；Pearson + 收缩", "最多 40 位正相关邻居", "候选至少两位邻居支持；不足则回退"], title_size=25, body_size=19)
    for x in (300, 800, 1300):
        d.route([(x, 888), (x, 916), (800, 916)], arrow=False)
    d.route([(800, 916), (800, 946)])
    d.card(64, 946, 1472, 142, "05  融合打分", ["常规：0.30 × 平滑口碑 + 0.70 × 内容偏好 + 0.65 × 邻域分", "无片单和评分、且指定本次类型：0.85 × 平滑口碑 + 0.15 × 内容偏好"], title_size=27, body_size=23)
    d.route([(800, 1088), (800, 1124)])
    d.rect(64, 1124, 1472, 144, WHITE, BORDER)
    d.text(88, 1164, "06  多样性与探索重排", 27, SAGE, bold=True)
    d.text(88, 1203, "0.08 × 相似度惩罚；优先每位导演最多两部，其他候选不足时放宽。", 22, MUTED)
    d.text(88, 1241, "精选：不强制探索   /   探索：最多六个位置选择有评分依据的长尾影片，无合格候选则正常补位。", 22, MUTED)
    d.route([(800, 1268), (800, 1302)])
    d.rect(64, 1302, 1472, 64, INK, radius=12)
    d.text(800, 1344, "返回最多 12 部电影 + 推荐理由 + 探索标记 + 观察 requestId", 26, WHITE, bold=True, anchor="middle")
    return d


def data_pipeline():
    d = Diagram("05-data-and-evaluation", 5, "数据导入与离线评估", "DATA PIPELINES", 1550,
                "左侧：公开电影资料快照经Python下载、跨来源ID及字段校验、图片缓存后生成JSON，由显式启动的事务导入服务更新本地电影库。右侧：独立MovieLens数据按时间切分，训练数据生成候选和邻居，运行基线与探索评估，输出指标报告；不导入本站账号或评分。")
    d.rect(64, 174, 712, 84, PALE, BORDER)
    d.text(88, 225, "A / 公开电影资料 → 本地片库", 29, SAGE, bold=True)
    d.rect(824, 174, 712, 84, WARM, BORDER)
    d.text(848, 225, "B / MovieLens → 独立评估报告", 29, CORAL, bold=True)
    left = [
        (296, "资料获取 · Python 脚本", ["中文电影资料快照 + Wikidata ID 对照", "TMDB 数据集快照与图片来源"]),
        (474, "跨来源核验与整理", ["唯一 ID、首映年份、字段与片长规则", "图片下载 / 文件校验，记录来源 URL 与 SHA-256"]),
        (652, "生成导入文件与本地图片", ["ready-catalog.json + preparation-report.json", "catalog-posters/ 单独提供静态图片"]),
        (830, "显式启动的事务导入", ["按来源 URL 或精确标题 + 年份匹配；幂等更新", "有歧义整批回滚；保留电影 ID 与用户交互"]),
        (1008, "应用片库", ["H2 / 可选 MySQL 保存电影及来源字段", "本地已扩充 923 部；仓库自带 18 部种子样例"]),
    ]
    right = [
        (296, "独立历史数据 · MovieLens 1M", ["1,000,209 条匿名评分 + 电影类型元数据", "独立电影 ID 空间，不映射到本站电影 ID"]),
        (474, "全局按时间切分 · 80 / 20", ["800,168 条训练 / 200,041 条测试", "参考分、流行度、画像与邻居仅由训练数据计算"]),
        (652, "真实推荐器与固定对照", ["默认混合排序 / 双探索实验 / 探索模式", "100 位新样本用户，与上一轮评估用户不重叠"]),
        (830, "离线指标与配对比较", ["Precision / Recall / NDCG@12、命中率", "长尾曝光、覆盖率、类型多样性与 bootstrap"]),
        (1008, "输出报告与代码决策", ["JSON 指标 + Markdown 解释，可重复运行", "保留默认排序；探索模式由用户主动选择"]),
    ]
    for x, items, accent in [(64, left, SAGE), (824, right, CORAL)]:
        d.route([(x + 356, 258), (x + 356, 296)], color=accent)
        for i, (y, title, lines) in enumerate(items):
            d.card(x, y, 712, 140, title, lines, color=accent, title_size=25, body_size=21)
            if i < len(items) - 1:
                d.route([(x + 356, y + 140), (x + 356, y + 178)], color=accent)
    d.rect(64, 1188, 1472, 68, INK, radius=12)
    d.text(800, 1231, "来源快照、完整片库与海报仅保存在本地；评估不写入应用账号、片单或评分。", 24, WHITE, bold=True, anchor="middle")
    d.card(64, 1290, 1472, 170, "C / 本站推荐观察 · 独立于离线评估", ["登录用户的版本化推荐 → 可见曝光 / 点击 → 服务端 7 天内收藏与评分归因 → 后台分组观察", "每人最多 200 次请求、最长 90 天；个人中心可暂停 / 清除；观察比率不等于准确率或因果提升"], title_size=26, body_size=23)
    return d


def png_metadata(path):
    """Read PNG dimensions and text chunks without Pillow, for portable CI checks."""
    data, meta, dimensions = path.read_bytes(), {}, None
    if data[:8] != b"\x89PNG\r\n\x1a\n":
        raise ValueError(f"Not PNG: {path}")
    offset = 8
    while offset < len(data):
        size = struct.unpack(">I", data[offset:offset + 4])[0]
        kind, content = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + size]
        if kind == b"IHDR":
            dimensions = struct.unpack(">II", content[:8])
        if kind == b"tEXt":
            key, value = content.split(b"\0", 1)
            meta[key.decode("latin1")] = value.decode("latin1")
        offset += size + 12
    return dimensions, meta


def export_png(diagram, svg, font_path, bold_path, font_index):
    from PIL import Image, ImageDraw, ImageFont, PngImagePlugin
    image = Image.new("RGB", (WIDTH * SCALE, diagram.height * SCALE), BG)
    draw, fonts = ImageDraw.Draw(image), {}

    def font(size, bold):
        key = (size, bold)
        if key not in fonts:
            fonts[key] = ImageFont.truetype(str(bold_path if bold else font_path), round(size * SCALE), index=font_index)
        return fonts[key]

    def xy(points):
        return [(round(x * SCALE), round(y * SCALE)) for x, y in points]

    for op in diagram.ops:
        if op[0] == "rect":
            _, x, y, w, h, fill, stroke, radius, dashed = op
            draw.rounded_rectangle((round(x * SCALE), round(y * SCALE), round((x + w) * SCALE), round((y + h) * SCALE)), radius=radius * SCALE, fill=fill, outline=stroke, width=3 if stroke else 1)
        elif op[0] == "text":
            _, x, y, value, size, color, bold, anchor, max_width = op
            chosen = font(size, bold)
            if max_width and draw.textlength(value, font=chosen) > max_width * SCALE:
                raise ValueError(f"PNG text overflow: {diagram.name}: {value}")
            draw.text((round(x * SCALE), round(y * SCALE)), value, font=chosen, fill=color, anchor={"start": "ls", "middle": "ms", "end": "rs"}[anchor])
        elif op[0] == "line":
            _, points, color, width, dashed = op
            draw.line(xy(points), fill=color, width=round(width * SCALE), joint="curve")
        elif op[0] == "polygon":
            _, points, color = op
            draw.polygon(xy(points), fill=color)
    meta = PngImagePlugin.PngInfo()
    meta.add_text("SVG-SHA256", hashlib.sha256(svg).hexdigest())
    meta.add_text("Generator", "scripts/generate_architecture.py")
    image.save(OUT / f"{diagram.name}.png", pnginfo=meta, optimize=True, dpi=(144, 144))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--check", action="store_true", help="Verify checked-in SVG, PNG provenance and README links without writing")
    parser.add_argument("--png", action="store_true", help="Also export 2x PNG images (requires Pillow and a CJK font)")
    parser.add_argument("--font", type=Path, default=Path("/System/Library/Fonts/STHeiti Light.ttc"))
    parser.add_argument("--bold-font", type=Path)
    parser.add_argument("--font-index", type=int, default=1, help="TTC index; use 0 for a standalone Noto Sans CJK font")
    args = parser.parse_args()
    if args.check and args.png:
        parser.error("--check and --png cannot be combined")
    if args.png and not args.font.is_file():
        parser.error("Choose a local CJK font with --font and --font-index (see docs/diagrams/README.md)")
    diagrams = [architecture(), user_features(), admin_features(), recommendation_flow(), data_pipeline()]
    if not args.check:
        OUT.mkdir(parents=True, exist_ok=True)
    readme = (ROOT / "README.md").read_text() if args.check else ""
    for diagram in diagrams:
        svg = diagram.bytes()
        svg_path, png_path = OUT / f"{diagram.name}.svg", OUT / f"{diagram.name}.png"
        if args.check:
            if not svg_path.exists() or svg_path.read_bytes() != svg:
                raise SystemExit(f"Stale or missing SVG: {svg_path.relative_to(ROOT)}")
            dimensions, meta = png_metadata(png_path)
            if dimensions != (WIDTH * SCALE, diagram.height * SCALE) or meta.get("SVG-SHA256") != hashlib.sha256(svg).hexdigest():
                raise SystemExit(f"Stale PNG: {png_path.relative_to(ROOT)}; run generator with --png")
            for path in (svg_path, png_path):
                if str(path.relative_to(ROOT)) not in readme:
                    raise SystemExit(f"Missing README reference: {path.relative_to(ROOT)}")
        else:
            svg_path.write_bytes(svg)
            if args.png:
                bold = args.bold_font or (Path("/System/Library/Fonts/STHeiti Medium.ttc") if str(args.font).endswith("STHeiti Light.ttc") else args.font)
                export_png(diagram, svg, args.font, bold, args.font_index)
        print(("Verified" if args.check else "Generated") + f" {diagram.name}")


if __name__ == "__main__":
    main()
