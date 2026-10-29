package com.nice.room.ui.roomlive.fragment.ask.result;
import com.hailiang.xxb.room.server.entity.ask.HandwriteAskShowAnswer;
public class QuestionAskHandwriteResultFragment extends QuestionAskAnswerResultBaseFragment {
 public HandwriteAskShowAnswer rendered;public boolean renderedMine;public int renders;
 // Models only the native updateAnswer bridge; no network response is assumed.
 public void updateAnswer(HandwriteAskShowAnswer raw,boolean mine){rendered=com.hljzjx.classroom.mod.ContinuationResults.resolve(this,raw,mine);renderedMine=mine;renders++;}
}
