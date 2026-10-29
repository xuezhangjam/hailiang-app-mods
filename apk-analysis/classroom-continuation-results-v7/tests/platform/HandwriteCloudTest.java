import java.util.*;import android.view.*;import android.widget.*;import android.os.Handler;import androidx.fragment.app.*;
import com.nice.room.ui.*;import com.nice.room.ui.roomlive.fragment.ask.answer.*;import com.nice.room.ui.roomlive.fragment.ask.result.*;import com.nice.room.ui.roomlive.fragment.ask.data.*;import com.hailiang.handwrite.widget.*;import com.hailiang.common.util.AppRuntimeData;import com.hljzjx.classroom.mod.*;
public class HandwriteCloudTest {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static TextView named(View v,String text){if(v instanceof TextView && text.equals(((TextView)v).text))return (TextView)v;if(v instanceof ViewGroup)for(View child:((ViewGroup)v).children){TextView b=named(child,text);if(b!=null)return b;}return null;}
 static TextView visible(View v,String text){if(v.visibility!=View.VISIBLE)return null;if(v instanceof TextView && text.equals(((TextView)v).text))return (TextView)v;if(v instanceof ViewGroup)for(View child:((ViewGroup)v).children){TextView b=visible(child,text);if(b!=null)return b;}return null;}
 static class Hand extends QuestionAskHandwriteFragment {
  List<QuestionAskSubmitItem> items=new ArrayList<>();public List getAskItemInfos(){if(broken)throw new IllegalStateException();return items;}
 }
 static class Page {
  RoomLiveActivity a=new RoomLiveActivity();FrameLayout host=new FrameLayout(a),root=new FrameLayout(a);TextView submit=new TextView(a);Hand f=new Hand();RoomToolBarViewFake toolbar=new RoomToolBarViewFake(a);
  Page(){AppRuntimeData.user=17;host.id=200;host.clickable=true;host.attach();host.addView(root,new FrameLayout.LayoutParams(-1,-1,0));submit.id=500;root.addView(submit,new FrameLayout.LayoutParams(72,40,0));toolbar.id=601;root.addView(toolbar,new FrameLayout.LayoutParams(-2,-2,0));f.root=root;f.canvas=new HandwriteView(a);f.canvas.id=600;root.addView(f.canvas,new FrameLayout.LayoutParams(-1,-1,0));f.items.add(new QuestionAskSubmitItem(801,null,"stroke_xy@1280;800&first",0,1,17));QuestionPptReturn.install(root);QuestionActions.install(f,root);View.drain();}
  void end(boolean pending){f.finished=true;f.firstSubmitting=pending;HandwriteContinuation.stateChanged(f);View.drain();}
  void edit(){end(false);TextView b=visible(host,"继续作答");ok(b!=null && b.enabled,"timer ends with a visible continuation entry even without a result page");b.performClick();View.drain();}
  void done(){host.removeView(root);View.drain();AppRuntimeData.user=17;}
 }
 static class RoomToolBarViewFake extends View{RoomToolBarViewFake(android.content.Context c){super(c);}}
 public static void main(String[] args){
  Page p=new Page();p.end(false);ok(visible(p.host,"继续作答")!=null,"timer end without a result must expose Continue");
  if(args.length>0){p.done();System.out.println("PASS missing-result timer regression");return;}
  ok(p.f.model.sent.isEmpty(),"showing the continuation entry does not upload");
  TextView start=visible(p.host,"继续作答");FrameLayout.LayoutParams at=(FrameLayout.LayoutParams)start.lp;ok(at.topMargin+at.height<=p.host.height-100,"entry is above taskbar and low controls");
  p.f.firstSubmitting=true;HandwriteContinuation.stateChanged(p.f);View.drain();ok(!start.enabled && !HandwriteContinuation.consumeSubmit(p.f),"original first auto-upload remains unconsumed while in flight");start.performClick();ok(!HandwriteContinuation.isLocal(p.f),"early tap cannot take over a pending first upload");
  p.f.firstSubmitting=false;p.f.submitted=true;HandwriteContinuation.stateChanged(p.f);View.drain();Object strokes=p.f.canvas.record;start.performClick();View.drain();
  ok(p.f.canvas.canDraw && p.f.canvas.enabled && p.f.canvas.record==strokes,"continue enables the same canvas and preserves strokes");ok("上传续答".equals(p.submit.text),"native submit location now exposes explicit cloud upload");
  long originalStart=p.f.started;QuestionAskSubmitItem live=p.f.items.get(0);live.content="stroke_xy@1280;800&continued";p.submit.performClick();
  ok(p.f.model.sent.size()==1 && p.f.model.types.get(0)==p.f.askType,"explicit button invokes original ViewModel replyAsk once with original type");
  QuestionAskSubmitAnswer body=p.f.model.sent.get(0);ok(body.room==p.f.room && body.list.get(0).id==801 && body.list.get(0).user==17,"own user, classroom and original question IDs retained");
  ok(body.list.get(0).auto==0 && body.list.get(0).seconds>=600,"manual continuation uses actual elapsed seconds beyond countdown");ok(p.f.finished && p.f.submitted && p.f.started==originalStart,"no timer or submitted/finished reset");
  ok(!p.submit.enabled && HandwriteCloud.busy(p.f) && p.f.mqttSends==0,"upload pending disables duplicates and sends no premature MQTT");HandwriteCloud.upload(p.f);ok(p.f.model.sent.size()==1,"double tap cannot send a second in-flight request");
  live.content="new strokes while request is pending";ok("stroke_xy@1280;800&continued".equals(body.list.get(0).content),"queued upload is an immutable answer snapshot");
  p.f.model.reply(0,true);View.drain();ok(p.f.mqttSends==1 && p.f.mqttBody==body,"only server success sends the original teacher notification");ok(p.submit.enabled && !HandwriteCloud.busy(p.f) && "上传续答".equals(p.submit.text),"accepted callback restores upload control");
  ok(Toast.messages.get(Toast.messages.size()-1).contains("服务器已接受"),"success message follows real API callback");
  live.content="stroke_xy@1280;800&continued";p.submit.performClick();ok(p.f.model.sent.size()==1,"identical accepted snapshot is not re-uploaded");
  live.content="revision two";p.submit.performClick();ok(p.f.model.sent.size()==2,"new strokes can be uploaded again");p.f.model.reply(1,false);View.drain();ok(p.f.mqttSends==1 && p.submit.enabled && p.f.canvas.record==strokes,"server rejection keeps edits, re-enables retry and sends no teacher notification");
  ok(Toast.messages.get(Toast.messages.size()-1).contains("未成功"),"rejection never says saved to cloud");
  p.submit.performClick();ok(p.f.model.sent.size()==3,"explicit retry after rejection");Handler.timeouts();View.drain();ok(!HandwriteCloud.busy(p.f) && p.submit.enabled && Toast.messages.get(Toast.messages.size()-1).contains("尚未确认"),"timeout reports unknown server state and retains contents");
  p.f.model.reply(2,true);View.drain();ok(p.f.mqttSends==1,"stale timed-out callback cannot falsely complete another upload");
  p.f.model.failStart=true;p.submit.performClick();View.drain();ok(p.submit.enabled && !HandwriteCloud.busy(p.f),"synchronous transport failure restores controls");p.f.model.failStart=false;
  p.f.throwMqtt=true;p.submit.performClick();p.f.model.reply(3,true);View.drain();ok(Toast.messages.get(Toast.messages.size()-1).contains("课堂通知未发送"),"accepted upload and notification failure are distinguished");p.f.throwMqtt=false;
  p.submit.performClick();ok(p.f.model.sent.size()==4,"notification failure cannot duplicate an accepted answer");p.done();
  // Late result binding is no longer required for continuation, and it cannot
  // switch the user away from an active continuation when it finally arrives.
  Page q=new Page();q.edit();FrameLayout result=new FrameLayout(q.a);q.host.addView(result,new FrameLayout.LayoutParams(-1,-1,0));QuestionAskHandwriteResultFragment r=new QuestionAskHandwriteResultFragment();r.root=result;r.ask=q.f.ask;r.room=q.f.room;QuestionActions.install(r,result);QuestionPptReturn.setResumeLabel(result,"答题结果");HandwriteContinuation.attachResult(r,result);View.drain();
  ok(q.root.visibility==View.VISIBLE && result.visibility==View.INVISIBLE,"late result preserves current continuation view");TextView resultButton=visible(q.host,"答题结果");ok(resultButton!=null,"result is still accessible");resultButton.performClick();View.drain();ok(result.visibility==View.VISIBLE && q.root.visibility==View.INVISIBLE,"original result can be viewed");
  visible(q.host,"继续作答").performClick();View.drain();q.submit.performClick();resultButton=visible(q.host,"答题结果");resultButton.performClick();View.drain();q.f.model.reply(0,true);View.drain();ok(q.f.mqttSends==1,"switching to result does not drop an accepted upload notification");
  q.host.removeView(result);View.drain();ok(visible(q.host,"继续作答")!=null || visible(q.host,"正在续答")!=null,"removing result retains standalone draft entry");q.done();
  Page active=new Page();ok(visible(active.host,"继续作答")==null && !HandwriteContinuation.consumeSubmit(active.f),"active question keeps first submission and has no premature continuation");active.done();
  Page bad=new Page();bad.edit();bad.f.items.get(0).user=99;bad.submit.performClick();ok(bad.f.model.sent.isEmpty(),"cross-account answer payload is rejected");bad.f.items.clear();bad.submit.performClick();ok(bad.f.model.sent.isEmpty(),"empty answer cannot replace cloud data");bad.f.broken=true;bad.submit.performClick();ok(bad.f.model.sent.isEmpty() && bad.f.canvas.record!=null,"serialization failure keeps editable answer");bad.done();
  Page account=new Page();account.edit();account.submit.performClick();AppRuntimeData.user=99;account.root.tree.global();View.drain();account.f.model.reply(0,true);View.drain();ok(account.f.mqttSends==0 && !HandwriteContinuation.isLocal(account.f),"account change ignores old callback and cannot use old draft");ok(visible(account.host,"继续作答")==null && visible(account.host,"正在续答")==null,"old entry disappears after account change");account.done();
  Page changed=new Page();changed.edit();changed.submit.performClick();changed.f.ask++;changed.f.room++;changed.f.model.reply(0,true);View.drain();ok(changed.f.mqttSends==0 && !HandwriteContinuation.isLocal(changed.f),"changed question or classroom cannot receive an old upload notification");changed.done();
  Page detached=new Page();detached.edit();detached.submit.performClick();detached.done();detached.f.model.reply(0,true);View.drain();ok(detached.f.mqttSends==0 && !HandwriteCloud.busy(detached.f) && Handler.delayed.isEmpty(),"detach clears request watchdog and ignores stale callback");
  System.out.println("PASS "+checks+" timer continuation and cloud-upload boundary assertions");
 }
}
