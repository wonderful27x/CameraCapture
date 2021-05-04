package com.cybercloud.cameracapture.media_component;

import androidx.annotation.IntDef;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author wonderful
 * @date 2021-4-4
 * @version 1.0
 * @descreption media状态，使用注解表示，减少内存开销
 */
@IntDef({
        MediaState.DEFAULT,
        MediaState.IDLE,
        MediaState.PREPARE,
        MediaState.RUNNING,
        MediaState.PAUSE,
        MediaState.STOP,
        MediaState.RELEASE,
})
@Target({ElementType.FIELD,ElementType.METHOD})
@Retention(RetentionPolicy.SOURCE)
public @interface MediaState {
    int DEFAULT = -1;       //保留
    int IDLE = 0;           //空闲状态
    int PREPARE = 1;        //准备状态
    int RUNNING = 2;        //运行状态
    int PAUSE = 3;          //暂停状态
    int STOP = 4;           //停止状态
    int RELEASE = 5;        //销毁状态
}
