package app.hyperscan.android.core;

import java.util.*;

/** HyperScan's model-free fallback. Never labelled as the original MNN detector. */
public final class ContourDetector {
    public static double[] detect(int[] argb,int width,int height) {
        if(width<32 || height<32 || argb.length!=width*height) return null;
        double scale=Math.min(1,320.0/Math.max(width,height)); int w=Math.max(1,(int)Math.round(width*scale)),h=Math.max(1,(int)Math.round(height*scale));
        int[] gray=new int[w*h],hist=new int[256];
        for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
            int c=argb[Math.min(height-1,(int)(y/scale))*width+Math.min(width-1,(int)(x/scale))];
            int v=(((c>>16)&255)*77+((c>>8)&255)*150+(c&255)*29)>>8;gray[y*w+x]=v;hist[v]++;
        }
        int low=0,high=255,total=w*h; while(low<255 && hist[low]==0)low++;while(high>0 && hist[high]==0)high--;
        if(high-low<35) return null;
        double sum=0;for(int i=0;i<256;i++)sum+=i*(double)hist[i];
        double sumBack=0,max=-1;int back=0,threshold=128;
        for(int i=0;i<256;i++){back+=hist[i];if(back==0)continue;int front=total-back;if(front==0)break;sumBack+=i*(double)hist[i];double diff=sumBack/back-(sum-sumBack)/front;double variance=(double)back*front*diff*diff;if(variance>max){max=variance;threshold=i;}}
        double[] best=null;double score=0;
        for(int delta:new int[]{0,20,-20}) for(boolean bright:new boolean[]{true,false}) {
            boolean[] mask=new boolean[total],seen=new boolean[total];int cut=Math.max(low,Math.min(high-1,threshold+delta));
            for(int y=1;y<h-1;y++)for(int x=1;x<w-1;x++){
                int hits=0;for(int yy=y-1;yy<=y+1;yy++)for(int xx=x-1;xx<=x+1;xx++)if((gray[yy*w+xx]>cut)==bright)hits++;
                mask[y*w+x]=hits>=5;
            }
            int[] queue=new int[total];
            for(int start=0;start<total;start++){
                if(seen[start] || !mask[start])continue;int tail=1,head=0;queue[0]=start;seen[start]=true;
                int xmin=w,ymin=h,xmax=0,ymax=0;
                int[] neighbours=new int[4];
                while(head<tail){int pos=queue[head++],x=pos%w,y=pos/w;xmin=Math.min(xmin,x);xmax=Math.max(xmax,x);ymin=Math.min(ymin,y);ymax=Math.max(ymax,y);
                    neighbours[0]=pos-1;neighbours[1]=pos+1;neighbours[2]=pos-w;neighbours[3]=pos+w;
                    for(int n:neighbours)if(n>=0 && n<total && !seen[n] && mask[n]){seen[n]=true;queue[tail++]=n;}
                }
                if(tail<total*.12 || tail>total*.94 || xmax-xmin<w*.15 || ymax-ymin<h*.15)continue;
                int touching=(xmin<=2?1:0)+(ymin<=2?1:0)+(xmax>=w-3?1:0)+(ymax>=h-3?1:0);if(touching>=3)continue;
                ArrayList<int[]> points=new ArrayList<>();
                // Extrema per scanline are enough for the convex hull, avoiding a point per pixel.
                int[] left=new int[h],right=new int[h];Arrays.fill(left,w);Arrays.fill(right,-1);
                for(int i=0;i<tail;i++){int pos=queue[i],y=pos/w,x=pos%w;left[y]=Math.min(left[y],x);right[y]=Math.max(right[y],x);}
                for(int y=ymin;y<=ymax;y++)if(right[y]>=0){points.add(new int[]{left[y],y});points.add(new int[]{right[y],y});}
                ArrayList<int[]> hull=hull(points);double hullArea=area(hull);if(hull.size()<4 || tail/hullArea<.68)continue;
                while(hull.size()>4){int remove=0;double least=Double.MAX_VALUE;for(int i=0;i<hull.size();i++){double a=Math.abs(cross(hull.get((i+hull.size()-1)%hull.size()),hull.get(i),hull.get((i+1)%hull.size())));if(a<least){least=a;remove=i;}}hull.remove(remove);}
                double quadArea=area(hull);if(quadArea<total*.12 || hullArea/quadArea>1.15)continue;
                double[] q=new double[8];int first=0;for(int i=1;i<4;i++)if(hull.get(i)[0]+hull.get(i)[1]<hull.get(first)[0]+hull.get(first)[1])first=i;
                for(int i=0;i<4;i++){int[] p=hull.get((first+i)%4);q[2*i]=p[0]/(double)(w-1);q[2*i+1]=p[1]/(double)(h-1);}
                if(!valid(q))continue;
                double candidate=quadArea*(tail/hullArea);if(candidate>score){score=candidate;best=q;}
            }
        }
        return best;
    }
    public static boolean valid(double[] q){
        if(q==null || q.length!=8)return false;double area=0;
        for(int i=0;i<4;i++){int a=i*2,b=((i+1)%4)*2,c=((i+2)%4)*2;if(!Double.isFinite(q[a]) || !Double.isFinite(q[a+1]) || q[a]<0 || q[a]>1 || q[a+1]<0 || q[a+1]>1)return false;
            double ux=q[b]-q[a],uy=q[b+1]-q[a+1],vx=q[c]-q[b],vy=q[c+1]-q[b+1];if(ux*vy-uy*vx<.004)return false;
            double dot=(-ux*vx-uy*vy)/Math.sqrt((ux*ux+uy*uy)*(vx*vx+vy*vy));if(Math.abs(dot)>.9)return false;
            area+=q[a]*q[b+1]-q[a+1]*q[b];}
        return area/2>=.12 && area/2<=.96;
    }
    private static double cross(int[] a,int[] b,int[] c){return (double)(b[0]-a[0])*(c[1]-a[1])-(double)(b[1]-a[1])*(c[0]-a[0]);}
    private static double area(List<int[]> p){double a=0;for(int i=0;i<p.size();i++){int[] x=p.get(i),y=p.get((i+1)%p.size());a+=(double)x[0]*y[1]-(double)x[1]*y[0];}return Math.abs(a)/2;}
    private static ArrayList<int[]> hull(ArrayList<int[]> p){p.sort((a,b)->a[0]==b[0]?Integer.compare(a[1],b[1]):Integer.compare(a[0],b[0]));ArrayList<int[]> h=new ArrayList<>();for(int[] v:p){while(h.size()>1 && cross(h.get(h.size()-2),h.get(h.size()-1),v)<=0)h.remove(h.size()-1);h.add(v);}int lower=h.size();for(int i=p.size()-2;i>=0;i--){int[] v=p.get(i);while(h.size()>lower && cross(h.get(h.size()-2),h.get(h.size()-1),v)<=0)h.remove(h.size()-1);h.add(v);}if(h.size()>1)h.remove(h.size()-1);return h;}
    private ContourDetector(){}
}
