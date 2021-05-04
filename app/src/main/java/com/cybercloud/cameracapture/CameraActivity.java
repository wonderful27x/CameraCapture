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
         * API 1: 切换前后摄
         */
        switchCamera.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cameraCapture.switchCamera();
            }
        });

        /**
         * API 2: 拍照
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
         * API 3: 暂停/开始
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
         * API 4: 停止/重新开始
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
         * API 5: 停止并释放资源
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
         * API 12: 自动旋转，只针对拍照有效果
         * 监听屏幕旋转,传递给相机模块，调用autoRotation更新旋转角则意味着内部将自动处理图片的旋转
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
                 * API 0: 预览
                 */
                .setPreview(surfaceView.getHolder().getSurface())
                /**
                 * API 2: 拍照
                 */
                .setTakePictureListener(new CameraCapture.TakePictureListener() {
                    @Override
                    public void picture(Bitmap bitmap, byte[] data, CameraCapture.CameraState cameraState) {
                        handler.post(new Runnable() {
                            @Override
                            public void run() {
                                Bitmap picture = bitmap;
                                if(cameraState.rotateAngle == -1){
                                    //todo 如果cameraState.rotateAngle = -1说明内部没有做旋转，需要外界自己处理
                                }
                                progress.setVisibility(View.GONE);
                                imageView.setImageBitmap(picture);

                                String state;
                                if(cameraState.cameraFacing == CameraCharacteristics.LENS_FACING_FRONT){
                                    state = "facing: 前摄\n";
                                }else {
                                    state = "facing: 后摄\n";
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
                /**设置拍照尺寸**/
                .setPictureSize(1920,1080)

                //todo 开启数据捕获，除了预览和拍照其他功能必须开启此功能
                .enableCapture(true)

                /**
                 * API 6: 捕获图像->转纹理,todo 请务必传递与egl线程关联的handler，check: checkAndUpdateEglState: invalid current EGLDisplay
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
                 * API 7: 捕获yuv数据，todo 与 API6排斥
                 */
                .captureDataBytes(new CameraCapture.CameraCaptureListener() {
                    @Override
                    public void capture(byte[] dataY, byte[] dataU, byte[] dataV, CameraCapture.CameraState cameraState) {
                        //Log.d(TAG, "capture yuv data,rotation: " + rotation);
                        //todo take the yuv data to do anything you want
                    }
                }, ImageFormat.YUV_420_888)

                /**
                 * API 8: 设置捕获输出surface，这在MediaCodec编码时常用,
                 * todo 与 API6 API7 排斥,优先级 API8 > API7 > API6
                 */
                .captureTargetSurface(/**pass a surface,example like from MediaCodec**/ null)

                /**
                 * API 9:设置数据捕获尺寸，API6,API7,API8 通用
                 * 参数3为辅助尺寸计算使用，传null默认为SurfaceTexture.class
                 */
                .setCaptureSize(1920, 1080, MediaCodec.class)

                /**
                 * API 10: 设置fps，预览和捕获一致
                 */
                .setFps(25)

                /**
                 * API 11: 初始摄像头
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


    // =============================================储存权限申请================================================
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
                    Toast.makeText(this,"您拒绝了必要权限！",Toast.LENGTH_SHORT).show();
                    finish();
                }else {
                    permissionGranted();
                }
            }
        }
    }
}