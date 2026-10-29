package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.os.Build;
import android.util.Rational;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Toast;
import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.WeakHashMap;

/** Explicit system PiP; retains the running classroom and its active page. */
public final class ClassroomPip {
    private static final WeakHashMap<Activity,Session> SESSIONS=new WeakHashMap<Activity,Session>();
    private static Activity activity(Context c){for(int i=0;c!=null && i<32;i++){
        if(c instanceof Activity)return (Activity)c;if(!(c instanceof ContextWrapper))return null;
        Context next=((ContextWrapper)c).getBaseContext();if(next==c)return null;c=next;}return null;}
    public static boolean active(Context c){Activity a=activity(c);Session s=a==null?null:SESSIONS.get(a);return s!=null && s.pip;}
    private static View find(Activity a,String name){int id=a.getResources().getIdentifier(name,"id",a.getPackageName());return id==0?null:a.findViewById(id);}
    public static void enter(Activity a){enter(a,findContent(a));}
    private static View findContent(Activity a){return a==null?null:find(a,"courseware_sv");}
    public static void enter(final Activity a,View content){
        if(a==null || a.isFinishing() || a.isDestroyed() || Build.VERSION.SDK_INT<26)return;
        if(a.isInPictureInPictureMode())return;
        if(!a.getPackageManager().hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)){
            Toast.makeText(a,"此系统不支持画中画",Toast.LENGTH_SHORT).show();return;}
        if(content==null || content.getWidth()<=0 || content.getHeight()<=0)content=a.getWindow().getDecorView();
        float ratio=Math.max(.42f,Math.min(2.38f,(float)content.getWidth()/Math.max(1,content.getHeight())));
        PictureInPictureParams.Builder b=new PictureInPictureParams.Builder().setAspectRatio(new Rational(Math.round(ratio*1000),1000));
        // Web content is not seamless-resize capable. Cross-fade instead of
        // animating a cropped intermediate WebView surface on Android 12+.
        if(Build.VERSION.SDK_INT>=31)b.setSeamlessResizeEnabled(false);
        Rect visible=new Rect();if(content.getGlobalVisibleRect(visible))b.setSourceRectHint(visible);
        PipViewport.begin(a);
        changed(a,true);
        try{if(!a.enterPictureInPictureMode(b.build())){changed(a,false);unavailable(a);}}
        catch(RuntimeException unavailable){changed(a,false);unavailable(a);}
    }
    private static void unavailable(Activity a){Toast.makeText(a,"无法进入画中画，请在应用信息中的“画中画”设置里允许",Toast.LENGTH_LONG).show();}
    public static void changed(final Activity a,boolean enabled){
        Session s=SESSIONS.get(a);if(s==null && enabled){s=new Session(a);SESSIONS.put(a,s);}
        if(enabled)PipViewport.begin(a);
        if(s!=null){s.pip=enabled;if(enabled)s.watch();else{s.restore();SESSIONS.remove(a);}}
        if(!enabled)PipViewport.end(a);
        QuestionPptReturn.pictureInPicture(a);
        final View root=a.getWindow().getDecorView();root.postDelayed(new Runnable(){public void run(){if(!a.isFinishing() && !a.isDestroyed())PptPageFit.refresh(root);}},100);
    }
    public static void close(Activity a){Session s=SESSIONS.remove(a);if(s!=null)s.restore();PipViewport.end(a);}
    private static final class Session implements ViewTreeObserver.OnGlobalLayoutListener {
        final WeakReference<Activity> owner;final WeakHashMap<View,Integer> hidden=new WeakHashMap<View,Integer>();ViewTreeObserver tree;boolean pip;
        Session(Activity a){owner=new WeakReference<Activity>(a);}
        void hide(View view){if(view!=null && view.getVisibility()==View.VISIBLE){if(!hidden.containsKey(view))hidden.put(view,Integer.valueOf(View.VISIBLE));view.setVisibility(View.INVISIBLE);}}
        void watch(){Activity a=owner.get();if(a==null)return;
            View root=a.getWindow().getDecorView();if(tree==null){tree=root.getViewTreeObserver();if(tree.isAlive())tree.addOnGlobalLayoutListener(this);}onGlobalLayout();}
        public void onGlobalLayout(){Activity a=owner.get();if(!pip || a==null)return;
            for(String name:new String[]{"room_toolbar","ll_status_bar","tv_flower_number","handwrite_toolbar","compose_group_member","btn_submit"})hide(find(a,name));
            hide(a.getWindow().getDecorView().findViewWithTag("mod_bottom_navigation"));
        }
        void restore(){pip=false;if(tree!=null && tree.isAlive())tree.removeOnGlobalLayoutListener(this);tree=null;
            for(Map.Entry<View,Integer> entry:hidden.entrySet()){View view=entry.getKey();if(view!=null && view.getVisibility()==View.INVISIBLE)view.setVisibility(entry.getValue().intValue());}hidden.clear();}
    }
}
