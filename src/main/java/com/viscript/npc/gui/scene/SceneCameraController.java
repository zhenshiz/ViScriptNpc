package com.viscript.npc.gui.scene;

import org.joml.Vector3f;

/**
 * 控制编辑器场景视口使用的摄像机姿态。
 *
 * <p>该控制器将摄像机交互与场景渲染分离，使视口从独立测试界面迁移到编辑器后仍能保留相同的输入语义。
 */
public interface SceneCameraController {
    /**
     * 让摄像机位置围绕当前观察点旋转。
     *
     * @param dragX 水平方向的指针移动量，单位为界面像素
     * @param dragY 垂直方向的指针移动量，单位为界面像素
     */
    void orbit(float dragX, float dragY);

    /**
     * 在保持摄像机位置不变的情况下旋转观察方向。
     *
     * @param dragX 水平方向的指针移动量，单位为界面像素
     * @param dragY 垂直方向的指针移动量，单位为界面像素
     */
    void lookInPlace(float dragX, float dragY);

    /**
     * 沿当前屏幕平面同时平移摄像机和观察点。
     *
     * @param dragX          水平方向的指针移动量，单位为界面像素
     * @param dragY          垂直方向的指针移动量，单位为界面像素
     * @param viewportWidth  视口宽度，单位为界面像素
     * @param viewportHeight 视口高度，单位为界面像素
     */
    void pan(float dragX, float dragY, float viewportWidth, float viewportHeight);

    /**
     * 让摄像机靠近或远离当前观察点。
     *
     * @param scrollDelta 带方向的鼠标滚轮移动量，正值表示靠近观察点
     */
    void zoom(double scrollDelta);

    /**
     * 在不改变当前观察角度的情况下，将视图框定到新的观察点。
     *
     * @param lookAt   放置在视图中心的世界坐标观察点
     * @param distance 摄像机与观察点之间的距离
     */
    void frame(Vector3f lookAt, float distance);

    /**
     * 获取当前摄像机的世界坐标位置。
     *
     * @return 摄像机位置的防御性副本
     */
    Vector3f getEyePosition();

    /**
     * 获取摄像机正在观察的世界坐标位置。
     *
     * @return 观察点的防御性副本
     */
    Vector3f getLookAtPosition();

    /**
     * 获取用于确定摄像机朝向的世界坐标上方向。
     *
     * @return 上方向的防御性副本
     */
    Vector3f getWorldUp();

    /**
     * 获取摄像机与观察点之间的距离。
     *
     * @return 世界坐标单位下的摄像机距离
     */
    float getDistance();
}
