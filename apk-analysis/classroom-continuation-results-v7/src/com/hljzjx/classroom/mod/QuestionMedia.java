package com.hljzjx.classroom.mod;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.ColorSpace;
import android.graphics.ImageDecoder;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import com.hailiang.handwrite.bean.DrawingRecord;
import com.hailiang.handwrite.bean.PathInfo;
import com.hailiang.handwrite.bean.PointInfo;
import com.hailiang.handwrite.serialize.XYStrokeSerializer;
import com.hailiang.handwrite.widget.HandwriteView;
import com.nice.room.ui.roomlive.fragment.ask.TakePhotoFragment;
import com.nice.room.ui.roomlive.fragment.ask.answer.QuestionAskHandwriteFragment;
import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Photos are previewed, then encoded as bounded ordinary paths. The original
 * handwrite submitter uploads them without requiring a new teacher-side format. */
public final class QuestionMedia {
    private static final int REQUEST=0x6a32,MAX_PAYLOAD=1200000;
    private static final AtomicLong IDS=new AtomicLong(System.currentTimeMillis()*10000L);
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final WeakHashMap<QuestionAskHandwriteFragment,State> STATES=new WeakHashMap<QuestionAskHandwriteFragment,State>();
    private static final class State {
        final WeakReference<QuestionAskHandwriteFragment> owner;final WeakReference<View> root;
        Dialog dialog;WeakReference<TakePhotoFragment> camera;boolean busy,closed;int generation;int[] pixels;int width,height;
        State(QuestionAskHandwriteFragment f){owner=new WeakReference<QuestionAskHandwriteFragment>(f);root=new WeakReference<View>(f.getView());}
    }
    private static boolean live(State s){QuestionAskHandwriteFragment f=s.owner.get();View v=s.root.get();
        return !s.closed && f!=null && v!=null && f.getView()==v && f.isAdded() && !f.isRemoving() && v.isAttachedToWindow();}
    private static void message(State s,String text){QuestionAskHandwriteFragment f=s.owner.get();if(f!=null && f.getContext()!=null)Toast.makeText(f.getContext(),text,Toast.LENGTH_LONG).show();}
    public static void open(final QuestionAskHandwriteFragment f){
        if(f.getView()==null || !f.getView().isAttachedToWindow())return;
        State previous=STATES.get(f);if(previous!=null && (previous.busy || previous.dialog!=null && previous.dialog.isShowing()))return;
        if(previous!=null)destroy(f);final State s=new State(f);STATES.put(f,s);
        final View root=f.getView();final Activity a=QuestionActions.activity(root.getContext());if(a==null)return;
        final Dialog dialog=new Dialog(a);s.dialog=dialog;LinearLayout panel=panel(root,"添加手写照片");
        TextView camera=QuestionActions.button(root,"拍照",android.R.drawable.ic_menu_camera);panel.addView(camera,new LinearLayout.LayoutParams(-1,-2));
        camera.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();s.dialog=null;if(!live(s))return;
            TakePhotoFragment photo=new TakePhotoFragment();s.camera=new WeakReference<TakePhotoFragment>(photo);photo.setOnTakePhotoListener(new TakePhotoFragment.OnTakePhotoListener(){public void onTakePhoto(String path){s.camera=null;if(live(s))decode(s,Uri.fromFile(new File(path)));}});
            int container=root.getResources().getIdentifier("fragment_container","id",root.getContext().getPackageName());
            try{photo.show(f.getParentFragmentManager(),container,false);}catch(RuntimeException failed){message(s,"无法打开相机，请尝试从相册导入");}
        }});
        TextView album=QuestionActions.button(root,"从相册导入",android.R.drawable.ic_menu_gallery);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=QuestionActions.dp(root,12);panel.addView(album,ap);
        album.setOnClickListener(new View.OnClickListener(){public void onClick(View v){dialog.dismiss();s.dialog=null;if(!live(s))return;s.busy=true;
            try{try{f.startActivityForResult(picker(Intent.ACTION_OPEN_DOCUMENT),REQUEST);}catch(ActivityNotFoundException missing){f.startActivityForResult(picker(Intent.ACTION_GET_CONTENT),REQUEST);}}
            catch(RuntimeException failed){s.busy=false;message(s,"没有可用的相册或文件选择器");}
        }});show(a,dialog,panel,420);
    }
    private static Intent picker(String action){return new Intent(action).setType("image/*").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_ALLOW_MULTIPLE,false).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);}
    public static void result(QuestionAskHandwriteFragment f,int request,int result,Intent data){
        if(request!=REQUEST)return;State s=STATES.get(f);
        if((s==null || s.root.get()!=f.getView()) && f.getView()!=null){s=new State(f);STATES.put(f,s);}
        if(s==null || !live(s))return;s.busy=false;
        if(result!=Activity.RESULT_OK)return;Uri uri=data==null?null:data.getData();
        if(uri==null && data!=null && data.getClipData()!=null && data.getClipData().getItemCount()==1)uri=data.getClipData().getItemAt(0).getUri();
        if(uri==null || !("content".equals(uri.getScheme()) || "file".equals(uri.getScheme()))){message(s,"无法读取所选照片");return;}decode(s,uri);
    }
    private static void decode(final State s,final Uri uri){
        if(!live(s))return;final QuestionAskHandwriteFragment f=s.owner.get();final Context context=f.requireContext().getApplicationContext();
        final HandwriteView canvas=f.modGetCanvas();if(canvas==null || canvas.getWidth()<=0 || canvas.getHeight()<=0){message(s,"画布尚未准备好，请稍后重试");return;}
        final int maxW=Math.max(1,(int)(canvas.getWidth()*.9f)),maxH=Math.max(1,(int)(canvas.getHeight()*.8f));
        final int ticket=++s.generation;s.busy=true;message(s,"正在转换照片，稍后可预览确认");
        WORKER.execute(new Runnable(){public void run(){Bitmap bitmap=null;int[] pixels=null;int width=0,height=0;String failure=null;
            try{if(s.closed)return;bitmap=ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.getContentResolver(),uri),new ImageDecoder.OnHeaderDecodedListener(){public void onHeaderDecoded(ImageDecoder decoder,ImageDecoder.ImageInfo info,ImageDecoder.Source source){
                int[] size=InkPhotoPlan.target(info.getSize().getWidth(),info.getSize().getHeight(),maxW,maxH);decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);decoder.setTargetColorSpace(ColorSpace.get(ColorSpace.Named.SRGB));decoder.setTargetSize(size[0],size[1]);}});
                width=bitmap.getWidth();height=bitmap.getHeight();pixels=new int[width*height];bitmap.getPixels(pixels,0,width,0,0,width,height);
            }catch(Exception bad){failure="照片无法读取，请选择另一张照片";}catch(OutOfMemoryError large){failure="照片过大，请选择较小的照片";}finally{if(bitmap!=null)bitmap.recycle();}
            final int[] prepared=pixels;final int w=width,h=height;final String error=failure;
            MAIN.post(new Runnable(){public void run(){if(!live(s) || ticket!=s.generation)return;s.busy=false;if(error!=null || prepared==null){message(s,error==null?"照片转换失败":error);return;}
                s.pixels=prepared;s.width=w;s.height=h;convert(s,false,maxW,maxH);}});
        }});
    }
    private static void convert(final State s,final boolean color,final int maxW,final int maxH){
        if(!live(s) || s.pixels==null)return;final int ticket=++s.generation;final int[] pixels=s.pixels;final int w=s.width,h=s.height;s.busy=true;
        WORKER.execute(new Runnable(){public void run(){InkPhotoPlan plan=null;try{plan=InkPhotoPlan.convert(pixels,w,h,maxW,maxH,color);}catch(RuntimeException bad){}catch(OutOfMemoryError large){}
            final InkPhotoPlan ready=plan;MAIN.post(new Runnable(){public void run(){if(!live(s) || ticket!=s.generation)return;s.busy=false;
                if(ready==null){message(s,"照片内容过于复杂，请选择较小的照片");return;}preview(s,ready,maxW,maxH);}});
        }});
    }
    private static LinearLayout panel(View root,String title){
        LinearLayout p=new LinearLayout(root.getContext());p.setOrientation(LinearLayout.VERTICAL);int pad=QuestionActions.dp(root,20);p.setPadding(pad,pad,pad,pad);
        GradientDrawable bg=new GradientDrawable();bg.setColor(0xff303034);bg.setCornerRadius(QuestionActions.dp(root,28));p.setBackground(bg);
        TextView t=new TextView(root.getContext());t.setText(title);t.setTextColor(Color.WHITE);t.setTextSize(21);t.setPadding(0,0,0,QuestionActions.dp(root,16));p.addView(t,new LinearLayout.LayoutParams(-1,-2));return p;
    }
    private static void show(Activity a,Dialog d,LinearLayout panel,int dpWidth){
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);d.setContentView(panel);d.show();Window w=d.getWindow();w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        int width=a.getWindow().getDecorView().getWidth();if(width<=0)width=a.getResources().getDisplayMetrics().widthPixels;
        w.setLayout(Math.min(QuestionActions.dp(panel,dpWidth),Math.max(QuestionActions.dp(panel,220),width-QuestionActions.dp(panel,24))),-2);
    }
    private static void preview(final State s,final InkPhotoPlan plan,final int maxW,final int maxH){
        if(!live(s))return;final View root=s.root.get();Activity a=QuestionActions.activity(root.getContext());if(a==null || a.isFinishing() || a.isDestroyed())return;
        if(s.dialog!=null)s.dialog.dismiss();final Dialog d=new Dialog(a);s.dialog=d;LinearLayout p=panel(root,plan.color?"照片预览 · 精简彩色":"照片预览 · 黑白文档");
        final Bitmap preview=Bitmap.createBitmap(plan.pixels,plan.width,plan.height,Bitmap.Config.ARGB_8888);
        final ImageView image=new ImageView(root.getContext());image.setBackgroundColor(Color.WHITE);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setImageBitmap(preview);
        int vh=a.getWindow().getDecorView().getHeight();if(vh<=0)vh=a.getResources().getDisplayMetrics().heightPixels;
        p.addView(image,new LinearLayout.LayoutParams(-1,Math.min(QuestionActions.dp(root,300),Math.max(QuestionActions.dp(root,100),vh/3))));
        TextView note=new TextView(root.getContext());note.setText("转换后作为可批注笔迹，随手写答案提交。请先确认清晰度。");note.setTextColor(0xffdddddd);note.setTextSize(13);note.setPadding(0,QuestionActions.dp(root,12),0,QuestionActions.dp(root,12));p.addView(note,new LinearLayout.LayoutParams(-1,-2));
        TextView toggle=QuestionActions.button(root,plan.color?"切换黑白文档":"切换精简彩色",android.R.drawable.ic_menu_edit);p.addView(toggle,new LinearLayout.LayoutParams(-1,-2));toggle.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();s.dialog=null;convert(s,!plan.color,maxW,maxH);}});
        TextView accept=QuestionActions.button(root,"确认插入",android.R.drawable.ic_menu_add);LinearLayout.LayoutParams ap=new LinearLayout.LayoutParams(-1,-2);ap.topMargin=QuestionActions.dp(root,8);p.addView(accept,ap);
        accept.setOnClickListener(new View.OnClickListener(){public void onClick(View v){if(!live(s))return;insert(s,plan);d.dismiss();s.dialog=null;s.pixels=null;}});
        TextView cancel=QuestionActions.button(root,"取消",android.R.drawable.ic_menu_close_clear_cancel);LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(-1,-2);cp.topMargin=QuestionActions.dp(root,8);p.addView(cancel,cp);cancel.setOnClickListener(new View.OnClickListener(){public void onClick(View v){d.dismiss();s.dialog=null;s.pixels=null;}});
        d.setOnDismissListener(new DialogInterface.OnDismissListener(){public void onDismiss(DialogInterface dialog){image.setImageDrawable(null);preview.recycle();if(s.dialog==d)s.dialog=null;}});show(a,d,p,620);
    }
    private static PointInfo point(int x,int y,int index,long id,int color,int width,int height){
        PointInfo p=new PointInfo(x,y);p.index=index;p.pathId=id;p.color=color;p.phoneWidth=width;p.phoneHeight=height;p.type=index==0?0:1;p.pressure=1;return p;
    }
    private static void insert(State s,InkPhotoPlan plan){
        if(!live(s))return;QuestionAskHandwriteFragment answer=s.owner.get();HandwriteView canvas=answer.modGetCanvas();
        int w=canvas.getWidth(),h=canvas.getHeight();float[] frame=InkPhotoPlan.placement(w,h,plan.width,plan.height);if(frame==null)return;
        if(plan.runs.isEmpty()){message(s,"预览中没有可插入的笔迹内容");return;}
        DrawingRecord old=canvas.getDrawingRecord(),record=new DrawingRecord();ArrayList<PathInfo> paths=new ArrayList<PathInfo>();
        for(InkPhotoPlan.Run run:plan.runs){PathInfo p=new PathInfo();p.drawType=1;p.color=run.color;p.alpha=1;p.strokeWidth=Math.max(1,frame[2]*1.02f);p.pathID=IDS.incrementAndGet();p.pointsList=new ArrayList<PointInfo>();
            int y=Math.round(frame[1]+(run.row+.5f)*frame[2]),x=Math.round(frame[0]+run.start*frame[2]),end=Math.max(x+1,Math.round(frame[0]+run.end*frame[2]));
            p.pointsList.add(point(x,y,0,p.pathID,p.color,w,h));p.pointsList.add(point(Math.min(w,end),y,1,p.pathID,p.color,w,h));p.updateRangeWithPointList();paths.add(p);}
        if(old.getPathInfoList()!=null)paths.addAll(old.getPathInfoList());record.setWidth(w);record.setHeight(h);record.setLeft(0);record.setTop(0);record.setRight(Math.max(w,old.getRight()));record.setBottom(Math.max(h,old.getBottom()));record.setPathInfoList(paths);
        String serialized=XYStrokeSerializer.Companion.xyStrokeSerialize(record);if(serialized==null || serialized.length()>MAX_PAYLOAD){message(s,"当前照片和笔迹过多，请减少内容后再试");return;}
        canvas.setDrawingRecord(record);canvas.invalidate();answer.localSave();
        message(s,HandwriteContinuation.isLocal(answer)?"已插入本地练习照片":"已插入照片，提交手写答案时一起发送");
    }
    public static void destroy(QuestionAskHandwriteFragment f){State s=STATES.remove(f);if(s==null)return;s.closed=true;s.generation++;s.pixels=null;if(s.dialog!=null)s.dialog.dismiss();s.dialog=null;
        TakePhotoFragment camera=s.camera==null?null:s.camera.get();if(camera!=null && camera.isAdded() && !camera.isRemoving())try{camera.dismiss();}catch(RuntimeException ignored){}s.camera=null;}
}
