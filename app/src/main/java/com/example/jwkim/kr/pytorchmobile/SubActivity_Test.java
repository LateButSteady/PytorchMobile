package com.example.jwkim.kr.pytorchmobile;

import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

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

// 참고 https://www.youtube.com/watch?v=5Lxuu16_28o&feature=youtu.be

public class SubActivity_Test extends AppCompatActivity {

    private Bitmap bitmap = null;
    private Module module = null;
    //----- outputTensor 띄우기 위함
    ImageView imgView_stillshot_processed;
    ImageView imgView_stillshot_org;
    //-----
    //TextView text_cpp;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_test);

        imgView_stillshot_processed = findViewById(R.id.imgView_stillshot_processed);
        imgView_stillshot_org = findViewById(R.id.imgView_stillshot_org);
        //text_cpp = findViewById(R.id.text_cpp);

        try {
            bitmap = BitmapFactory.decodeStream(getAssets().open("macaw.JPG")); // 대소문자 중요
            // loading serialized torchscript module from packaged into app android asset model.pt,
            // app/src/model/assets/model.pt
            module = Module.load(assetFilePath(SubActivity_Test.this, "mobilenet-v2.pt"));

        } catch (IOException e) {
            Log.e(getString(R.string.tag), "Error reading assets", e);
            finish();
        }

        // showing image on UI
        ImageView imageView_Infer = findViewById(R.id.imageView_Infer);

        imageView_Infer.setImageBitmap(bitmap);

        // infer 버튼
        final Button button_infer = (Button) findViewById(R.id.btn_Infer);
        button_infer.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {

                final Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap,
                        TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);

                // running the model
                final Tensor outputTensor = module.forward(IValue.from(inputTensor)).toTensor();

                // output tensor를 bitmap으로 convert
                // 참고: https://github.com/pytorch/pytorch/issues/30655
                // 음...? 바로 밑에서 floatarray로 바꾸네

                // getting tensor content as java array of floats
                final float[] scores = outputTensor.getDataAsFloatArray();
                /*
                final long[] output_shape = outputTensor.shape();   // 1, 3, 32, 32

                ArrayList<Float> arraylist = new ArrayList<Float>();
                for (int i = 0; i< scores.length ; i++) arraylist.add(scores[i]);


                int length = scores.length;
                Bitmap outBitmap = arrayFloatToBitmap(arraylist, (int) output_shape[3], (int) output_shape[2]);

                imgView_stillshot_processed.setImageBitmap(outBitmap);
                imgView_stillshot_processed.setVisibility(View.VISIBLE);
                imgView_stillshot_org.setVisibility(View.INVISIBLE);
                */

                // mobilenet-v2 classification일때
                // searching for the index with maximum score
                float maxScore = -Float.MAX_VALUE;
                int maxScoreIdx = -1;
                for (int i = 0; i < scores.length; i++){
                    if(scores[i] > maxScore){
                        maxScore = scores[i];
                        maxScoreIdx = i;
                    }
                }

                String className = ImageNetClasses.IMAGENET_CLASSES[maxScoreIdx];

                //showing className on UI
                TextView textView = findViewById(R.id.text_InferResult);
                textView.setText("Classification result : " + className);

            }
        });


        // myFunction 실행 (java 공부하면서 output 여기로 뿌릴 수 있음)
        final Button button_run = findViewById(R.id.btn_Run);
        button_run.setOnClickListener(new View.OnClickListener(){
            public void onClick(View v) {
                try {
                    myFunction();
                } catch (IOException e) {
                    String strResult = "[ERROR] Failed to myFunction";
                    ShowResult(strResult);
                    e.printStackTrace();
                }
            }
        });


        // 초기화면 넘어가기
        final Button btn_move = (Button) findViewById(R.id.btn_Choose);
        btn_move.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(SubActivity_Test.this, MainActivity.class);
                startActivity(intent);  // activity 이동.
            }
        });
    }





    public static String assetFilePath(Context context, String assetName) throws IOException {
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






    /////// 여기가 myFunction 내용 ////////
    public void myFunction() throws IOException {
        String strResult = null;
        String strPathFile = "sample.txt";
        // ------ <body ------








        // txt 로딩해서 내용 띄우기
        strResult = Func_LoadingTxt(strPathFile);
        // ------ /body> ------
        ShowResult(strResult);
    }
    ///////////////////////////////////////

    public String Func_LoadingTxt(String strPathFile) throws IOException {
        String line = null;
        String strFileContent = null;
        InputStream is = null;
        byte buf[] = new byte[1024];

        //파일읽기 참고: https://recipes4dev.tistory.com/125
        AssetManager am = getResources().getAssets();

        try {
            is = am.open(strPathFile);

            if (is.read(buf) > 0){
                strFileContent = new String(buf);
            }
        } catch (Exception e) {
            e.printStackTrace();
            strFileContent = "[ERROR] Failed to load " + strPathFile;
        }

        return strFileContent;
    }


    // run textbox에 결과 string 출력
    // 이제 java 배울 환경 만들었음
    void ShowResult(String strResult){
        TextView textView = findViewById(R.id.text_RunResult);
        textView.setText(strResult);
    }


    //
    private Bitmap arrayFloatToBitmap(List<Float> floatArray, int width, int height){

        byte alpha = (byte) 255 ;

        Bitmap bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) ;

        ByteBuffer byteBuffer = ByteBuffer.allocate(width*height*4*3) ;

        float Maximum = Collections.max(floatArray);
        float minmum = Collections.min(floatArray);
        float delta = Maximum - minmum ;

        int i = 0 ;
        for (float value : floatArray){
            byte temValue = (byte) ((byte) ((((value-minmum)/delta)*255)));
            byteBuffer.put(4*i, temValue) ;
            byteBuffer.put(4*i+1, temValue) ;
            byteBuffer.put(4*i+2, temValue) ;
            byteBuffer.put(4*i+3, alpha) ;
            i++ ;
        }
        bmp.copyPixelsFromBuffer(byteBuffer) ;
        return bmp ;
    }
}