#!/usr/bin/env python3
"""Validate or import a UTF-8 movie catalog through authenticated admin APIs.

python3 scripts/import_catalog.py docs/demo-catalog.json
ADMIN_USERNAME=... ADMIN_PASSWORD=... python3 scripts/import_catalog.py catalog.json --apply
By default only validates; credentials are read from the environment and never printed.
"""
import argparse
import http.cookiejar
import json
import os
from pathlib import Path
import sys
import urllib.error
import urllib.parse
import urllib.request

FIELDS = {
    'title': 'title', 'original_title': 'originalTitle', 'release_year': 'releaseYear',
    'genres': 'genres', 'runtime': 'runtime', 'rating': 'rating', 'rating_count': 'ratingCount',
    'director': 'director', 'country': 'country', 'synopsis': 'synopsis',
    'poster_url': 'posterUrl', 'backdrop_url': 'backdropUrl', 'featured': 'featured',
}

def normalize(row):
    if not isinstance(row, dict):
        raise ValueError('每条电影资料必须是 JSON 对象')
    result = {target: row.get(target, row.get(source)) for source, target in FIELDS.items()}
    missing = [key for key, value in result.items() if value is None]
    if missing:
        raise ValueError('缺少字段：' + ', '.join(missing))
    if not isinstance(result['title'], str) or not result['title'].strip():
        raise ValueError('电影名称不能为空')
    if not 1888 <= int(result['releaseYear']) <= 2100:
        raise ValueError('上映年份不正确')
    if not 0 <= float(result['rating']) <= 10:
        raise ValueError('参考评分应为 0–10')
    return result

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('catalog', type=Path)
    parser.add_argument('--base-url', default='http://127.0.0.1:8080')
    parser.add_argument('--apply', action='store_true', help='实际写入；默认仅验证文件')
    args = parser.parse_args()
    rows = json.loads(args.catalog.read_text(encoding='utf-8'))
    if not isinstance(rows, list):
        raise ValueError('目录需要是 JSON 数组')
    payloads = [normalize(row) for row in rows]
    keys = [(row['originalTitle'], row['releaseYear']) for row in payloads]
    if len(set(keys)) != len(keys):
        raise ValueError('文件内有重复的原片名 / 年份，需先消歧')
    if not args.apply:
        print(f'结构验证通过：{len(payloads)} 部电影。没有连接服务或写入数据。')
        return
    base = urllib.parse.urlparse(args.base_url)
    if base.scheme != 'https' and not (base.scheme == 'http' and base.hostname in {'localhost', '127.0.0.1', '::1'}):
        raise ValueError('远程导入必须使用 HTTPS')
    username, password = os.environ.get('ADMIN_USERNAME'), os.environ.get('ADMIN_PASSWORD')
    if not username or not password:
        raise ValueError('实际导入需要 ADMIN_USERNAME 和 ADMIN_PASSWORD 环境变量')
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    token = ''
    def request(method, path, data=None, form=False):
        headers = {'Accept': 'application/json'}
        body = None
        if data is not None:
            headers['Content-Type'] = 'application/x-www-form-urlencoded' if form else 'application/json'
            body = (urllib.parse.urlencode(data) if form else json.dumps(data, ensure_ascii=False)).encode()
        if token and method != 'GET':
            headers['X-XSRF-TOKEN'] = token
        req = urllib.request.Request(args.base_url.rstrip('/') + '/api' + path, data=body, headers=headers, method=method)
        with opener.open(req, timeout=30) as response:
            return json.load(response)
    token = request('GET', '/session')['csrfToken']
    request('POST', '/auth/login', {'username': username, 'password': password}, True)
    session = request('GET', '/session')
    token = session['csrfToken']
    if not session['user'] or session['user']['role'] != 'ADMIN':
        raise ValueError('当前账号没有管理员权限')
    created = updated = 0
    try:
        for row in payloads:
            result = request('GET', '/movies?' + urllib.parse.urlencode({'q': row['originalTitle'], 'size': 48}))
            matches = [m for m in result['items'] if (m['originalTitle'], m['releaseYear']) == (row['originalTitle'], row['releaseYear'])]
            if len(matches) > 1 or result['total'] > 48:
                raise ValueError(f'电影匹配存在歧义：{row["title"]}')
            if matches:
                request('PUT', f'/admin/movies/{matches[0]["id"]}', row); updated += 1
            else:
                request('POST', '/admin/movies', row); created += 1
        print(f'导入完成：新增 {created}，更新 {updated}。')
    finally:
        request('POST', '/auth/logout')

if __name__ == '__main__':
    try:
        main()
    except (ValueError, OSError, urllib.error.HTTPError) as error:
        print(f'导入停止：{error}。已成功的条目会保留，可修正后重跑。', file=sys.stderr)
        sys.exit(1)
