package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.fragment.app.Fragment;
import com.nice.room.ui.RoomLiveActivity;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskHandwriteFragment;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Question-local actions, inserted into the same parent as the question. */
public final class QuestionActions {
    private static final WeakHashMap<View,WeakReference<State>> STATES=new WeakHashMap<View,WeakReference<State>>();
    static Activity activity(Context c){
        for(int i=0;c!=null && i<32;i++){if(c instanceof Activity)return (Activity)c;
            if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;}
        return null;
    }
    static int dp(View v,int n){return Math.round(v.getResources().getDisplayMetrics().density*n);}
    public static void install(Fragment fragment,View root){
        if(fragment==null || root==null)return;Activity a=activity(root.getContext());
        if(a==null || !a.getClass().getName().equals("com.nice.room.ui.RoomLiveActivity"))return;
        WeakReference<State> ref=STATES.get(root);if(ref!=null && ref.get()!=null)return;
        State s=new State(fragment,root);STATES.put(root,new WeakReference<State>(s));root.addOnAttachStateChangeListener(s);
        if(root.isAttachedToWindow())root.post(s);
    }
    public static void visibility(View root,boolean visible){
        WeakReference<State> ref=STATES.get(root);State s=ref==null?null:ref.get();if(s!=null){s.allowed=visible;s.sync();}
    }
    static TextView button(View root,String title,int icon){
        TextView b=new TextView(root.getContext());b.setText(title);b.setContentDescription(title);b.setTextColor(Color.WHITE);b.setTextSize(14);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setPadding(dp(root,16),dp(root,8),dp(root,16),dp(root,8));b.setMinHeight(dp(root,48));b.setFocusable(true);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0x99000000);bg.setCornerRadius(dp(root,26));b.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),bg,null));
        Drawable d=root.getContext().getDrawable(icon).mutate();d.setTint(Color.WHITE);d.setBounds(0,0,dp(root,20),dp(root,20));b.setCompoundDrawables(d,null,null,null);b.setCompoundDrawablePadding(dp(root,6));return b;
    }
    private static final class State implements View.OnAttachStateChangeListener,ViewTreeObserver.OnGlobalLayoutListener,Runnable {
        final WeakReference<Fragment> fragment;final WeakReference<View> root;
        LinearLayout entry;WeakReference<ViewTreeObserver> tree;boolean allowed=true;
        State(Fragment f,View v){fragment=new WeakReference<Fragment>(f);root=new WeakReference<View>(v);}
        public void run(){
            final View v=root.get();final Fragment f=fragment.get();if(v==null || f==null || !v.isAttachedToWindow() || !(v.getParent() instanceof FrameLayout))return;
            FrameLayout parent=(FrameLayout)v.getParent();int id=parent.getId();
            int question=v.getResources().getIdentifier("fragment_container","id",v.getContext().getPackageName());
            int practice=v.getResources().getIdentifier("fl_do_work","id",v.getContext().getPackageName());
            if(id!=question && id!=practice)return;if(entry!=null){sync();return;}
            entry=new LinearLayout(v.getContext());entry.setTag("mod_question_actions");entry.setGravity(Gravity.CENTER_VERTICAL);
            TextView ai=button(v,"AI 问问",android.R.drawable.ic_menu_help);entry.addView(ai,new LinearLayout.LayoutParams(-2,-2));
            ai.setOnClickListener(new View.OnClickListener(){public void onClick(View ignored){
                if(!v.isAttachedToWindow() || v.getVisibility()!=View.VISIBLE)return;
                final Activity a=activity(v.getContext());if(a==null || a.isFinishing() || a.isDestroyed())return;
                AiPicker.show(a,new AiPicker.OfficialAction(){public void openOfficial(){if(a instanceof RoomLiveActivity)((RoomLiveActivity)a).modOpenLocalApp(0);}});
            }});
            if(f instanceof QuestionAskHandwriteFragment){
                TextView photo=button(v,"拍照／相册",android.R.drawable.ic_menu_camera);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,-2);p.leftMargin=dp(v,8);entry.addView(photo,p);
                photo.setOnClickListener(new View.OnClickListener(){public void onClick(View ignored){if(v.isAttachedToWindow() && v.getVisibility()==View.VISIBLE)QuestionMedia.open((QuestionAskHandwriteFragment)f);}});
            }
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,-2,Gravity.BOTTOM|Gravity.START);p.setMarginStart(dp(v,12));p.setMarginEnd(dp(v,12));p.bottomMargin=dp(v,id==practice?132:74);parent.addView(entry,p);
            ViewTreeObserver observer=v.getViewTreeObserver();tree=new WeakReference<ViewTreeObserver>(observer);if(observer.isAlive())observer.addOnGlobalLayoutListener(this);sync();
        }
        void sync(){View v=root.get();Fragment f=fragment.get();if(f instanceof com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskAnswerBaseFragment)HandwriteContinuation.stateChanged((com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskAnswerBaseFragment)f);if(entry!=null)entry.setVisibility(allowed && v!=null && v.isShown() && !QuestionOverlayLayout.covered(v)?View.VISIBLE:View.GONE);if(v!=null && entry!=null && entry.getParent() instanceof View)QuestionOverlayLayout.place(v,entry,QuestionOverlayLayout.START,dp(v,((View)entry.getParent()).getId()==v.getResources().getIdentifier("fl_do_work","id",v.getContext().getPackageName())?132:74));}
        public void onGlobalLayout(){sync();}
        void detach(){QuestionOverlayLayout.remove(entry);if(entry!=null && entry.getParent() instanceof ViewGroup)((ViewGroup)entry.getParent()).removeView(entry);entry=null;
            ViewTreeObserver observer=tree==null?null:tree.get();if(observer!=null && observer.isAlive())observer.removeOnGlobalLayoutListener(this);tree=null;}
        public void onViewAttachedToWindow(View v){v.post(this);}
        public void onViewDetachedFromWindow(View v){v.removeCallbacks(this);detach();}
    }
}
