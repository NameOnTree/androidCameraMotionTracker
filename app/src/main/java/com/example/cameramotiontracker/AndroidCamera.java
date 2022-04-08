package com.example.cameramotiontracker;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class AndroidCamera {

    private final CameraManager cameraManager;
    private CameraCharacteristics mCameraCharacteristics;
    private CameraDevice mCameraDevice;
    private String mCameraId;
    private CameraDevice.StateCallback mCameraDeviceStateCallback;

    private HandlerThread mHandlerThread;
    private Handler mHandler;

    Lock lock;
    Condition mCameraCondition;
    Executor mExecutor;
    int EXECUTOR_THREADS;

    private static final Map<Integer, String> LENS_INT_TO_STRING = new HashMap<>();
    static {
        LENS_INT_TO_STRING.put(CameraCharacteristics.LENS_FACING_FRONT, "LENS_FACING_FRONT");
        LENS_INT_TO_STRING.put(CameraCharacteristics.LENS_FACING_BACK, "LENS_FACING_BACK");
        LENS_INT_TO_STRING.put(CameraCharacteristics.LENS_FACING_EXTERNAL, "LENS_FACING_EXTERNAL");
    }

    public AndroidCamera(CameraManager manager) {
        cameraManager = manager;
        mCameraDeviceStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                lock.lock();
                try {
                    mCameraDevice = camera;
                    mCameraCondition.signal();
                } finally {
                    lock.unlock();
                }
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                camera.close();
                mCameraDevice = null;
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                camera.close();
                mCameraDevice = null;
            }

            @Override
            public void onClosed(@NonNull CameraDevice camera) {
                Log.d("CameraDevice", "CameraDevice Closed");
            }
        };
        lock = new ReentrantLock();
        mCameraCondition = lock.newCondition();
    }
//    public void shutdownExecutorService() {
//        if(mExecutor == null) {
//            return;
//        }
//        try {
//            // Wait a while for existing tasks to terminate
//            mExecutor.shutdown();
//            if (!mExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
//                mExecutor.shutdownNow(); // Cancel currently executing tasks
//                // Wait a while for tasks to respond to being cancelled
//                if (!mExecutor.awaitTermination(5, TimeUnit.SECONDS))
//                    Log.e("ExecutorService", "Pool did not terminate.");
//            }
//            mExecutor = null;
//        } catch (InterruptedException ie) {
//            // (Re-)Cancel if current thread also interrupted
//            mExecutor.shutdownNow();
//            mExecutor = null;
//            // Preserve interrupt status
//            Thread.currentThread().interrupt();
//        }
//    }

//    private void startThread() {
//        mHandlerThread = new HandlerThread("AndroidCameraThread");
//        mHandlerThread.start();
//        mHandler = new Handler(mHandlerThread.getLooper());
//    }
//    public void stopThread() {
//        if(mHandlerThread != null) {
//            mHandlerThread.quit();
//            try {
//                mHandlerThread.join(1);
//                mHandlerThread = null;
//                mHandler = null;
//            } catch (InterruptedException e) {
//                e.printStackTrace();
//            }
//        }
//    }

    /**
     * Choose Camera Resolution from list of resolutions.
     * This method picks resolution that is big enough to cover the whole height.
     *
     * @param options
     * @param height
     */
    private Size chooseCameraResolutionByHeight(Size[] options, int height) {
        List<Size> bigSize = new ArrayList<>();
        for(Size size : options) {
            if(size.getHeight() > height) {
                bigSize.add(size);
            }
        }
        if(bigSize.size() > 0) {
            return Collections.min(bigSize, new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Long.signum((long)o1.getWidth() * o1.getHeight() - (long)o2.getWidth() * o2.getHeight());
                }
            });
        } else {
            return Collections.max(Arrays.asList(options), new Comparator<Size>() {
                @Override
                public int compare(Size o1, Size o2) {
                    return Long.signum((long)o1.getWidth() * o1.getHeight() - (long)o2.getWidth() * o2.getHeight());
                }
            });
        }
    }

    /**
     * Setup Camera properties
     * The setup follows the following sequence:
     *
     * 1. Get Camera Manager from the system.
     * 2. Check if there's a Camera Permission.
     * 3. Select Camera from the list of Camera from Camera,
     * 4. get device id
     * @param lensFacing camera lens facing direction. Use field of CameraCharacteristics as input
     * @throws IllegalAccessException when Camera Lens is not found
     */
    public void setupCamera(int lensFacing) throws IllegalAccessException {

        try {
            for(String cameraId : cameraManager.getCameraIdList()) {
                CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
                if(cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) != lensFacing) {
                    continue;
                }
                mCameraId = cameraId;
                mCameraCharacteristics = cameraCharacteristics;
            }
            if(mCameraId == null) {
                throw new IllegalAccessException("Camera Lens" + LENS_INT_TO_STRING.get(lensFacing) + "Not Found!");
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void startCamera(Context currentContext, Executor executor) throws InterruptedException {
        mExecutor = executor;

        if (mCameraDevice == null) {
            try {
                if(currentContext.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(mCameraId, mExecutor, mCameraDeviceStateCallback);
                    lock.lock();
                    try {
                        while(mCameraDevice == null) {
                            Log.d("CameraDevice", "Waiting Camera to start...");
                            mCameraCondition.await();
                        }
                    } finally {
                        lock.unlock();
                    }
                }
            } catch (CameraAccessException e) {
                e.printStackTrace();
            }
        }
    }

    public void startCameraRequest(List<Surface> surfaceList) {
        //mExecutor = Executors.newFixedThreadPool(EXECUTOR_THREADS);
        try {
            List<OutputConfiguration> configurations = new ArrayList<>();
            CaptureRequest.Builder builder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            for (Surface surface : surfaceList) {
                builder.addTarget(surface);
                configurations.add(new OutputConfiguration(surface));
            }
            mCameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, configurations,
                    mExecutor, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        session.setSingleRepeatingRequest(builder.build(), mExecutor, new CameraCaptureSession.CaptureCallback() {
                        });

                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e("AndroidCamera.startCameraRequest","Camera Capture Request failed!", new RuntimeException("Camera Capture Request failed!"));
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    super.onClosed(session);
//                    shutdownExecutorService();
                }
            }));

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    public void stopCamera() {
        if(mCameraDevice != null) {
            mCameraDevice.close();
            mCameraDevice = null;
        }
    }

    public CameraDevice getCameraDevice() {
        return mCameraDevice;
    }

    public CameraCharacteristics getCameraCharacteristics() {
        return mCameraCharacteristics;
    }

    public Size getOptimalSize(int width, int height, int format) {
        CameraCharacteristics cameraCharacteristics = null;
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size = chooseCameraResolutionByHeight(map.getOutputSizes(format), height);
        return size;
    }

    public Size getOptimalSize(int width, int height, Class format) {
        CameraCharacteristics cameraCharacteristics = null;
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        Size size = chooseCameraResolutionByHeight(map.getOutputSizes(format), height);
        return size;
    }

    public int[] getOutputFormats() {
        CameraCharacteristics cameraCharacteristics = null;
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(mCameraId);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        return map.getOutputFormats();
    }

}
