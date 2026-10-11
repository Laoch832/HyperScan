package app.hyperscan.android.core;

import android.content.Context;
import android.graphics.*;
import android.util.Log;
import com.taobao.android.alinnkit.alinn.AliNNNetNative;
import java.io.*;
import java.util.*;

/** Original packaged RGB classifier + four-corner model, with an explicitly named contour fallback. */
public final class DocumentDetector implements AutoCloseable {
    public static final class Result {
        public final double[] corners; public final String engine;
        Result(double[] corners,String engine){this.corners=corners;this.engine=engine;}
    }
    private long clsNet,clsSession,edgeNet,edgeSession;
    private boolean attempted;
    private final Context context;
    public DocumentDetector(Context context){this.context=context.getApplicationContext();}
    private void initialize() throws Exception {
        System.loadLibrary("c++_shared");System.loadLibrary("MNN");System.loadLibrary("mnnkitcore");
        clsNet=AliNNNetNative.nativeCreateNetFromFile(model("doc_cls-2023-07-13.mnn").getPath());
        edgeNet=AliNNNetNative.nativeCreateNetFromFile(model("doc_4_detect_0818.mnn").getPath());
        if(clsNet==0 || edgeNet==0)throw new IOException("Original model did not load");
        clsSession=AliNNNetNative.nativeCreateSession(clsNet,0,2,0,0,0,null);
        edgeSession=AliNNNetNative.nativeCreateSession(edgeNet,0,2,0,0,0,null);
        if(clsSession==0 || edgeSession==0)throw new IOException("Original CPU session failed");
        Log.i("HyperScanEdges","ORIGINAL_MODELS_READY classifier=224 corners=416 padding=8");
    }
    private synchronized File model(String name) throws Exception {
        File directory=new File(context.getFilesDir(),"scanner-models");if(!directory.exists() && !directory.mkdirs())throw new IOException("Model directory failed");
        File target=new File(directory,name); File temp=File.createTempFile("model-",".tmp",directory);
        // APK assets are checked by release audit; always refresh to avoid stale models after updates.
        try(InputStream in=context.getAssets().open("scanner-models/"+name);OutputStream out=new FileOutputStream(temp)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);}
        if(!temp.renameTo(target)){temp.delete();throw new IOException("Cannot install model");}
        return target;
    }
    public synchronized Result detect(Bitmap bitmap) {
        if(!attempted){attempted=true;try{initialize();}catch(Exception|LinkageError error){Log.e("HyperScanEdges","ORIGINAL_MODEL_INIT_FAILED; using local contours",error);close();}}
        if(edgeSession!=0)try{
            float[] cls=infer(bitmap,clsNet,clsSession,224,0,.5f,.5f);
            if(cls.length<5)throw new IOException("Unexpected classifier output "+cls.length);
            int type=argmax(cls,0,3);if(argmax(cls,3,5)==4)type=3;
            if(type==1 || type==2){
                float[] raw=infer(bitmap,edgeNet,edgeSession,416,8,.442f,.281f);
                if(raw.length!=8)throw new IOException("Unexpected corner output "+raw.length);
                double scale=Math.min(400.0/bitmap.getWidth(),400.0/bitmap.getHeight());
                int rw=(int)Math.rint(bitmap.getWidth()*scale),rh=(int)Math.rint(bitmap.getHeight()*scale);
                double[] q=new double[8];
                // Clip after converting back from model padding, matching the original worker.
                for(int i=0;i<8;i++)q[i]=Math.max(0,Math.min(1,(((raw[i]+1)*416-1)/2.0-8)/(i%2==0?rw:rh)));
                if(ContourDetector.valid(q))return new Result(q,"original-mnn-four-corner");
            }
            // Class 0 is full-frame; class 3 is curved/book. Do not fabricate a reliable quad.
        }catch(Exception|LinkageError error){Log.e("HyperScanEdges","ORIGINAL_INFERENCE_FAILED; using local contours",error);close();}
        int w=bitmap.getWidth(),h=bitmap.getHeight();int[] pixels=new int[w*h];bitmap.getPixels(pixels,0,w,0,0,w,h);
        return new Result(ContourDetector.detect(pixels,w,h),"hyperscan-local-contour");
    }
    private static int argmax(float[] a,int from,int to){int best=from;for(int i=from+1;i<to;i++)if(a[i]>a[best])best=i;return best;}
    private float[] infer(Bitmap original,long net,long session,int size,int padding,float mean,float std) throws Exception {
        long input=AliNNNetNative.nativeGetSessionInput(net,session,null),output=AliNNNetNative.nativeGetSessionOutput(net,session,null);
        if(input==0 || output==0)throw new IOException("Missing model tensors");
        int[] dims=AliNNNetNative.nativeTensorGetDimensions(input);boolean nchw=dims.length==4 && dims[1]==3;
        if(dims.length!=4 || (!nchw && dims[3]!=3))throw new IOException("Unsupported tensor shape "+Arrays.toString(dims));
        int height=nchw?dims[2]:dims[1],width=nchw?dims[3]:dims[2];if(width!=size || height!=size)throw new IOException("Model input size mismatch "+Arrays.toString(dims));
        int rw=size,rh=size;if(padding>0){double s=(size-2.0*padding)/Math.max(original.getWidth(),original.getHeight());rw=Math.max(1,(int)Math.rint(original.getWidth()*s));rh=Math.max(1,(int)Math.rint(original.getHeight()*s));}
        Bitmap resized=Bitmap.createScaledBitmap(original,rw,rh,true);int[] pixels=new int[rw*rh];resized.getPixels(pixels,0,rw,0,0,rw,rh);if(resized!=original)resized.recycle();
        int format=AliNNNetNative.nativeTensorGetDimensionType(input);
        boolean packed=format==2;
        float[] values=new float[size*size*(packed?4:3)];Arrays.fill(values,-mean/std);
        if(AliNNNetNative.nativeTensorGetData(input,null)!=values.length)throw new IOException("Unexpected input storage size; refusing raw tensor write");
        for(int y=0;y<rh;y++)for(int x=0;x<rw;x++){int color=pixels[y*rw+x],position=(y+padding)*size+x+padding;for(int c=0;c<3;c++){float v=(((color>>(16-8*c))&255)/255f-mean)/std;values[packed?position*4+c:nchw?c*size*size+position:position*3+c]=v;}}
        AliNNNetNative.nativeSetInputFloatData(net,input,values);if(AliNNNetNative.nativeRunSession(net,session)!=0)throw new IOException("Model inference failed");
        int count=AliNNNetNative.nativeTensorGetData(output,null);if(count<1 || count>64)throw new IOException("Unexpected tensor length");float[] result=new float[count];AliNNNetNative.nativeTensorGetData(output,result);for(float v:result)if(!Float.isFinite(v))throw new IOException("Non-finite result");
        int[] outputDims=AliNNNetNative.nativeTensorGetDimensions(output);
        if(AliNNNetNative.nativeTensorGetDimensionType(output)==2 && outputDims.length==4){
            int batches=outputDims[0],channels=outputDims[1],area=outputDims[2]*outputDims[3];
            if(batches*channels*area>64 || ((channels+3)/4)*4*area*batches!=result.length)throw new IOException("Unsupported packed output shape");
            float[] logical=new float[batches*channels*area];for(int n=0;n<batches;n++)for(int c=0;c<channels;c++)for(int xy=0;xy<area;xy++)logical[(n*channels+c)*area+xy]=result[((n*((channels+3)/4)+c/4)*area+xy)*4+c%4];result=logical;
        }
        return result;
    }
    @Override public synchronized void close(){if(clsSession!=0)AliNNNetNative.nativeReleaseSession(clsNet,clsSession);if(edgeSession!=0)AliNNNetNative.nativeReleaseSession(edgeNet,edgeSession);if(clsNet!=0)AliNNNetNative.nativeReleaseNet(clsNet);if(edgeNet!=0)AliNNNetNative.nativeReleaseNet(edgeNet);clsSession=edgeSession=clsNet=edgeNet=0;}
}
