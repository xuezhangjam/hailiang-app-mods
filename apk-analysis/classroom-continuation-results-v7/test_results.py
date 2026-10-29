"""Actual feature Java with explicit API callbacks and native view fixtures."""
from pathlib import Path
import hashlib,json,subprocess
B=Path(__file__).resolve().parent;ROOT=B.parent.parent;T=ROOT/'tools/apk-patch'
JAVA=T/'java21/zulu21.52.203-ca-jre21.0.12.1-win_x64/bin/java.exe'
fixtures=list((B/'tests/platform').rglob('*.java'));src=list((B/'src').rglob('*.java'))
def run(args,expect=True):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,encoding='utf-8',errors='replace',timeout=150)
 if expect:assert p.returncode==0,p.stdout+p.stderr
 return p
def compile(target,sources):
 target.mkdir(exist_ok=True)
 run([JAVA,'-jar',T/'ecj.jar','-proc:none','-source','8','-target','8','-warn:none','-encoding','UTF-8','-d',target,*sources,*fixtures])
classes=B/'test-classes';compile(classes,src)
results={}
for name in ['ContinuationResultsTest','OverlayLayoutTest','ChoiceCloudTest','ChoiceBaselineTest','HandwriteCloudTest','NoResultBaselineTest','ToolsTest','DiscussionPipTest','PipViewportTest']:
 p=run([JAVA,'-cp',classes,name]);results[name]=p.stdout.strip();(B/(name+'-results.txt')).write_text(p.stdout,'utf-8');print(p.stdout,end='',flush=True)
old=[]
for family in ['HandwriteCloud','HandwriteContinuation']:
 p=B/'tests/previous'/(family+'.java');p.write_text((B/'tests/previous'/(family+'.java.txt')).read_text('utf-8'),'utf-8');old.append(p)
baseline=B/'baseline-classes';compile(baseline,[p for p in src if p.stem not in {'HandwriteCloud','HandwriteContinuation'}]+old)
p=run([JAVA,'-cp',baseline,'ContinuationResultsTest','regression'],False)
assert p.returncode!=0 and 'accepted upload must refresh the existing result fragment' in p.stderr,p.stdout+p.stderr
(B/'previous-negative.txt').write_text(p.stderr,'utf-8');print('PASS actual v6 continuation-helper negative control',flush=True)
results['previous_negative_control']=True
results['source_sha256']={p.relative_to(B).as_posix():hashlib.sha256(p.read_bytes()).hexdigest() for p in src}
(B/'tests.json').write_text(json.dumps(results,ensure_ascii=False,indent=2),'utf-8')
