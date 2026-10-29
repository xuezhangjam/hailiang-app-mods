"""Refresh accepted continuation results on the v6 split-screen baseline."""
from pathlib import Path
import hashlib,json,os,re,shutil,subprocess,zipfile
B=Path(__file__).resolve().parent;ROOT=B.parent.parent;T=ROOT/'tools/apk-patch'
JAVA=T/'java21/zulu21.52.203-ca-jre21.0.12.1-win_x64/bin/java.exe';ANDROID=ROOT/'tools/android-sdk/android-34-ext12/android.jar'
SOURCE=ROOT/'学生端课堂-3.2.1.0-分屏底栏恢复-v6.apk'
DEST=ROOT/'学生端课堂-3.2.1.0-续答结果显示修复-v7.apk'
EXPECTED='1c120811d90fa426419e75c9076b44e29a94c908c97c594e1a51b970d4711f0c'
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest()==EXPECTED
def run(args):
 p=subprocess.run(list(map(str,args)),capture_output=True,text=True,encoding='utf-8',errors='replace')
 if p.stdout:print(p.stdout,flush=True)
 if p.stderr:print(p.stderr,flush=True)
 p.check_returncode()
def write(p,s):
 p.parent.mkdir(parents=True,exist_ok=True);temp=p.with_name(p.name+'.tmp');temp.write_text(s,'utf-8');os.replace(temp,p)
def long(p):return '\\\\?\\'+str(p.resolve()) if os.name=='nt' else str(p)
base=B/'base4';work=B/'work4';cache=ROOT/'apk-analysis/classroom-split-bottom-v6'
if not base.exists():
 reuse=False
 if (cache/'classes4-patch.apk').exists():
  with zipfile.ZipFile(SOURCE) as a,zipfile.ZipFile(cache/'classes4-patch.apk') as c:reuse=a.read('classes4.dex')==c.read('classes.dex')
 if reuse:
  base.mkdir()
  for p in (cache/'work4').rglob('*'):
   rel=p.relative_to(cache/'work4')
   if rel.parts[0] in {'build','dist'}:continue
   q=base/rel
   if p.is_dir():q.mkdir(parents=True,exist_ok=True)
   else:q.parent.mkdir(parents=True,exist_ok=True);os.link(long(p),long(q))
 else:
  thin=B/'input-classes4.apk'
  with zipfile.ZipFile(SOURCE) as a,zipfile.ZipFile(thin,'w') as z:z.writestr('classes.dex',a.read('classes4.dex'));z.writestr('AndroidManifest.xml',a.read('AndroidManifest.xml'))
  run([JAVA,'-jar',T/'apktool.jar','d','-r','-o',base,thin])
for p in base.rglob('*'):
 rel=p.relative_to(base)
 if rel.parts[0] in {'build','dist'}:continue
 q=work/rel
 if p.is_dir():q.mkdir(parents=True,exist_ok=True)
 elif not os.path.exists(long(q)):q.parent.mkdir(parents=True,exist_ok=True);os.link(long(p),long(q))
PAT=re.compile(r'^\.method ([^\n]+)\n.*?^\.end method',re.M|re.S)
HELPER='Lcom/hljzjx/classroom/mod/ContinuationResults;'
MODEL='Lcom/hailiang/xxb/room/server/entity/ask/HandwriteAskShowAnswer;'
RESULT='Lcom/nice/room/ui/roomlive/fragment/ask/result/QuestionAskHandwriteResultFragment;'
changes={}
def edit(file,signature,fn):
 path=work/'smali'/file;s=path.read_text('utf-8');found=[m for m in PAT.finditer(s) if m[1].strip().endswith(signature)];assert len(found)==1
 m=found[0];new=fn(m[0]);assert new!=m[0];write(path,s[:m.start()]+new+s[m.end():]);changes[file+'::'+signature]=hashlib.sha256(new.encode()).hexdigest()
def prefix(body,extra):
 marker=re.search(r'    \.locals \d+\n',body);assert marker
 return body[:marker.end()]+'\n'+extra+body[marker.end():]
def once(body,old,new):assert body.count(old)==1;return body.replace(old,new)
base_result='com/nice/room/ui/roomlive/fragment/ask/result/QuestionAskHandwriteResultFragment.smali'
view='com/nice/room/ui/roomlive/fragment/ask/view/QuestionAskHandwriteAnswerView.smali'
# Always start with immutable baseline text, even on a rebuild.
for file in [base_result,view]:write(work/'smali'/file,(base/'smali'/file).read_text('utf-8'))
edit(base_result,'updateAnswer('+MODEL+'Z)V',lambda old:prefix(old,'    invoke-static {p0, p1, p2}, '+HELPER+'->resolve('+RESULT+MODEL+'Z)'+MODEL+'\n    move-result-object p1\n'))
def update(old):
 new=prefix(old,'    invoke-static {p0}, '+HELPER+'->beginRender(Landroid/view/View;)V\n')
 return once(new,'invoke-virtual {v9, v0}, Lcom/hailiang/handwrite/widget/HandwriteView;->post(Ljava/lang/Runnable;)Z','invoke-static {p0, v9, v0}, '+HELPER+'->postRender(Landroid/view/View;Landroid/view/View;Ljava/lang/Runnable;)Z')
