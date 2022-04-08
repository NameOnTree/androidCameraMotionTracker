package com.example.cameramotiontracker;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.util.Log;
import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import org.tensorflow.lite.gpu.CompatibilityList;
import org.tensorflow.lite.gpu.GpuDelegate;


public class PoseEstimator {
    public Interpreter mTensorflowLiteInterpreter;

    public final static double MIN_CROP_KEYPOINT_SCORE = 0.5;
    private final static String TAG = "PoseEstimator";

    private final static HashMap<String, Integer> KEYPOINT_DICT = new HashMap<>();

    static {
        KEYPOINT_DICT.put("nose", 0);
        KEYPOINT_DICT.put("left_eye", 1);
        KEYPOINT_DICT.put("right_eye", 2);
        KEYPOINT_DICT.put("left_ear", 3);
        KEYPOINT_DICT.put("right_ear", 4);
        KEYPOINT_DICT.put("left_shoulder", 5);
        KEYPOINT_DICT.put("right_shoulder", 6);
        KEYPOINT_DICT.put("left_elbow", 7);
        KEYPOINT_DICT.put("right_elbow", 8);
        KEYPOINT_DICT.put("left_wrist", 9);
        KEYPOINT_DICT.put("right_wrist", 10);
        KEYPOINT_DICT.put("left_hip", 11);
        KEYPOINT_DICT.put("right_hip", 12);
        KEYPOINT_DICT.put("left_knee", 13);
        KEYPOINT_DICT.put("right_knee", 14);
        KEYPOINT_DICT.put("left_ankle", 15);
        KEYPOINT_DICT.put("right_ankle", 16);
    }

    private final static String[] TORSO_JOINTS = new String[]{"left_shoulder", "right_shoulder", "left_hip", "right_hip"};

    private final static int CROP_REGION_Y_MIN = 0;
    private final static int CROP_REGION_X_MIN = 1;
    private final static int CROP_REGION_Y_MAX = 2;
    private final static int CROP_REGION_X_MAX = 3;
    private final static int CROP_REGION_HEIGHT = 4;
    private final static int CROP_REGION_WIDTH = 5;

    private double[] mCropRegion;
    private Size mCropSize;


    public PoseEstimator(Context context, String filename, int imageHeight, int imageWidth, Size cropSize) {
        Interpreter.Options options = new Interpreter.Options();
        CompatibilityList compatList = new CompatibilityList();

//        if(compatList.isDelegateSupportedOnThisDevice()){
//            // if the device has a supported GPU, add the GPU delegate
//            Log.i(TAG, "GPU Found!");
//            GpuDelegate.Options delegateOptions = compatList.getBestOptionsForThisDevice();
//            GpuDelegate gpuDelegate = new GpuDelegate(delegateOptions);
//            options.addDelegate(gpuDelegate);
//        } else {
//            // if the GPU is not supported, run on 4 threads
//            options.setNumThreads(4);
//        }
        GpuDelegate gpuDelegate = new GpuDelegate();
        options.addDelegate(gpuDelegate);

        try {
            mTensorflowLiteInterpreter = new Interpreter(loadModelFileFromAsset(context, filename), options);
        } catch (IOException e) {
            e.printStackTrace();
        }
        mCropRegion = initCropRegion(imageHeight, imageWidth);
        mCropSize = cropSize;
//        mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC4);
    }

