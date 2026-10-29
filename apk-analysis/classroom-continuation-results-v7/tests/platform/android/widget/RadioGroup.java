package android.widget;
public class RadioGroup extends android.view.ViewGroup {
 public int checked=-1;
 public RadioGroup(android.content.Context c){super(c);}
 public int getCheckedRadioButtonId(){return checked;}
}