edit(view,'update(Landroidx/lifecycle/LifecycleCoroutineScope;Lcom/hailiang/xxb/room/server/entity/ask/QuestionAskType;'+MODEL+'Z)V',update)
def images(old):
 needle='    const/16 v8, 0x10'
 return once(old,needle,'    invoke-static {p3, v7}, '+HELPER+'->guardImages(Landroid/view/View;Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;\n    move-result-object v7\n\n'+needle)
edit(view,'update$lambda$6(Lcom/hailiang/handwrite/bean/DrawingRecord;Lcom/hailiang/handwrite/bean/DrawingRecord;Landroidx/lifecycle/LifecycleCoroutineScope;Lcom/nice/room/ui/roomlive/fragment/ask/view/QuestionAskHandwriteAnswerView;'+MODEL+'Lcom/hailiang/xxb/room/server/entity/ask/QuestionAskType;)V',images)
edit(view,'update$lambda$6$lambda$5(Lcom/hailiang/xxb/room/server/entity/ask/QuestionAskType;'+MODEL+'Lcom/nice/room/ui/roomlive/fragment/ask/view/QuestionAskHandwriteAnswerView;Ljava/util/List;I)Lkotlin/Unit;',lambda old:once(old,'invoke-virtual {p0, p1}, Lcom/hailiang/handwrite/widget/HandwriteView;->post(Ljava/lang/Runnable;)Z','invoke-static {p2, p0, p1}, '+HELPER+'->postRender(Landroid/view/View;Landroid/view/View;Ljava/lang/Runnable;)Z'))
classes=B/'classes';classes.mkdir(exist_ok=True)
run([JAVA,'-jar',T/'ecj.jar','-proc:none','-source','8','-target','8','-encoding','UTF-8','-warn:none','-cp',ANDROID,'-d',classes,*list((B/'src').rglob('*.java')),*list((B/'compile-api').rglob('*.java'))])
own={p.stem for p in (B/'src').rglob('*.java')};jar=B/'continuation-tools.jar';api=B/'compile-api.jar'
with zipfile.ZipFile(jar,'w') as z,zipfile.ZipFile(api,'w') as lib:
 for p in classes.rglob('*.class'):
  name=p.relative_to(classes).as_posix();family=p.name.split('$')[0].split('.')[0]
  target=z if name.startswith('com/hljzjx/classroom/mod/') and family in own else lib;target.write(p,name)
helper=B/'helper10';helper.mkdir(exist_ok=True)
run([JAVA,'-cp',T/'jadx/lib/jadx-gui-1.5.6-all.jar','com.android.tools.r8.D8','--min-api','29','--lib',JAVA.parent.parent,'--lib',ANDROID,'--lib',api,'--output',helper,jar])
patched=B/'classes4-patch.apk';run([JAVA,'-jar',T/'apktool.jar','b',work,'-o',patched])
with zipfile.ZipFile(patched) as z:replacement={'classes4.dex':z.read('classes.dex'),'classes10.dex':(helper/'classes.dex').read_bytes()}
unsigned=B/'continuation-results-unsigned.apk'
def sig(n):return n.startswith('META-INF/') and n.upper().endswith(('.MF','.SF','.RSA','.DSA','.EC'))
with zipfile.ZipFile(SOURCE) as a,zipfile.ZipFile(unsigned,'w') as z:
 for info in a.infolist():
  if not sig(info.filename):z.writestr(info,replacement.get(info.filename) or a.read(info.filename))
stage=Path(os.environ['LOCALAPPDATA'])/'CodexBuilds/classroom-continuation-results-v7';stage.mkdir(parents=True,exist_ok=True)
shutil.copyfile(unsigned,stage/unsigned.name);signed=stage/'signed';signed.mkdir(exist_ok=True);result=signed/'continuation-results-aligned-signed.apk';result.unlink(missing_ok=True)
run([JAVA,'-jar',T/'uber-apk-signer.jar','-a',stage/unsigned.name,'-o',signed,'--ks',T/'signing/classroom-mod.jks','--ksAlias','classroommod','--ksPass','android','--ksKeyPass','android'])
shutil.copyfile(result,DEST)
meta={'input':SOURCE.name,'input_sha256':EXPECTED,'output':DEST.name,'sha256':hashlib.sha256(DEST.read_bytes()).hexdigest(),'size':DEST.stat().st_size,'changed_payloads':['classes4.dex','classes10.dex'],'native_method_patches':changes,'device_tested':False,'school_server_tested':False}
write(B/'build.json',json.dumps(meta,ensure_ascii=False,indent=2));print(json.dumps(meta,ensure_ascii=False,indent=2))
