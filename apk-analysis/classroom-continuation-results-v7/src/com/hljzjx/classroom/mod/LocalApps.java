package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import com.nice.room.ui.RoomLiveActivity;
import com.nice.room.ui.roomlive.interaction.impl.ThirdAppInteraction.ExternalLinkInfo;
import com.nice.room.ui.roomlive.tool.FunctionToolItemInfo;
import com.nice.room.ui.roomlive.tool.RoomToolBarView;
import com.nice.room.ui.roomlive.tool.ToolBarState;
import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;

/** Local entries are ordinary FunctionToolItemInfo instances, rendered by the
 * exact same FunctionItem composable as teacher-dispatched applications. */
public final class LocalApps {
    private static final String AI="com.hailiang.h5.ai_portal",ONION="com.yangcong345.zhike";
    private static final WeakHashMap<ToolBarState,Entries> ENTRIES=new WeakHashMap<ToolBarState,Entries>();
    private static Activity owner(Context c){
        for(int i=0;c!=null && i<32;i++){
            if(c instanceof Activity)return (Activity)c;
            if(!(c instanceof ContextWrapper))break;Context next=((ContextWrapper)c).getBaseContext();if(next==c)break;c=next;
        }return null;
    }
    public static void registerToolbar(RoomToolBarView toolbar){
        Activity a=owner(toolbar.getContext());
        if(a==null || a.isFinishing() || a.isDestroyed() || !a.getClass().getName().equals("com.nice.room.ui.RoomLiveActivity"))return;
        int id=a.getResources().getIdentifier("room_toolbar","id",a.getPackageName());
        if(id==0 || toolbar.getId()!=id)return;
        ToolBarState state=toolbar.modGetToolBarState();Entries previous=ENTRIES.get(state);
        if(previous==null || previous.owner.get()!=a)ENTRIES.put(state,new Entries(a));
    }
    public static void install(final Activity activity){
        new Handler(Looper.getMainLooper()).post(new Runnable(){public void run(){
            if(activity.isFinishing() || activity.isDestroyed())return;
            int id=activity.getResources().getIdentifier("room_toolbar","id",activity.getPackageName());
            View view=id==0?null:activity.findViewById(id);if(view instanceof RoomToolBarView)registerToolbar((RoomToolBarView)view);
        }});
    }
    public static void append(List<FunctionToolItemInfo> items,ToolBarState state){
        Entries e=ENTRIES.get(state);if(e==null)return;Activity a=e.owner.get();
        if(a==null || a.isFinishing() || a.isDestroyed())return;
        ExternalLinkInfo teacher=state.getAppInfo();String app=teacher==null?null:teacher.getAppId();
        if(AI.equals(app)){
            for(int i=items.size()-1;i>=0;i--){FunctionToolItemInfo item=items.get(i);
                if(teacher.getName().equals(item.getLabel())){
                    if(!(item.getOnClick() instanceof AiAction))items.set(i,FunctionToolItemInfo.modLocalItem(item.getLabel(),item.getIconRes(),item.getIconLink(),new AiAction(a,state,teacher,item)));
                    break;
                }
            }
        }
        if(!AI.equals(app) && !items.contains(e.ai))items.add(e.ai);
        if(!ONION.equals(app) && !items.contains(e.onion))items.add(e.onion);
        if(!items.contains(e.pip))items.add(e.pip);
    }
    public static void close(Activity activity){
        AiPicker.close(activity);ChatWebPanel.close(activity);ClassroomPip.close(activity);
        Iterator<Map.Entry<ToolBarState,Entries>> iterator=ENTRIES.entrySet().iterator();
        while(iterator.hasNext()){Activity owner=iterator.next().getValue().owner.get();if(owner==null || owner==activity)iterator.remove();}
    }
    private static final class Entries {
        final WeakReference<Activity> owner;final FunctionToolItemInfo ai,onion,pip;
        Entries(Activity a){
            owner=new WeakReference<Activity>(a);
            int icon=a.getResources().getIdentifier("function_ai_portal","drawable",a.getPackageName());
            if(icon==0)icon=android.R.drawable.ic_menu_help;
            ai=FunctionToolItemInfo.modLocalItem("AI问问",icon,null,new AiAction(a,null,null,null));
            onion=FunctionToolItemInfo.modLocalItem("洋葱智课",-1,"file:///android_asset/mod_toolbar/onion.png",new Action(a,1));
            pip=FunctionToolItemInfo.modLocalItem("画中画",-1,"file:///android_asset/mod_toolbar/pip.png",new PipAction(a));
        }
    }
    private static final class PipAction implements Function1<FunctionToolItemInfo,Unit>{
        final WeakReference<Activity> owner;PipAction(Activity a){owner=new WeakReference<Activity>(a);}
        public Unit invoke(FunctionToolItemInfo ignored){Activity a=owner.get();if(a!=null)ClassroomPip.enter(a);return Unit.INSTANCE;}
    }
    private static final class AiAction implements Function1<FunctionToolItemInfo,Unit>,AiPicker.OfficialAction {
        final WeakReference<Activity> owner;final WeakReference<ToolBarState> state;final ExternalLinkInfo teacher;final FunctionToolItemInfo item;
        AiAction(Activity a,ToolBarState s,ExternalLinkInfo info,FunctionToolItemInfo original){owner=new WeakReference<Activity>(a);state=new WeakReference<ToolBarState>(s);teacher=info;item=original;}
        public Unit invoke(FunctionToolItemInfo ignored){Activity a=owner.get();if(a!=null)AiPicker.show(a,this);return Unit.INSTANCE;}
        public void openOfficial(){
            Activity a=owner.get();if(a==null || a.isFinishing() || a.isDestroyed())return;
            try{if(item==null)((RoomLiveActivity)a).modOpenLocalApp(0);else{ToolBarState s=state.get();if(s==null || s.getAppInfo()!=teacher){Toast.makeText(a,"课堂应用已更新，请重新打开",Toast.LENGTH_SHORT).show();return;}item.getOnClick().invoke(item);}}
            catch(RuntimeException unavailable){Toast.makeText(a,"暂时无法打开，请检查登录状态或应用安装",Toast.LENGTH_LONG).show();}
        }
    }
    private static final class Action implements Function1<FunctionToolItemInfo,Unit> {
        final WeakReference<Activity> owner;final int action;
        Action(Activity a,int which){owner=new WeakReference<Activity>(a);action=which;}
        public Unit invoke(FunctionToolItemInfo ignored){
            Activity a=owner.get();if(a==null || a.isFinishing() || a.isDestroyed())return Unit.INSTANCE;
            try{((RoomLiveActivity)a).modOpenLocalApp(action);}
            catch(RuntimeException unavailable){Toast.makeText(a,"暂时无法打开，请检查登录状态或应用安装",Toast.LENGTH_LONG).show();}
            return Unit.INSTANCE;
        }
    }
}
