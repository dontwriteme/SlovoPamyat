package org.biblememory.app;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.ReplacementSpan;
import android.text.style.StrikethroughSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Collections;
import java.util.List;
import static org.biblememory.app.ScreenUi.WHITE;

final class LessonAnswerReviewView {
    private final ScreenUi ui;
    LessonAnswerReviewView(ScreenUi ui){this.ui=ui;}

    void render(LinearLayout content,String expected,AnswerReviewLogic.Result result){
        addAnswerReviewCard(content,"Твой ответ",result.summary==AnswerReviewLogic.Summary.EMPTY?
            "Ответ не введён":result.displayedActual,result.actualMarks,false,
            0xFFF9E9E4,0xFFECD2CA,0xFF923C37);
        addAnswerReviewCard(content,"Правильный ответ",expected,Collections.emptyList(),true,
            0xFFF0F3E6,0xFFDDE5D4,0xFF436044);
    }

    private LinearLayout answerReviewPanel(int background,int border){
        LinearLayout panel=ui.column();
        panel.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(14));
        panel.setBackground(ui.outlined(background,18,border));
        return panel;
    }

    private static final class ReviewErrorSpan extends ReplacementSpan {
        private final float thickness;
        ReviewErrorSpan(float thickness){this.thickness=thickness;}
        @Override public int getSize(Paint paint,CharSequence text,int start,int end,Paint.FontMetricsInt metrics){
            if(metrics!=null){
                Paint.FontMetricsInt source=paint.getFontMetricsInt();
                metrics.top=source.top;metrics.ascent=source.ascent;
                metrics.descent=Math.max(source.descent,(int)Math.ceil(thickness*2));
                metrics.bottom=Math.max(source.bottom,metrics.descent);
                metrics.leading=source.leading;
            }
            boolean originalBold=paint.isFakeBoldText();
            paint.setFakeBoldText(true);
            int width=(int)Math.ceil(paint.measureText(text,start,end));
            paint.setFakeBoldText(originalBold);
            return width;
        }
        @Override public void draw(Canvas canvas,CharSequence text,int start,int end,float x,int top,
                                   int baseline,int bottom,Paint paint){
            int originalColor=paint.getColor();
            boolean originalBold=paint.isFakeBoldText();
            float originalWidth=paint.getStrokeWidth();
            paint.setColor(0xFFB72F2B);
            paint.setFakeBoldText(true);
            float width=paint.measureText(text,start,end);
            canvas.drawText(text,start,end,x,baseline,paint);
            paint.setStrokeWidth(thickness);
            float underline=Math.min(bottom-thickness/2f,baseline+thickness);
            canvas.drawLine(x,underline,x+width,underline,paint);
            paint.setStrokeWidth(originalWidth);
            paint.setFakeBoldText(originalBold);
            paint.setColor(originalColor);
        }
    }

    private void addAnswerReviewCard(LinearLayout content,String title,String text,List<AnswerReviewLogic.Mark> marks,
                                     boolean correct,int background,int border,int ink){
        LinearLayout panel=answerReviewPanel(background,border);
        LinearLayout heading=ui.row();
        TextView icon=ui.txt(correct?"✓":"×",15,Color.WHITE,true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(ui.shape(ink,20));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconSize=new LinearLayout.LayoutParams(ui.dp(24),ui.dp(24));
        iconSize.rightMargin=ui.dp(8);
        heading.addView(icon,iconSize);
        heading.addView(ui.txt(title,14,ink,true),new LinearLayout.LayoutParams(0,-2,1));
        ui.add(panel,heading,0);
        SpannableString marked=new SpannableString(text);
        for(AnswerReviewLogic.Mark mark:marks){
            if(mark.kind==AnswerReviewLogic.Kind.SPELLING){
                marked.setSpan(new ReviewErrorSpan(ui.dp(2)),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }else{
                marked.setSpan(new ForegroundColorSpan(0xFFAD3C37),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                if(mark.kind==AnswerReviewLogic.Kind.EXTRA)
                    marked.setSpan(new StrikethroughSpan(),mark.start,mark.end,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        TextView body=answerReviewText(marked,19);
        body.setLineSpacing(ui.dp(3),1.08f);
        ui.add(panel,body,8);
        ui.add(content,panel,content.getChildCount()==0?0:10);
    }

    private TextView answerReviewText(CharSequence text,int size){
        TextView view=ui.txt("",size,WHITE,false);
        view.setText(text);
        return view;
    }

}
