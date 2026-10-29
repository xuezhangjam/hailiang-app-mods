package com.hailiang.xxb.room.server.entity.ask;
import java.util.List;
public class HandwriteAskShowAnswer {
 public final long user;public final String name,strokes,check,time;public final QuestionAskType type;public final List<String> images;public final boolean excellent;
 public HandwriteAskShowAnswer(long u,String n,QuestionAskType t,List<String> i,String s,String c,String a,boolean e){user=u;name=n;type=t;images=i;strokes=s;check=c;time=a;excellent=e;}
 public long getUserId(){return user;}public String getUsername(){return name;}public QuestionAskType getAskType(){return type;}
 public List<String> getImages(){return images;}public String getStudentStrokes(){return strokes;}
}
