package com.yang.raindrops;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


/**
 * Created by qinfeng on 16/7/18.
 */

public class RainDrops {
    final private int DROP_SIZE = 64;
    final private int BRUSH_SIZE = 64;

    private int minR = 10;
    private int maxR = 40;
    private int maxDrops = 900;
    private float rainChance = 0.3f;
    private int rainLimit = 3;
    private int dropletsRate = 50;



    private int[]  dropletsSize = {0,1};

    private float dropletsCleaningRadiusMultiplier = 0.56f;
    private boolean raining = true;
    private float globalTimeScale = 1f;
    private int trailRate = 1;
    private boolean autoShrink = true;
    private float[] spawnArea = {-0.1f,0.95f};
    private float[]  trailScaleRange = {0.2f,0.5f};
    private float collisionRadius = 0.65f;
    private float collisionRadiusIncrease = 0.01f;
    private float dropFallMultiplier = 0.6f;
    private float  collisionBoostMultiplier = 0.05f;
    private int collisionBoost = 1;

    private int mWidth;
    private int mHeight;
    private float mScale;
    private Bitmap mDropColor;
    private Bitmap mDropAlpha;

    private Canvas mCanvas;

    //TODO float
    private int dropletsPixelDensity = 1;

    private Canvas mDropletCanvas =null;

    private int dropletsCounter = 0;
    private ArrayList<Drop> drops = null;

    public ArrayList<Bitmap> getDropsGfx() {
        return dropsGfx;
    }

    private ArrayList<Bitmap> dropsGfx = null;
    private Bitmap clearDropletsGfx = null;
    private int textureCleaningIterations = 0;
    private long lastRender = 0L;
    private Paint mSrcOutPaint;
    private Paint mSrcOverPaint;
    private Paint mDstInPaint;
    private Paint mSrcInPaint;

    public Bitmap getCanvasBitmap() {
        return mCanvasBitmap;
    }

    private Bitmap mCanvasBitmap;
    private Bitmap mDropletBitmap;

    public RainDrops( int width, int height, float scale, Bitmap dropColor, Bitmap dropAlpha) {

        mWidth = width;
        mHeight = height;
        mDropColor = dropColor;

        mScale = scale;
        mDropAlpha = dropAlpha;
        init();
    }

    private void init() {
        mCanvasBitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        mCanvas = new Canvas(mCanvasBitmap);
        mDropletBitmap = Bitmap.createBitmap(mWidth * dropletsPixelDensity, mHeight * dropletsPixelDensity, Bitmap.Config.ARGB_8888);
        mDropletCanvas = new Canvas(mDropletBitmap);
        drops = new ArrayList<>();
        dropsGfx =new ArrayList<>();



        mSrcOutPaint = new Paint();
        mSrcOutPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
        mSrcOutPaint.setAntiAlias(true);

        mSrcOverPaint = new Paint();
        mSrcOverPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        mSrcOverPaint.setAntiAlias(true);

        mSrcInPaint = new Paint();
        mSrcInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        mSrcInPaint.setAntiAlias(true);

        mDstInPaint = new Paint();
        mDstInPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        mDstInPaint.setAntiAlias(true);

        renderDropsGfx();
        update();

    }

    private float getDeltaR() {
        return maxR - minR;
    }

    private float getArea() {
        return mWidth *  mHeight / mScale ;
    }

    private  float getAreaMultiplier() {
        return (float) Math.sqrt(getArea() / (1024 * 768)) ;
    }


    public void clearDrops(){
        for (final Drop drop : drops) {
            new java.util.Timer().schedule(
                    new java.util.TimerTask() {
                        @Override
                        public void run() {
                            drop.shrink=0.1f+(random(0.5f));
                        }
                    },
                    random(1200)
            );
        }

        clearTexture();
    }


    private void renderDropsGfx() {
        for (int i = 0; i < 255 ; i++) {
            dropsGfx.add(composite(i));
        }

        clearDropletsGfx = Bitmap.createBitmap(BRUSH_SIZE, BRUSH_SIZE, Bitmap.Config.ARGB_8888);

        Canvas brushCanvas = new Canvas(clearDropletsGfx);
        //once
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setColor(Color.BLACK);
        paint.setStyle(Paint.Style.FILL);
        brushCanvas.drawCircle(BRUSH_SIZE/2, BRUSH_SIZE/2 ,BRUSH_SIZE/2, paint);

    }

