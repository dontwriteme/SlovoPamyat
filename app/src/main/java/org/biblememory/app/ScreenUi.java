package org.biblememory.app;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;

/** Shared native styles and view factories. Callers own screen and domain state. */
final class ScreenUi {
    private final Context context;

    ScreenUi(Context context) {
        this.context = context;
    }

    // Accessible dark ink sits over opaque ivory reading surfaces. Study logic is unchanged.
    static final int BG=0xFFFFF1E8, CARD=0xFFFFFAF5, CARD_ALT=0xFFF7E4D8;
    static final int WHITE=0xFF27372D, MUTED=0xFF735D51, GREEN=0xFFBC6448;
    static final int VIOLET=0xFF657F75, BORDER=0xFFE8CFBE, RED=0xFFC34040;
    static final int HOME_SURFACE=0xFFFFF8F2, HOME_BORDER=0xFFE7CCB8;
    static final int SUCCESS=0xFF4D815C, GOLD=0xFFD8A75C;
    static final int PRIMARY_ON=0xFFFFFFFF;
    int dp(float d) {return dp(context,d);}
    static int dp(Context context,float d) {
        return (int)(context.getResources().getDisplayMetrics().density*d+0.5f);
    }
    GradientDrawable shape(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;
    }
    GradientDrawable outlined(int color,int radius,int border) {
        GradientDrawable d=shape(color,radius);d.setStroke(dp(1),border);return d;
    }
    private GradientDrawable gradient() {
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFFFD7BF,0xFFF6D1C3,0xFFE1E9DD});
        d.setCornerRadius(dp(25));d.setStroke(dp(1),BORDER);return d;
    }
    /** Subtle watercolor-like mountain silhouettes outside the opaque text surfaces. */
    android.graphics.drawable.Drawable pageBackground(){
        return new android.graphics.drawable.Drawable(){
            final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(Canvas canvas){
                Rect bounds=getBounds();float w=bounds.width(),h=bounds.height();
                if(w<=0||h<=0)return;
                paint.setShader(new LinearGradient(0,0,0,h,
                    0xFFFFF7F0,0xFFF8E5D9,Shader.TileMode.CLAMP));
                canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
                Path far=new Path();far.moveTo(0,dp(85));
                far.lineTo(w*.09f,dp(64));far.lineTo(w*.20f,dp(85));
                far.lineTo(w*.31f,dp(49));far.lineTo(w*.44f,dp(95));
                far.lineTo(w*.58f,dp(57));far.lineTo(w*.75f,dp(88));
                far.lineTo(w*.90f,dp(62));far.lineTo(w,dp(91));
                far.lineTo(w,0);far.lineTo(0,0);far.close();
                paint.setColor(0x50EEC3B4);canvas.drawPath(far,paint);
                Path foothills=new Path();foothills.moveTo(0,h);
                foothills.lineTo(0,h-dp(72));foothills.lineTo(w*.19f,h-dp(110));
                foothills.lineTo(w*.36f,h-dp(62));foothills.lineTo(w*.57f,h-dp(126));
                foothills.lineTo(w*.79f,h-dp(85));foothills.lineTo(w,h-dp(120));
                foothills.lineTo(w,h);foothills.close();
                paint.setColor(0x55B8CEC2);canvas.drawPath(foothills,paint);
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return android.graphics.PixelFormat.TRANSLUCENT;}
        };
    }
    GradientDrawable softAccent(){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,
            new int[]{0xFFDB8C6C,0xFFB8583F});d.setCornerRadius(dp(18));return d;
    }
    TextView displayText(String text,int size){
        TextView view=txt(text,size,WHITE,true);
        view.setTypeface(Typeface.create("serif",Typeface.BOLD));
        view.setLetterSpacing(0.005f);return view;
    }
    TextView prose(String text,int size){
        TextView view=txt(text,size,WHITE,false);
        view.setTypeface(Typeface.create("serif",Typeface.NORMAL));
        view.setLineSpacing(dp(4),1.14f);return view;
    }
    TextView txt(String value,int sp,int color,boolean bold) {
        TextView t=new TextView(context);t.setText(value);t.setTextSize(sp);t.setTextColor(color);
        t.setIncludeFontPadding(true);
        if(bold)t.setTypeface(Typeface.create("sans-serif-medium",Typeface.BOLD));
        t.setLineSpacing(dp(2),1.10f);
        return t;
    }
    LinearLayout column() {LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.VERTICAL);return l;}
    LinearLayout row() {LinearLayout l=new LinearLayout(context);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    void add(LinearLayout into,View v,int top) {
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.topMargin=dp(top);into.addView(v,p);
    }
    void gap(LinearLayout into,int d) {View v=new View(context);into.addView(v,new LinearLayout.LayoutParams(1,dp(d)));}
    LinearLayout card(LinearLayout into) {
        LinearLayout l=column();l.setPadding(dp(17),dp(18),dp(17),dp(18));
        l.setBackground(outlined(CARD,22,BORDER));l.setElevation(dp(2));
        add(into,l,12);return l;
    }
    LinearLayout headerCard(LinearLayout into) {
        LinearLayout l=column();l.setPadding(dp(19),dp(23),dp(19),dp(23));
        l.setBackground(gradient());add(into,l,10);return l;
    }
    TextView button(String label,boolean primary,Runnable action) {
        TextView b=txt(label,15,primary?PRIMARY_ON:WHITE,true);
        b.setGravity(Gravity.CENTER);b.setMinHeight(dp(49));
        b.setPadding(dp(14),dp(11),dp(14),dp(11));
        b.setBackground(primary?softAccent():outlined(CARD_ALT,18,BORDER));
        b.setClickable(true);b.setFocusable(true);b.setOnClickListener(v->action.run());
        return b;
    }
    void animateLessonPress(View button){
        button.setOnTouchListener((view,event)->{
            if(!view.isEnabled()||!ValueAnimator.areAnimatorsEnabled())return false;
            switch(event.getActionMasked()){
                case MotionEvent.ACTION_DOWN:
                    animateLessonScale(view,0.98f,100);
                    break;
                case MotionEvent.ACTION_MOVE:
                    if(event.getX()<0||event.getX()>view.getWidth()||
                       event.getY()<0||event.getY()>view.getHeight())
                        animateLessonScale(view,1f,160);
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    animateLessonScale(view,1f,160);
                    break;
            }
            return false; // Keep Android's click and cancellation behavior.
        });
        button.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
            @Override public void onViewAttachedToWindow(View view){}
            @Override public void onViewDetachedFromWindow(View view){
                view.animate().cancel();view.setScaleX(1f);view.setScaleY(1f);
            }
        });
    }
    private void animateLessonScale(View view,float scale,long duration){
        view.animate().cancel();
        view.animate().scaleX(scale).scaleY(scale).setDuration(duration)
            .setInterpolator(new DecelerateInterpolator()).start();
    }
    void action(LinearLayout into,String label,boolean primary,Runnable runnable) {add(into,button(label,primary,runnable),10);}
    LinearLayout tapCard(LinearLayout into,String title,String secondary,String arrow,Runnable action) {
        LinearLayout c=card(into);
        LinearLayout line=row();
        LinearLayout left=column();left.addView(txt(title,16,WHITE,true));
        if(secondary!=null&&!secondary.isEmpty())add(left,txt(secondary,13,MUTED,false),4);
        line.addView(left,new LinearLayout.LayoutParams(0,-2,1));
        line.addView(txt(arrow,18,GREEN,true));
        c.addView(line);c.setClickable(true);c.setFocusable(true);c.setOnClickListener(v->action.run());
        return c;
    }
    void label(LinearLayout into,String label) { add(into,txt(label,15,WHITE,true),14); }
    void helper(LinearLayout into,String label) { add(into,txt(label,13,MUTED,false),5); }
    void progress(LinearLayout into,int value,int total) {
        LinearLayout track=column();track.setBackground(shape(CARD_ALT,12));
        LinearLayout.LayoutParams trackP=new LinearLayout.LayoutParams(-1,dp(8));trackP.topMargin=dp(12);
        into.addView(track,trackP);
        View fill=new View(context);fill.setBackground(shape(GREEN,12));
        int pct=total<=0?0:Math.max(0,Math.min(100,Math.round(100f*value/total)));
        track.post(()->{LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(Math.max(dp(3),track.getWidth()*pct/100),dp(8));fill.setLayoutParams(p);});
        track.addView(fill,new LinearLayout.LayoutParams(dp(3),dp(8)));
    }
    EditText input(String hint,boolean multiline) {
        EditText e=new EditText(context);
        e.setTextColor(WHITE);e.setHintTextColor(MUTED);e.setTextSize(15);
        e.setSingleLine(!multiline);e.setHint(hint);
        e.setInputType(multiline?android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES:android.text.InputType.TYPE_CLASS_TEXT);
        e.setGravity(multiline?Gravity.TOP|Gravity.START:Gravity.CENTER_VERTICAL);
        e.setPadding(dp(15),dp(13),dp(15),dp(13));
        e.setBackground(outlined(CARD_ALT,14,BORDER));
        if(multiline){e.setMinLines(5);e.setMaxLines(12);}
        return e;
    }
    void forbidKeyboardHints(EditText edit,boolean multiline){
        int flags=android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
        if(multiline)flags|=android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE;
        edit.setInputType(flags);
        edit.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        edit.setPrivateImeOptions("nm");
    }
    EditText numeric(String hint,String initial) {
        EditText e=input(hint,false);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setText(initial);e.setSelectAllOnFocus(true);return e;
    }
    void styledDialog(AlertDialog d){
        d.setOnShowListener(x->{
            if(d.getWindow()!=null){
                d.getWindow().setBackgroundDrawable(shape(CARD,22));
                d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
                d.getWindow().setLayout((int)(context.getResources().getDisplayMetrics().widthPixels*.91f),-2);
            }
            int[] ids={android.R.id.button1,android.R.id.button2,android.R.id.button3};
            for(int id:ids){View v=d.findViewById(id);if(v instanceof TextView)((TextView)v).setTextColor(GREEN);}
        });
        d.show();
    }
    int bookInk(int book){
        int hue=BookColorLogic.color(book);
        return Color.rgb((Color.red(hue)*2+Color.red(WHITE))/4,
            (Color.green(hue)*2+Color.green(WHITE))/4,
            (Color.blue(hue)*2+Color.blue(WHITE))/4);
    }
}
