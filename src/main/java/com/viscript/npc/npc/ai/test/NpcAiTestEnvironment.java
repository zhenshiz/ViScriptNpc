package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * 定义 NPC AI 编辑器测试场景在客户端和服务端共用的边界与标识。
 *
 * <p>该类只包含维度键和动态编辑区域规则，不持有服务端对象，因而可以安全地被客户端界面引用。
 */
public final class NpcAiTestEnvironment {
    public static final String ARENA_CENTER_TAG = "_npc_ai_test_arena_center";
    public static final ResourceKey<Level> LEVEL_KEY = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(ViScriptNpc.MOD_ID, "npc_ai_test"));
    public static final int ARENA_FLOOR_Y = 64;
    public static final int DEFAULT_ARENA_WIDTH = 10;
    public static final int DEFAULT_ARENA_LENGTH = 10;
    public static final int MIN_ARENA_SIZE = 4;
    public static final int MAX_ARENA_SIZE = 32;
    public static final int REGION_HEIGHT = 16;

    private NpcAiTestEnvironment() {
    }

    /**
     * 判断指定世界是否为 NPC AI 编辑器测试维度。
     *
     * @param level 待判断的世界
     * @return 世界不为空且维度键匹配时返回 {@code true}
     */
    public static boolean isTestLevel(Level level) {
        return level != null && level.dimension().equals(LEVEL_KEY);
    }

    /**
     * 将场地边长限制在测试维度能够安全分配的范围内。
     *
     * @param size 用户设置的场地边长
     * @return 可用于场地生成的边长
     */
    public static int normalizeArenaSize(int size) {
        return Math.max(MIN_ARENA_SIZE, Math.min(MAX_ARENA_SIZE, size));
    }

    /**
     * 根据场地中心和尺寸创建服务器权威的编辑边界。
     *
     * @param center 当前玩家被分配的场地中心
     * @param width 场地沿 X 轴的方块数
     * @param length 场地沿 Z 轴的方块数
     * @return 围绕场地中心创建的编辑区域
     */
    public static AABB createRegion(BlockPos center, int width, int length) {
        int normalizedWidth = normalizeArenaSize(width);
        int normalizedLength = normalizeArenaSize(length);
        int minX = center.getX() - normalizedWidth / 2;
        int minZ = center.getZ() - normalizedLength / 2;
        return new AABB(
                minX,
                center.getY(),
                minZ,
                minX + normalizedWidth,
                center.getY() + REGION_HEIGHT,
                minZ + normalizedLength);
    }

    /**
     * 判断方块坐标是否落在指定测试区域内。
     *
     * @param region 当前玩家被分配的测试区域
     * @param pos 待判断的方块坐标
     * @return 坐标位于测试区域内时返回 {@code true}
     */
    public static boolean isEditable(AABB region, BlockPos pos) {
        return region.contains(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
    }

    /**
     * 判断实体边界盒是否完整落在指定测试区域内。
     *
     * @param region 当前玩家被分配的测试区域
     * @param box 待判断的实体边界盒
     * @return 边界盒未越过测试区域边界时返回 {@code true}
     */
    public static boolean isEditable(AABB region, AABB box) {
        return box.minX >= region.minX && box.maxX <= region.maxX
                && box.minY >= region.minY && box.maxY <= region.maxY
                && box.minZ >= region.minZ && box.maxZ <= region.maxZ;
    }
}
