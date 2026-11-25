package org.biblememory.app;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Outline navigation icons, independent of system fonts and Android theme packs. */
final class NavigationGlyph extends View {
    private final String kind;
    private final int ink;
    private final Paint pen=new Paint(Paint.ANTI_ALIAS_FLAG);
    NavigationGlyph(Context context,String kind,int ink){super(context);this.kind=kind;this.ink=ink;
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float size=ScreenUi.dp(getContext(),kind.equals("info")?25:kind.startsWith("mode-")?22:23),cx=getWidth()/2f,cy=getHeight()/2f;
        canvas.save();canvas.translate(cx,cy);canvas.scale(size/24f,size/24f);
        pen.setColor(ink);pen.setStrokeWidth(1.85f);pen.setStrokeCap(Paint.Cap.ROUND);
        pen.setStrokeJoin(Paint.Join.ROUND);pen.setStyle(Paint.Style.STROKE);
        Path path=new Path();
        if(kind.startsWith("mode-")||kind.equals("info"))pen.setStrokeWidth(1.6f);
        if(kind.equals("info")){
            canvas.drawCircle(0,0,10,pen);
            canvas.drawPoint(0,-4,pen);
            canvas.drawLine(0,-0.8f,0,5,pen);
        }else if(kind.equals("puzzle")||kind.equals("help-puzzle")){
            if(kind.equals("puzzle")){
                canvas.rotate(-22f);
                pen.setStyle(Paint.Style.FILL);
            }else pen.setStrokeWidth(1.6f);
            path.moveTo(-9,-9);path.lineTo(-3,-9);
            path.cubicTo(-5,-15,5,-15,3,-9);
            path.lineTo(9,-9);path.lineTo(9,-3);
            path.cubicTo(3,-5,3,5,9,3);
            path.lineTo(9,9);path.lineTo(3,9);
            path.cubicTo(5,15,-5,15,-3,9);
            path.lineTo(-9,9);path.lineTo(-9,3);
            path.cubicTo(-3,5,-3,-5,-9,-3);
            path.close();canvas.drawPath(path,pen);
        }else if(kind.equals("mode-cards")){
            canvas.drawRoundRect(-10,-3,10,8,2,2,pen);
            path.moveTo(-8,-6);path.lineTo(8,-6);
            path.moveTo(-6,-9);path.lineTo(6,-9);
            canvas.drawPath(path,pen);
        }else if(kind.equals("mode-check")){
            canvas.drawRoundRect(-8,-9,8,10,2,2,pen);
            canvas.drawRoundRect(-4,-11,4,-7,1.5f,1.5f,pen);
            path.moveTo(-4,2);path.lineTo(-1,5);path.lineTo(4,-1);
            canvas.drawPath(path,pen);
        }else if(kind.equals("home")){
            path.moveTo(-10,-1);path.lineTo(0,-10);path.lineTo(10,-1);
            path.lineTo(10,10);path.lineTo(-10,10);path.close();canvas.drawPath(path,pen);
            canvas.drawRoundRect(-2,3,2,10,1,1,pen);
        }else if(kind.equals("book")||kind.equals("mode-book")){
            path.moveTo(0,-8);path.cubicTo(-4,-11,-8,-10,-11,-8);
            path.lineTo(-11,9);path.cubicTo(-6,7,-2,8,0,10);
            path.cubicTo(2,8,6,7,11,9);path.lineTo(11,-8);
            path.cubicTo(8,-10,4,-11,0,-8);path.close();canvas.drawPath(path,pen);
            canvas.drawLine(0,-8,0,10,pen);
        }else if(kind.equals("refresh")||kind.equals("mode-repeat")){
            // Symmetric two-arrow circular refresh glyph, fully path-based.
            // Both arrowheads are connected to their arcs: no overlapping strokes.
            path.moveTo(-9,-2);
            path.cubicTo(-8,-6,-4,-9,1,-9);
            path.cubicTo(5,-9,8,-6,9,-2);
            path.moveTo(4,-2);path.lineTo(9,-2);path.lineTo(9,-7);
            canvas.drawPath(path,pen);path.reset();
            path.moveTo(9,2);
            path.cubicTo(8,6,4,9,-1,9);
            path.cubicTo(-5,9,-8,6,-9,2);
            path.moveTo(-4,2);path.lineTo(-9,2);path.lineTo(-9,7);
            canvas.drawPath(path,pen);
        }else if(kind.equals("settings")){
            // Crisp outlined gear with eight spokes, independent of system emoji.
            canvas.drawCircle(0,0,7.5f,pen);
            canvas.drawCircle(0,0,3.1f,pen);
            for(int spoke=0;spoke<8;spoke++){
                canvas.save();canvas.rotate(spoke*45f);
                canvas.drawLine(0,-8f,0,-11f,pen);
                canvas.restore();
            }
        }else if(kind.equals("search")){
            // Magnifying glass, sized to match the collection search bar.
            canvas.drawCircle(-2,-2,6.5f,pen);
            canvas.drawLine(3,3,10,10,pen);
        }else if(kind.equals("brain")||kind.equals("mode-brain")){
            // Split cerebral hemispheres and internal folds.
            path.moveTo(0,-9);path.cubicTo(-4,-12,-8,-9,-8,-6);
            path.cubicTo(-12,-5,-12,-1,-10,1);
            path.cubicTo(-12,5,-8,9,-5,8);
            path.cubicTo(-4,12,-1,11,0,9);
            path.moveTo(0,-9);path.cubicTo(4,-12,8,-9,8,-6);
            path.cubicTo(12,-5,12,-1,10,1);
            path.cubicTo(12,5,8,9,5,8);
            path.cubicTo(4,12,1,11,0,9);
            path.moveTo(0,-9);path.lineTo(0,9);
            canvas.drawPath(path,pen);
            canvas.drawLine(-7,-2,-4,0,pen);canvas.drawLine(7,-2,4,0,pen);
            canvas.drawLine(-6,5,-3,4,pen);canvas.drawLine(6,5,3,4,pen);
        }else if(kind.equals("notebook")){
            // Notebook and pencil: strict writing-from-memory examination.
            canvas.drawRoundRect(-10,-10,5,10,2,2,pen);
            canvas.drawLine(-6,-10,-6,10,pen);
            canvas.drawLine(-3,-5,2,-5,pen);
            canvas.drawLine(-3,-1,2,-1,pen);
            path.moveTo(1,8);path.lineTo(3,4);
            path.lineTo(9,-5);path.lineTo(11,-7);
            path.lineTo(8,-9);path.lineTo(6,-7);
            path.lineTo(1,2);path.close();canvas.drawPath(path,pen);
        }else if(kind.equals("repeat")){
            // Two consecutive opposing arrows for the global repeat action.
            canvas.drawLine(-8,-4,8,-4,pen);
            canvas.drawLine(8,-4,4,-8,pen);
            canvas.drawLine(8,-4,4,0,pen);
            canvas.drawLine(8,5,-8,5,pen);
            canvas.drawLine(-8,5,-4,1,pen);
            canvas.drawLine(-8,5,-4,9,pen);
        }else{
            canvas.drawRoundRect(-10,-6,6,9,1.8f,1.8f,pen);
            canvas.drawRoundRect(-6,-10,10,5,1.8f,1.8f,pen);
            canvas.drawLine(-1,-2,4,-2,pen);
        }
        canvas.restore();
    }
}
