"""Verify output bytes, narrow native hooks, exact older feature preservation."""
from pathlib import Path
import copy,hashlib,json,re,sys,zipfile
B=Path(__file__).resolve().parent;ROOT=B.parent.parent;sys.path[:0]=[str(ROOT/'apk-analysis/deps'),str(ROOT/'apk-analysis')]
from loguru import logger
logger.remove()
from androguard.core.apk import APK
from androguard.core.dex import DEX
from canonical_notice_methods import methods
from launcher_dex_linkage import DexLinker
meta=json.loads((B/'build.json').read_text('utf-8'));source=ROOT/meta['input'];output=ROOT/meta['output']
assert hashlib.sha256(source.read_bytes()).hexdigest()==meta['input_sha256'];assert hashlib.sha256(output.read_bytes()).hexdigest()==meta['sha256']
def sig(n):return n.startswith('META-INF/') and n.upper().endswith(('.MF','.SF','.RSA','.DSA','.EC'))
with zipfile.ZipFile(source) as a,zipfile.ZipFile(output) as z:
 assert z.testzip() is None
 before={n for n in a.namelist() if not sig(n)};after={n for n in z.namelist() if not sig(n)}
 assert before==after
 changed={n for n in before if a.read(n)!=z.read(n)};assert changed=={'classes4.dex','classes10.dex'},changed
 old4=DEX(a.read('classes4.dex'));old10=DEX(a.read('classes10.dex'));dexes={n:DEX(z.read(n)) for n in z.namelist() if re.fullmatch(r'classes\d*\.dex',n)}
def no_nops(seq):
 indexes={i:sum(op!='nop' for op,args in seq[:i]) for i in range(len(seq))};result=copy.deepcopy([item for item in seq if item[0]!='nop'])
 for op,args in result:
  for i,(kind,value) in enumerate(args):
   if kind==3:args[i]=(kind,indexes[value])
   elif kind=='targets':args[i]=(kind,[indexes[v] for v in value])
 return result
HELPER='Lcom/hljzjx/classroom/mod/ContinuationResults;'
RESULT='Lcom/nice/room/ui/roomlive/fragment/ask/result/QuestionAskHandwriteResultFragment;'
VIEW='Lcom/nice/room/ui/roomlive/fragment/ask/view/QuestionAskHandwriteAnswerView;'
om,nm=methods(old4),methods(dexes['classes4.dex']);assert om.keys()==nm.keys()
delta={k for k in om if om[k]!=nm[k]}
intended={k for k in om if (k[0]==RESULT and k[1]=='updateAnswer') or (k[0]==VIEW and k[1] in {'update','update$lambda$6','update$lambda$6$lambda$5'})}
assert len(intended)==4 and intended<=delta
for k in delta-intended:assert om[k][:2]==nm[k][:2] and no_nops(om[k][2])==no_nops(nm[k][2]),k
# Check exact original source bodies can be recovered by removing ONLY the hooks.
PAT=re.compile(r'^\.method ([^\n]+)\n.*?^\.end method',re.M|re.S)
def methods_text(p):return {m[1]:m[0] for m in PAT.finditer(p.read_text('utf-8'))}
native_files=[Path('com/nice/room/ui/roomlive/fragment/ask/result/QuestionAskHandwriteResultFragment.smali'),Path('com/nice/room/ui/roomlive/fragment/ask/view/QuestionAskHandwriteAnswerView.smali')]
hooked=0
for f in native_files:
 old,new=methods_text(B/'base4/smali'/f),methods_text(B/'work4/smali'/f)
 for signature,body in old.items():
  n=new[signature]
  if n==body:continue
  if ' updateAnswer(' in signature:
   n=re.sub(r'\n    invoke-static \{p0, p1, p2\}, '+re.escape(HELPER)+r'->resolve\([^\n]+\n    move-result-object p1\n','',n,count=1)
  elif signature.startswith('public final update('):
   n=n.replace('\n    invoke-static {p0}, '+HELPER+'->beginRender(Landroid/view/View;)V\n','',1)
   n=n.replace('invoke-static {p0, v9, v0}, '+HELPER+'->postRender(Landroid/view/View;Landroid/view/View;Ljava/lang/Runnable;)Z','invoke-virtual {v9, v0}, Lcom/hailiang/handwrite/widget/HandwriteView;->post(Ljava/lang/Runnable;)Z',1)
  elif ' update$lambda$6(' in signature:
   n=n.replace('    invoke-static {p3, v7}, '+HELPER+'->guardImages(Landroid/view/View;Lkotlin/jvm/functions/Function1;)Lkotlin/jvm/functions/Function1;\n    move-result-object v7\n\n','',1)
  elif ' update$lambda$6$lambda$5(' in signature:
   n=n.replace('invoke-static {p2, p0, p1}, '+HELPER+'->postRender(Landroid/view/View;Landroid/view/View;Ljava/lang/Runnable;)Z','invoke-virtual {p0, p1}, Lcom/hailiang/handwrite/widget/HandwriteView;->post(Ljava/lang/Runnable;)Z',1)
  assert n==body,signature;hooked+=1
