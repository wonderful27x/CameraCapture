package com.cybercloud.cameracapture.media_component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.opengl.GLES11Ext;
import android.opengl.GLES30;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import androidx.annotation.NonNull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author wonderful
 * @date 2021-4-15
 * @version v1.0
 * @descreption 相机采集模块
 * TODO given/notify the chosen size outside
 */
public class CameraCapture implements MediaControlInterface {

    private static final String TAG = "CameraCapture";

    private static final int PICTURE_MAX_IMAGES = 1;
    private static final int CAPTURE_MAX_IMAGES = 3;
    private static final int SIZE_OFFSET = 32;

    private Context context;

    //camera val
    private CameraManager manager;
    private CameraDevice cameraDevice;
    private CameraCharacteristics cameraCharacteristics;
    private CameraCaptureSession captureSession;
    private String cameraId;

    private Size pictureSize;                                           //拍照size
    private Size captureSize;                                           //录制/预览size

    private Surface preview;                                            //预览surface
    private Surface captureView;                                        //录制surface，如果外界没有传入则内部创建
    private SurfaceTexture surfaceTexture;                              //内部SurfaceTexture
    private int[] texture;                                              //内部纹理
    private boolean innerCaptureView = false;                           //captureView是否为内部创建
    private boolean enableCapture = false;                              //是否开启录制
    private Class<?> auxiliaryCaptureSizeClass = SurfaceTexture.class;  //获取尺寸用的辅助类Class

    private ImageReader pictureImageReader;                             //拍照时用于读取数据
    private ImageReader captureImageReader;                             //录制时用于读取数据
    private int pictureFormat = ImageFormat.JPEG;                       //拍照数据格式，目前只支持ImageFormat.JPEG
    private int captureFormat = ImageFormat.YUV_420_888;                //预览数据格式，目前只支持ImageFormat.YUV_420_888
    private int cameraFacing = CameraCharacteristics.LENS_FACING_BACK;  //前后摄
    private int fps = 25;                                               //预览/录制帧率

    private ConditionSyncLock syncLock;

    private @MediaState int mediaState = MediaState.IDLE;

    private CameraState cameraState;                                    //相机状态信息
    private int screenOrientation = -1;                                 //屏幕旋转角

    private ExecutorService executorService;
    private HandlerThread handlerThread;
    private Handler cameraHandler;
    private Handler glHandler;

    private TextureUpdateListener textureUpdateListener;
    private TakePictureListener takePictureListener;
    private CameraCaptureListener cameraCaptureListener;
    private TextureCreateListener textureCreateListener;

    private CameraCapture(Context context){
        this.context = context;
        cameraState = new CameraState();
    }

    /**
     * 抛出非法状态异常
     * @param method 调用的方法名
     * @param validMediaState 有效的状态
     * @param invalidMediaStateCode 无效的状态（code）
     */
    private void throwException(String method,String validMediaState,int invalidMediaStateCode){
        throw new IllegalStateException(
                getClass().getName() + " exception:\n" + "try to " + method + " with invalid state: @MediaState(" + invalidMediaStateCode + "),it can only be @MediaState." + validMediaState
        );
    }

    /**
     * 获取屏幕旋转角，调用此方法则意味着内部将自动处理拍照的旋转，
     * 此方法应该在OrientationEventListener监听中更新时调用
     * @param orientation 屏幕旋转角
     */
    public void autoRotation(int orientation){
        screenOrientation = orientation;
    }

    public void switchCamera(){
        if(cameraFacing == CameraCharacteristics.LENS_FACING_BACK){
            cameraFacing = CameraCharacteristics.LENS_FACING_FRONT;
        }else if(cameraFacing == CameraCharacteristics.LENS_FACING_FRONT){
            cameraFacing = CameraCharacteristics.LENS_FACING_BACK;
        }

        stop();
        prepare();
        start();
    }

