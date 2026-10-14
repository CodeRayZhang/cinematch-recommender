#!/usr/bin/env python3
"""Download a bounded prefix of public TMDB metadata; never an API key."""
import codecs
import csv
import hashlib
import io
import json
from pathlib import Path
import struct
import urllib.request
import zlib


def main():
    root=Path(__file__).resolve().parents[1] / '.cache/catalog'
    root.mkdir(parents=True, exist_ok=True)
    listing_url='https://www.kaggle.com/api/v1/datasets/list/asaniczka/tmdb-movies-dataset-2023-930k-movies'
    with urllib.request.urlopen(listing_url, timeout=30) as response:
        listing=json.load(response)
    metadata=next(row for row in listing['datasetFiles'] if row['name']=='TMDB_movie_dataset_v11.csv')
    source_updated=metadata['creationDate'][:10]
    url='https://www.kaggle.com/api/v1/datasets/download/asaniczka/tmdb-movies-dataset-2023-930k-movies/TMDB_movie_dataset_v11.csv'
    # This large public archive is ordered by audience votes. Read a bounded prefix,
    # retain only complete CSV records, then verify IDs and images before importing.
    request=urllib.request.Request(url,headers={'User-Agent':'CineMatch/1.0 (local educational catalog)'})
    with urllib.request.urlopen(request,timeout=60) as response:
        print('TMDB current snapshot:',response.status,response.headers.get('Content-Type'),flush=True)
        header=response.read(30)
        output=bytearray(); downloaded=0
        if header.startswith(b'PK\x03\x04'):
            fields=struct.unpack('<4s5H3I2H',header)
            if fields[3]!=8 or fields[2]&1: raise ValueError('Unsupported archive structure')
            name=response.read(fields[-2]).decode(); response.read(fields[-1])
            if not name.endswith('.csv'): raise ValueError('Expected CSV archive entry')
            decoder=zlib.decompressobj(-15)
        else:
            output.extend(header); decoder=None
        while downloaded<12*1024*1024 and len(output)<48*1024*1024:
            block=response.read(256*1024)
            if not block: break
            downloaded+=len(block); output.extend(decoder.decompress(block) if decoder else block)
            if downloaded % (2*1024*1024)==0: print('Downloaded MiB:',downloaded//(1024*1024),flush=True)
    text=codecs.getincrementaldecoder('utf-8')().decode(output, final=False)
    # Ignore a possibly truncated final physical line; strict CSV rejects an
    # unfinished quoted record rather than interpreting it as a complete movie.
    text=text[:text.rfind('\n')+1]
    reader=csv.DictReader(io.StringIO(text),strict=True)
    rows=[]
    try:
        for row in reader:
            if row.get('id','').isdigit() and None not in row and all(value is not None for value in row.values()):
                rows.append(row)
    except csv.Error:
        pass
    if len(rows)<1000 or 'poster_path' not in (reader.fieldnames or []):
        raise ValueError('Snapshot prefix is not a usable movie table')
    target=root/'tmdb-current-metadata.csv'
    with target.open('w',encoding='utf-8',newline='') as out:
        writer=csv.DictWriter(out,fieldnames=reader.fieldnames); writer.writeheader(); writer.writerows(rows)
    report={'url':url,'sourceUpdated':source_updated,'boundedPrefix':True,'bytesRead':downloaded,
            'completeRecords':len(rows),'sha256':hashlib.sha256(target.read_bytes()).hexdigest(),
            'firstTitles':[row.get('title') for row in rows[:5]]}
    (root/'tmdb-current-manifest.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
    print(json.dumps(report,ensure_ascii=False),flush=True)

if __name__ == "__main__":
    main()
