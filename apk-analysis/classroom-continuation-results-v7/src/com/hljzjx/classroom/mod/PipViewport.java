package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Keep the live page's logical size while PiP changes its outer window.
 * One outer matrix covers the WebView, teacher ink and student's ink together.
 * No screenshots, WebView zoom or changes to the teacher's inner matrix. */
public final class PipViewport {
    private static final WeakHashMap<ViewGroup,Session> SESSIONS=new WeakHashMap<ViewGroup,Session>();
    private PipViewport(){}
    public static boolean begin(Activity a){
        if(a==null || a.isFinishing() || a.isDestroyed())return false;
        View v=a.findViewById(android.R.id.content);
        if(!(v instanceof ViewGroup))return false;
        ViewGroup parent=(ViewGroup)v;
        if(SESSIONS.containsKey(parent))return true;
        if(parent.getChildCount()==0)return false;
        View root=parent.getChildAt(0);
        if(root.getWidth()<=0 || root.getHeight()<=0 || root.getLayoutParams()==null)return false;
        Session s=new Session(parent,root);SESSIONS.put(parent,s);s.watch();s.fit();return true;
    }
    /** Called before WindowSizing can reflow the content for the tiny window. */
    public static boolean apply(ViewGroup parent,View root){
        Session s=SESSIONS.get(parent);
        if(s==null || s.root.get()!=root)return false;
        s.fit();return true;
    }
    public static void end(Activity a){
        if(a==null)return;
        View v=a.findViewById(android.R.id.content);
        if(!(v instanceof ViewGroup))return;
        Session s=SESSIONS.remove((ViewGroup)v);
        if(s!=null){s.restore();if(!a.isFinishing() && !a.isDestroyed())WindowSizing.install(a);}
    }
    private static final class Session implements View.OnLayoutChangeListener,ViewTreeObserver.OnPreDrawListener {
        final WeakReference<ViewGroup> parent;final WeakReference<View> root;
        final int width,height,lpWidth,lpHeight,gravity,left,top,right,bottom,start,end;
        final float sx,sy,px,py,tx,ty;
        ViewTreeObserver observer;boolean busy;int lastWidth,lastHeight;
        Session(ViewGroup p,View v){
            parent=new WeakReference<ViewGroup>(p);root=new WeakReference<View>(v);
            width=v.getWidth();height=v.getHeight();ViewGroup.LayoutParams lp=v.getLayoutParams();lpWidth=lp.width;lpHeight=lp.height;
            sx=v.getScaleX();sy=v.getScaleY();px=v.getPivotX();py=v.getPivotY();tx=v.getTranslationX();ty=v.getTranslationY();
            gravity=lp instanceof FrameLayout.LayoutParams?((FrameLayout.LayoutParams)lp).gravity:-1;
            ViewGroup.MarginLayoutParams m=lp instanceof ViewGroup.MarginLayoutParams?(ViewGroup.MarginLayoutParams)lp:null;
            left=m==null?0:m.leftMargin;top=m==null?0:m.topMargin;right=m==null?0:m.rightMargin;bottom=m==null?0:m.bottomMargin;
            start=m==null?0:m.getMarginStart();end=m==null?0:m.getMarginEnd();
        }
        void watch(){ViewGroup p=parent.get();if(p==null)return;p.addOnLayoutChangeListener(this);
            observer=p.getViewTreeObserver();if(observer.isAlive())observer.addOnPreDrawListener(this);}
        void fit(){
            ViewGroup p=parent.get();View v=root.get();if(busy || p==null || v==null || v.getParent()!=p)return;
            int w=p.getWidth()-p.getPaddingLeft()-p.getPaddingRight(),h=p.getHeight()-p.getPaddingTop()-p.getPaddingBottom();
            if(w<=0 || h<=0)return;
            busy=true;
            try{
                ViewGroup.LayoutParams lp=v.getLayoutParams();if(lp==null)return;
                boolean layout=lp.width!=width || lp.height!=height;lp.width=width;lp.height=height;
                if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;
                    if(m.leftMargin!=0 || m.topMargin!=0 || m.rightMargin!=0 || m.bottomMargin!=0 || m.getMarginStart()!=0 || m.getMarginEnd()!=0){m.setMargins(0,0,0,0);m.setMarginStart(0);m.setMarginEnd(0);layout=true;}}
                if(lp instanceof FrameLayout.LayoutParams && ((FrameLayout.LayoutParams)lp).gravity!=(Gravity.TOP|Gravity.LEFT)){
                    ((FrameLayout.LayoutParams)lp).gravity=Gravity.TOP|Gravity.LEFT;layout=true;}
                if(layout)v.setLayoutParams(lp);
                float scale=Math.min((float)w/width,(float)h/height);
                v.setPivotX(0);v.setPivotY(0);v.setScaleX(scale);v.setScaleY(scale);
                v.setTranslationX((w-width*scale)*.5f);v.setTranslationY((h-height*scale)*.5f);
                if(p.getScrollX()!=0 || p.getScrollY()!=0)p.scrollTo(0,0);
                if(lastWidth!=w || lastHeight!=h){lastWidth=w;lastHeight=h;
                    Log.i("ClassroomPip","fit logical="+width+"x"+height+" window="+w+"x"+h+" scale="+scale);}
            }finally{busy=false;}
        }
        public void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int orr,int ob){fit();}
        public boolean onPreDraw(){fit();View v=root.get();
            // Wait one measure/layout pass rather than displaying a corner of
            // the old-sized surface during an asynchronous resize.
            return v==null || v.getParent()!=parent.get() || v.getWidth()==width && v.getHeight()==height;}
        void restore(){
            ViewGroup p=parent.get();if(p!=null)p.removeOnLayoutChangeListener(this);
            if(observer!=null && observer.isAlive())observer.removeOnPreDrawListener(this);observer=null;
            View v=root.get();if(v==null)return;ViewGroup.LayoutParams lp=v.getLayoutParams();
            if(lp!=null){lp.width=lpWidth;lp.height=lpHeight;
                if(lp instanceof FrameLayout.LayoutParams)((FrameLayout.LayoutParams)lp).gravity=gravity;
                if(lp instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams m=(ViewGroup.MarginLayoutParams)lp;
                    m.setMarginStart(start);m.setMarginEnd(end);m.setMargins(left,top,right,bottom);}
                v.setLayoutParams(lp);}
            v.setPivotX(px);v.setPivotY(py);v.setScaleX(sx);v.setScaleY(sy);v.setTranslationX(tx);v.setTranslationY(ty);
        }
    }
}
