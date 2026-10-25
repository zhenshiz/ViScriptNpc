package com.viscript.npc.npc.ai.data;

import com.lowdragmc.lowdraglib2.syncdata.IPersistedSerializable;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import net.minecraft.world.phys.Vec3;

/** 服务器感知判定与客户端线框共用的圆柱范围和水平扇形视野。 */
public class AiPerceptionVolume implements IPersistedSerializable {
    @Persisted public String mode = "range";
    @Persisted public Vec3 origin = Vec3.ZERO;
    @Persisted public Vec3 forward = new Vec3(0, 0, 1);
    @Persisted public double range;
    @Persisted public double viewRadius;
    /** 扇形的完整夹角，单位为度；左右各占一半。 */
    @Persisted public double viewAngle = 90;
    @Persisted public double height;
    @Persisted public boolean requireSight;

    /**
     * 检查实体脚部坐标是否位于感知范围；遮挡由服务器另行检查。
     *
     * @param position 世界中的脚部坐标
     * @return 坐标位于启用的圆柱或扇形体内时返回 {@code true}
     */
    public boolean contains(Vec3 position) {
        Vec3 delta = position.subtract(origin);
        if (Math.abs(delta.y) > height / 2) return false;
        if (hasRange() && delta.horizontalDistanceSqr() <= range * range) return true;
        double distanceSquared = delta.horizontalDistanceSqr();
        if (!hasView() || viewRadius <= 0 || viewAngle <= 0 || distanceSquared > viewRadius * viewRadius + 1e-9) return false;
        double projection = delta.x * forward.x + delta.z * forward.z;
        return projection + 1e-9 >= Math.sqrt(distanceSquared) * Math.cos(Math.toRadians(viewAngle / 2));
    }
    /** @return 是否启用周围圆柱感知。 */
    public boolean hasRange() { return !mode.equals("view"); }
    /** @return 是否启用扇形视野。 */
    public boolean hasView() { return !mode.equals("range"); }
    /**
     * 将局部水平偏移和高度转换为世界坐标。
     *
     * @param along 从 NPC 向前的距离
     * @param side 向右的距离
     * @param y 相对于脚部高度的偏移
     * @return 世界坐标中的位置
     */
    public Vec3 point(double along, double side, double y) {
        return origin.add(forward.scale(along)).add(forward.z * side, y, -forward.x * side);
    }

    /**
     * 获取视野圆弧上的位置，角度相对于水平朝向。
     *
     * @param angle 相对于朝向的偏角，单位为度
     * @param y 相对于脚部高度的偏移
     * @return 半径为 {@code viewRadius} 的圆弧位置
     */
    public Vec3 viewPoint(double angle, double y) {
        double radians = Math.toRadians(angle);
        return point(Math.cos(radians) * viewRadius, Math.sin(radians) * viewRadius, y);
    }
}
