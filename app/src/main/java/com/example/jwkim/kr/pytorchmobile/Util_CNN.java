package com.example.jwkim.kr.pytorchmobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;

import org.pytorch.IValue;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.List;

public class Util_CNN {
    Activity activity;
    Module module;
    Util_Common util_common;

    public Util_CNN(Activity mAct) {
        this.activity = mAct;
        this.util_common = new Util_Common(mAct);
    }

    /*
     * inputBitmap -> Model
     */
    public Bitmap runModel(Module module, Bitmap inputBitmap) {

        // Bitmap -> Tensor 변환
        Log.i(activity.getString(R.string.tag), "Converting: Bitmap -> Tensor");

        // 시간 count 시작
        final long timeStart = SystemClock.elapsedRealtime();

        final Tensor inputTensor = convertBitmapToTensor(inputBitmap);

        // Bitmap -> Tensor 변환 시간
        final long timeBitmapToTensor = SystemClock.elapsedRealtime() - timeStart;

        final long[] input_shape = inputTensor.shape();
        Log.i(activity.getString(R.string.tag), "input Tensor shape: [" + input_shape[0] + "," + input_shape[1] + "," + input_shape[2] + "," + input_shape[3] + "]");
        Log.i(activity.getString(R.string.tag), "Completed: Bitmap -> Tensor");
        Log.i(activity.getString(R.string.tag), "Time : Bitmap -> Tensor : " + timeBitmapToTensor + " ms");


        // run model
        Log.i(activity.getString(R.string.tag), "Start: forwardModel");
        final Tensor outputTensor = forwardModel(module, inputTensor);

        // Forward 동작 시간
        final long timeForward = SystemClock.elapsedRealtime() - timeStart - timeBitmapToTensor;

        final long[] output_shape = outputTensor.shape();   // 1, 3(RGB), H, W
        Log.i(activity.getString(R.string.tag), "outputTensor shape: [" + output_shape[0] + "," + output_shape[1] + "," + output_shape[2] + "," + output_shape[3] + "]");
        Log.i(activity.getString(R.string.tag), "Completed: forwardModelCNN");
        Log.i(activity.getString(R.string.tag), "Time : Forward : " + timeForward + " ms");


        // Tensor -> RGB Bitmap 변환
        Log.i(activity.getString(R.string.tag), "Converting: Tensor -> Bitmap");
        final Bitmap outputBitmap = convertTensorToBitmapRGB(outputTensor, output_shape);
        // Tensor -> RGB Bitmap 변환 시간
        final long timeTensorToBitmap = SystemClock.elapsedRealtime() - timeStart - timeBitmapToTensor - timeForward;
        Log.i(activity.getString(R.string.tag), "Completed: Tensor -> Bitmap");
        Log.i(activity.getString(R.string.tag), "Time : Tensor -> Bitmap : " + timeTensorToBitmap + " ms");

        // InputTensor -> OutputTensor Inference 소요 시간
        final long timeInference = SystemClock.elapsedRealtime() - timeStart;
        Log.i(activity.getString(R.string.tag), "Elapsed time for inference : " + timeInference + " ms");
        Toast.makeText(activity, "BtoT: " + timeBitmapToTensor + "/ Fwd: " + timeForward + "/ TtoB: " + timeTensorToBitmap, Toast.LENGTH_LONG).show();

        return outputBitmap;
    }






    /*
     * Bitmap -> float32 Tensor 변환
     */
    public Tensor convertBitmapToTensor(Bitmap bitmap) {
        Bitmap mutableBitmap = null;
        if (Build.VERSION.SDK_INT >= 29) {
            // Android 10에서부터 Bitmap이 HARDWARE를 사용함.(자세한 이유는 모르겠음) => 못사용하게 막음
            // 다음 에러가 뜸게 됨(작동엔 이상 없음): E/libc: Access denied finding property "ro.hardware.chipname"
            // 참고: https://discuss.pytorch.org/t/unable-to-getpixels-pixel-access-is-not-supported-on-config-hardware-bitmaps/70691
            mutableBitmap = bitmap.copy(Bitmap.Config.RGBA_F16, true);
        }
        else {
            mutableBitmap = bitmap;
        }

        final Tensor tensor = TensorImageUtils.bitmapToFloat32Tensor(mutableBitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);
        return tensor;
    }

    /*
     * CNN 파일 asset 불러오기
     */
    public Module loadModelCNN(String assetFileName_CNN) throws IOException {
        // asset에서 TorchScript 모델 file 로드
        Module module = Module.load(util_common.assetFilePath(activity, assetFileName_CNN));
        return module;
    }

