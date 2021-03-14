package com.example.jwkim.kr.pytorchmobile;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
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
     * 모델 불러오기
     */
    public Module loadModel(String assetName) {
        try {
            //bitmap = BitmapFactory.decodeStream(getAssets().open("0.jpg"));
            module = Module.load(assetFilePath(activity, "cnn_TorchScript.pt"));
        } catch (IOException e) {
            util_common.fn_IOexception(e, "Error reading assets");
        }

        return module;
    }


    /*
     * 모델 돌리기
     * Bitmap -> Tensor -> [[Module]] -> Tensor -> float array -> Bitmap
     */
    public Bitmap runModel(@NonNull Module module, Bitmap bitmap) {

        // 시작 시간
        final long timeStart = System.currentTimeMillis();

        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);
        long[] inputTensor_shape = inputTensor.shape();
        if (4 == inputTensor_shape.length) {
            Log.i(activity.getString(R.string.tag),"Shape: Input Tensor: [" + inputTensor_shape[0] + ", " + inputTensor_shape[0] + ", " + inputTensor_shape[2] + ", " + inputTensor_shape[3] + "]");
        }
        // running the model
        final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();
        long[] outputTensor_shape = outputTensor.shape();
        if (4 == outputTensor_shape.length) {
            Log.i(activity.getString(R.string.tag),"Shape: Output Tensor: [" + outputTensor_shape[0] + ", " + outputTensor_shape[0] + ", " + outputTensor_shape[2] + ", " + outputTensor_shape[3] + "]");
        }
        // Forward까지 시간
        final long timeForward = System.currentTimeMillis() - timeStart;

        // output tensor를 bitmap으로 convert
        // 참고: https://github.com/pytorch/pytorch/issues/30655
        // getting tensor content as java array of floats
        final float[] outFloatArray = outputTensor.getDataAsFloatArray();
        final long[] output_shape = outputTensor.shape();   // 1, 3, W, H

        Bitmap outBitmap = bitmapFromRGBImageAsFloatArray(outFloatArray, (int) output_shape[3], (int) output_shape[2]);

        // Bitmap으로 infer까지 시간
        final long timeInfer = System.currentTimeMillis() - timeStart;

        // 결과 간략 요약
        // Context 이름이 명시되면 text + long 이렇게도 허용됨..
        Toast.makeText(activity, "Resol:" + output_shape[2] + "x" + output_shape[3] + " Fwrd:" + timeForward + " Inf:" + timeInfer + "", Toast.LENGTH_LONG).show();

        return outBitmap;
    }



    /*
     * Asset File 불러오기
     */
    @NonNull
    public static String assetFilePath(@NonNull Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists() && file.length() >0){
            return file.getAbsolutePath();
        }

        try (InputStream is = context.getAssets().open(assetName)){
            try (OutputStream os = new FileOutputStream(file)){
                byte[] buffer = new byte[4 * 1024];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
                os.flush();
            }
            return file.getAbsolutePath();
        }
    }



    /*
     * float array -> Gray Bitmap
     */
    @NonNull
    private Bitmap arrayFloatToBitmap(List<Float> floatArray, int width, int height){
        //private Bitmap arrayFloatToBitmap(Float[] floatArray, int width, int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4*3) ;

        float Maximum = Collections.max(floatArray);
        float minimum = Collections.min(floatArray);
        float delta = Maximum - minimum ;

        int i = 0 ;
        for (float value : floatArray){
            byte temValue = (byte) ((byte) ((((value-minimum)/delta)*255)));
            byteBuffer.put(4*i, temValue) ;
            byteBuffer.put(4*i+1, temValue) ;
            byteBuffer.put(4*i+2, temValue) ;
            byteBuffer.put(4*i+3, alpha) ;
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }


    /*
     * float array -> RGB Bitmap
     * https://github.com/pytorch/pytorch/issues/49782
     */
    public static Bitmap bitmapFromRGBImageAsFloatArray(@NonNull float[] data, int width, int height){

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);

        float max = data[0];
        float min = data[0];

        for(float f: data){
            if(f > max) max = f;
            if(f < min) min = f;
        }

        int delta = (int)(max - min);

        for (int i = 0; i < width * height; i++) {

            int r = (int) ((data[i] - min)/delta*255.0f);
            int g = (int) ((data[i + width * height] - min) / delta*255.0f);
            int b = (int) ((data[i + width * height * 2] - min) / delta*255.0f);

            int w = i % width;
            int h = i / width;

            int color = Color.rgb(r, g, b);
            bmp.setPixel(w, h, color);
        }
        return bmp;
    }




    /*
     *
     */


    /*
     *
     */

}
