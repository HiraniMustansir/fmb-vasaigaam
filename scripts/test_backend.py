#!/usr/bin/env python3
"""Run migrations and business/access/race tests on a disposable PostgreSQL 16 container."""
from pathlib import Path
import concurrent.futures, json, os, subprocess, uuid
ROOT=Path(__file__).resolve().parents[1]
container=f'fmb-tests-{os.getpid()}'
def shell(args,**kw): return subprocess.run(args,text=True,capture_output=True,**kw)
def sql(source,check=True):
 r=shell(['docker','exec','-i',container,'psql','-X','-U','postgres','-v','ON_ERROR_STOP=1','-At'],input=source)
 if check and r.returncode: raise RuntimeError(r.stderr)
 return r
owner='00000000-0000-0000-0000-000000000001'
other='00000000-0000-0000-0000-000000000002'
def command(kind,data,key=None):
 payload=json.dumps(data).replace("'","''")
 return sql(f"select set_config('request.jwt.claim.sub','{owner}',false); select public.execute_command('{kind}','{payload}','{key or uuid.uuid4()}');",False)
def must_command(kind,data,key=None):
 r=command(kind,data,key)
 if r.returncode: raise RuntimeError(r.stderr)
 return json.loads(r.stdout.strip().splitlines()[-1])
def race(action):
 with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool: return list(pool.map(lambda _:action(),range(2)))
try:
 r=shell(['docker','run','--rm','--name',container,'-e','POSTGRES_PASSWORD=local-test-only','-d','postgres:16-alpine'])
 if r.returncode: raise RuntimeError(r.stderr)
 import time
 for _ in range(120):
  logs=shell(['docker','logs',container])
  initialized='PostgreSQL init process complete' in logs.stdout+logs.stderr
  if initialized and shell(['docker','exec',container,'pg_isready','-U','postgres']).returncode==0: break
  time.sleep(.25)
 else: raise RuntimeError('PostgreSQL did not become ready')
 files=[ROOT/'supabase/tests/local_platform.sql',*sorted((ROOT/'supabase/migrations').glob('*.sql')),ROOT/'supabase/tests/business_rules.sql']
 result=sql('\n'.join(p.read_text() for p in files));print(result.stderr.strip())
 sql(f"insert into auth.users values ('{owner}','owner@example.test',now()),('{other}','other@example.test',now()); insert into public.bootstrap_authorizations values(true,'owner@example.test',encode(extensions.digest('race-token','sha256'),'hex'),now()+interval '1 hour',null);")
 results=race(lambda:command('bootstrap',{'name':'Owner','token':'race-token'}))
 assert sorted(r.returncode==0 for r in results)==[False,True],[(r.stdout,r.stderr) for r in results]
 print('PASS: Concurrent bootstrap produces exactly one workspace owner')
 w=sql('select id from public.workspaces').stdout.strip()
 item=must_command('item',{'workspace_id':w,'name':'Rice','unit':'kg'})['id']
 must_command('stock',{'workspace_id':w,'item_id':item,'kind':'opening','quantity':10,'unit_cost':60,'reason':'Opening stock'})
 results=race(lambda:command('stock',{'workspace_id':w,'item_id':item,'kind':'waste','quantity':-7,'unit_cost':60,'reason':'Concurrent issue'}))
 assert sorted(r.returncode==0 for r in results)==[False,True],[(r.stdout,r.stderr) for r in results]
 assert sql('select sum(quantity) from public.stock_movements').stdout.strip()=='3.000000'
 print('PASS: Two concurrent stock issues cannot overspend stock')
 key=str(uuid.uuid4());results=race(lambda:command('stock',{'workspace_id':w,'item_id':item,'kind':'opening','quantity':3,'unit_cost':60,'reason':'Retry safe'},key))
 assert all(r.returncode==0 for r in results),[r.stderr for r in results]
 assert sql('select sum(quantity) from public.stock_movements').stdout.strip()=='6.000000'
 print('PASS: Concurrent identical requests return success and post once')
 foreign=str(uuid.uuid4())
 sql(f"insert into public.workspaces(id,name,owner_id) values('{foreign}','Other committee','{other}'); insert into public.memberships values('{foreign}','{other}','Other','Secretary',true); insert into public.items(workspace_id,name,unit) values('{foreign}','Private rice','kg'); insert into storage.objects(bucket_id,name) values('committee-attachments','{foreign}/private.jpg');")
 read=sql(f"select set_config('request.jwt.claim.sub','{owner}',false);set role authenticated; select count(*) from public.items; select count(*) from storage.objects;")
 assert read.stdout.strip().splitlines()[-2:]==['1','0'],read.stdout
 print('PASS: API role cannot read another workspace or its attachments')
 own=sql(f"select set_config('request.jwt.claim.sub','{owner}',false);set role authenticated; insert into storage.objects(bucket_id,name) values('committee-attachments','{w}/receipt.jpg');")
 bad=sql(f"select set_config('request.jwt.claim.sub','{owner}',false);set role authenticated; insert into storage.objects(bucket_id,name) values('committee-attachments','{foreign}/injected.jpg');",False)
 assert bad.returncode!=0
 print('PASS: Storage upload policy permits only active workspace members')
 bad=sql(f"select set_config('request.jwt.claim.sub','{owner}',false);set role authenticated; insert into public.stock_movements(workspace_id,item_id,quantity,kind,reason,created_by) values('{w}','{item}',999,'opening','Bypass','{owner}');",False)
 assert bad.returncode!=0
 print('PASS: Direct client mutation of the stock ledger is denied')
 print('All backend checks passed on PostgreSQL 16. Supabase platform services are stubbed; hosted integration is not tested.')
finally:
 shell(['docker','rm','-f',container])
