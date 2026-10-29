package com.nice.room.ui.roomlive.fragment.ask.data;import java.util.*;
public class QuestionAskSubmitItem{public long id,user;public String content;public List<Integer> options;public int seconds,auto;
public QuestionAskSubmitItem(long i,List<Integer> o,String c,int t,int a,long u){id=i;options=o;content=c;seconds=t;auto=a;user=u;}
public long getAskTitleId(){return id;}public long getStudentId(){return user;}public String getAnswerContent(){return content;}public List<Integer> getAnswerOptionList(){return options;}}
