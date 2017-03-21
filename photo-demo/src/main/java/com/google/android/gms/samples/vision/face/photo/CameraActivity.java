package com.google.android.gms.samples.vision.face.photo;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Camera;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Point;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.graphics.drawable.AnimationDrawable;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.ExifInterface;
import android.media.Image;
import android.media.ImageReader;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Size;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.gms.samples.vision.face.patch.SafeFaceDetector;
import com.google.android.gms.vision.Detector;
import com.google.android.gms.vision.Frame;
import com.google.android.gms.vision.face.Face;
import com.google.android.gms.vision.face.FaceDetector;
import com.google.android.gms.vision.face.Landmark;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static com.google.android.gms.vision.face.FaceDetector.ALL_CLASSIFICATIONS;
import static com.google.android.gms.vision.face.Landmark.BOTTOM_MOUTH;
import static com.google.android.gms.vision.face.Landmark.LEFT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.LEFT_EYE;
import static com.google.android.gms.vision.face.Landmark.LEFT_MOUTH;
import static com.google.android.gms.vision.face.Landmark.NOSE_BASE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_CHEEK;
import static com.google.android.gms.vision.face.Landmark.RIGHT_EYE;
import static com.google.android.gms.vision.face.Landmark.RIGHT_MOUTH;

public class CameraActivity extends Activity {

    /**
     * Tag for the {@link Log}
     */
    private static final String TAG = "CameraActivity";

    /* ----- UI ----- */

    /**
     * An {@link AnimationDrawable} for loading.
     */
    private AnimationDrawable loadingAnimation;
    private ImageView loadingView;
    private ImageButton switchCameraButton;
    private Button getpicture;

    /**
     * An {@link AutoFitTextureView} for camera preview.
     */
    private AutoFitTextureView textureView;


    /* ----- Camera Objects ----- */

    private Size mPreviewSize;
    private CameraDevice cameraDevice;
    private CaptureRequest.Builder previewBuilder;
    private CameraCaptureSession previewSession;
    /**
     * A {@link Semaphore} to prevent the app from exiting before closing the camera.
     */
    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    final ConcurrentHashMap<String,Double> mImageScores = new ConcurrentHashMap<>();
    /**
     * An {@link ImageReader} that handles still image capture.
     */
    private ImageReader mImageReader;

    /**
     * Orientation of the camera sensor
     */
    private int mSensorOrientation;

    private String cameraId;

    private int cameraFace = CameraCharacteristics.LENS_FACING_BACK;

    private boolean mTakePhoto = false;

    /* ----- Constants ----- */

    private static final int REQUEST_CAMERA = 1;
    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static final SparseIntArray ORIENTATIONS=new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_WIDTH = 1920;

    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    private static final int MAX_PREVIEW_HEIGHT = 1080;

    /* ----- Thread Objects ----- */

    /**
     * An additional thread for running tasks that shouldn't block the UI.
     */
    private HandlerThread mBackgroundThread;

    /**
     * A {@link Handler} for running tasks in the background.
     */
    private Handler mBackgroundHandler;

    ExecutorService taskExecutor;

    private Handler mHandler;

    private Runnable mAction = new Runnable() {
        @Override public void run() {
            mTakePhoto = true;
            mHandler.postDelayed(this, 500);
        }
    };

    private Runnable mFinish = new Runnable() {
        @Override public void run() {
            if (mHandler == null) return;
            new PhotoProcessTask().execute();
        }
    };

    /* ----- Permissions ----- */
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static String[] PERMISSIONS_CAMERA = {
            Manifest.permission.CAMERA
    };

    /* ----- Misc ----- */

    Detector<Face> safeDetector;

