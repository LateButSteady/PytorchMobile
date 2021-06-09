package com.example.jwkim.kr.pytorchmobile;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.example.jwkim.kr.pytorchmobile.R;
import com.example.jwkim.kr.pytorchmobile.Util_CNN;
import com.example.jwkim.kr.pytorchmobile.Util_Common;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.ops.DequantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public class SubActivity_Gallery extends AppCompatActivity  implements View.OnClickListener {

    public SubActivity_Gallery() {
        // empty constructor
    }

    Util_Common util_common = new Util_Common(SubActivity_Gallery.this);
//    Util_CNN util_cnn    = new Util_CNN(SubActivity_Gallery.this);

    final static int PICK_IMAGE_REQUEST = 2000;
    boolean good2Process = false;
    boolean bRescale = false;
    boolean useNnapi = false;

    Button btn_choose = null;
    Button btn_process2 = null;
    Button btn_toggle2 = null;
    Button btn_rotate2 = null;
    String mCurrentPhotoPath = null;
    String mCurrentPhotoPath_RIA = null;
    ImageView imgView_stillshot_org2 = null;
    ImageView imgView_stillshot_processed2 = null;
    Bitmap inputBitmap = null;
    Bitmap inputBitmap_rotate = null;
    Bitmap inputBitmap_rescale = null;
    Bitmap outputBitmap = null;
    Bitmap outputBitmap_tmp = null;
    TextView textView_msg2 = null;
    Uri photoURI = null;
    private long time_start = 0;
    private long time_taken = 0;

    private float exifDegree;
    private float rotateDegree = 0;
    //private Module module = null;

    static final int DIM_IMG_RGB = 3;
    ByteBuffer inputBuffer = null;
    ByteBuffer outputBuffer = null;
    //ThreadTest thread;

    // nnapi delegate 미리 초기화
    Interpreter tflite = null;
    Interpreter.Options options = new Interpreter.Options();
    private NnApiDelegate nnApiDelegate = null;
    // private HexagonDelegate hexagonDelegate;

    final int h_FHD = 32;
    final int w_FHD = 32;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_gallery);

        imgView_stillshot_org2 = findViewById(R.id.imgView_stillshot_org2);
        imgView_stillshot_processed2 = findViewById(R.id.imgView_stillshot_processed2);

        btn_choose = findViewById(R.id.btn_choose);
        btn_process2 = findViewById(R.id.btn_process2);
        btn_toggle2 = findViewById(R.id.btn_toggle2);
        btn_rotate2 = findViewById(R.id.btn_rotate2);
        textView_msg2 = findViewById(R.id.textView_msg2);

        // 권한 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.d(getString(R.string.tag), "권한 설정 완료");
            } else {
                Log.d(getString(R.string.tag), "권한 설정 요청");
                ActivityCompat.requestPermissions(SubActivity_Gallery.this, new String[]{
                        Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        // Button listen set
        btn_choose.setOnClickListener(this);
        btn_process2.setOnClickListener(this);
        btn_toggle2.setOnClickListener(this);
        btn_rotate2.setOnClickListener(this);
    }


    // 여러개 버튼 처리
    @SuppressLint("NonConstantResourceId")
    @Override
    public void onClick(@NonNull View v) {
        switch (v.getId()) {
            case R.id.btn_choose:
                Log.i(getString(R.string.tag), "CHOOSE button pressed");

                // 먼저 초기화
                imgView_stillshot_org2.setImageBitmap(null);
                imgView_stillshot_processed2.setImageBitmap(null);
                imgView_stillshot_org2.setVisibility(View.VISIBLE);
                imgView_stillshot_processed2.setVisibility(View.INVISIBLE);
                exifDegree = 0;
                mCurrentPhotoPath = null;
                mCurrentPhotoPath_RIA = null;

                // 갤러리 앱 실행
                loadImagefromGallery(v);

                textView_msg2.setText(getString(R.string.press_PROCESS));
                break;

            case R.id.btn_process2:
                Log.i(getString(R.string.tag), "PROCESS button pressed");

                // PROCESS 진행해도 되는지 체크
                good2Process = checkBeforeProcess();
                if (!good2Process) {
                    Log.e(getString(R.string.tag), "Fail: Not ready to do PROCESS");
                    break;
                }

                // PROCESS 버튼 비활성화
                //thread = new ThreadTest(pbar);
                //btn_process2.setText("RUNNING...");
                //btn_process2.setEnabled(false);

                // 먼저 imageView 비었으면 에러
                if (null == imgView_stillshot_org2.getDrawable()) {
                    Log.e(getString(R.string.tag), "imgView is Empty");
                    textView_msg2.setText(getString(R.string.press_SHOOT));
                    Toast.makeText(SubActivity_Gallery.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
                    break;
                }

                // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Log.e(getString(R.string.tag), "File size = 0kB");
                    textView_msg2.setText(getString(R.string.press_SHOOT));
                    Toast.makeText(SubActivity_Gallery.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
                    break;
                }

                // Extension 체크
                if (null != mCurrentPhotoPath) {
                    Log.i(getString(R.string.tag), "File extension : " + util_common.getExtension(mCurrentPhotoPath));
                }

                // input bitmap 불러오기
                //exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);
                inputBitmap = util_common.loadFileToBitmap(mCurrentPhotoPath);

                // 바로 세우도록 회전
                inputBitmap_rotate = util_common.rotate(inputBitmap, exifDegree);

                // 원래 image size
                final int h_org = inputBitmap_rotate.getHeight();
                final int w_org = inputBitmap_rotate.getWidth();

                // FHD 아니라면 rescale
                if (!((h_FHD == h_org && w_FHD == w_org) || (w_FHD == h_org && h_FHD == w_org))) {
                    bRescale = true;

                    // 세로 이미지
                    if (h_org > w_org) {
                        inputBitmap_rescale = Bitmap.createScaledBitmap(inputBitmap_rotate, w_FHD, h_FHD, false);
                    }
                    // 가로 이미지
                    else {
                        inputBitmap_rescale = Bitmap.createScaledBitmap(inputBitmap_rotate, w_FHD, h_FHD, false);
                    }
                } else inputBitmap_rescale = inputBitmap_rotate;

                // height, width 중 0이 하나라도 있다면 error
                if (0 == h_org || 0 == w_org) {
                    util_common.fn_error("Input bitmap h=0 or w=0", getString(R.string.tag));
                }

                // outputBuffer 준비
                outputBuffer = ByteBuffer.allocateDirect(4 * w_FHD * h_FHD * DIM_IMG_RGB);
                outputBuffer.order(ByteOrder.nativeOrder());
                outputBuffer.rewind();

                // 시간 측정 시작
                time_start = System.currentTimeMillis();

                //TensorImage tfImage = new TensorImage(DataType.UINT8);
                TensorImage tfImage = new TensorImage(DataType.FLOAT32);

                // tfImage.load가 ARGB_8888만 받아들임
                inputBitmap_rescale = inputBitmap_rescale.copy(Bitmap.Config.ARGB_8888, true);
                tfImage.fromBitmap(inputBitmap_rescale);
                tfImage.load(inputBitmap_rescale);

                // input 영상을 [0, 1] 범위로 normalize
                ImageProcessor imageProcessor = new ImageProcessor.Builder()
                        .add(new DequantizeOp(0, 1.0f/255.f))
                        .build();
                TensorImage dequantized = imageProcessor.process(tfImage);

                // input 영상 TensorImage -> bytebuffer
                inputBuffer = dequantized.getBuffer();

                // time check
                time_taken = System.currentTimeMillis() - time_start;
                Log.i(getString(R.string.tag), "Time taken: bitmap -> byteBuffer = " + time_taken + " ms");

                // runModel 시작 (rotate + rescale 이미지)
                Log.i(getString(R.string.tag), "Start runModel");
                tflite = getTfliteInterpreter("my_cnn_epoch10.tflite", useNnapi);

                if (null == nnApiDelegate) {
                    Log.i(getString(R.string.tag), "NNAPI delegate is null...");
                } else {
                    Log.i(getString(R.string.tag), "NNAPI delegate prepared...");
                }

                tflite.run(inputBuffer, outputBuffer);

                // time check
                time_taken = System.currentTimeMillis() - time_start - time_taken;
                Log.i(getString(R.string.tag), "Time taken: run model = " + time_taken + " ms");


                // Unload delegate
                // 이걸 어디에 위치시켜야 할 지 고민 필요함
                // 현재는 back 버튼 눌러서 mainActivity에 간 후, 다시 gallery로 돌아와야만 NNAPI를 초기화 하게끔 되어있음
                // process 버튼은 일회용임
                if (useNnapi) {
                    tflite.close();
                    if(null != nnApiDelegate) {
                        nnApiDelegate.close();
                        Log.i(getString(R.string.tag), "nnApi is closed");
                    }
                }

                // output ByteBuffer -> bitmap
                outputBitmap_tmp = getOutputImage(outputBuffer, w_FHD, h_FHD);

                // rescale 된거라면 되돌리기
                if (bRescale) {
                    outputBitmap_tmp = Bitmap.createScaledBitmap(outputBitmap_tmp, w_org, h_org, false);
                }

                // output을 input 파일처럼 돌리고 rescale
                outputBitmap = util_common.rotate(outputBitmap_tmp, -exifDegree);

                // time check
                time_taken = System.currentTimeMillis() - time_start - time_taken;
                Log.i(getString(R.string.tag), "Time taken: byte buffer -> bitmap = " + time_taken + " ms");

                // 처리된 영상 save
                try {
                    util_common.saveBitmapToFile(outputBitmap, mCurrentPhotoPath_RIA);
                }
                catch(IOException e) {
                    util_common.fn_IOexception(e, "Error writing RIA image file", getString(R.string.tag));
                }

                // 결과 띄우기 (rescale 되었을 수도 있으니 다시 띄우기)
                imgView_stillshot_processed2.setImageBitmap(util_common.rotate(outputBitmap, exifDegree));
                imgView_stillshot_org2.setImageBitmap(util_common.rotate(inputBitmap, exifDegree));

                // 결과 영상 띄우기
                imgView_stillshot_processed2.setVisibility(View.VISIBLE);
                imgView_stillshot_org2.setVisibility(View.INVISIBLE);

                textView_msg2.setText(getString(R.string.press_TOGGLE));

                /*
                // PROCESS 버튼 재활성화
                btn_process2.setText("PROCESS");
                btn_process2.setEnabled(true);
                */

                break;

            case R.id.btn_toggle2:
                Log.i(getString(R.string.tag), "TOGGLE button pressed");
                util_common.toggle_imageViews(imgView_stillshot_org2, imgView_stillshot_processed2);

                break;

            case R.id.btn_rotate2:
                Log.i(getString(R.string.tag), "Rotate button pressed");

                if (util_common.checkEmptyImageView(imgView_stillshot_org2)
                        || null == mCurrentPhotoPath) {
                    Toast.makeText(SubActivity_Gallery.this, getString(R.string.press_CHOOSE), Toast.LENGTH_LONG).show();
                    break;
                }
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Toast.makeText(SubActivity_Gallery.this, "File size = 0", Toast.LENGTH_LONG).show();
                    break;
                }


                rotateDegree = (float) ((rotateDegree - 90.0) % 360);
                Log.i(getString(R.string.tag), "exifDegree = " + exifDegree);

                util_common.rotateImageView(imgView_stillshot_org2, mCurrentPhotoPath, rotateDegree);
                util_common.rotateImageView(imgView_stillshot_processed2, mCurrentPhotoPath_RIA, rotateDegree);

                break;

            default:
                break;
        }
    }


    // bitmap -> float 값을 가진 ByteBuffer
    // 드디어 output이 float형태로 12288 맞춰진다.
    // https://wikidocs.net/101767
    private @NonNull ByteBuffer bitmap2bytebuffer(@NonNull Bitmap bitmap, @NonNull int width, @NonNull int height, @NonNull int color) {
        ByteBuffer byteBuffer = ByteBuffer.allocateDirect(width * height * color * 4);
        byteBuffer.order(ByteOrder.nativeOrder()); //A float has 4 bytes
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {

                Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                int pixel = mutableBitmap.getPixel(x, y);

                // Get channel values from the pixel value.
                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // Normalize channel values to [-1.0, 1.0]. This requirement depends
                // on the model. For example, some models might require values to be
                // normalized to the range [0.0, 1.0] instead.
                float rf = r;
                float gf = g;
                float bf = b;

                byteBuffer.putFloat(rf);
                byteBuffer.putFloat(gf);
                byteBuffer.putFloat(bf);
            }
        }

        return byteBuffer;
    }

    // ByteBuffer -> Bitmap
    // 참고: https://github.com/tensorflow/tensorflow/issues/34992
    private @NonNull Bitmap getOutputImage(@NonNull ByteBuffer output, @NonNull int width, @NonNull int height){
        output.rewind();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int [] pixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int a = 0xFF;

            float rf = output.getFloat() * 255.f;
            float gf = output.getFloat() * 255.f;
            float bf = output.getFloat() * 255.f;

            int r = rf > 255.f ? 255 : rf < 0.f ? 0: (int)rf;
            int g = gf > 255.f ? 255 : rf < 0.f ? 0: (int)gf;
            int b = bf > 255.f ? 255 : rf < 0.f ? 0: (int)bf;

            pixels[i] = a << 24 | ((int) r << 16) | ((int) g << 8) | (int) b;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }



    // Bitmap Byte size 구하기
    protected int byteSizeOf(Bitmap data) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
            return data.getRowBytes() * data.getHeight();
        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return data.getByteCount();
        } else {
            return data.getAllocationByteCount();
        }
    }


    /*
     * 갤러리에서 영상 선택하기
     * 참고: https://blog.naver.com/cosmosjs/220940841567
     */
    public void loadImagefromGallery(View view) {
        // Intent 생성
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        // 목록에 이미지만 나오도록
        intent.setType("image/*");

        //Intent 시작 - 갤러리앱을 열어서 원하는 이미지를 선택할 수 있다.
        startActivityForResult(intent, PICK_IMAGE_REQUEST);
    }

    //이미지 선택작업을 후의 결과 처리
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        try {
            //이미지를 하나 골랐을때
            if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && null != data) {

                //data에서 절대경로로 이미지를 가져옴
                photoURI = data.getData();
                mCurrentPhotoPath = util_common.getPathFromURI(photoURI);
                mCurrentPhotoPath_RIA = util_common.addFileName(mCurrentPhotoPath, "_RIA");

                util_common.dispImgFile(imgView_stillshot_org2, mCurrentPhotoPath);
            }
        } catch (Exception e) {
            Toast.makeText(this, "로딩에 오류가 있습니다.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }


    /*
     * Process 진행 전 방어코드
     */
    protected boolean checkBeforeProcess() {
        boolean bRtnValue = false;

        // 순서 중요
        // 먼저 imageView 비었으면 에러
        if (null == imgView_stillshot_org2.getDrawable()) {
            Log.e(getString(R.string.tag), "imgView is Empty");
            bRtnValue = false;
        }

        // path null이면 에러
        else if (null == mCurrentPhotoPath) {
            Log.e(getString(R.string.tag), "File path = null");
            bRtnValue = false;
        }

        // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
        else if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
            Log.e(getString(R.string.tag), "File size = 0kB");
            bRtnValue = false;
        }

        else bRtnValue = true;

        textView_msg2.setText(getString(R.string.press_CHOOSE));
        Toast.makeText(SubActivity_Gallery.this, getString(R.string.press_CHOOSE), Toast.LENGTH_SHORT).show();
        return bRtnValue;
    }


    /*
     * Thread 구현 pbar
     */
    public class ThreadTest extends Thread {
        public ThreadTest() {
            // 초기화
        }

        public void run(@NonNull ProgressBar pbar) {
            pbar.setEnabled(true);
        }

    }

    // Initialize TFLite interpreter
    // 모델 파일 인터프리터를 생성하는 공통 함수
    // loadModelFile 함수에 예외가 포함되어 있기 때문에 반드시 try, catch 블록이 필요하다.
    private Interpreter getTfliteInterpreter(String modelPath, boolean useNnApi) {
        // Initialize interpreter with NNAPI delegate for Android Pie or above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            useNnApi = true;
        }

        try {
//            System.loadLibrary("tensorflowlite_hexagon_jni");
//            Interpreter.Options options = new Interpreter.Options();
            nnApiDelegate = new NnApiDelegate();
            if (useNnApi)
                options.addDelegate(nnApiDelegate);
//            hexagonDelegate = new HexagonDelegate(this);
//            options.addDelegate((hexagonDelegate));
            Interpreter interpreter = new Interpreter(loadModelFile(this, modelPath), options);
            return interpreter;
//            return new Interpreter(loadModelFile(this, modelPath), options);
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        // 여기까지 오지 않음
        return null;
    }


    // 모델을 읽어오는 함수로, 텐서플로 라이트 홈페이지에 있다.
    // MappedByteBuffer 바이트 버퍼를 Interpreter 객체에 전달하면 모델 해석을 할 수 있다.
    private MappedByteBuffer loadModelFile(Activity activity, String modelPath) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(modelPath);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
}