#!/usr/bin/env python3
"""Offline source-image inventory; no download, S3 or PostgreSQL write capability."""
import argparse
import collections
import ipaddress
import json
import sqlite3
import uuid
from pathlib import Path
from urllib.parse import urlsplit
import import_postgres as importer
from portable_seed import load_seed

ALLOWED_HOSTS = {'cellphones': {'cdn2.cellphones.com.vn'},
                 'tgdd': {'cdn.tgdd.vn', 'cdnv2.tgdd.vn'}}

def url_issue(url, source):
    if not isinstance(url, str) or not url or url != url.strip(): return 'INVALID_URL'
    try:
        parsed = urlsplit(url)
        if parsed.scheme != 'https' or parsed.username or parsed.password or parsed.fragment or parsed.port not in (None,443): return 'UNSAFE_URL'
        host = parsed.hostname
        if not host or host not in ALLOWED_HOSTS.get(source,set()): return 'HOST_NOT_ALLOWLISTED'
        try:
            ipaddress.ip_address(host)
            return 'IP_LITERAL_NOT_ALLOWED'
        except ValueError: pass
    except ValueError: return 'INVALID_URL'
    return None

def image_record(product, row):
    identity = product['identity']
    source,url = identity.split('|',1)
    if row is None: raise ValueError('Missing exact source identity: '+identity)
    sid,site,source_url,payload = row
    raw = json.loads(payload)
    if (site,source_url)!=(source,url) or (raw.get('id'),raw.get('source'),raw.get('source_url'))!=(sid,source,url):
        raise ValueError('Source identity mismatch: '+identity)
    if str(product['product_id']) != importer.normalize.proposed_uuid(source,url): raise ValueError('Product UUID mismatch')
    images = raw.get('images')
    if images is None: images=[]
    if not isinstance(images,list): raise ValueError('Malformed source images: '+identity)
    records=[];seen=set()
    for index,image in enumerate(images):
        issue=url_issue(image,source)
        if isinstance(image,str) and image in seen: issue='DUPLICATE_SOURCE_URL'
        if isinstance(image,str): seen.add(image)
        records.append(dict(source_index=index,source_url=image,issue=issue,
                            availability='NOT_CHECKED_PERMISSION_BLOCKED',
                            image_bytes_validated=False))
    return dict(product_id=str(product['product_id']),source_identity=identity,source_product_id=sid,
                images=records,possible_primary_source_index=next((r['source_index'] for r in records if not r['issue']),None),
                permission='NOT_ESTABLISHED',status='BLOCKED_SOURCE_MEDIA_PERMISSION',
                primary_selection='PROPOSAL_ONLY_REQUIRES_VALIDATED_BYTES')

def inventory(database,products):
    connection=sqlite3.connect(database.resolve().as_uri()+'?mode=ro',uri=True)
    try:
        connection.execute('PRAGMA query_only=ON')
        result=[]
        for product in products:
            source,url=product['identity'].split('|',1)
            rows=connection.execute('SELECT id,site,url,payload FROM products WHERE site=? AND url=?',(source,url)).fetchall()
            if len(rows)!=1: raise ValueError('Source identity must resolve exactly once')
            result.append(image_record(product,rows[0]))
        return result
    finally: connection.close()

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('--seed',type=Path,required=True);p.add_argument('--source-db',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    a=p.parse_args();_,products=load_seed(a.seed)
    records=inventory(a.source_db,products)
    issues=collections.Counter(image['issue'] for r in records for image in r['images'] if image['issue'])
    summary=dict(products=len(records),source_image_entries=sum(len(r['images']) for r in records),
                 products_without_source_urls=sum(not r['images'] for r in records),issues=dict(issues),
                 downloads_attempted=0,uploads_attempted=0,permission_blocked_products=len(records))
    a.output.parent.mkdir(parents=True,exist_ok=True)
    with a.output.open('x',encoding='utf-8') as f:json.dump(dict(summary=summary,products=records),f,ensure_ascii=False,indent=2)
    print(json.dumps(summary))
if __name__=='__main__':main()
