package com.viscript.npc.npc.ai.navigation;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/** 服务端分片生成的碰撞快照；完整生成后才交给搜索线程。 */
public final class NavigationSnapshot {
    private static final List<AABB> BLOCKED = List.of(new AABB(0, 0, 0, 1, 1, 1));
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final List<AABB>[] cells;
    private final byte[] media;
    private final boolean[] unsupported;
    public final double width, height, speed, jumpPower, stepHeight;
    public final boolean canJump;
    public final NavigationOptions options;

    public NavigationSnapshot(CustomNpc npc, Vec3 goal) {
        this(npc, goal, NavigationOptions.GROUND);
        var terrain = new NavigationTerrain((ServerLevel) npc.level());
        for (int i = 0; i < cells.length; i++) capture(terrain, i);
    }
    public static NavigationSnapshot capture(CustomNpc npc, Vec3 goal, NavigationOptions options) {
        var snapshot = new NavigationSnapshot(npc, goal, options);
        var terrain = new NavigationTerrain((ServerLevel) npc.level());
        for (int i = 0; i < snapshot.cells.length; i++) snapshot.capture(terrain, i);
        return snapshot;
    }

    @SuppressWarnings("unchecked")
    private NavigationSnapshot(CustomNpc npc, Vec3 goal, NavigationOptions options) {
        ServerLevel level = (ServerLevel) npc.level();
        BlockPos start = npc.blockPosition();
        minX = Math.min(start.getX(), (int) Math.floor(goal.x)) - 6;
        minZ = Math.min(start.getZ(), (int) Math.floor(goal.z)) - 6;
        minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), (int) Math.floor(goal.y)) - 4);
        sizeX = Math.max(start.getX(), (int) Math.floor(goal.x)) + 7 - minX;
        sizeZ = Math.max(start.getZ(), (int) Math.floor(goal.z)) + 7 - minZ;
        sizeY = Math.max(1, Math.min(level.getMaxBuildHeight(), Math.max(start.getY(), (int) Math.floor(goal.y)) + 6) - minY);
        cells = (List<AABB>[]) new List<?>[sizeX * sizeY * sizeZ];
        media = new byte[cells.length];
        unsupported = new boolean[cells.length];
        width = npc.getBoundingBox().getXsize(); height = npc.getBoundingBox().getYsize();
        speed = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        jumpPower = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        stepHeight = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        canJump = !npc.isNoGravity(); this.options = options;
    }

    /** 时间预算用尽后保留游标，下轮继续；生成中的对象不会发布。 */
    static final class Builder {
        private final NavigationSnapshot snapshot;
        private int cursor;
        Builder(CustomNpc npc, Vec3 goal, NavigationOptions options) { snapshot = new NavigationSnapshot(npc, goal, options); }
        boolean advance(NavigationTerrain terrain, long deadline) {
            while (cursor < snapshot.cells.length) {
                if ((cursor & 31) == 0 && System.nanoTime() >= deadline) return false;
                snapshot.capture(terrain, cursor++);
            }
            return true;
        }
        NavigationSnapshot finish() {
            if (cursor != snapshot.cells.length) throw new IllegalStateException("Incomplete terrain snapshot");
            return snapshot;
        }
    }

    private void capture(NavigationTerrain terrain, int index) {
        int y = index % sizeY, column = index / sizeY;
        var cell = terrain.cell(new BlockPos(column / sizeZ + minX, y + minY, column % sizeZ + minZ));
        boolean blocked = cell.blocked() || cell.water() && !options.swim() || cell.climbable() && !options.climb()
                || cell.door() && !options.openDoors();
        cells[index] = blocked ? BLOCKED : cell.door() ? cell.openShapes() : cell.shapes();
        unsupported[index] = cell.blocked() || cell.water() || cell.climbable() || cell.door();
        media[index] = (byte) (blocked ? 0 : cell.water() ? 1 : cell.climbable() ? 2 : 0);
    }
    private int index(int x, int y, int z) { return (x * sizeZ + z) * sizeY + y; }
    private byte medium(Vec3 feet) {
        int x = (int) Math.floor(feet.x) - minX, y = (int) Math.floor(feet.y + .01) - minY, z = (int) Math.floor(feet.z) - minZ;
        return x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ ? 0 : media[index(x, y, z)];
    }
    public boolean swimmable(Vec3 feet) { return medium(feet) == 1; }
    public boolean climbable(Vec3 feet) { return medium(feet) == 2; }
    public boolean occupiable(Vec3 feet) { return clear(feet) && (swimmable(feet) || climbable(feet) || standable(feet)); }

    /** 搜索私有的站立结果缓存，不修改快照，也不在多个工作线程间共享。 */
    public Query query() { return new Query(); }
    public final class Query {
        private final java.util.Map<Vec3, Boolean> standing = new java.util.HashMap<>();
        private final java.util.Map<Vec3, Boolean> occupied = new java.util.HashMap<>();
        public boolean standable(Vec3 feet) { return standing.computeIfAbsent(feet, NavigationSnapshot.this::standable); }
        public boolean occupiable(Vec3 feet) {
            return occupied.computeIfAbsent(feet, point -> swimmable(point) || climbable(point) ? clear(point) : standable(point));
        }
        public boolean clear(Vec3 feet) { return NavigationSnapshot.this.clear(feet); }
        public boolean traverse(Vec3 from, Vec3 to) { return NavigationSnapshot.this.traverse(from, to); }
    }

    public boolean clear(Vec3 feet) {
        return clear(new AABB(feet.x - width / 2 + .001, feet.y + .001, feet.z - width / 2 + .001,
                feet.x + width / 2 - .001, feet.y + height - .001, feet.z + width / 2 - .001));
    }
    private boolean clear(AABB box) {
        for (int x = (int) Math.floor(box.minX); x <= Math.floor(box.maxX); x++)
            for (int z = (int) Math.floor(box.minZ); z <= Math.floor(box.maxZ); z++)
                for (int y = (int) Math.floor(box.minY); y <= Math.floor(box.maxY); y++) {
                    int ix = x - minX, iy = y - minY, iz = z - minZ;
                    if (ix < 0 || iy < 0 || iz < 0 || ix >= sizeX || iy >= sizeY || iz >= sizeZ) return false;
                    for (AABB shape : cells[index(ix, iy, iz)]) if (box.intersects(shape.move(x, y, z))) return false;
                }
        return true;
    }
    public boolean standable(Vec3 feet) {
        double halfWidth = width / 2 - .001;
        int supportY = (int) Math.floor(feet.y - .01) - minY;
        for (int x = (int) Math.floor(feet.x - halfWidth); x <= Math.floor(feet.x + halfWidth); x++)
            for (int z = (int) Math.floor(feet.z - halfWidth); z <= Math.floor(feet.z + halfWidth); z++) {
                int ix = x - minX, iz = z - minZ;
                if (ix < 0 || iz < 0 || supportY < 0 || ix >= sizeX || iz >= sizeZ || supportY >= sizeY
                        || unsupported[index(ix, supportY, iz)]) return false;
            }
        return clear(feet) && !clear(new AABB(feet.x - halfWidth, feet.y - .06, feet.z - halfWidth,
                feet.x + halfWidth, feet.y, feet.z + halfWidth));
    }
    public boolean traverse(Vec3 from, Vec3 to) {
        int samples = Math.max(4, (int) (from.distanceTo(to) * 8));
        for (int i = 1; i <= samples; i++) {
            Vec3 point = from.lerp(to, (double) i / samples);
            if (to.y > from.y && to.y - from.y <= stepHeight) point = new Vec3(point.x, to.y, point.z);
            if (to.y < from.y) point = new Vec3(point.x, from.y, point.z);
            if (!clear(point)) return false;
        }
        if (to.y < from.y) for (double y = from.y; y > to.y; y -= .125)
            if (!clear(new Vec3(to.x, y, to.z))) return false;
        return true;
    }
}
