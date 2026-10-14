#!/usr/bin/env python3
"""Prepare a checksummed, private MovieLens training dataset and conservative identity map."""
import argparse
import csv
import hashlib
import json
import re
import unicodedata
import urllib.parse
import urllib.request
from collections import defaultdict
from pathlib import Path


def normalized(title):
    title = re.sub(r"\s*\(\d{4}\)\s*$", "", title)
    match = re.match(r"^(.*),\s*(The|A|An)$", title, re.I)
    if match:
        title = match[2] + " " + match[1]
    return "".join(c for c in unicodedata.normalize("NFKC", title).casefold() if c.isalnum())


def movie_rows(directory):
    if (directory / "movies.csv").exists():
        with (directory / "movies.csv").open(encoding="utf-8-sig", newline="") as f:
            return {int(r["movieId"]): r["title"] for r in csv.DictReader(f)}
    result = {}
    for line in (directory / "movies.dat").read_text(encoding="iso-8859-1").splitlines():
        if "::" in line:
            movie_id, title, _ = line.split("::", 2)
        else:
            movie_id, rest = line.split(",", 1)
            title, _ = rest.rsplit(",", 1)
        result[int(movie_id)] = title
    return result


def year(title):
    found = re.search(r"\((\d{4})\)\s*$", title)
    return int(found[1]) if found else 0


def fetch_catalog(url):
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme != "https" and not (parsed.scheme == "http" and parsed.hostname in {"127.0.0.1", "localhost"}):
        raise ValueError("远程目录必须使用 HTTPS")
    result = []
    page = 1
    while True:
        with urllib.request.urlopen(url.rstrip("/") + f"/api/movies?page={page}&size=48&sort=recent", timeout=30) as response:
            body = json.load(response)
        result.extend(body["items"])
        if len(result) >= body["total"]:
            return result
        if not body["items"] or page > 200:
            raise ValueError("电影目录分页不完整或超出限制")
        page += 1


def prepare(args):
    source, links = Path(args.movielens), Path(args.links)
    titles, linked_titles = movie_rows(source), movie_rows(links)
    ids = defaultdict(set)
    with (links / "links.csv").open(encoding="utf-8-sig", newline="") as f:
        for row in csv.DictReader(f):
            key = int(row["movieId"])
            # Never equate IDs alone across files: verify the title and release year too.
            if key in titles and key in linked_titles and row["imdbId"] and year(titles[key]) == year(linked_titles[key]) and normalized(titles[key]) == normalized(linked_titles[key]):
                ids["tt" + row["imdbId"].zfill(7)].add(key)
    wikidata = defaultdict(set)
    for row in json.loads(Path(args.wikidata).read_text()):
        if "douban" in row and "imdb" in row:
            wikidata[row["douban"]["value"]].add(row["imdb"]["value"])
    exact = defaultdict(set)
    for row in json.loads(Path(args.catalog_metadata).read_text()):
        m = row["movie"]
        for title in {m["title"], m["originalTitle"]}:
            exact[(normalized(title), m["releaseYear"])].add(row["sourceId"])
    catalog = fetch_catalog(args.base_url)
    proposed, excluded = [], []
    for movie in catalog:
        sources = {movie["sourceId"]} if movie.get("sourceUrl", "") and "douban.com/subject/" in movie["sourceUrl"] else set()
        evidence = "source-id + wikidata-imdb + movielens-title-year"
        if not sources:
            sources = exact[(normalized(movie["title"]), movie["releaseYear"])] | exact[(normalized(movie["originalTitle"]), movie["releaseYear"])]
            evidence = "unique-catalog-title-year + wikidata-imdb + movielens-title-year"
        matches = set()
        if len(sources) == 1:
            imdbs = wikidata[next(iter(sources))]
            if len(imdbs) == 1:
                imdb = next(iter(imdbs))
                if len(ids[imdb]) == 1:
                    external = next(iter(ids[imdb]))
                    if year(titles[external]) == movie["releaseYear"]:
                        matches.add((external, imdb))
        if len(matches) != 1:
            excluded.append({"movieId": movie["id"], "title": movie["title"], "reason": "no unambiguous identity and year match"})
            continue
        external, imdb = next(iter(matches))
        proposed.append({"movieId": movie["id"], "itemId": external, "title": movie["title"], "releaseYear": movie["releaseYear"],
                         "sourceUrl": movie.get("sourceUrl"), "imdbId": imdb, "evidence": evidence})
    counts = defaultdict(int)
    for row in proposed:
        counts[row["itemId"]] += 1
    mappings = [row for row in proposed if counts[row["itemId"]] == 1]
    excluded.extend({"movieId": row["movieId"], "title": row["title"], "reason": "duplicate local movie identity"}
                    for row in proposed if counts[row["itemId"]] != 1)
    output = Path(args.output)
    if output.exists():
        raise ValueError("输出目录已存在，请使用新的目录名；不覆盖正在使用的训练源")
    output.mkdir(parents=True)
    users, items, pairs = set(), set(), set()
    count = 0
    source_file = source / ("ratings.csv" if (source / "ratings.csv").exists() else "ratings.dat")
    with source_file.open(encoding="utf-8-sig") as f, (output / "ratings.csv").open("w", newline="") as target:
        writer = csv.writer(target, lineterminator="\n")
        writer.writerow(["userId", "movieId", "rating", "timestamp"])
        if source_file.suffix == ".csv":
            rows = ((r["userId"], r["movieId"], r["rating"], r["timestamp"]) for r in csv.DictReader(f))
        else:
            rows = (line.strip().split("::" if "::" in line else ",") for line in f)
        for u, i, rating, timestamp in rows:
            u, i, rating, timestamp = int(u), int(i), float(rating), int(timestamp)
            if u <= 0 or i not in titles or not .5 <= rating <= 5 or timestamp < 0 or (u, i) in pairs:
                raise ValueError("评分非法或重复，未生成完整训练清单")
            writer.writerow([u, i, rating, timestamp]); users.add(u); items.add(i); pairs.add((u, i)); count += 1
            if count > 2_000_000:
                raise ValueError("当前单机训练上限为 200 万条评分")
    mappings = [m for m in mappings if m["itemId"] in items]
    (output / "mappings.json").write_text(json.dumps(mappings, ensure_ascii=False, indent=2))
    digest = lambda p: hashlib.sha256(p.read_bytes()).hexdigest()
    manifest = {"format": "cinematch.ratings.v1", "dataset": args.name, "ratings": count, "users": len(users), "items": len(items),
                "catalogMovies": len(catalog), "mappedItems": len(mappings), "sha256": {n: digest(output / n) for n in ["ratings.csv", "mappings.json"]},
                "sourceSha256": {str(source_file.name): digest(source_file), "links.csv": digest(links / "links.csv")},
                "source": "https://grouplens.org/datasets/movielens/", "externalUsersAreSiteAccounts": False}
    (output / "mapping-report.json").write_text(json.dumps({"mapped": mappings, "excluded": excluded}, ensure_ascii=False, indent=2))
    (output / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--movielens", required=True)
    p.add_argument("--links", required=True, help="Official MovieLens directory containing links.csv and movies.csv")
    p.add_argument("--wikidata", required=True)
    p.add_argument("--catalog-metadata", required=True)
    p.add_argument("--output", required=True)
    p.add_argument("--name", default="movielens-1m")
    p.add_argument("--base-url", default="http://127.0.0.1:8080")
    prepare(p.parse_args())
