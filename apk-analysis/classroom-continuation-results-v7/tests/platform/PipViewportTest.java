import android.app.*;import android.view.*;import android.widget.*;import com.nice.room.ui.*;import com.nice.room.ui.roomlive.tool.*;import com.hailiang.handwrite.widget.*;import com.hljzjx.classroom.mod.*;import java.util.*;
public class PipViewportTest {
static int checks;static void ok(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
static void near(float a,float b,String message){ok(Math.abs(a-b)<.002,message+": "+a+" vs "+b);}
static class Page{
 RoomLiveActivity a=new RoomLiveActivity();FrameLayout parent=new FrameLayout(a),root=new FrameLayout(a),doc=new FrameLayout(a);HandwriteView teacher=new HandwriteView(a),student=new HandwriteView(a);Object strokes=student.record;
 Page(){parent.width=2560;parent.height=1600;root.width=2560;root.height=1600;parent.id=android.R.id.content;parent.addView(root,new FrameLayout.LayoutParams(2560,1600,Gravity.TOP|Gravity.LEFT));root.addView(doc,new FrameLayout.LayoutParams(2560,1600,0));root.addView(teacher,new FrameLayout.LayoutParams(2560,1600,0));root.addView(student,new FrameLayout.LayoutParams(2560,1600,0));parent.attach();a.getWindow().setDecorView(parent);a.views.put(android.R.id.content,parent);teacher.setScaleX(1.4f);teacher.setScaleY(1.4f);teacher.setTranslationX(-110);teacher.setTranslationY(-70);}
 // Replay the unchanged standard WindowSizing body if the new entry guard is
 // not active. A density/configuration change must not replace the live frame.
 void resize(int w,int h,float density){parent.resize(w,h);if(!PipViewport.apply(parent,root)){int base=Math.round(1280*density);float scale=(float)w/base;root.lp.width=base;root.lp.height=Math.round(h/scale);root.setScaleX(scale);root.setScaleY(scale);}root.width=root.lp.width;root.height=root.lp.height;parent.tree.draw();}
}
public static void main(String[] args){
 Page p=new Page();ClassroomPip.enter(p.a,p.root);p.resize(780,488,.6f);
 ok(p.root.lp.width==2560 && p.root.lp.height==1600,"PiP must preserve the entering page's logical rendering frame");
 if(args.length>0){ClassroomPip.close(p.a);System.out.println("PASS stable-frame regression");return;}
 ok(Boolean.FALSE.equals(p.a.lastPip.seamless),"non-video WebView opts out of seamless surface resizing");
 for(int[] wh:new int[][]{{780,488},{640,400},{360,225},{1100,688},{600,800},{1000,400},{2560,1600}}){
  p.resize(wh[0],wh[1],.5f);float s=Math.min(wh[0]/2560f,wh[1]/1600f);
  near(p.root.sx,s,"single X scale");near(p.root.sy,s,"uniform Y scale");near(p.root.px,0,"zero X pivot");near(p.root.py,0,"zero Y pivot");
  for(int[] point:new int[][]{{0,0},{2560,0},{0,1600},{2560,1600},{1280,800}}){float x=p.root.tx+point[0]*s,y=p.root.ty+point[1]*s;ok(x>=-.01 && x<=wh[0]+.01 && y>=-.01 && y<=wh[1]+.01,"all page corners and center remain in PiP");}
  ok(p.doc.lp.width==2560 && p.doc.lp.height==1600 && p.teacher.lp.width==2560 && p.student.lp.height==1600,"document and both ink layers retain the same source frame");
  near(p.teacher.sx,1.4f,"remote teacher zoom preserved");near(p.teacher.tx,-110,"remote teacher pan preserved");ok(p.student.record==p.strokes,"live ink instance survives resizing");
 }
 // A renderer/configuration callback that rewrites outer params is repaired
 // before drawing. Inner teacher changes stay live and are never restored away.
 p.root.lp.width=640;p.root.lp.height=400;p.root.width=640;p.root.height=400;
 ok(!p.parent.tree.draw(),"skip the intermediate wrongly measured frame");ok(p.root.lp.width==2560 && p.root.lp.height==1600,"pre-draw restores the logical frame");p.root.width=2560;p.root.height=1600;ok(p.parent.tree.draw(),"draw after the next measure pass");
 p.teacher.setTranslationX(-220);p.parent.scrollTo(50,80);p.resize(780,488,.4f);ok(p.parent.scrollX==0 && p.parent.scrollY==0,"no stale outer scroll clips a corner");
 FrameLayout other=new FrameLayout(p.a);ok(!PipViewport.apply(other,p.root),"unrelated roots keep normal window sizing");
 int before=WindowSizing.installs;ClassroomPip.changed(p.a,false);p.a.inPip=false;near(p.root.sx,1,"exit restores original outer scale");near(p.teacher.tx,-220,"exit keeps newer teacher transform");ok(p.root.lp.width==2560 && p.root.lp.height==1600 && WindowSizing.installs==before+1,"exit restores original layout and resumes native sizing");ok(p.parent.layoutListeners.isEmpty() && p.parent.tree.pre.isEmpty(),"exit removes resize and draw listeners");ok(!PipViewport.apply(p.parent,p.root),"outside PiP native sizing is untouched");View.drain();
 // Repeated entry from a window that already has an outer scale must replace
 // that scale, not multiply it or recapture a tiny PiP frame on the mode callback.
 for(int i=0;i<5;i++){Page q=new Page();q.root.height=1250;q.root.lp.height=1250;q.root.sx=q.root.sy=.7f;q.root.tx=7;q.root.ty=9;
  FrameLayout.LayoutParams lp=(FrameLayout.LayoutParams)q.root.lp;lp.gravity=17;lp.setMargins(3,4,5,6);lp.setMarginStart(8);lp.setMarginEnd(9);q.root.px=10;q.root.py=11;
  ClassroomPip.enter(q.a,q.root);q.resize(800,500,.5f);ClassroomPip.changed(q.a,true);q.resize(400,250,.3f);near(q.root.sx,400/2560f,"mode callback never recaptures smaller baseline");ok(q.root.lp.height==1250,"entering window aspect retained");
  ClassroomPip.changed(q.a,false);q.a.inPip=false;near(q.root.sx,.7f,"restore previous window scale");near(q.root.tx,7,"restore previous translation");near(q.root.px,10,"restore previous pivot");ok(lp.gravity==17 && lp.leftMargin==3 && lp.topMargin==4 && lp.endMargin==9 && lp.startMargin==8,"restore margins and gravity");ok(q.parent.tree.pre.isEmpty() && q.parent.layoutListeners.isEmpty(),"no retained observers after repeated entry");View.drain();}
 for(int failure=0;failure<3;failure++){Page q=new Page();if(failure==0)q.a.manager.pip=false;else if(failure==1)q.a.enterResult=false;else q.a.throwPip=true;ClassroomPip.enter(q.a,q.root);ok(!ClassroomPip.active(q.a) && !PipViewport.apply(q.parent,q.root),"denial leaves no frozen window");ok(q.parent.tree.pre.isEmpty() && q.parent.layoutListeners.isEmpty(),"denial removes observers");View.drain();}
 Page q=new Page();ClassroomPip.enter(q.a,q.root);q.a.destroyed=true;before=WindowSizing.installs;ClassroomPip.close(q.a);ok(WindowSizing.installs==before && q.parent.tree.pre.isEmpty(),"destroy removes listeners without reinstalling destroyed activity");View.drain();
 RoomLiveActivity a=new RoomLiveActivity();RoomToolBarView bar=new RoomToolBarView(a);bar.id=300;LocalApps.registerToolbar(bar);ArrayList<FunctionToolItemInfo> items=new ArrayList<>();LocalApps.append(items,bar.state);
 ok(items.size()==3,"three native local entries");ok(items.get(0).icon==400,"AI keeps original icon");ok(items.get(1).icon<0 && "file:///android_asset/mod_toolbar/onion.png".equals(items.get(1).link),"Onion icon loads from packaged asset");ok(items.get(2).icon<0 && "file:///android_asset/mod_toolbar/pip.png".equals(items.get(2).link),"PiP uses its own packaged icon");a.manager.installed=false;LocalApps.close(a);
 LocalApps.registerToolbar(bar);items.clear();LocalApps.append(items,bar.state);ok(items.get(1).link.endsWith("onion.png"),"Onion icon remains available when external app absent");LocalApps.close(a);
 System.out.println("PASS "+checks+" PiP geometry, restoration, failure and offline-icon assertions");
}}