    public void update() {
        clearCanvas();
        long now = System.currentTimeMillis();
        if (lastRender == 0L)
            lastRender = now;

        long delta = now - lastRender;
        float timeScale = ((int) delta) / ((1f / 60f) *1000f);

        if (timeScale >1.1f)
            timeScale = 1.1f;
        timeScale *= globalTimeScale;
        
        lastRender = now;
        
        updateDrops(timeScale);


    }

    private void updateDrops(float timeScale) {
        ArrayList<Drop> newDrops = new ArrayList<>();
        updateDroplets(timeScale);

        ArrayList<Drop> rainDrops = updateRain(timeScale);

        newDrops.addAll(rainDrops);


        Collections.sort(drops, new DropComparator());
        for (int i = 0; i < drops.size(); i++) {
            Drop drop = drops.get(i);
            if(!drop.killed){
                // update gravity
                // (chance of drops "creeping down")
                if(chance((drop.r-(minR * dropFallMultiplier)) * (0.1f/getDeltaR()) * timeScale)){
                    drop.momentum += random((drop.r / (float) maxR) * 4);
                }
                // clean small drops
                if(autoShrink && drop.r <= minR && chance(0.05f * timeScale)){
                    drop.shrink+=0.01f;
                }
                //update shrinkage
                drop.r = drop.r - (int)(drop.shrink*timeScale);
//                drop.r -= drop.shrink*timeScale;

                if(drop.r<=0)
                    drop.killed=true;

                // update trails
                if(raining){
                    drop.lastSpawn += drop.momentum * timeScale * trailRate;
                    if(drop.lastSpawn>drop.nextSpawn){
                        if (canCreateDrop()) {
                            Drop trailDrop= new Drop();
                            trailDrop.x = drop.x+(int)(random(-drop.r,drop.r)*0.1f);
                            trailDrop.y = drop.y-(int)(drop.r*0.01f);
                            trailDrop.r = (int)(drop.r*random(trailScaleRange[0],trailScaleRange[1]));
                            trailDrop.spreadY = drop.momentum*0.1f;
                            trailDrop.parent = drop;


                            newDrops.add(trailDrop);
                            drop.r = (int)(drop.r * Math.pow(0.97,timeScale));
//                            drop.r*=Math.pow(0.97,timeScale);
                            drop.lastSpawn=0f;
                            drop.nextSpawn=random(minR,maxR)-(drop.momentum * 2 * trailRate) + (maxR-drop.r);
                        }


                    }
                }

                //normalize spread
                drop.spreadX = drop.spreadX * (float)Math.pow(0.4f,timeScale);
                drop.spreadY = drop.spreadY * (float)Math.pow(0.7f,timeScale);
//                drop.spreadX*=Math.pow(0.4f,timeScale);
//                drop.spreadY*=Math.pow(0.7f,timeScale);

                //update position
                boolean moved=drop.momentum > 0;
                if(moved && !drop.killed){
                    drop.y = drop.y + (int)(drop.momentum * globalTimeScale);
                    drop.x = drop.x+ (int)(drop.momentumX * globalTimeScale);
//                    drop.y += drop.momentum * globalTimeScale;
//                    drop.x += drop.momentumX * globalTimeScale;
                    if(drop.y > (mHeight / mScale) + drop.r){
                        drop.killed = true;
                    }
                }
                boolean checkCollision=(moved || drop.isNew) && !drop.killed;
                drop.isNew=false;


                if(checkCollision) {
                    int size = drops.size();
                    int start = i + 1;
                    if (start > size)
                        start = size;
                    int end = i + 70;
                    if (end > size)
                        end = size;
                    ArrayList<Drop> slice = new ArrayList<>(drops.subList(start, end));
                    for (Drop drop2 : slice) {
                        if(drop != drop2 &&
                                drop.r > drop2.r &&
                                drop.parent != drop2 &&
                                drop2.parent != drop &&
                                !drop2.killed) {

                            int dx=drop2.x - drop.x;
                            int dy=drop2.y - drop.y;
                            double d=Math.sqrt((dx * dx) + (dy * dy));
                            //if it's within acceptable distance
                            if(d < (drop.r + drop2.r) * (collisionRadius+(drop.momentum * collisionRadiusIncrease * timeScale))){
                                double pi = Math.PI;
                                int r1 = drop.r;
                                int r2 = drop2.r;
                                double a1 = pi * (r1 * r1);
                                double a2 = pi * (r2 * r2);
                                double targetR = Math.sqrt((a1 + (a2 * 0.8)) / pi);
                                if(targetR > maxR){
                                    targetR = maxR;
                                }
                                drop.r = (int) targetR;
                                drop.momentumX += dx * 0.1f;
                                drop.spreadX = 0f;
                                drop.spreadY = 0f;
                                drop2.killed = true;
                                drop.momentum = (float) Math.max(drop2.momentum, Math.min(40, drop.momentum+(targetR * collisionBoostMultiplier) + collisionBoost));
                            }

                        }
                    }

                }

                //slowdown momentum
                drop.momentum -= Math.max(1, (minR * 0.5f) - drop.momentum) * 0.1f * timeScale;
                if(drop.momentum < 0)
                    drop.momentum = 0;
                drop.momentumX *=((float) Math.pow(0.7,timeScale));

                if (drop.r <= 0)
                    drop.killed =true;

                if(!drop.killed){
                    newDrops.add(drop);
                    if(moved && dropletsRate > 0)
                        clearDroplets(drop.x, drop.y, (int)(drop.r * dropletsCleaningRadiusMultiplier));
                   drawDrop(mCanvas, drop);
                }

            }

        }

        drops = newDrops;
    }