    private static MappedByteBuffer loadModelFileFromAsset(Context context, String filename) throws IOException {
        AssetFileDescriptor fileDescriptor = context.getAssets().openFd(filename);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long length = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, length);
    }

    public float[][][][] predict(ByteBuffer buffer) {
        float[][][][] output = new float[1][1][17][3];
        mTensorflowLiteInterpreter.run(buffer, output);
        return output;
    }

    public double[][] runInference(Mat image) {
        double[][] bodyKeypoint = new double[17][3];

        int imageHeight = image.height();
        int imageWidth = image.width();
//        Log.i("runInference", "image Size" + imageWidth + "x" + imageHeight);
        //Log.i("runInference", "mCropRegion: " + Arrays.toString(mCropRegion));

        image = ImagePreprocessor.PadCropResize(image, (float)mCropRegion[CROP_REGION_X_MIN],
                (float)mCropRegion[CROP_REGION_Y_MIN], (float)mCropRegion[CROP_REGION_X_MAX], (float)mCropRegion[CROP_REGION_Y_MAX], mCropSize);


        ByteBuffer input = ImagePreprocessor.getByteBufferFromMat(image);
        float[][][][] output = this.predict(input);
        for (int i = 0; i < 17; i++) {
            bodyKeypoint[i][0] = (mCropRegion[CROP_REGION_Y_MIN] + output[0][0][i][0] * mCropRegion[CROP_REGION_HEIGHT]) * imageHeight;
            bodyKeypoint[i][1] = (mCropRegion[CROP_REGION_X_MIN] + output[0][0][i][1] * mCropRegion[CROP_REGION_WIDTH]) * imageWidth;
            bodyKeypoint[i][2] = output[0][0][i][2];
        }
        mCropRegion = determineCropRegion(bodyKeypoint, imageHeight, imageWidth);
        return bodyKeypoint;
    }
    public Mat runInferenceTest(Mat image) {
        double[][] bodyKeypoint = new double[17][3];

        int imageHeight = image.height();
        int imageWidth = image.width();
//        Log.i("runInference", "image Size" + imageWidth + "x" + imageHeight);
//        Log.i("runInference", "mCropRegion: " + Arrays.toString(mCropRegion));

        image = ImagePreprocessor.PadCropResize(image, (float)mCropRegion[CROP_REGION_X_MIN],
                (float)mCropRegion[CROP_REGION_Y_MIN], (float)mCropRegion[CROP_REGION_X_MAX], (float)mCropRegion[CROP_REGION_Y_MAX], mCropSize);


        ByteBuffer input = ImagePreprocessor.getByteBufferFromMat(image);
        float[][][][] output = this.predict(input);
//        Log.i("runInference", "output: " + Arrays.toString(output[0][0][0]));
        for (int i = 0; i < 17; i++) {
            bodyKeypoint[i][0] = (mCropRegion[CROP_REGION_Y_MIN] + output[0][0][i][0] * mCropRegion[CROP_REGION_HEIGHT]) * imageHeight;
            bodyKeypoint[i][1] = (mCropRegion[CROP_REGION_X_MIN] + output[0][0][i][1] * mCropRegion[CROP_REGION_WIDTH]) * imageWidth;
            bodyKeypoint[i][2] = output[0][0][i][2];
        }
        mCropRegion = determineCropRegion(bodyKeypoint, imageHeight, imageWidth);
        return image;
    }

    public double[] initCropRegion(int imageHeight, int imageWidth) {
        double[] region = new double[6];

        if (imageWidth > imageHeight) {
            region[CROP_REGION_HEIGHT] = 1.0 * imageWidth / imageHeight;
            region[CROP_REGION_WIDTH] = 1.0;
            region[CROP_REGION_Y_MIN] = (1.0 - region[CROP_REGION_HEIGHT]) / 2;
            region[CROP_REGION_X_MIN] = 0.0;
        } else {
            region[CROP_REGION_HEIGHT] = 1.0;
            region[CROP_REGION_WIDTH] = 1.0 * imageHeight / imageWidth;
            region[CROP_REGION_Y_MIN] = 0.0;
            region[CROP_REGION_X_MIN] = (1.0 - region[CROP_REGION_WIDTH]) / 2;
        }
        region[CROP_REGION_Y_MAX] = region[CROP_REGION_Y_MIN] + region[CROP_REGION_HEIGHT];
        region[CROP_REGION_X_MAX] = region[CROP_REGION_X_MIN] + region[CROP_REGION_WIDTH];
        return region;
    }

    public boolean torsoVisible(double[][] keypoints) {
        return (keypoints[KEYPOINT_DICT.get("left_hip")][2] > MIN_CROP_KEYPOINT_SCORE
                || keypoints[KEYPOINT_DICT.get("right_hip")][2] > MIN_CROP_KEYPOINT_SCORE)
                && (keypoints[KEYPOINT_DICT.get("left_shoulder")][2] > MIN_CROP_KEYPOINT_SCORE
                || keypoints[KEYPOINT_DICT.get("right_shoulder")][2] > MIN_CROP_KEYPOINT_SCORE);
    }

    public List<Double> determineBodyRange(double[][] bodyKeypoint, double centerY, double centerX) {
        double maxBodyYRange = 0.0;
        double maxBodyXRange = 0.0;

        double distY;
        double distX;

        for (String joint : KEYPOINT_DICT.keySet()) {
            if (bodyKeypoint[KEYPOINT_DICT.get(joint)][2] < MIN_CROP_KEYPOINT_SCORE) {
                continue;
            }
            distY = Math.abs(centerY - bodyKeypoint[KEYPOINT_DICT.get(joint)][0]);
            distX = Math.abs(centerX - bodyKeypoint[KEYPOINT_DICT.get(joint)][1]);
            if (distY > maxBodyYRange) {
                maxBodyYRange = distY;
            }
            if (distX > maxBodyXRange) {
                maxBodyXRange = distX;
            }
        }
        return Arrays.asList(maxBodyYRange, maxBodyXRange);
    }

    public double[] determineCropRegion(double[][] bodyKeypoint, int imageHeight, int imageWidth) {
        double[] cropRegion = new double[6];

        if (!torsoVisible(bodyKeypoint)) {
            return initCropRegion(imageHeight, imageWidth);
        } else {
            double centerY = (bodyKeypoint[KEYPOINT_DICT.get("left_hip")][0] + bodyKeypoint[KEYPOINT_DICT.get("right_hip")][0]) / 2;
            double centerX = (bodyKeypoint[KEYPOINT_DICT.get("left_hip")][1] + bodyKeypoint[KEYPOINT_DICT.get("right_hip")][1]) / 2;

            if (centerX < 0 || centerY < 0) {
                return initCropRegion(imageHeight, imageWidth);
            }

            List<Double> bodyRange = determineBodyRange(bodyKeypoint, centerY, centerX);
            bodyRange.set(0, bodyRange.get(0) * 1.2);
            bodyRange.set(1, bodyRange.get(1) * 1.2);
            //double cropLengthHalf = bodyRange[0] > bodyRange[1] ? bodyRange[0] * 1.2 : bodyRange[1] * 1.2;
            double cropLengthHalfByKeypoint = Collections.max(bodyRange);
            double cropLengthHalfByCenter = Collections.max((List<Double>) Arrays.asList(centerX, imageWidth - centerX, centerY, imageHeight - centerY));

            double cropLengthHalf = Math.min(cropLengthHalfByKeypoint, cropLengthHalfByCenter);

            //Log.i("PoseEstimator", "" + cropLengthHalf);

            if (cropLengthHalf > Math.max(imageHeight, imageWidth) / 2.0) {
                return initCropRegion(imageHeight, imageWidth);
            } else {
                cropRegion[CROP_REGION_Y_MIN] = (centerY - cropLengthHalf) / imageHeight;
                cropRegion[CROP_REGION_X_MIN] = (centerX - cropLengthHalf) / imageWidth;
                cropRegion[CROP_REGION_Y_MAX] = (centerY + cropLengthHalf) / imageHeight;
                cropRegion[CROP_REGION_X_MAX] = (centerX + cropLengthHalf) / imageWidth;
                cropRegion[CROP_REGION_HEIGHT] = (cropLengthHalf * 2) / imageHeight;
                cropRegion[CROP_REGION_WIDTH] = (cropLengthHalf * 2) / imageWidth;

                return cropRegion;
            }
        }
    }


}
