/*
 * Copyright (C) The Android Open Source Project
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
package com.google.android.gms.samples.vision.face.photo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.support.v7.widget.Toolbar;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;

import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.Landmark;

import org.rosuda.REngine.REXPMismatchException;
import org.rosuda.REngine.Rserve.RConnection;
import org.rosuda.REngine.Rserve.RserveException;

import static com.google.android.gms.vision.face.Landmark.BOTTOM_MOUTH;
import static com.google.android.gms.vision.face.Landmark.LEFT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.LEFT_EYE;
import static com.google.android.gms.vision.face.Landmark.LEFT_MOUTH;
import static com.google.android.gms.vision.face.Landmark.NOSE_BASE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.RIGHT_EYE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_MOUTH;

/**
 * View which displays a bitmap containing a face along with overlay graphics that identify the
 * locations of detected facial landmarks.
 */
public class FaceView extends View {
    private Bitmap mBitmap;
    private SparseArray<Face> mFaces;
    public FaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    private static final String TAG = "FaceView";

    /**
     * Sets the bitmap background and the associated face detections.
     */
    void setContent(Bitmap bitmap, SparseArray<Face> faces) {
        mBitmap = bitmap;
        mFaces = faces;
        invalidate();
    }

    /**
     * Draws the bitmap background and the associated face landmarks.
     */
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if ((mBitmap != null) && (mFaces != null)) {
            double scale = drawBitmap(canvas);
            drawFaceAnnotations(canvas, scale);
        }
    }

    /**
     * Draws the bitmap background, scaled to the device size.  Returns the scale for future use in
     * positioning the facial landmark graphics.
     */
    private double drawBitmap(Canvas canvas) {
        double viewWidth = canvas.getWidth();
        double viewHeight = canvas.getHeight();
        double imageWidth = mBitmap.getWidth();
        double imageHeight = mBitmap.getHeight();
        double scale = Math.min(viewWidth / imageWidth, viewHeight / imageHeight);

        Rect destBounds = new Rect(0, 0, (int)(imageWidth * scale), (int)(imageHeight * scale));
        canvas.drawBitmap(mBitmap, null, destBounds, null);
        return scale;
    }

    /**
     * Draws a small circle for each detected landmark, centered at the detected landmark position.
     * <p>
     *
     * Note that eye landmarks are defined to be the midpoint between the detected eye corner
     * positions, which tends to place the eye landmarks at the lower eyelid rather than at the
     * pupil position.
     */
    private void drawFaceAnnotations(Canvas canvas, double scale) {
        Paint paint = new Paint();
        paint.setColor(Color.GREEN);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);

        for (int i = 0; i < mFaces.size(); ++i) {
            Face face = mFaces.valueAt(i);
            for (Landmark landmark : face.getLandmarks()) {
                int cx = (int) (landmark.getPosition().x * scale);
                int cy = (int) (landmark.getPosition().y * scale);
                canvas.drawCircle(cx, cy, 10, paint);
            }

            Landmark rightEye = null;
            Landmark leftEye = null;
            Landmark leftCheek = null;
            Landmark rightCheek = null;
            Landmark noseBase = null;
            Landmark bottomMouth = null;
            Landmark leftMouth = null;
            Landmark rightMouth = null;

            for (Landmark landmark : face.getLandmarks()) {

                switch (landmark.getType()) {
                    case RIGHT_EYE:
                        rightEye = landmark;
                        break;
                    case LEFT_EYE:
                        leftEye = landmark;
                        break;
                    case LEFT_CHEEK:
                        leftCheek = landmark;
                        break;
                    case RIGHT_CHEEK:
                        rightCheek = landmark;
                        break;
                    case NOSE_BASE:
                        noseBase = landmark;
                        break;
                    case BOTTOM_MOUTH:
                        bottomMouth = landmark;
                        break;
                    case LEFT_MOUTH:
                        leftMouth = landmark;
                        break;
                    case RIGHT_MOUTH:
                        rightMouth = landmark;
                        break;
                    default:
                        break;
                }
            }

            if (face.getLandmarks().size() <= 0) {
                // Score = -1 because no landmarks found
                Log.d(TAG,"YOOOOOOOOO Score is: -1");
                continue;
            }

            // RConnection connection = null;

            /*try {
            RConnection connection = new RConnection("172.30.20.6", 6311);

                connection.eval("source('/Users/robertrtung/StudioProjects/PhotoOpt/Data/ScoreFace.R')");
                double score = -1;
                if(rightEye == null || leftEye == null) {
                    score=connection.eval("scoreIt("+"NA" + "," +
                            "NA" + "," +
                            "NA" + "," +
                            "NA" + "," +
                            "NA" + "," +
                            Float.toString(face.getIsLeftEyeOpenProbability()) + "," +
                            Float.toString(face.getIsRightEyeOpenProbability()) + "," +
                            Float.toString(face.getIsSmilingProbability()) +
                            ")").asDouble();
                } else {
                    double eyeSize = dist(rightEye,leftEye);

                    score=connection.eval("scoreIt("+Double.toString(dist(rightMouth,leftMouth)/eyeSize) + "," +
                            Double.toString(dist(rightCheek,leftCheek)/eyeSize) + "," +
                            Double.toString(dist(bottomMouth,noseBase)/eyeSize) + "," +
                            Double.toString(dist(leftEye,leftCheek)/eyeSize) + "," +
                            Double.toString(dist(rightEye,rightCheek)/eyeSize) + "," +
                            Float.toString(face.getIsLeftEyeOpenProbability()) + "," +
                            Float.toString(face.getIsRightEyeOpenProbability()) + "," +
                            Float.toString(face.getIsSmilingProbability()) +
                            ")").asDouble();
                }
                Log.d(TAG,"YOOOOOOOOOOOOOOOOOOOOO The score is=" + score);
            } catch (RserveException e) {
                e.printStackTrace();
            } catch (REXPMismatchException e) {
                e.printStackTrace();
            }*/
            double score = -1;
            double[] lm = {0.24293,-0.16650,0.01077};
            double[] means = {0.8014056,1.1130010, 0.5566747, 0.4936905, 0.4792462, 0.8035098, 0.7082552, 0.2604325};
            double[] pca1 = {-0.4570806, -0.4739974, -0.4124803, -0.1661393, -0.3926277, -0.1542638, -0.1628371, -0.4052058};
            double[] pca2 = {0.07049220, -0.27247838, -0.07652864, -0.32021894, -0.29826944,  0.54186635, 0.58493271, 0.29607347};

            double lo;
            if(face.getIsLeftEyeOpenProbability() < 0) {
                lo = means[5];
            } else {
                lo = face.getIsLeftEyeOpenProbability();
            }

            double ro;
            if(face.getIsRightEyeOpenProbability() < 0) {
                ro = means[6];
            } else {
                ro = face.getIsRightEyeOpenProbability();
            }

            double sm;
            if(face.getIsSmilingProbability() < 0) {
                sm = means[7];
            } else {
                sm = face.getIsSmilingProbability();
            }
            if(rightEye == null || leftEye == null) {
                // Use mean values if no ratios available
                score = lm[0] + lm[1]*(pca1[0]*(means[0])+
                        pca1[1]*(means[1])+
                        pca1[2]*(means[2])+
                        pca1[3]*(means[3])+
                        pca1[4]*(means[4])+
                        pca1[5]*(lo)+
                        pca1[6]*(ro)+
                        pca1[7]*(sm)) + lm[2]*(pca2[0]*(means[0])+
                        pca2[1]*(means[1])+
                        pca2[2]*(means[2])+
                        pca2[3]*(means[3])+
                        pca2[4]*(means[4])+
                        pca2[5]*(lo)+
                        pca2[6]*(ro)+
                        pca2[7]*(sm));

            } else {
                double eyeSize = dist(rightEye,leftEye);
                score = lm[0] + lm[1]*(pca1[0]*(dist(rightMouth,leftMouth,means,0)/eyeSize)+
                        pca1[1]*(dist(rightCheek,leftCheek,means,1)/eyeSize)+
                        pca1[2]*(dist(bottomMouth,noseBase,means,2)/eyeSize)+
                        pca1[3]*(dist(leftEye,leftCheek,means,3)/eyeSize)+
                        pca1[4]*(dist(rightEye,rightCheek,means,4)/eyeSize)+
                        pca1[5]*(face.getIsLeftEyeOpenProbability())+
                        pca1[6]*(face.getIsRightEyeOpenProbability())+
                        pca1[7]*(face.getIsSmilingProbability())) + lm[2]*(pca2[0]*(dist(rightMouth,leftMouth,means,0)/eyeSize)+
                        pca2[1]*(dist(rightCheek,leftCheek,means,1)/eyeSize)+
                        pca2[2]*(dist(bottomMouth,noseBase,means,2)/eyeSize)+
                        pca2[3]*(dist(leftEye,leftCheek,means,3)/eyeSize)+
                        pca2[4]*(dist(rightEye,rightCheek,means,4)/eyeSize)+
                        pca2[5]*(lo)+
                        pca2[6]*(ro)+
                        pca2[7]*(sm));
            }

            Log.d(TAG,"YOOOOOOOOO Score is: " + score);
        }
    }

    public double dist(Landmark one, Landmark two) {
        if(one == null || two == null) {
            return 0;
        }

        return Math.sqrt(Math.pow(one.getPosition().x - two.getPosition().x,2)+Math.pow(one.getPosition().y - two.getPosition().y,2));
    }

    public double dist(Landmark one, Landmark two, double[] means, int i) {
        if(one == null || two == null) {
            return means[i];
        }

        return Math.sqrt(Math.pow(one.getPosition().x - two.getPosition().x,2)+Math.pow(one.getPosition().y - two.getPosition().y,2));
    }
}