    private void clearDroplets(int x, int y, int r) {

        int left = (int) ((x - r) * dropletsPixelDensity * mScale);
        int top = (int) ((y - r) * dropletsPixelDensity * mScale);
        int width = (int) ((r * 2) * dropletsPixelDensity * mScale);
        int height = (int) ((r * 2) * dropletsPixelDensity * mScale * 1.5);
        Rect rect = new Rect(left, top, left + width, top + height);
        mDropletCanvas.drawBitmap(clearDropletsGfx,null,rect, mSrcOutPaint);
    }

    private ArrayList<Drop> updateRain(float timeScale) {
        ArrayList<Drop> rainDrops = new ArrayList<>();
        if (raining){
            int  limit = (int)(rainLimit * timeScale * getAreaMultiplier());
            int count = 0;
            while (chance(rainChance * timeScale * getAreaMultiplier()) && count < limit) {
                count++;
                int r = randomWithPow3(minR, maxR);
                if (canCreateDrop()) {
                    Drop drop = new Drop();
                    drop.x = random((int) (mWidth / mScale));
                    drop.y = random((int) ((mHeight / mScale)*spawnArea[0]),(int) ((mHeight / mScale)*spawnArea[1]));
                    drop.r = r;
                    //moentun 1 + 1-2 + delta *0.1 <1, 3+delta*0.1>
                    drop.momentum = 1 +(r-minR)*0.1f + random(2f);
                    drop.spreadX = 1.5f;
                    drop.spreadY = 1.5f;

                    drops.add(drop);

                }


            }

        }

        return rainDrops;

    }

    private void updateDroplets(float timeScale) {
        if (textureCleaningIterations > 0){
            textureCleaningIterations =textureCleaningIterations - (int)(1*timeScale);
//            textureCleaningIterations -= (1*timeScale);
            mDropletCanvas.drawColor(Color.argb((int) (0.05f * timeScale * 255),0,0,0), PorterDuff.Mode.DST_OUT);
        }

        if (raining){
            dropletsCounter =dropletsCounter + (int)(dropletsRate * timeScale * getAreaMultiplier());
//            dropletsCounter += dropletsRate * timeScale * getAreaMultiplier();
            int times = dropletsCounter;
            for (int i = 0; i < times; i++) {
                dropletsCounter--;
                drawDroplet(random((int)(mWidth / mScale)),
                        random((int)(mHeight / mScale)),
                        randomSqure(dropletsSize[0],dropletsSize[1]));
            }
        }


        //Draw to mCanvas
        mCanvas.drawBitmap(mDropletBitmap,0,0, mSrcOverPaint);

    }


