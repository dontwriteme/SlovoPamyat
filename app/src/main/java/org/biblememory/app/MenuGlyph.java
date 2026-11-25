package org.biblememory.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Small custom vector icons; all strokes are drawn, not text symbols or emoji. */
final class MenuGlyph extends View {
    final String kind;final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    MenuGlyph(Context context,String kind,int color){
        super(context);this.kind=kind;
        pen.setColor(color);pen.setStyle(Paint.Style.STROKE);
        pen.setStrokeWidth(1.8f);pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    }
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=Math.min(getWidth(),getHeight())*.57f;
        canvas.save();
        canvas.translate((getWidth()-size)/2f,(getHeight()-size)/2f);
        canvas.scale(size/24f,size/24f);
        Path path=new Path();
        switch(kind){
            case "book":
                path.moveTo(12,6);path.cubicTo(8,3.5f,5,3.5f,3,5);
                path.lineTo(3,18);path.cubicTo(6,16.5f,9,17,12,19);
                path.cubicTo(15,17,18,16.5f,21,18);path.lineTo(21,5);
                path.cubicTo(19,3.5f,16,3.5f,12,6);canvas.drawPath(path,pen);
                canvas.drawLine(12,6,12,19,pen);break;
            case "file":
                canvas.drawRoundRect(5,3,19,21,2,2,pen);
                canvas.drawLine(8,8,16,8,pen);
                canvas.drawLine(12,11,12,18,pen);canvas.drawLine(8.5f,14.5f,15.5f,14.5f,pen);break;
            case "pencil":
                path.moveTo(4,20);path.lineTo(8.5f,18.9f);path.lineTo(20,7.4f);
                path.quadTo(21.3f,6.1f,20,4.8f);path.lineTo(19.2f,4);
                path.quadTo(17.9f,2.7f,16.6f,4);path.lineTo(5,15.6f);
                path.close();canvas.drawPath(path,pen);
                canvas.drawLine(14.8f,5.8f,18.2f,9.2f,pen);break;
            case "shuffle":
                // Crossing tracks with separate arrowheads (shuffle).
                path.moveTo(3,6);path.lineTo(6,6);
                path.cubicTo(12,6,12,18,18,18);path.lineTo(21,18);
                path.moveTo(18,15);path.lineTo(21,18);path.lineTo(18,21);
                path.moveTo(3,18);path.lineTo(6,18);
                path.cubicTo(12,18,12,6,18,6);path.lineTo(21,6);
                path.moveTo(18,3);path.lineTo(21,6);path.lineTo(18,9);
                canvas.drawPath(path,pen);break;
            case "eraser":
                // Eraser icon is deliberately distinct from the delete/trash icon.
                path.moveTo(4,14);path.lineTo(12,5);path.quadTo(13,4,14,5);
                path.lineTo(20,11);path.quadTo(21,12,20,13);
                path.lineTo(13,20);path.lineTo(9,20);path.close();
                canvas.drawPath(path,pen);canvas.drawLine(12,7,18,13,pen);
                canvas.drawLine(12,20,21,20,pen);break;
            case "trash":
                canvas.drawRoundRect(6,7,18,21,1,1,pen);
                canvas.drawLine(4,6,20,6,pen);canvas.drawLine(10,4,14,4,pen);
                canvas.drawLine(10,10,10,18,pen);canvas.drawLine(14,10,14,18,pen);break;
            default: // graduation cap, for «Изучить»
                path.moveTo(2,9);path.lineTo(12,4);path.lineTo(22,9);
                path.lineTo(12,14);path.close();canvas.drawPath(path,pen);
                path.reset();path.moveTo(7,12);path.lineTo(7,17);
                path.quadTo(12,21,17,17);path.lineTo(17,12);canvas.drawPath(path,pen);
                canvas.drawLine(22,9,22,18,pen);break;
        }
        canvas.restore();
    }
}
