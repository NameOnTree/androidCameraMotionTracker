package com.example.cameramotiontracker;

import android.graphics.Bitmap;
import android.media.Image;

import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * This Class uses OpenCV Java APIs to preprocess Image
 *
 */
public class ImagePreprocessor {

    /**
     * Converts Android Image object into OpenCV Mat Object
     * This method ASSUMES "YUV_420_888" Image object as a parameter
     *
     * @param img Android Image object
     * @return Mat object
     */
    public static Mat getOpenCVMat(Image img) {
        Mat mat = null;
        int[] argb = null;

        try {
            argb = YuvConverter.toARGB(img);
        } catch (Exception e) {
            e.printStackTrace();
        }
        // each 32 bit integer of argb array is made of a byte of each channels in ARGB
        // Need to split them into bytes using ByteBuffer Trick
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(argb.length*4);
        IntBuffer intBuffer = byteBuffer.asIntBuffer();
        intBuffer.put(argb);
        // create an empty byte array to store image data from argb
        byte[] imageByte = new byte[byteBuffer.capacity()];
        byteBuffer.order(ByteOrder.nativeOrder()).get(imageByte);

        mat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC4);
        mat.put(0, 0, imageByte);
        // Switch first dimension to the last dimension.
        // ARGB to RGBA because OpenCV cvtColor API assumes alpha channel at the end.
        List<Mat> channels = new ArrayList<>();
        Core.split(mat, channels);
        channels.add(channels.get(0));
        channels.remove(0);
        Core.merge(channels, mat);

        // New Mat object to store BGR type
        // because most OpenCV APIs operates only on BGR type.
        Mat bgrMat = new Mat(img.getHeight(), img.getWidth(), CvType.CV_8UC3);
        Imgproc.cvtColor(mat, bgrMat, Imgproc.COLOR_RGBA2BGR);

        return bgrMat;
    }

    public static Bitmap getBitmapFromMat(Mat mat) {
        Bitmap bm = Bitmap.createBitmap(mat.width(), mat.height(), Bitmap.Config.ARGB_8888);
        Imgproc.cvtColor(mat, mat, Imgproc.COLOR_BGR2RGB);
        Utils.matToBitmap(mat, bm); // matToBitmap automatically converts Mat object to Image ARGB_8888 type.
        return bm;
    }

    public static Mat PadCropResize(Mat img, float left, float top, float right, float bottom, Size resizeSize) {
        int height = img.height();
        int width = img.width();

        boolean topPadOrResize = top < 0;
        boolean bottomPadOrResize = bottom > 1;
        boolean leftPadOrResize = left < 0;
        boolean rightPadOrResize = right > 1;

        int topPixelPad = topPadOrResize ? Math.abs(Math.round(top * height)) : 0;
        int bottomPixelPad = bottomPadOrResize ? Math.round((bottom - 1) * height) : 0;
        int leftPixelPad = leftPadOrResize ? Math.abs(Math.round(left * width)) : 0;
        int rightPixelPad = rightPadOrResize ? Math.round((right - 1) * width) : 0;

        Mat mat = new Mat();
        Core.copyMakeBorder(img, mat, topPixelPad, bottomPixelPad, leftPixelPad, rightPixelPad, Core.BORDER_CONSTANT, new Scalar(0,0,0));

        int topPixelCrop = topPadOrResize ? 0 : Math.round(top * height);
        int bottomPixelCrop = bottomPadOrResize ? mat.height() : Math.round(bottom * height);
        int leftPixelCrop = leftPadOrResize ? 0 : Math.round(left * width);
        int rightPixelCrop = rightPadOrResize ? mat.width() : Math.round(right * width);

        mat = mat.rowRange(topPixelCrop, bottomPixelCrop).colRange(leftPixelCrop, rightPixelCrop);

        //Mat resizedMat = new Mat();
        Imgproc.resize(mat, mat, resizeSize);

        return mat;
    }

    public static ByteBuffer getByteBufferFromMat(Mat mat) {
        ByteBuffer buffer = ByteBuffer.allocate(mat.cols() * mat.rows() * mat.channels());
        mat.get(0, 0, buffer.array());
        return buffer;
    }
}


