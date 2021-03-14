package com.example.jwkim.kr.pytorchmobile;

// 카메라 촬영 참고
// https://ebbnflow.tistory.com/177

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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

import org.pytorch.BuildConfig;
import org.pytorch.Module;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class SubActivity_Camera extends AppCompatActivity {

    final int REQUEST_TAKE_PHOTO = 1;
    ImageView imgView_stillshot_org = null;
    ImageView imgView_stillshot_processed = null;
    TextView textView_msg = null;
    String mCurrentPhotoPath = null;
    Button btn_shoot = null;
    Button btn_process = null;
    Button btn_toggle = null;

    private int exifDegree; // output Bitmap rotation 각
    private Bitmap inputBitmap = null;
    private Bitmap outputBitmap_tmp = null;
    private Bitmap outputBitmap = null;
    private Module module = null;

    private View.OnClickListener clickListener;
    com.example.jwkim.kr.pytorchmobile.Util_Common util_common = new com.example.jwkim.kr.pytorchmobile.Util_Common(SubActivity_Camera.this);
    com.example.jwkim.kr.pytorchmobile.Util_CNN util_cnn    = new com.example.jwkim.kr.pytorchmobile.Util_CNN(SubActivity_Camera.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_camera);

        imgView_stillshot_org = findViewById(R.id.imgView_stillshot_org);
        imgView_stillshot_processed = findViewById(R.id.imgView_stillshot_processed);

        btn_shoot = findViewById(R.id.btn_shoot);
        btn_process = findViewById(R.id.btn_process);
        btn_toggle = findViewById(R.id.btn_toggle);
        textView_msg = findViewById(R.id.textView_msg);

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


        // 여러개 버튼 처리
        clickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.btn_shoot:
                        imgView_stillshot_org.setImageBitmap(null);

                        imgView_stillshot_processed.setVisibility(View.INVISIBLE);
                        imgView_stillshot_org.setVisibility(View.VISIBLE);

                        dispatchTakePictureIntent();
                        // 현실 모습대로 방향 똑바로 맞춰서 save 함

                        break;

                    case R.id.btn_process:
                        // process 먼저 실행
                        //   - .pt 파일 로드
                        //   - 입력영상 불러오기
                        //   - 처리하기
                        //   - 결과영상 저장
                        //   - 입력영상, 결과영상 경로 intent bundle에 싣기

                        // 먼저 imageView 비었으면 에러
                        if (null == imgView_stillshot_org.getDrawable()) {
                            Log.e(getString(R.string.tag), "imgView is Empty");
                            textView_msg.setText(getString(R.string.Press_SHOOT));
                            Toast.makeText(SubActivity_Camera.this, getString(R.string.Press_SHOOT), Toast.LENGTH_SHORT).show();
                            break;
                        }
                        // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
                        if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                            Log.e(getString(R.string.tag), "File size = 0kB");
                            textView_msg.setText(getString(R.string.Press_SHOOT));
                            Toast.makeText(SubActivity_Camera.this, getString(R.string.Press_SHOOT), Toast.LENGTH_SHORT).show();
                            break;
                        }

                        // load model
                        module = util_cnn.loadModel("cnn_TorchScript.pt");

                        // input bitmap
                        inputBitmap = BitmapFactory.decodeFile(mCurrentPhotoPath);

                        // Model run
                        outputBitmap_tmp = util_cnn.runModel(module, inputBitmap);

                        // rotate시켜서 output Bitmap 똑바로 세우기
                        exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);
                        outputBitmap = util_common.rotate(outputBitmap_tmp, exifDegree);

                        // 결과 영상 저장




                        // 결과 영상 띄우기
                        imgView_stillshot_processed.setImageBitmap(outputBitmap);
                        imgView_stillshot_processed.setVisibility(View.VISIBLE);
                        imgView_stillshot_org.setVisibility(View.INVISIBLE);

                        textView_msg.setText(getString(R.string.Press_TOGGLE));

                        break;

                    case R.id.btn_toggle:
                        util_common.toggle_imageViews(imgView_stillshot_org, imgView_stillshot_processed);
                        break;
                }
            }
        };

        // 이걸 넣어줘야 listen 시작함
        btn_shoot.setOnClickListener(clickListener);
        btn_process.setOnClickListener(clickListener);
        btn_toggle.setOnClickListener(clickListener);
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
                case REQUEST_TAKE_PHOTO: {
                    if (resultCode == RESULT_OK) {
                        // 사진촬영 끝나면
                        // SHOOT 버튼 누르고 영상 띄우는건 rotate 안하고 띄우기
                        util_common.dispImgFile(imgView_stillshot_org, mCurrentPhotoPath);
                        textView_msg.setText(getString(R.string.Press_PROCESS));

                        // 홍드로이드 16:00
                        // https://www.youtube.com/watch?v=MAB8LEfRIG8&t=231s
                        // exif를 사용해서 image rotation까지 구현
                    }
                    break;
                }
            }
        } catch (Exception error) {
            error.printStackTrace();
        }
    }


    // 기본 카메라 인텐트 실행하는 부분
    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                Toast.makeText(SubActivity_Camera.this, "Image file not found", Toast.LENGTH_SHORT).show();
                Log.e(getString(R.string.tag), "Image file not found");
            }
            if (photoFile != null) {
                // 중요
                // 참고: https://stackoverflow.com/questions/56598480/couldnt-find-meta-data-for-provider-with-authority
                // 1. getUriForFile의 input 중 authority란에 "앱ID.FileProvider" 부분이 AndroidManifest.xml의 authorities와 동일해야 한다.
                // 2. getUriForFile의 input 중 filepaths.xml에서 정의한 path와 경로가 맞아야 한다.
                Uri photoURI = FileProvider.getUriForFile(SubActivity_Camera.this,
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
        String imageFileName = "PNG_" + timeStamp;
        // getExternal... 참고 https://androidhuman.tistory.com/432
        // 찐참고: https://sondroid.tistory.com/entry/Android-%EB%82%B4%EB%B6%80-%EC%A0%80%EC%9E%A5%EC%86%8C-%EA%B2%BD%EB%A1%9C
        // FileProvider()를 사용하기 위해 filepath.xml에 나와있는 경로와 맞춰야 한다.
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        //File storageDir = getExternalStorageDirectory(Environment.DIRECTORY_DCIM);    // deprecated
        //File image = File.createTempFile(imageFileName, ".png", storageDir);  // createTempFile은 랜덤 숫자를 파일명에 붙여서 관리 힘듬
        File image = new File(storageDir, imageFileName + ".png");  // exif 때문에 jpg로 저장
        if (!image.exists()) image.createNewFile();

        mCurrentPhotoPath = image.getAbsolutePath();
        return image;
    }






}