    //create Drop data from updateDroplets
    private void drawDroplet(int x, int y, int r) {
        if (canCreateDrop()){
            Drop drop = new Drop();
            drop.x = x * dropletsPixelDensity;
            drop.y = y * dropletsPixelDensity;
            drop.r = r * dropletsPixelDensity;

            drawDrop(mDropletCanvas,drop);
        }
    }

    private boolean canCreateDrop() {
        return drops.size() <= maxDrops * getAreaMultiplier();
    }

    //Draw drop to mDropletCanvas
    private void drawDrop(Canvas canvas,Drop drop) {
        if (dropsGfx.size() > 0) {
            float x = drop.x;
            float y = drop.y;
            float r = drop.r;
            float spreadX = drop.spreadX;
            float spreadY = drop.spreadY;


            float scaleX = 1f;
            float scaleY = 1.5f;

            double d = Math.max(0, Math.min(1, ((r - minR) / getDeltaR() * 0.9f)));
            d *= 1 / ((drop.spreadX + drop.spreadY) *0.5 + 1);


            int df = (int) Math.floor(d * (dropsGfx.size() - 1));

//            Paint paint = new Paint();
//            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
//            paint.setAlpha(255);

            Bitmap bitmap = dropsGfx.get(df);


//            Bitmap scaledBitmap = Bitmap.createScaledBitmap(bitmap,
//                    (int) ((r * 2 * scaleX * (spreadX + 1)) * mScale),
//                    (int) ((r * 2 * scaleY * (spreadY + 1)) * mScale),
//                    false);
//
//            canvas.drawBitmap(scaledBitmap,
//                    (int)((x - (r * scaleX * (spreadX + 1))) * mScale),
//                    (int)((y - (r * scaleY * (spreadY + 1))) * mScale), mSrcOverPaint);
//            scaledBitmap.recycle();


            int left = (int)((x - (r * scaleX * (spreadX + 1))) * mScale);
            int top = (int)((y - (r * scaleY * (spreadY + 1))) * mScale);
            int width = (int) ((r * 2 * scaleX * (spreadX + 1)) * mScale);
            int height = (int) ((r * 2 * scaleY * (spreadY + 1)) * mScale);
            Rect rect = new Rect(left, top, left + width, top + height);


            canvas.drawBitmap(bitmap,null,rect, mSrcOverPaint);



        }
    }