    public void takePicture() throws CameraAccessException {
        if(mediaState != MediaState.RUNNING){
            Log.w(TAG,"CameraCapture is not running while taking picture!");
            return;
        }

        if(takePictureListener == null){
            Log.w(TAG,"TakePictureListener does not set,cannot take picture!");
            return;
        }

        if(captureSession == null){
            Log.e(TAG, "CaptureSession is null,takePicture will not action !");
            return;
        }

        if(!syncLock.nonBlockingLock()){
            Log.w(TAG,"Cannot get lock when taking picture,it is may because the last picture does not finish!");
            return;
        }

        final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
        builder.addTarget(pictureImageReader.getSurface());
        // 自动对焦
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        // 自动曝光
        builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
        //todo
        //CameraState cameraState = updateCameraState();
        //if(cameraState != null && cameraState.rotateAngle > 0){
        //    builder.set(CaptureRequest.JPEG_ORIENTATION,cameraState.rotateAngle);
        //}
        CaptureRequest request = builder.build();
        //发送拍照请求capture只发送一次
        captureSession.capture(request, null,cameraHandler);
    }

    /**
     * 开始预览和录制，将二者添加到一个CaptureRequest中
     * @throws CameraAccessException
     */
    private void startPreviewAndCapture() throws CameraAccessException {
        if(captureSession == null){
            Log.e(TAG, "captureSession is null,startPreviewAndCapture will not action !");
            return;
        }

        if(preview == null && captureView == null && captureImageReader == null)return;

        CaptureRequest.Builder builder;
        if(captureView != null || captureImageReader != null){
            builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
        }else {
            builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
        }

        if(enableCapture){
            if(captureView != null){
                builder.addTarget(captureView);
            }else {
                builder.addTarget(captureImageReader.getSurface());
            }
        }else {
            Log.w(TAG,"Camera capture does not enable,you can get nothing except preview and take picture !!! You can enable it through " + getClass().getName() + ".Builder");
        }

        if(preview != null){
            builder.addTarget(preview);
        }

        //todo
        //https://blog.csdn.net/sunyFS/article/details/105454314
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE,new Range<Integer>(fps, fps));
        //自动对焦
        builder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        CaptureRequest request = builder.build();
        captureSession.setRepeatingRequest(request, null,cameraHandler);
    }

    //初始化拍照和录制的ImageReader
    private void initImageReader(){
        final StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        //拍照ImageReader
        if(takePictureListener != null){
            if(streamConfigurationMap.isOutputSupportedFor(pictureFormat)){
                pictureImageReader = ImageReader.newInstance(pictureSize.getWidth(),pictureSize.getHeight(),pictureFormat,PICTURE_MAX_IMAGES);
                pictureImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        syncLock.nonBlockingUnlock();
                        if(mediaState != MediaState.RUNNING)return;
                        //拿到拍照照片数据
                        Image image = reader.acquireLatestImage();
                        if(image == null)return;

                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        image.close();

                        Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                        CameraState cameraState = updateCameraState();
                        if(cameraState == null)return;
                        if(cameraState.rotateAngle > 0) {
                            bitmap = bitmapRotate(bitmap,cameraState.rotateAngle);
                        }
                        takePictureListener.picture(bitmap,bytes,cameraState);
                    }
                },cameraHandler);
            }else {
                Log.e(TAG, "unsupported picture format: " + pictureFormat);
            }
        }

        //录制ImageReader
        if(enableCapture && cameraCaptureListener != null) {
            if(streamConfigurationMap.isOutputSupportedFor(captureFormat)){
                captureImageReader = ImageReader.newInstance(captureSize.getWidth(),captureSize.getHeight(),captureFormat,CAPTURE_MAX_IMAGES);
                captureImageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                    @Override
                    public void onImageAvailable(ImageReader reader) {
                        if(mediaState != MediaState.RUNNING)return;
                        //todo do not use acquireNextImage or it will calls a bug when pause/start frequently
                        Image image = reader.acquireLatestImage();
                        if(image == null)return;

                        ByteBuffer bufferY = image.getPlanes()[0].getBuffer();
                        ByteBuffer bufferU = image.getPlanes()[1].getBuffer();
                        ByteBuffer bufferV = image.getPlanes()[2].getBuffer();
                        byte[] bytesY = new byte[bufferY.remaining()];
                        byte[] bytesU = new byte[bufferU.remaining()];
                        byte[] bytesV = new byte[bufferV.remaining()];
                        bufferY.get(bytesY);
                        bufferU.get(bytesU);
                        bufferV.get(bytesV);
                        image.close();

                        CameraState cameraState = updateCameraState();
                        if(cameraState == null)return;
                        cameraCaptureListener.capture(bytesY,bytesU,bytesV,cameraState);
                    }
                },cameraHandler);
            }else {
                Log.e(TAG, "unsupported capture format: " + captureFormat);
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void initCamera() throws CameraAccessException {

        //1. find camera
        manager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        for (String id:manager.getCameraIdList()){
            CameraCharacteristics cameraCharacteristics = manager.getCameraCharacteristics(id);
            Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == cameraFacing){
                cameraId = id;
                CameraCapture.this.cameraCharacteristics = cameraCharacteristics;
                Log.i(TAG, "found optimal camera,id is: " + cameraId);
                break;
            }
        }

        //2. 初始化尺寸
        initSize(cameraCharacteristics);

        //3. 初始化surface
        initImageReader();
        createCaptureSurface();

        //4. 打开camera
        syncLock.lock("prepare");
        manager.openCamera(cameraId, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Log.i(TAG, "camera onOpened");
                cameraDevice = camera;
                syncLock.unlock("prepare");
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Log.i(TAG, "camera onDisconnected");
                syncLock.unlock("prepare");
                camera.close();
                //todo how to do ???
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Log.e(TAG, "camera onError: " + error);
                syncLock.unlock("prepare");
                camera.close();
                //todo how to do ???
            }
        }, cameraHandler);

        //5. 创建CaptureSession
        syncLock.lock("prepare");
        if(cameraDevice == null){
            syncLock.unlockAll("prepare");
            syncLock.unlockAll("start");
            return;
        }
        createCaptureSession();
    }

    //创建Capture Session会话
    private void createCaptureSession(){
        //创建CaptureSession
        if(android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.P){
            List<OutputConfiguration> outputConfigurations = new ArrayList<>();
            if(pictureImageReader != null){
                outputConfigurations.add(new OutputConfiguration(pictureImageReader.getSurface()));
            }
            if(captureImageReader != null){
                outputConfigurations.add(new OutputConfiguration(captureImageReader.getSurface()));
            }
            if(preview != null){
                outputConfigurations.add(new OutputConfiguration(preview));
            }
            if(captureView != null){
                outputConfigurations.add(new OutputConfiguration(captureView));
            }

            SessionConfiguration configuration = new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputConfigurations, executorService, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "cameraCaptureSession create successful!");
                    captureSession = session;
                    syncLock.unlockAll("prepare");
                    syncLock.unlockAll("start");
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Log.e(TAG, "failed to create CameraCaptureSession!");
                    syncLock.unlockAll("prepare");
                    syncLock.unlockAll("start");
                    session.close();
                }
            });

            try {
                cameraDevice.createCaptureSession(configuration);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                syncLock.unlockAll("prepare");
                syncLock.unlockAll("start");
            }
        }else{
            List<Surface> surfaces = new ArrayList<>();
            if(pictureImageReader != null){
                surfaces.add(pictureImageReader.getSurface());
            }
            if(captureImageReader != null){
                surfaces.add(captureImageReader.getSurface());
            }
            if(preview != null){
                surfaces.add(preview);
            }
            if(captureView != null){
                surfaces.add(captureView);
            }
            try {
                cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(@NonNull CameraCaptureSession session) {
                        Log.i(TAG, "cameraCaptureSession create successful!");
                        captureSession = session;
                        syncLock.unlockAll("prepare");
                        syncLock.unlockAll("start");
                    }

                    @Override
                    public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                        Log.e(TAG, "failed to create CameraCaptureSession!");
                        syncLock.unlockAll("prepare");
                        syncLock.unlockAll("start");
                        session.close();
                    }
                },cameraHandler);
            } catch (CameraAccessException e) {
                e.printStackTrace();
                syncLock.unlockAll("prepare");
                syncLock.unlockAll("start");
            }
        }

    }

    private void initSize(CameraCharacteristics cameraCharacteristics){
        final StreamConfigurationMap streamConfigurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if(streamConfigurationMap == null){
            Log.e(TAG, "size init failed,StreamConfigurationMap is null!");
            return;
        }
        //拍照size
        if(pictureSize == null){
            //获取相机支持的最大拍照尺寸
            pictureSize = Collections.max(Arrays.asList(streamConfigurationMap.getOutputSizes(pictureFormat)), new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                }
            });
        }else {
            //获取相机支持的最接近的尺寸
            pictureSize = getOptimalSize(streamConfigurationMap.getOutputSizes(pictureFormat),pictureSize.getWidth(),pictureSize.getHeight());
        }

        if(pictureSize != null){
            Log.i(TAG, "found optimal picture size: " + pictureSize.getWidth() + "-" + pictureSize.getHeight());
        }else {
            Log.e(TAG, "pictureSize is null!");
        }


        //预览/录制size
        if(captureSize == null){
            //获取相机支持的最大预览尺寸
            captureSize = Collections.max(Arrays.asList(streamConfigurationMap.getOutputSizes(auxiliaryCaptureSizeClass)), new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth());
                }
            });
        }else {
            //获取相机支持的最接近的尺寸
            captureSize = getOptimalSize(streamConfigurationMap.getOutputSizes(auxiliaryCaptureSizeClass),captureSize.getWidth(),captureSize.getHeight());
        }

        if(captureSize != null){
            Log.i(TAG, "found optimal captureSize size: " + captureSize.getWidth() + "-" + captureSize.getHeight());
        }else {
            Log.e(TAG, "captureSize is null!");
        }
    }

    //旋转
    private Bitmap bitmapRotate(Bitmap bitmap, float degree){
        Matrix matrix = new Matrix();
        matrix.postRotate(degree);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }

    private int getDisplayOrientation(){
        int rotation = context.getDisplay().getRotation();
        int displayRotation = 0;
        switch (rotation) {
            //自然方向（手机竖屏）
            case Surface.ROTATION_0:
                displayRotation = 0;
                break;
            //自然方向逆时针旋转90度
            case Surface.ROTATION_90:
                displayRotation = 90;
                break;
            //自然方向旋转180度
            case Surface.ROTATION_180:
                displayRotation = 180;
                break;
            //自然方向顺时针旋转90度
            case Surface.ROTATION_270:
                displayRotation = 270;
                break;
        }
        return displayRotation;
    }

    //更新相机状态信息
    //https://www.jianshu.com/p/067889611ae7
    //官方demo
    private CameraState updateCameraState(){
        if(cameraCharacteristics == null){
            Log.e(TAG, "Get sensorOrientation while cameraCharacteristics is null,this should not happen,things out of control !!!");
            return null;
        }

        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        int displayOrientation = getDisplayOrientation();

        int rotation;
        if(screenOrientation < 0){
            rotation = -1;
        }
        else if(screenOrientation <= 45) {
            rotation = 0;
        }else if(screenOrientation <= 135){
            rotation = 90;
        }else if(screenOrientation <= 225){
            rotation = 180;
        }else if(screenOrientation <= 315){
            rotation = 270;
        }else {
            rotation = 0;
        }

        // Reverse device orientation for front-facing cameras
        int sign;
        if (cameraCharacteristics.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_FRONT) {
            sign = 1;
        }else {
            sign = -1;
        }

        // Calculate desired JPEG orientation relative to camera orientation to make
        // the image upright relative to the device orientation
        int result;
        if(rotation == -1){
            result = -1;
        }else {
            result = (sensorOrientation - (rotation * sign) + 360) % 360;
        }

        cameraState.cameraFacing = cameraFacing;
        cameraState.displayOrientation = displayOrientation;
        cameraState.sensorOrientation = sensorOrientation;
        cameraState.screenOrientation = screenOrientation;
        cameraState.rotateAngle = result;

        return cameraState;
    }

    //在保持比列的情况下选择最合适的size
    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        Size min = null;
        Size mid = null;
        Size max = null;
        for (Size size:sizeMap){
            if(size.getWidth() * height == size.getHeight() * width){
                //如果找到相等的size直接返回
                if(size.getWidth() == width && size.getHeight() == height){
                    return size  ;
                }
                //否则记录小于且最接近的
                else if(size.getWidth() < width && size.getHeight() < height){
                    if(min == null || (size.getWidth() > min.getWidth() && size.getHeight() > min.getHeight())){
                        min = size;
                    }
                }
                //否则记录大于且最接近的
                else if(size.getWidth() < width + SIZE_OFFSET && size.getHeight() < height + SIZE_OFFSET){
                    if(mid == null || (size.getWidth() < mid.getWidth() && size.getHeight() < mid.getHeight())){
                        mid = size;
                    }
                }else {
                    max = size;
                }
            }
        }

        if(mid != null){
            Log.i(TAG, "cannot find equal size: " + width + "-" + height + ",mid size: " + mid.getWidth() + "-" + mid.getHeight() + " will return");
            return mid;
        }

        if(min != null){
            Log.i(TAG, "cannot find equal size: " + width + "-" + height + ",min size: " + min.getWidth() + "-" + min.getHeight() + " will return");
            return min;
        }

        if(max != null){
            Log.i(TAG, "cannot find equal size: " + width + "-" + height + ",max size: " + max.getWidth() + "-" + max.getHeight() + " will return");
            return max;
        }

        Log.e(TAG, "cannot find optimal size, the default size in index 0 will return, size: " + sizeMap[0].getWidth() + "-" + sizeMap[0].getHeight());
        return sizeMap[0];
    }

    //创建录制surface（如何外界没有提供）
    private void createCaptureSurface(){
        if(!enableCapture || captureView != null || cameraCaptureListener != null){
            syncLock.unlock("prepare");
            return;
        }

        if(glHandler == null){
            throw new RuntimeException("Create texture without EGL environment,need a GL_HANDLER !!! You can pass it through " + getClass().getName() + ".Builder");
        }

        glHandler.post(new Runnable() {
            @Override
            public void run() {
                //创建纹理
                texture = new int[1];
                GLES30.glGenTextures(1,texture,0);
                GLES30.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,texture[0]);
                GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
                GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST);
                GLES30.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);

                //绑定纹理到SurfaceTexture
                surfaceTexture = new SurfaceTexture(texture[0]);
                surfaceTexture.setDefaultBufferSize(captureSize.getWidth(),captureSize.getHeight());
                surfaceTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                    @Override
                    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                        if(mediaState != MediaState.RUNNING)return;
                        surfaceTexture.updateTexImage();
                        //更新纹理后回调到GL线程
                        if(textureUpdateListener != null){
                            CameraState cameraState = updateCameraState();
                            if(cameraState == null)return;
                            textureUpdateListener.textureUpdated(texture[0],surfaceTexture.getTimestamp(),cameraState);
                        }
                    }
                },glHandler);
                captureView = new Surface(surfaceTexture);
                innerCaptureView = true;

                if(textureCreateListener != null){
                    textureCreateListener.textureCreated(texture[0]);
                }

                syncLock.unlock("prepare");
            }
        });
    }

    /**
     * 初始化
     */
    @Override
    public void prepare(){

        if(mediaState == MediaState.PREPARE) {
            Log.w(TAG,"it has already prepared when invoking prepare!");
            return;
        }

        if(mediaState != MediaState.IDLE && mediaState != MediaState.STOP){
            throwException("prepare","IDLE/STOP/PREPARE",mediaState);
        }

        if(syncLock == null){
            syncLock = new ConditionSyncLock(false);
        }

        if(executorService == null){
            executorService = Executors.newCachedThreadPool();
        }

        if(handlerThread == null){
            handlerThread = new HandlerThread("CameraCapture");
            handlerThread.start();
            cameraHandler = new Handler(handlerThread.getLooper());
        }

        syncLock.lock("prepare");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    initCamera();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
            }
        });

        mediaState = MediaState.PREPARE;
    }

    @Override
    public void start() {
        if(mediaState == MediaState.RUNNING){
            Log.w(TAG,"it has already running when invoking start!");
            return;
        }else if(mediaState == MediaState.PAUSE){
            //pause 状态下调用start则唤醒线程
            mediaState = MediaState.RUNNING;
            try {
                startPreviewAndCapture();
            } catch (CameraAccessException e) {
                e.printStackTrace();
                Log.e(TAG, "start with error: " + e);
            }
            return;
        }

        if(mediaState != MediaState.PREPARE){
            throwException("start","PREPARE/PAUSE/RUNNING",mediaState);
        }

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                syncLock.lock("start");
                try {
                    startPreviewAndCapture();
                } catch (CameraAccessException e) {
                    e.printStackTrace();
                }
                syncLock.unlock("start");
            }
        });

        mediaState = MediaState.RUNNING;
    }

    @Override
    public void pause() {

        if(mediaState == MediaState.PAUSE){
            Log.w(TAG,"it has already paused when invoking pause!");
            return;
        }

        if(mediaState != MediaState.RUNNING){
            throwException("pause","RUNNING/PAUSE",mediaState);
        }

        mediaState = MediaState.PAUSE;
        try {
            captureSession.stopRepeating();
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void stop() {

        if(syncLock != null)syncLock.releaseLock();

        mediaState = MediaState.STOP;

        cameraCharacteristics = null;

        try {
            if(captureSession != null){
                captureSession.stopRepeating();
                captureSession.close();
                captureSession = null;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

        if(cameraDevice != null){
            cameraDevice.close();
            cameraDevice = null;
        }

        if(texture != null){
            GLES30.glDeleteTextures(texture.length,texture,0);
            texture = null;
        }

        if(surfaceTexture != null){
            surfaceTexture.release();
            surfaceTexture = null;
        }

        if(innerCaptureView && captureView != null){
            captureView.release();
            captureView = null;
        }

        if(pictureImageReader != null){
            pictureImageReader.close();
            pictureImageReader = null;
        }

        if(captureImageReader != null){
            captureImageReader.close();
            captureImageReader = null;
        }
    }

    @Override
    public void release() {

        if(mediaState != MediaState.STOP){
            throwException("release","STOP",mediaState);
        }

        context = null;

        textureUpdateListener = null;
        takePictureListener = null;
        cameraCaptureListener = null;

        preview = null;
        captureView = null;

        cameraHandler = null;
        glHandler = null;
        manager = null;

        if(syncLock != null){
            syncLock.releaseLock();
            syncLock = null;
        }

        if(executorService != null){
            executorService.shutdownNow();
            executorService = null;
        }

        if (handlerThread != null) {
            handlerThread.quit();
            handlerThread = null;
        }

        mediaState = MediaState.RELEASE;
    }

    /**
     * 纹理跟新接口
     */
    public interface TextureUpdateListener{
        public void textureUpdated(int texture,long timestamp,CameraState cameraState);
    }

    /**
     * 相机录制回调接口
     */
    public interface CameraCaptureListener{
        public void capture(byte[] dataY,byte[] dataU,byte[] dataV,CameraState cameraState);
    }

    /**
     * 拍照回调接口
     */
    public interface TakePictureListener{
        public void picture(Bitmap bitmap,byte[] data,CameraState cameraState);
    }

    /**
     * 纹理创建成功接口回调
     */
    public interface TextureCreateListener{
        public void textureCreated(int texture);
    }

    /**
     * 相机状态信息
     */
    public static class CameraState{
        public int cameraFacing;        //前后摄
        public int displayOrientation;  //显示方向
        public int sensorOrientation;   //传感器方向
        public int screenOrientation;   //屏幕旋转角
        public int rotateAngle;         //旋转角度
    }

    /**
     * 构造者模式,open API
     */
    public static class Builder {

        private CameraCapture cameraCapture;

        public Builder(Context context){
            cameraCapture = new CameraCapture(context);
        }

        /**
         * 设置拍照尺寸
         * @param width
         * @param height
         * @return
         */
        public Builder setPictureSize(int width,int height){
            cameraCapture.pictureSize = new Size(width,height);
            return this;
        }

        /**
         * 设置录制尺寸
         * @param width
         * @param height
         * @param auxiliaryCaptureSizeClass 尺寸计算辅助类，如果传null则默认为SurfaceTexture.class,还可以传递如MediaCodec
         *                                  {@link StreamConfigurationMap#isOutputSupportedFor(Class)}
         * @return
         */
        public Builder setCaptureSize(int width,int height,Class<?> auxiliaryCaptureSizeClass){
            cameraCapture.captureSize = new Size(width,height);
            if(auxiliaryCaptureSizeClass != null){
                cameraCapture.auxiliaryCaptureSizeClass = auxiliaryCaptureSizeClass;
            }
            return this;
        }

        /**
         * 设置预览surface
         * @param preview
         * @return
         */
        public Builder setPreview(Surface preview){
            cameraCapture.preview = preview;
            return this;
        }

        /**
         * 设置capture输出surface，如编码时可传递MediaCodec的surface
         * @param captureView
         * @return
         */
        public Builder captureTargetSurface(Surface captureView){
            cameraCapture.captureView = captureView;
            return this;
        }

        /**
         * 设置预览和录制fps
         * @param fps
         * @return
         */
        public Builder setFps(int fps){
            cameraCapture.fps = fps;
            return this;
        }

        /**
         * 初始化前后摄
         * @param cameraFacing
         * @return
         */
        public Builder setCameraFacing(int cameraFacing){
            cameraCapture.cameraFacing = cameraFacing;
            return this;
        }

        /**
         * 自动创建Texture，捕获数据转成纹理
         * @param glHandler egl相关联的Handler
         * @param textureCreateListener texture创建成功回调
         * @return
         */
        public Builder captureForAutoTexture(Handler glHandler,TextureCreateListener textureCreateListener){
            cameraCapture.glHandler = glHandler;
            cameraCapture.textureCreateListener = textureCreateListener;
            return this;
        }

        /**
         * 开启数据捕获，
         * todo 除了预览和拍照外的其他功能都需要开启
         * @param enableCapture
         * @return
         */
        public Builder enableCapture(boolean enableCapture){
            cameraCapture.enableCapture = enableCapture;
            return this;
        }

        /**
         * 设置纹理更新回调
         * @param textureUpdateListener
         * @return
         */
        public Builder setTextureUpdateListener(TextureUpdateListener textureUpdateListener){
            cameraCapture.textureUpdateListener = textureUpdateListener;
            return this;
        }

        /**
         * 设置拍照回调
         * @param takePictureListener 数据回调
         * @param pictureFormat 数据格式，目前只支持JPEG
         * @return
         */
        public Builder setTakePictureListener(TakePictureListener takePictureListener,int pictureFormat){
            cameraCapture.takePictureListener = takePictureListener;
            if(pictureFormat != ImageFormat.JPEG){
                throw new IllegalArgumentException("only support format: JPEG");
            }
            return this;
        }

        /**
         * 捕获字节原始数据，
         * @param cameraCaptureListener 数据回调接口
         * @param captureFormat 数据格式，目前只支持YUV_420_888
         * todo captureForAutoTexture,captureTargetSurface,captureDataBytes三者互斥，
         * todo 优先级captureTargetSurface > captureDataBytes >  captureForAutoTexture
         * @return
         */
        public Builder captureDataBytes(CameraCaptureListener cameraCaptureListener,int captureFormat){
            cameraCapture.cameraCaptureListener = cameraCaptureListener;
            if(captureFormat != ImageFormat.YUV_420_888){
                throw new IllegalArgumentException("only support format: YUV_420_888");
            }
            return this;
        }

        public CameraCapture build(){
            try{
                return cameraCapture;
            }finally {
                cameraCapture = null;
            }
        }
    }
}
