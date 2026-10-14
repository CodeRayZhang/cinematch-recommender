# CineMatch movie catalog snapshot

This directory contains the 923 public movie records used by the local CineMatch app, exported on 2026-10-14, and 905 referenced poster files (about 45 MiB including metadata). The other 19 image references reuse assets already present in `frontend/public/images/`.

- `movies.json`: portable transaction-import data, with source identities, historical ratings and movie metadata. Database row IDs, accounts, personal reviews, lists, passwords and model weights are excluded.
- `manifest.json`: catalog hash, image hashes and byte lengths, image source URLs, upstream revision and snapshot dates.
- `posters/`: only poster files referenced by this catalog; no unused local images or raw download caches.

## Attribution and scope

- Chinese metadata and historical reference ratings: [dengfuping/douban-movies-spider](https://github.com/dengfuping/douban-movies-spider/tree/671045f9470ed41394c635589e3b65388ff11e46/data), revision `671045f9470ed41394c635589e3b65388ff11e46`, a 2025 snapshot. Each movie retains its Douban source URL. These are not live ratings or CineMatch user ratings.
- Cross-source identities and first-release checks: [Wikidata](https://www.wikidata.org/), using Douban / IMDb / TMDB identifiers and release dates.
- Original titles and poster lookup: [Asaniczka / TMDB Movies Dataset](https://www.kaggle.com/datasets/asaniczka/tmdb-movies-dataset-2023-930k-movies), prepared from the bounded metadata snapshot described in [data sources](../../docs/data-sources.md).
- Images: TMDB image CDN; individual URLs are retained in `manifest.json`. Movie artwork belongs to its respective rights holders. Existing seed artwork is documented in [artwork sources](../../docs/artwork.md).

This snapshot supports this personal project's educational and research use. The code's MIT license does not relicense third-party metadata, synopses or images, and this notice grants no additional rights. The original source's educational/research scope remains applicable. The bundle is kept separate from application binaries and training datasets.

## Use

From the repository root, run `python3 scripts/check_catalog_bundle.py` to check every catalog/image hash, then `bash scripts/dev.sh --catalog` to start with the bundle. The [deployment guide](../../docs/deployment.md) also covers JAR and Docker Compose import. Re-import preserves existing movie identities and user interactions, skips manually edited movies, and does not delete other movies. Fresh databases have 923 movies; existing custom catalogs can have a different total.
