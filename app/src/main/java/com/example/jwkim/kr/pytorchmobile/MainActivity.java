package com.example.jwkim.kr.pytorchmobile;
// package naming convention
// 참고: https://stackoverflow.com/questions/6273892/android-package-name-convention

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private long time = 0;

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


        btn_camera.setOnClickListener(this);
        btn_test.setOnClickListener(this);
        btn_gallery.setOnClickListener(this);
        btn_exit.setOnClickListener(this);
    }

    @Override
    public void onClick(@NonNull View v) {

        Intent intent;

        switch (v.getId()) {
            case R.id.btn_camera:
                intent = new Intent(MainActivity.this, SubActivity_Camera.class);
                startActivity(intent);
                break;

            case R.id.btn_test:
                intent = new Intent(MainActivity.this, SubActivity_Test.class);
                startActivity(intent);
                break;

            case R.id.btn_gallery:
                intent = new Intent(MainActivity.this, SubActivity_Gallery.class);
                startActivity(intent);
                break;

            case R.id.btn_exit:
                AlertDialog.Builder builder_exit = new AlertDialog.Builder(MainActivity.this);
                builder_exit.setMessage(getString(R.string.ExitMsg));
                builder_exit.setCancelable(true)
                        .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                terminateApp(MainActivity.this);
                            }
                        })
                        .setNegativeButton("No", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                dialog.cancel();
                            }
                        });
                AlertDialog alert = builder_exit.create();
                alert.setTitle(getString(R.string.ExitTitle));
                alert.show();
                break;

            default:
                break;
        }
    }


    // back 버튼 2초 안에 두번 누르면 앱 종료
    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - time >= 2000) {
            time = System.currentTimeMillis();
            Toast.makeText(getApplicationContext(), getString(R.string.AskExit), Toast.LENGTH_SHORT).show();
        } else {
            terminateApp(MainActivity.this);
        }
    }


    // 앱 종료
    public void terminateApp(Activity activity) {
        ActivityCompat.finishAffinity(activity);    // 앱의 루트 activity 종료
        System.runFinalization();   // 작업중인 thread 모두 종료되면 그때 종료.
        System.exit(0);     // 현재 activity 종료 (바로 종료됨)
    }
}