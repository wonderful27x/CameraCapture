package com.cybercloud.cameracapture;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.media.MediaCodec;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.OrientationEventListener;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;
import com.cybercloud.cameracapture.media_component.CameraCapture;
import java.util.ArrayList;
import java.util.List;

/**
 * Camera activity,shows how to use CameraCapture API
 * create by wonderful
 * TODO fit SurfaceView size with the size witch camera chosen
 */
public class CameraActivity extends AppCompatActivity {

    private static final String TAG = "CameraActivity";

    private OrientationEventListener orientationListener;
    private SurfaceView surfaceView;
    private CameraCapture cameraCapture;
    private ImageView imageView;
    private Button switchCamera;
    private Button takePicture;
    private Button pauseStart;
    private Button stopRestart;
    private Button release;

    private Handler handler;

    private boolean pause = false;
    private boolean stop = false;

    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        surfaceView = findViewById(R.id.surfaceView);
        imageView = findViewById(R.id.imageView);
        switchCamera = findViewById(R.id.switchCamera);
        takePicture = findViewById(R.id.takePicture);
        pauseStart = findViewById(R.id.pauseStart);
        stopRestart = findViewById(R.id.stopRestart);
        release = findViewById(R.id.release);
        progress = findViewById(R.id.progress);

        handler = new Handler(getMainLooper());

        surfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                permissionCheck();
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {

            }
        });

        /**
         * API 1: ???????????????
         */
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraCapture.switchCamera();
            }
        });

        /**
         * API 2: ??????
         */
        takePicture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    progress.setVisibility(View.VISIBLE);
                    cameraCapture.takePicture();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        /**
         * API 3: ??????/??????
         */
        pauseStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pause = !pause;
                if(pause){
                    cameraCapture.pause();
                }else {
                    cameraCapture.start();
                }
            }
        });

        /**
         * API 4: ??????/????????????
         */
        stopRestart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stop = !stop;
                if(stop){
                    cameraCapture.stop();
                }else {
                    cameraCapture.prepare();
                    cameraCapture.start();
                }
            }
        });

        /**
         * API 5: ?????????????????????
         */
        release.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(cameraCapture != null){
                    cameraCapture.stop();
                    cameraCapture.release();
                    cameraCapture = null;
                }
            }
        });

        /**
         * API 12: ???????????????????????????????????????
         * ??????????????????,??????????????????????????????autoRotation???????????????????????????????????????????????????????????????
         */
        orientationListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if(cameraCapture != null){
                    cameraCapture.autoRotation(orientation);
                }
            }
        };
        if (orientationListener.canDetectOrientation()) {
            Log.v(TAG, "Can detect orientation");
            orientationListener.enable();
        } else {
            Log.v(TAG, "Cannot detect orientation");
            orientationListener.disable();

        }

    }

    /**
     * create camera and start
     */
    private void permissionGranted(){
        CameraCapture.Builder builder = new CameraCapture.Builder(this)
                /**
                 * API 0: ??????
                 */
                .setPreview(surfaceView.getHolder().getSurface())
                /**
                 * API 2: ??????
                 */
                .setTakePictureListener(new CameraCapture.TakePictureListener() {
                    @Override
                    public void picture(Bitmap bitmap, byte[] data, CameraCapture.CameraState cameraState) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Bitmap picture = bitmap;
                                if(cameraState.rotateAngle == -1){
                                    //todo ??????cameraState.rotateAngle = -1??????????????????????????????????????????????????????
                                }
                                progress.setVisibility(View.GONE);
                                imageView.setImageBitmap(picture);

                                String state;
                                if(cameraState.cameraFacing == CameraCharacteristics.LENS_FACING_FRONT){
                                    state = "facing: ??????\n";
                                }else {
                                    state = "facing: ??????\n";
                                }
                                state += "displayOrientation: " + cameraState.displayOrientation + "\n";
                                state += "sensorOrientation: " + cameraState.sensorOrientation + "\n";
                                state += "screenOrientation: " + cameraState.screenOrientation + "\n";
                                state += "rotateAngle: " + cameraState.rotateAngle + "\n";
                                Log.d(TAG, "CameraState: \n" + state);
                            }
                        });
                    }
                }, ImageFormat.JPEG)
                /**??????????????????**/
                .setPictureSize(1920,1080)

                //todo ???????????????????????????????????????????????????????????????????????????
                .enableCapture(true)

                /**
                 * API 6: ????????????->?????????,todo ??????????????????egl???????????????handler???check: checkAndUpdateEglState: invalid current EGLDisplay
                 */
                .captureForAutoTexture(/**pass your handler which connect to egl thread**/null, new CameraCapture.TextureCreateListener() {
                    @Override
                    public void textureCreated(int texture) {
                        //todo keep the texture,then you can draw it without setTextureUpdateListener and totally control the fps
                    }
                })
                .setTextureUpdateListener(new CameraCapture.TextureUpdateListener() {
                    @Override
                    public void textureUpdated(int texture, long timestamp, CameraCapture.CameraState cameraState) {
                        //Log.d(TAG, "textureUpdated, texture id: " + texture + " timestamp: " + timestamp + " rotation: " + rotation);
                        //todo you can draw with openGL
                    }
                })

                /**
                 * API 7: ??????yuv?????????todo ??? API6??????
                 */
                .captureDataBytes(new CameraCapture.CameraCaptureListener() {
                    @Override
                    public void capture(byte[] dataY, byte[] dataU, byte[] dataV, CameraCapture.CameraState cameraState) {
                        //Log.d(TAG, "capture yuv data,rotation: " + rotation);
                        //todo take the yuv data to do anything you want
                    }
                }, ImageFormat.YUV_420_888)

                /**
                 * API 8: ??????????????????surface?????????MediaCodec???????????????,
                 * todo ??? API6 API7 ??????,????????? API8 > API7 > API6
                 */
                .captureTargetSurface(/**pass a surface,example like from MediaCodec**/ null)

                /**
                 * API 9:???????????????????????????API6,API7,API8 ??????
                 * ??????3?????????????????????????????????null?????????SurfaceTexture.class
                 */
                .setCaptureSize(1920, 1080, MediaCodec.class)

                /**
                 * API 10: ??????fps????????????????????????
                 */
                .setFps(25)

                /**
                 * API 11: ???????????????
                 */
                .setCameraFacing(CameraCharacteristics.LENS_FACING_FRONT);

        cameraCapture = builder.build();
        cameraCapture.prepare();
        cameraCapture.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if(cameraCapture != null) cameraCapture.pause();
        Log.d(TAG, "onPause: ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(cameraCapture != null){
            cameraCapture.start();
        }
        Log.d(TAG, "onResume: ");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if(cameraCapture != null){
            cameraCapture.stop();
            cameraCapture.release();
            cameraCapture = null;
        }
        orientationListener.disable();
        Log.d(TAG, "onDestroy: ");
    }


    // =============================================????????????====================================================
    private List<String> checkPermission(String... permissions) {
        List<String> list = null;
        for (String permission:permissions){
            if(ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED){
                if(list == null){
                    list = new ArrayList<>();
                }
                list.add(permission);
            }
        }
        return list;
    }

    private boolean permissionCheck() {
        List<String> list = checkPermission(Manifest.permission.CAMERA);
        if(list != null){
            ActivityCompat.requestPermissions(this, list.toArray(new String[0]), 0);
            return false;
        }else {
            permissionGranted();
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case 0: {
                if (grantResults.length <= 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this,"???????????????????????????",Toast.LENGTH_SHORT).show();
                    finish();
                }else {
                    permissionGranted();
                }
            }
        }
    }
}