/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yang.raindrops;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.renderscript.Allocation;
import android.renderscript.Element;
import android.renderscript.RenderScript;
import android.renderscript.ScriptIntrinsicBlur;

import com.yang.raindrops.utils.ESShader;
import com.yang.raindrops.utils.RawResourceReader;
import com.yang.raindrops.utils.TextureHelper;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class RainDropLayer
{


    private final Context mContext;
    private boolean isFirst =true;
    private int renderShadow = 0;
    private int renderShine = 0;
    private int minRefraction = 256;
    private int maxRefraction = 512;
    private float brightness = 1f;



    private int alphaMultiply = 20;
    private int alphaSubtract = 5;
    private int parallaxBg = 5;
    private int parallaxFg = 20;
    private float parallaX = 0f;
    private float parallaY = 0f;

    private int mResolution;
    private int mTextureRatio;
    private int mRenderShine;
    private int mRenderShadow;
    private int mMinRefraction;
    private int mRefractionDelta;
    private int mBrightness;
    private int mAlphaMultiply;
    private int mAlphaSubtract;
    private int mParallaxBg;
    private int mParallaxFg;
    private float mWidth;
    private float mHeight;
    private int mParallax;


    private final ShortBuffer indiceBuffer;
    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;	// buffer holding the textureCoordsData coordinates

    private float textureCoordsData[] = {
            // Mapping coordinates for the vertices
            0.0f,  0.0f, 	// top left		(V2)
            0.0f,  1.0f, 	// bottom left	(V1)
            1.0f,  1.0f, 	// top right	(V4)
            1.0f,  0.0f	// bottom right	(V3)
    };

    static float vertexsData[] = {
            -1f,  1f,
            -1f, -1f,
            1f, -1f,
            1f,  1f,
    };
    private final short[] mIndicesData =
            {
                    0, 1, 2, 0, 2, 3
            };

    private final int mProgrammerHandle;
    private final int mBackgroundTextureHandle;
    private final int mForegroundtextureHandle;
    private  int mDropShineHandle;
    private int mRainMapTextureHandle;

    private int mPositionHandle;
    private int mTexCoordHandle;

    private int mTextureUniformHandleWaterMap;
    private int mTextureUniformHandleShine;
    private int mTextureUniformHandleFg;
    private int mTextureUniformHandleBg;


    public RainDropLayer(Context context, int width, int height, Bitmap srcImage) {
        mWidth = width;
        mHeight = height;

        mContext = context;
        vertexBuffer = ByteBuffer.allocateDirect(vertexsData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(vertexsData);
        vertexBuffer.position(0);

        indiceBuffer = ByteBuffer.allocateDirect ( mIndicesData.length * 2 )
                .order ( ByteOrder.nativeOrder() ).asShortBuffer();
        indiceBuffer.put ( mIndicesData ).position ( 0 );

        textureBuffer = ByteBuffer.allocateDirect(textureCoordsData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.put(textureCoordsData);
        textureBuffer.position(0);

        mProgrammerHandle = ESShader.loadProgram(RawResourceReader.readTextFileFromRawResource(context, R.raw.simple_vertex_shader), RawResourceReader.readTextFileFromRawResource(context,R.raw.water_fragment_shader));
        Bitmap bitmap = srcImage;
        Bitmap crop = cropCenter(width, height, bitmap);
        Bitmap bg = blur(context, crop,20);
        Bitmap scaled = Bitmap.createScaledBitmap(crop, (int) (crop.getWidth() / 2f), (int) (crop.getHeight() / 2f), false);
        Bitmap fg = blur(context, scaled,10);

        mBackgroundTextureHandle = TextureHelper.loadTexture(bg);
        mForegroundtextureHandle = TextureHelper.loadTexture(fg);
        mDropShineHandle = TextureHelper.loadTexture(context, R.drawable.drop_shine);



    }

    public Bitmap cropCenter(int width,int height,Bitmap src) {
        Bitmap dst;
        float dstAspectRatio = width * 1f / height;
        float srcAspectRatio = src.getWidth() * 1f / src.getHeight();
        if (srcAspectRatio > dstAspectRatio ) {
            //src widther
            float scale = height * 1f / src.getHeight();
            int scaledWidth = (int) (src.getWidth() * scale);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(src, scaledWidth, height, false);
            int startX =(int)((scaledWidth - width) / 2f) ;
            dst = Bitmap.createBitmap(scaledBitmap, startX,0,width, height);
        }else {

            float scale = width * 1f / src.getWidth();
            int scaledHeight = (int) (src.getHeight() * scale);
            Bitmap scaledBitmap = Bitmap.createScaledBitmap(src, width,scaledHeight,  false);
            int startY =(int)((scaledHeight - height) / 2f) ;
            dst = Bitmap.createBitmap(scaledBitmap,0, startY,width, height);
        }

        return dst;

    }


    public  Bitmap blur(Context context, Bitmap image,float radius) {
        int width = Math.round(image.getWidth() * 1.0f);
        int height = Math.round(image.getHeight() * 1.0f);
        Bitmap inputBitmap = Bitmap.createScaledBitmap(image, width, height, false);
        Bitmap outputBitmap = Bitmap.createBitmap(inputBitmap);

        RenderScript rs = RenderScript.create(context);
        ScriptIntrinsicBlur theIntrinsic = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs));
        Allocation tmpIn = Allocation.createFromBitmap(rs, inputBitmap);
        Allocation tmpOut = Allocation.createFromBitmap(rs, outputBitmap);
        theIntrinsic.setRadius(radius);
        theIntrinsic.setInput(tmpIn);
        theIntrinsic.forEach(tmpOut);
        tmpOut.copyTo(outputBitmap);

        return outputBitmap;
    }



    public void draw(Bitmap rainMap) {


        GLES20.glUseProgram(mProgrammerHandle);



        mPositionHandle = GLES20.glGetAttribLocation(mProgrammerHandle, "a_position");
        GLES20.glVertexAttribPointer(
                mPositionHandle, 2,
                GLES20.GL_FLOAT, false,
                0, vertexBuffer);
        GLES20.glEnableVertexAttribArray(mPositionHandle);

        mTexCoordHandle = GLES20.glGetAttribLocation(mProgrammerHandle, "a_texCoord");
        GLES20.glVertexAttribPointer(mTexCoordHandle, 2, GLES20.GL_FLOAT, false,
                0, textureBuffer);
        GLES20.glEnableVertexAttribArray(mTexCoordHandle);

        mTextureUniformHandleWaterMap = GLES20.glGetUniformLocation(mProgrammerHandle, "u_waterMap");
        mTextureUniformHandleShine = GLES20.glGetUniformLocation(mProgrammerHandle, "u_textureShine");
        mTextureUniformHandleFg = GLES20.glGetUniformLocation(mProgrammerHandle, "u_textureFg");
        mTextureUniformHandleBg = GLES20.glGetUniformLocation(mProgrammerHandle, "u_textureBg");

        mResolution = GLES20.glGetUniformLocation(mProgrammerHandle, "u_resolution");
        mParallax = GLES20.glGetUniformLocation(mProgrammerHandle, "u_parallax");
        mTextureRatio = GLES20.glGetUniformLocation(mProgrammerHandle, "u_textureRatio");
        mRenderShine = GLES20.glGetUniformLocation(mProgrammerHandle, "u_renderShine");
        mRenderShadow = GLES20.glGetUniformLocation(mProgrammerHandle, "u_renderShadow");
        mMinRefraction = GLES20.glGetUniformLocation(mProgrammerHandle, "u_minRefraction");
        mRefractionDelta = GLES20.glGetUniformLocation(mProgrammerHandle, "u_refractionDelta");
        mBrightness = GLES20.glGetUniformLocation(mProgrammerHandle, "u_brightness");
        mAlphaMultiply = GLES20.glGetUniformLocation(mProgrammerHandle, "u_alphaMultiply");
        mAlphaSubtract = GLES20.glGetUniformLocation(mProgrammerHandle, "u_alphaSubtract");
        mParallaxBg = GLES20.glGetUniformLocation(mProgrammerHandle, "u_parallaxBg");
        mParallaxFg = GLES20.glGetUniformLocation(mProgrammerHandle, "u_parallaxFg");

        GLES20.glUniform2f(mResolution,mWidth,mHeight);

        GLES20.glUniform2f(mParallax, parallaX, parallaY);
        GLES20.glUniform1f(mTextureRatio,mWidth/mHeight);
        GLES20.glUniform1i(mRenderShine,renderShine);
        GLES20.glUniform1i(mRenderShadow,renderShadow);
        GLES20.glUniform1f(mMinRefraction,minRefraction);
        GLES20.glUniform1f(mRefractionDelta,maxRefraction - minRefraction);
        GLES20.glUniform1f(mBrightness,brightness);
        GLES20.glUniform1f(mAlphaMultiply,alphaMultiply);
        GLES20.glUniform1f(mAlphaSubtract,alphaSubtract);
        GLES20.glUniform1f(mParallaxBg,parallaxBg);
        GLES20.glUniform1f(mParallaxFg,parallaxFg);

        if (isFirst){
            mRainMapTextureHandle = TextureHelper.loadTexture(rainMap);
            isFirst = false;
        }

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mRainMapTextureHandle);
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0,0,0, rainMap);
        GLES20.glUniform1i(mTextureUniformHandleWaterMap, 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mDropShineHandle);
        GLES20.glUniform1i(mTextureUniformHandleShine, 1);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE2);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mForegroundtextureHandle);
        GLES20.glUniform1i(mTextureUniformHandleFg, 2);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE3);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mBackgroundTextureHandle);
        GLES20.glUniform1i(mTextureUniformHandleBg, 3);

        GLES20.glDrawElements ( GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, indiceBuffer);

    }
    public int getRenderShadow() {
        return renderShadow;
    }

    public void setRenderShadow(int renderShadow) {
        this.renderShadow = renderShadow;
    }

    public int getRenderShine() {
        return renderShine;
    }

    public void setRenderShine(int renderShine) {
        this.renderShine = renderShine;
    }

    public int getMinRefraction() {
        return minRefraction;
    }

    public void setMinRefraction(int minRefraction) {
        this.minRefraction = minRefraction;
    }

    public int getMaxRefraction() {
        return maxRefraction;
    }

    public void setMaxRefraction(int maxRefraction) {
        this.maxRefraction = maxRefraction;
    }

    public float getBrightness() {
        return brightness;
    }

    public void setBrightness(float brightness) {
        this.brightness = brightness;
    }

    public int getAlphaMultiply() {
        return alphaMultiply;
    }

    public void setAlphaMultiply(int alphaMultiply) {
        this.alphaMultiply = alphaMultiply;
    }

    public int getAlphaSubtract() {
        return alphaSubtract;
    }

    public void setAlphaSubtract(int alphaSubtract) {
        this.alphaSubtract = alphaSubtract;
    }

    public int getParallaxBg() {
        return parallaxBg;
    }

    public void setParallaxBg(int parallaxBg) {
        this.parallaxBg = parallaxBg;
    }

    public int getParallaxFg() {
        return parallaxFg;
    }

    public void setParallaxFg(int parallaxFg) {
        this.parallaxFg = parallaxFg;
    }

    public float getParallaX() {
        return parallaX;
    }

    public void setParallaX(float parallaX) {
        this.parallaX = parallaX;
    }

    public float getParallaY() {
        return parallaY;
    }

    public void setParallaY(float parallaY) {
        this.parallaY = parallaY;
    }
    public void setIsFirst(boolean isFirst) {
        this.isFirst = isFirst;
    }





}