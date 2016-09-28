package com.yang.raindrops;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * Created by qinfeng on 2016/9/27.
 */

public class RainingImageView extends GLSurfaceView{

    private Bitmap mSrcImage;
    private Context mContext;

    public RainingImageView(Context context) {
        super(context);
        init(context,null);
    }

    public RainingImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context,attrs);
    }

    private void handleTypedArray(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray typedArray = context.obtainStyledAttributes(attrs, R.styleable.RainingImageView);
        int srcImageResourceId = typedArray.getResourceId(R.styleable.RainingImageView_srcImage,0);
        mSrcImage =  BitmapFactory.decodeResource(context.getResources(),srcImageResourceId);
    }
    private void init(Context context, AttributeSet attrs) {
        handleTypedArray(context,attrs);

        mContext = context;
        setEGLContextClientVersion(2);
        RainingRenderer rainingRenderer = new RainingRenderer();
        setRenderer(rainingRenderer);
    }

    public class RainingRenderer implements GLSurfaceView.Renderer {

        private RainDropLayer mRainDropLayer;
        private RainDrops mRainDrops;

        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {

            mRainDropLayer = new RainDropLayer(mContext,getWidth(),getHeight(),mSrcImage);
            mRainDropLayer.setAlphaMultiply(6);
            mRainDropLayer.setAlphaSubtract(3);
            mRainDropLayer.setParallaxFg(10);
            mRainDropLayer.setBrightness(1.1f);
            final BitmapFactory.Options options = new BitmapFactory.Options();
            options.inScaled = false;
            final Bitmap dropColor = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.drop_color, options);
            final Bitmap dropAlpha = BitmapFactory.decodeResource(mContext.getResources(), R.drawable.drop_alpha, options);

            mRainDrops = new RainDrops(getWidth(),getHeight(), 2.0f, dropColor, dropAlpha);

            mRainDrops.setRainChance(0.35f);

            mRainDrops.setRainLimit(6);

            mRainDrops.setDropletsRate(20);

            int[] a =  { 2 , 3};
            mRainDrops.setDropletsSize(a);

            mRainDrops.setTrailRate(1);

            float[] t =  { 0.25f , 0.35f};
            mRainDrops.setTrailScaleRange(t);

            GLES20.glClearColor ( 1.0f, 1.0f, 1.0f, 0.0f );
        }

        @Override
        public void onDrawFrame(GL10 unused) {
            // Set the view-port
            GLES20.glViewport ( 0, 0,getWidth(),getHeight() );
            // Clear the color buffer
            GLES20.glClear ( GLES30.GL_COLOR_BUFFER_BIT );
            mRainDrops.update();
            mRainDropLayer.draw(mRainDrops.getCanvasBitmap());

        }

        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            // Adjust the viewport based on geometry changes,
            // such as screen rotation
            GLES20.glViewport(0, 0, width, height);
            mRainDropLayer.setIsFirst(true);
        }

    }
}
