"""Read-only audit analysis: never writes app code or database rows."""
from pathlib import Path
import json, hashlib, re, subprocess
out = Path(__file__).parent
def read(name): return json.loads((out/name).read_text(encoding='utf-8-sig'))
def write(name,value): (out/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf8')
def read_log(file):
 raw=file.read_bytes()
 return raw.decode('utf-16' if raw.startswith((b'\xff\xfe',b'\xfe\xff')) else 'utf8',errors='replace')
d=read('fixture-db.json'); checks={}
orders=d['orders']; movements=d['movements']
first=[o for o in orders if o['checkout_id']==6281]
checks['three_orders_one_checkout']=len(first)==3 and len({o['store_id'] for o in first})==3
checks['completed_orders_paid']=len([o for o in orders if o['order_status']=='COMPLETED'])==3 and all(o['payment_status']=='PAID' for o in first)
checks['snapshot_after_price_change']=any(i['order_id']==9809 and i['unit_price']==111000 and i['quantity']==2 and i['subtotal']==222000 for i in d['items'])
checks['cancel_restore_exactly_once']=len([m for m in movements if m['reference_order_id']==9812 and m['type']=='CANCEL_ORDER'])==1
checks['movement_arithmetic']=all(m['quantity_after']==m['quantity_before']+m['quantity_change'] for m in movements)
checks['ledger_continuity']=True; checks['ledger_matches_stock']=True
for stock in d['stock']:
 ledger=[m for m in movements if m['store_product_id']==stock['store_product_id']]
 checks['ledger_continuity'] &= all(a['quantity_after']==b['quantity_before'] for a,b in zip(ledger,ledger[1:]))
 checks['ledger_matches_stock'] &= ledger[-1]['quantity_after']==stock['quantity']
checks['history_connected']=True; checks['status_matches_history']=True; checks['duplicate_completion_absent']=True
for order in orders:
 history=[h for h in d['history'] if h['order_id']==order['order_id']]
 checks['history_connected'] &= history[0]['old_status'] is None and all(a['new_status']==b['old_status'] for a,b in zip(history,history[1:]))
 checks['status_matches_history'] &= history[-1]['new_status']==order['order_status']
 checks['duplicate_completion_absent'] &= len([h for h in history if h['new_status']=='COMPLETED'])<=1
checks['payment_amount_method']=all(p['amount']==next(o['total_amount'] for o in orders if o['order_id']==p['order_id']) and p['method']==next(o['payment_method'] for o in orders if o['order_id']==p['order_id']) for p in d['payments'])
checks['soft_disabled_records_retained']=d['stores'][0]['status']=='INACTIVE' and d['products'][0]['status']=='INACTIVE' and d['stock'][0]['status']=='INACTIVE'
checks['unicode_no_account_created']=len(d['unicode'])==0
race=[m for m in movements if m['store_product_id']==820 and 'simultaneous' in (m['note'] or '')]
checks['admin_staff_race_actor_and_serial_ledger']=len(race)==2 and {m['staff_id'] for m in race}=={1,270} and race[0]['quantity_before']==9 and race[0]['quantity_after']==race[1]['quantity_before'] and race[1]['quantity_after']==17
write('journey-db-checks.json',checks)
assert all(checks.values()), checks
before=read('source-manifest-before.json')
changed=[p for p,h in before.items() if not Path(p).is_file() or hashlib.sha256(Path(p).read_bytes()).hexdigest()!=h]
write('source-comparison.json',{'trackedFiles':len(before),'unchanged':not changed,'changed':changed,'commit':subprocess.check_output(['git','rev-parse','HEAD'],text=True).strip()})
assert not changed, changed
q=read('database-inspection.json')['queries']
summary={'tables':len(q['tables']['rows']),'columns':len(q['columns']['rows']),'foreignKeys':len(q['foreignKeys']['rows']),'checks':len(q['checks']['rows']),'uniqueIndexes':len({(r['table_name'],r['name']) for r in q['indexes']['rows'] if r['is_unique']}),'indexCount':len({(r['table_name'],r['name']) for r in q['indexes']['rows']}),'integrityViolations':{k:len(q[k]['rows']) for k in ['constraintViolations','monetaryIntegrity','itemStoreIntegrity','negativeStock','duplicateMovements','brokenTimeline','statusHistory','stockLedger','duplicatePrimaryImages','imageBinaryColumns']}}
write('database-summary.json',summary)
survey=read('browser-survey.json'); docs=[x for x in survey['results'] if x['device']=='desktop']
write('browser-summary.json',{'browser':survey['browser'],'routes':len(docs),'viewports':len(survey['results']),'screenshots':len(list((out/'screenshots').glob('*.png'))),'pageOverflow':[{k:r[k] for k in ['role','route','device','viewport']} for r in survey['results'] if r['viewport']['scrollWidth']>r['viewport']['width']+1],'brokenImages':[{k:r[k] for k in ['role','route','device','brokenImages']} for r in survey['results'] if r['brokenImages']],'unlabelledControls':[{k:r[k] for k in ['role','route','device','unlabelledControls']} for r in survey['results'] if r['unlabelledControls']],'journeyRecords':len(read('browser-journey.json')['records']),'additionalRecords':len(read('browser-additional.json')['rows'])})
config={}
for line in Path('.env').read_text(encoding='utf8').splitlines():
 if '=' in line and not line.lstrip().startswith('#'):
  k,v=line.split('=',1);config[k.strip()]=v.strip()
secrets={k:config.get(k,'') for k in ['DB_PASSWORD','JWT_SECRET','CLOUDINARY_API_KEY','CLOUDINARY_API_SECRET']}
matches=[]
for f in out.glob('*.log'):
 data=read_log(f)
 for key,value in secrets.items():
  if len(value)>=8 and value in data: matches.append({'file':f.name,'key':key,'occurrences':data.count(value)})
write('secret-log-scan.json',{'method':'exact matching configured sensitive values of at least 8 characters; values never retained','matches':matches,'scope':'audit logs only; not an exhaustive entropy or Git-history scan'})
log=read_log(out/'regression.log')
build={'status':'BUILD SUCCESS' if '[INFO] BUILD SUCCESS' in log else 'NOT VERIFIED','summary':re.findall(r'\[INFO\] Tests run: 927[^\r\n]*',log),'finishedAt':re.findall(r'\[INFO\] Finished at: ([^\r\n]*)',log),'duration':re.findall(r'\[INFO\] Total time: ([^\r\n]*)',log),'command':"mvn -o '-Dmaven.repo.local=C:/Users/Admin/.m2/repository' clean package",'artifact':{'path':'target/oneshop.jar','bytes':Path('target/oneshop.jar').stat().st_size,'sha256':hashlib.sha256(Path('target/oneshop.jar').read_bytes()).hexdigest()},'liveCloudinaryRerun':False}
write('build-summary.json',build)
print(json.dumps({'db':summary,'sourceUnchanged':not changed,'fixtureChecks':len(checks),'secretMatches':len(matches),'build':build['status']},ensure_ascii=False))
