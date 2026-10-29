import java.util.*;import android.view.*;import android.widget.*;import android.os.Handler;import androidx.fragment.app.*;
import com.nice.room.ui.*;import com.nice.room.ui.roomlive.fragment.ask.answer.*;import com.nice.room.ui.roomlive.fragment.ask.result.*;import com.nice.room.ui.roomlive.fragment.ask.data.*;import com.hailiang.common.util.AppRuntimeData;import com.hljzjx.classroom.mod.*;
public class ChoiceCloudTest {
 static int checks;static void ok(boolean b,String m){checks++;if(!b)throw new AssertionError(m);}
 static TextView find(View v,String text){if(v.visibility!=View.VISIBLE)return null;if(v instanceof TextView && text.equals(((TextView)v).text))return (TextView)v;if(v instanceof ViewGroup)for(View child:((ViewGroup)v).children){TextView b=find(child,text);if(b!=null)return b;}return null;}
 static class Page {
  RoomLiveActivity a=new RoomLiveActivity();FrameLayout host=new FrameLayout(a),root=new FrameLayout(a),result;TextView submit=new TextView(a);QuestionAskOptionFragment f=new QuestionAskOptionFragment();
  Page(int type){AppRuntimeData.user=17;host.id=200;host.clickable=true;host.attach();host.addView(root,new FrameLayout.LayoutParams(-1,-1,0));submit.id=500;submit.left=1188;submit.top=24;submit.width=72;submit.height=40;submit.setText("提交");root.addView(submit,new FrameLayout.LayoutParams(72,40,0));f.root=root;f.askType=type;f.titles.addAll(Arrays.asList(801L,802L,803L));f.items.add(new QuestionAskSubmitItem(801,new ArrayList<>(Arrays.asList(1)),null,0,1,17));QuestionPptReturn.install(root);QuestionActions.install(f,root);View.drain();}
  void end(boolean paired){f.finished=true;if(paired){f.submitted=true;HandwriteContinuation.removeOrKeep(new FragmentTransaction(),f);result=new FrameLayout(a);host.addView(result,new FrameLayout.LayoutParams(-1,-1,0));QuestionAskAnswerResultBaseFragment r=new QuestionAskAnswerResultBaseFragment();r.ask=f.ask;r.room=f.room;r.root=result;QuestionPptReturn.install(result);QuestionActions.install(r,result);HandwriteContinuation.attachResult(r,result);}HandwriteContinuation.stateChanged(f);View.drain();}
  void edit(boolean paired){end(paired);TextView start=find(host,"继续作答");ok(start!=null,"ended single/multiple choice exposes continuation");start.performClick();View.drain();ok(root.visibility==View.VISIBLE && "上传续答".equals(submit.text),"continuation returns to the original choices and exposes upload");}
  void close(){host.removeView(root);if(result!=null)host.removeView(result);View.drain();AppRuntimeData.user=17;}
 }
 public static void main(String[] args){
  for(int type:new int[]{1,2})for(boolean paired:new boolean[]{false,true}){
   Page p=new Page(type);ok(!HandwriteContinuation.consumeSubmit(p.f),"active choices keep original submission");p.edit(paired);
   p.f.items.get(0).options.clear();p.f.items.get(0).options.add(type==1?3:2);if(type==2)p.f.items.get(0).options.add(4);
   p.f.items.add(new QuestionAskSubmitItem(802,new ArrayList<>(Arrays.asList(2)),null,0,1,17));long started=p.f.started;
   p.submit.performClick();ok(p.f.model.sent.size()==1 && p.f.model.types.get(0)==type,"edited choice list reaches original replyAsk with original type");
   QuestionAskSubmitAnswer body=p.f.model.sent.get(0);ok(body.room==p.f.room && body.list.size()==2 && body.list.get(0).options.equals(type==1?Arrays.asList(3):Arrays.asList(2,4)),"new selections, question IDs and complete native items are uploaded");
   ok(body.list.get(0).user==17 && body.list.get(0).content==null && body.list.get(0).auto==0 && body.list.get(0).seconds>=600,"choice payload preserves null stroke content, user and actual late elapsed time");
   ok(p.f.started==started && p.f.finished && p.f.submitted==paired,"no countdown/submitted flag falsification");
   ok(!p.submit.enabled && HandwriteCloud.busy(p.f) && p.f.mqttSends==0,"no duplicate upload or premature teacher notification");HandwriteCloud.upload(p.f);ok(p.f.model.sent.size()==1,"pending upload deduplicated");
   p.f.items.get(0).options.clear();p.f.items.get(0).options.add(7);ok(!body.list.get(0).options.contains(7),"upload is a detached snapshot of selections");
   p.f.model.reply(0,false);View.drain();ok(p.submit.enabled && p.f.mqttSends==0 && Toast.messages.get(Toast.messages.size()-1).contains("未成功"),"rejection retains changed options and allows retry");
   p.submit.performClick();p.f.model.reply(1,true);View.drain();ok(p.f.mqttSends==1 && p.f.mqttBody==p.f.model.sent.get(1),"server success then sends original MQTT with exactly the accepted payload");ok(p.submit.enabled && "上传续答".equals(p.submit.text),"accepted choice upload restores action");
   Collections.reverse(p.f.items);p.submit.performClick();ok(p.f.model.sent.size()==2,"native map iteration order does not cause another accepted upload");
   p.f.items.get(0).options.add(6);p.submit.performClick();Handler.timeouts();View.drain();ok(p.submit.enabled && Toast.messages.get(Toast.messages.size()-1).contains("尚未确认"),"timeout does not falsely claim saved or lost");p.f.model.reply(2,true);View.drain();ok(p.f.mqttSends==1,"expired callback cannot notify current lesson");
   p.close();
  }
  Page timer=new Page(1);timer.f.firstSubmitting=true;timer.end(false);TextView start=find(timer.host,"继续作答");ok(start!=null && !start.enabled && !HandwriteContinuation.consumeSubmit(timer.f),"first timer auto-submit remains protected");start.performClick();ok(!HandwriteContinuation.isLocal(timer.f),"continue cannot steal an in-flight original submit");timer.f.firstSubmitting=false;HandwriteContinuation.stateChanged(timer.f);View.drain();start.performClick();View.drain();ok("上传续答".equals(timer.submit.text),"original callback makes choice continuation usable");timer.close();
  Page empty=new Page(2);empty.edit(false);empty.f.items.get(0).options.clear();empty.submit.performClick();ok(empty.f.model.sent.isEmpty() && Toast.messages.get(Toast.messages.size()-1).contains("选择"),"empty options are not silently submitted as a cloud answer");empty.f.items.get(0).options.add(1);empty.f.items.get(0).id=999;empty.submit.performClick();ok(empty.f.model.sent.isEmpty(),"foreign question ID blocked");empty.f.items.get(0).id=801;empty.f.items.get(0).user=99;empty.submit.performClick();ok(empty.f.model.sent.isEmpty(),"other user's choice blocked");empty.close();
  Page account=new Page(1);account.edit(false);account.submit.performClick();AppRuntimeData.user=99;account.f.model.reply(0,true);View.drain();ok(account.f.mqttSends==0 && !HandwriteContinuation.isLocal(account.f),"stale-account callback never publishes a teacher message");account.close();
  Page closed=new Page(1);closed.edit(false);closed.submit.performClick();closed.close();closed.f.model.reply(0,true);View.drain();ok(closed.f.mqttSends==0 && !HandwriteCloud.busy(closed.f) && Handler.delayed.isEmpty(),"detach clears watchdog and stale selection upload notification");
  System.out.println("PASS "+checks+" single/multiple-choice continuation cloud-boundary assertions");
 }
}
