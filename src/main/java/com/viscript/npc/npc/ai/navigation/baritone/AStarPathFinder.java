/* Adapted from Baritone v1.11.3 AStarPathFinder/Moves/MovementParkour, LGPL-3.0-or-later.
 * Client calculation contexts are replaced with immutable entity collision snapshots.
 * Only complete routes are returned. See META-INF/licenses/baritone/NOTICE.txt.
 */
package com.viscript.npc.npc.ai.navigation.baritone;

import com.viscript.npc.npc.ai.navigation.JumpTrajectory;
import com.viscript.npc.npc.ai.navigation.NavigationSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 在有界快照内执行 Baritone 的堆搜索与代价松弛，输出明确的运动步骤。 */
public final class AStarPathFinder {
    public enum Movement { WALK, STEP_UP, DESCEND, JUMP, SWIM, CLIMB }
    public record Step(Vec3 position, Movement movement) {}
    public record Result(List<Step> steps, String reason, int expanded, long nanos) {}
    private static final int[][] DIRECTIONS = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private static final int[] HEIGHT_OFFSETS = {0, 1, 2, -1, -2, -3, -4, -5, -6};
    private static final java.lang.management.ThreadMXBean CPU = java.lang.management.ManagementFactory.getThreadMXBean();

    public static Result calculate(NavigationSnapshot world, Vec3 start, Vec3 goal, double tolerance,
                                   BooleanSupplier cancelled) {
        long began = System.nanoTime();
        long cpuBegan = CPU.isCurrentThreadCpuTimeSupported() && CPU.isThreadCpuTimeEnabled() ? CPU.getCurrentThreadCpuTime() : -1;
        var query = world.query();
        var nodes = new Long2ObjectOpenHashMap<PathNode>();
        var heap = new BinaryHeapOpenSet();
        PathNode first = new PathNode((int) Math.floor(start.x), (int) Math.round(start.y * 2), (int) Math.floor(start.z), goal);
        first.cost = 0;
        first.combinedCost = first.estimatedCostToGoal;
        nodes.put(first.key(), first);
        heap.insert(first);
        double platformEdge = .5 + world.width / 2 - .02;
        double maxJumpDistance = JumpTrajectory.maximumDistance(world.speed * 2.5, world.jumpPower, -3)
                + platformEdge * 2 * Math.sqrt(2);
        int jumpRadius = (int) Math.ceil(maxJumpDistance);
        int expanded = 0;
        while (!heap.isEmpty() && expanded < 20000) {
            // 线程被其他任务抢占时不消耗工作预算；仍保留一秒墙钟上限和节点上限。
            if ((expanded & 63) == 0 && (cancelled.getAsBoolean() || System.nanoTime() - began > 1_000_000_000L
                    || (cpuBegan >= 0 ? CPU.getCurrentThreadCpuTime() - cpuBegan : System.nanoTime() - began) > 150_000_000L))
                return new Result(List.of(), cancelled.getAsBoolean() ? "cancelled" : "search_timeout", expanded, System.nanoTime() - began);
            PathNode current = heap.removeLowest();
            expanded++;
            Vec3 from = current.position();
            if (Math.abs(from.y - goal.y) < .6 && from.subtract(goal).horizontalDistance() <= tolerance) {
                List<Step> steps = new ArrayList<>();
                for (PathNode node = current; node.previous != null; node = node.previous) {
                    if (node.takeoff != null) {
                        steps.add(new Step(node.position(), Movement.WALK));
                        steps.add(new Step(node.landing, Movement.JUMP));
                        steps.add(new Step(node.takeoff, Movement.WALK));
                    } else steps.add(new Step(node.position(), node.movement));
                }
                Collections.reverse(steps);
                return new Result(List.copyOf(steps), "", expanded, System.nanoTime() - began);
            }
            if (world.swimmable(from) || world.climbable(from)) for (double dy : new double[]{.5, -.5}) {
                Vec3 to = from.add(0, dy, 0);
                if (query.occupiable(to) && query.traverse(from, to))
                    relax(nodes, heap, current, to, goal, ActionCosts.WALK_ONE_BLOCK_COST,
                            world.climbable(from) ? Movement.CLIMB : Movement.SWIM);
            }
            for (int[] dir : DIRECTIONS) {
                boolean diagonal = dir[0] != 0 && dir[1] != 0;
                for (int dy : HEIGHT_OFFSETS) {
                    Vec3 to = new Vec3(current.x + dir[0] + .5, (current.y + dy) * .5, current.z + dir[1] + .5);
                    if (!query.occupiable(to)) continue;
                    boolean swim = world.swimmable(from) || world.swimmable(to);
                    boolean climb = world.climbable(from) || world.climbable(to);
                    if ((swim || climb) && Math.abs(dy) > 2) continue;
                    boolean jump = !swim && !climb && dy * .5 > world.stepHeight;
                    if (jump && (!world.canJump || JumpTrajectory.calculate(from, to,
                            world.speed * 2.5, world.jumpPower, query::clear) == null)) continue;
                    if (!jump && !query.traverse(from, to)) continue;
                    double cost = ActionCosts.WALK_ONE_BLOCK_COST * (diagonal ? Math.sqrt(2) : 1);
                    if (dy > 0) cost += ActionCosts.JUMP_ONE_BLOCK_COST;
                    if (swim || climb) cost += 2;
                    if (dy < 0) cost += ActionCosts.FALL_N_BLOCKS_COST[Math.max(1, -dy / 2)];
                    relax(nodes, heap, current, to, goal, cost,
                            swim ? Movement.SWIM : climb ? Movement.CLIMB : jump ? Movement.JUMP : dy > 0 ? Movement.STEP_UP : dy < 0 ? Movement.DESCEND : Movement.WALK);
                    break;
                }
                if (!world.canJump || world.swimmable(from) || world.climbable(from) || query.standable(from.add(dir[0], 0, dir[1]))
                        || world.swimmable(from.add(dir[0], 0, dir[1])) || world.climbable(from.add(dir[0], 0, dir[1]))) continue;
                // 紧邻障碍的抬升已由上面的台阶/跳跃处理；跨越枚举只用于有净空的缺口。
                if (!query.clear(from.add(dir[0], 0, dir[1]))) continue;
                if (diagonal) {
                    // 两轴独立枚举；半径由玩家跳跃能力、落差和可站立的平台边缘决定。
                    for (int dx = 1; dx <= jumpRadius; dx++) for (int dz = 1; dz <= jumpRadius; dz++) {
                        int distanceSquared = dx * dx + dz * dz;
                        if (distanceSquared <= 2 || distanceSquared > maxJumpDistance * maxJumpDistance) continue;
                        addParkour(world, query, nodes, heap, current, goal, dir[0] * dx, dir[1] * dz);
                    }
                } else for (int distance = 2; distance <= jumpRadius; distance++) {
                    addParkour(world, query, nodes, heap, current, goal, dir[0] * distance, dir[1] * distance);
                }
            }
        }
        return new Result(List.of(), "unreachable", expanded, System.nanoTime() - began);
    }

