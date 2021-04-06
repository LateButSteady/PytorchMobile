package com.example.jwkim.kr.pytorchmobile;

import android.app.Activity;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class Util_Common {
    private long time_BackBtnPressed = 0;
    Activity activity;
    Toast toast;

    // 호출되는 activity 가져오기
    public Util_Common(Activity mAct) {
        // Util_Common이 선언된 activity 가져오기
        // 참고:ㅣhttps://stackoverflow.com/questions/29823480/android-custom-class-with-getapplicationcontext
        this.activity = mAct;
    }


    /*
     * back 버튼 짧게 두번 누르면 App 종료
     * 참고: https://dsnight.tistory.com/14
     */
    public void exitBackBtn(long interval) {
        // back 버튼을 긴 interval로 누르면 toast로 먼저 안내
        if (System.currentTimeMillis() > time_BackBtnPressed + interval) {
            time_BackBtnPressed = System.currentTimeMillis();
            toast = Toast.makeText(activity, "뒤로 가기 키를 한번 더 누르면 종료합니다.", Toast.LENGTH_SHORT);
            toast.show();
            return;
        }

        // 짧게 누른거로 판명되면 종료
        if (System.currentTimeMillis() <= time_BackBtnPressed + interval) {
            if(toast != null) toast.cancel();
            exitApp();
        }
    }


    /*
     * MainActivity 종료버튼 누르면 Alert Dialog
     * Yes: 앱 종료, No: Dialog 취소
     */
    public void exitBtnAlert() {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setMessage("종료 하시겠습니까?");
        builder.setTitle("종료 알림창")
                .setCancelable(true)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        exitApp();
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
        AlertDialog alert = builder.create();
        alert.setTitle("종료 알림창");
        alert.show();
    }


    /*
     * App 종료
     */
    public void exitApp() {
        ActivityCompat.finishAffinity(activity);   // 앱의 루트 activity 종료
        System.runFinalization();   // 작업중 thread 모두 종료되면 그때 종료
        System.exit(0);     // 현재 activity 종료
    }


    /*
     * IOexception
     */
    public void fn_IOexception(IOException e, String msg, String tag) {
        Log.e(tag, msg, e);
        e.printStackTrace();
        activity.finish();
    }

    /*
     * Exception
     */
    public void fn_Exception(Exception e, String msg, String tag) {
        Log.e(tag, msg, e);
        e.printStackTrace();
        activity.finish();
    }

    /*
     * Routine error
     */
    public void fn_error(String msg, String tag) {
        Log.e(tag, "Error reading file");
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }


    /*
     * asset file 불러오기
     */
    public static @NonNull String assetFilePath(@NonNull Context context, String assetName) throws IOException {
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






    /*
     * image 파일 Bitmap으로 불러오기
     */
    public Bitmap loadFileToBitmap(String pathFile) {
        Bitmap bitmap = null;

        try {
            File file = new File(pathFile);

            if (Build.VERSION.SDK_INT >= 29) {
                // android API29에서는 getBitmap deprecated되었음. -> 버전별로 나눠서 처리
                ImageDecoder.Source source = ImageDecoder.createSource(activity.getContentResolver(), Uri.fromFile(file));
                bitmap = ImageDecoder.decodeBitmap(source); // API29 이상 버전에서 getBitmap 대체하는 줄
            } else {
                bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), Uri.fromFile(file));
            }
        } catch (IOException e){
            fn_IOexception(e, "Error reading file", activity.getString(R.string.tag));
        }
        return bitmap;
    }


    /*
     * Bitmap을 image 파일로 저장
     */
    public void saveBitmapToFile(@NonNull Bitmap bmp, @NonNull String path) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        File f = new File(path);
        f.createNewFile();
        FileOutputStream fo = new FileOutputStream(f);
        fo.write(bytes.toByteArray());
        fo.close();
    }


    /*
     * File size 구하기
     */
    public long getFileSize(String path) {
        File file = new File(path);
        long lFileSize = 0;
        if (file.exists()) {
            lFileSize = file.length();
        }
        return lFileSize;
    }


    /*
     * 영상 파일의 exif에서 방향 정보를 얻기
     * 참고: https://duckssi.tistory.com/11?category=328338
     */
    public int getRotatationDegreeFromExif(String pathImg) {
        ExifInterface exif = null;
        int exifDegree = 0;
        int exifOrientation;

        try {
            // jpg 파일에서만 exif 구할 수 있음
            exif = new ExifInterface(pathImg);
        } catch (IOException e) {
            fn_IOexception(e, "Failed to get exif", activity.getString(R.string.tag));
        }

        if (exif != null) {
            exifOrientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
            exifDegree = exifOrientationToDegree(exifOrientation);
        }

        return exifDegree;
    }


    /*
     * 카메라 돌아갈때 회전하는 degree 반환
     * exif
     *      - 디카 등에서 사용되는 이미지 파일 메타데이터 포맷.
     *      - 사진/녹음파일에 시간 등의 각종 정보가 있음.
     *      - JPG, TIFF 포맷 지원
     * 참고: https://thxwelchs.github.io/METADATA-EXIF/
     */
    private int exifOrientationToDegree(int exifOrientation) {
        if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_90) {
            return 90;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_180) {
            return 180;
        } else if (exifOrientation == ExifInterface.ORIENTATION_ROTATE_270) {
            return 270;
        }
        return 0;
    }


    /*
     * Bitmap 회전
     */
    public Bitmap rotate(Bitmap bitmap, float degree) {
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    /*
     * imageView toggle
     */
    public void toggle_imageViews(@NonNull ImageView imgView1,@NonNull ImageView imgView2) {
        // 먼저 imageView가 null인지 체크
        //if (checkEmptyImageView(imgView1)) {
        //    fn_error("ImageView of origin image is empty", activity.getString(R.string.tag));
        //}

        // 현재 상태 체크
        // after 출력 상태일때
        if (View.VISIBLE == imgView2.getVisibility() &&
                View.INVISIBLE == imgView1.getVisibility()) {

            imgView2.setVisibility(View.INVISIBLE);
            imgView1.setVisibility(View.VISIBLE);
        }
        // before 출력 상태일때
        else if (View.INVISIBLE == imgView2.getVisibility() &&
                View.VISIBLE == imgView1.getVisibility()) {
            imgView2.setVisibility(View.VISIBLE);
            imgView1.setVisibility(View.INVISIBLE);
        }
        // 비정상
        else {
            Log.e(activity.getString(R.string.tag), "Both imgView are already ON or OFF");
            Toast.makeText(activity, "Both imgView are already ON or OFF", Toast.LENGTH_SHORT).show();
        }
    }


    /*
     * ImageView 비었는지 체크 (비었으면 Toast)
     */
    public boolean checkEmptyImageView(@NonNull ImageView imageView) {
        // 먼저 imageView가 null인지 체크
        boolean bRtnValue = true;
        imageView.getDrawable();
        if (null == imageView.getDrawable()) {
            Log.e(activity.getString(R.string.tag), "imageView is empty");
            bRtnValue = true;
        } else {
            bRtnValue = false;
        }
        return bRtnValue;
    }


    /*
     * URI에서 absolute path 얻기
     * 참고: https://www.viralpatel.net/pick-image-from-galary-android-app/
     */
    public String getPathFromURI(@NonNull Uri uri) {

        String[] filePathColumn = { MediaStore.Images.Media.DATA };
        // Get the cursor
        Cursor cursor = activity.getContentResolver().query(uri, filePathColumn, null, null, null);
        // Move to first row
        cursor.moveToFirst();

        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
        String path = cursor.getString(columnIndex);
        cursor.close();
        return path;
    }


    /*
     * ImageView에 영상 file 띄우기
     * pathImg는 jpg여야 한다?
     */
    //public void dispImgFile(ImageView imageView, String pathImg, boolean flagRotate) {
    public void dispImgFile(ImageView imageView, String pathImg) {
        Bitmap bitmap = null;

        // input 검사
        if (pathImg == null) {
            Log.e(activity.getString(R.string.tag), "Image path is null");
            Toast.makeText(activity, "Image path is null", Toast.LENGTH_SHORT).show();
        }
        if (imageView == null) {
            Log.e(activity.getString(R.string.tag), "imageView is null");
            Toast.makeText(activity, "imageView is null", Toast.LENGTH_SHORT).show();
        }

        // 불러와서 imgView에 띄우기
        File file = new File(pathImg);
        if (file.exists()) {

            // SDK 버전따라 bitmap 불러오기
            if (Build.VERSION.SDK_INT >= 29) {
                ImageDecoder.Source source = ImageDecoder.createSource(activity.getContentResolver(), Uri.fromFile(file));
                try {
                    bitmap = ImageDecoder.decodeBitmap(source);
                } catch (IOException e) {
                    fn_IOexception(e, activity.getString(R.string.FileNotFound), activity.getString(R.string.tag));
                }
            } else {
                try {
                    bitmap = MediaStore.Images.Media.getBitmap(activity.getContentResolver(), Uri.fromFile(file));
                } catch (IOException e) {
                    fn_IOexception(e, activity.getString(R.string.FileNotFound), activity.getString(R.string.tag));
                }
            }

            // imageView에 bitmap 띄우기. 이건 rotation 필요 없음
            imageView.setImageBitmap(bitmap);
        } else {
            Log.e(activity.getString(R.string.tag), activity.getString(R.string.FileNotFound));
            Toast.makeText(activity, activity.getString(R.string.FileNotFound), Toast.LENGTH_SHORT).show();
        }
    }


    /*
     * 파일 회전해서 imageView에 띄우기
     */
    public void rotateImageView(@NonNull ImageView imgView, @NonNull String path, float angle) {
        // path에 파일 있는지부터 체크
        File file = new File(path);
        Bitmap bitmap = null;
        //  영상 파일 새로 불러와서 setImageBitmap
        if (file.exists()) bitmap = loadFileToBitmap(path);
        else Log.e(activity.getString(R.string.tag),"File not found");
        if (null != bitmap) imgView.setImageBitmap(rotate(bitmap, angle));
    }


    /*
     * 파일 확장자 가져오기
     */
    public String getExtension(@NonNull String filePath) {
        String fileExtension = filePath.substring(filePath.lastIndexOf(".")+1,filePath.length());
        return fileExtension;
    }


    /*
     * 확장자 빼고 파일명에 더하기
     */
    public String addFileName(@NonNull String orgFileName, @NonNull String strAdd) {
        String Extension = orgFileName.substring(orgFileName.lastIndexOf(".")+1, orgFileName.length());
        String orgFileNameNoExtension = orgFileName.substring(0, orgFileName.lastIndexOf("."));
        return orgFileNameNoExtension + strAdd + "." + Extension;
    }


    /*
     * absolute path에서 URI 얻기
     * 참고: https://crystalcube.co.kr/184
     */
    public Uri getUriFromPath(String filePath) {
        Cursor cursor = activity.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                null, "_data = '" + filePath + "'", null, null);

        cursor.moveToNext();
        int id = cursor.getInt(cursor.getColumnIndex("_id"));
        Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);

        return uri;
    }
}
