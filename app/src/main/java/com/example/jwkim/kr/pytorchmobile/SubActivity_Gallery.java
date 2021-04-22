package com.example.jwkim.kr.pytorchmobile;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import org.pytorch.Module;

import java.io.IOException;

public class SubActivity_Gallery extends AppCompatActivity implements View.OnClickListener {

    public SubActivity_Gallery() {
        // empty constructor
    }

    final int PICK_IMAGE_REQUEST = 1000;
    boolean good2Process = false;

    ImageView imgView_stillshot_org2 = null;
    ImageView imgView_stillshot_processed2 = null;
    TextView textView_msg2 = null;
    String mCurrentPhotoPath = null;
    String mCurrentPhotoPath_RIA = null;
    Button btn_choose = null;
    Button btn_process2 = null;
    Button btn_toggle2 = null;
    Button btn_rotate2 = null;
    Uri photoURI = null;

    private boolean bRescale = false;
    private float exifDegree; // output Bitmap rotation 각
    private Bitmap inputBitmap = null;
    private Bitmap inputBitmap_rotate = null;
    private Bitmap inputBitmap_rescale = null;

    private Bitmap outputBitmap = null;
    private Bitmap outputBitmap_tmp = null;
    private Module module = null;

    private View.OnClickListener clickListener;
    Util_Common util_common  = new Util_Common(SubActivity_Gallery.this);
    Util_CNN    util_cnn     = new Util_CNN(SubActivity_Gallery.this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sub_gallery);

        imgView_stillshot_org2 = findViewById(R.id.imgView_stillshot_org2);
        imgView_stillshot_processed2 = findViewById(R.id.imgView_stillshot_processed2);

        btn_choose = findViewById(R.id.btn_choose);
        btn_process2 = findViewById(R.id.btn_process2);
        btn_toggle2 = findViewById(R.id.btn_toggle2);
        textView_msg2 = findViewById(R.id.textView_msg2);
        btn_rotate2 = findViewById(R.id.btn_rotate2);


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

        // 이걸 넣어줘야 listen 시작함
        btn_choose.setOnClickListener(this);
        btn_process2.setOnClickListener(this);
        btn_toggle2.setOnClickListener(this);
        btn_rotate2.setOnClickListener(this);

    }


    // 여러개 버튼 처리
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_choose:
                Log.i(getString(R.string.tag), "CHOOSE button pressed");

                // 먼저 초기화
                inputBitmap = null;
                outputBitmap = null;
                imgView_stillshot_org2.setImageBitmap(inputBitmap);
                imgView_stillshot_processed2.setImageBitmap(outputBitmap);
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

                // Extension 체크
                if (null != mCurrentPhotoPath) {
                    Log.i(getString(R.string.tag), "File extension : " + util_common.getExtension(mCurrentPhotoPath));
                }

                // asset에서 CNN model 불러오기
                try {
                    module = util_cnn.loadModelCNN(getString(R.string.CNN_Model_FileName));
                } catch (IOException e) {
                    util_common.fn_IOexception(e, "Error reading assets", getString(R.string.tag));
                }

                // input bitmap 불러오기
                exifDegree = util_common.getRotatationDegreeFromExif(mCurrentPhotoPath);
                inputBitmap = util_common.loadFileToBitmap(mCurrentPhotoPath);

                // 바로 세우도록 회전
                inputBitmap_rotate = util_common.rotate(inputBitmap, exifDegree);

                // 원래 image size
                final int h = inputBitmap_rotate.getHeight();
                final int w = inputBitmap_rotate.getWidth();

                // FHD 아니라면 rescale
                if (!((1080 == h && 1920 == w) || (1920 == h && 1080 == w))) {
                    bRescale = true;
                    // 세로 이미지
                    if (h > w) {
                        inputBitmap_rescale = Bitmap.createScaledBitmap(inputBitmap_rotate, 1080, 1920, false);
                    }
                    // 가로 이미지
                    else {
                        inputBitmap_rescale = Bitmap.createScaledBitmap(inputBitmap_rotate, 1920, 1080, false);
                    }
                } else inputBitmap_rescale = inputBitmap_rotate;

                // height, width 중 0이 하나라도 있다면 error
                if (0 == h || 0 == w) {
                    util_common.fn_error("Input bitmap h=0 or w=0", getString(R.string.tag));
                }

                // runModel 시작 (rotate + rescale 이미지)
                Log.i(getString(R.string.tag), "Start runModel");
                outputBitmap_tmp = util_cnn.runModel(module, inputBitmap_rescale);

                // rescale 된거라면 되돌리기
                if (bRescale) {
                    outputBitmap_tmp = Bitmap.createScaledBitmap(outputBitmap_tmp, w, h, false);
                }

                // output을 input 파일처럼 돌리고 rescale
                outputBitmap = util_common.rotate(outputBitmap_tmp, -exifDegree);

                // 처리된 영상 save
                try {
                    util_common.saveBitmapToFile(outputBitmap, mCurrentPhotoPath_RIA);
                }
                catch(IOException e) {
                    util_common.fn_IOexception(e, "Error writing RIA image file", getString(R.string.tag));
                }

                // 결과 띄우기 (rescale 되었을 수도 있으니 다시 띄우기)
                imgView_stillshot_processed2.setImageBitmap(outputBitmap);
                imgView_stillshot_org2.setImageBitmap(util_common.rotate(inputBitmap,exifDegree));

                // 결과 영상 띄우기
                imgView_stillshot_processed2.setVisibility(View.VISIBLE);
                imgView_stillshot_org2.setVisibility(View.INVISIBLE);

                textView_msg2.setText(getString(R.string.press_TOGGLE));

                break;

            case R.id.btn_toggle2:
                Log.i(getString(R.string.tag), "TOGGLE button pressed");
                util_common.toggle_imageViews(imgView_stillshot_org2, imgView_stillshot_processed2);

                break;

            case R.id.btn_rotate2:
                Log.i(getString(R.string.tag), "Rotate button pressed");
                // 방어코드 조건
                //   - ImageView 화면이 empty
                //   - path = null
                //   - path의 파일 사이즈 = 0 (이건 다른 if문으로 체크)
                if (util_common.checkEmptyImageView(imgView_stillshot_org2)
                        || null == mCurrentPhotoPath) {
                    Toast.makeText(SubActivity_Gallery.this, getString(R.string.press_CHOOSE), Toast.LENGTH_LONG).show();
                    break;
                }
                if (0 == util_common.getFileSize(mCurrentPhotoPath)) {
                    Toast.makeText(SubActivity_Gallery.this, "File size = 0", Toast.LENGTH_LONG).show();
                    break;
                }


                exifDegree = (float) ((exifDegree - 90.0) % 360);
                Log.i(getString(R.string.tag), "exifDegree = " + exifDegree);

                util_common.rotateImageView(imgView_stillshot_org2, mCurrentPhotoPath, exifDegree);
                util_common.rotateImageView(imgView_stillshot_processed2, mCurrentPhotoPath_RIA, exifDegree);

                break;

            default:
                break;
        }
    }




    /*
     * 갤러리에서 영상 선택하기
     * 참고: https://blog.naver.com/cosmosjs/220940841567
     */
    public void loadImagefromGallery(View view) {
        //Intent 생성
        Intent intent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);

        //이미지만 보이도록
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

}
