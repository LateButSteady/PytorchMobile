package com.example.jwkim.kr.pytorchmobile;

// 카메라 촬영 참고
// https://ebbnflow.tistory.com/177

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.nnapi.NnApiDelegate;
import org.tensorflow.lite.support.common.ops.DequantizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SubActivity_Camera extends AppCompatActivity implements View.OnClickListener {

    public SubActivity_Camera() {
        // empty constructor
    }

    Util_Common util_common = new Util_Common(SubActivity_Camera.this);
//    Util_CNN     util_cnn    = new Util_CNN(SubActivity_Camera.this);

    final int REQUEST_TAKE_PHOTO = 1;
    boolean good2Process = false;
    boolean bRescale = false;
    boolean useNnapi = true;
    Button btn_shoot = null;
    Button btn_process = null;
    Button btn_toggle = null;
    Button btn_rotate = null;
    String mCurrentPhotoPath = null;
    String mCurrentPhotoPath_RIA = null;
    ImageView imgView_stillshot_org = null;
    ImageView imgView_stillshot_processed = null;
    Bitmap inputBitmap = null;
    Bitmap inputBitmap_rotate = null;
    Bitmap inputBitmap_rescale = null;
    Bitmap outputBitmap = null;
    Bitmap outputBitmap_tmp = null;
    TextView textView_msg = null;
    Uri photoURI = null;
    private long time_start = 0;
    private long time_taken = 0;

    private float exifDegree; // output Bitmap rotation 각
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

    int h_FHD = 0;
    int w_FHD = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_camera);

        imgView_stillshot_org = (ImageView) findViewById(R.id.imgView_stillshot_org);
        imgView_stillshot_processed = (ImageView) findViewById(R.id.imgView_stillshot_processed);

        btn_shoot = (Button) findViewById(R.id.btn_shoot);
        btn_process = (Button) findViewById(R.id.btn_process);
        btn_toggle = (Button) findViewById(R.id.btn_toggle);
        btn_rotate = (Button) findViewById(R.id.btn_rotate);
        textView_msg = (TextView) findViewById(R.id.textView_msg);

        // 권한 설정
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                Log.d(getString(R.string.tag), "권한 설정 완료");
            } else {
                Log.d(getString(R.string.tag), "권한 설정 요청");
                ActivityCompat.requestPermissions(SubActivity_Camera.this, new String[]{
                        Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
            }
        }

        // Button listen set
        btn_shoot.setOnClickListener(this);
        btn_process.setOnClickListener(this);
        btn_toggle.setOnClickListener(this);
        btn_rotate.setOnClickListener(this);
    }

    // 여러개 버튼 처리
    @Override
    public void onClick(@NonNull View v) {
        switch (v.getId()) {
            case R.id.btn_shoot:
                Log.i(getString(R.string.tag), "SHOOT button pressed");

                // 먼저 초기화
                imgView_stillshot_org.setImageBitmap(null);
                imgView_stillshot_processed.setImageBitmap(null);
                imgView_stillshot_org.setVisibility(View.VISIBLE);
                imgView_stillshot_processed.setVisibility(View.INVISIBLE);
                exifDegree = 0;
                mCurrentPhotoPath = null;
                mCurrentPhotoPath_RIA = null;

                // 카메라 앱 실행
                dispatchTakePictureIntent();

                textView_msg.setText(getString(R.string.press_PROCESS));
                break;

            case R.id.btn_process:
                Log.i(getString(R.string.tag), "PROCESS button pressed");

                // PROCESS 진행해도 되는지 체크
                good2Process = checkBeforeProcess();
                if (!good2Process) {
                    Log.e(getString(R.string.tag), "Fail: Not ready to do PROCESS");
                    break;
                }


                // 먼저 imageView 비었으면 에러
                if (null == imgView_stillshot_org.getDrawable()) {
                    Log.e(getString(R.string.tag), "imgView is Empty");
                    textView_msg.setText(getString(R.string.press_SHOOT));
                    Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
                    break;
                }

                // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Log.e(getString(R.string.tag), "File size = 0kB");
                    textView_msg.setText(getString(R.string.press_SHOOT));
                    Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
                    break;
                }


                // input bitmap 불러오기
                inputBitmap = util_common.loadFileToBitmap(mCurrentPhotoPath);

                // 바로 세우도록 회전
                inputBitmap_rotate = util_common.rotate(inputBitmap, exifDegree);

                // 원래 image size
                final int h_org = inputBitmap_rotate.getHeight();
                final int w_org = inputBitmap_rotate.getWidth();

                // FHD 아니라면 rescale
                // 세로 이미지
                if (h_org > w_org) {
                    h_FHD = 32;
                    w_FHD = 32;
                }
                // 가로 이미지
                else {
                    h_FHD = 32;
                    w_FHD = 32;
                }

                if (!(h_FHD == h_org && w_FHD == w_org)) {
                    bRescale = true;
                    inputBitmap_rescale = Bitmap.createScaledBitmap(inputBitmap_rotate, w_FHD, h_FHD, false);
                } else {
                    inputBitmap_rescale = inputBitmap_rotate;
                }

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
                        .add(new DequantizeOp(0, 1.0f / 255.f))
                        .build();
                TensorImage dequantized = imageProcessor.process(tfImage);

                // input 영상 TensorImage -> bytebuffer
                inputBuffer = dequantized.getBuffer();

                // time check
                time_taken = System.currentTimeMillis() - time_start;
                Log.i(getString(R.string.tag), "Time taken: bitmap -> byteBuffer = " + time_taken + " ms");

                // runModel 시작
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
                    if (null != nnApiDelegate) {
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

                // output을 input 파일처럼 회전
                outputBitmap = util_common.rotate(outputBitmap_tmp, -exifDegree);

                // time check
                time_taken = System.currentTimeMillis() - time_start - time_taken;
                Log.i(getString(R.string.tag), "Time taken: byte buffer -> bitmap = " + time_taken + " ms");

                // 처리된 영상 save
                try {
                    util_common.saveBitmapToFile(outputBitmap, mCurrentPhotoPath_RIA);
                } catch (IOException e) {
                    util_common.fn_IOexception(e, "Error writing RIA image file", getString(R.string.tag));
                }

                // 결과 띄우기 (rescale 되었을 수도 있으니 다시 띄우기)
                //imgView_stillshot_processed.setImageBitmap(util_common.rotate(outputBitmap, exifDegree));
                //imgView_stillshot_org.setImageBitmap(util_common.rotate(inputBitmap,exifDegree));
                imgView_stillshot_processed.setImageBitmap(outputBitmap);
                imgView_stillshot_org.setImageBitmap(inputBitmap);

                // 처리된 영상 출력
                imgView_stillshot_processed.setVisibility(View.VISIBLE);
                imgView_stillshot_org.setVisibility(View.INVISIBLE);

                textView_msg.setText(getString(R.string.press_TOGGLE));

                break;

            case R.id.btn_toggle:
                Log.i(getString(R.string.tag), "TOGGLE button pressed");
                util_common.toggle_imageViews(imgView_stillshot_org, imgView_stillshot_processed);

                break;

            case R.id.btn_rotate:
                Log.i(getString(R.string.tag), "Rotate button pressed");

                if (util_common.checkEmptyImageView(imgView_stillshot_org)
                        || null == mCurrentPhotoPath) {
                    Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_LONG).show();
                    break;
                }

                // 파일 사이즈 체크
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Toast.makeText(SubActivity_Camera.this, "File size = 0", Toast.LENGTH_LONG).show();
                    break;
                }

                rotateDegree = (float) ((rotateDegree - 90.0) % 360);
                Log.i(getString(R.string.tag), "exifDegree = " + exifDegree);

                util_common.rotateImageView(imgView_stillshot_org, mCurrentPhotoPath, rotateDegree);
                util_common.rotateImageView(imgView_stillshot_processed, mCurrentPhotoPath_RIA, rotateDegree);

                break;

            default:
                break;

        }
    }


    // 권한 요청
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        Log.d(getString(R.string.tag), "onRequestPermissionsResult");
        if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            Log.d(getString(R.string.tag), "Permission: " + permissions[0] + "was " + grantResults[0]);
        }
    }

    // 카메라로 촬영한 영상을 가져오는 부분
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        try {
            switch (requestCode) {
                case REQUEST_TAKE_PHOTO:
                    if (resultCode == RESULT_OK) {
                        File file = new File(mCurrentPhotoPath);

                        // 폰을 세로로 똑바로 세웠을때 exifDegree = 90
                        exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);

                        Bitmap bitmap = null;
                        // android API29에서는 getBitmap deprecated되었음. -> 버전별로 나눠서 처리
                        if (Build.VERSION.SDK_INT >= 29) {
                            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), Uri.fromFile(file));
                            try {
                                bitmap = ImageDecoder.decodeBitmap(source); // API29 이상 버전에서 getBitmap 대체하는 줄
                            } catch (IOException e) {
                                util_common.fn_IOexception(e, "Error reading file from URI", getString(R.string.tag));
                            }
                        } else {
                            try {
                                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.fromFile(file));
                            } catch (IOException e) {
                                util_common.fn_IOexception(e, "Error reading file from URI", getString(R.string.tag));
                            }
                        }
                        if (null == bitmap) {
                            util_common.fn_error("bitmap is null", getString(R.string.tag));
                        }
                        // 나중에 손 볼 부분
                        //Log.i(getString(R.string.tag), "exifDegree in Camera SubActivity = " + exifDegree);
                        //displayBitmap(imgView_stillshot_org, util_common.rotate(bitmap, exifDegree));
                        displayBitmap(imgView_stillshot_org, bitmap);

                    }
                    break;

                default:
                    break;
            }
        } catch (Exception e) {
            util_common.fn_Exception(e, "Error during image analysis", getString(R.string.tag));
        }
    }


    // 기본 카메라 인텐트 실행하는 부분
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (null != takePictureIntent.resolveActivity(getPackageManager())) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException e) {
                util_common.fn_IOexception(e, "Failed on createImageFile()", getString(R.string.tag));
            }
            if (photoFile != null) {
                // 중요
                // 참고: https://stackoverflow.com/questions/56598480/couldnt-find-meta-data-for-provider-with-authority
                // 1. getUriForFile의 input 중 authority란에 "앱ID.FileProvider" 부분이 AndroidManifest.xml의 authorities와 동일해야 한다.
                // 2. getUriForFile의 input 중 filepaths.xml에서 정의한 path와 경로가 맞아야 한다.
                photoURI = FileProvider.getUriForFile(SubActivity_Camera.this,
                        getApplicationContext().getPackageName() + ".FileProvider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);
            }
        }
    }

    // 사진 촬영 후 썸네일만 띄워줌. 이미지를 파일로 저장해야 함
    @NonNull
    private File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "PNG_" + timeStamp + "_before.png";
        String imageFileName_RIA = "PNG_" + timeStamp + "_after.png";

        // getExternal... 참고 https://androidhuman.tistory.com/432
        // 찐참고: https://sondroid.tistory.com/entry/Android-%EB%82%B4%EB%B6%80-%EC%A0%80%EC%9E%A5%EC%86%8C-%EA%B2%BD%EB%A1%9C
        // FileProvider()를 사용하기 위해 filepath.xml에 나와있는 경로와 맞춰야 한다.
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES); // storage/emulated/0
        //File storageDir = getExternalStorageDirectory(Environment.DIRECTORY_DCIM);    // deprecated
        //File image = File.createTempFile(imageFileName, ".png", storageDir);  // createTempFile은 랜덤 숫자를 파일명에 붙여서 관리 힘듬
        File image = new File(storageDir, imageFileName);
        if (!image.exists()) image.createNewFile();

        mCurrentPhotoPath = image.getAbsolutePath();
        mCurrentPhotoPath_RIA = storageDir + "/" + imageFileName_RIA;
        return image;
    }


    // bitmap이 잘 넘어왔다면 영상 띄우고 msg 변경
    protected void displayBitmap(ImageView imageView, Bitmap bitmap) {
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
            // 촬영 후 영상을 띄웠다면 메시지 변경
            textView_msg.setText(getString(R.string.press_PROCESS));
        } else {
            // 지금 안통함 -> 일단 보류
            textView_msg.setText(getString(R.string.press_SHOOT));
        }
    }


    // ImageView 비었는지 체크 (비었으면 Toast)
    public boolean checkEmptyImageView(@NonNull ImageView imageView) {
        // 먼저 imageView가 null인지 체크
        boolean bRtnValue = true;
        imageView.getDrawable();
        if (null == imageView.getDrawable()) {
            Toast.makeText(SubActivity_Camera.this, "SHOOT 버튼을 눌러 촬영하세요", Toast.LENGTH_SHORT).show();
            bRtnValue = true;
        } else {
            bRtnValue = false;
        }
        return bRtnValue;
    }


    /*
     * Process 진행 전 방어코드
     */
    protected boolean checkBeforeProcess() {
        boolean bRtnValue = false;
        // 먼저 imageView 비었으면 에러
        if (null == imgView_stillshot_org.getDrawable()) {
            Log.e(getString(R.string.tag), "imgView is Empty");
            bRtnValue = false;
        }

        // path null이면 에러
        else if (null == mCurrentPhotoPath) {
            Log.e(getString(R.string.tag), "File path = null");
            textView_msg.setText(getString(R.string.press_SHOOT));
            Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
            bRtnValue = false;
        }

        // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
        else if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
            Log.e(getString(R.string.tag), "File size = 0kB");
            textView_msg.setText(getString(R.string.press_SHOOT));
            Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
            bRtnValue = false;
        } else bRtnValue = true;

        textView_msg.setText(getString(R.string.press_SHOOT));
        Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
        return bRtnValue;
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
        } catch (Exception e) {
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


    // ByteBuffer -> Bitmap
    // 참고: https://github.com/tensorflow/tensorflow/issues/34992
    private @NonNull
    Bitmap getOutputImage(@NonNull ByteBuffer output, @NonNull int width, @NonNull int height) {
        output.rewind();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        int[] pixels = new int[width * height];
        for (int i = 0; i < width * height; i++) {
            int a = 0xFF;

            float rf = output.getFloat() * 255.f;
            float gf = output.getFloat() * 255.f;
            float bf = output.getFloat() * 255.f;

            int r = rf > 255.f ? 255 : rf < 0.f ? 0 : (int) rf;
            int g = gf > 255.f ? 255 : rf < 0.f ? 0 : (int) gf;
            int b = bf > 255.f ? 255 : rf < 0.f ? 0 : (int) bf;

            pixels[i] = a << 24 | ((int) r << 16) | ((int) g << 8) | (int) b;
        }
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }
}