package com.example.cameramotiontracker;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.widget.ImageView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // tensorFlow lite setting
    final static String MODEL_FILENAME = "lite-model_movenet_singlepose_lightning_tflite_int8_4.tflite";
    //final static String MODEL_FILENAME = "lite-model_movenet_singlepose_lightning_3.tflite";
    final static org.opencv.core.Size MODEL_IMAGE_SIZE = new org.opencv.core.Size(192, 192);

    final static int EXECUTOR_THREADS = 2;
    final static int CAMERA_PIXEL_FORMAT = ImageFormat.YUV_420_888;

    final int LENS_FACING_DIRECTION = CameraCharacteristics.LENS_FACING_FRONT;
    private static SparseIntArray ORIENTATIONS = new SparseIntArray();
    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 0);
        ORIENTATIONS.append(Surface.ROTATION_90, 90);
        ORIENTATIONS.append(Surface.ROTATION_180, 180);
        ORIENTATIONS.append(Surface.ROTATION_270, 270);
    }
    // Inference variables
    PoseEstimator mPoseEstimator;
    // OpenCV Variables
    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    long cTime;
    long pTime;

    ImageReader mImageReader;
    ImageReader.OnImageAvailableListener mOnImageAvailableListener = new ImageReader.OnImageAvailableListener() {
        @Override
        public void onImageAvailable(ImageReader reader) {
            cTime = System.currentTimeMillis();
            Log.i(TAG, "Overall FPS : " + 1000.0 / (cTime - pTime));
            pTime = cTime;
            long ppTime = cTime;
            Bitmap overlay = Bitmap.createBitmap(mCameraTextureViewSize.getWidth(), Math.round(mCameraTextureViewSize.getHeight() * mCameraTextureViewRatioScale), Bitmap.Config.ARGB_8888);
            //Bitmap overlay = Bitmap.createBitmap(mOptimalCameraSize.getHeight(), mOptimalCameraSize.getWidth(), Bitmap.Config.ARGB_8888);
//            Log.i(TAG, "" + mOptimalCameraSize.getWidth() + "x" + mOptimalCameraSize.getHeight());
            Canvas canvas = new Canvas(overlay);
            Image img = mImageReader.acquireLatestImage();
            if(img != null) {
                try {
                    //Log.i(TAG, "" + img.getWidth() + "x" + img.getHeight());
                    ppTime = cTime;

                    Mat mat = ImagePreprocessor.getOpenCVMat(img);

                    cTime = System.currentTimeMillis();
                    Log.i(TAG, "Time consumed for getOpenCVMat in milli seconds : " + (cTime - ppTime));
                    ppTime = cTime;
//                    Log.i(TAG, "" + mCameraRotation);

                    if(mCameraRotation == 270) {
                        Core.rotate(mat, mat, Core.ROTATE_90_COUNTERCLOCKWISE);
                    }
                    if(mCameraRotation == 90) {
                        Core.rotate(mat, mat, Core.ROTATE_90_CLOCKWISE);
                    }
                    if(mCameraRotation == 0) {
                        Core.rotate(mat, mat, Core.ROTATE_180);
                    }
                    Core.flip(mat, mat, 1);
                    Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);

                    cTime = System.currentTimeMillis();
                    Log.i(TAG, "Time consumed for preprocessing before inference in milli seconds : " + (cTime - ppTime));
                    ppTime = cTime;

                    double[][] bodyKeypoint = mPoseEstimator.runInference(mat);

                    cTime = System.currentTimeMillis();
                    Log.i(TAG, "Time consumed after inference in milli seconds : " + (cTime - ppTime));
                    ppTime = cTime;
//                    mat = mPoseEstimator.runInferenceTest(mat);
//                    Bitmap overlay = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888);
//                    Utils.matToBitmap(mat, overlay);
                    Paint paint = new Paint();
                    paint.setStyle(Paint.Style.STROKE);
                    paint.setColor(Color.rgb(255, 127, 200));
                    paint.setStrokeWidth(40f);
                    for (int i = 0; i < bodyKeypoint.length; i++) {
                        if (bodyKeypoint[i][2] < PoseEstimator.MIN_CROP_KEYPOINT_SCORE) {
                            continue;
                        }
//                        Log.i(TAG, "keypoint X: " + bodyKeypoint[i][1] + " Y:" + bodyKeypoint[i][0]);
                        float x = (float)bodyKeypoint[i][1] / mOptimalCameraSize.getHeight() * mCameraTextureView.getWidth();
                        float y = (float)bodyKeypoint[i][0] / mOptimalCameraSize.getWidth() * Math.round(mCameraTextureViewSize.getHeight() * mCameraTextureViewRatioScale);
                        canvas.drawPoint(x, y, paint);
                    }
                    paint.setStrokeWidth(20f);
                    canvas.drawRect(new Rect(1, 1, mCameraTextureViewSize.getWidth() - 1, Math.round(mCameraTextureViewSize.getHeight() * mCameraTextureViewRatioScale) - 1), paint);
//                        canvas.drawRect(500, 500, 600, 600, paint);

                    cTime = System.currentTimeMillis();
                    Log.i(TAG, "Time consumed for drawing in milli seconds : " + (cTime - ppTime));

                    runOnUiThread(() -> {
                        mKeypointImageView.setImageBitmap(overlay);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                }
                img.close();
            }
        }
    };

    HandlerThread mHandlerThread;
    Handler mHandler;

    AndroidCamera androidCamera;
    Size mOptimalCameraSize;
    private int mCameraRotation;

    Lock mLock;
    Condition mCondition;

    TextureView mCameraTextureView;
    Size mCameraTextureViewSize;
    float mCameraTextureViewRatioScale;

    ImageView mKeypointImageView;
    List<Surface> mCameraSurfaceList;

    TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            mCameraTextureViewSize = new Size(width, height);
            mOptimalCameraSize = androidCamera.getOptimalSize(width, height, SurfaceTexture.class);
            // insert surface into surfaceList
            mCameraSurfaceList.add(surfaceTextureToSurface(width, height, surface));
            // set textureView transformation matrix to adjust its aspect ratio.
            Matrix scaler = new Matrix();
            mCameraTextureViewRatioScale = getMatrixFixingRatio(width, height, mOptimalCameraSize.getHeight(), mOptimalCameraSize.getWidth(), true);
            scaler.setScale(1F, mCameraTextureViewRatioScale);
            mCameraTextureView.setTransform(scaler);
            // create a lock, send a signal to the condition variable.
            // These statements will prevent this program to go further forward before this task is done.
            mLock.lock();
            try {
                mCondition.signal();
            } finally {
                mLock.unlock();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };

    ExecutorService mExecutorService;
    private class CameraRunTask implements Runnable {
        @Override
        public void run() {
            runTask();
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////
    /// Helper Function Started /////////////////////////////////////////////////////////////////
    //////////////////////////////////////////////////////////////////////////////////////////////

    /**
     * This method sets the Size for given surfaceTexture
     *
     * @param width
     * @param height
     * @param surfaceTexture
     */
    private Surface surfaceTextureToSurface(int width, int height, SurfaceTexture surfaceTexture) {
        int c_width = width;
        int c_height = height;
        // if orientation of this device is portrait,
        // switch width and height to match longest dimension of screen with camera size
        // and choose the optimal camera size for this device
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            c_width = height;
            c_height = width;
        }
        //choose previewSize that is optimal
        Size previewSize = androidCamera.getOptimalSize(c_width, c_height, SurfaceTexture.class);
        Log.d(TAG, "Preview Size: " + previewSize.getWidth() + "x" + previewSize.getHeight());
        // set the buffer size. Later on, camera HLL will use this information to choose specific size of camera stream.
        surfaceTexture.setDefaultBufferSize(previewSize.getHeight(),previewSize.getWidth());
        // add this to the SurfaceLists, which will be used for building camera request
        return new Surface(surfaceTexture);
    }

    /**
     * Calculate Aspect Ratio and get transformation matrix.
     *
     * @param height
     * @param width
     * @param targetHeight
     * @param targetWidth
     * @param adjustHeight Whether to change height or width to adjust aspect ratio
     * @return
     */
    private float getMatrixFixingRatio(int width, int height, int targetWidth, int targetHeight, boolean adjustHeight) {

        // suppose we want to adjust the ratio by changing the height.
        // Then, (optimalHeight / width) should be equal to (targetHeight / targetWidth)
        // => optimalHeight = (targetHeight / targetWidth) * width.
        // Because what the scaling factor for our original height is (height / optimalHeight),
        // optimal ratio is (targetHeight / targetWidth) * (width / height);
        float optimalRatio;
        if (adjustHeight) {
            optimalRatio = ((float)targetHeight / targetWidth) * ((float)width / height);
        } else {
            optimalRatio = ((float)targetWidth / targetHeight) * ((float)height / width);
        }
        //Log.i(TAG, "optimalRatio is " + optimalRatio);
        return optimalRatio;
    }


    private int calculateCameraRotation() {
        int deviceOrientation;
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            deviceOrientation = ORIENTATIONS.get(getDisplay().getRotation());
        } else {
            deviceOrientation = ORIENTATIONS.get(getWindowManager().getDefaultDisplay().getRotation());
        }
        int cameraOrientation = androidCamera.getCameraCharacteristics().get(CameraCharacteristics.SENSOR_ORIENTATION);

        Log.d(TAG, "Device Orientation x Camera Orientation = " + deviceOrientation + "x" + cameraOrientation);
        if(LENS_FACING_DIRECTION == CameraCharacteristics.LENS_FACING_FRONT) {
            return (cameraOrientation - deviceOrientation + 360) % 360;
        }
        return (cameraOrientation + deviceOrientation + 360) % 360;
    }

    private void shutdownExecutorService() {
        try {
            // Wait a while for existing tasks to terminate
            mExecutorService.shutdown();
            if (!mExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                mExecutorService.shutdownNow(); // Cancel currently executing tasks
                // Wait a while for tasks to respond to being cancelled
                if (!mExecutorService.awaitTermination(5, TimeUnit.SECONDS))
                    Log.e("ExecutorService", "Pool did not terminate.");
            }
            mExecutorService = null;
        } catch (InterruptedException ie) {
            // (Re-)Cancel if current thread also interrupted
            mExecutorService.shutdownNow();
            mExecutorService = null;
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }
    }

    private void startExecutorService() {
        if (mExecutorService == null) {
            mExecutorService = Executors.newFixedThreadPool(EXECUTOR_THREADS);
        }
    }

    private void startImageProcessingThread(String name) {
        if(mHandlerThread == null) {
            mHandlerThread = new HandlerThread(name);
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }
    }
    private void stopImageProcessingThread() {
        mHandlerThread.quitSafely();
        try {
            mHandlerThread.join();
            mHandlerThread = null;
            mHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void runTask() {
        // wait until everything to run this task is available
        mLock.lock();
        try {
            while(!mCameraTextureView.isAvailable()) {
                try {
                    mCondition.await();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        } finally {
            mLock.unlock();
        }

        Log.i(TAG, "Camera Size of Image: " + mOptimalCameraSize.getWidth() + "x" + mOptimalCameraSize.getHeight());

        //androidCamera.getOptimalSize()
        mImageReader = ImageReader.newInstance(mOptimalCameraSize.getWidth(), mOptimalCameraSize.getHeight(), CAMERA_PIXEL_FORMAT, 5);
        mImageReader.setOnImageAvailableListener(mOnImageAvailableListener, mHandler);
        mCameraSurfaceList.add(mImageReader.getSurface());
        // initialize PoseEstimator
        mPoseEstimator = new PoseEstimator(getApplicationContext(), MODEL_FILENAME, mOptimalCameraSize.getHeight(), mOptimalCameraSize.getWidth(), MODEL_IMAGE_SIZE);

        //mKeypointImageView.setImageMatrix(getMatrixFixingRatio(mKeypointImageView.getWidth(), mKeypointImageView.getHeight(),
        //        mOptimalCameraSize.getHeight(), mOptimalCameraSize.getWidth(), true));
        // if all conditions are matched, start camera request
        try {
            androidCamera.startCamera(getApplicationContext(), mExecutorService);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        androidCamera.startCameraRequest(mCameraSurfaceList);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (!OpenCVLoader.initDebug()) {
            Log.d(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_4_0, this, mLoaderCallback);
        } else {
            Log.d(TAG, "OpenCV library found inside package. Using it!");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        // make pool of executors
        startExecutorService();
        // set up camera
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        androidCamera = new AndroidCamera(cameraManager);

        if(getApplicationContext().checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                Toast.makeText(getApplicationContext(), "Video app requires access to camera", Toast.LENGTH_SHORT).show();
            }
            requestPermissions(new String[] {Manifest.permission.CAMERA}, 0);
        }
        try {
            androidCamera.setupCamera(LENS_FACING_DIRECTION);
        } catch (IllegalAccessException e) {
            Toast.makeText(getApplicationContext(), "Could not setup Camera! Check if camera exists", Toast.LENGTH_SHORT);
            e.printStackTrace();
        }

        mLock = new ReentrantLock();
        mCondition = mLock.newCondition();

        mCameraSurfaceList = new ArrayList<>();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // get TextureView from the layout
        mCameraTextureView = findViewById(R.id.cameraTextureView);

        mKeypointImageView = findViewById(R.id.imageView);

        mCameraRotation = calculateCameraRotation();

        startExecutorService();
        startImageProcessingThread("CameraImageProcessing");
        // Texture view should be available from now on.
        if(!mCameraTextureView.isAvailable()) {
            mCameraTextureView.setSurfaceTextureListener(surfaceTextureListener);
        } else {
            int textureViewWidth = mCameraTextureView.getWidth();
            int textureViewHeight = mCameraTextureView.getHeight();

            mCameraTextureViewSize = new Size(textureViewWidth, textureViewHeight);
            mOptimalCameraSize = androidCamera.getOptimalSize(textureViewWidth, textureViewHeight, SurfaceTexture.class);
            // insert surface into surfaceList
            mCameraSurfaceList.add(surfaceTextureToSurface(textureViewWidth, textureViewHeight, mCameraTextureView.getSurfaceTexture()));
            // set textureView transformation matrix to adjust its aspect ratio.
            // flip target dimension, as textureview automatically flips the dimension to match the size.
            Matrix scaler = new Matrix();
            mCameraTextureViewRatioScale = getMatrixFixingRatio(textureViewWidth, textureViewHeight, mOptimalCameraSize.getHeight(), mOptimalCameraSize.getWidth(), true);
            scaler.setScale(1F, mCameraTextureViewRatioScale);
            mCameraTextureView.setTransform(scaler);
        }
        mExecutorService.execute(new CameraRunTask());
    }



    @Override
    protected void onPause() {
        Log.d("onPause", "Pausing related services...");
        androidCamera.stopCamera();
        Log.d("onPause", "finished stopping camera");
        shutdownExecutorService();
        stopImageProcessingThread();
        Log.d("onPause", "finished shutting down background Threads");
        mCameraSurfaceList.clear();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        Log.d("onDestroy", "Destroying...");
//        androidCamera.stopCamera();
//        androidCamera.stopThread();
//        shutdownExecutorService();
//        stopImageProcessingThread();
        //mCameraSurfaceList.clear();
        super.onDestroy();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mCameraRotation = calculateCameraRotation();
    }
}