assert hooked==4
om10,nm10=methods(old10),methods(dexes['classes10.dex'])
changed_families=('Lcom/hljzjx/classroom/mod/HandwriteCloud','Lcom/hljzjx/classroom/mod/HandwriteContinuation')
unrelated10={k for k in om10 if not any(k[0].startswith(f) for f in changed_families)}
for k in unrelated10:assert k in nm10 and om10[k]==nm10[k],k
for k in nm10.keys()-om10.keys():assert k[0].startswith('Lcom/hljzjx/classroom/mod/ContinuationResults') or any(k[0].startswith(f) for f in changed_families),k
classes=[c for d in dexes.values() for c in d.get_classes()];assert len(classes)==len({c.get_name() for c in classes})
helpers=list(dexes['classes10.dex'].get_classes());assert all(c.get_name().startswith('Lcom/hljzjx/classroom/mod/') for c in helpers)
refs,errors=DexLinker(dexes.values()).check(helpers+[dexes['classes4.dex'].get_class(RESULT),dexes['classes4.dex'].get_class(VIEW)]);assert not errors,errors[:30]
base,apk=APK(str(source)),APK(str(output));assert base.get_package()==apk.get_package()=='com.hljzjx.edu.student.classroom'
assert base.get_androidversion_code()==apk.get_androidversion_code()=='321000';assert base.get_androidversion_name()==apk.get_androidversion_name()=='3.2.1.0'
assert apk.get_min_sdk_version()==base.get_min_sdk_version()=='29';assert apk.get_target_sdk_version()==base.get_target_sdk_version()=='30'
assert apk.is_signed_v2() and apk.is_signed_v3();cert=(apk.get_certificates_der_v3() or apk.get_certificates_der_v2())[0];assert cert==(base.get_certificates_der_v3() or base.get_certificates_der_v2())[0]
# Literal resource names used by the result hook must resolve in the actual APK.
arsc=apk.get_android_resources();resource_names=arsc.get_public_resources('com.hljzjx.edu.student.classroom').decode('utf-8')
for name in ['rgAnswer','btnMyAnswer']:assert 'name="'+name+'"' in resource_names,name
tests=json.loads((B/'tests.json').read_text('utf-8'));assert tests['previous_negative_control']
for name,sha in tests['source_sha256'].items():assert hashlib.sha256((B/name).read_bytes()).hexdigest()==sha,name
report={**meta,'preserved_payloads':len(before)-2,'native_hooks_verified':hooked,'other_native_methods_identical':len(om)-len(delta),'alignment_only_methods':len(delta-intended),
 'unrelated_dex10_methods_identical':len(unrelated10),'ppt_v5_dex9_and_split_bottom_v6_dex11_identical':True,'checked_member_references':refs,'certificate_sha256':hashlib.sha256(cert).hexdigest(),
 'tests':tests,'limitations':'Actual authored Java with observable native/API fixtures, not Pixel Tablet rendering or school-server readback. API-accepted snapshot is labelled and kept distinct from matching cloud-returned metadata. Choice results use native refetch only. No installation or real HTTP/MQTT.'}
(B/'verification.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),'utf-8');print(json.dumps({k:v for k,v in report.items() if k not in {'tests','native_method_patches'}},ensure_ascii=False,indent=2))