    private void clearCanvas() {
        mCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR);

    }

    private Bitmap composite(int depth){
        Bitmap cop = Bitmap.createBitmap(mDropAlpha.getWidth(), mDropAlpha.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas copCanvas = new Canvas(cop);
        copCanvas.drawBitmap(mDropAlpha,0,0,mSrcOverPaint);


        Bitmap bitmap = Bitmap.createBitmap(mDropColor.getWidth(), mDropColor.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawBitmap(mDropColor,0,0,mSrcOverPaint);
        canvas.drawColor(Color.argb(255,0,0,depth), PorterDuff.Mode.SCREEN);

        copCanvas.drawBitmap(bitmap,0,0,mSrcInPaint);
        return cop;


//        //color
//        Bitmap mutableBitmap = mDropColor.copy(Bitmap.Config.ARGB_8888, true);
//        Canvas mutableBitmapCanvas = new Canvas(mutableBitmap);
//        //depth
//        mutableBitmapCanvas.drawColor(Color.argb(255,0,0,depth), PorterDuff.Mode.SCREEN);
//        //alpha
//
//        mutableBitmapCanvas.drawBitmap(mDropAlpha,0,0, mDstInPaint);
//
//        return mutableBitmap;
    }

    private void clearTexture(){
       textureCleaningIterations=50;
    }
    private int randomSqure(int from, int to) {
        int delta = to - from;
        double fra = Math.random();
        fra = fra * fra;
        return from + (int)(fra * delta);

    }


    private int random(int seed) {

        return (int)(Math.random() * seed);

    }

    private int random(int from,int to){

        return from + (int) (Math.random() * (to -from));
    }

    private float random(float from,float to){

        return  from + (float)(Math.random() * (to -from));
    }

    private float random(float seed){
        return (float) (Math.random() * seed);
    }
    private int randomWithPow3 (int from, int to) {
        double delta = to - from;

        double fra = Math.pow(Math.random(), 3);
        return from + (int) (fra * delta);

    }

    private boolean chance(float c) {
        return ((float) Math.random())<= c;
    }
    class Drop {

        public int x = 0;
        public int y = 0;
        public int r = 0;
        public float spreadX = 0f;
        public float spreadY = 0f;
        public float momentum = 0f;
        public float momentumX = 0f;
        public float lastSpawn = 0f;
        public float nextSpawn = 0f;
        public Drop parent = null;
        public boolean isNew = true;
        public boolean killed = false;
        public float shrink = 0;
    }



    public class DropComparator implements Comparator {
        @Override
        public int compare(Object lhs, Object rhs) {
            Drop drop1 = (Drop) lhs;
            Drop drop2 = (Drop) rhs;
            float v1 = drop1.y * (mWidth / mScale) + drop1.x;
            float v2 = drop2.y * (mWidth / mScale) + drop2.x;

            return v1 > v2 ? 1 : v1 == v2 ? 0: -1;
        }
    }

    public int getMinR() {
        return minR;
    }

    public void setMinR(int minR) {
        this.minR = minR;
    }

    public int getMaxR() {
        return maxR;
    }

    public void setMaxR(int maxR) {
        this.maxR = maxR;
    }

    public float getRainChance() {
        return rainChance;
    }

    public void setRainChance(float rainChance) {
        this.rainChance = rainChance;
    }

    public int getMaxDrops() {
        return maxDrops;
    }

    public void setMaxDrops(int maxDrops) {
        this.maxDrops = maxDrops;
    }

    public int getRainLimit() {
        return rainLimit;
    }

    public void setRainLimit(int rainLimit) {
        this.rainLimit = rainLimit;
    }

    public int getDropletsRate() {
        return dropletsRate;
    }

    public void setDropletsRate(int dropletsRate) {
        this.dropletsRate = dropletsRate;
    }

    public int[] getDropletsSize() {
        return dropletsSize;
    }

    public void setDropletsSize(int[] dropletsSize) {
        this.dropletsSize = dropletsSize;
    }

    public float getDropletsCleaningRadiusMultiplier() {
        return dropletsCleaningRadiusMultiplier;
    }

    public void setDropletsCleaningRadiusMultiplier(float dropletsCleaningRadiusMultiplier) {
        this.dropletsCleaningRadiusMultiplier = dropletsCleaningRadiusMultiplier;
    }

    public boolean isRaining() {
        return raining;
    }

    public void setRaining(boolean raining) {
        this.raining = raining;
    }

    public float getGlobalTimeScale() {
        return globalTimeScale;
    }

    public void setGlobalTimeScale(float globalTimeScale) {
        this.globalTimeScale = globalTimeScale;
    }

    public int getTrailRate() {
        return trailRate;
    }

    public void setTrailRate(int trailRate) {
        this.trailRate = trailRate;
    }

    public boolean isAutoShrink() {
        return autoShrink;
    }

    public void setAutoShrink(boolean autoShrink) {
        this.autoShrink = autoShrink;
    }

    public float[] getSpawnArea() {
        return spawnArea;
    }

    public void setSpawnArea(float[] spawnArea) {
        this.spawnArea = spawnArea;
    }

    public float[] getTrailScaleRange() {
        return trailScaleRange;
    }

    public void setTrailScaleRange(float[] trailScaleRange) {
        this.trailScaleRange = trailScaleRange;
    }

    public float getCollisionRadius() {
        return collisionRadius;
    }

    public void setCollisionRadius(float collisionRadius) {
        this.collisionRadius = collisionRadius;
    }

    public float getCollisionRadiusIncrease() {
        return collisionRadiusIncrease;
    }

    public void setCollisionRadiusIncrease(float collisionRadiusIncrease) {
        this.collisionRadiusIncrease = collisionRadiusIncrease;
    }

    public float getDropFallMultiplier() {
        return dropFallMultiplier;
    }

    public void setDropFallMultiplier(float dropFallMultiplier) {
        this.dropFallMultiplier = dropFallMultiplier;
    }

    public float getCollisionBoostMultiplier() {
        return collisionBoostMultiplier;
    }

    public void setCollisionBoostMultiplier(float collisionBoostMultiplier) {
        this.collisionBoostMultiplier = collisionBoostMultiplier;
    }

    public int getCollisionBoost() {
        return collisionBoost;
    }

    public void setCollisionBoost(int collisionBoost) {
        this.collisionBoost = collisionBoost;
    }

}
