package org.biblememory.app;

import android.os.Build;
import android.content.Context;
import android.graphics.*;

import android.animation.ValueAnimator;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import static org.biblememory.app.ScreenUi.*;

/** Owns the anchored help window and its presentation. */
final class ModeInfoPopover {
    private final ScreenUi ui;

    ModeInfoPopover(ScreenUi ui) {
        this.ui = ui;
    }

    private PopupWindow modeInfoPopup;
    private View modeInfoAnchor;

    void onActivityTouch(MotionEvent event){
        if(modeInfoAnchor!=null && event.getActionMasked()==MotionEvent.ACTION_DOWN){
            int[] location=new int[2];
            modeInfoAnchor.getLocationOnScreen(location);
            float x=event.getRawX(),y=event.getRawY();
            if(x<location[0]||x>=location[0]+modeInfoAnchor.getWidth()||
               y<location[1]||y>=location[1]+modeInfoAnchor.getHeight())dismiss(true);
        }
    }

    boolean dismiss(boolean animated){
        PopupWindow popup=modeInfoPopup;
        if(popup==null)return false;
        View bubble=popup.getContentView();
        bubble.animate().cancel();
        if(animated&&ValueAnimator.areAnimatorsEnabled()){
            bubble.animate().alpha(0f).scaleX(.9f).scaleY(.9f).setDuration(120)
                .setInterpolator(new DecelerateInterpolator()).withEndAction(popup::dismiss).start();
        }else popup.dismiss();
        return true;
    }

