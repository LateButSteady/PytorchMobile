package com.example.jwkim.kr.pytorchmobile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.pytorch.Module;

import java.io.File;

public class SubActivity_Gallery extends AppCompatActivity {

    final int PICK_IMAGE_REQUEST = 1000;
    ImageView imgView_stillshot_org2 = null;
    ImageView imgView_stillshot_processed2 = null;
    TextView textView_msg2 = null;
    String mCurrentPhotoPath = null;
    Button btn_pick = null;
    Button btn_process2 = null;
    Button btn_toggle2 = null;

    private int exifDegree; // output Bitmap rotation 각
    private Bitmap inputBitmap = null;
    private Bitmap outputBitmap_tmp = null;
    private Bitmap outputBitmap = null;
    private Module module = null;

    private View.OnClickListener clickListener;
    com.example.jwkim.kr.pytorchmobile.Util_Common  util_common  = new com.example.jwkim.kr.pytorchmobile.Util_Common(SubActivity_Gallery.this);
    com.example.jwkim.kr.pytorchmobile.Util_CNN     util_cnn     = new com.example.jwkim.kr.pytorchmobile.Util_CNN(SubActivity_Gallery.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_gallery);

        imgView_stillshot_org2 = findViewById(R.id.imgView_stillshot_org2);
        imgView_stillshot_processed2 = findViewById(R.id.imgView_stillshot_processed2);

        btn_pick = findViewById(R.id.btn_pick);
        btn_process2 = findViewById(R.id.btn_process2);
        btn_toggle2 = findViewById(R.id.btn_toggle2);
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


        // 여러개 버튼 처리
        clickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch (v.getId()) {
                    case R.id.btn_pick:
                        loadImagefromGallery(v);
                        break;

                    case R.id.btn_process2:
                        // process 먼저 실행
                        //   - .pt 파일 로드
                        //   - 입력영상 불러오기
                        //   - 처리하기
                        //   - 결과영상 저장
                        //   - 입력영상, 결과영상 경로 intent bundle에 싣기

                        // 먼저 imageView 비었으면 에러
                        if (null == imgView_stillshot_org2.getDrawable()) {
                            Log.e(getString(R.string.tag), "imgView is Empty");
                            textView_msg2.setText(getString(R.string.Press_SHOOT));
                            Toast.makeText(SubActivity_Gallery.this, getString(R.string.Press_SHOOT), Toast.LENGTH_SHORT).show();
                            break;
                        }

                        // path null이면 에러
                        if (null == mCurrentPhotoPath) {
                            Log.e(getString(R.string.tag), "File path = null");
                            Toast.makeText(SubActivity_Gallery.this, "File path = null", Toast.LENGTH_SHORT).show();
                            break;
                        }
                        // 사진 찍지 않거나 실패해서 file size = 0 이라면 에러
                        else if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                            Log.e(getString(R.string.tag), "File size = 0kB");
                            textView_msg2.setText(getString(R.string.Press_SHOOT));
                            Toast.makeText(SubActivity_Gallery.this, getString(R.string.Press_SHOOT), Toast.LENGTH_SHORT).show();
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
                        imgView_stillshot_processed2.setImageBitmap(outputBitmap);
                        imgView_stillshot_processed2.setVisibility(View.VISIBLE);
                        imgView_stillshot_org2.setVisibility(View.INVISIBLE);

                        textView_msg2.setText(getString(R.string.Press_TOGGLE));

                        break;

                    case R.id.btn_toggle2:
                        util_common.toggle_imageViews(imgView_stillshot_org2, imgView_stillshot_processed2);
                        break;
                }
            }
        };

        // 이걸 넣어줘야 listen 시작함
        btn_pick.setOnClickListener(clickListener);
        btn_process2.setOnClickListener(clickListener);
        btn_toggle2.setOnClickListener(clickListener);
    }



    /*
     * 갤러리에서 영상 선택하기
     * 참고: https://blog.naver.com/cosmosjs/220940841567
     */
    public void loadImagefromGallery(View view) {
        //Intent 생성
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        intent.setType("image/*"); //이미지만 보이게
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
                Uri uri = data.getData();
                mCurrentPhotoPath = util_common.getPathFromURI(uri);

                util_common.dispImgFile(imgView_stillshot_org2, mCurrentPhotoPath);

            } else {
                Toast.makeText(this, "취소 되었습니다.", Toast.LENGTH_LONG).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Oops! 로딩에 오류가 있습니다.", Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }

    }

}
