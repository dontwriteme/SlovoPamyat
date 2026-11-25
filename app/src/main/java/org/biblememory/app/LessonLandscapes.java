package org.biblememory.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.Random;

/** Six existing illustrations and ten original, lightweight vector scenes.
 * Indices are persisted, so keep their ordering stable when adding artwork. */
final class LessonLandscapes {
    static final int COUNT=16;
    private static final int[] ORIGINAL={
        R.drawable.hero_sunrise,R.drawable.hero_lake,R.drawable.hero_autumn,
        R.drawable.hero_forest,R.drawable.hero_evening,R.drawable.hero_valley
    };
    private static final String LAST="last_new_landscape";
    private LessonLandscapes(){}

    static int choose(SharedPreferences prefs,String identity,Random random){
        String key="scene:"+identity;
        int saved=prefs.getInt(key,-1);
        if(saved>=0&&saved<COUNT)return saved;
        int last=prefs.getInt(LAST,-1);
        int next;
        if(last>=0&&last<COUNT){
            next=random.nextInt(COUNT-1);
            if(next>=last)next++;
        }else next=random.nextInt(COUNT);
        prefs.edit().putInt(key,next).putInt(LAST,next).apply();
        return next;
    }

    static Drawable artwork(Context context,int index){
        if(index<0||index>=COUNT)index=0;
        if(index<ORIGINAL.length)return context.getDrawable(ORIGINAL[index]);
        return new Scene(index);
    }

    // Pale upper backgrounds preserve the contrast of brown text and cream cards.
    static int[] sky(int index){
        switch(index){
            case 7:case 13:return new int[]{0xFFF1EBE2,0xFFEDF0E4,0xFFDCE9DE};
            case 9:case 15:return new int[]{0xFFECEAE8,0xFFF3F1E9,0xFFDFE6E3};
            case 11:return new int[]{0xFFF0E8E8,0xFFF3E8E5,0xFFE5DDE7};
            case 8:case 14:return new int[]{0xFFF5E9DD,0xFFF8F0DB,0xFFE4E8C8};
            default:return new int[]{0xFFF5E7E2,0xFFFFEBD5,0xFFF9D9A7};
        }
    }

    /** Vector artwork is prepared once per drawable; draw performs no path creation. */
    private static final class Scene extends Drawable {
        private static final int W=1000,H=900;
        private final ArrayList<Layer> layers=new ArrayList<>();
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Shader sky;
        private int alpha=255;
        private ColorFilter filter;

        Scene(int index){
            String top,bottom;
            switch(index){
                case 6:top="E7C8B2";bottom="F8E4BD";break;
                case 7:top="D4E3DD";bottom="F6E7CE";break;
                case 8:top="E5DCCC";bottom="F6E6BA";break;
                case 9:top="CDDCE0";bottom="F4E7DE";break;
                case 10:top="D7E3D6";bottom="F7E8D3";break;
                case 11:top="8C92B0";bottom="D5BFCB";break;
                case 12:top="D8B9AD";bottom="F5D8B4";break;
                case 13:top="D0DDD5";bottom="F3E6CF";break;
                case 14:top="E8D7B8";bottom="F7EACB";break;
                default:top="D5DDDA";bottom="F2E8DC";break;
            }
            sky=new LinearGradient(0,0,0,H,c(top),c(bottom),Shader.TileMode.CLAMP);
            if(index!=11){
                oval("FFF0CC",682,132,66,66);
                cloud("F5E8D8",190,118,1f);
                cloud("F5E8D8",510,210,.7f);
                cloud("F5E8D8",910,94,.75f);
            }
            switch(index){
                case 6:desert();break;
                case 7:coast();break;
                case 8:meadow();break;
                case 9:winter();break;
                case 10:waterfall();break;
                case 11:night();break;
                case 12:canyon();break;
                case 13:river();break;
                case 14:olive();break;
                default:mist();break;
            }
        }

