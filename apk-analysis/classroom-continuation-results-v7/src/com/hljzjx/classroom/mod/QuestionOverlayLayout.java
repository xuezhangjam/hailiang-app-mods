package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.TreeSet;
import java.util.WeakHashMap;

/** Place question-local additions around actual native actions and each other.
 * All geometry is mapped back into the host's logical coordinate frame. */
public final class QuestionOverlayLayout {
    public static final int TOP=0,START=1,END=2;
    private static final WeakHashMap<View,Slot> SLOTS=new WeakHashMap<View,Slot>();
    private static boolean layingOut;
    private QuestionOverlayLayout(){}
    private static int dp(View v,int n){return Math.round(n*v.getResources().getDisplayMetrics().density);}
    public static boolean covered(View root){
        if(root==null || !(root.getParent() instanceof ViewGroup))return false;
        ViewGroup host=(ViewGroup)root.getParent();
        for(int i=host.indexOfChild(root)+1;i<host.getChildCount();i++){View v=host.getChildAt(i);
            if(v.getVisibility()==View.VISIBLE && !mod(v) && v.getWidth()>=host.getWidth()*.65f && v.getHeight()>=host.getHeight()*.6f)return true;}
        return false;
    }
    public static void place(View root,View controls,int edge,int bottom){
        if(root==null || controls==null || !(controls.getParent() instanceof FrameLayout))return;
        Slot s=SLOTS.get(controls);if(s==null){s=new Slot(root,controls);SLOTS.put(controls,s);}
        s.edge=edge;s.bottom=bottom;
        reflow((FrameLayout)controls.getParent());
    }
    public static void remove(View controls){Slot s=controls==null?null:SLOTS.remove(controls);if(s!=null)expand(s);}
    private static final class Slot {
        final WeakReference<View> root,view;int edge,bottom;boolean collapsed;
        TextView more;Dialog dialog;
        Slot(View r,View v){root=new WeakReference<View>(r);view=new WeakReference<View>(v);}
    }
    private static RectF bounds(View v,Matrix hostInverse){
        if(!v.isShown() || v.getWidth()<=0 || v.getHeight()<=0)return null;
        Matrix matrix=new Matrix();v.transformMatrixToGlobal(matrix);
        RectF rect=new RectF(0,0,v.getWidth(),v.getHeight());matrix.mapRect(rect);hostInverse.mapRect(rect);
        return rect;
    }
    private static boolean intersects(RectF a,RectF b,int gap){return a.left<b.right+gap && a.right>b.left-gap && a.top<b.bottom+gap && a.bottom>b.top-gap;}
    private static boolean mod(View v){Object tag=v.getTag();return tag instanceof String && ((String)tag).startsWith("mod_");}
    private static void scan(View v,FrameLayout host,Matrix inverse,RectF area,ArrayList<RectF> blocks,int[] budget,int toolbar){
        if(v==null || budget[0]--<=0 || !v.isShown() || mod(v))return;
        RectF r=bounds(v,inverse);if(r==null || !intersects(r,area,0))return;
        // Full-page click shields, WebViews and handwriting canvases are not
        // small action buttons. Their actual native child controls still count.
        boolean action=v.isClickable() || v.isLongClickable() || v.hasOnClickListeners();
        boolean headerText=v instanceof TextView && r.top<area.top+dp(host,88) && r.height()<=dp(host,80);
        boolean small=r.height()<=dp(host,132) && r.width()<=area.width() && r.width()*r.height()<=area.width()*area.height()*.45f;
        boolean named=toolbar!=0 && v.getId()==toolbar;
        if(small && (action || headerText || named))blocks.add(r);
        if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount() && budget[0]>0;i++)scan(g.getChildAt(i),host,inverse,area,blocks,budget,toolbar);}
    }
    /** Candidate edges, not a screen-sized pixel search. Native buttons can be
     * added/resized/hidden at runtime without another resource-ID allowlist. */
    private static RectF find(RectF area,int w,int h,Slot slot,ArrayList<RectF> blocks,int gap){
        if(w<=0 || h<=0 || w>area.width() || h>area.height())return null;
        float preferredX=slot.edge==START?area.left:area.right-w;
        float preferredY=slot.edge==TOP?area.top+gap:area.bottom-h-slot.bottom;
        preferredY=Math.max(area.top,Math.min(area.bottom-h,preferredY));
        TreeSet<Float> ys=new TreeSet<Float>();ys.add(preferredY);ys.add(area.top);ys.add(area.bottom-h);
        for(RectF b:blocks){ys.add(b.top-gap-h);ys.add(b.bottom+gap);}
        RectF best=null;double score=Double.MAX_VALUE;
        for(float y:ys){
            if(y<area.top || y+h>area.bottom)continue;
            // Union the forbidden X intervals for this row. This avoids a
            // cubic rectangle search on a page with many question selectors.
            ArrayList<float[]> intervals=new ArrayList<float[]>();
            for(RectF b:blocks)if(y<b.bottom+gap && y+h>b.top-gap)intervals.add(new float[]{b.left-gap-w,b.right+gap});
            Collections.sort(intervals,new Comparator<float[]>(){public int compare(float[] a,float[] b){return Float.compare(a[0],b[0]);}});
            float cursor=area.left,maxX=area.right-w,x=Float.NaN;double bestDistance=Double.MAX_VALUE;
            for(float[] interval:intervals){
                if(interval[1]<=cursor)continue;
                if(interval[0]>maxX)break;
                if(interval[0]>=cursor){float point=Math.max(cursor,Math.min(interval[0],preferredX));double distance=Math.abs(point-preferredX);
                    if(distance<bestDistance){x=point;bestDistance=distance;}}
                cursor=Math.max(cursor,interval[1]);if(cursor>maxX)break;
            }
            if(cursor<=maxX){float point=Math.max(cursor,Math.min(maxX,preferredX));double distance=Math.abs(point-preferredX);if(distance<bestDistance){x=point;bestDistance=distance;}}
            if(Float.isNaN(x))continue;
            // Stay in the requested header/footer row before moving over any
            // course content. On a crowded header, prefer leftward movement.
            double distance=bestDistance+Math.abs(y-preferredY)*8;
            if(distance<score){score=distance;best=new RectF(x,y,x+w,y+h);}
        }return best;
    }
    private static int[] size(View v,boolean vertical){
        int unbounded=View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED);
        if(v instanceof LinearLayout){
            LinearLayout row=(LinearLayout)v;int width=0,height=0;
            for(int i=0;i<row.getChildCount();i++){View child=row.getChildAt(i);if(child.getVisibility()==View.GONE)continue;
                child.measure(unbounded,unbounded);int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                ViewGroup.LayoutParams raw=child.getLayoutParams();int l=0,t=0,r=0,b=0;
                if(raw instanceof ViewGroup.MarginLayoutParams){ViewGroup.MarginLayoutParams p=(ViewGroup.MarginLayoutParams)raw;l=p.leftMargin;r=p.rightMargin;t=p.topMargin;b=p.bottomMargin;}
                if(vertical){width=Math.max(width,w+l+r);height+=h+t+b;}else{width+=w+l+r;height=Math.max(height,h+t+b);}
            }return new int[]{width+v.getPaddingLeft()+v.getPaddingRight(),height+v.getPaddingTop()+v.getPaddingBottom()};
        }
        v.measure(unbounded,unbounded);return new int[]{v.getMeasuredWidth(),v.getMeasuredHeight()};
    }
    private static void apply(View v,RectF box,FrameLayout host,boolean vertical){
        if(v instanceof LinearLayout){LinearLayout row=(LinearLayout)v;int orientation=vertical?LinearLayout.VERTICAL:LinearLayout.HORIZONTAL;if(row.getOrientation()!=orientation)row.setOrientation(orientation);}
        int left=Math.round(box.left)-host.getPaddingLeft(),top=Math.round(box.top)-host.getPaddingTop(),w=Math.round(box.width()),h=Math.round(box.height());
        ViewGroup.LayoutParams raw=v.getLayoutParams();
        if(raw instanceof FrameLayout.LayoutParams){FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)raw;
            if(p.gravity==(Gravity.TOP|Gravity.LEFT) && p.leftMargin==left && p.topMargin==top && p.width==w && p.height==h)return;}
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(w,h,Gravity.TOP|Gravity.LEFT);p.leftMargin=left;p.topMargin=top;
        v.setLayoutParams(p);
    }
    private static void reflow(final FrameLayout host){
        if(layingOut || host.getWidth()<=0 || host.getHeight()<=0)return;layingOut=true;
        try{
            ArrayList<Slot> slots=new ArrayList<Slot>();
            for(Slot s:SLOTS.values()){View v=s.view.get();if(v==null || v.getParent()!=host || !v.isAttachedToWindow())continue;
                if(v.getVisibility()==View.VISIBLE || (s.collapsed && v.getVisibility()==View.INVISIBLE))slots.add(s);else expand(s);}
            if(slots.isEmpty())return;
            Collections.sort(slots,new Comparator<Slot>(){public int compare(Slot a,Slot b){int order=a.edge-b.edge;if(order!=0)return order;return host.indexOfChild(a.view.get())-host.indexOfChild(b.view.get());}});
            Matrix matrix=new Matrix(),inverse=new Matrix();host.transformMatrixToGlobal(matrix);if(!matrix.invert(inverse))return;
            Rect visible=new Rect();if(!host.getLocalVisibleRect(visible))return;
            int margin=dp(host,12),gap=dp(host,12);
            RectF area=new RectF(Math.max(host.getPaddingLeft(),visible.left)+margin,Math.max(host.getPaddingTop(),visible.top)+dp(host,4),
                Math.min(host.getWidth()-host.getPaddingRight(),visible.right)-margin,Math.min(host.getHeight()-host.getPaddingBottom(),visible.bottom)-dp(host,4));
            // Window-visible bounds include an obstructing IME/system taskbar.
            Rect frame=new Rect();host.getWindowVisibleDisplayFrame(frame);
            if(frame.right>frame.left && frame.bottom>frame.top){RectF window=new RectF(frame);inverse.mapRect(window);if(!area.intersect(window))return;}
            if(area.width()<=0 || area.height()<=0)return;
            int toolbar=host.getResources().getIdentifier("handwrite_toolbar","id",host.getContext().getPackageName());
            ArrayList<RectF> blocks=new ArrayList<RectF>();scan(host.getRootView(),host,inverse,area,blocks,new int[]{2048},toolbar);
            for(Slot s:slots){View v=s.view.get();View r=s.root.get();if(v==null || r==null)continue;
                int[] normal=size(v,false);RectF box=find(area,normal[0],normal[1],s,blocks,gap);boolean vertical=false;
                if(box==null && v instanceof LinearLayout){int[] stacked=size(v,true);box=find(area,stacked[0],stacked[1],s,blocks,gap);vertical=box!=null;}
                if(box!=null){expand(s);apply(v,box,host,vertical);blocks.add(box);}
                // If no space exists at all, never cover a native submit/option.
                // Keep the tools reachable via a compact, movable tools menu.
                else compact(s,v,area,blocks,gap,host);
            }
        }finally{layingOut=false;}
    }
    private static void expand(Slot s){
        if(s.more!=null && s.more.getParent() instanceof ViewGroup)((ViewGroup)s.more.getParent()).removeView(s.more);s.more=null;
        if(s.dialog!=null)s.dialog.dismiss();s.dialog=null;
        View v=s.view.get();if(s.collapsed && v!=null && v.getVisibility()==View.INVISIBLE)v.setVisibility(View.VISIBLE);s.collapsed=false;
    }
    private static void compact(final Slot s,View v,RectF area,ArrayList<RectF> blocks,int gap,FrameLayout host){
        if(s.more==null){
            s.more=QuestionActions.button(v,"工具",android.R.drawable.ic_menu_more);s.more.setTag("mod_question_overflow");
            s.more.setContentDescription("展开课堂工具");
            s.more.setOnClickListener(new View.OnClickListener(){public void onClick(View ignored){menu(s);}});
            host.addView(s.more,new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT));
        }
        s.collapsed=true;v.setVisibility(View.INVISIBLE);
        int[] measured=size(s.more,false);RectF box=find(area,measured[0],measured[1],s,blocks,gap);
        if(box==null)box=find(area,measured[0],measured[1],s,blocks,dp(host,2));
        // An entirely occupied tiny viewport cannot fit another touch target.
        // Do not obscure a native action; reevaluate when that viewport changes.
        s.more.setVisibility(box==null?View.GONE:View.VISIBLE);
        if(box!=null){apply(s.more,box,host,false);blocks.add(box);}
    }
    private static void menu(final Slot s){
        final View original=s.view.get();View root=s.root.get();Activity a=root==null?null:QuestionActions.activity(root.getContext());
        if(a==null || a.isFinishing() || a.isDestroyed() || original==null || !original.isAttachedToWindow() || !s.collapsed || ClassroomPip.active(a))return;
        if(s.dialog!=null && s.dialog.isShowing())return;
        final Dialog dialog=new Dialog(a);s.dialog=dialog;dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(root,16),dp(root,16),dp(root,16),dp(root,16));
        panel.setBackgroundColor(0xff303034);
        ArrayList<View> actions=new ArrayList<View>();
        if(original instanceof ViewGroup){ViewGroup row=(ViewGroup)original;for(int i=0;i<row.getChildCount();i++)actions.add(row.getChildAt(i));}else actions.add(original);
        for(final View action:actions){if(action!=original && action.getVisibility()!=View.VISIBLE)continue;
            CharSequence label=action instanceof TextView?((TextView)action).getText():action.getContentDescription();
            if(label==null)continue;TextView item=QuestionActions.button(root,label.toString(),android.R.drawable.ic_menu_more);item.setEnabled(action.isEnabled());
            panel.addView(item,new LinearLayout.LayoutParams(-1,-2));item.setOnClickListener(new View.OnClickListener(){public void onClick(View ignored){dialog.dismiss();if(s.collapsed && original.isAttachedToWindow() && action.isEnabled())action.performClick();}});
        }
        dialog.setContentView(panel);dialog.show();Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.setLayout(Math.min(dp(root,300),Math.max(dp(root,160),a.getWindow().getDecorView().getWidth()-dp(root,32))),-2);}
    }
}
