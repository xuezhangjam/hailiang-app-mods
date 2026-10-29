package com.hljzjx.classroom.mod;

import android.view.View;
import com.hailiang.handwrite.widget.HandwriteView;
import com.nice.room.ui.roomlive.tool.RoomToolBarView;
import java.util.WeakHashMap;

/** Reuse the real brush settings and stroke color, without changing membership. */
public final class DiscussionTools {
    private static final WeakHashMap<HandwriteView,Integer> COLORS=new WeakHashMap<HandwriteView,Integer>();
    public static void install(View root){
        if(root==null)return;
        int canvasId=root.getResources().getIdentifier("handwrite","id",root.getContext().getPackageName());
        int toolbarId=root.getResources().getIdentifier("handwrite_toolbar","id",root.getContext().getPackageName());
        View canvas=root.findViewById(canvasId),toolbar=root.findViewById(toolbarId);
        if(canvas instanceof HandwriteView && !COLORS.containsKey((HandwriteView)canvas))COLORS.put((HandwriteView)canvas,null);
        if(toolbar instanceof RoomToolBarView)((RoomToolBarView)toolbar).setHideColorOptions(false);
        QuestionPptReturn.installDiscussion(root);
    }
    public static void chosenColor(HandwriteView view,int color){
        view.setPenColor(color);
        if(COLORS.containsKey(view))COLORS.put(view,Integer.valueOf(color));
    }
    public static void memberColor(HandwriteView view,int color){
        Integer chosen=COLORS.get(view);
        view.setPenColor(chosen==null?color:chosen.intValue());
    }
}
