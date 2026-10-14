#!/usr/bin/env python3
"""Build an attributed local catalog from cached public snapshots and verified IDs.

Run fetch_movie_sources.py first. Images and generated data stay outside Git.
"""
import argparse
from collections import Counter, defaultdict
from concurrent.futures import ThreadPoolExecutor, as_completed
import csv
import hashlib
import html
import json
from pathlib import Path
import re
import time
import urllib.request

ROOT = Path(__file__).resolve().parents[1]

def clean(value):
    return re.sub(r'\s+', ' ', html.unescape(str(value or ''))).strip()

def verified_links(rows):
    grouped = defaultdict(list)
    for row in rows:
        grouped[row['douban']['value']].append(row)
    result = {}
    for key, matches in grouped.items():
        entities = {r.get('film', {}).get('value') for r in matches}
        imdbs = {r['imdb']['value'] for r in matches if 'imdb' in r}
        tmdbs = {r['tmdb']['value'] for r in matches if 'tmdb' in r}
        dates = sorted(r['firstRelease']['value'] for r in matches if 'firstRelease' in r)
        if len(entities) != 1 or len(imdbs) != 1 or len(tmdbs) != 1 or not dates:
            continue
        result[key] = {'imdb': next(iter(imdbs)), 'tmdb': next(iter(tmdbs)),
                       'year': int(dates[0][:4]), 'wikidata': next(iter(entities))}
    return result

def normalize(row, link, metadata, seed=None):
    source_id = str(row['movie_id'])
    # A snapshot release_date can be a regional re-release. Never use it as the original year.
    year = int(seed['release_year']) if seed else link['year']
    runtime_match = re.search(r'\d+', str(row.get('runtime') or ''))
    runtime = int(runtime_match.group()) if runtime_match else 0
    title, director, summary, country = map(clean, (row.get('title'), row.get('directors'), row.get('summary'), row.get('countries')))
    genres = ','.join(dict.fromkeys(clean(g) for g in re.split(r'[,，/]', row.get('genres') or '') if clean(g)))
    if not all([title, director, summary, country, genres]) or not 60 <= runtime <= 600 or not 1888 <= year <= 2026:
        raise ValueError('missing or invalid film metadata')
    if not seed and (metadata.get('imdb_id') != link['imdb'] or metadata.get('id') != link['tmdb']):
        raise ValueError('cross-source ID mismatch')
    if not seed and metadata.get('adult', '').lower() == 'true':
        raise ValueError('excluded adult catalog entry')
    original = seed['original_title'] if seed else clean(metadata.get('original_title'))
    if not original:
        raise ValueError('original title unavailable')
    poster_path = metadata.get('poster_path', '')
    if not seed and not re.fullmatch(r'/[a-zA-Z0-9]+\.(jpg|png|webp)', poster_path):
        raise ValueError('verified poster unavailable')
    suffix = Path(poster_path).suffix if not seed else ''
    poster = seed['poster_url'] if seed else f'/images/catalog/douban-{source_id}{suffix}'
    backdrop = seed['backdrop_url'] if seed else poster
    payload = {'title': title, 'originalTitle': original, 'releaseYear': year,
               'genres': genres, 'runtime': runtime, 'rating': float(row['rating']),
               'ratingCount': int(row['total_ratings']), 'director': director, 'country': country,
               'synopsis': summary, 'posterUrl': poster, 'backdropUrl': backdrop, 'featured': False}
    limits = {'title':160, 'originalTitle':200, 'genres':160, 'director':160, 'country':100, 'synopsis':6000}
    if any(len(payload[k]) > limit for k, limit in limits.items()) or not 0 <= payload['rating'] <= 10 or payload['ratingCount'] < 0:
        raise ValueError('invalid field length or rating')
    return {'sourceId': source_id, 'sourceUrl': f'https://movie.douban.com/subject/{source_id}/',
            'snapshot': '2025', 'movie': payload}, None if seed else 'https://image.tmdb.org/t/p/w342' + poster_path

