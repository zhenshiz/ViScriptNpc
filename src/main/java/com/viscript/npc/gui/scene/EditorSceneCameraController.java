package com.viscript.npc.gui.scene;

import net.minecraft.util.Mth;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 维护支持环绕、原地转向、平移和缩放操作的编辑器摄像机。
 */
public final class EditorSceneCameraController implements SceneCameraController {
    private static final float ROTATION_SENSITIVITY = (float) Math.toRadians(1.0);
    private static final float PAN_SENSITIVITY = 1.35f;
    private static final float MIN_PITCH = (float) Math.toRadians(-89.5);
    private static final float MAX_PITCH = (float) Math.toRadians(89.5);
    private static final float MIN_DISTANCE = 3.0f;
    private static final float MAX_DISTANCE = 96.0f;
    private static final float ZOOM_BASE = 0.82f;

    private final Vector3f eyePosition = new Vector3f();
    private final Vector3f lookAtPosition = new Vector3f();
    private final Vector3f worldUp = new Vector3f(0.0f, 1.0f, 0.0f);
    private float distance;
    private float yaw;
    private float pitch;

    /**
     * 使用默认的编辑器三分之四视角创建摄像机。
     *
     * @param lookAt   初始世界坐标观察点
     * @param distance 摄像机与观察点之间的初始距离
     */
    public EditorSceneCameraController(Vector3f lookAt, float distance) {
        this(lookAt, distance, (float) Math.toRadians(-135.0), (float) Math.toRadians(25.0));
    }

    /**
     * 使用明确的球面观察角度创建摄像机。
     *
     * @param lookAt   初始世界坐标观察点
     * @param distance 摄像机与观察点之间的初始距离
     * @param yaw      水平环绕角度，单位为弧度
     * @param pitch    垂直环绕角度，单位为弧度
     */
    public EditorSceneCameraController(Vector3f lookAt, float distance, float yaw, float pitch) {
        this.yaw = wrapRadians(yaw);
        this.pitch = Mth.clamp(pitch, MIN_PITCH, MAX_PITCH);
        frame(lookAt, distance);
    }

    @Override
    public void orbit(float dragX, float dragY) {
        updateAngles(dragX, dragY);
        updateEyeFromLookAt();
    }

    @Override
    public void lookInPlace(float dragX, float dragY) {
        Vector3f lookDirection = new Vector3f(lookAtPosition).sub(eyePosition);
        Vector3f pitchAxis = new Vector3f(lookDirection).cross(worldUp);
        if (pitchAxis.lengthSquared() < 1.0E-6f) {
            pitchAxis.set(1.0f, 0.0f, 0.0f);
        } else {
            pitchAxis.normalize();
        }

        float minimumPoleAngle = 0.5f;
        float pitchToUp = (float) Math.toDegrees(lookDirection.angle(worldUp));
        float newPitchToUp = Mth.clamp(
                pitchToUp + dragY,
                minimumPoleAngle,
                180.0f - minimumPoleAngle);
        float pitchRotation = pitchToUp - newPitchToUp;

        lookDirection.rotate(new Quaternionf(new AxisAngle4f(
                (float) Math.toRadians(pitchRotation), pitchAxis)));
        lookDirection.rotate(new Quaternionf(new AxisAngle4f(
                (float) Math.toRadians(-dragX), worldUp)));
        lookAtPosition.set(eyePosition).add(lookDirection);
        synchronizeAnglesFromPose();
    }

    @Override
    public void pan(float dragX, float dragY, float viewportWidth, float viewportHeight) {
        Vector3f forward = new Vector3f(lookAtPosition).sub(eyePosition).normalize();
        Vector3f right = new Vector3f(forward).cross(worldUp).normalize();
        Vector3f up = new Vector3f(right).cross(forward).normalize();
        float viewportPixels = Math.max(1.0f, Math.min(viewportWidth, viewportHeight));
        float worldUnitsPerPixel = distance * PAN_SENSITIVITY / viewportPixels;
        Vector3f translation = right.mul(-dragX * worldUnitsPerPixel)
                .add(up.mul(dragY * worldUnitsPerPixel));
        eyePosition.add(translation);
        lookAtPosition.add(translation);
    }

    @Override
    public void zoom(double scrollDelta) {
        if (scrollDelta == 0.0) {
            return;
        }
        distance = Mth.clamp(
                (float) (distance * Math.pow(ZOOM_BASE, scrollDelta)),
                MIN_DISTANCE,
                MAX_DISTANCE);
        updateEyeFromLookAt();
    }

    @Override
    public void frame(Vector3f lookAt, float distance) {
        lookAtPosition.set(lookAt);
        this.distance = Mth.clamp(distance, MIN_DISTANCE, MAX_DISTANCE);
        updateEyeFromLookAt();
    }

    @Override
    public Vector3f getEyePosition() {
        return new Vector3f(eyePosition);
    }

    @Override
    public Vector3f getLookAtPosition() {
        return new Vector3f(lookAtPosition);
    }

    @Override
    public Vector3f getWorldUp() {
        return new Vector3f(worldUp);
    }

    @Override
    public float getDistance() {
        return distance;
    }

    private void updateAngles(float dragX, float dragY) {
        yaw = wrapRadians(yaw + dragX * ROTATION_SENSITIVITY);
        pitch = Mth.clamp(pitch + dragY * ROTATION_SENSITIVITY, MIN_PITCH, MAX_PITCH);
    }

    private void updateEyeFromLookAt() {
        eyePosition.set(lookAtPosition).add(createOffset());
    }

    private Vector3f createOffset() {
        float horizontalDistance = (float) Math.cos(pitch) * distance;
        return new Vector3f(
                (float) Math.cos(yaw) * horizontalDistance,
                (float) Math.sin(pitch) * distance,
                (float) Math.sin(yaw) * horizontalDistance);
    }

    private void synchronizeAnglesFromPose() {
        Vector3f offset = new Vector3f(eyePosition).sub(lookAtPosition);
        distance = offset.length();
        yaw = wrapRadians((float) Math.atan2(offset.z, offset.x));
        pitch = Mth.clamp(
                (float) Math.atan2(offset.y, Math.sqrt(offset.x * offset.x + offset.z * offset.z)),
                MIN_PITCH,
                MAX_PITCH);
    }

    private static float wrapRadians(float angle) {
        return (float) Math.toRadians(Mth.wrapDegrees(Math.toDegrees(angle)));
    }
}
