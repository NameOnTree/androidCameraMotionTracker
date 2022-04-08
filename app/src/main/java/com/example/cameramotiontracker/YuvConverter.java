package com.example.cameramotiontracker;

import android.graphics.ImageFormat;
import android.media.Image;

import java.nio.ByteBuffer;

public class YuvConverter {
    private YuvConverter() {}

    static {
        // define this in CMakeLists.txt file.
        System.loadLibrary("yuv2rgb-lib");
    }

    public static int[] toARGB(Image image) throws Exception {
        if (image.getFormat() != ImageFormat.YUV_420_888) {
            throw new IllegalArgumentException("Invalid image format");
        }

        int[] argbOutput = new int[image.getWidth() * image.getHeight()];
        if (!yuv420toArgbNative(
                image.getWidth(),
                image.getHeight(),
                image.getPlanes()[0].getBuffer(),       // Y buffer
                image.getPlanes()[1].getBuffer(),       // U buffer
                image.getPlanes()[2].getBuffer(),       // V buffer
                image.getPlanes()[0].getPixelStride(),  // Y pixel stride
                image.getPlanes()[1].getPixelStride(),  // U/V pixel stride
                image.getPlanes()[0].getRowStride(),    // Y row stride
                image.getPlanes()[1].getRowStride(),    // U/V row stride
                argbOutput)) {
            // Handle this based on your usecase.
            throw new Exception("Failed to convert YUV to Bitmap");
        }
        return argbOutput;
    }

    private static native boolean yuv420toArgbNative(
            int width,
            int height,
            ByteBuffer yByteBuffer,
            ByteBuffer uByteBuffer,
            ByteBuffer vByteBuffer,
            int yPixelStride,
            int uvPixelStride,
            int yRowStride,
            int uvRowStride,
            int[] argbOutput);
}
