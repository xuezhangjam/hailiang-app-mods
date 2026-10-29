package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;
import java.util.WeakHashMap;

/** Separate public-site WebView, with no classroom JavaScript interfaces. */
public final class ChatWebPanel {
    public static final String HOME="https://chatgpt.com/";
    private static final WeakHashMap<Activity,WeakReference<Panel>> PANELS=new WeakHashMap<Activity,WeakReference<Panel>>();
    private static int dp(Activity a,int n){return Math.round(n*a.getResources().getDisplayMetrics().density);}
    public static void open(Activity a){
        if(a==null || a.isFinishing() || a.isDestroyed())return;
        WeakReference<Panel> ref=PANELS.get(a);Panel p=ref==null?null:ref.get();
        if(p==null || p.closed){p=new Panel(a);PANELS.put(a,new WeakReference<Panel>(p));}
        p.show();
    }
    public static void close(Activity a){WeakReference<Panel> ref=PANELS.remove(a);Panel p=ref==null?null:ref.get();if(p!=null)p.close();}
    private static final class Panel implements View.OnAttachStateChangeListener {
        final WeakReference<Activity> owner;final Dialog dialog;final WebView web;final TextView address;final View decor;boolean closed;
        Panel(final Activity a){
            owner=new WeakReference<Activity>(a);dialog=new Dialog(a);dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            LinearLayout root=new LinearLayout(a);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.WHITE);
            LinearLayout top=new LinearLayout(a);top.setGravity(Gravity.CENTER_VERTICAL);top.setPadding(dp(a,8),dp(a,4),dp(a,8),dp(a,4));top.setBackgroundColor(0xff303034);
            address=new TextView(a);address.setText("chatgpt.com");address.setTextSize(15);address.setTextColor(Color.WHITE);address.setSingleLine(true);
            web=new WebView(a);WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);settings.setUseWideViewPort(true);settings.setLoadWithOverviewMode(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);CookieManager.getInstance().setAcceptCookie(true);CookieManager.getInstance().setAcceptThirdPartyCookies(web,true);
            web.setWebViewClient(new WebViewClient(){
                public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !"https".equalsIgnoreCase(request.getUrl().getScheme());}
                public boolean shouldOverrideUrlLoading(WebView view,String url){return !"https".equalsIgnoreCase(Uri.parse(url).getScheme());}
                public void onPageFinished(WebView view,String url){String host=Uri.parse(url).getHost();if(host!=null)address.setText(host);CookieManager.getInstance().flush();}
            });
            button(a,top,"后退",new View.OnClickListener(){public void onClick(View v){if(web.canGoBack())web.goBack();}});
            button(a,top,"前进",new View.OnClickListener(){public void onClick(View v){if(web.canGoForward())web.goForward();}});
            top.addView(address,new LinearLayout.LayoutParams(0,-2,1f));
            button(a,top,"刷新",new View.OnClickListener(){public void onClick(View v){web.reload();}});
            button(a,top,"返回课堂",new View.OnClickListener(){public void onClick(View v){dialog.dismiss();}});
            root.addView(top,new LinearLayout.LayoutParams(-1,-2));root.addView(web,new LinearLayout.LayoutParams(-1,0,1f));dialog.setContentView(root);
            dialog.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface ignored){if(!closed)web.onPause();}});
            dialog.setOnKeyListener(new DialogInterface.OnKeyListener(){public boolean onKey(DialogInterface ignored,int key,KeyEvent event){if(key!=KeyEvent.KEYCODE_BACK)return false;if(event.getAction()==KeyEvent.ACTION_UP){if(web.canGoBack())web.goBack();else dialog.dismiss();}return true;}});
            decor=a.getWindow().getDecorView();decor.addOnAttachStateChangeListener(this);web.loadUrl(HOME);
        }
        private void button(Activity a,LinearLayout bar,String text,View.OnClickListener click){TextView b=new TextView(a);b.setText(text);b.setTextColor(Color.WHITE);b.setTextSize(14);b.setGravity(Gravity.CENTER);b.setMinHeight(dp(a,48));b.setPadding(dp(a,12),0,dp(a,12),0);b.setOnClickListener(click);bar.addView(b,new LinearLayout.LayoutParams(-2,-2));}
        void show(){if(closed)return;dialog.show();Window w=dialog.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));w.getDecorView().setPadding(0,0,0,0);w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);web.onResume();}
        void close(){if(closed)return;closed=true;dialog.dismiss();decor.removeOnAttachStateChangeListener(this);web.stopLoading();if(web.getParent() instanceof ViewGroup)((ViewGroup)web.getParent()).removeView(web);web.destroy();}
        public void onViewAttachedToWindow(View v){}
        public void onViewDetachedFromWindow(View v){close();}
    }
}
