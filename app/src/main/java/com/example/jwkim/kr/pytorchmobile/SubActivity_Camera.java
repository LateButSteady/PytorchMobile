package com.example.jwkim.kr.pytorchmobile;

// 카메라 촬영 참고
// https://ebbnflow.tistory.com/177

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.FileProvider;

import org.pytorch.Module;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SubActivity_Camera extends AppCompatActivity implements View.OnClickListener {

    public SubActivity_Camera() {
        // empty constructor
    }

    Util_Common  util_common = new Util_Common(SubActivity_Camera.this);
    Util_CNN     util_cnn    = new Util_CNN(SubActivity_Camera.this);

    final int REQUEST_TAKE_PHOTO = 1;
    boolean good2Process = false;

    ImageView imgView_stillshot_org = null;
    ImageView imgView_stillshot_processed = null;
    TextView textView_msg = null;
    String mCurrentPhotoPath = null;
    String mCurrentPhotoPath_RIA = null;
    Button btn_shoot = null;
    Button btn_process = null;
    Button btn_toggle = null;
    Button btn_rotate = null;
    Uri photoURI = null;

    private float exifDegree = 0; // output Bitmap rotation 각
    private Module module = null;

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

        // Process, Toggle 버튼 deactivate
        btn_process.setActivated(false);
        btn_toggle.setActivated(false);
    }

    // 여러개 버튼 처리
    @Override
    public void onClick(@NonNull View v) {
        switch (v.getId()) {
            case R.id.btn_shoot:
                Log.i(getString(R.string.tag), "SHOOT button pressed");

                // 먼저 초기화
                Bitmap inputBitmap = null;
                Bitmap outputBitmap = null;
                imgView_stillshot_org.setImageBitmap(inputBitmap);
                imgView_stillshot_processed.setImageBitmap(outputBitmap);
                imgView_stillshot_org.setVisibility(View.VISIBLE);
                imgView_stillshot_processed.setVisibility(View.INVISIBLE);
                exifDegree = 0;
                mCurrentPhotoPath = null;
                mCurrentPhotoPath_RIA = null;

                // 카메라 앱 실행
                dispatchTakePictureIntent();

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

                /*
                // Button inactivate + Progress bar 보이기
                btn_process.setActivated(false);
                pbar.setVisibility(View.VISIBLE);
                */

                // asset에서 CNN model 불러오기
                try {
                    module = util_cnn.loadModelCNN(getString(R.string.CNN_Model_FileName));
                    //module = util_cnn.loadModelCNN("RIA_model_file_TorchScript.pt");
                    //module = util_cnn.loadModelCNN("netG_RIA_TorchScript.pt");
                } catch (IOException e) {
                    util_common.fn_IOexception(e, "Error reading assets", getString(R.string.tag));
                }

                // input bitmap 불러오기
                inputBitmap = util_common.loadFileToBitmap(mCurrentPhotoPath);

                // FHD 아니라면 rescale
                final int h = inputBitmap.getHeight();
                final int w = inputBitmap.getWidth();
                if (!((1080 == h && 1920 == w) || (1920 == h && 1080 == w))) {
                    // 세로 이미지
                    if (h > w) {
                        inputBitmap = Bitmap.createScaledBitmap(inputBitmap, 1080, 1920, false);
                    }
                    // 가로 이미지
                    else {
                        inputBitmap = Bitmap.createScaledBitmap(inputBitmap, 1920, 1080, false);
                    }
                }
                // height, width 중 0이 하나라도 있다면 error
                if (0 == h || 0 == w) {
                    util_common.fn_error("Input bitmap h=0 or w=0", getString(R.string.tag));
                }

                Log.i(getString(R.string.tag), "Start runModel");
                outputBitmap = util_cnn.runModel(module, inputBitmap);

                // rotate시켜서 output Bitmap 똑바로 세우기
                exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);

                // 처리된 영상 save
                try {
                    util_common.saveBitmapToFile(outputBitmap, mCurrentPhotoPath_RIA);
                }
                catch(IOException e) {
                    util_common.fn_IOexception(e, "Error writing RIA image file", getString(R.string.tag));
                }

                // 결과 띄우기 (rescale 되었을 수도 있으니 다시 띄우기)
                imgView_stillshot_processed.setImageBitmap(util_common.rotate(outputBitmap, exifDegree));
                imgView_stillshot_org.setImageBitmap(util_common.rotate(inputBitmap,exifDegree));

                // 처리된 영상 출력
                imgView_stillshot_processed.setVisibility(View.VISIBLE);
                imgView_stillshot_org.setVisibility(View.INVISIBLE);

                textView_msg.setText(getString(R.string.press_TOGGLE));

                break;

            case R.id.btn_toggle:
                Log.i(getString(R.string.tag), "TOGGLE button pressed");
                util_common.toggle_imageViews(imgView_stillshot_org,  imgView_stillshot_processed);

                break;

            case R.id.btn_rotate:
                Log.i(getString(R.string.tag), "Rotate button pressed");
                // 방어코드 조건
                //   - ImageView 화면이 empty
                //   - path = null
                //   - path의 파일 사이즈 = 0 (이건 다른 if문으로 체크)
                if (util_common.checkEmptyImageView(imgView_stillshot_org)
                        || null == mCurrentPhotoPath) {
                    Toast.makeText(SubActivity_Camera.this,  getString(R.string.press_SHOOT), Toast.LENGTH_LONG).show();
                    break;
                }
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Toast.makeText(SubActivity_Camera.this,  "File size = 0", Toast.LENGTH_LONG).show();
                    break;
                }


                exifDegree = (float) ((exifDegree - 90.0) % 360);
                Log.i(getString(R.string.tag), "exifDegree = " + exifDegree);

                util_common.rotateImageView(imgView_stillshot_org,  mCurrentPhotoPath, exifDegree);
                util_common.rotateImageView(imgView_stillshot_processed,  mCurrentPhotoPath_RIA, exifDegree);

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
                        exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);

                        Bitmap bitmap = null;
                        // android API29에서는 getBitmap deprecated되었음. -> 버전별로 나눠서 처리
                        if (Build.VERSION.SDK_INT >= 29) {
                            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), Uri.fromFile(file));
                            try {
                                bitmap = ImageDecoder.decodeBitmap(source); // API29 이상 버전에서 getBitmap 대체하는 줄
                            } catch (IOException e){
                                util_common.fn_IOexception(e, "Error reading file from URI", getString(R.string.tag));
                            }
                        }
                        else {
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
                        Log.i(getString(R.string.tag), "exifDegree in Camera SubActivity = " + exifDegree);
                        displayBitmap(imgView_stillshot_org, util_common.rotate(bitmap, exifDegree));

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
        File image = new File(storageDir, imageFileName + ".png");
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
        }

        else bRtnValue = true;

        textView_msg.setText(getString(R.string.press_SHOOT));
        Toast.makeText(SubActivity_Camera.this, getString(R.string.press_SHOOT), Toast.LENGTH_SHORT).show();
        return bRtnValue;
    }




}