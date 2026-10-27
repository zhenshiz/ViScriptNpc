package com.viscript.npc.npc.ai.navigation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 单次调度期间共享的地形读取缓存；不加载区块，也不跨服务器 tick 复用。 */
final class NavigationTerrain implements BlockGetter {
    private static final List<AABB> BLOCKED = List.of(new AABB(0, 0, 0, 1, 1, 1));
    record Cell(List<AABB> shapes, List<AABB> openShapes, boolean water, boolean climbable, boolean door, boolean blocked) {}
    private static final Cell SOLID = new Cell(BLOCKED, BLOCKED, false, false, false, true);
    private final ServerLevel level;
    private final Map<Long, Cell> cells = new HashMap<>();
    private final Map<BlockState, List<AABB>> shapes = new HashMap<>();
    NavigationTerrain(ServerLevel level) { this.level = level; }
    Cell cell(BlockPos pos) { return cells.computeIfAbsent(pos.asLong(), key -> read(pos)); }
    private Cell read(BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null || !level.getWorldBorder().isWithinBounds(pos)) return SOLID;
        var state = chunk.getBlockState(pos);
        boolean water = state.getFluidState().is(FluidTags.WATER);
        if (isHazard(state)) return SOLID;
        boolean closedDoor = state.getBlock() instanceof DoorBlock && !state.getValue(DoorBlock.OPEN);
        boolean woodenDoor = state.getBlock() instanceof DoorBlock door && door.type().canOpenByHand();
        if (closedDoor && !woodenDoor) return SOLID;
        var collision = shape(state, pos);
        return new Cell(collision, closedDoor ? shape(state.setValue(DoorBlock.OPEN, true), pos) : collision,
                water, state.is(BlockTags.CLIMBABLE), closedDoor, false);
    }
    static boolean isHazard(BlockState state) {
        return (!state.getFluidState().isEmpty() && !state.getFluidState().is(FluidTags.WATER))
                || state.is(Blocks.FIRE) || state.is(Blocks.SOUL_FIRE) || state.is(Blocks.CACTUS)
                || state.is(Blocks.MAGMA_BLOCK) || state.is(Blocks.CAMPFIRE)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW);
    }
    private List<AABB> shape(BlockState state, BlockPos pos) {
        return state.getBlock().hasDynamicShape() ? List.copyOf(state.getCollisionShape(this, pos).toAabbs())
                : shapes.computeIfAbsent(state, ignored -> List.copyOf(state.getCollisionShape(this, pos).toAabbs()));
    }
    @Override public BlockState getBlockState(BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? Blocks.BEDROCK.defaultBlockState() : chunk.getBlockState(pos);
    }
    @Override public net.minecraft.world.level.material.FluidState getFluidState(BlockPos pos) { return getBlockState(pos).getFluidState(); }
    @Override public net.minecraft.world.level.block.entity.BlockEntity getBlockEntity(BlockPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        return chunk == null ? null : chunk.getBlockEntity(pos);
    }
    @Override public int getHeight() { return level.getHeight(); }
    @Override public int getMinBuildHeight() { return level.getMinBuildHeight(); }
}
