package org.biblememory.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class CollectionsBookMotionTest {
    private static final float EPS=.002f;
    private CollectionsBookMotion fresh(){CollectionsBookMotion m=new CollectionsBookMotion();m.reset();return m;}
    @Test public void referenceHasTwoUprightBooksAndOneHorizontal(){
        CollectionsBookMotion m=fresh();
        assertEquals(0,m.cycle());assertFalse(m.isRunning());assertEquals(0,m.starPulse(),EPS);
        for(int i=0;i<3;i++){assertEquals(i,m.poses[i].slot);assertEquals(0,m.poses[i].angle,EPS);}
        assertTrue(m.poses[0].y<0);assertTrue(m.poses[1].y<0);assertTrue(m.poses[2].y>0);
    }
    @Test public void bothShelvesParticipateAndQuarterTurnsAreContinuous(){
        CollectionsBookMotion m=fresh();m.start();m.sample(325);
        assertTrue(m.poses[0].scale>1);assertTrue(m.poses[1].scale>1);assertTrue(m.poses[2].scale>1);
        m.sample(650);assertTrue(m.poses[0].angle<0&&m.poses[0].angle>-90);
        assertTrue(m.poses[2].angle>0&&m.poses[2].angle<90);
        m.finish();assertEquals(2,m.poses[0].slot);assertEquals(0,m.poses[1].slot);assertEquals(1,m.poses[2].slot);
        assertEquals(-90,m.poses[0].angle,EPS);assertEquals(90,m.poses[2].angle,EPS);
    }
    @Test public void allPermutationsRemainSeparateAndInsideTheIcon(){
        CollectionsBookMotion m=fresh();
        for(int cycle=0;cycle<3;cycle++){
            m.start();
            for(int ms=0;ms<=1500;ms+=2){
                m.sample(ms);double[][][] points=new double[3][][];
                for(int i=0;i<3;i++){
                    points[i]=corners(i,m.poses[i]);
                    for(double[] point:points[i]){assertTrue("clipped x at "+ms,Math.abs(point[0])<=13.5);assertTrue("clipped y at "+ms,Math.abs(point[1])<=13.5);}
                }
                for(int i=0;i<3;i++)for(int j=i+1;j<3;j++)assertFalse("overlap cycle "+cycle+" time "+ms+" books "+i+"/"+j,overlap(points[i],points[j]));
            }
            m.finish();
        }
        assertEquals(0,m.cycle());
    }
    @Test public void repeatContinuesFromTheActualFinishedPermutation(){
        CollectionsBookMotion m=fresh();m.start();m.finish();
        float x=m.poses[0].x,y=m.poses[0].y,a=m.poses[0].angle,s=m.poses[0].scale;
        m.start();assertEquals(x,m.poses[0].x,EPS);assertEquals(y,m.poses[0].y,EPS);
        assertEquals(a,m.poses[0].angle,EPS);assertEquals(s,m.poses[0].scale,EPS);
        m.sample(650);x=m.poses[0].x;m.start();assertEquals(x,m.poses[0].x,EPS);
        assertEquals(2,m.settledCycle());
    }
    @Test public void exitDuringFlightRestoresEveryReferencePose(){
        CollectionsBookMotion m=fresh(),reference=fresh();m.start();m.sample(650);m.reset();
        for(int i=0;i<3;i++){
            assertEquals(reference.poses[i].x,m.poses[i].x,EPS);assertEquals(reference.poses[i].y,m.poses[i].y,EPS);
            assertEquals(reference.poses[i].angle,m.poses[i].angle,EPS);assertEquals(1,m.poses[i].scale,EPS);
        }
        assertFalse(m.isRunning());assertEquals(0,m.starPulse(),EPS);
    }
    @Test public void starFlashesOnlyAfterAllBooksAreSeated(){
        CollectionsBookMotion m=fresh();m.start();m.sample(1249);assertEquals(0,m.starPulse(),EPS);
        m.sample(1250);float x=m.poses[0].x,y=m.poses[0].y;
        m.sample(1375);assertTrue(m.starPulse()>.9);assertEquals(x,m.poses[0].x,EPS);assertEquals(y,m.poses[0].y,EPS);
        m.sample(1500);assertEquals(0,m.starPulse(),EPS);m.finish();assertFalse(m.isRunning());
    }
    @Test public void finalBooksFitTheirShelfSlotsForEveryOrder(){
        CollectionsBookMotion m=fresh();
        for(int c=0;c<3;c++){
            m.start();m.finish();
            for(int i=0;i<3;i++){
                CollectionsBookMotion.Pose p=m.poses[i];double[][] pts=corners(i,p);
                double maxY=-100,minX=100,maxX=-100;
                for(double[] point:pts){maxY=Math.max(maxY,point[1]);minX=Math.min(minX,point[0]);maxX=Math.max(maxX,point[0]);}
                assertEquals(p.slot==2?5.88:.87,maxY,EPS);
                assertTrue(minX>=-6.11);assertTrue(maxX<=1.41);
                if(p.slot==2)assertEquals("Lower stack must retain its full width",7.5,maxX-minX,EPS);
            }
        }
    }
    // Independent oriented-rectangle collision check, including intermediate rotation.
    private static double[][] corners(int book,CollectionsBookMotion.Pose p){
        double w=CollectionsBookMotion.width(book)*p.scale/2,h=CollectionsBookMotion.height(book)*p.scale/2;
        double c=Math.cos(Math.toRadians(p.angle)),s=Math.sin(Math.toRadians(p.angle));
        double[][] result=new double[4][2];
        for(int n=0;n<4;n++){double x=(n==0||n==3)?-w:w,y=n<2?-h:h;result[n][0]=p.x+c*x-s*y;result[n][1]=p.y+s*x+c*y;}
        return result;
    }
    private static boolean overlap(double[][] a,double[][] b){
        for(double[][] shape:new double[][][]{a,b})for(int edge=0;edge<2;edge++){
            double nx=shape[edge+1][1]-shape[edge][1],ny=shape[edge][0]-shape[edge+1][0];
            double amin=Double.POSITIVE_INFINITY,amax=-amin,bmin=amin,bmax=-amin;
            for(double[] p:a){double v=p[0]*nx+p[1]*ny;amin=Math.min(amin,v);amax=Math.max(amax,v);}
            for(double[] p:b){double v=p[0]*nx+p[1]*ny;bmin=Math.min(bmin,v);bmax=Math.max(bmax,v);}
            if(amax<=bmin+.00001||bmax<=amin+.00001)return false;
        }
        return true;
    }
}
