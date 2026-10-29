package com.hljzjx.classroom.mod;

import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;
import com.hailiang.common.util.AppRuntimeData;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskAnswerBaseFragment;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskOptionFragment;
import com.nice.room.ui.roomlive.fragment.ask.data.QuestionAskSubmitAnswer;
import com.nice.room.ui.roomlive.fragment.ask.data.QuestionAskSubmitItem;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Handwriting and single/multiple choice continuation upload through the original logged-in classroom API.
 * Neither the countdown nor submitted/finished flags are reset. */
public final class HandwriteCloud {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final WeakHashMap<QuestionAskAnswerBaseFragment,Request> PENDING=new WeakHashMap<QuestionAskAnswerBaseFragment,Request>();
    private static final WeakHashMap<QuestionAskAnswerBaseFragment,String> ACCEPTED=new WeakHashMap<QuestionAskAnswerBaseFragment,String>();
    private HandwriteCloud(){}
    public static boolean busy(QuestionAskAnswerBaseFragment answer){return PENDING.containsKey(answer);}
    private static void toast(QuestionAskAnswerBaseFragment a,String text){if(a.getContext()!=null)Toast.makeText(a.getContext(),text,Toast.LENGTH_LONG).show();}
    private static TextView submit(QuestionAskAnswerBaseFragment answer){View v=answer.getView();if(v==null)return null;int id=v.getResources().getIdentifier("btn_submit","id",v.getContext().getPackageName());View b=id==0?null:v.findViewById(id);return b instanceof TextView?(TextView)b:null;}
    public static void upload(final QuestionAskAnswerBaseFragment answer){
        if(!HandwriteContinuation.canUpload(answer))return;
        if(answer.modIsSubmitting()){toast(answer,"原答案正在提交，请稍后上传续答");return;}
        if(PENDING.containsKey(answer))return;
        final long user=AppRuntimeData.getUserId();
        if(user<=0)return;
        final QuestionAskSubmitAnswer body;final String signature;final ContinuationResults.Snapshot resultSnapshot;
        try{
            List<QuestionAskSubmitItem> source=answer.getAskItemInfos();
            if(source==null || source.isEmpty()){toast(answer,answer instanceof QuestionAskOptionFragment?"请先选择答案":"请先写入答案");return;}
            long start=answer.getStartAnswerTime(),elapsed=start>0?Math.max(0,(System.currentTimeMillis()-start)/1000):0;
            int seconds=(int)Math.min(Integer.MAX_VALUE,elapsed);
            boolean choice=answer instanceof QuestionAskOptionFragment;boolean selected=false;
            List<Long> titles=choice?answer.getAskQuestionIds():null;
            if(choice && (titles==null || titles.isEmpty())){toast(answer,"题目信息已变化，请重新打开当前题目");return;}
            ArrayList<QuestionAskSubmitItem> snapshot=new ArrayList<QuestionAskSubmitItem>();StringBuilder fingerprint=new StringBuilder();
            java.util.HashSet<Long> seen=new java.util.HashSet<Long>();
            for(QuestionAskSubmitItem item:source){
                if(item==null || item.getStudentId()!=user || !seen.add(Long.valueOf(item.getAskTitleId())) || (choice && !titles.contains(Long.valueOf(item.getAskTitleId())))){
                    toast(answer,"账号或题目已变化，请重新打开当前题目");return;
                }
                String content=item.getAnswerContent();
                List<Integer> options=item.getAnswerOptionList()==null?null:new ArrayList<Integer>(item.getAnswerOptionList());
                if(choice){
                    if(options==null)options=new ArrayList<Integer>();
                    for(Integer option:options)if(option==null || option.intValue()<0){toast(answer,"读取选项失败，当前答案仍保留");return;}
                    if(!options.isEmpty())selected=true;
                    // Native answerMap iteration order is not stable. Sort only
                    // the snapshot and fingerprint, never the live answerMap.
                    java.util.Collections.sort(options);
                }else if(content==null || content.trim().length()==0){toast(answer,"请先写入答案");return;}
                snapshot.add(new QuestionAskSubmitItem(item.getAskTitleId(),options,content,seconds,0,user));
            }
            if(choice && !selected){toast(answer,"请先选择答案");return;}
            java.util.Collections.sort(snapshot,new java.util.Comparator<QuestionAskSubmitItem>(){public int compare(QuestionAskSubmitItem a,QuestionAskSubmitItem b){return Long.compare(a.getAskTitleId(),b.getAskTitleId());}});
            for(QuestionAskSubmitItem item:snapshot){String content=item.getAnswerContent();
                fingerprint.append(item.getAskTitleId()).append('\u0000').append(item.getAnswerOptionList()).append('\u0000').append(content==null?-1:content.length()).append(':').append(content).append('\u0000');}
            body=new QuestionAskSubmitAnswer(answer.getClassroomId(),snapshot);
            resultSnapshot=ContinuationResults.capture(answer,snapshot,seconds);
            signature=answer.getClassroomId()+"/"+answer.getAskId()+"/"+user+"/"+fingerprint.toString();
            if(signature.equals(ACCEPTED.get(answer))){toast(answer,"这份续答已被服务器接受");return;}
        }catch(RuntimeException invalid){toast(answer,"读取续答失败，当前内容仍保留");return;}
        HandwriteContinuation.saveDraft(answer);
        final Request request=new Request(answer,body,signature,user,resultSnapshot);PENDING.put(answer,request);
        TextView button=submit(answer);if(button!=null){button.setText("上传中…");button.setEnabled(false);button.setAlpha(.5f);}
        MAIN.postDelayed(request,45000);
        try{answer.getViewModel().replyAsk(answer.getAskType(),body,new Function1<Boolean,Unit>(){
            public Unit invoke(final Boolean success){MAIN.post(new Runnable(){public void run(){complete(request,Boolean.TRUE.equals(success),false);}});return Unit.INSTANCE;}
        });}catch(RuntimeException unavailable){complete(request,false,false);}
    }
    private static void complete(Request request,boolean success,boolean timeout){
        QuestionAskAnswerBaseFragment answer=request.answer.get();if(answer==null || PENDING.get(answer)!=request)return;
        PENDING.remove(answer);MAIN.removeCallbacks(request);
        if(AppRuntimeData.getUserId()!=request.user || answer.getAskId()!=request.askId || answer.getClassroomId()!=request.roomId || answer.getView()!=request.root.get() || !HandwriteContinuation.isLocal(answer))return;
        if(success){
            ACCEPTED.put(answer,request.signature);
            ContinuationResults.accepted(answer,request.resultSnapshot);
            try{answer.replyAskMqtt(request.body);toast(answer,"服务器已接受本次续答");}
            catch(RuntimeException notifyFailed){toast(answer,"服务器已接受续答，课堂通知未发送");}
        }else toast(answer,timeout?"服务器响应超时，是否保存尚未确认；当前内容已保留":"续答上传未成功，当前内容已保留，可重试");
        HandwriteContinuation.stateChanged(answer);
    }
    public static void close(QuestionAskAnswerBaseFragment answer){Request r=PENDING.remove(answer);if(r!=null)MAIN.removeCallbacks(r);ACCEPTED.remove(answer);ContinuationResults.close(answer);}
    private static final class Request implements Runnable {
        final WeakReference<QuestionAskAnswerBaseFragment> answer;final WeakReference<View> root;final QuestionAskSubmitAnswer body;final String signature;final long user,askId,roomId;final ContinuationResults.Snapshot resultSnapshot;
        Request(QuestionAskAnswerBaseFragment a,QuestionAskSubmitAnswer b,String s,long u,ContinuationResults.Snapshot snapshot){answer=new WeakReference<QuestionAskAnswerBaseFragment>(a);root=new WeakReference<View>(a.getView());body=b;signature=s;user=u;askId=a.getAskId();roomId=a.getClassroomId();resultSnapshot=snapshot;}
        public void run(){complete(this,false,true);}
    }
}
