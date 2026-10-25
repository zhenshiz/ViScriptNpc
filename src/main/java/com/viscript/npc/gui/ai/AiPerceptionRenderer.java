package com.viscript.npc.gui.ai;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.npc.ai.data.AiPerceptionVolume;
import net.minecraft.world.phys.Vec3;

/** 将服务器实际感知体绘制为橙色近身圆圈与黄色扇形视野。 */
public final class AiPerceptionRenderer {
    private AiPerceptionRenderer() {}

    /**
     * 绘制与脚部位置判定一致的水平轮廓及垂直边界。
     *
     * @param pose 场景中的世界坐标变换
     * @param buffer 场景线框缓冲
     * @param volume 服务器同步的感知体
     */
    public static void render(PoseStack pose, VertexConsumer buffer, AiPerceptionVolume volume) {
        if (volume.hasRange()) {
            for (int i = 0; i < 64; i++) {
                double a = i * Math.PI / 32, b = (i + 1) * Math.PI / 32;
                for (double y : new double[]{.04, -volume.height / 2, volume.height / 2})
                    line(pose, buffer, volume.origin.add(Math.cos(a) * volume.range, y, Math.sin(a) * volume.range),
                            volume.origin.add(Math.cos(b) * volume.range, y, Math.sin(b) * volume.range), 1, .55f, .12f);
                if (i % 16 == 0) line(pose, buffer,
                        volume.origin.add(Math.cos(a) * volume.range, -volume.height / 2, Math.sin(a) * volume.range),
                        volume.origin.add(Math.cos(a) * volume.range, volume.height / 2, Math.sin(a) * volume.range), 1, .55f, .12f);
            }
        }
        if (volume.hasView() && volume.viewRadius > 0 && volume.viewAngle > 0) {
            double halfAngle = volume.viewAngle / 2, height = volume.height / 2;
            int segments = Math.max(1, (int) Math.ceil(volume.viewAngle / 360 * 64));
            for (double y : new double[]{.04, -height, height}) {
                for (int i = 0; i < segments; i++)
                    line(pose, buffer, volume.viewPoint(-halfAngle + volume.viewAngle * i / segments, y),
                            volume.viewPoint(-halfAngle + volume.viewAngle * (i + 1) / segments, y), 1, .95f, .15f);
                if (volume.viewAngle < 360) {
                    line(pose, buffer, volume.point(0, 0, y), volume.viewPoint(-halfAngle, y), 1, .95f, .15f);
                    line(pose, buffer, volume.point(0, 0, y), volume.viewPoint(halfAngle, y), 1, .95f, .15f);
                }
            }
            for (int i = 0; i <= 4; i++) {
                double angle = -halfAngle + volume.viewAngle * i / 4;
                line(pose, buffer, volume.viewPoint(angle, -height), volume.viewPoint(angle, height), 1, .95f, .15f);
            }
            if (volume.viewAngle < 360)
                line(pose, buffer, volume.point(0, 0, -height), volume.point(0, 0, height), 1, .95f, .15f);
            line(pose, buffer, volume.point(0, 0, .05), volume.viewPoint(0, .05), 1, .95f, .15f);
        }
    }

    public static void line(PoseStack pose, VertexConsumer buffer, Vec3 from, Vec3 to, float r, float g, float b) {
        Vec3 normal = to.subtract(from).normalize();
        if (normal.lengthSqr() < .000001) return;
        buffer.addVertex(pose.last().pose(), (float) from.x, (float) from.y, (float) from.z)
                .setColor(r, g, b, 1).setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
        buffer.addVertex(pose.last().pose(), (float) to.x, (float) to.y, (float) to.z)
                .setColor(r, g, b, 1).setNormal(pose.last(), (float) normal.x, (float) normal.y, (float) normal.z);
    }
}
