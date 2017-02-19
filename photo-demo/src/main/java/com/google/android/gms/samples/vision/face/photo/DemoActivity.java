package com.google.android.gms.samples.vision.face.photo;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.util.SparseArray;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.samples.vision.face.patch.SafeFaceDetector;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static android.R.attr.bitmap;
import static android.os.Environment.DIRECTORY_DOWNLOADS;
import static com.google.android.gms.vision.face.FaceDetector.ALL_CLASSIFICATIONS;
import static com.google.android.gms.vision.face.Landmark.BOTTOM_MOUTH;
import static com.google.android.gms.vision.face.Landmark.LEFT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.LEFT_EYE;
import static com.google.android.gms.vision.face.Landmark.LEFT_MOUTH;
import static com.google.android.gms.vision.face.Landmark.NOSE_BASE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.RIGHT_EYE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_MOUTH;

public class DemoActivity extends Activity {
    ArrayList<String> filelist;
    ImageView imageView;
    private static final String TAG = "DemoActivity";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_demo);
        if (getIntent() != null) {
            filelist =  (ArrayList<String>) getIntent().getSerializableExtra("filenames");
        }
        Log.d(TAG,"Number of pictures: "+filelist.size());
        imageView = (ImageView) findViewById(R.id.output);
        grabImage();
    }

    public void grabImage(){
        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(ALL_CLASSIFICATIONS)
                .build();

        // This is a temporary workaround for a bug in the face detector with respect to operating
        // on very small images.  This will be fixed in a future release.  But in the near term, use
        // of the SafeFaceDetector class will patch the issue.
        Detector<Face> safeDetector = new SafeFaceDetector(detector);

        double maxsumscore = 0;
        String bestPhotoPath = filelist.get(1);
        for (int i = 0; i < filelist.size(); i++) {
            Bitmap bitmap = null;
            double sumscore = 0;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(filelist.get(i), options);
                bitmap = checkImageRotation(filelist.get(i),bitmap);
            } catch (Exception e){
                e.printStackTrace();
            }
            try
            {
                // Create a frame from the bitmap and run face detection on the frame.
                Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                SparseArray<Face> mFaces = safeDetector.detect(frame);

                for (int j = 0; j < mFaces.size(); ++j) {
                    Face face = mFaces.valueAt(j);

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
                    sumscore += score;
                }
            }
            catch (Exception e)
            {
                Toast.makeText(this, "Failed to load 1 " + i, Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Failed to load", e);
            }
            Log.d(TAG,"YOOOOOOOOO SumScore is: " + sumscore);
            if (sumscore > maxsumscore){
                bestPhotoPath = filelist.get(i);
                maxsumscore = sumscore;
            }
        }
        Log.d(TAG,"YOOOOOOOOO BEST SCORE is: " + maxsumscore);
        Bitmap bitmap = null;
        try {
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = false;
            bitmap = BitmapFactory.decodeFile(bestPhotoPath, options);
            bitmap = checkImageRotation(bestPhotoPath,bitmap);
        } catch (Exception e){
            e.printStackTrace();
        }
        imageView.setImageBitmap(bitmap);

    }
    public static Bitmap checkImageRotation (String path,Bitmap bitmap){
        try {
            ExifInterface ei = new ExifInterface(path);
            int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_UNDEFINED);
            switch (orientation) {
                case ExifInterface.ORIENTATION_ROTATE_90:
                    bitmap = rotateImage(bitmap, 90);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_180:
                    bitmap = rotateImage(bitmap, 180);
                    break;

                case ExifInterface.ORIENTATION_ROTATE_270:
                    bitmap = rotateImage(bitmap, 270);
                    break;

                case ExifInterface.ORIENTATION_NORMAL:

                default:
                    break;
            }
        } catch (Exception e){
            e.printStackTrace();
        }
        return bitmap;
    }
    public static Bitmap rotateImage(Bitmap source, float angle) {
        Matrix matrix = new Matrix();
        matrix.postRotate(angle);
        return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(),
                matrix, true);
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
