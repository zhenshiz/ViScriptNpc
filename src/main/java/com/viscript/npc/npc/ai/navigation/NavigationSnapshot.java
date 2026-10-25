package com.viscript.npc.npc.ai.navigation;

import com.viscript.npc.npc.CustomNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 服务器线程生成的不可变碰撞快照；搜索线程不持有世界或实体。 */
public final class NavigationSnapshot {
    private static final List<AABB> BLOCKED = List.of(new AABB(0, 0, 0, 1, 1, 1));
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final List<AABB>[] cells;
    public final double width, height, speed, jumpPower, stepHeight;
    public final boolean canJump;

    @SuppressWarnings("unchecked")
    public NavigationSnapshot(CustomNpc npc, Vec3 goal) {
        ServerLevel level = (ServerLevel) npc.level();
        BlockPos start = npc.blockPosition();
        minX = Math.min(start.getX(), (int) Math.floor(goal.x)) - 6;
        minZ = Math.min(start.getZ(), (int) Math.floor(goal.z)) - 6;
        minY = Math.max(level.getMinBuildHeight(), Math.min(start.getY(), (int) Math.floor(goal.y)) - 4);
        sizeX = Math.max(start.getX(), (int) Math.floor(goal.x)) + 7 - minX;
        sizeZ = Math.max(start.getZ(), (int) Math.floor(goal.z)) + 7 - minZ;
        sizeY = Math.min(level.getMaxBuildHeight(), Math.max(start.getY(), (int) Math.floor(goal.y)) + 6) - minY;
        cells = (List<AABB>[]) new List<?>[sizeX * sizeY * sizeZ];
        width = npc.getBoundingBox().getXsize();
        height = npc.getBoundingBox().getYsize();
        speed = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        jumpPower = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.JUMP_STRENGTH);
        stepHeight = npc.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.STEP_HEIGHT);
        canJump = !npc.isNoGravity();
        // A dynamic modded collision shape may query neighboring cells. Even those
        // reads must never cause the server to load an absent chunk.
        net.minecraft.world.level.BlockGetter terrain = new net.minecraft.world.level.BlockGetter() {
            @Override public BlockState getBlockState(BlockPos position) {
                var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(position);
            }
            @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos position) {
                return getBlockState(position).getFluidState();
            }
            @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos position) {
                var chunk = level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
                return chunk == null ? null : chunk.getBlockEntity(position);
            }
            @Override public int getHeight() { return level.getHeight(); }
            @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
        };
        Map<BlockState, List<AABB>> shapes = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < sizeX; x++) for (int z = 0; z < sizeZ; z++) {
            var chunk = level.getChunkSource().getChunkNow((x + minX) >> 4, (z + minZ) >> 4);
            for (int y = 0; y < sizeY; y++) {
                pos.set(x + minX, y + minY, z + minZ);
                List<AABB> boxes = BLOCKED;
                if (chunk != null && level.getWorldBorder().isWithinBounds(pos)) {
                    BlockState state = chunk.getBlockState(pos);
                    if (state.getFluidState().isEmpty() && !state.is(BlockTags.CLIMBABLE)
                            && !(state.getBlock() instanceof DoorBlock) && !state.is(Blocks.FIRE)
                            && !state.is(Blocks.SOUL_FIRE) && !state.is(Blocks.CACTUS)
                            && !state.is(Blocks.MAGMA_BLOCK) && !state.is(Blocks.CAMPFIRE)
                            && !state.is(Blocks.SWEET_BERRY_BUSH) && !state.is(Blocks.POWDER_SNOW)) {
                        // 动态形状不缓存；每格碰撞形状仍在服务器线程计算。
                        boxes = state.getBlock().hasDynamicShape()
                                ? state.getCollisionShape(terrain, pos).toAabbs()
                                : shapes.computeIfAbsent(state, ignored -> state.getCollisionShape(terrain, pos).toAabbs());
                    }
                }
                cells[index(x, y, z)] = List.copyOf(boxes);
            }
        }
    }

    private int index(int x, int y, int z) { return (x * sizeZ + z) * sizeY + y; }

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
        return clear(feet) && !clear(new AABB(feet.x - halfWidth, feet.y - .06, feet.z - halfWidth,
                feet.x + halfWidth, feet.y, feet.z + halfWidth));
    }

    public boolean traverse(Vec3 from, Vec3 to) {
        int samples = Math.max(4, (int) (from.distanceTo(to) * 8));
        for (int i = 1; i <= samples; i++) {
            Vec3 point = from.lerp(to, (double) i / samples);
            // 台阶的上升发生在碰撞边缘，先抬到目标高度再检查水平净空。
            if (to.y > from.y && to.y - from.y <= stepHeight) point = new Vec3(point.x, to.y, point.z);
            if (to.y < from.y) point = new Vec3(point.x, from.y, point.z);
            if (!clear(point)) return false;
        }
        if (to.y < from.y) for (double y = from.y; y > to.y; y -= .125)
            if (!clear(new Vec3(to.x, y, to.z))) return false;
        return true;
    }
}
