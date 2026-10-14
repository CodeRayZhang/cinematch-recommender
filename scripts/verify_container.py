#!/usr/bin/env python3
"""Exercise the actual Compose image and persistence with disposable CI credentials."""
import argparse
import os
import subprocess
import http.cookiejar
import json
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

parser=argparse.ArgumentParser()
parser.add_argument('phase',choices=['before','after'])
parser.add_argument('--base-url',default='http://127.0.0.1:8080')
args=parser.parse_args()
base=args.base_url.rstrip('/')
opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def request(path,method='GET',data=None,form=False):
    headers={}
    if method!='GET': headers['X-XSRF-TOKEN']=request('/api/session')['csrfToken']
    body=None
    if data is not None:
        body=(urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        headers['Content-Type']='application/x-www-form-urlencoded' if form else 'application/json'
    with opener.open(urllib.request.Request(base+path,data=body,method=method,headers=headers),timeout=10) as response:
        return json.load(response)

for attempt in range(60):
    try:
        assert request('/api/health')['status']=='UP'
        break
    except (OSError,AssertionError,ValueError):
        if attempt==59: raise
        time.sleep(2)

state=Path('.cache/container-check.json')
password='Container-test-only-842!'
if args.phase=='before':
    assert request('/api/movies?size=1')['total']==18
    username='container_'+uuid.uuid4().hex[:12]
    request('/api/auth/register','POST',{'username':username,'displayName':'容器验收','password':password})
else:
    saved=json.loads(state.read_text())
    username=saved['username']
request('/api/auth/login','POST',{'username':username,'password':password},form=True)
if args.phase=='before':
    collection=request('/api/me/collections','POST',{'name':'容器持久化验收','description':'重启后必须保留'})
    request('/api/me/collections/'+collection['id']+'/movies','POST',{'movieIds':[1,2]})
    request('/api/me/reviews/1','PUT',{'stars':5,'body':'数据库重启后保留'})
    state.parent.mkdir(exist_ok=True)
    request('/api/auth/logout','POST')
    for index in range(5):
        peer='model_'+uuid.uuid4().hex[:12]
        request('/api/auth/register','POST',{'username':peer,'displayName':'Model fixture','password':password})
        request('/api/auth/login','POST',{'username':peer,'password':password},form=True)
        for movie in range(1,6):
            request('/api/me/reviews/'+str(movie),'PUT',{'stars':1+(index+movie)%5,'body':''})
        request('/api/auth/logout','POST')
    request('/api/auth/login','POST',{'username':os.environ['ADMIN_USERNAME'],'password':os.environ['ADMIN_PASSWORD']},form=True)
    model=request('/api/admin/recommender/models','POST',{'dataset':'local','rank':2,'iterations':2,'regularization':.1,'seed':42})['id']
    for attempt in range(30):
        version=next(m for m in request('/api/admin/recommender/models') if m['id']==model)
        if version['status'] in ('READY','FAILED'): break
        time.sleep(1)
    assert version['status']=='READY',version
    request('/api/admin/recommender/models/'+model+'/activate','POST')
    request('/api/auth/logout','POST')
    request('/api/auth/login','POST',{'username':username,'password':password},form=True)
    for movie,stars in [(2,4),(3,2)]:request('/api/me/reviews/'+str(movie),'PUT',{'stars':stars,'body':''})
    state.write_text(json.dumps({'username':username,'collection':collection['id'],'model':model}))
else:
    assert request('/api/me/reviews/1')['stars']==5
    assert request('/api/me/collections/'+saved['collection'])['total']==2
    assert request('/api/me/library/summary')['watched']==3
    model=saved['model']
status=request('/api/recommendations/status')
assert status['ready'] and status['modelVersion']==model
assert request('/api/recommendations')[0]['engine']=='als-hybrid'
subprocess.run(['docker','compose','-p','cinematch-ci','exec','-T','app','test','-s','/app/.data/recommender/runs/'+model+'/ratings.csv'],check=True)
for path in ('/','/movie/1','/picks','/collections','/collections/example'):
    with opener.open(base+path,timeout=10) as response:
        assert response.status==200 and b'<div id="app"' in response.read()
with opener.open(base+'/images/catalog/proxy-check.svg',timeout=10) as response:
    assert b'CineMatch catalog proxy fixture' in response.read()
print('Compose '+args.phase+': health, pages, mounted poster, authentication, trained model snapshot, active model and persistent records passed.')
