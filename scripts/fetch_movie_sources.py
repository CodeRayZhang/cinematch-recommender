#!/usr/bin/env python3
"""Fetch pinned Chinese metadata, public Wikidata identifiers and bounded TMDB metadata."""
import concurrent.futures
import json
import time
import urllib.parse
import urllib.request
from pathlib import Path

import hashlib
import subprocess
import sys

def main():
    root = Path(__file__).resolve().parents[1] / '.cache/catalog'
    root.mkdir(parents=True, exist_ok=True)
    revision = '671045f9470ed41394c635589e3b65388ff11e46'
    base = f'https://raw.githubusercontent.com/dengfuping/douban-movies-spider/{revision}/data/'
    for name in ['douban_all_movies.json', 'DATASET.md', 'dataset-metadata.json']:
        path = root / name
        if not path.exists():
            with urllib.request.urlopen(base + name, timeout=45) as response:
                data = response.read(5_000_001)
            if len(data) > 5_000_000: raise ValueError('Unexpected source size')
            path.write_bytes(data)
    digest = hashlib.sha256((root / 'douban_all_movies.json').read_bytes()).hexdigest()
    if digest != '412a9f5b6d441d2ad7193c34d33e817daac8aed88ebb57465382f050c4114775':
        raise ValueError('Pinned source checksum mismatch; do not use this snapshot')
    (root / 'upstream-revision.json').write_text(json.dumps({'sha': revision, 'date': '2025-11-03T12:10:00Z'}))
    rows = json.loads((root / 'douban_all_movies.json').read_text())
    ids = [str(row['movie_id']) for row in rows]
    cache = root / 'wikidata-batches'
    cache.mkdir(exist_ok=True)

    def fetch(job):
        index, batch = job
        path = cache / f'{index:03}.json'
        if path.exists():
            return json.loads(path.read_text())
        query = '''SELECT ?film ?douban ?imdb ?tmdb (MIN(?date) AS ?firstRelease) WHERE {
          VALUES ?douban { %s }
          ?film wdt:P4529 ?douban .
          OPTIONAL { ?film wdt:P345 ?imdb }
          OPTIONAL { ?film wdt:P4947 ?tmdb }
          OPTIONAL { ?film wdt:P577 ?date }
        } GROUP BY ?film ?douban ?imdb ?tmdb''' % ' '.join(json.dumps(v) for v in batch)
        url = 'https://query.wikidata.org/sparql?' + urllib.parse.urlencode({'query': query, 'format': 'json'})
        req = urllib.request.Request(url, headers={
            'User-Agent': 'CineMatch/1.0 (https://github.com/CodeRayZhang/cinematch-recommender)',
            'Accept': 'application/sparql-results+json',
        })
        for attempt in range(3):
            try:
                with urllib.request.urlopen(req, timeout=50) as response:
                    data = json.load(response)
                path.write_text(json.dumps(data, ensure_ascii=False))
                print(f'Wikidata batch {index + 1}/21: {len(data["results"]["bindings"])} matches', flush=True)
                return data
            except Exception:
                if attempt == 2:
                    raise
                time.sleep(2 + attempt * 3)

    all_rows = []
    jobs = list(enumerate([ids[i:i + 100] for i in range(0, len(ids), 100)]))
    with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
        for result in pool.map(fetch, jobs):
            all_rows.extend(result['results']['bindings'])
    (root / 'wikidata-links.json').write_text(json.dumps(all_rows, ensure_ascii=False, indent=2))
    print(f'Saved {len(all_rows)} cross-source movie identifiers.', flush=True)

    if not (root / 'tmdb-current-metadata.csv').exists():
        subprocess.run([sys.executable, str(Path(__file__).with_name('fetch_tmdb_snapshot.py'))], check=True)
    print('Public source files are ready. Run prepare_public_catalog.py next.', flush=True)

if __name__ == "__main__":
    main()
