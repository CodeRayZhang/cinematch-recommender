#!/usr/bin/env python3
"""Verify the committed movie bundle and optionally compare an isolated live import.

Uses only the Python standard library. --base-url performs read-only requests;
it is intended for a fresh test database because curated local edits can differ.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
BUNDLE = ROOT / 'data/catalog'
MOVIE_FIELDS = {'title', 'originalTitle', 'releaseYear', 'genres', 'runtime', 'rating',
                'ratingCount', 'director', 'country', 'synopsis', 'posterUrl', 'backdropUrl', 'featured'}


def require(condition, message):
    if not condition:
        raise ValueError(message)


def check_file(root, asset):
    path = root / asset['path']
    require(not path.is_symlink() and path.resolve().is_relative_to(root.resolve()),
            f'Asset outside expected directory: {asset["path"]}')
    raw = path.read_bytes()
    require(len(raw) == asset['bytes'] and hashlib.sha256(raw).hexdigest() == asset['sha256'],
            f'Asset size/hash mismatch: {asset["path"]}')
    require(asset['sourceUrl'].startswith('https://image.tmdb.org/t/p/'),
            f'Missing image attribution: {asset["path"]}')


def check_bundle():
    manifest = json.loads((BUNDLE / 'manifest.json').read_text(encoding='utf-8'))
    require(manifest['schemaVersion'] == 1, 'Unsupported bundle schema')
    raw = (BUNDLE / 'movies.json').read_bytes()
    require(len(raw) == manifest['catalogBytes'] and hashlib.sha256(raw).hexdigest() == manifest['catalogSha256'],
            'Catalog size/hash mismatch')
    entries = json.loads(raw)
    require(len(entries) == manifest['movieCount'] and 0 < len(entries) <= 10000, 'Wrong movie count')
    require(len({row['sourceId'] for row in entries}) == len(entries), 'Duplicate movie identities')
    references = set()
    for entry in entries:
        require(set(entry) == {'sourceId', 'sourceUrl', 'snapshot', 'movie'}, 'Unexpected exported fields')
        key = entry['sourceId']
        require(re.fullmatch(r'[1-9][0-9]{0,19}', key) is not None, 'Invalid movie identity')
        require(entry['sourceUrl'] == f'https://movie.douban.com/subject/{key}/', 'Source URL mismatch')
        require(re.fullmatch(r'\d{4}(?:-\d{2}-\d{2})?', entry['snapshot']) is not None, 'Invalid snapshot')
        movie = entry['movie']
        require(set(movie) == MOVIE_FIELDS, f'Unexpected metadata fields: {key}')
        require(0 <= movie['rating'] <= 10 and movie['ratingCount'] >= 0, f'Invalid rating: {key}')
        require(1888 <= movie['releaseYear'] <= 2100 and 1 <= movie['runtime'] <= 600, f'Invalid year/runtime: {key}')
        require(isinstance(movie['featured'], bool), f'Invalid featured flag: {key}')
        for name in ('title', 'originalTitle', 'genres', 'director', 'country', 'synopsis'):
            require(isinstance(movie[name], str) and movie[name].strip(), f'Empty {name}: {key}')
        references.update((movie['posterUrl'], movie['backdropUrl']))
    assets, seeds = manifest['assets'], manifest['seedAssets']
    require(len(assets) == manifest['posterCount'], 'Wrong poster count')
    require(sum(a['bytes'] for a in assets) == manifest['posterBytes'], 'Wrong poster byte count')
    expected = {a['publicPath'] for a in assets + seeds}
    require(len(expected) == len(assets) + len(seeds), 'Duplicate image references')
    require(references == expected, 'Missing or unused image references')
    require({p.name for p in (BUNDLE / 'posters').iterdir()} == {Path(a['path']).name for a in assets},
            'Missing or unlisted poster files')
    for asset in assets:
        require(re.fullmatch(r'posters/douban-[1-9][0-9]*\.(jpg|png|webp)', asset['path']) is not None,
                'Invalid bundled poster path')
        require(asset['publicPath'] == '/images/catalog/' + Path(asset['path']).name, 'Poster URL mismatch')
        check_file(BUNDLE, asset)
    for asset in seeds:
        require(re.fullmatch(r'frontend/public/images/[A-Za-z0-9_-]+\.(jpg|png|webp)', asset['path']) is not None,
                'Invalid seed image path')
        require(asset['publicPath'] == '/' + asset['path'].removeprefix('frontend/public/'), 'Seed URL mismatch')
        check_file(ROOT, asset)
    print(f'Bundle verified: {len(entries)} movies, {len(assets)} posters, {len(seeds)} existing image references.')
    return entries, manifest


def check_live(base, entries, manifest, wait_seconds):
    base = base.rstrip('/')

    def fetch(path):
        with urllib.request.urlopen(base + path, timeout=10) as response:
            return response.read()

    deadline = time.monotonic() + wait_seconds
    while True:
        try:
            first = json.loads(fetch('/api/movies?page=1&size=48&sort=recent'))
            require(first['total'] == len(entries), 'Live catalog count does not match bundle')
            break
        except (OSError, ValueError):
            if time.monotonic() >= deadline:
                raise
            time.sleep(1)
    live = list(first['items'])
    for page in range(2, (first['total'] + 47) // 48 + 1):
        live.extend(json.loads(fetch(f'/api/movies?page={page}&size=48&sort=recent'))['items'])
    by_source = {row['sourceId']:row for row in live}
    require(len(live) == len(by_source) == len(entries), 'Live catalog has duplicate/missing identities')
    for entry in entries:
        actual = by_source[entry['sourceId']]
        require(actual['dataSource'] == 'douban' and actual['sourceUrl'] == entry['sourceUrl']
                and actual['sourceSnapshot'] == entry['snapshot'], 'Live provenance mismatch')
        require({k:actual[k] for k in MOVIE_FIELDS} == entry['movie'], f'Live metadata differs: {entry["sourceId"]}')
    for asset in manifest['assets'] + manifest['seedAssets']:
        require(hashlib.sha256(fetch(asset['publicPath'])).hexdigest() == asset['sha256'],
                f'Live image differs: {asset["publicPath"]}')
    print(f'Live import verified: all {len(entries)} metadata records and {len(manifest["assets"]) + len(manifest["seedAssets"])} image responses match.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--base-url', help='Also compare a fresh isolated application with the bundle')
    parser.add_argument('--wait-seconds', type=int, default=0, help='Wait for the expected live catalog count (maximum 120)')
    args = parser.parse_args()
    if not 0 <= args.wait_seconds <= 120:
        parser.error('--wait-seconds must be in [0, 120]')
    entries, manifest = check_bundle()
    if args.base_url:
        check_live(args.base_url, entries, manifest, args.wait_seconds)


if __name__ == '__main__':
    main()
