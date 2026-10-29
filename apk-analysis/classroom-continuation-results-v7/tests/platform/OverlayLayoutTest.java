import android.view.*;import android.widget.*;import android.graphics.*;import android.content.res.Resources;import android.app.Dialog;
import com.nice.room.ui.*;import com.nice.room.ui.roomlive.fragment.ask.answer.*;import com.hailiang.common.util.AppRuntimeData;import com.hljzjx.classroom.mod.*;
public class OverlayLayoutTest {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static TextView named(View v,String s){if(v instanceof TextView && s.equals(((TextView)v).text))return (TextView)v;if(v instanceof ViewGroup)for(View child:((ViewGroup)v).children){TextView b=named(child,s);if(b!=null)return b;}return null;}
 static int d(float den,int px){return Math.round(den*px);}
 static Rect box(View v){if(v.lp instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)v.lp;if(p.gravity==(Gravity.TOP|Gravity.LEFT))return new Rect(v.parent.pl+p.leftMargin,v.parent.pt+p.topMargin,v.parent.pl+p.leftMargin+p.width,v.parent.pt+p.topMargin+p.height);
   if(p.gravity==(Gravity.TOP|Gravity.END)){int right=v.parent.width-p.endMargin,top=p.topMargin;return new Rect(right-v.width,top,right,top+v.height);}}return new Rect(v.left,v.top,v.left+v.width,v.top+v.height);}
 static boolean overlap(Rect a,Rect b){return a.left<b.right && a.right>b.left && a.top<b.bottom && a.bottom>b.top;}
 static class Page {
  RoomLiveActivity a=new RoomLiveActivity();FrameLayout outer=new FrameLayout(a),host=new FrameLayout(a),root=new FrameLayout(a),header=new FrameLayout(a);TextView submit=new TextView(a),other=new TextView(a),title=new TextView(a);LinearLayout top;float density;int submits;
  Page(float den,int width,int height){density=den;Resources.testDensity=den;AppRuntimeData.user=17;outer.width=host.width=root.width=d(den,width);outer.height=host.height=root.height=d(den,height);outer.addView(host,new FrameLayout.LayoutParams(-1,-1,0));host.id=200;outer.attach();host.addView(root,new FrameLayout.LayoutParams(-1,-1,0));root.addView(header,new FrameLayout.LayoutParams(-1,d(den,80),0));header.width=host.width;header.height=d(den,80);
   submit.id=500;submit.setText("提交");place(submit,width-92,24,72,40);header.addView(submit,new FrameLayout.LayoutParams(-2,-2,0));submit.setOnClickListener(new View.OnClickListener(){public void onClick(View v){submits++;}});
   other.id=91001;other.setText("原有操作");place(other,width-204,24,100,48);other.setOnClickListener(new View.OnClickListener(){public void onClick(View v){}});header.addView(other,new FrameLayout.LayoutParams(-2,-2,0));
   title.setText("单选题 01:36");place(title,20,24,180,40);header.addView(title,new FrameLayout.LayoutParams(-2,-2,0));a.getWindow().setDecorView(outer);QuestionPptReturn.install(root);View.drain();top=(LinearLayout)named(host,"返回 PPT").parent;
  }
  void place(View v,int x,int y,int w,int h){v.left=d(density,x);v.top=d(density,y);v.width=d(density,w);v.height=d(density,h);}
  Rect nativeBox(View v){return new Rect(header.left+v.left,header.top+v.top,header.left+v.left+v.width,header.top+v.top+v.height);}
  void layout(){root.tree.global();View.drain();}
  void clear(View v,View n,String message){ok(!overlap(box(v),nativeBox(n)),message);}
  void inside(View v){Rect b=box(v);ok(b.left>=0 && b.top>=0 && b.right<=host.width && b.bottom<=host.height,"tools stay inside current logical viewport");}
  void done(){host.removeView(root);View.drain();Resources.testDensity=1;}
 }
 public static void main(String[] args){
  Page screenshot=new Page(1,1280,800);
  if(args.length>0){screenshot.top.width=220;screenshot.top.height=48;screenshot.clear(screenshot.top,screenshot.other,"top tools must avoid an unlisted native action");return;}
  screenshot.clear(screenshot.top,screenshot.submit,"submit stays clear");screenshot.clear(screenshot.top,screenshot.other,"unlisted native action stays clear");screenshot.clear(screenshot.top,screenshot.title,"title and countdown stay clear");screenshot.inside(screenshot.top);screenshot.submit.performClick();ok(screenshot.submits==1,"original submit listener preserved");
  int changes=screenshot.top.parameterChanges;for(int n=0;n<12;n++)screenshot.layout();ok(screenshot.top.parameterChanges==changes,"stable geometry causes no relayout loop");
  screenshot.place(screenshot.other,780,24,388,48);screenshot.layout();screenshot.clear(screenshot.top,screenshot.other,"dynamic native action width triggers movement");screenshot.other.visibility=View.GONE;screenshot.layout();ok(box(screenshot.top).right<=screenshot.submit.left-12,"hidden action releases space but visible submit keeps gap");screenshot.header.visibility=View.INVISIBLE;screenshot.layout();ok(box(screenshot.top).right==1268,"hidden ancestor releases stale native bounds");screenshot.header.visibility=View.VISIBLE;
  screenshot.host.setPadding(22,18,14,10);screenshot.layout();screenshot.inside(screenshot.top);screenshot.clear(screenshot.top,screenshot.submit,"host padding does not double-offset controls");
  FrameLayout newer=new FrameLayout(screenshot.a);screenshot.host.addView(newer,new FrameLayout.LayoutParams(-1,-1,0));screenshot.layout();ok(screenshot.top.visibility==View.GONE,"old page's top tools hide below a newer native question");screenshot.host.removeView(newer);screenshot.layout();ok(screenshot.top.visibility==View.VISIBLE,"current tools restore after covering page disappears");screenshot.done();
  for(float density:new float[]{1,1.5f,2,2.5f})for(float scale:new float[]{1,.72f,.5f}){
   Page p=new Page(density,1280,800);p.outer.sx=p.outer.sy=scale;p.outer.locationX=200;p.outer.locationY=90;p.host.tx=d(density,8);p.layout();p.clear(p.top,p.submit,"density/window scaling leaves native submit clear");p.clear(p.top,p.other,"density/window scaling leaves unknown native control clear");p.inside(p.top);
   p.header.left=d(density,30);p.other.left-=d(density,30);p.submit.left-=d(density,30);p.layout();p.clear(p.top,p.other,"nested header offsets handled in host coordinates");p.clear(p.top,p.submit,"nested submit offsets handled");
   p.submit.width=d(density,150);p.submit.left=d(density,1080);p.layout();p.clear(p.top,p.submit,"upload label resize reserved");p.done();
  }
  // Other new buttons use the same allocator. No accidental AI/next-page,
  // result/AI, or return-PPT/PiP collisions in full and narrower windows.
  for(int width:new int[]{1280,900,600,420,320}){
   Page p=new Page(1,width,800);p.title.width=Math.min(160,width/3);p.other.visibility=View.GONE;
   TextView pager=new TextView(p.a);pager.setText("上一题 / 下一题");p.place(pager,12,700,170,64);pager.setOnClickListener(new View.OnClickListener(){public void onClick(View v){}});p.root.addView(pager,new FrameLayout.LayoutParams(-2,-2,0));
   TextView next=new TextView(p.a);next.setText("下一题");p.place(next,width-162,700,150,64);next.setOnClickListener(new View.OnClickListener(){public void onClick(View v){}});p.root.addView(next,new FrameLayout.LayoutParams(-2,-2,0));
   QuestionAskOptionFragment f=new QuestionAskOptionFragment();f.root=p.root;QuestionActions.install(f,p.root);f.finished=true;HandwriteContinuation.stateChanged(f);p.layout();
   LinearLayout ai=(LinearLayout)named(p.host,"AI 问问").parent;TextView cont=named(p.host,"继续作答");ok(cont!=null,"continuation stays available in narrow windows");
   for(View v:new View[]{p.top,ai,cont})if(v.visibility==View.VISIBLE){p.inside(v);ok(!overlap(box(v),box(pager)) && !overlap(box(v),box(next)),"extra tools avoid both lower page/next controls");}
   if(ai.visibility==View.VISIBLE && cont.visibility==View.VISIBLE)ok(!overlap(box(ai),box(cont)),"new AI and continuation controls avoid each other");
   if(p.top.visibility==View.VISIBLE && ai.visibility==View.VISIBLE)ok(!overlap(box(p.top),box(ai)),"top and bottom groups avoid each other");
   p.outer.windowFrame=new Rect(0,0,width,540);p.layout();for(View v:new View[]{p.top,ai,cont})if(v.visibility==View.VISIBLE)ok(box(v).bottom<=540,"IME visible frame excludes obscured space");
   p.outer.windowFrame=null;p.layout();p.done();
  }
  // Force the natural two-button row to need a stack. Both actions remain
  // usable and return to a horizontal row once the window expands.
  Page stack=new Page(1,210,600);stack.title.visibility=View.GONE;stack.submit.visibility=View.GONE;stack.other.visibility=View.GONE;stack.layout();ok(stack.top.getOrientation()==LinearLayout.VERTICAL,"narrow window stacks the two actions instead of clipping");stack.host.width=stack.root.width=stack.outer.width=600;stack.layout();ok(stack.top.getOrientation()==LinearLayout.HORIZONTAL,"expanded window restores the horizontal row");stack.done();
  // A large-font two-action row cannot fit either arrangement, but a compact
  // tools menu still fits. It forwards the same actions and cleans up on exit.
  Page menu=new Page(1,320,300);menu.title.visibility=menu.submit.visibility=menu.other.visibility=View.GONE;TextView back=named(menu.host,"返回 PPT"),pip=named(menu.host,"画中画");back.naturalWidth=pip.naturalWidth=350;menu.layout();TextView more=named(menu.host,"工具");ok(more!=null && more.visibility==View.VISIBLE && menu.top.visibility==View.INVISIBLE,"crowded tools collapse into a visible compact menu");more.performClick();ok(Dialog.last.isShowing() && named(Dialog.last.content,"返回 PPT")!=null && named(Dialog.last.content,"画中画")!=null,"compact menu contains both original actions");named(Dialog.last.content,"返回 PPT").performClick();ok(menu.root.visibility==View.INVISIBLE,"compact menu forwards original PPT action");View.drain();menu.done();ok(!Dialog.last.isShowing(),"detaching question closes compact tools menu");
  System.out.println("PASS "+checks+" adaptive native/mod control avoidance assertions");
 }
}