    void show(View anchor,LearningHelp help){
        if(modeInfoAnchor==anchor){dismiss(true);return;}
        dismiss(false);
        if(!anchor.isAttachedToWindow())return;

        Rect visible=new Rect();
        anchor.getWindowVisibleDisplayFrame(visible);
        visible.inset(ui.dp(12),ui.dp(12));
        if(visible.width()<=ui.dp(64)||visible.height()<=ui.dp(64))return;
        int[] location=new int[2];
        anchor.getLocationOnScreen(location);
        int centerX=location[0]+anchor.getWidth()/2;
        int centerY=location[1]+anchor.getHeight()/2;
        int sideTip=location[0]+ui.dp(3);
        int caret=ui.dp(8);
        int width=Math.min(ui.dp(320),visible.width());
        boolean opensLeft=sideTip-visible.left>=Math.min(ui.dp(250),width);
        if(opensLeft)width=Math.min(width,sideTip-visible.left);

        LinearLayout card=ui.column();
        card.setPadding(ui.dp(14),ui.dp(4),ui.dp(8),ui.dp(12));
        LinearLayout heading=ui.row();
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.addView(ui.txt(help.title,16,WHITE,true),new LinearLayout.LayoutParams(0,-2,1));
        TextView close=ui.txt("×",23,MUTED,false);
        close.setGravity(Gravity.CENTER);
        close.setContentDescription("Закрыть подсказку");
        close.setOnClickListener(v->dismiss(true));
        heading.addView(close,new LinearLayout.LayoutParams(ui.dp(48),ui.dp(48)));
        card.addView(heading);
        for(int i=0;i<help.sectionCount();i++){
            String label=help.heading(i);
            boolean hasHeading=!label.isEmpty();
            if(hasHeading){
                TextView subtitle=ui.txt(label,14,SUCCESS,true);
                subtitle.setIncludeFontPadding(false);
                if(Build.VERSION.SDK_INT>=28)subtitle.setAccessibilityHeading(true);
                String stageIcon=help==LearningHelp.STUDY?
                    ("Ознакомление".equals(label)?"mode-book":
                     "Сборка".equals(label)?"help-puzzle":
                     "Написание".equals(label)?"notebook":null):null;
                if(stageIcon!=null){
                    LinearLayout stage=ui.row();
                    stage.setGravity(Gravity.CENTER_VERTICAL);
                    LinearLayout.LayoutParams iconParams=new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22));
                    iconParams.rightMargin=ui.dp(7);
                    stage.addView(new NavigationGlyph(anchor.getContext(),stageIcon,SUCCESS),iconParams);
                    stage.addView(subtitle,new LinearLayout.LayoutParams(0,-2,1));
                    ui.add(card,stage,8);
                }else ui.add(card,subtitle,i==0?0:8);
            }
            TextView detail=ui.txt(help.body(i),14,WHITE,false);
            detail.setIncludeFontPadding(false);
            detail.setPadding(0,0,ui.dp(6),0);
            detail.setLineSpacing(ui.dp(1),1f);
            ui.add(card,detail,hasHeading?3:(i==0?0:8));
        }
        card.measure(View.MeasureSpec.makeMeasureSpec(width-(opensLeft?caret:0),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));

        int desiredHeight=card.getMeasuredHeight()+(opensLeft?0:caret);
        int above=Math.max(0,location[1]+ui.dp(3)-visible.top);
        int below=Math.max(0,visible.bottom-(location[1]+anchor.getHeight()-ui.dp(3)));
        boolean opensAbove=!opensLeft && below<desiredHeight && above>=below;
        int height=Math.min(desiredHeight,opensLeft?visible.height():opensAbove?above:below);
        if(height<=caret)return;
        int x=opensLeft?sideTip-width:Math.max(visible.left,Math.min(centerX-width/2,visible.right-width));
        int y=opensLeft?Math.max(visible.top,Math.min(centerY-height/2,visible.bottom-height)):
            opensAbove?location[1]+ui.dp(3)-height:location[1]+anchor.getHeight()-ui.dp(3);
        int tangent=opensLeft?centerY-y:centerX-x;

        FrameLayout bubble=new FrameLayout(anchor.getContext());
        bubble.setOutlineProvider(new ViewOutlineProvider(){
            @Override public void getOutline(View view,android.graphics.Outline outline){
                outline.setRoundRect(0,opensLeft||opensAbove?0:caret,
                    view.getWidth()-(opensLeft?caret:0),view.getHeight()-(opensAbove?caret:0),ui.dp(18));
            }
        });
        bubble.setElevation(ui.dp(4));
        View surface=new ModeInfoSurface(anchor.getContext(),opensLeft,opensAbove,tangent,caret);
        bubble.addView(surface,new FrameLayout.LayoutParams(-1,-1));
        ScrollView scroll=new ScrollView(anchor.getContext());
        scroll.setFillViewport(true);
        // Scroll is only a fallback for small windows or large accessibility fonts.
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(card,new ScrollView.LayoutParams(-1,-2));
        FrameLayout.LayoutParams scrollParams=new FrameLayout.LayoutParams(-1,-1);
        scrollParams.setMargins(0,opensLeft||opensAbove?0:caret,opensLeft?caret:0,opensAbove?caret:0);
        bubble.addView(scroll,scrollParams);

        PopupWindow popup=new PopupWindow(bubble,width,height,false);
        popup.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        popup.setAnimationStyle(0);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        // The Activity receives outside touches normally, including the anchor's second tap.
        popup.setOutsideTouchable(false);
        popup.setOnDismissListener(()->{
            if(modeInfoPopup==popup){modeInfoPopup=null;modeInfoAnchor=null;}
        });
        modeInfoPopup=popup;
        modeInfoAnchor=anchor;
        boolean animate=ValueAnimator.areAnimatorsEnabled();
        bubble.setPivotX(opensLeft?width:tangent);
        bubble.setPivotY(opensLeft?tangent:opensAbove?height:0);
        if(animate){bubble.setAlpha(0f);bubble.setScaleX(.9f);bubble.setScaleY(.9f);}
        popup.showAsDropDown(anchor,x-location[0],y-location[1]-anchor.getHeight(),Gravity.LEFT);
        if(animate)bubble.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(175)
            .setInterpolator(new DecelerateInterpolator()).start();
    }

    /** Draw the rounded panel and its caret as a single outlined surface. */
    private final class ModeInfoSurface extends View {
        private final boolean left,above;
        private final int tangent,caret;
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path outline=new Path();

        ModeInfoSurface(Context context,boolean left,boolean above,int tangent,int caret){
            super(context);
            this.left=left;this.above=above;this.tangent=tangent;this.caret=caret;
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight){
            float top=left||above?1:caret;
            float right=left?width-caret:width-1;
            float bottom=above?height-caret:height-1;
            outline.rewind();
            outline.addRoundRect(1,top,right,bottom,ui.dp(18),ui.dp(18),Path.Direction.CW);
            Path arrow=new Path();
            float base=Math.max(ui.dp(14),Math.min(tangent,(left?height:width)-ui.dp(14)));
            if(left){
                arrow.moveTo(right-1,base-caret);arrow.lineTo(width-1,tangent);
                arrow.lineTo(right-1,base+caret);
            }else{
                float edge=above?bottom-1:top+1;
                arrow.moveTo(base-caret,edge);arrow.lineTo(tangent,above?height-1:1);
                arrow.lineTo(base+caret,edge);
            }
            arrow.close();
            outline.op(arrow,Path.Op.UNION);
        }

        @Override protected void onDraw(Canvas canvas){
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(CARD);
            canvas.drawPath(outline,paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(ui.dp(1));
            paint.setColor(BORDER);
            canvas.drawPath(outline,paint);
        }
    }

}
