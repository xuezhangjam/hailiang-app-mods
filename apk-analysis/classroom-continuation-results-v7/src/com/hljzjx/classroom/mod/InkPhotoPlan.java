package com.hljzjx.classroom.mod;

import java.util.ArrayList;
import java.util.List;

/** Bounded raster-to-stroke conversion for the original stroke-only protocol. */
public final class InkPhotoPlan {
    public static final int MAX_RUNS=9000,MAX_SIDE=1100;
    public final int width,height;public final int[] pixels;public final List<Run> runs;
    public final boolean color;
    public static final class Run {
        public final int start,end,row,color;
        Run(int x,int end,int y,int c){start=x;this.end=end;row=y;color=c;}
    }
    private InkPhotoPlan(int w,int h,int[] p,List<Run> r,boolean c){width=w;height=h;pixels=p;runs=r;color=c;}
    public static int[] target(int w,int h,int maxW,int maxH){
        if(w<=0 || h<=0 || maxW<=0 || maxH<=0)throw new IllegalArgumentException("Invalid image dimensions");
        double s=Math.min(1,Math.min((double)Math.min(MAX_SIDE,maxW)/w,(double)Math.min(MAX_SIDE,maxH)/h));
        return new int[]{Math.max(1,(int)Math.floor(w*s)),Math.max(1,(int)Math.floor(h*s))};
    }
    private static int gray(int c){return ((c>>16&255)*77+(c>>8&255)*150+(c&255)*29)>>8;}
    private static int threshold(int[] src){
        int[] histogram=new int[256];long sum=0;for(int c:src){int y=gray(c);histogram[y]++;sum+=y;}
        int left=0,best=160;long partial=0;double maximum=-1;
        for(int t=0;t<255;t++){left+=histogram[t];partial+=(long)t*histogram[t];int right=src.length-left;if(left==0 || right==0)continue;
            double a=(double)partial/left,b=(double)(sum-partial)/right,d=a-b,v=(double)left*right*d*d;
            if(v>maximum){maximum=v;best=t;}}
        return Math.max(70,Math.min(215,best));
    }
    private static int quantize(int c,boolean color,int threshold){
        int a=c>>>24;if(a==0)return 0xffffffff;
        int r=c>>16&255,g=c>>8&255,b=c&255;
        if(a<255){r=(r*a+255*(255-a)+127)/255;g=(g*a+255*(255-a)+127)/255;b=(b*a+255*(255-a)+127)/255;}
        if(!color)return ((r*77+g*150+b*29)>>8)<=threshold?0xff000000:0xffffffff;
        r=((r*3+127)/255)*85;g=((g*3+127)/255)*85;b=((b*3+127)/255)*85;
        return 0xff000000|(r<<16)|(g<<8)|b;
    }
    /** The returned preview is exactly the quantized raster encoded as runs. */
    public static InkPhotoPlan convert(int[] source,int sourceWidth,int sourceHeight,int maxW,int maxH,boolean color){
        if(source==null || (long)sourceWidth*sourceHeight!=source.length)throw new IllegalArgumentException("Invalid pixels");
        int[] size=target(sourceWidth,sourceHeight,maxW,maxH);int w=size[0],h=size[1],t=threshold(source);
        for(int pass=0;pass<24;pass++){
            int[] pixels=new int[w*h];ArrayList<Run> runs=new ArrayList<Run>();int runCount=0;
            for(int y=0;y<h;y++){
                int sy=Math.min(sourceHeight-1,(int)((y+.5)*sourceHeight/h));int start=0,last=0xffffffff;
                for(int x=0;x<=w;x++){
                    int c=x==w?0:quantize(source[sy*sourceWidth+Math.min(sourceWidth-1,(int)((x+.5)*sourceWidth/w))],color,t);
                    if(x<w)pixels[y*w+x]=c;
                    if(x==0){last=c;start=0;}
                    else if(x==w || c!=last){if(last!=0xffffffff){runCount++;if(runCount<=MAX_RUNS)runs.add(new Run(start,x,y,last));}start=x;last=c;}
                }
            }
            if(runCount<=MAX_RUNS)return new InkPhotoPlan(w,h,pixels,runs,color);
            double shrink=Math.min(.85,Math.sqrt((double)MAX_RUNS/runCount)*.94);
            int nw=Math.max(1,(int)Math.floor(w*shrink)),nh=Math.max(1,(int)Math.floor(h*shrink));
            if(nw==w && nh==h)break;w=nw;h=nh;
        }
        throw new IllegalArgumentException("Image too complex");
    }
    public static float[] placement(int canvasWidth,int canvasHeight,int imageWidth,int imageHeight){
        if(canvasWidth<=0 || canvasHeight<=0 || imageWidth<=0 || imageHeight<=0)return null;
        float s=Math.min(canvasWidth*.9f/imageWidth,canvasHeight*.8f/imageHeight);
        return new float[]{(canvasWidth-imageWidth*s)/2,(canvasHeight-imageHeight*s)/2,s};
    }
}
