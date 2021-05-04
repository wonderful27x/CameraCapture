package com.cybercloud.cameracapture.media_component;

/**
 * @author wonderful
 * @date 2021-4-8
 * @version 1.0
 * @descreption 播放控制接口，实现统一控制
 */
public interface MediaControlInterface {

    /**
     * 准备
     */
    public void prepare();

    /**
     * 开始，开始之前应该先调用prepare
     */
    public void start();

    /**
     * 暂停，暂停之后可以调用start重新开始
     */
    public void pause();

    /**
     * 停止，停止之后必须重新调用prepare、start才能开始
     * 但是stop也可以设计成和release一样不再支持start
     */
    public void stop();

    /**
     * 释放，释放之后表示不再使用，所有资源都已释放，要想开始必须重新调用构造函数
     */
    public void release();
}
