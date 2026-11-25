package org.biblememory.app;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class LessonAssemblyView {
    private final Context context;
    private final ScreenUi ui;
    private final LessonAssemblyState lessonAssembly;
    private LessonWordWrap currentBank;

    LessonAssemblyView(Context context,ScreenUi ui,LessonAssemblyState state){
        this.context=context;this.ui=ui;this.lessonAssembly=state;
    }

    private TextView lessonBuildAction;
    private final List<BuildSlot> lessonBuildSlots=new ArrayList<>();
    static final int BUILD_INK=0xFF553722;
    private final List<TextView> lessonBuildTiles=new ArrayList<>();
    private int lessonBuildRequired=0;

    int required(){return lessonBuildRequired;}

    void clear(){
        currentBank=null;
        lessonBuildAction=null;
        lessonBuildRequired=0;
        lessonBuildTiles.clear();
        lessonBuildSlots.clear();
    }

    void installAction(TextView action,boolean complete){
        action.setTypeface(Typeface.create("serif",Typeface.BOLD));
        action.setTextSize(22);
        action.setElevation(ui.dp(3));
        styleAssemblyContinue(action,complete);
        lessonBuildAction=action;
    }

    void render(LinearLayout content,String[] tokens,int[] missing,long lessonSeed,
                Runnable onComplete,Runnable save){
        clear();
        lessonBuildRequired=missing.length;
        lessonAssembly.validatePrefix(tokens,missing);
        LessonWordWrap prompt=new LessonWordWrap(context,ui.dp(7),true);
        prompt.setPadding(ui.dp(14),ui.dp(16),ui.dp(14),ui.dp(16));
        prompt.setBackground(ui.outlined(0xF9FFF2E1,23,0xFFFFFAF3));
        prompt.setElevation(ui.dp(2));
        prompt.setTag("assembly-answer");
        for(int i=0;i<tokens.length;i++){
            if(Arrays.binarySearch(missing,i)>=0){
                BuildSlot slot=new BuildSlot(tokens[i]);
                lessonBuildSlots.add(slot);
                prompt.addView(slot);
            }else{
                TextView word=ui.displayText(tokens[i],18);
                word.setTextColor(BUILD_INK);
                word.setGravity(Gravity.CENTER);
                word.setMinHeight(ui.dp(43));
                word.setPadding(ui.dp(4),ui.dp(8),ui.dp(4),ui.dp(8));
                prompt.addView(word);
            }
        }
        ui.add(content,prompt,10);
        LessonWordWrap choices=new LessonWordWrap(context,ui.dp(8),true);
        choices.setTag("assembly-word-bank");
        choices.setPadding(ui.dp(6),ui.dp(4),ui.dp(6),ui.dp(4));
        choices.setClipChildren(false);
        choices.setClipToPadding(false);
        int[] shuffled=WordOrderLogic.shuffledIndices(missing.length,lessonSeed);
        for(int index:shuffled){
            final int pressed=index;
            final int targetPos=missing[index];
            String caption=tokens[targetPos].replaceAll("[^\\p{L}\\p{N}\\p{M}\\s]", "").trim();
            TextView tile=wordTile(caption,0xFFFFEEDC,BUILD_INK,0xFFFFF9F0,()->{
                // The target and bank occupy fixed views. Never call render() on a tile tap.
                if(choices!=currentBank)return;
                if(lessonAssembly.tap(tokens,missing,pressed)==LessonAssemblyState.Tap.IGNORED)return;
                updateLessonBuildView();
                // The progress bar advances per completed exercise, not per word.
                // Avoid re-querying mastery and recomputing every layout on each tap.
                if(lessonAssembly.picked()==lessonBuildRequired)
                    onComplete.run();
                save.run();
            });
            tile.setTypeface(Typeface.create("serif",Typeface.BOLD));
            tile.setTextSize(18);
            tile.setBackground(ui.outlined(0xFFFFEEDC,17,0xFFFFF9F0));
            tile.setElevation(ui.dp(2));
            ui.animateLessonPress(tile);
            tile.setTag(index);lessonBuildTiles.add(tile);choices.addView(tile);
        }
        currentBank=choices;
        ui.add(content,choices,14);
        updateLessonBuildView();
    }
    private TextView wordTile(String word,int bg,int color,int stroke,Runnable tapped) {
        TextView tv=ui.txt(word,16,color,true);
        tv.setMinHeight(ui.dp(43));tv.setGravity(Gravity.CENTER);
        tv.setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));tv.setBackground(ui.outlined(bg,12,stroke));
        if(tapped!=null){tv.setClickable(true);tv.setFocusable(true);tv.setOnClickListener(v->tapped.run());}
        return tv;
    }
    private final class BuildSlot extends TextView {
        private final String token;
        private final SpannableString hidden;
        private boolean filled;

        BuildSlot(String token){
            super(context);
            this.token=token;
            hidden=new SpannableString(token);
            for(int[] range:ReviewLogic.blurRanges(token,100,0))
                hidden.setSpan(new ForegroundColorSpan(Color.TRANSPARENT),range[0],range[1],
                    android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            setTextSize(18);
            setTypeface(Typeface.create("serif",Typeface.BOLD));
            setTextColor(BUILD_INK);
            setPadding(ui.dp(10),ui.dp(8),ui.dp(10),ui.dp(8));
            setGravity(Gravity.CENTER);
            setMinWidth(ui.dp(56));
            setMinHeight(ui.dp(43));
            showWord(false);
        }

        void showWord(boolean filled){
            this.filled=filled;
            setText(filled?token:hidden);
            GradientDrawable frame=ui.shape(filled?0xFFE9EDDA:0x44FFF9EF,12);
            if(filled)frame.setStroke(ui.dp(1),0xFFCCD6B5);
            else frame.setStroke(ui.dp(1),0xFFBEB0A0,ui.dp(5),ui.dp(4));
            setBackground(frame);
            setContentDescription(filled?null:"Пропущенное слово");
        }

        @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){
            super.onInitializeAccessibilityNodeInfo(info);
            if(!filled)info.setText(null);
        }
    }

    void reference(LinearLayout content,String caption){
        LinearLayout reference=ui.row();
        reference.setGravity(Gravity.CENTER);
        reference.setPadding(ui.dp(15),ui.dp(8),ui.dp(15),ui.dp(8));
        reference.setBackground(ui.outlined(0xFFFFEBD5,26,0xFFFFF8EF));
        reference.setElevation(ui.dp(2));
        reference.addView(new NavigationGlyph(context,"book",BUILD_INK),new LinearLayout.LayoutParams(ui.dp(28),ui.dp(27)));
        TextView label=ui.displayText(caption,18);
        label.setTextColor(BUILD_INK);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-2,-2);
        textParams.leftMargin=ui.dp(8);
        reference.addView(label,textParams);
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,-2);
        params.gravity=Gravity.CENTER_HORIZONTAL;
        content.addView(reference,params);
    }

    private void styleAssemblyContinue(TextView action,boolean enabled){
        GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,enabled?
            new int[]{0xFF7D9C49,0xFF3E692D}:new int[]{0xFFC3C8A7,0xFFA1AA83});
        background.setCornerRadius(ui.dp(32));
        background.setStroke(ui.dp(1),0xFFFFF9EA);
        action.setBackground(background);
        action.setTextColor(Color.WHITE);
        action.setEnabled(enabled);
    }

    android.graphics.drawable.Drawable background(){
        return new android.graphics.drawable.Drawable(){
            private final android.graphics.drawable.Drawable scenery=context.getDrawable(R.drawable.hero_sunrise);
            private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
            private int horizon;
            private Shader sky,fade;
            @Override protected void onBoundsChange(Rect bounds){
                int height=bounds.height(),width=bounds.width();
                if(height<=0||width<=0)return;
                horizon=bounds.top+Math.round(height*.52f);
                int sceneryHeight=bounds.bottom-horizon;
                float aspect=scenery.getIntrinsicWidth()/(float)scenery.getIntrinsicHeight();
                int drawWidth=Math.max(width,Math.round(sceneryHeight*aspect));
                int drawHeight=Math.round(drawWidth/aspect);
                int left=bounds.centerX()-drawWidth/2;
                scenery.setBounds(left,bounds.bottom-drawHeight,left+drawWidth,bounds.bottom);
                sky=new LinearGradient(0,bounds.top,0,bounds.bottom,
                    new int[]{0xFFF5E7E2,0xFFFFEBD5,0xFFF9D9A7},null,Shader.TileMode.CLAMP);
                fade=new LinearGradient(0,horizon,0,bounds.bottom,
                    new int[]{0xFFFFEBD5,0xA8FFEAD1,0x30FFE5BC,0x58FFE0A8},
                    new float[]{0f,.32f,.72f,1f},Shader.TileMode.CLAMP);
            }
            @Override public void draw(Canvas canvas){
                if(sky==null)return;
                Rect bounds=getBounds();
                paint.setShader(sky);
                canvas.drawRect(bounds,paint);
                canvas.save();
                canvas.clipRect(bounds.left,horizon,bounds.right,bounds.bottom);
                scenery.draw(canvas);
                paint.setShader(fade);
                canvas.drawRect(bounds,paint);
                canvas.restore();
            }
            @Override public void setAlpha(int alpha){}
            @Override public void setColorFilter(android.graphics.ColorFilter filter){}
            @Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
        };
    }

    private void updateLessonBuildView(){
        for(int i=0;i<lessonBuildSlots.size();i++){
            BuildSlot slot=lessonBuildSlots.get(i);
            if(slot.filled!=(i<lessonAssembly.picked()))slot.showWord(i<lessonAssembly.picked());
        }
        for(int i=0;i<lessonBuildTiles.size();i++){
            TextView tile=lessonBuildTiles.get(i);
            int idx=(Integer)tile.getTag();
            int visibility=lessonAssembly.used(idx)?View.INVISIBLE:View.VISIBLE;
            if(tile.getVisibility()!=visibility)tile.setVisibility(visibility);
            boolean wrong=idx==lessonAssembly.wrongTile();
            // A wrong tap changes at most two tiles (previous and current).
            // Avoid allocating new GradientDrawables and invalidating every tile.
            if(tile.isSelected()!=wrong){
                tile.setSelected(wrong);
                tile.setTextColor(wrong?0xFF923C37:BUILD_INK);
                tile.setBackground(ui.outlined(wrong?0xFFF8DDD4:0xFFFFEEDC,17,
                    wrong?0xFFE6B4A7:0xFFFFF9F0));
            }
        }
        if(lessonBuildAction!=null&&lessonBuildAction.isEnabled()!=(lessonAssembly.picked()==lessonBuildRequired))
            styleAssemblyContinue(lessonBuildAction,lessonAssembly.picked()==lessonBuildRequired);
    }
}
