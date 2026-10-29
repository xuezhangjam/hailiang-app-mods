package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Minimize the existing answer view, preserving its answers and timer. */
public final class QuestionPptReturn {
    private static final WeakHashMap<View,WeakReference<State>> STATES=new WeakHashMap<View,WeakReference<State>>();
    private static final WeakHashMap<FrameLayout,Gate> GATES=new WeakHashMap<FrameLayout,Gate>();
    private static final String TAG="mod_question_ppt_return";
    private static Activity owner(Context context){
        for(int i=0;context!=null && i<32;i++){
            if(context instanceof Activity)return (Activity)context;
            if(!(context instanceof ContextWrapper))break;
            Context next=((ContextWrapper)context).getBaseContext();if(next==context)break;context=next;
        }return null;
    }
    private static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    public static void install(View root){
        if(root==null)return;Activity activity=owner(root.getContext());
        if(activity==null || !activity.getClass().getName().equals("com.nice.room.ui.RoomLiveActivity"))return;
        WeakReference<State> ref=STATES.get(root);State previous=ref==null?null:ref.get();
        if(previous!=null)return;
        State state=new State(root);STATES.put(root,new WeakReference<State>(state));
        root.addOnAttachStateChangeListener(state);
        if(root.isAttachedToWindow())root.post(state);
    }
    private static State find(View root){WeakReference<State> ref=STATES.get(root);return ref==null?null:ref.get();}
    public static void installDiscussion(View root){
        install(root);State s=find(root);if(s!=null){s.discussion=true;s.resumeLabel="返回讨论";s.update();}
    }
    public static void setResumeLabel(View root,String label){install(root);State s=find(root);if(s!=null){s.resumeLabel=label;s.update();}}
    /** Used while showing the submitted answer or its original local canvas. */
    public static void suspend(View root){install(root);State s=find(root);if(s!=null)s.change(true,true);}
    public static void resume(View root){State s=find(root);if(s!=null)s.change(false,false);}
    public static void pictureInPicture(Activity activity){
        for(WeakReference<State> ref:STATES.values()){State s=ref.get();if(s!=null && owner(s.root.getContext())==activity)s.update();}
    }
    /** A full-screen clickable practice container must pass PPT touches through
     * while minimized. Share ownership if two answer views overlap briefly. */
    private static final class Gate {
        final WeakReference<FrameLayout> reference;
        final boolean clickable,focusable,touchFocusable;
        int users;
        Gate(FrameLayout parent){reference=new WeakReference<FrameLayout>(parent);clickable=parent.isClickable();focusable=parent.isFocusable();touchFocusable=parent.isFocusableInTouchMode();}
        void acquire(){FrameLayout parent=reference.get();users++;if(parent!=null){parent.setClickable(false);parent.setFocusable(false);}}
        void release(){
            if(users<=0)return;users--;FrameLayout parent=reference.get();
            if(users==0 && parent!=null){parent.setClickable(clickable);parent.setFocusableInTouchMode(touchFocusable);parent.setFocusable(focusable);GATES.remove(parent);}
        }
    }
    private static final class State implements View.OnAttachStateChangeListener,View.OnClickListener,ViewTreeObserver.OnGlobalLayoutListener,Runnable {
        final View root;
        FrameLayout parent;TextView entry,pipEntry;LinearLayout controls;Gate gate;ViewTreeObserver tree;
        boolean minimized,suspended,pending,pendingHide,pendingQuiet,discussion;int savedVisibility;String resumeLabel="继续作答";
        State(View view){root=view;}
        public void run(){
            if(!root.isAttachedToWindow() || !(root.getParent() instanceof FrameLayout))return;
            FrameLayout host=(FrameLayout)root.getParent();
            int practice=root.getResources().getIdentifier("fl_do_work","id",root.getContext().getPackageName());
            int question=root.getResources().getIdentifier("fragment_container","id",root.getContext().getPackageName());
            if(!discussion && host.getId()!=practice && host.getId()!=question)return;
            if(parent==host && entry!=null)return;
            boolean deferred=pending,hide=pendingHide,quiet=pendingQuiet;detach();parent=host;
            entry=new TextView(root.getContext());entry.setTag(TAG);entry.setTextColor(Color.WHITE);entry.setTextSize(14);entry.setTypeface(Typeface.DEFAULT,Typeface.BOLD);entry.setGravity(Gravity.CENTER);entry.setMinHeight(dp(root,48));entry.setPadding(dp(root,16),dp(root,8),dp(root,16),dp(root,8));
            GradientDrawable shape=new GradientDrawable();shape.setColor(0x99000000);shape.setCornerRadius(dp(root,26));
            entry.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape,null));entry.setOnClickListener(this);entry.setFocusable(true);
            // Same container as the question. New teacher overlays are added
            // above this sibling; this control never floats over their windows.
            controls=new LinearLayout(root.getContext());controls.setOrientation(LinearLayout.HORIZONTAL);controls.setTag("mod_question_top_controls");
            controls.addView(entry,new LinearLayout.LayoutParams(-2,-2));
            pipEntry=QuestionActions.button(root,"画中画",android.R.drawable.ic_menu_slideshow);
            LinearLayout.LayoutParams gap=new LinearLayout.LayoutParams(-2,-2);gap.leftMargin=dp(root,8);controls.addView(pipEntry,gap);
            pipEntry.setOnClickListener(new View.OnClickListener(){public void onClick(View v){ClassroomPip.enter(owner(root.getContext()),root);}});
            parent.addView(controls,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.END));update();tree=root.getViewTreeObserver();if(tree.isAlive())tree.addOnGlobalLayoutListener(this);
            if(deferred)change(hide,quiet);
        }
        private void visibility(){
            boolean pip=ClassroomPip.active(root.getContext());
            boolean show=!suspended && !pip && (minimized || root.isShown()) && !QuestionOverlayLayout.covered(root);
            QuestionActions.visibility(root,show && !minimized);
            if(controls!=null){int visibility=show?View.VISIBLE:View.GONE;if(controls.getVisibility()!=visibility)controls.setVisibility(visibility);}
        }
        private void update(){
            visibility();
            if(entry==null || parent==null)return;
            if(pipEntry!=null)pipEntry.setVisibility(minimized?View.GONE:View.VISIBLE);
            entry.setText(minimized?resumeLabel:"返回 PPT");entry.setContentDescription(minimized?resumeLabel:"暂时收起当前页面并返回 PPT");
            Drawable icon=root.getContext().getDrawable(minimized?android.R.drawable.ic_menu_edit:android.R.drawable.ic_menu_slideshow).mutate();icon.setTint(Color.WHITE);icon.setBounds(0,0,dp(root,20),dp(root,20));entry.setCompoundDrawables(icon,null,null,null);entry.setCompoundDrawablePadding(dp(root,6));
            position();
        }
        public void onGlobalLayout(){visibility();position();}
        private void position(){
            if(controls==null || parent==null)return;
            QuestionOverlayLayout.place(root,controls,minimized?QuestionOverlayLayout.END:QuestionOverlayLayout.TOP,minimized?dp(root,74):0);
        }
        public void onClick(View ignored){
            if(parent==null || !root.isAttachedToWindow() || root.getParent()!=parent)return;
            change(!minimized,false);
        }
        void change(boolean hide,boolean quiet){
            if(!root.isAttachedToWindow())return;
            if(parent==null){pending=true;pendingHide=hide;pendingQuiet=quiet;return;}
            if(root.getParent()!=parent)return;
            if(hide && !minimized){
                // Respect a view already hidden or ended by the original app.
                if(root.getVisibility()!=View.VISIBLE)return;
                savedVisibility=root.getVisibility();minimized=true;
                gate=GATES.get(parent);if(gate==null){gate=new Gate(parent);GATES.put(parent,gate);}gate.acquire();
                root.clearFocus();
                Object manager=root.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if(manager instanceof InputMethodManager)((InputMethodManager)manager).hideSoftInputFromWindow(root.getWindowToken(),0);
                root.setVisibility(View.INVISIBLE);
            }else if(!hide && minimized){
                minimized=false;
                if(root.getVisibility()==View.INVISIBLE)root.setVisibility(savedVisibility);
                if(gate!=null){gate.release();gate=null;}
            }
            suspended=quiet;
            update();
        }
        private void detach(){
            if(gate!=null){gate.release();gate=null;}
            if(minimized && root.getVisibility()==View.INVISIBLE)root.setVisibility(savedVisibility);
            minimized=false;suspended=false;pending=false;
            if(tree!=null && tree.isAlive())tree.removeOnGlobalLayoutListener(this);tree=null;
            QuestionOverlayLayout.remove(controls);
            if(controls!=null && controls.getParent() instanceof ViewGroup)((ViewGroup)controls.getParent()).removeView(controls);
            entry=null;pipEntry=null;controls=null;parent=null;
        }
        public void onViewAttachedToWindow(View v){root.post(this);}
        public void onViewDetachedFromWindow(View v){root.removeCallbacks(this);detach();}
    }
}
