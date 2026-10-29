from pathlib import Path
import hashlib,json,zipfile
B=Path(__file__).resolve().parent;ROOT=B.parent.parent;DEST=ROOT/'学生端课堂-续答结果显示修复-v7-源码与脚本.zip'
files={B/n for n in ['README.md','build.py','verify.py','test_results.py','package_source.py','build.json','verification.json','tests.json','previous-negative.txt']}
for folder in ['src','compile-api','tests/platform']:
 files.update(p for p in (B/folder).rglob('*.java') if p.is_file())
files.update((B/'tests/previous').glob('*.java.txt'))
files.update(B.glob('*-results.txt'))
files.update(ROOT/'apk-analysis'/n for n in ['canonical_notice_methods.py','launcher_dex_linkage.py'])
with zipfile.ZipFile(DEST,'w',zipfile.ZIP_DEFLATED) as z:
 for p in sorted(files):assert p.is_file(),p;z.write(p,p.relative_to(ROOT).as_posix())
 z.write(ROOT/'github-release/repository/LICENSE','LICENSE')
with zipfile.ZipFile(DEST) as z:
 assert z.testzip() is None
 assert all(Path(n).suffix not in {'.apk','.jks','.keystore','.dex','.class','.jar'} for n in z.namelist())
 summary={'archive':DEST.name,'files':len(z.namelist()),'size':DEST.stat().st_size,'sha256':hashlib.sha256(DEST.read_bytes()).hexdigest()}
(B/'source-archive.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2),'utf-8');print(json.dumps(summary,ensure_ascii=False,indent=2))
