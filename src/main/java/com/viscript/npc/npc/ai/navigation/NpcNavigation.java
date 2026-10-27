package com.viscript.npc.npc.ai.navigation;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.navigation.baritone.AStarPathFinder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/** 每个 NPC 的导航控制器；请求排队、搜索与实体物理分别在对应线程执行。 */
@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public final class NpcNavigation {
    private static final ArrayDeque<NpcNavigation> PENDING = new ArrayDeque<>();
    private static final ThreadPoolExecutor SEARCH = new ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
                Thread thread = new Thread(runnable, "ViScriptNpc-Baritone");
                thread.setDaemon(true);
                return thread;
            });
    private final CustomNpc npc;
    private volatile long generation;
    private record Completed(long generation, AStarPathFinder.Result result) {}
    private final java.util.concurrent.atomic.AtomicReference<Completed> result = new java.util.concurrent.atomic.AtomicReference<>();
    private static volatile long serverEpoch;
    private static int lastDispatchCount;
    public static int lastDispatchCount() { return lastDispatchCount; }
    private NavigationStatus status = NavigationStatus.IDLE;
    private String reason = "";
    private Vec3 goal = Vec3.ZERO;
    private NavigationOptions options = NavigationOptions.GROUND;
    private NavigationSnapshot.Builder capture;
    private Vec3 lastPosition = Vec3.ZERO;
    private double tolerance = .75;
    private List<AStarPathFinder.Step> steps = List.of();
    private int index, stuckTicks, age, replans;
    private boolean jumping;
    private Vec3 pendingJumpVelocity;
    private long horizontalLaunchTick;
    private volatile long requestedNanos, queueNanos, searchNanos;
    private long snapshotNanos, coalescedRequests;
    private long requests, failedRequests;

    public NpcNavigation(CustomNpc npc) { this.npc = npc; }
    public NavigationStatus status() { return status; }
    public String reason() { return reason; }
    public List<AStarPathFinder.Step> path() { return steps; }
    public long queueNanos() { return queueNanos; }
    public long searchNanos() { return searchNanos; }
    public long requests() { return requests; }
    public long failedRequests() { return failedRequests; }
    public long coalescedRequests() { return coalescedRequests; }
    public long snapshotNanos() { return snapshotNanos; }
    public static int pendingCount() { return PENDING.size() + SEARCH.getQueue().size(); }

    /**
     * 提交一个只使用已加载区块的导航请求。
     *
     * @param target 有限世界坐标，距 NPC 不超过 48 格
     * @param movementSpeed 正数移动速度属性值，直接替换本次导航的基础速度
     * @param arrivalTolerance 正数水平到达容差
     * @return 参数有效且队列接受请求时返回 <code>true</code>
     */
    public boolean request(Vec3 target, double movementSpeed, double arrivalTolerance) {
        return request(target, movementSpeed, arrivalTolerance, NavigationOptions.GROUND);
    }

    public boolean request(Vec3 target, double movementSpeed, double arrivalTolerance, NavigationOptions capabilities) {
        requests++;
        if (!(npc.level() instanceof ServerLevel) || !npc.isAlive() || !npc.getNpcAI().isEnabled()
                || !Double.isFinite(target.lengthSqr()) || target.distanceToSqr(npc.position()) > 48 * 48
                || !Double.isFinite(movementSpeed) || movementSpeed <= 0
                || !Double.isFinite(arrivalTolerance) || arrivalTolerance <= 0 || capabilities == null) { failedRequests++; return false; }
        double acceptedTolerance = Math.max(.55, arrivalTolerance);
        if ((status == NavigationStatus.COMPUTING || status == NavigationStatus.MOVING) && goal.equals(target)
                && tolerance == acceptedTolerance && options.equals(capabilities)
                && npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getBaseValue() == movementSpeed) {
            coalescedRequests++; return true;
        }
        boolean queued = PENDING.contains(this);
        if (queued) {
            // 尚未提交搜索的请求原位更新，避免移动目标反复排到队尾。
            generation++; result.set(null); capture = null; steps = List.of(); stopControls(); coalescedRequests++;
        } else cancel();
        goal = target;
        options = capabilities;
        npc.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).setBaseValue(movementSpeed);
        tolerance = acceptedTolerance;
        replans = 0;
        if (!queued) enqueue();
        return status == NavigationStatus.COMPUTING;
    }

    private void enqueue() {
        if (PENDING.size() >= 64) { fail("queue_full"); return; }
        status = NavigationStatus.COMPUTING;
        reason = "";
        result.set(null);
        capture = null; snapshotNanos = 0;
        requestedNanos = System.nanoTime();
        PENDING.remove(this);
        PENDING.addLast(this);
    }

    @SubscribeEvent
    public static void schedule(ServerTickEvent.Post event) {
        lastDispatchCount = 0;
        // 按时间预算分片生成，多份小快照同 tick 提交；重叠区域只读取一次地形。
        long deadline = System.nanoTime() + 4_000_000L;
        var terrain = new java.util.IdentityHashMap<ServerLevel, NavigationTerrain>();
        for (int submitted = 0; submitted < 8 && !PENDING.isEmpty() && System.nanoTime() < deadline;) {
            if (SEARCH.getQueue().remainingCapacity() == 0) break;
            NpcNavigation navigation = PENDING.peekFirst();
            if (navigation.npc.isRemoved() || navigation.npc.level().getServer() != event.getServer()
                    || navigation.status != NavigationStatus.COMPUTING) { navigation.cancel(); continue; }
            long request = navigation.generation, epoch = serverEpoch;
            try {
                long began = System.nanoTime();
                if (navigation.capture == null) navigation.capture = new NavigationSnapshot.Builder(navigation.npc, navigation.goal, navigation.options);
                boolean complete = navigation.capture.advance(terrain.computeIfAbsent((ServerLevel) navigation.npc.level(), NavigationTerrain::new), deadline);
                navigation.snapshotNanos += System.nanoTime() - began;
                if (!complete) break;
                NavigationSnapshot snapshot = navigation.capture.finish(); navigation.capture = null;
                PENDING.removeFirst(); submitted++; lastDispatchCount++;
                Vec3 start = navigation.npc.position(), target = navigation.goal;
                double tolerance = navigation.tolerance;
                long requested = navigation.requestedNanos;
                SEARCH.execute(new SearchJob(navigation, () -> {
                    if (navigation.generation == request) navigation.queueNanos = System.nanoTime() - requested;
                    AStarPathFinder.Result calculated;
                    try {
                        calculated = AStarPathFinder.calculate(snapshot, start, target, tolerance,
                                () -> navigation.generation != request || epoch != serverEpoch);
                    } catch (RuntimeException exception) {
                        ViScriptNpc.LOGGER.error("NPC navigation calculation failed", exception);
                        calculated = new AStarPathFinder.Result(List.of(), "search_error", 0, 0);
                    }
                    if (!calculated.reason().isEmpty() && !calculated.reason().equals("cancelled"))
                        ViScriptNpc.LOGGER.debug("NPC navigation search: {} after {} nodes, {} ms", calculated.reason(), calculated.expanded(), calculated.nanos() / 1_000_000d);
                    if (navigation.generation == request && epoch == serverEpoch) navigation.result.accumulateAndGet(new Completed(request, calculated), (old, next) ->
                            navigation.generation == request && (old == null || old.generation <= request) ? next : old);
                }));
            } catch (RuntimeException exception) {
                navigation.fail("snapshot_error");
                ViScriptNpc.LOGGER.warn("NPC navigation request rejected", exception);
            }
        }
    }

    private record SearchJob(NpcNavigation owner, Runnable task) implements Runnable {
        @Override public void run() { task.run(); }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        serverEpoch++;
        for (NpcNavigation navigation : List.copyOf(PENDING)) navigation.cancel();
        PENDING.clear();
        SEARCH.getQueue().clear();
    }

    public void tick() {
        if (pendingJumpVelocity != null && npc.level().getGameTime() >= horizontalLaunchTick) {
            if (!npc.onGround()) {
                npc.setDeltaMovement(pendingJumpVelocity.x, npc.getDeltaMovement().y, pendingJumpVelocity.z);
                npc.hasImpulse = true;
            }
            pendingJumpVelocity = null;
        }
        if (status == NavigationStatus.COMPUTING && result.get() != null) {
            Completed delivery = result.getAndSet(null);
            if (delivery.generation != generation) return;
            AStarPathFinder.Result completed = delivery.result;
            searchNanos = completed.nanos();
            if (!completed.reason().isEmpty()) { fail(completed.reason()); return; }
            steps = completed.steps();
            index = age = stuckTicks = 0;
            lastPosition = npc.position();
            status = NavigationStatus.MOVING;
        }
        if (status != NavigationStatus.MOVING) return;
        if (++age > 600) { fail("move_timeout"); return; }
        if (jumping) {
            if (!npc.onGround()) return;
            jumping = false;
        }
        if (index >= steps.size()) {
            if (Math.abs(npc.getY() - goal.y) <= .75
                    && npc.position().subtract(goal).horizontalDistance() <= tolerance) {
                stopControls();
                status = NavigationStatus.ARRIVED;
            } else replan();
            return;
        }
        var step = steps.get(index);
        Vec3 point = step.position();
        boolean medium = step.movement() == AStarPathFinder.Movement.SWIM || step.movement() == AStarPathFinder.Movement.CLIMB;
        boolean preciseTakeoff = step.movement() == AStarPathFinder.Movement.WALK && index + 1 < steps.size()
                && steps.get(index + 1).movement() == AStarPathFinder.Movement.JUMP;
        double horizontalDistance = npc.position().subtract(point).horizontalDistance();
        boolean nearJump = preciseTakeoff || (step.movement() == AStarPathFinder.Movement.WALK
                && ((index + 2 < steps.size() && steps.get(index + 2).movement() == AStarPathFinder.Movement.JUMP)
                || (index > 0 && steps.get(index - 1).movement() == AStarPathFinder.Movement.JUMP)));
        boolean preciseGround = nearJump && npc.onGround() && horizontalDistance < 1.25 && Math.abs(npc.getY() - point.y) < .1;
        if (Math.abs(npc.getY() - point.y) < (medium ? .15 : .6) && horizontalDistance < (preciseTakeoff ? .01 : medium ? .22 : .3)) {
            if (preciseTakeoff || preciseGround) stopControls();
            index++;
            stuckTicks = 0;
            return;
        }
        if (npc.position().distanceToSqr(lastPosition) < .0004) stuckTicks++; else stuckTicks = 0;
        lastPosition = npc.position();
        if (stuckTicks > 30) { replan(); return; }
        openDoors(point);
        if (!clearLoaded(point)) { replan(); return; }
        if (!medium && step.movement() != AStarPathFinder.Movement.JUMP && !canStandAt(point)) { replan(); return; }
        if (medium) {
            npc.setMoveControlForAi(new MoveControl(npc)); npc.setSpeed(0); npc.setZza(0); npc.setXxa(0);
            Vec3 delta = point.subtract(npc.position());
            double speed = Math.min(.15, npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * .4);
            Vec3 horizontal = delta.multiply(1, 0, 1).normalize().scale(Math.min(speed, delta.horizontalDistance() * .5));
            double vertical = npc.isInWater() ? Math.clamp(delta.y * .4, -.12, .12) + .005
                    : step.movement() == AStarPathFinder.Movement.SWIM && npc.horizontalCollision && delta.y > 0
                    ? Math.clamp(delta.y * .5 + .08, .08, .18)
                    : npc.onClimbable() ? Math.clamp(delta.y * .5, -.12, .18) : npc.getDeltaMovement().y;
            npc.setDeltaMovement(horizontal.x, vertical, horizontal.z); npc.hasImpulse = true;
            return;
        }
        if (step.movement() == AStarPathFinder.Movement.JUMP && npc.onGround()) {
            if (!jump(point)) replan();
            return;
        }
        if (step.movement() != AStarPathFinder.Movement.JUMP) {
            Vec3 from = npc.position();
            int samples = Math.max(2, (int) Math.ceil(from.distanceTo(point) * 4));
            for (int i = 1; i < samples; i++) {
                Vec3 sample = from.lerp(point, (double) i / samples);
                sample = new Vec3(sample.x, Math.max(from.y, point.y), sample.z);
                if (!clearLoaded(sample)) { replan(); return; }
            }
        }
        if (preciseGround) {
            // 平台边缘只施加小步水平速度，仍由实体的普通碰撞和重力完成移动。
            stopControls();
            Vec3 direction = point.subtract(npc.position()).multiply(1, 0, 1).normalize();
            double velocity = Math.min(.08, Math.min(horizontalDistance * .5,
                    npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)));
            npc.setDeltaMovement(direction.x * velocity, npc.getDeltaMovement().y, direction.z * velocity);
            npc.hasImpulse = true;
        } else npc.getMoveControl().setWantedPosition(point.x, point.y, point.z, 1);
    }

    public boolean jump(Vec3 landing) {
        if (!npc.onGround() || npc.isNoGravity()) return false;
        JumpTrajectory trajectory = JumpTrajectory.calculate(npc.position(), landing,
                npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED) * 2.5,
                npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH),
                this::clearLoaded);
        if (trajectory == null) return false;
        stopControls();
        npc.setOnGround(false);
        npc.setDeltaMovement(trajectory.horizontalDelay() == 0 ? trajectory.velocity()
                : new Vec3(0, trajectory.velocity().y, 0));
        pendingJumpVelocity = trajectory.horizontalDelay() == 0 ? null : trajectory.velocity();
        horizontalLaunchTick = npc.level().getGameTime() + trajectory.horizontalDelay();
        npc.hasImpulse = true;
        jumping = true;
        return true;
    }

    private boolean clearLoaded(Vec3 feet) {
        var box = npc.getBoundingBox().move(feet.subtract(npc.position())).deflate(.001);
        ServerLevel level = (ServerLevel) npc.level();
        for (int x = (int) Math.floor(box.minX) >> 4; x <= (int) Math.floor(box.maxX) >> 4; x++)
            for (int z = (int) Math.floor(box.minZ) >> 4; z <= (int) Math.floor(box.maxZ) >> 4; z++)
                if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ),
                net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var state = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
            if (NavigationTerrain.isHazard(state) || !options.swim() && !state.getFluidState().isEmpty()
                    || !options.climb() && state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return false;
        }
        return level.getWorldBorder().isWithinBounds(box) && level.noCollision(npc, box);
    }

    /** 供巡逻和撤退挑选安全落点，先检查整个碰撞箱的区块加载状态。 */
    public boolean canStandAt(Vec3 feet) {
        if (!(npc.level() instanceof ServerLevel level) || !clearLoaded(feet)) return false;
        var box = npc.getBoundingBox().move(feet.subtract(npc.position())).deflate(.001);
        var support = new net.minecraft.world.phys.AABB(box.minX, feet.y - .06, box.minZ, box.maxX, feet.y, box.maxZ);
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(support.minX, support.minY, support.minZ),
                net.minecraft.core.BlockPos.containing(support.maxX, support.minY, support.maxZ))) {
            var state = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4).getBlockState(pos);
            if (NavigationTerrain.isHazard(state) || !state.getFluidState().isEmpty() || state.is(net.minecraft.tags.BlockTags.CLIMBABLE)) return false;
        }
        return level.getBlockCollisions(npc, support).iterator().hasNext();
    }

    private void openDoors(Vec3 point) {
        if (!options.openDoors() || point.distanceToSqr(npc.position()) > 6.25) return;
        var level = (ServerLevel) npc.level();
        var box = npc.getBoundingBox().move(point.subtract(npc.position())).inflate(.05);
        for (var pos : net.minecraft.core.BlockPos.betweenClosed(net.minecraft.core.BlockPos.containing(box.minX, box.minY, box.minZ),
                net.minecraft.core.BlockPos.containing(box.maxX, box.maxY, box.maxZ))) {
            var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            var state = chunk.getBlockState(pos);
            if (state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock door && door.type().canOpenByHand()
                    && !state.getValue(net.minecraft.world.level.block.DoorBlock.OPEN)) door.setOpen(npc, level, state, pos, true);
        }
    }

    private void replan() {
        generation++;
        stopControls();
        if (++replans > 3) fail("blocked"); else enqueue();
    }

    private void fail(String reason) {
        failedRequests++;
        generation++;
        SEARCH.getQueue().removeIf(job -> job instanceof SearchJob search && search.owner == this);
        PENDING.remove(this);
        capture = null;
        stopControls();
        this.reason = reason;
        status = NavigationStatus.FAILED;
    }

    public void cancel() {
        generation++;
        SEARCH.getQueue().removeIf(job -> job instanceof SearchJob search && search.owner == this);
        PENDING.remove(this);
        capture = null;
        result.set(null);
        steps = List.of();
        jumping = false;
        stopControls();
        status = NavigationStatus.CANCELLED;
    }

    private void stopControls() {
        pendingJumpVelocity = null;
        npc.setMoveControlForAi(new MoveControl(npc));
        npc.setSpeed(0);
        npc.setXxa(0);
        npc.setZza(0);
        npc.setJumping(false);
        // 空中取消只撤销驱动，保留物理动量与重力。
        if (npc.onGround()) npc.setDeltaMovement(new Vec3(0, npc.getDeltaMovement().y, 0));
    }
}