    private TextureView.SurfaceTextureListener surfaceTextureListener=new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
            openCamera(width,height);
        }
        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        }
        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
            return false;
        }
        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        }
    };

    private CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            cameraDevice = camera;
            startCamera();
        }
        @Override
        public void onDisconnected(CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            cameraDevice = null;
        }
        @Override
        public void onError(CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            cameraDevice.close();
            cameraDevice = null;
            CameraActivity.this.finish();
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);
        View decorView = getWindow().getDecorView();
        // Hide the status bar.
        int uiOptions = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        decorView.setSystemUiVisibility(uiOptions);

        verifyCameraPermissions(this);
        verifyStoragePermissions(this);
        textureView=(AutoFitTextureView)findViewById(R.id.textureview);
        textureView.setSurfaceTextureListener(surfaceTextureListener);
        getpicture = (Button)findViewById(R.id.getpicture);
        switchCameraButton = (ImageButton) findViewById(R.id.switch_camera);
        switchCameraButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                switchCamera();
            }
        });

        loadingView = (ImageView) findViewById(R.id.loading);
        loadingView.setBackgroundResource(R.drawable.loading_animation);
        loadingAnimation = (AnimationDrawable) loadingView.getBackground();
        getpicture.setOnTouchListener(new View.OnTouchListener() {

            @Override public boolean onTouch(View v, MotionEvent event) {
                switch(event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (mHandler != null) return true;
                        mHandler = new Handler();
                        mHandler.post(mAction);
                        mHandler.postDelayed(mFinish,5000);
                        break;
                    case MotionEvent.ACTION_UP:
                        if (mHandler == null) return true;
                        new PhotoProcessTask().execute();
                        break;
                }
                return false;
            }
        });

        taskExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadingView.setVisibility(View.GONE);
        loadingAnimation.stop();
        startBackgroundThread();
        FaceDetector detector = new FaceDetector.Builder(getApplicationContext())
                .setMode(FaceDetector.FAST_MODE)
                .setLandmarkType(FaceDetector.ALL_LANDMARKS)
                .setClassificationType(FaceDetector.ALL_CLASSIFICATIONS)
                .setTrackingEnabled(false)
                .build();
        taskExecutor = Executors.newSingleThreadExecutor();
        // This is a temporary workaround for a bug in the face detector with respect to operating
        // on very small images.  This will be fixed in a future release.  But in the near term, use
        // of the SafeFaceDetector class will patch the issue.
        safeDetector = new SafeFaceDetector(detector);

        // When the screen is turned off and turned back on, the SurfaceTexture is already
        // available, and "onSurfaceTextureAvailable" will not be called. In that case, we can open
        // a camera and start preview from here (otherwise, we wait until the surface is ready in
        // the SurfaceTextureListener).
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }
    @Override
    public void onPause() {
        closeCamera();
        stopBackgroundThread();
        safeDetector.release();
        safeDetector = null;
        taskExecutor = null;
        super.onPause();
    }

    @Override
    protected void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState); // the UI component values are saved here.
        savedInstanceState.putInt("CameraFace", cameraFace);
    }
    @Override
    public void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        cameraFace = savedInstanceState.getInt("CameraFace");
    }
    /**
     * Starts a background thread and its {@link Handler}.
     */
    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    /**
     * Stops the background thread and its {@link Handler}.
     */
    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
    public static void verifyCameraPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.CAMERA);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_CAMERA,
                    REQUEST_CAMERA
            );
        }
    }
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.READ_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    public void switchCamera() {
        if (cameraFace == CameraCharacteristics.LENS_FACING_BACK) {
            cameraFace = CameraCharacteristics.LENS_FACING_FRONT;
            closeCamera();
            reopenCamera();

        } else if (cameraFace == CameraCharacteristics.LENS_FACING_FRONT) {
            cameraFace = CameraCharacteristics.LENS_FACING_BACK;
            closeCamera();
            reopenCamera();
        }
    }

    public void reopenCamera() {
        if (textureView.isAvailable()) {
            openCamera(textureView.getWidth(), textureView.getHeight());
        } else {
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }
    /**
     * Closes the current {@link CameraDevice}.
     */
    private void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            if (null != previewSession) {
                previewSession.close();
                previewSession = null;
            }
            if (null != cameraDevice) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (null != mImageReader) {
                mImageReader.close();
                mImageReader = null;
            }
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    public  void openCamera(int width, int height) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            verifyCameraPermissions(this); // TODO clean this
            return;
        }
        setUpCameraOutputs(width,height);
        configureTransform(width,height);
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            manager.openCamera(cameraId, stateCallback, mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while trying to lock camera opening.", e);
        }
    }

    /**
     * Sets up member variables related to camera.
     *
     * @param width  The width of available size for camera preview
     * @param height The height of available size for camera preview
     */
    private void setUpCameraOutputs(int width, int height) {
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics
                        = manager.getCameraCharacteristics(cameraId);

                // We don't use a front facing camera in this sample.
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing != cameraFace) {
                    continue;
                }

                StreamConfigurationMap map = characteristics.get(
                        CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (map == null) {
                    continue;
                }
                // For still image captures, we use the largest available size.
                Size largest = map.getOutputSizes(ImageFormat.JPEG)[0];

                ImageReader.OnImageAvailableListener imageAvailableListener = new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        if (!mTakePhoto) {
                            if (reader != null) {
                                Image image = reader.acquireLatestImage();
                                if(image != null)
                                    image.close();
                            }
                            return;
                        }
                        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmssSSS").format(new Date());
                        String imageFileName = "JPEG_" + timeStamp + ".jpg";
                        File photo = new File(Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_DOWNLOADS), imageFileName);
                        boolean faceFront = cameraFace == CameraCharacteristics.LENS_FACING_FRONT;
                        String currentPhotoPath = photo.getAbsolutePath();
                        mImageScores.put(currentPhotoPath,0.0);
                        mBackgroundHandler.post(new ImageSaver(reader.acquireNextImage(), photo,
                                safeDetector, faceFront, mImageScores, taskExecutor));
                        mTakePhoto = false;
                    }
                };
                mImageReader = ImageReader.newInstance(largest.getWidth(),
                        largest.getHeight(), ImageFormat.JPEG, 2);
                mImageReader.setOnImageAvailableListener(imageAvailableListener, mBackgroundHandler);

                // Find out if we need to swap dimension to get the preview size relative to sensor
                // coordinate.
                int displayRotation = getWindowManager().getDefaultDisplay().getRotation();
                //noinspection ConstantConditions
                mSensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
                boolean swappedDimensions = false;
                switch (displayRotation) {
                    case Surface.ROTATION_0:
                    case Surface.ROTATION_180:
                        if (mSensorOrientation == 90 || mSensorOrientation == 270) {
                            swappedDimensions = true;
                        }
                        break;
                    case Surface.ROTATION_90:
                    case Surface.ROTATION_270:
                        if (mSensorOrientation == 0 || mSensorOrientation == 180) {
                            swappedDimensions = true;
                        }
                        break;
                    default:
                        Log.e(TAG, "Display rotation is invalid: " + displayRotation);
                }
                Point displaySize = new Point();
                getWindowManager().getDefaultDisplay().getSize(displaySize);
                int rotatedPreviewWidth = width;
                int rotatedPreviewHeight = height;
                int maxPreviewWidth = displaySize.x;
                int maxPreviewHeight = displaySize.y;

                if (swappedDimensions) {
                    rotatedPreviewWidth = height;
                    rotatedPreviewHeight = width;
                    maxPreviewWidth = displaySize.y;
                    maxPreviewHeight = displaySize.x;
                }

                if (maxPreviewWidth > MAX_PREVIEW_WIDTH) {
                    maxPreviewWidth = MAX_PREVIEW_WIDTH;
                }

                if (maxPreviewHeight > MAX_PREVIEW_HEIGHT) {
                    maxPreviewHeight = MAX_PREVIEW_HEIGHT;
                }

                // Danger, W.R.! Attempting to use too large a preview size could  exceed the camera
                // bus' bandwidth limitation, resulting in gorgeous previews but the storage of
                // garbage capture data.
                mPreviewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class),
                        rotatedPreviewWidth, rotatedPreviewHeight, maxPreviewWidth,
                        maxPreviewHeight, largest);

                // We fit the aspect ratio of TextureView to the size of preview we picked.
                int orientation = getResources().getConfiguration().orientation;
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    textureView.setAspectRatio(
                            mPreviewSize.getWidth(), mPreviewSize.getHeight());
                } else {
                    textureView.setAspectRatio(
                            mPreviewSize.getHeight(), mPreviewSize.getWidth());
                }


                this.cameraId = cameraId;
                return;
            }
        } catch(CameraAccessException e){
            e.printStackTrace();
        }
    }
    /**
     * Configures the necessary {@link android.graphics.Matrix} transformation to `mTextureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `mTextureView` is fixed.
     *
     * @param viewWidth  The width of `mTextureView`
     * @param viewHeight The height of `mTextureView`
     */
    private void configureTransform(int viewWidth, int viewHeight) {
        if (null == textureView || null == mPreviewSize) {
            return;
        }
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getHeight(), mPreviewSize.getWidth());
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();
        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation) {
            bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth());
            matrix.postScale(scale, scale, centerX, centerY);
            matrix.postRotate(90 * (rotation - 2), centerX, centerY);
        } else if (Surface.ROTATION_180 == rotation) {
            matrix.postRotate(180, centerX, centerY);
        }
        textureView.setTransform(matrix);
    }
    private static Size chooseOptimalSize(Size[] choices, int textureViewWidth,
                                          int textureViewHeight, int maxWidth,
                                          int maxHeight, Size aspectRatio) {

        // Collect the supported resolutions that are at least as big as the preview Surface
        List<Size> bigEnough = new ArrayList<>();
        // Collect the supported resolutions that are smaller than the preview Surface
        List<Size> notBigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size option : choices) {
            if (option.getWidth() <= maxWidth && option.getHeight() <= maxHeight &&
                    option.getHeight() == option.getWidth() * h / w) {
                if (option.getWidth() >= textureViewWidth &&
                        option.getHeight() >= textureViewHeight) {
                    bigEnough.add(option);
                } else {
                    notBigEnough.add(option);
                }
            }
        }

        // Pick the smallest of those big enough. If there is no one big enough, pick the
        // largest of those not big enough.
        if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else if (notBigEnough.size() > 0) {
            return Collections.max(notBigEnough, new CompareSizesByArea());
        } else {
            Log.e(TAG, "Couldn't find any suitable preview size");
            return choices[0];
        }
    }
    private static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    private void startCamera() {
        try {
            if(cameraDevice == null || !textureView.isAvailable() || mPreviewSize == null) {
                return;
            }
            SurfaceTexture texture = textureView.getSurfaceTexture();
            if(texture == null) {
                return;
            }
            texture.setDefaultBufferSize(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            Surface surface = new Surface(texture);
            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(surface);
            previewBuilder.addTarget(mImageReader.getSurface());
            cameraDevice.createCaptureSession(Arrays.asList(surface,mImageReader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession session) {
                            previewSession = session;
                            getChangedPreview();
                        }
                        @Override
                        public void onConfigureFailed(CameraCaptureSession session) {
                        }
            },null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    void getChangedPreview() {
        if(cameraDevice == null) {
            return;
        }
        try {
            previewBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
            previewBuilder.set(CaptureRequest.EDGE_MODE,
                    CaptureRequest.EDGE_MODE_FAST);
            previewBuilder.set(
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                    CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_ON);
            previewBuilder.set(
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE,
                    CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE_OFF);
            previewBuilder.set(CaptureRequest.NOISE_REDUCTION_MODE,
                    CaptureRequest.NOISE_REDUCTION_MODE_OFF);
            previewBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER,
                    CaptureRequest.CONTROL_AF_TRIGGER_CANCEL);
            previewSession.setRepeatingRequest(previewBuilder.build(), null, mBackgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Saves a JPEG {@link Image} into the specified {@link File}.
     */
    private static class ImageSaver implements Runnable {

        /**
         * The JPEG image
         */
        private final Image mImage;
        /**
         * The file we save the image into.
         */
        private final File mFile;
        /**
         * Reference to a Context
         */
        private final Detector<Face> mSafeDetector;

        /**
         * Reference to the score ArrayList
         */
        private final Map<String,Double> mImageScores;

        private final ExecutorService mTaskExecutor;

        boolean mFaceFront;

        public ImageSaver(Image image, File file, Detector<Face> safeDetector, boolean faceFront,
                          Map<String,Double> imageScores, ExecutorService taskExecutor) {
            mImage = image;
            mFile = file;
            mSafeDetector = safeDetector;
            mFaceFront = faceFront;
            mImageScores = imageScores;
            mTaskExecutor = taskExecutor;
        }

        @Override
        public void run() {
            Log.d(TAG, mFile.toString());
            ByteBuffer buffer = mImage.getPlanes()[0].getBuffer();
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            FileOutputStream output = null;
            try {
                output = new FileOutputStream(mFile);
                output.write(bytes);

                // write to EXIF header
                // TODO: 3/14/2017 temporary solution
                ExifInterface exif = new ExifInterface(mFile.getAbsolutePath());
                if (mFaceFront) {
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_ROTATE_270));
                } else{
                    exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                            String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                }
                exif.saveAttributes();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                mImage.close();
                if (null != output) {
                    try {
                        output.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }

            mTaskExecutor.submit(new ImageAnalyzer(mFile.getAbsolutePath(),
                    mSafeDetector, mImageScores));
            // TODO: 3/16/2017 Race condition
        }
    }
    /**
     * Analyzes bitmaps for faces.
     */
    private static class ImageAnalyzer implements Runnable {
        /**
         * The file we save the image into.
         */
        private final String mFilepath;
        /**
         * Reference to a Context
         */
        private final Detector<Face> mSafeDetector;

        /**
         * Reference to the score ArrayList
         */
        private final Map<String,Double> mImageScores;

        public ImageAnalyzer(String filepath, Detector<Face> safeDetector,
                             Map<String,Double> imageScores) {
            mFilepath = filepath;
            mSafeDetector = safeDetector;
            mImageScores = imageScores;
        }

        @Override
        public void run() {

            Log.d(TAG,mFilepath);
            Bitmap bitmap = null;
            double sumscore = 0;
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inJustDecodeBounds = false;
                bitmap = BitmapFactory.decodeFile(mFilepath, options);

                // rotate image based on EXIF header
                Bitmap newBitmap = checkImageRotation(mFilepath,bitmap);
                bitmap.recycle();
                bitmap = newBitmap;
            } catch (Exception e){
                e.printStackTrace();
            }
            try {
            Log.d(TAG,mFilepath);
                // Create a frame from the bitmap and run face detection on the frame.
                Frame frame = new Frame.Builder().setBitmap(bitmap).build();
                SparseArray<Face> mFaces = mSafeDetector.detect(frame);
                bitmap.recycle();

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

                    double score;
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
                    Log.d(TAG,"Score is: " + score);
                    sumscore += score;
                }
                mImageScores.put(mFilepath,sumscore);
            }
            catch (Exception e) {
                Log.d(TAG, "Failed to load", e);
            }
            Log.d(TAG,"Total Score is: " + sumscore + " for "+mFilepath);
        }
        public static Bitmap checkImageRotation (String path,Bitmap bitmap){
            try {
                ExifInterface ei = new ExifInterface(path);
                int orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL);
                switch (orientation) {
                    case ExifInterface.ORIENTATION_ROTATE_90:
                        bitmap = rotateScaledImage(bitmap, 90);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_180:
                        bitmap = rotateScaledImage(bitmap, 180);
                        break;

                    case ExifInterface.ORIENTATION_ROTATE_270:
                        bitmap = rotateScaledImage(bitmap, 270);
                        break;

                    case ExifInterface.ORIENTATION_NORMAL:
                        bitmap = rotateScaledImage(bitmap, 0);

                    default:
                        break;
                }
            } catch (Exception e){
                e.printStackTrace();
            }
            return bitmap;
        }
        public static Bitmap rotateScaledImage(Bitmap source, float angle) {
            Matrix matrix = new Matrix();
            matrix.postRotate(angle);
            Bitmap newBitmap = Bitmap.createScaledBitmap(source, 640,
                    480, true);
            return Bitmap.createBitmap(newBitmap, 0, 0, newBitmap.getWidth(), newBitmap.getHeight(),
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
    private class PhotoProcessTask extends AsyncTask<Void, Void, String> {

        @Override
        protected String doInBackground(Void... params) {
            mHandler.removeCallbacks(mAction);
            mHandler.removeCallbacks(mFinish);
            mHandler = null;
            closeCamera();
            taskExecutor.shutdown();
            try {
                taskExecutor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return "Executed";
        }

        @Override
        protected void onPostExecute(String result) {
            Intent intent = new Intent(CameraActivity.this, DemoActivity.class);
            intent.putExtra("image_scores", mImageScores);
            startActivity(intent);
            loadingView.setVisibility(View.GONE);
            loadingAnimation.stop();
        }

        @Override
        protected void onPreExecute() {
            loadingView.setVisibility(View.VISIBLE);
            loadingAnimation.start();
        }

        @Override
        protected void onProgressUpdate(Void... values) {}
    }
}