    private static void addParkour(NavigationSnapshot world, NavigationSnapshot.Query query, Long2ObjectOpenHashMap<PathNode> nodes,
                                   BinaryHeapOpenSet heap, PathNode current, Vec3 goal, int dx, int dz) {
        Vec3 from = current.position();
        double distance = Math.hypot(dx, dz);
        for (int dy : HEIGHT_OFFSETS) {
            Vec3 to = from.add(dx, dy * .5, dz);
            if (!query.standable(to)) continue;
            double reach = JumpTrajectory.maximumDistance(world.speed * 2.5, world.jumpPower, dy * .5);
            if (JumpTrajectory.calculate(from, to, world.speed * 2.5, world.jumpPower, query::clear) != null) {
                double cost = (distance > 3 ? ActionCosts.SPRINT_ONE_BLOCK_COST : ActionCosts.WALK_ONE_BLOCK_COST) * distance;
                relax(nodes, heap, current, to, goal, cost + 2 + (dy > 0 ? ActionCosts.JUMP_ONE_BLOCK_COST : 0), Movement.JUMP);
            } else {
                // 走到平台内侧边缘起跳，落在对面边缘后再走向中心。
                double platformEdge = .5 + world.width / 2 - .02;
                Vec3 edge = new Vec3(Math.copySign(Math.min(platformEdge, Math.abs(dx) * .5), dx), 0,
                        Math.copySign(Math.min(platformEdge, Math.abs(dz) * .5), dz));
                Vec3 takeoff = from.add(edge);
                Vec3 landing = to.subtract(new Vec3(Math.signum(dx) * .5, 0, Math.signum(dz) * .5));
                if (takeoff.subtract(landing).horizontalDistance() > reach + 1e-6
                        || !query.standable(takeoff) || !query.standable(landing)
                        || !query.traverse(from, takeoff) || !query.traverse(landing, to)) continue;
                if (JumpTrajectory.calculate(takeoff, landing, world.speed * 2.5, world.jumpPower, query::clear) == null) continue;
                double cost = ActionCosts.SPRINT_ONE_BLOCK_COST * distance + 2 + (dy > 0 ? ActionCosts.JUMP_ONE_BLOCK_COST : 0);
                relax(nodes, heap, current, to, goal, cost, Movement.JUMP, takeoff, landing);
            }
        }
    }

    private static void relax(Long2ObjectOpenHashMap<PathNode> nodes, BinaryHeapOpenSet heap, PathNode current,
                              Vec3 to, Vec3 goal, double actionCost, Movement movement) {
        relax(nodes, heap, current, to, goal, actionCost, movement, null, null);
    }

    private static void relax(Long2ObjectOpenHashMap<PathNode> nodes, BinaryHeapOpenSet heap, PathNode current,
                              Vec3 to, Vec3 goal, double actionCost, Movement movement, Vec3 takeoff, Vec3 landing) {
        int x = (int) Math.floor(to.x), y = (int) Math.round(to.y * 2), z = (int) Math.floor(to.z);
        PathNode neighbor = nodes.computeIfAbsent(BlockPos.asLong(x, y, z), ignored -> new PathNode(x, y, z, goal));
        double tentativeCost = current.cost + actionCost;
        if (neighbor.cost - tentativeCost > .01) {
            neighbor.previous = current;
            neighbor.movement = movement;
            neighbor.takeoff = takeoff;
            neighbor.landing = landing;
            neighbor.cost = tentativeCost;
            neighbor.combinedCost = tentativeCost + neighbor.estimatedCostToGoal;
            if (neighbor.isOpen()) heap.update(neighbor); else heap.insert(neighbor);
        }
    }
}
