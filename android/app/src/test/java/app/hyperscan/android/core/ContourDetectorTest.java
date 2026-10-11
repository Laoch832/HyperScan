package app.hyperscan.android.core;
import org.junit.Test;
import static org.junit.Assert.*;
import java.util.Arrays;
public class ContourDetectorTest {
    private int[] paper(int w,int h,double[] q,boolean dark,boolean shadow){int[] pixels=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++){double xx=x/(double)(w-1),yy=y/(double)(h-1);boolean inside=true;for(int i=0;i<4;i++){int a=i*2,b=((i+1)%4)*2;if((q[b]-q[a])*(yy-q[a+1])-(q[b+1]-q[a+1])*(xx-q[a])<0)inside=false;}int v=inside?(dark?35:shadow?175+y*65/h:240):(dark?225:30);if(inside && y%29<3 && x>w*.3 && x<w*.6)v=dark?200:50;pixels[y*w+x]=0xff000000|v<<16|v<<8|v;}return pixels;}
    @Test public void findsSlantedPageWithText(){double[] q={.18,.12,.83,.2,.75,.88,.1,.79};double[] found=ContourDetector.detect(paper(480,640,q,false,false),480,640);assertNotNull(found);for(int i=0;i<8;i++)assertEquals(q[i],found[i],.035);}
    @Test public void findsShadowedPageAndDarkPage(){double[] q={.2,.1,.8,.16,.84,.86,.14,.91};for(boolean dark:new boolean[]{false,true}){double[] found=ContourDetector.detect(paper(640,480,q,dark,!dark),640,480);assertNotNull(found);for(int i=0;i<8;i++)assertEquals(q[i],found[i],.04);}}
    @Test public void rejectsBlankAndNearlyFullFrame(){int[] blank=new int[480*640];Arrays.fill(blank,0xffeeeeee);assertNull(ContourDetector.detect(blank,480,640));double[] q={0,0,1,0,1,1,0,1};assertNull(ContourDetector.detect(paper(480,640,q,false,false),480,640));}
    @Test public void rejectsRoundObject(){int w=400,h=400;int[] p=new int[w*h];for(int y=0;y<h;y++)for(int x=0;x<w;x++)p[y*w+x]=(x-200)*(x-200)+(y-200)*(y-200)<140*140?0xffeeeeee:0xff202020;assertNull(ContourDetector.detect(p,w,h));}
    @Test public void validatesCornerGeometry(){assertFalse(ContourDetector.valid(new double[]{.1,.1,.9,.9,.9,.1,.1,.9}));assertFalse(ContourDetector.valid(new double[]{Double.NaN,.1,.9,.1,.9,.9,.1,.9}));assertTrue(ContourDetector.valid(new double[]{.1,.1,.9,.1,.9,.9,.1,.9}));}
}
