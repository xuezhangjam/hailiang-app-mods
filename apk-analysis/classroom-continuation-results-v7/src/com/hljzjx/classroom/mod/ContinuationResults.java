package com.hljzjx.classroom.mod;

import android.view.View;
import android.widget.RadioGroup;
import com.hailiang.common.util.AppRuntimeData;
import com.hailiang.xxb.room.server.entity.ask.HandwriteAskShowAnswer;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskAnswerBaseFragment;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskHandwriteFragment;
import com.nice.room.ui.roomlive.fragment.ask.data.QuestionAskSubmitItem;
import com.nice.room.ui.roomlive.fragment.ask.result.QuestionAskAnswerResultBaseFragment;
import com.nice.room.ui.roomlive.fragment.ask.result.QuestionAskHandwriteResultFragment;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.WeakHashMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Result projection of an API-accepted upload, separate from the live draft.
 * The native result endpoint is refreshed as well. A projected handwriting
 * version is explicitly labelled; it never asserts a new grade or exhibition.
 * All entry points, like the original view callbacks, run on the UI thread. */
public final class ContinuationResults {
    private static final WeakHashMap<QuestionAskAnswerBaseFragment,Snapshot> ACCEPTED=new WeakHashMap<QuestionAskAnswerBaseFragment,Snapshot>();
    private static final WeakHashMap<QuestionAskAnswerResultBaseFragment,Binding> BINDINGS=new WeakHashMap<QuestionAskAnswerResultBaseFragment,Binding>();
    private static final WeakHashMap<View,Object> RENDERS=new WeakHashMap<View,Object>();
    private ContinuationResults(){}

    /** Capture before calling replyAsk, but do not publish until its true callback. */
    static Snapshot capture(QuestionAskAnswerBaseFragment answer,List<QuestionAskSubmitItem> items,int seconds){
        String strokes=null;
        if(answer instanceof QuestionAskHandwriteFragment && items.size()==1)strokes=items.get(0).getAnswerContent();
        return new Snapshot(answer,strokes,seconds);
    }
    static void accepted(QuestionAskAnswerBaseFragment answer,Snapshot snapshot){
        if(snapshot==null || !snapshot.matches(answer))return;
        ACCEPTED.put(answer,snapshot);
        for(Binding b:new ArrayList<Binding>(BINDINGS.values()))if(b.answer.get()==answer)b.schedule();
    }
    static void bind(QuestionAskAnswerBaseFragment answer,QuestionAskAnswerResultBaseFragment result,View root){
        if(answer==null || result==null || root==null)return;
        Binding old=BINDINGS.get(result);
        if(old!=null && old.answer.get()==answer && old.root.get()==root){if(ACCEPTED.containsKey(answer))old.schedule();return;}
        if(old!=null)old.cancel();
        if(answer.getAskId()!=result.getAskId() || answer.getClassroomId()!=result.getClassroomId())return;
        Binding b=new Binding(answer,result,root);BINDINGS.put(result,b);
        if(ACCEPTED.containsKey(answer))b.schedule();
    }
    static void showing(QuestionAskAnswerResultBaseFragment result){
        Binding b=BINDINGS.get(result);if(b!=null && ACCEPTED.containsKey(b.answer.get()))b.schedule();
    }
    static void unbind(QuestionAskAnswerResultBaseFragment result){Binding b=BINDINGS.remove(result);if(b!=null)b.cancel();}
    static void close(QuestionAskAnswerBaseFragment answer){
        ACCEPTED.remove(answer);
        for(QuestionAskAnswerResultBaseFragment result:new ArrayList<QuestionAskAnswerResultBaseFragment>(BINDINGS.keySet())){
            Binding b=BINDINGS.get(result);if(b!=null && b.answer.get()==answer)unbind(result);
        }
    }