        private static int c(String hex){return Color.parseColor("#"+hex);}
        private void add(String color,Path path){layers.add(new Layer(c(color),path));}
        private void poly(String color,float... xy){
            Path p=new Path();p.moveTo(xy[0],xy[1]);
            for(int i=2;i<xy.length;i+=2)p.lineTo(xy[i],xy[i+1]);
            p.close();add(color,p);
        }
        private void oval(String color,float x,float y,float rx,float ry){
            Path p=new Path();p.addOval(x-rx,y-ry,x+rx,y+ry,Path.Direction.CW);add(color,p);
        }
        // Start point followed by groups of cubic control points/endpoints.
        private void ridge(String color,float x,float y,float... curves){
            Path p=new Path();p.moveTo(x,y);
            for(int i=0;i<curves.length;i+=6)
                p.cubicTo(curves[i],curves[i+1],curves[i+2],curves[i+3],curves[i+4],curves[i+5]);
            p.lineTo(W,H);p.lineTo(0,H);p.close();add(color,p);
        }
        private void cloud(String color,float x,float y,float scale){
            oval(color,x,y,74*scale,8*scale);
            oval(color,x-36*scale,y-5*scale,43*scale,7*scale);
        }
        private void pine(String color,float x,float base,float h){
            poly(color,x-3,base,x+3,base,x+3,base-h*.70f,x-3,base-h*.70f);
            for(int i=0;i<3;i++){
                float tip=base-h+i*h*.20f,half=h*(.19f+i*.035f);
                poly(color,x,tip,x-half,tip+h*.47f,x+half,tip+h*.47f);
            }
        }
        private void tree(String trunk,String foliage,float x,float base,float h){
            poly(trunk,x-h*.035f,base,x+h*.035f,base,x+h*.022f,base-h*.62f,x-h*.025f,base-h*.62f);
            oval(foliage,x,base-h*.64f,h*.32f,h*.28f);
            oval(foliage,x-h*.22f,base-h*.55f,h*.22f,h*.21f);
            oval(foliage,x+h*.24f,base-h*.58f,h*.22f,h*.23f);
        }
        private void ripples(String color,float y,float left,float right,int count){
            for(int i=0;i<count;i++){
                float x=left+(right-left)*((i*37%101)/101f);
                oval(color,x,y+i*13,12+(i%4)*11,1.6f);
            }
        }
        private void desert(){
            ridge("C7AFA0",0,340,180,220,290,365,460,305,650,220,805,260,1000,350);
            ridge("D7BC96",0,460,210,260,360,335,550,445,750,575,870,360,1000,390);
            ridge("E5CC9F",0,550,220,470,320,650,570,530,780,400,900,485,1000,570);
            ridge("C9A67F",0,720,210,550,460,690,630,760,790,820,900,650,1000,675);
            ridge("DDBA8E",0,790,250,675,420,820,680,770,815,740,920,780,1000,805);
            for(int i=0;i<8;i++){float x=45+i*129,y=845+(i%3)*15;oval("B79B78",x,y,24,2);}
            for(int i=0;i<3;i++){float x=96+i*38, y=866-i*17;poly("888F75",x,y,x-12,y-34,x,y-23,x+13,y-41,x+6,y);}
        }
        private void coast(){
            ridge("A7B9B4",0,375,170,315,220,280,390,360,600,445,780,310,1000,350);
            poly("94B8B5",0,410,1000,410,1000,900,0,900);
            ripples("CDDCD0",445,60,940,23);
            ridge("C6C6A2",0,625,190,535,300,690,510,745,725,810,880,590,1000,580);
            ridge("E4D2AD",0,718,260,640,365,765,570,815,760,855,905,680,1000,670);
            ridge("ADB99C",0,820,160,720,225,842,415,900,630,900,840,740,1000,770);
            for(int i=0;i<10;i++){float x=i<5?i*37:820+(i-5)*36;pine("839B8C",x,880,74+(i%3)*28);}
        }
        private void meadow(){
            ridge("B1BEAE",0,390,160,250,315,350,470,380,665,420,810,260,1000,395);
            ridge("A8BC96",0,490,170,400,360,500,540,430,740,350,850,490,1000,470);
            ridge("C1C99E",0,625,210,430,400,620,615,540,790,495,900,625,1000,580);
            ridge("94AC86",0,785,180,630,380,720,540,770,780,855,860,660,1000,700);
            for(int i=0;i<34;i++){
                float x=22+(i*137%960),y=740+(i*71%155);
                poly("788F70",x,y,x-2,y-24,x+2,y-24,x+3,y);
                oval(i%3==0?"F4DEAC":i%3==1?"E5B9AD":"F0E8CF",x,y-25,5,4);
            }
            tree("8A9279","91A883",125,756,145);
            tree("8A9279","98AC86",860,735,120);
        }
        private void winter(){
            poly("A5B9C3",0,425,172,240,280,348,445,176,620,382,780,239,1000,418,1000,900,0,900);
            poly("E8E5E2",172,240,116,326,178,299,221,324);
            poly("F4F0E8",445,176,368,286,445,260,515,299);
            poly("EEEAE4",780,239,704,333,777,304,835,336);
            ridge("BCCBCB",0,535,220,395,320,570,500,498,700,420,845,515,1000,445);
            ridge("E6EAE5",0,695,200,545,400,705,610,640,760,585,900,665,1000,580);
            for(int i=0;i<16;i++){float x=i*68,base=690+(i*53%75);pine("91A8A8",x,base,72+(i%4)*22);}
            ridge("F1EDE5",0,780,250,690,430,840,610,770,805,705,900,790,1000,750);
            for(int i=0;i<8;i++){float x=i<4?20+i*65:780+(i-4)*65;pine("718D8B",x,875,120+(i%3)*37);poly("E8ECE5",x,875-120-(i%3)*37,x-17,785-(i%3)*37,x+17,785-(i%3)*37);}
        }
        private void waterfall(){
            ridge("ACBBAF",0,372,175,245,390,365,500,310,680,245,820,400,1000,300);
            poly("A3A89C",0,475,220,390,410,414,605,375,1000,465,1000,900,0,900);
            poly("81998C",0,500,210,430,410,485,395,655,0,760);
            poly("8D9C90",620,455,820,405,1000,520,1000,795,640,660);
            poly("C7DBD1",420,410,606,402,584,620,655,735,365,735,425,607);
            poly("EEF0DB",467,418,527,410,510,604,548,715,474,715,489,597);
            poly("A3C2B6",0,725,1000,725,1000,900,0,900);
            oval("D8E6D6",505,733,155,20);
            ripples("D4E1CC",760,150,840,9);
            ridge("7D9986",0,830,160,720,240,795,325,900,580,960,760,810,1000,785);
            for(int i=0;i<7;i++){float x=i<4?20+i*65:850+(i-4)*54;pine("657F70",x,884,115+(i%3)*30);}
        }
        private void night(){
            oval("F5E8CB",734,148,57,57);
            oval("999BB4",754,130,53,53);
            for(int i=0;i<38;i++){
                float x=24+(i*137%951),y=27+(i*71%299);
                if(Math.abs(x-735)>90||Math.abs(y-148)>80)oval("F4E5D0",x,y,i%7==0?2.7f:1.4f,i%7==0?2.7f:1.4f);
            }
            poly("939DB4",0,465,172,280,370,450,536,260,690,445,845,320,1000,433,1000,900,0,900);
            ridge("8299A3",0,570,220,410,400,565,590,480,765,420,900,560,1000,520);
            poly("9EB6B5",0,620,1000,620,1000,900,0,900);
            ripples("C9D1C5",645,120,880,15);
            ridge("6D8887",0,850,180,705,300,820,485,900,660,920,830,735,1000,780);
            for(int i=0;i<10;i++){float x=i<5?i*43:820+(i-5)*43;pine("5D7978",x,890,98+(i%3)*37);}
        }
        private void canyon(){
            ridge("C5A698",0,380,210,285,340,400,480,360,690,280,850,330,1000,395);
            poly("CBA48C",0,350,180,330,205,410,325,410,345,500,410,535,420,900,0,900);
            poly("B79282",1000,335,850,312,826,385,733,395,708,490,625,520,600,900,1000,900);
            poly("DCC0A0",0,500,205,480,260,560,360,565,420,900,0,900);
            poly("C8A28D",1000,500,790,470,770,565,655,580,590,900,1000,900);
            poly("E2CBB0",0,690,310,610,455,545,540,545,690,615,1000,690,1000,900,0,900);
            poly("99B7B2",505,540,560,560,484,650,610,740,590,820,750,900,430,900,473,806,390,740,455,635);
            for(int i=0;i<6;i++){float x=40+i*30;poly("8D9A7C",x,875,x-6,843,x+4,851,x+8,830,x+10,875);}
        }
        private void river(){
            ridge("B3BFB0",0,390,170,290,330,430,510,340,695,270,855,430,1000,360);
            ridge("A0B49B",0,535,180,420,360,510,540,450,715,390,880,530,1000,450);
            ridge("C6CBA6",0,710,205,510,365,640,580,575,770,520,900,640,1000,600);
            poly("9ABCB8",530,485,570,492,520,555,660,640,600,710,770,800,860,900,475,900,480,815,405,710,510,640,460,555);
            for(int i=0;i<8;i++)oval("CFDED1",540+(i*31%92),650+i*27,22+i*3,2);
            ridge("8DA78B",0,815,180,670,280,785,400,900,550,990,760,840,1000,820);
            for(int i=0;i<9;i++){float x=i<5?i*70:780+(i-5)*65;tree("87917A","779A83",x,860,96+(i%3)*32);}
        }
        private void olive(){
            ridge("B7BDA2",0,400,210,270,340,415,530,335,720,270,855,400,1000,370);
            ridge("D2C5A0",0,550,210,420,345,560,555,480,715,425,900,500,1000,450);
            ridge("B5BD95",0,690,220,525,450,680,625,595,785,540,900,640,1000,580);
            ridge("D8CAAA",0,840,200,690,420,795,620,760,775,735,920,690,1000,730);
            for(int i=0;i<5;i++)tree("99947B","A4B397",95+i*202,650+(i%2)*35,82);
            for(int i=0;i<5;i++)tree("7F8068","899E80",50+i*235,865-(i%2)*35,155+(i%3)*10);
            for(int i=0;i<16;i++)oval("B4B79A",28+(i*139%950),820+(i*73%65),11,2);
        }
        private void mist(){
            ridge("B9C8C4",0,385,160,240,330,430,510,335,740,240,860,410,1000,330);
            ridge("A2B7B1",0,480,220,350,385,525,540,450,760,350,850,490,1000,440);
            for(int i=0;i<22;i++)pine("91AAA5",i*49,605+(i*37%40),90+(i%5)*25);
            ridge("D5DDD4",0,620,200,545,365,650,520,595,710,530,900,640,1000,565);
            for(int i=0;i<15;i++)pine("839F97",i*74,760+(i*47%80),135+(i%4)*35);
            ridge("BBCBC0",0,765,170,695,350,790,550,730,760,665,890,760,1000,715);
            for(int i=0;i<10;i++){float x=i<5?i*47:800+(i-5)*49;pine("68877D",x,900,160+(i%3)*45);}
        }
        @Override public int getIntrinsicWidth(){return W;}
        @Override public int getIntrinsicHeight(){return H;}
        @Override public void draw(Canvas canvas){
            Rect b=getBounds();if(b.isEmpty())return;
            int save=canvas.save();
            canvas.translate(b.left,b.top);
            canvas.scale(b.width()/(float)W,b.height()/(float)H);
            paint.setShader(sky);paint.setColor(Color.WHITE);paint.setAlpha(alpha);paint.setColorFilter(filter);
            canvas.drawRect(0,0,W,H,paint);
            paint.setShader(null);
            for(Layer layer:layers){paint.setColor(layer.color);paint.setAlpha(alpha);canvas.drawPath(layer.path,paint);}
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha){this.alpha=alpha;invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){this.filter=filter;invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        private static final class Layer{
            final int color;final Path path;
            Layer(int color,Path path){this.color=color;this.path=path;}
        }
    }
}
