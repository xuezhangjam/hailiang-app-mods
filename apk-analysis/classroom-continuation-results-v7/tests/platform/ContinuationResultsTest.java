import java.util.*;
import android.view.*;import android.widget.*;import android.os.Handler;
import com.hailiang.common.util.AppRuntimeData;
import com.hailiang.xxb.room.server.entity.ask.*;
import com.nice.room.ui.roomlive.fragment.ask.result.*;
import com.hljzjx.classroom.mod.*;
import kotlin.Unit;import kotlin.jvm.functions.Function1;

/** Behavioral read-model tests: the API and its later readback are independent.
 * The result fixture invokes exactly the native resolve bridge verified in DEX.
 * No callback implicitly changes the simulated server's stored result. */
public class ContinuationResultsTest {
 static int checks;
 static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static HandwriteAskShowAnswer raw(String content){return new HandwriteAskShowAnswer(17,"student",new QuestionAskType(4),new ArrayList<String>(Arrays.asList("question.png")),content,"original teacher checks","01:32",true);}
 static class Page {
  HandwriteCloudTest.Page p=new HandwriteCloudTest.Page();
  FrameLayout root;RadioGroup tabs;QuestionAskHandwriteResultFragment r;
  HandwriteAskShowAnswer original=raw("initial submitted strokes");
  Page(boolean result){p.edit();if(result)bind();}
  void bind(){root=new FrameLayout(p.a);p.host.addView(root,new FrameLayout.LayoutParams(-1,-1,0));tabs=new RadioGroup(p.a);tabs.id=610;tabs.checked=611;root.addView(tabs,new FrameLayout.LayoutParams(-2,-2,0));r=new QuestionAskHandwriteResultFragment();r.root=root;r.ask=p.f.ask;r.room=p.f.room;QuestionPptReturn.install(root);QuestionActions.install(r,root);HandwriteContinuation.attachResult(r,root);r.updateAnswer(original,true);View.drain();}
  void submit(String content){p.f.items.get(0).content=content;p.submit.performClick();}
  void reply(boolean success){p.f.model.reply(p.f.model.sent.size()-1,success);View.drain();}
  void server(HandwriteAskShowAnswer answer){r.updateAnswer(answer,tabs.checked==611);View.drain();}
  void result(){TextView b=HandwriteCloudTest.visible(p.host,"答题结果");ok(b!=null,"continuation retains answer-results navigation");b.performClick();View.drain();}
  void edit(){TextView b=HandwriteCloudTest.visible(p.host,"继续作答");ok(b!=null,"result retains continuation navigation");b.performClick();View.drain();}
  void close(){p.done();if(root!=null)p.host.removeView(root);View.drain();}
 }
 public static void main(String[] args){
  Page p=new Page(true);
  ok(p.r.rendered==p.original && p.r.refreshes==0,"normal result is unmodified before an accepted continuation");
  p.submit("old strokes plus blue 2");
  ok(p.r.rendered==p.original && p.r.refreshes==0,"pending upload does not replace result");
  p.p.f.items.get(0).content="newer blue 3 not uploaded";
  p.reply(true);
  ok(p.r.refreshes>0,"accepted upload must refresh the existing result fragment");
  if(args.length>0)return;
  ok("old strokes plus blue 2".equals(p.r.rendered.strokes),"result displays the immutable API-accepted snapshot, not subsequent live edits");
  ok(p.r.rendered.time.startsWith("本次续答已上传") && p.r.rendered.time.endsWith("10:00"),"projection is labelled as this upload and uses actual submission elapsed time");
  ok(p.r.rendered.images.equals(p.original.images) && p.r.rendered.images!=p.original.images,"native question background is retained with a detached image list");
  ok(p.r.rendered.check==null && !p.r.rendered.excellent,"old teacher review and exhibition are not asserted for changed strokes");
  ok("initial submitted strokes".equals(p.original.strokes) && p.original.excellent && "original teacher checks".equals(p.original.check),"original readback object is never modified");
  HandwriteAskShowAnswer shown=p.r.rendered;p.server(p.original);
  ok(p.r.rendered==shown,"a delayed old cloud response cannot overwrite the latest upload");
  p.result();ok(p.r.rendered.strokes.equals("old strokes plus blue 2") && p.r.refreshes>=2,"opening results requests readback and still shows uploaded version");
  HandwriteAskShowAnswer cloud=raw("old strokes plus blue 2");p.server(cloud);
  ok(p.r.rendered==cloud && p.r.rendered.excellent && p.r.rendered.check!=null,"matching server result is authoritative, including its real review metadata");
  p.edit();p.submit("failed revision");p.reply(false);
  ok(p.r.rendered==cloud,"failed upload retains last successful result");
  int before=p.r.refreshes;p.submit("timed out revision");Handler.timeouts();View.drain();
  ok(p.r.rendered==cloud && p.r.refreshes==before,"timeout does not publish an unconfirmed result");
  p.p.f.model.reply(p.p.f.model.sent.size()-1,true);View.drain();
  ok(p.r.rendered==cloud,"expired success cannot publish an old revision");
  p.submit("second accepted version");p.reply(true);
  ok(p.r.rendered.strokes.equals("second accepted version"),"a later successful upload replaces the previous accepted version");
  p.server(cloud);ok(p.r.rendered.strokes.equals("second accepted version"),"late readback for an earlier successful upload stays stale");
  HandwriteAskShowAnswer excellent=new HandwriteAskShowAnswer(99,"peer",new QuestionAskType(4),Arrays.asList("peer.png"),"peer answer","peer review","00:40",true);
  p.tabs.checked=612;p.server(excellent);ok(p.r.rendered==excellent && !p.r.renderedMine,"excellent tab remains entirely native");
  p.submit("third accepted version");p.reply(true);
  ok(p.r.rendered==excellent && p.r.refreshes>before,"upload while viewing excellent results refreshes without switching tabs");
  p.tabs.checked=611;p.server(p.original);ok(p.r.rendered.strokes.equals("third accepted version"),"switching back to My answer selects latest upload rather than stale own excellent answer");
  p.r.failRefresh=true;p.submit("accepted despite readback failure");p.reply(true);
  ok(p.r.rendered.strokes.equals("accepted despite readback failure") && p.p.f.mqttSends==4,"readback failure cannot undo accepted upload or suppress its original notification");p.r.failRefresh=false;
  p.p.host.removeView(p.root);View.drain();p.bind();
  ok(p.r.rendered.strokes.equals("accepted despite readback failure") && p.r.refreshes>0,"recreated result uses retained accepted snapshot and requests native readback");
  p.close();

  Page late=new Page(false);late.submit("uploaded before result existed");late.reply(true);late.bind();
  ok(late.r.refreshes>0 && late.r.rendered.strokes.equals("uploaded before result existed"),"late-created result is refreshed and renders previously accepted continuation");late.close();

  Page rejected=new Page(true);rejected.submit("unaccepted ink");rejected.reply(false);
  rejected.server(rejected.original);ok(rejected.r.rendered==rejected.original && rejected.r.refreshes==0,"rejected first continuation leaves native results untouched");rejected.close();

  Page notify=new Page(true);notify.p.f.throwMqtt=true;notify.submit("accepted without notification");notify.reply(true);
  ok(notify.r.rendered.strokes.equals("accepted without notification") && notify.r.refreshes>0,"MQTT failure does not discard the API-accepted result");notify.close();

  for(int change=0;change<4;change++){
   Page stale=new Page(true);stale.submit("must not be published");
   if(change==0)AppRuntimeData.user=88;else if(change==1)stale.p.f.ask++;else if(change==2)stale.p.f.room++;else stale.p.host.removeView(stale.p.root);
   stale.reply(true);ok(stale.r.rendered==stale.original && stale.r.refreshes==0,"account/question/room/draft-detach rejects stale upload-result callback "+change);stale.close();
  }
  Page types=new Page(true);types.submit("typed upload");types.reply(true);
  HandwriteAskShowAnswer otherType=new HandwriteAskShowAnswer(17,"student",new QuestionAskType(5),Arrays.asList("photo.png"),"other type",null,"00:10",false);
  types.server(otherType);ok(types.r.rendered==otherType,"a different native answer type is not projected");
  HandwriteAskShowAnswer otherUser=raw("other user result");otherUser=new HandwriteAskShowAnswer(88,"other",otherUser.type,otherUser.images,otherUser.strokes,null,"00:10",false);
  types.server(otherUser);ok(types.r.rendered==otherUser,"even the My branch cannot replace another user's data");types.close();

  ChoiceCloudTest.Page choice=new ChoiceCloudTest.Page(2);choice.edit(true);
  QuestionAskAnswerResultBaseFragment choiceResult=new QuestionAskAnswerResultBaseFragment();
  choice.host.removeView(choice.result);choice.result=new FrameLayout(choice.a);choice.host.addView(choice.result,new FrameLayout.LayoutParams(-1,-1,0));choiceResult.root=choice.result;choiceResult.ask=choice.f.ask;choiceResult.room=choice.f.room;
  HandwriteContinuation.attachResult(choiceResult,choice.result);View.drain();choice.submit.performClick();choice.f.model.reply(0,true);View.drain();
  ok(choiceResult.refreshes>0,"choice continuation also re-reads native answer details and statistics");choice.close();

  paintOrdering();
  System.out.println("PASS "+checks+" accepted-result snapshot/readback/lifecycle/render-order assertions");
 }
 static void paintOrdering(){
  android.app.Activity a=new android.app.Activity();View owner=new View(a),target=new View(a);owner.attach();target.attach();final List<String> paints=new ArrayList<String>();
  ContinuationResults.beginRender(owner);ContinuationResults.postRender(owner,target,new Runnable(){public void run(){paints.add("old queued stroke");}});
  Function1<Integer,Unit> image=ContinuationResults.guardImages(owner,new Function1<Integer,Unit>(){public Unit invoke(Integer height){paints.add("old image callback");return Unit.INSTANCE;}});
  ContinuationResults.beginRender(owner);ContinuationResults.postRender(owner,target,new Runnable(){public void run(){paints.add("latest queued stroke");}});image.invoke(100);View.drain();
  ok(paints.equals(Arrays.asList("latest queued stroke")),"new render invalidates old queued strokes and async image callbacks");
  paints.clear();ContinuationResults.beginRender(owner);
  Function1<Integer,Unit> current=ContinuationResults.guardImages(owner,new Function1<Integer,Unit>(){public Unit invoke(Integer height){ContinuationResults.postRender(owner,target,new Runnable(){public void run(){paints.add("final old paint");}});return Unit.INSTANCE;}});
  current.invoke(50);ContinuationResults.beginRender(owner);View.drain();ok(paints.isEmpty(),"new render between image completion and final post blocks old strokes");
  Function1<Integer,Unit> detached=ContinuationResults.guardImages(owner,new Function1<Integer,Unit>(){public Unit invoke(Integer height){paints.add("detached image");return Unit.INSTANCE;}});
  ContinuationResults.postRender(owner,target,new Runnable(){public void run(){paints.add("detached post");}});owner.detach();detached.invoke(30);View.drain();ok(paints.isEmpty(),"detached result cannot receive old paint jobs");
  owner.attach();ContinuationResults.beginRender(owner);ContinuationResults.guardImages(owner,new Function1<Integer,Unit>(){public Unit invoke(Integer height){paints.add("current image "+height);return Unit.INSTANCE;}}).invoke(70);
  ok(paints.equals(Arrays.asList("current image 70")),"current native callback retains original argument and behavior");
 }
}
