package app.hyperscan.android;
import android.content.Context;
import android.graphics.*;
import android.view.View;
/** Only paints geometry returned by the detector, never a static pretend detection frame. */
final class DocumentOverlay extends View {
    private double[] corners; private int imageWidth,imageHeight; private boolean stable;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    DocumentOverlay(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
    void update(double[] q,int w,int h,boolean stable){corners=q;imageWidth=w;imageHeight=h;this.stable=stable;invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(corners==null || imageWidth==0 || imageHeight==0)return;
        float scale=Math.min(getWidth()/(float)imageWidth,getHeight()/(float)imageHeight),dx=(getWidth()-imageWidth*scale)/2,dy=(getHeight()-imageHeight*scale)/2;
        Path path=new Path();for(int i=0;i<4;i++){float x=dx+(float)corners[i*2]*imageWidth*scale,y=dy+(float)corners[i*2+1]*imageHeight*scale;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();
        paint.setColor(stable?Color.rgb(157,233,176):Color.rgb(213,190,255));paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(3*getResources().getDisplayMetrics().density);canvas.drawPath(path,paint);
        paint.setStyle(Paint.Style.FILL);paint.setAlpha(25);canvas.drawPath(path,paint);paint.setAlpha(255);
    }
}
