import unittest
from prepare_public_catalog import normalize, verified_links

class CatalogPreparationTest(unittest.TestCase):
    def setUp(self):
        self.row = {'movie_id':12345,'title':'测试电影','release_date':'2025-01-01',
                    'runtime':'100分钟','directors':'导演','summary':'电影简介','countries':'中国',
                    'genres':'剧情, 爱情','rating':8,'total_ratings':100}
        self.link = {'imdb':'tt12345','tmdb':'67890','year':1999}
        self.metadata = {'imdb_id':'tt12345','id':'67890','original_title':'Test Film',
                         'poster_path':'/validPoster.jpg','adult':'False'}

    def test_regional_rerelease_never_replaces_verified_original_year(self):
        entry, url = normalize(self.row, self.link, self.metadata)
        self.assertEqual(1999, entry['movie']['releaseYear'])
        self.assertEqual('剧情,爱情', entry['movie']['genres'])
        self.assertEqual('https://image.tmdb.org/t/p/w342/validPoster.jpg', url)

    def test_cross_source_id_mismatch_is_rejected(self):
        self.metadata['id'] = '99999'
        with self.assertRaises(ValueError): normalize(self.row, self.link, self.metadata)

    def test_ambiguous_wikidata_identity_is_not_guessed(self):
        def row(film):
            return {'film':{'value':film},'douban':{'value':'12345'},'imdb':{'value':'tt12345'},
                    'tmdb':{'value':'67890'},'firstRelease':{'value':'1999-01-01T00:00:00Z'}}
        self.assertEqual({}, verified_links([row('Q1'),row('Q2')]))

if __name__ == '__main__': unittest.main()