def download_poster(job):
    row, url, directory = job
    if url is None:
        return True, row, None
    target = directory / Path(row['movie']['posterUrl']).name
    provenance = target.with_suffix(target.suffix + '.source.json')
    if target.exists() and provenance.exists():
        recorded = json.loads(provenance.read_text())
        if recorded.get('url') == url and recorded.get('sha256') == hashlib.sha256(target.read_bytes()).hexdigest():
            return True, row, None
    try:
        request = urllib.request.Request(url, headers={'User-Agent': 'CineMatch/1.0 (local movie catalog)'})
        with urllib.request.urlopen(request, timeout=25) as response:
            data = response.read(3_000_001)
        valid = data.startswith(b'\xff\xd8\xff') or data.startswith(b'\x89PNG\r\n\x1a\n') or (data[:4] == b'RIFF' and data[8:12] == b'WEBP')
        if not valid or not 1000 < len(data) <= 3_000_000:
            raise ValueError('invalid poster response')
        temporary = target.with_suffix(target.suffix + '.part')
        temporary.write_bytes(data)
        temporary.replace(target)
        provenance.write_text(json.dumps({'url': url, 'sha256': hashlib.sha256(data).hexdigest()}))
        return True, row, None
    except Exception as error:
        return False, row, type(error).__name__ + ': ' + str(error)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--limit', type=int, default=1000)
    parser.add_argument('--cache', type=Path, default=ROOT / '.cache/catalog')
    parser.add_argument('--posters', type=Path, default=ROOT / 'backend/.data/catalog-posters')
    args = parser.parse_args()
    if not 18 <= args.limit <= 5000:
        parser.error('--limit must be between 18 and 5000')
    rows = json.loads((args.cache / 'douban_all_movies.json').read_text())
    links = verified_links(json.loads((args.cache / 'wikidata-links.json').read_text()))
    with (args.cache / 'tmdb-current-metadata.csv').open(encoding='utf-8', newline='') as f:
        metadata = {r['imdb_id']: r for r in csv.DictReader(f) if re.fullmatch(r'tt\d+', r.get('imdb_id') or '')}
    seeds = {r['title']: r for r in json.loads((ROOT / 'docs/demo-catalog.json').read_text())}
    selected, excluded, year_corrections, identities = [], Counter(), [], set()
    for row in sorted(rows, key=lambda r: (r['title'] not in seeds, -int(r['total_ratings']))):
        key = str(row['movie_id']); seed = seeds.get(row['title']); link = links.get(key)
        if not link and not seed:
            excluded['unverified_identifier_or_year'] += 1; continue
        match = metadata.get(link['imdb'], {}) if link else {}
        try:
            entry, poster = normalize(row, link, match, seed)
        except ValueError as error:
            excluded[str(error)] += 1; continue
        identity = link['imdb'] if link else key
        if identity in identities:
            excluded['duplicate_cross_source_identity'] += 1; continue
        identities.add(identity)
        original_year = str(row.get('release_date') or '')[:4]
        if original_year != str(entry['movie']['releaseYear']):
            year_corrections.append({'sourceId':key,'title':entry['movie']['title'],'snapshotYear':original_year,'year':entry['movie']['releaseYear']})
        selected.append((entry, poster, args.posters))
    print(f'Qualified movies: {len(selected)}; preparing up to {args.limit}.', flush=True)
    args.posters.mkdir(parents=True, exist_ok=True)
    qualified = len(selected)
    selected = selected[:args.limit]
    accepted, failures, assets = [], [], []
    urls = {row['sourceId']: url for row, url, _ in selected}
    with ThreadPoolExecutor(max_workers=6) as pool:
        jobs = [pool.submit(download_poster, job) for job in selected]
        for index, future in enumerate(as_completed(jobs), 1):
            ok, row, error = future.result()
            if ok:
                accepted.append(row)
                if urls[row['sourceId']]:
                    file = args.posters / Path(row['movie']['posterUrl']).name
                    assets.append({'sourceId':row['sourceId'], 'url':urls[row['sourceId']], 'file':file.name,
                                   'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
            else:
                failures.append({'sourceId':row['sourceId'],'error':error})
            if index % 100 == 0 or index == len(selected):
                print(f'Posters checked: {index}/{len(selected)}, ready: {len(accepted)}, failed: {len(failures)}', flush=True)
    accepted.sort(key=lambda row: int(row['sourceId']))
    (args.cache / 'ready-catalog.json').write_text(json.dumps(accepted, ensure_ascii=False, indent=2))
    report = {'sourceRecords':len(rows),'wikidataVerified':len(links),'qualified':qualified,
              'importReady':len(accepted),'excluded':dict(excluded),'posterFailures':failures,
              'yearCorrections':year_corrections,'posterAssets':assets,
              'preparedAt':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),
              'snapshotRevision':json.loads((args.cache / 'upstream-revision.json').read_text())}
    (args.cache / 'preparation-report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
    print(f'Ready catalog: {args.cache / "ready-catalog.json"} ({len(accepted)} movies). No database changes.', flush=True)

if __name__ == '__main__':
    main()
