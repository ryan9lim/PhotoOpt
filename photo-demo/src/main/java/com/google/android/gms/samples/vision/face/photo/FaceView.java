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

            RConnection connection = null;

            try {
            /* Create a connection to Rserve instance running on default port
             * 6311
             */
                connection = new RConnection();

            /* Note four slashes (\\\\) in the path */
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
                System.out.println("The score is=" + score);
            } catch (RserveException e) {
                e.printStackTrace();
            } catch (REXPMismatchException e) {
                e.printStackTrace();
            }
        }
    }

    public double dist(Landmark one, Landmark two) {
        if(one == null || two == null) {
            return 0;
        }

        return Math.sqrt(Math.pow(one.getPosition().x - two.getPosition().x,2)+Math.pow(one.getPosition().y - two.getPosition().y,2));
    }
}


