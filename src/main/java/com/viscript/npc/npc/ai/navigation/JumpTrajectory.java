package com.viscript.npc.npc.ai.navigation;

import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/** 使用普通实体重力和空气阻力预测跳跃；不改变位置或绕过碰撞。 */
public record JumpTrajectory(Vec3 velocity, List<Vec3> samples, int horizontalDelay) {
    private static final double AIR_DRAG = .91;
    private static final double PLAYER_GROUND_DRAG = .6 * AIR_DRAG;
    private static final double PLAYER_GROUND_INPUT = .1 * 1.3 * .98;
    private static final double PLAYER_AIR_INPUT = .026 * .98;
    private static final double PLAYER_SPRINT_BOOST = .2;
    private static final double PLAYER_TAKEOFF_SPEED = playerTakeoffSpeed();

    public static JumpTrajectory calculate(Vec3 start, Vec3 end, double maxSpeed,
                                            double jumpPower, Predicate<Vec3> clear) {
        int ticks = flightTicks(jumpPower, end.y - start.y);
        if (ticks == 0 || end.y - start.y > 1.25 || end.y - start.y < -3) return null;
        double distance = start.subtract(end).horizontalDistance();
        if (distance > maximumDistance(maxSpeed, jumpPower, end.y - start.y) + 1e-6) return null;
        // 紧邻整格台阶时先竖直起跳，再施加水平动量，避免脚部提前撞上方块侧面。
        int maxDelay = end.y > start.y ? Math.min(4, ticks - 1) : 0;
        for (int delay = 0; delay <= maxDelay; delay++) {
            double dragSum = (1 - Math.pow(AIR_DRAG, ticks - delay)) / (1 - AIR_DRAG);
            Vec3 velocity = new Vec3((end.x - start.x) / dragSum, jumpPower, (end.z - start.z) / dragSum);
            if (velocity.horizontalDistance() > maxSpeed || distance > playerDistance(ticks - delay) + 1e-6) continue;
            var trajectory = simulate(start, end, velocity, ticks, delay, clear);
            if (trajectory != null) return trajectory;
        }
        return null;
    }

    /**
     * 返回给定落差的水平位移上限，取 NPC 速度约束和普通玩家疾跑跳跃范围中的较小值。
     *
     * @param maxSpeed NPC 可施加的最大水平初速度，单位为格每 tick
     * @param jumpPower 向上的初速度，单位为格每 tick
     * @param heightDifference 落点脚部相对起点的高度，单位为格
     * @return 不含起跳平台及落地平台边缘距离的最大水平位移；没有下降交点时为零
     */
    public static double maximumDistance(double maxSpeed, double jumpPower, double heightDifference) {
        int ticks = flightTicks(jumpPower, heightDifference);
        return ticks == 0 ? 0 : Math.min(maxSpeed * (1 - Math.pow(AIR_DRAG, ticks)) / (1 - AIR_DRAG), playerDistance(ticks));
    }

    private static int flightTicks(double jumpPower, double heightDifference) {
        double height = 0, vy = jumpPower;
        for (int i = 1; i <= 40; i++) {
            height += vy;
            vy = (vy - .08) * .98;
            if (vy < 0 && height <= heightDifference + .05) return i;
        }
        return 0;
    }

    private static double playerTakeoffSpeed() {
        // 原版 Player/LivingEntity：普通地面、无药水、连续疾跑跳的稳定动量作为能力上界。
        int ticks = flightTicks(.42, 0);
        double retained = PLAYER_GROUND_DRAG * Math.pow(AIR_DRAG, ticks - 1);
        double momentum = ((PLAYER_SPRINT_BOOST + PLAYER_GROUND_INPUT) * retained
                + PLAYER_AIR_INPUT * AIR_DRAG * (1 - Math.pow(AIR_DRAG, ticks - 1)) / (1 - AIR_DRAG)) / (1 - retained);
        return momentum + PLAYER_SPRINT_BOOST + PLAYER_GROUND_INPUT;
    }

    private static double playerDistance(int ticks) {
        double speed = PLAYER_TAKEOFF_SPEED, distance = 0;
        for (int tick = 0; tick < ticks; tick++) {
            if (tick > 0) speed += PLAYER_AIR_INPUT;
            distance += speed;
            speed *= tick == 0 ? PLAYER_GROUND_DRAG : AIR_DRAG;
        }
        return distance;
    }

    private static JumpTrajectory simulate(Vec3 start, Vec3 end, Vec3 velocity, int ticks, int delay,
                                            Predicate<Vec3> clear) {
        List<Vec3> samples = new ArrayList<>();
        Vec3 pos = start, motion = new Vec3(0, velocity.y, 0);
        for (int i = 0; i < ticks; i++) {
            if (i == delay) motion = new Vec3(velocity.x, motion.y, velocity.z);
            Vec3 next = pos.add(motion);
            if (i == ticks - 1 && next.y < end.y) {
                // 原版先解析竖直碰撞；本 tick 水平移动之前就必须已经压住落地平台。
                if (clear.test(new Vec3(pos.x, end.y - .01, pos.z))) return null;
                next = new Vec3(next.x, end.y, next.z);
            }
            int subdivisions = Math.max(2, (int) Math.ceil(pos.distanceTo(next) / .15));
            for (int j = 1; j <= subdivisions; j++) {
                if (!clear.test(pos.lerp(next, (double) j / subdivisions))) return null;
            }
            samples.add(next);
            pos = next;
            motion = new Vec3(motion.x * .91, (motion.y - .08) * .98, motion.z * .91);
        }
        return new JumpTrajectory(velocity, List.copyOf(samples), delay);
    }
}
