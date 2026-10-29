#!/usr/bin/env python3
"""Explicit local role provisioning followed by ordinary Auth API login."""
import argparse,json,os,secrets
from pathlib import Path
from urllib.parse import urlsplit
import requests
import import_postgres as importer

def provision(a):
 parsed=urlsplit(a.api_base)
 if parsed.scheme!='http' or parsed.hostname not in ('127.0.0.1','localhost') or not parsed.port or parsed.path:raise ValueError('Explicit loopback API required')
 session=requests.Session();session.trust_env=False
 db=importer.target_connection(a,read_only=not a.provision)
 try:
  if a.provision:
   if a.credentials.exists():raise ValueError('Credentials file already exists')
   if db.execute('SELECT count(*) FROM users WHERE username=%s',(a.username,)).fetchone()[0]:raise ValueError('Username already exists; refusing to grant another account')
   credentials=dict(username=a.username,password=secrets.token_urlsafe(36),target=a.confirm_target,api_base=a.api_base)
   r=session.post(a.api_base+'/api/auth/register',json={k:credentials[k] for k in ('username','password')},timeout=15)
   if r.status_code!=201:raise ValueError('Registration failed: HTTP '+str(r.status_code))
   # Private operator action, never an HTTP role-grant endpoint. Roles are read on every JWT use.
   uid=db.execute('SELECT id FROM users WHERE username=%s',(a.username,)).fetchone()[0]
   db.execute("INSERT INTO roles(name) VALUES ('ADMIN') ON CONFLICT (name) DO NOTHING")
   db.execute("INSERT INTO user_roles(user_id,role_id) SELECT %s,id FROM roles WHERE name='ADMIN' ON CONFLICT DO NOTHING",(uid,));db.commit()
  else:
   credentials=json.loads(a.credentials.read_text())
   if credentials['target']!=a.confirm_target or credentials['api_base']!=a.api_base:raise ValueError('Credential target mismatch')
   db.rollback()
  r=session.post(a.api_base+'/api/auth/login',json=dict(identifier=credentials['username'],password=credentials['password']),timeout=15)
  if r.status_code!=200:raise ValueError('Login failed: HTTP '+str(r.status_code))
  token=r.headers['Authorization'];assert token.startswith('Bearer ')
  credentials['token']=token[7:]
  fd=os.open(a.credentials,os.O_WRONLY|os.O_CREAT|os.O_TRUNC,0o600)
  with os.fdopen(fd,'w') as f:json.dump(credentials,f)
  os.chmod(a.credentials,0o600)
  print('Development ADMIN login verified; private credentials/token stored, never printed')
 finally:db.close()

def main():
 p=argparse.ArgumentParser(description=__doc__)
 p.add_argument('--api-base',required=True);p.add_argument('--credentials',type=Path,required=True)
 p.add_argument('--provision',action='store_true');p.add_argument('--username',default='catalog-admin')
 p.add_argument('--target-kind',choices=['local-development','disposable'],required=True);p.add_argument('--dsn-env',default='CATALOG_IMPORT_DSN')
 p.add_argument('--expect-database',required=True);p.add_argument('--expect-host',required=True);p.add_argument('--expect-port',type=int,required=True);p.add_argument('--confirm-target',required=True)
 a=p.parse_args()
 try:provision(a)
 except Exception as e:p.exit(2,(str(e) if isinstance(e,ValueError) else type(e).__name__)+'\n')
if __name__=='__main__':main()
