package com.example.jwkim.kr.pytorchmobile;
// package naming convention
// 참고: https://stackoverflow.com/questions/6273892/android-package-name-convention

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends AppCompatActivity {

    final int PICK_PHOTO = 1000;

    // custom class 쓰기 위한 초기화 먼저
    com.example.jwkim.kr.pytorchmobile.Util_Common util_common = new com.example.jwkim.kr.pytorchmobile.Util_Common(MainActivity.this);

    // onClickListener 선언 (버튼 여러개 코드 간단하게 구현하기 위해 필요)
    // 참고: https://seungjuitmemo.tistory.com/66
    private View.OnClickListener clickListener;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btn_camera = (Button) findViewById(R.id.btn_camera);
        Button btn_test = (Button) findViewById(R.id.btn_test);
        Button btn_gallery = (Button) findViewById(R.id.btn_gallery);
        Button btn_exit = (Button) findViewById(R.id.btn_exit);

        clickListener = new Button.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent;

                switch (v.getId()) {
                    case R.id.btn_camera:
                        intent = new Intent(MainActivity.this, com.example.jwkim.kr.pytorchmobile.SubActivity_Camera.class);
                        startActivity(intent);
                        break;
                    case R.id.btn_test:
                        intent = new Intent(MainActivity.this, SubActivity_Test.class);
                        startActivity(intent);
                        break;
                    case R.id.btn_gallery:
                        intent = new Intent( MainActivity.this, SubActivity_Gallery.class);
                        startActivity(intent);
                        break;
                    case R.id.btn_exit:
                        util_common.exitBtn();
                        break;
                }
            }
        };

        btn_camera.setOnClickListener(clickListener);
        btn_test.setOnClickListener(clickListener);
        btn_gallery.setOnClickListener(clickListener);
        btn_exit.setOnClickListener(clickListener);
    }

    public void pickImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(intent, PICK_PHOTO);

        //Intent intent2 = new Intent(MainActivity.this, SubActivity_Gallery.class);
        //startActivity(intent2);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_PHOTO && resultCode == Activity.RESULT_OK) {
            if (data == null) {
                //Display an error
                util_common.fn_error("Failed: Pick photo");
                return;
            }
            try {
                //Now you can do whatever you want with your inpustream, save it as file, upload to a server, decode a bitmap...
                InputStream inputStream = MainActivity.this.getContentResolver().openInputStream(data.getData());
            } catch (IOException e) {
                util_common.fn_IOexception(e, "Failed: Pick photo 2");
            }
        }
    }

        @Override
        public void onBackPressed () {
            util_common.exitBackBtn(2000);
        }
    }