    /*
     * Model forward 시작
     */
    public Tensor forwardModel(@NonNull Module module, Tensor inputTensor) {
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        return outputTensor;
    }

    /*
     * Tensor -> float array 변환
     */
    public Bitmap convertTensorToBitmapRGB(@NonNull Tensor tensor, @NonNull long[] tensorShape) {

        final int width = (int) tensorShape[3];
        final int height = (int) tensorShape[2];

        // Tensor -> float array
        final float[] fArray = tensor.getDataAsFloatArray();

        // RGB 나누기: [RGB][width*height]
        final float[][] fArray_RGB = separateRGB(fArray, width, height);

        // 채널마다 denormalize + [0, 255]범위로 clip
        final int[][] denormClipResult = denormAndClipRGB(fArray_RGB, width, height);

        // denorm RGB를
        final Bitmap outBitmap = bitmapFromRGBImageAsDenormIntArray(denormClipResult, width, height);

        return outBitmap;
    }


    /*
     *  Separating RGB from a fArray to denormalize RGB separately
     */
    public float[][] separateRGB(@NonNull float[] fArray, @NonNull int width, @NonNull int height) {
        int numPixel = width * height;
        float[][] fArray_RGB = new float[3][numPixel];

        // fArray 총 길이가 RGB 분리할 수 있는 길이가 아니면 에러
        if ( numPixel * 3 != fArray.length) {
            util_common.fn_error("fArray length is not proper to separate RGB", activity.getString(R.string.tag));
        }

        // RGB 분리

        for(int i = 0; i < numPixel; i++) {
            fArray_RGB[0][i] = fArray[i];                           // R
            fArray_RGB[1][i] = fArray[i + 1 * (numPixel)];    // G
            fArray_RGB[2][i] = fArray[i + 2 * (numPixel)];    // B
        }

        return fArray_RGB;
    }


    /*
     * RGB denormalization + x255 + clipping to [0, 255]
     */
    public int[][] denormAndClipRGB(@NonNull float[][] img_RGB, @NonNull int width, @NonNull int height) {
        int numPixel = width * height;

        // RGB mean, std
        final float[] mean = {0.485f, 0.456f, 0.406f};
        final float[] std  = {0.229f, 0.224f, 0.225f};

        // denormalization => x 255 => int
        int[][] denormResult = new int[3][numPixel];
        for (int i = 0; i < numPixel ; i++) {
            denormResult[0][i] = (int) ((img_RGB[0][i] * std[0] + mean[0]) * 255);
            denormResult[1][i] = (int) ((img_RGB[1][i] * std[1] + mean[1]) * 255);
            denormResult[2][i] = (int) ((img_RGB[2][i] * std[2] + mean[2]) * 255);

            // clipping
            for (int j = 0; j < 3; j++) {
                if (denormResult[j][i] > 255) denormResult[j][i] = 255;
                else if (denormResult[j][i] < 0) denormResult[j][i] = 0;
            }
        }

        return denormResult;
    }


    /*
     * Denormalized + x255 + clipping된 int array -> bitmap 변환
     */
    public static Bitmap bitmapFromRGBImageAsDenormIntArray(@NonNull int[][] data, @NonNull int width, @NonNull int height){
        int numPixel = width * height;

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        for (int i = 0; i < numPixel; i++) {
            int r = data[0][i]; // R
            int g = data[1][i]; // G
            int b = data[2][i]; // B

            int x = i % width;  // 가로
            int y = i / width;  // 세로
            int color = Color.rgb(r, g, b);
            bitmap.setPixel(x, y, color);
        }
        return bitmap;
    }


    /*
     * float array -> RGB Bitmap 변환
     * 문제점: RGB 채널 상관 없이 max-min으로 normalize
     * 참고: https://github.com/pytorch/pytorch/issues/30655
     */
    public static Bitmap bitmapFromRGBImageAsFloatArray(@NonNull float[] data, @NonNull int width, @NonNull int height){
        int numPixel = width * height;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        float max = data[0];
        float min = data[0];

        for(float f: data){
            if(f > max) max = f;
            if(f < min) min = f;
        }

        float delta = (float)(max - min);

        for (int i = 0; i < numPixel; i++) {
            int r = (int) ((data[i] - min)/delta*255.0f);                           // R
            int g = (int) ((data[i + numPixel] - min) / delta*255.0f);        // G
            int b = (int) ((data[i + numPixel * 2] - min) / delta*255.0f);    // B

            int x = i % width;  // 가로
            int y = i / width;  // 세로
            int color = Color.rgb(r, g, b);
            bitmap.setPixel(x, y, color);
        }
        return bitmap;
    }
}
