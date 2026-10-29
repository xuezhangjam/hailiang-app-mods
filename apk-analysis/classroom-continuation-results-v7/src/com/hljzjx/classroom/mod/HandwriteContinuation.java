package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import com.hailiang.common.util.AppRuntimeData;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskHandwriteFragment;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskOptionFragment;
import com.nice.room.ui.roomlive.fragment.ask.result.QuestionAskHandwriteResultFragment;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskAnswerBaseFragment;
import com.nice.room.ui.roomlive.fragment.ask.result.QuestionAskAnswerResultBaseFragment;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Retain the original input and expose continuation independently of results. */
public final class HandwriteContinuation {
    private static final WeakHashMap<Activity,WeakReference<Record>> RECORDS=new WeakHashMap<Activity,WeakReference<Record>>();
    private static final WeakHashMap<View,Long> OWNERS=new WeakHashMap<View,Long>();
    private static Activity activity(View root){
        if(root==null)return null;Context c=root.getContext();
        for(int i=0;c!=null && i<32;i++){
            if(c instanceof Activity)return (Activity)c;
            if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;
        }return null;
    }
    private static Record record(QuestionAskAnswerBaseFragment answer,boolean create){
        View root=answer.getView();Activity a=activity(root);
        if(a==null || root==null || !root.isAttachedToWindow() || !(root.getParent() instanceof FrameLayout)
                || !a.getClass().getName().equals("com.nice.room.ui.RoomLiveActivity"))return null;
        Long user=OWNERS.get(root);if(user!=null && user.longValue()!=AppRuntimeData.getUserId())return null;
        WeakReference<Record> ref=RECORDS.get(a);Record previous=ref==null?null:ref.get();
        if(previous!=null && previous.answer.get()==answer && previous.draft.get()==root && previous.userId==AppRuntimeData.getUserId() && previous.askId==answer.getAskId() && previous.classroomId==answer.getClassroomId())return previous;
        if(!create || AppRuntimeData.getUserId()<=0)return null;
        View old=previous==null?null:previous.draft.get();
        if(old!=null && old.getParent()==root.getParent() && old.isAttachedToWindow()
                && ((ViewGroup)root.getParent()).indexOfChild(root)<((ViewGroup)root.getParent()).indexOfChild(old))return null;
        if(previous!=null)previous.removeEntry();
        OWNERS.put(root,Long.valueOf(AppRuntimeData.getUserId()));Record next=new Record(a,answer,root);RECORDS.put(a,new WeakReference<Record>(next));root.addOnAttachStateChangeListener(next);next.watch();return next;
    }
    /** UI-thread state hook posts behind the original submit/end method, so it
     * cannot swallow the automatic first upload when the timer reaches zero. */
    public static void stateChanged(QuestionAskAnswerBaseFragment answer){
        if(!cloudSupported(answer))return;
        Record r=record(answer,true);if(r!=null)r.request();
    }
    public static boolean cloudSupported(QuestionAskAnswerBaseFragment answer){return answer instanceof QuestionAskHandwriteFragment || answer instanceof QuestionAskOptionFragment;}
    public static boolean canUpload(QuestionAskAnswerBaseFragment answer){Record r=record(answer,false);return r!=null && r.local && r.writing && isLocal(answer);}
    public static boolean hasResult(QuestionAskHandwriteFragment answer){Record r=record(answer,false);return r!=null && r.result!=null && r.result.get()!=null;}
    public static FragmentTransaction removeOrKeep(FragmentTransaction transaction,Fragment fragment){
        if(fragment instanceof QuestionAskAnswerBaseFragment){
            QuestionAskAnswerBaseFragment answer=(QuestionAskAnswerBaseFragment)fragment;
            View root=answer.getView();Activity a=activity(root);
            if(a!=null && a.getClass().getName().equals("com.nice.room.ui.RoomLiveActivity") && root.isAttachedToWindow()
                    && root.getParent() instanceof FrameLayout && (answer.getSubmitted() || answer.getFinished())){
                Record r=record(answer,true);if(r==null)return transaction.remove(fragment);r.local=true;
                r.editable();QuestionPptReturn.setResumeLabel(root,"继续作答");QuestionPptReturn.suspend(root);saveDraft(answer);
                r.request();
                return transaction;
            }
        }
        return transaction.remove(fragment);
    }
    public static void attachResult(QuestionAskHandwriteResultFragment fragment,View root){
        attachResult((QuestionAskAnswerResultBaseFragment)fragment,root);
    }
    public static void attachResult(QuestionAskAnswerResultBaseFragment fragment,View root){
        Activity a=activity(root);if(a==null)return;
        WeakReference<Record> ref=RECORDS.get(a);Record r=ref==null?null:ref.get();
        if(r==null || r.askId!=fragment.getAskId() || r.classroomId!=fragment.getClassroomId())return;
        if(r.result!=null && r.result.get()==root)return;
        r.result=new WeakReference<View>(root);r.resultFragment=new WeakReference<QuestionAskAnswerResultBaseFragment>(fragment);r.resultBound=false;ContinuationResults.bind(r.answer.get(),fragment,root);root.addOnAttachStateChangeListener(r);r.request();
    }
    public static boolean isLocal(QuestionAskAnswerBaseFragment answer){
        View root=answer.getView();Activity a=activity(root);if(a==null || root==null || !root.isAttachedToWindow())return false;
        WeakReference<Record> ref=RECORDS.get(a);Record r=ref==null?null:ref.get();
        return r!=null && r.local && r.answer.get()==answer && r.draft.get()==root && r.userId==AppRuntimeData.getUserId() && r.askId==answer.getAskId() && r.classroomId==answer.getClassroomId() && (answer.getSubmitted() || answer.getFinished());
    }
    public static boolean saveDraft(QuestionAskAnswerBaseFragment answer){
        if(!isLocal(answer))return false;
        persist(answer);return true;
    }
    public static boolean consumeSubmit(QuestionAskAnswerBaseFragment answer){
        if(!isLocal(answer))return false;boolean saved=persist(answer);
        Context c=answer.getContext();if(c!=null)Toast.makeText(c,saved?"已保存本地练习":"本地保存失败，当前页面仍保留练习内容",Toast.LENGTH_SHORT).show();return true;
    }
    private static boolean persist(QuestionAskAnswerBaseFragment answer){
        Context c=answer.getContext();long user=AppRuntimeData.getUserId();if(c==null || user<=0)return false;
        try{
            boolean hand=answer instanceof QuestionAskHandwriteFragment;
            String data=hand?((QuestionAskHandwriteFragment)answer).modExportContinuation():com.alibaba.fastjson.JSON.toJSONString(answer.getAskItemInfos());
            if(data==null)return false;
            c.getSharedPreferences(hand?"mod_handwrite_continuations":"mod_question_continuations",Context.MODE_PRIVATE).edit()
                .putString(user+"_"+answer.getClassroomId()+"_"+answer.getAskId(),data).apply();return true;
        }catch(RuntimeException ignored){return false;}
    }
    public static void saveIfEnded(QuestionAskHandwriteFragment answer){
        if(!(answer.getSubmitted() || answer.getFinished()) || answer.getView()==null)return;
        Context c=answer.getContext();if(c==null)return;
        try{
            long user=AppRuntimeData.getUserId();if(user<=0)return;
            Activity a=activity(answer.getView());WeakReference<Record> ref=a==null?null:RECORDS.get(a);Record r=ref==null?null:ref.get();
            if(r!=null && r.answer.get()==answer && r.userId!=user)return;
            String data=answer.modExportContinuation();if(data==null)return;
            String key=user+"_"+answer.getClassroomId()+"_"+answer.getAskId();
            c.getSharedPreferences("mod_handwrite_continuations",Context.MODE_PRIVATE).edit().putString(key,data).apply();
        }catch(RuntimeException ignored){/* Original submitted-answer storage is untouched. */}
    }
    private static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    private static final class Record implements View.OnAttachStateChangeListener,View.OnClickListener,ViewTreeObserver.OnGlobalLayoutListener,Runnable {
        final WeakReference<Activity> owner;final long askId,classroomId,userId;final WeakReference<View> draft;
        final WeakReference<QuestionAskAnswerBaseFragment> answer;
        WeakReference<View> result;WeakReference<QuestionAskAnswerResultBaseFragment> resultFragment;
        TextView entry;ViewTreeObserver tree;boolean writing,local,queued,resultBound;
        Record(Activity a,QuestionAskAnswerBaseFragment fragment,View view){owner=new WeakReference<Activity>(a);askId=fragment.getAskId();classroomId=fragment.getClassroomId();userId=AppRuntimeData.getUserId();draft=new WeakReference<View>(view);answer=new WeakReference<QuestionAskAnswerBaseFragment>(fragment);}
        boolean current(){Activity a=owner.get();WeakReference<Record> ref=a==null?null:RECORDS.get(a);return ref!=null && ref.get()==this && userId==AppRuntimeData.getUserId();}
        void watch(){View d=draft.get();if(d==null)return;ViewTreeObserver next=d.getViewTreeObserver();if(tree!=next){if(tree!=null && tree.isAlive())tree.removeOnGlobalLayoutListener(this);tree=next;if(tree.isAlive())tree.addOnGlobalLayoutListener(this);}}
        void request(){View d=draft.get();if(!queued && d!=null && d.isAttachedToWindow()){queued=true;d.post(this);}}
        public void onGlobalLayout(){request();}
        void editable(){
            final QuestionAskAnswerBaseFragment fragment=answer.get();View root=draft.get();if(fragment==null || root==null)return;
            int id=root.getResources().getIdentifier("btn_submit","id",root.getContext().getPackageName());View submit=root.findViewById(id);
            if(submit instanceof TextView){
                boolean cloud=writing && cloudSupported(fragment);
                if(cloud && HandwriteCloud.busy(fragment))return;
                String label=cloud?"上传续答":"保存本地练习";if(!label.contentEquals(((TextView)submit).getText()))((TextView)submit).setText(label);
                submit.setEnabled(true);submit.setAlpha(1);
                submit.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(writing && cloudSupported(fragment))HandwriteCloud.upload(fragment);else consumeSubmit(fragment);}});}
        }
        public void run(){
            queued=false;
            View d=draft.get(),r=result==null?null:result.get();
            QuestionAskAnswerBaseFragment f=answer.get();
            if(!current()){removeEntry();return;}if(d==null || f==null || !d.isAttachedToWindow() || !(d.getParent() instanceof FrameLayout))return;
            boolean paired=r!=null && r.isAttachedToWindow() && r.getParent()==d.getParent();
            if(!local && !(f.getSubmitted() || f.getFinished()))return;
            if(local)editable();if(paired)QuestionPptReturn.setResumeLabel(r,"答题结果");
            Activity a=owner.get();if(a!=null)RECORDS.put(a,new WeakReference<Record>(this));
            if(entry==null){
                entry=new TextView(d.getContext());entry.setTag("mod_continue_handwrite");entry.setTextColor(Color.WHITE);entry.setTextSize(14);entry.setTypeface(Typeface.DEFAULT,Typeface.BOLD);entry.setGravity(Gravity.CENTER);entry.setMinHeight(dp(d,48));entry.setPadding(dp(d,16),dp(d,8),dp(d,16),dp(d,8));
                GradientDrawable shape=new GradientDrawable();shape.setColor(0x99000000);shape.setCornerRadius(dp(d,26));entry.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape,null));entry.setOnClickListener(this);
                FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.END);p.setMarginEnd(dp(d,12));p.bottomMargin=dp(d,f instanceof QuestionAskHandwriteFragment?132:12);((FrameLayout)d.getParent()).addView(entry,p);
            }
            if(paired && !resultBound){resultBound=true;
                if(writing){QuestionPptReturn.suspend(r);QuestionPptReturn.resume(d);}else{QuestionPptReturn.suspend(d);QuestionPptReturn.resume(r);}}
            // A timer can end while the original answer view remains onscreen,
            // or before its result fragment is created. The entry still exists.
            update();
        }
        private void update(){if(entry!=null){
            View d=draft.get(),r=result==null?null:result.get();QuestionAskAnswerBaseFragment f=answer.get();
            boolean paired=r!=null && r.isAttachedToWindow() && d!=null && r.getParent()==d.getParent();
            String label=writing?(paired?"答题结果":"正在续答"):"继续作答";
            if(!label.contentEquals(entry.getText()))entry.setText(label);
            entry.setContentDescription(writing?"查看作答结果":"返回原画布继续作答");
            boolean waiting=cloudSupported(f) && f.modIsSubmitting();
            entry.setEnabled(!waiting && (!writing || paired));entry.setAlpha(waiting?.5f:1);
            boolean visible=current() && d!=null && !ClassroomPip.active(d.getContext());
            if(visible && d.getParent() instanceof ViewGroup){ViewGroup host=(ViewGroup)d.getParent();int target=Math.max(host.indexOfChild(d),paired?host.indexOfChild(r):-1);
                for(int i=target+1;i<host.getChildCount();i++){View v=host.getChildAt(i);Object tag=v.getTag();if(v!=entry && v.getVisibility()==View.VISIBLE && !(tag instanceof String && ((String)tag).startsWith("mod_"))){visible=false;break;}}}
            int visibility=visible?View.VISIBLE:View.GONE;if(entry.getVisibility()!=visibility)entry.setVisibility(visibility);
            if(visible && entry.getParent() instanceof ViewGroup){ViewGroup host=(ViewGroup)entry.getParent();if(host.indexOfChild(entry)!=host.getChildCount()-1)entry.bringToFront();}
            if(d!=null)QuestionOverlayLayout.place(d,entry,QuestionOverlayLayout.END,dp(d,f instanceof QuestionAskHandwriteFragment?132:12));
        }}
        public void onClick(View ignored){
            View d=draft.get(),r=result==null?null:result.get();
            if(!current() || d==null || !d.isAttachedToWindow()){clear();return;}
            QuestionAskAnswerBaseFragment f=answer.get();if(f==null)return;
            if(cloudSupported(f) && f.modIsSubmitting()){Toast.makeText(d.getContext(),"原答案正在提交，请稍后继续",Toast.LENGTH_SHORT).show();return;}
            boolean paired=r!=null && r.isAttachedToWindow() && r.getParent()==d.getParent();if(writing && !paired)return;
            local=true;saveDraft(f);writing=!writing;
            if(writing){if(paired)QuestionPptReturn.suspend(r);QuestionPptReturn.resume(d);
                if(f instanceof QuestionAskHandwriteFragment){com.hailiang.handwrite.widget.HandwriteView canvas=((QuestionAskHandwriteFragment)f).modGetCanvas();if(canvas!=null){canvas.setCanDraw(true);canvas.setEnabled(true);}int id=d.getResources().getIdentifier("handwrite_toolbar","id",d.getContext().getPackageName());View bar=id==0?null:d.findViewById(id);if(bar!=null)bar.setVisibility(View.VISIBLE);}}
            else{QuestionPptReturn.suspend(d);QuestionPptReturn.resume(r);QuestionAskAnswerResultBaseFragment resultOwner=resultFragment==null?null:resultFragment.get();if(resultOwner!=null)ContinuationResults.showing(resultOwner);}
            editable();update();
        }
        private void removeEntry(){
            QuestionOverlayLayout.remove(entry);
            if(entry!=null && entry.getParent() instanceof ViewGroup)((ViewGroup)entry.getParent()).removeView(entry);entry=null;
        }
        private void clear(){
            removeEntry();
            View d=draft.get(),r=result==null?null:result.get();if(d!=null){d.removeCallbacks(this);d.removeOnAttachStateChangeListener(this);}if(r!=null)r.removeOnAttachStateChangeListener(this);
            if(tree!=null && tree.isAlive())tree.removeOnGlobalLayoutListener(this);tree=null;queued=false;
            QuestionAskAnswerBaseFragment f=answer.get();if(cloudSupported(f))HandwriteCloud.close(f);
            Activity a=owner.get();WeakReference<Record> ref=a==null?null:RECORDS.get(a);if(ref!=null && ref.get()==this)RECORDS.remove(a);
        }
        public void onViewAttachedToWindow(View v){watch();request();}
        public void onViewDetachedFromWindow(View v){v.removeCallbacks(this);queued=false;if(v==draft.get())clear();else{removeEntry();if(result!=null && result.get()==v){QuestionAskAnswerResultBaseFragment owner=resultFragment==null?null:resultFragment.get();if(owner!=null)ContinuationResults.unbind(owner);result=null;resultFragment=null;resultBound=false;}request();}}
    }
}