    /** Native updateAnswer hook: only "My answer", only the matching upload.
     * No global LiveData mutation and no changes to excellent answers. */
    public static HandwriteAskShowAnswer resolve(QuestionAskHandwriteResultFragment result,HandwriteAskShowAnswer original,boolean mine){
        if(!mine)return original;
        Binding b=BINDINGS.get(result);if(b==null || !b.current())return original;
        QuestionAskAnswerBaseFragment answer=b.answer.get();
        if(original!=null && original.getUserId()!=b.user)return original;
        b.original=original;
        Snapshot s=ACCEPTED.get(answer);
        if(s==null || s.strokes==null || !s.matches(answer) || original==null
                || original.getAskType().getValue()!=s.type)return original;
        if(s.strokes.equals(original.getStudentStrokes()))return original;
        if(b.projectedBase!=original || b.projectedSnapshot!=s){
            List<String> images=original.getImages()==null?null:Collections.unmodifiableList(new ArrayList<String>(original.getImages()));
            String time="本次续答已上传 · "+String.format(Locale.ROOT,"%02d:%02d",s.seconds/60,s.seconds%60);
            b.projected=new HandwriteAskShowAnswer(s.user,original.getUsername(),original.getAskType(),images,s.strokes,null,time,false);
            b.projectedBase=original;b.projectedSnapshot=s;
        }
        return b.projected;
    }
    private static boolean mineSelected(View root){
        int group=root.getResources().getIdentifier("rgAnswer","id",root.getContext().getPackageName());
        int mine=root.getResources().getIdentifier("btnMyAnswer","id",root.getContext().getPackageName());
        View v=group==0?null:root.findViewById(group);
        return mine!=0 && v instanceof RadioGroup && ((RadioGroup)v).getCheckedRadioButtonId()==mine;
    }
    static final class Snapshot {
        final WeakReference<View> root;final long user,room,ask;final int type,seconds;final String strokes;
        Snapshot(QuestionAskAnswerBaseFragment a,String content,int elapsed){root=new WeakReference<View>(a.getView());user=AppRuntimeData.getUserId();room=a.getClassroomId();ask=a.getAskId();type=a.getAskType();seconds=elapsed;strokes=content;}
        boolean matches(QuestionAskAnswerBaseFragment a){return a!=null && user>0 && user==AppRuntimeData.getUserId()
                && a.getView()==root.get() && a.getClassroomId()==room && a.getAskId()==ask && a.getAskType()==type && HandwriteContinuation.isLocal(a);}
    }
    private static final class Binding implements Runnable {
        final WeakReference<QuestionAskAnswerBaseFragment> answer;final WeakReference<QuestionAskAnswerResultBaseFragment> result;
        final WeakReference<View> root,draft;final long user,ask,room;
        HandwriteAskShowAnswer original,projectedBase,projected;Snapshot projectedSnapshot;boolean queued;
        Binding(QuestionAskAnswerBaseFragment a,QuestionAskAnswerResultBaseFragment r,View view){answer=new WeakReference<QuestionAskAnswerBaseFragment>(a);result=new WeakReference<QuestionAskAnswerResultBaseFragment>(r);root=new WeakReference<View>(view);draft=new WeakReference<View>(a.getView());user=AppRuntimeData.getUserId();ask=a.getAskId();room=a.getClassroomId();}
        boolean current(){QuestionAskAnswerBaseFragment a=answer.get();QuestionAskAnswerResultBaseFragment r=result.get();View v=root.get(),d=draft.get();
            return a!=null && r!=null && BINDINGS.get(r)==this && v!=null && d!=null && user>0 && user==AppRuntimeData.getUserId()
                && a.getView()==d && r.getView()==v && d.isAttachedToWindow() && v.isAttachedToWindow() && d.getParent()==v.getParent()
                && a.getAskId()==ask && r.getAskId()==ask && a.getClassroomId()==room && r.getClassroomId()==room;}
        void schedule(){View v=root.get();if(!queued && v!=null){queued=true;v.post(this);}}
        void cancel(){View v=root.get();if(v!=null)v.removeCallbacks(this);queued=false;}
        public void run(){queued=false;if(!current())return;QuestionAskAnswerBaseFragment a=answer.get();Snapshot s=ACCEPTED.get(a);
            if(s==null || !s.matches(a))return;QuestionAskAnswerResultBaseFragment r=result.get();View v=root.get();
            // Repaint immediately from the accepted request, then ask the
            // original read endpoint for its persisted answer/review metadata.
            if(r instanceof QuestionAskHandwriteResultFragment && original!=null && mineSelected(v))
                ((QuestionAskHandwriteResultFragment)r).updateAnswer(original,true);
            try{r.refreshData();}catch(RuntimeException unavailable){/* Upload success remains distinct from readback availability. */}
        }
    }

    /** Original result rendering posts strokes and awaits image downloads.
     * A newer result/tab must invalidate those older paint callbacks. */
    public static void beginRender(View owner){RENDERS.put(owner,new Object());}
    private static boolean currentRender(View owner,Object token){return owner!=null && owner.isAttachedToWindow() && RENDERS.get(owner)==token;}
    public static boolean postRender(View owner,View target,final Runnable job){
        final Object token=RENDERS.get(owner);if(token==null)return target.post(job);
        final WeakReference<View> weak=new WeakReference<View>(owner);
        return target.post(new Runnable(){public void run(){if(currentRender(weak.get(),token))job.run();}});
    }
    public static Function1<Integer,Unit> guardImages(View owner,final Function1<Integer,Unit> callback){
        final Object token=RENDERS.get(owner);final WeakReference<View> weak=new WeakReference<View>(owner);
        return new Function1<Integer,Unit>(){public Unit invoke(Integer height){return currentRender(weak.get(),token)?callback.invoke(height):Unit.INSTANCE;}};
    }
}
