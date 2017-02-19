package com.google.android.gms.samples.vision.face.photo;

import android.Manifest;
import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PointF;
import android.os.Environment;
import android.os.Bundle;
import android.util.Log;
import android.util.SparseArray;
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

public class ParseActivity extends Activity {
    private static final String TAG = "ParseActivity";
    // Storage Permissions
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parse);

        grabImages();
    }
    public void grabImages() throws IOException
    {
        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                .setTrackingEnabled(false)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(ALL_CLASSIFICATIONS)
                .build();

        // This is a temporary workaround for a bug in the face detector with respect to operating
        // on very small images.  This will be fixed in a future release.  But in the near term, use
        // of the SafeFaceDetector class will patch the issue.
        Detector<Face> safeDetector = new SafeFaceDetector(detector);

        for (int i = 1; i < 91; i++){
            String photoPath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)+"/goodpic"+i+".jpg";
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);

            // Set up filewriter

            String baseDir = android.os.Environment.getExternalStorageDirectory().getAbsolutePath();
            String fileName = "AnalysisData.csv";
            String filePath = baseDir + File.separator + fileName;
            File f = new File(filePath );
            CSVWriter writer;
            // File exist
            if(f.exists() && !f.isDirectory()){
                FileWriter mFileWriter = new FileWriter(filePath, true);
                writer = new CSVWriter(mFileWriter);
            }
            else {
                writer = new CSVWriter(new FileWriter(filePath));
            }

            List<String[]> data = new ArrayList<String[]>();

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

                    double mouthSize = dist(rightMouth,leftMouth);

                    data.add(new String[] {Double.toString(dist(rightEye,leftEye)/mouthSize),
                            Double.toString(dist(rightCheek,leftCheek)/mouthSize),
                            Double.toString(dist(bottomMouth,noseBase)/mouthSize),
                            Double.toString(dist(leftEye,leftCheek)/mouthSize),
                            Double.toString(dist(rightEye,rightCheek)/mouthSize),
                            Float.toString(face.getIsLeftEyeOpenProbability()),
                            Float.toString(face.getIsRightEyeOpenProbability()),
                            Float.toString(face.getIsSmilingProbability())});

                    String leftEyeScore = i + " Probability of left eye open is " + face.getIsLeftEyeOpenProbability();
                    Log.v(TAG, leftEyeScore);

                    String rightEyeScore = i + " Probability of right eye open is " + face.getIsRightEyeOpenProbability();
                    Log.v(TAG, rightEyeScore);

                    String smileScore = i + " Probability of smiling is " + face.getIsSmilingProbability();
                    Log.v(TAG, smileScore);
                }

                writer.writeAll(data);

                writer.close();
            }
            catch (Exception e)
            {
                Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Failed to load", e);
                writer.close();
            }
        }
        for (int i = 1; i < 89; i++){
            String photoPath = Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)+"/badpic"+i+".jpg";
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = BitmapFactory.decodeFile(photoPath, options);
            try
            {
                // Create a frame from the bitmap and run face detection on the frame.
                Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                SparseArray<Face> mFaces = safeDetector.detect(frame);
                Log.v(TAG, Integer.toString(mFaces.size()));
                for (int j = 0; j < mFaces.size(); ++j) {
                    Face face = mFaces.valueAt(j);

                    String leftEyeScore = i+" Probability of left eye open is " + face.getIsLeftEyeOpenProbability();
                    Log.v(TAG, leftEyeScore);

                    String rightEyeScore = i+" Probability of right eye open is " + face.getIsRightEyeOpenProbability();
                    Log.v(TAG, rightEyeScore);

                    String smileScore = i+" Probability of smiling is " + face.getIsSmilingProbability();
                    Log.v(TAG, smileScore);
                }
            }
            catch (Exception e)
            {
                Toast.makeText(this, "Failed to load", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Failed to load", e);
            }
        }
        safeDetector.release();
    }

    public double dist(Landmark one, Landmark two) {
        return Math.sqrt(Math.pow(one.getPosition().x - two.getPosition().x,2)+Math.pow(one.getPosition().y - two.getPosition().y,2));
    }
}
