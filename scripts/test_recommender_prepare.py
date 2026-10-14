import argparse
import contextlib
import io
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import prepare_recommender as prepare


class PreparationTest(unittest.TestCase):
    def test_normalization_preserves_identity_guards(self):
        self.assertEqual(prepare.normalized('Matrix, The (1999)'), prepare.normalized('The Matrix (1999)'))
        self.assertEqual(prepare.year('Toy Story (1995)'), 1995)
        self.assertNotEqual(prepare.normalized('Toy Story (1995)'), prepare.normalized('Toy Story 2 (1999)'))

    def run_fixture(self, root, linked_title='Toy Story (1995)', catalog_year=1995, duplicate=False):
        source = root / 'source'; links = root / 'links'
        source.mkdir(); links.mkdir()
        (source / 'movies.dat').write_text('1::Toy Story (1995)::Animation\n')
        (source / 'ratings.dat').write_text('1::1::5::100\n')
        (links / 'movies.csv').write_text(f'movieId,title,genres\n1,{linked_title},Animation\n')
        (links / 'links.csv').write_text('movieId,imdbId,tmdbId\n1,0114709,862\n')
        wiki = root / 'wiki.json'; metadata = root / 'catalog.json'
        wiki.write_text(json.dumps([{'douban': {'value': '42'}, 'imdb': {'value': 'tt0114709'}}]))
        metadata.write_text('[]')
        movie = {'id': 700, 'title': '玩具总动员', 'originalTitle': 'Toy Story', 'releaseYear': catalog_year,
                 'sourceId': '42', 'sourceUrl': 'https://movie.douban.com/subject/42/'}
        catalog = [movie, {**movie, 'id': 701}] if duplicate else [movie]
        args = argparse.Namespace(movielens=str(source), links=str(links), wikidata=str(wiki), catalog_metadata=str(metadata),
                                  base_url='http://127.0.0.1:8080', output=str(root / 'output'), name='fixture')
        with patch.object(prepare, 'fetch_catalog', return_value=catalog), contextlib.redirect_stdout(io.StringIO()):
            prepare.prepare(args)
        return json.loads((root / 'output/mappings.json').read_text())

    def test_joins_by_imdb_not_local_numeric_id(self):
        with tempfile.TemporaryDirectory() as directory:
            maps = self.run_fixture(Path(directory))
            self.assertEqual(1, len(maps))
            self.assertEqual((700, 1, 'tt0114709'), (maps[0]['movieId'], maps[0]['itemId'], maps[0]['imdbId']))

    def test_conflicting_title_year_and_duplicates_are_excluded(self):
        for options in [{'linked_title': 'Another Movie (1995)'}, {'catalog_year': 1996}, {'duplicate': True}]:
            with self.subTest(options=options), tempfile.TemporaryDirectory() as directory:
                self.assertEqual([], self.run_fixture(Path(directory), **options))


if __name__ == '__main__':
    unittest.main()
