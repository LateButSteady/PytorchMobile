package com.example.jwkim.kr.pytorchmobile;
// package naming convention
// 참고: https://stackoverflow.com/questions/6273892/android-package-name-convention

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    // test github commit
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
        Button btn_gallery = (Button) findViewById(R.id.btn_gallery);
        Button btn_video = (Button) findViewById(R.id.btn_video);
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
                    case R.id.btn_gallery:
                        intent = new Intent(MainActivity.this, com.example.jwkim.kr.pytorchmobile.SubActivity_Gallery.class);
                        startActivity(intent);
                        break;
                    case R.id.btn_video:
                        Toast.makeText(MainActivity.this, "구현중입니다.", Toast.LENGTH_SHORT).show();
                        break;
                    case R.id.btn_exit:
                        util_common.exitBtn();
                        break;
                }
            }
        };

        btn_camera.setOnClickListener(clickListener);
        btn_gallery.setOnClickListener(clickListener);
        btn_video.setOnClickListener(clickListener);
        btn_exit.setOnClickListener(clickListener);
    }

    @Override
    public void onBackPressed() {
        util_common.exitBackBtn(2000);
    }
}