package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

public final class AiPicker {
    public interface OfficialAction {void openOfficial();}
    private static final WeakHashMap<Activity,WeakReference<Dialog>> OPEN=new WeakHashMap<Activity,WeakReference<Dialog>>();
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    private static GradientDrawable shape(Activity a,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(a,radius));return d;}
    public static void show(final Activity a,final OfficialAction official){
        if(a.isFinishing() || a.isDestroyed())return;
        WeakReference<Dialog> ref=OPEN.get(a);Dialog current=ref==null?null:ref.get();if(current!=null && current.isShowing())return;
        final Dialog dialog=new Dialog(a);OPEN.put(a,new WeakReference<Dialog>(dialog));dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel=new LinearLayout(a);panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(a,24),dp(a,24),dp(a,24),dp(a,20));panel.setBackground(shape(a,0xff303034,28));
        TextView title=new TextView(a);title.setText("选择 AI");title.setTextSize(23);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);title.setTextColor(Color.WHITE);panel.addView(title,new LinearLayout.LayoutParams(-1,-2));
        row(a,panel,"海亮自带",false,new View.OnClickListener(){public void onClick(View v){dialog.dismiss();if(!a.isFinishing() && !a.isDestroyed())official.openOfficial();}});
        row(a,panel,"ChatGPT",true,new View.OnClickListener(){public void onClick(View v){dialog.dismiss();ChatWebPanel.open(a);}});
        TextView cancel=new TextView(a);cancel.setText("取消");cancel.setTextSize(15);cancel.setTextColor(0xffd6d7dc);cancel.setGravity(Gravity.CENTER);cancel.setMinHeight(dp(a,48));LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=dp(a,12);panel.addView(cancel,cp);cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
        dialog.setContentView(panel);dialog.show();Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));int width=a.getWindow().getDecorView().getWidth();if(width<=0)width=a.getResources().getDisplayMetrics().widthPixels;w.setLayout(Math.min(dp(a,400),Math.max(dp(a,220),width-dp(a,40))),-2);
    }
    private static void row(Activity a,LinearLayout parent,String label,boolean chat,View.OnClickListener click){
        LinearLayout row=new LinearLayout(a);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(a,18),dp(a,12),dp(a,18),dp(a,12));row.setMinimumHeight(dp(a,72));row.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33ffffff),shape(a,0xff45454b,20),null));
        Drawable logo=null;if(chat)try{logo=a.getPackageManager().getApplicationIcon("com.openai.chatgpt");}catch(Exception missing){}
        else{int id=a.getResources().getIdentifier("function_ai_portal","drawable",a.getPackageName());if(id!=0)logo=a.getDrawable(id);}
        if(logo==null){logo=a.getDrawable(android.R.drawable.ic_menu_help).mutate();logo.setTint(Color.WHITE);}
        ImageView icon=new ImageView(a);icon.setImageDrawable(logo);row.addView(icon,new LinearLayout.LayoutParams(dp(a,34),dp(a,34)));
        LinearLayout texts=new LinearLayout(a);texts.setOrientation(LinearLayout.VERTICAL);TextView name=new TextView(a);name.setText(label);name.setTextColor(Color.WHITE);name.setTextSize(18);name.setTypeface(Typeface.DEFAULT,Typeface.BOLD);texts.addView(name,new LinearLayout.LayoutParams(-1,-2));
        if(chat){TextView domain=new TextView(a);domain.setText("chatgpt.com · 应用内打开");domain.setTextColor(0xffd6d7dc);domain.setTextSize(13);texts.addView(domain,new LinearLayout.LayoutParams(-1,-2));}
        LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(-1,-2);tp.leftMargin=dp(a,16);row.addView(texts,tp);LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);rp.topMargin=dp(a,14);parent.addView(row,rp);row.setContentDescription(label);row.setOnClickListener(click);
    }
    public static void close(Activity a){WeakReference<Dialog> ref=OPEN.remove(a);Dialog d=ref==null?null:ref.get();if(d!=null)d.dismiss();}
}
