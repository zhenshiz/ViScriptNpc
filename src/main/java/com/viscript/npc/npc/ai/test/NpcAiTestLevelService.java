package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.util.NpcRuntimeFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import org.jetbrains.annotations.Nullable;
import org.thexeler.MindMachine;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.world.MindPosition;
import org.thexeler.intention.base.MoveIntention;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 管理 NPC AI 编辑器使用的真实测试维度以及其中的服务器权威编辑操作。
 *
 * <p>测试玩家进入维度时会保存原维度和位置。测试对象直接使用真实的 {@link ServerPlayer}，因此 NPC 的目标判定和实体交互仍然遵循 Minecraft 的玩家语义。
 */
@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public final class NpcAiTestLevelService {
    public static final String ACTION_PLACE_BLOCK = "place_block";
    public static final String ACTION_BREAK_BLOCK = "break_block";
    public static final String ACTION_PLACE_NPC = "place_npc";
    public static final String ACTION_PLACE_ENTITY = "place_entity";
    public static final String ACTION_PLACE_PLAYER = "place_player";
    public static final String ACTION_MOVE_NPC = "move_npc";
    public static final String ACTION_MOVE_ENTITY = "move_entity";
    public static final String ACTION_DELETE_ENTITY = "delete_entity";
    public static final String ACTION_PATHFIND_NPC = "pathfind_npc";
    public static final String ACTION_RESIZE_ARENA = "resize_arena";

    private static final int ARENA_GRID_SPACING = 64;
    private static final int RANDOM_GRID_RADIUS = 256;
    private static final int RANDOM_ALLOCATION_ATTEMPTS = 256;
    private static final Map<UUID, TestSession> TEST_SESSIONS = new HashMap<>();

    private NpcAiTestLevelService() {
    }

    /**
     * 将玩家送入测试维度，并为本次会话记录返回位置。
     *
     * @param player 请求进入测试场景的玩家
     * @return 测试维度存在且传送成功时返回 {@code true}
     */
    public static boolean enter(ServerPlayer player) {
        return enter(player, NpcAiTestEnvironment.DEFAULT_ARENA_WIDTH,
                NpcAiTestEnvironment.DEFAULT_ARENA_LENGTH);
    }

    /**
     * 将玩家送入指定尺寸的测试场地。
     *
     * @param player 请求进入测试场景的玩家
     * @param width 场地沿 X 轴的方块数
     * @param length 场地沿 Z 轴的方块数
     * @return 测试维度存在且传送成功时返回 {@code true}
     */
    public static boolean enter(ServerPlayer player, int width, int length) {
        if (!isAuthorized(player)) {
            return false;
        }
        ServerLevel testLevel = player.server.getLevel(NpcAiTestEnvironment.LEVEL_KEY);
        if (testLevel == null) {
            return false;
        }
        TestSession session = TEST_SESSIONS.get(player.getUUID());
        if (session == null) {
            ReturnLocation returnLocation = NpcAiTestEnvironment.isTestLevel(player.serverLevel())
                    ? null
                    : new ReturnLocation(player.serverLevel().dimension(), player.position(),
                    player.getYRot(), player.getXRot());
            assignNewArena(player, testLevel, returnLocation, width, length);
        } else if (session.width() != NpcAiTestEnvironment.normalizeArenaSize(width)
                || session.length() != NpcAiTestEnvironment.normalizeArenaSize(length)) {
            resizeArena(player, testLevel, session, width, length);
        }
        return true;
    }

    /**
     * 为已经位于测试维度的玩家重新分配一块独立场地。
     *
     * <p>重新分配时保留玩家首次进入测试维度前的返回位置。当前活动场地仍参与占用检查，
     * 因此新场地不会与原场地或其他玩家的场地重合。
     *
     * @param player 请求创建新测试场地的玩家
     * @return 玩家位于测试维度且新场地分配成功时返回 {@code true}
     */
    public static boolean renewArena(ServerPlayer player) {
        return renewArena(player, NpcAiTestEnvironment.DEFAULT_ARENA_WIDTH,
                NpcAiTestEnvironment.DEFAULT_ARENA_LENGTH);
    }

    /**
     * 为已经位于测试维度的玩家重新分配指定尺寸的独立场地。
     *
     * @param player 请求创建新测试场地的玩家
     * @param width 场地沿 X 轴的方块数
     * @param length 场地沿 Z 轴的方块数
     * @return 玩家位于测试维度且新场地分配成功时返回 {@code true}
     */
    public static boolean renewArena(ServerPlayer player, int width, int length) {
        if (!isAuthorized(player) || !NpcAiTestEnvironment.isTestLevel(player.serverLevel())) {
            return false;
        }
        ServerLevel testLevel = player.server.getLevel(NpcAiTestEnvironment.LEVEL_KEY);
        if (testLevel == null) {
            return false;
        }
        TestSession currentSession = TEST_SESSIONS.get(player.getUUID());
        ReturnLocation returnLocation = currentSession == null ? null : currentSession.returnLocation();
        assignNewArena(player, testLevel, returnLocation, width, length);
        return true;
    }

    /**
     * 将玩家从测试维度送回进入测试前保存的位置。
     *
     * @param player 请求退出测试场景的玩家
     * @return 找到返回世界并完成传送时返回 {@code true}
     */
    public static boolean leave(@Nullable ServerPlayer player) {
        if (!isAuthorized(player)) {
            return false;
        }
        TestSession session = TEST_SESSIONS.remove(player.getUUID());
        return restorePlayer(player, session == null ? null : session.returnLocation());
    }

    /**
     * 获取玩家当前测试会话的场地中心。
     *
     * @param player 正在使用测试场景的玩家
     * @return 已分配的场地中心；不存在活动会话时返回 {@code null}
     */
    @Nullable
    public static BlockPos getArenaCenter(@Nullable ServerPlayer player) {
        TestSession session = player == null ? null : TEST_SESSIONS.get(player.getUUID());
        return session == null ? null : session.center();
    }

    /**
     * 获取玩家当前测试会话的服务器权威编辑区域。
     *
     * @param player 正在使用测试场景的玩家
     * @return 已分配的编辑区域；不存在活动会话时返回 {@code null}
     */
    @Nullable
    public static AABB getArenaRegion(@Nullable ServerPlayer player) {
        TestSession session = player == null ? null : TEST_SESSIONS.get(player.getUUID());
        return session == null ? null : session.region();
    }

    /**
     * 防止玩家断线或服务器重启后被永久留在内部测试维度。
     *
     * @param event 玩家完成登录时触发的服务端事件
     */
    @SubscribeEvent
    public static void playerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && NpcAiTestEnvironment.isTestLevel(player.serverLevel())) {
            TestSession session = TEST_SESSIONS.remove(player.getUUID());
            restorePlayer(player, session == null ? null : session.returnLocation());
        }
    }

    /**
     * 清除已经停止的服务器留下的会话返回点，避免下一张存档复用旧坐标。
     *
     * @param event 服务器停止事件
     */
    @SubscribeEvent
    public static void serverStopped(ServerStoppedEvent event) {
        TEST_SESSIONS.clear();
    }

    private static boolean restorePlayer(ServerPlayer player, @Nullable ReturnLocation returnLocation) {
        ServerLevel level = returnLocation == null
                ? player.server.overworld()
                : player.server.getLevel(returnLocation.dimension());
        if (level == null) {
            level = player.server.overworld();
        }
        if (returnLocation == null) {
            BlockPos spawn = level.getSharedSpawnPos();
            player.teleportTo(level, spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D,
                    player.getYRot(), player.getXRot());
        } else {
            player.teleportTo(level, returnLocation.position.x(), returnLocation.position.y(),
                    returnLocation.position.z(), returnLocation.yaw, returnLocation.pitch);
        }
        return true;
    }

    private static void assignNewArena(ServerPlayer player, ServerLevel testLevel,
                                       @Nullable ReturnLocation returnLocation,
                                       int width, int length) {
        BlockPos center = allocateArenaCenter();
        int normalizedWidth = NpcAiTestEnvironment.normalizeArenaSize(width);
        int normalizedLength = NpcAiTestEnvironment.normalizeArenaSize(length);
        AABB region = NpcAiTestEnvironment.createRegion(center, normalizedWidth, normalizedLength);
        TestSession session = new TestSession(
                center, region, normalizedWidth, normalizedLength, returnLocation);
        TEST_SESSIONS.put(player.getUUID(), session);
        prepareArena(testLevel, center, normalizedWidth, normalizedLength);
        teleportPlayerIntoArena(player, testLevel, region);
    }

    /**
     * 执行一次来自编辑器测试页的世界操作。
     *
     * @param player 发送操作的玩家
     * @param action 操作标识
     * @param payload 操作参数
     */
    public static void applyAction(ServerPlayer player, String action, CompoundTag payload) {
        if (!isAuthorized(player) || !NpcAiTestEnvironment.isTestLevel(player.serverLevel())) {
            return;
        }
        TestSession session = TEST_SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        ServerLevel level = player.serverLevel();
        switch (action) {
            case ACTION_PLACE_BLOCK -> placeBlock(level, session.region(), payload);
            case ACTION_BREAK_BLOCK -> breakBlock(level, session.region(), payload);
            case ACTION_PLACE_NPC -> placeNpc(level, session.region(), payload);
            case ACTION_PLACE_ENTITY -> placeEntity(level, session.region(), payload);
            case ACTION_PLACE_PLAYER -> placePlayer(player, session.region(), payload);
            case ACTION_MOVE_NPC -> moveNpc(level, session.region(), payload);
            case ACTION_MOVE_ENTITY -> moveEntity(level, session.region(), payload);
            case ACTION_DELETE_ENTITY -> deleteEntity(level, session.region(), payload);
            case ACTION_PATHFIND_NPC -> pathfindNpc(level, session.region(), payload);
            case ACTION_RESIZE_ARENA -> resizeArena(player, level, session,
                    payload.getInt("width"), payload.getInt("length"));
            default -> ViScriptNpc.LOGGER.warn("忽略未知的 NPC AI 测试操作：{}", action);
        }
    }

    /**
     * 判断玩家是否有权使用测试维度编辑功能。
     *
     * @param player 待判断的玩家
     * @return 玩家存在且具有管理员权限时返回 {@code true}
     */
    public static boolean isAuthorized(@Nullable ServerPlayer player) {
        return player != null && player.hasPermissions(2);
    }

    private static BlockPos allocateArenaCenter() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < RANDOM_ALLOCATION_ATTEMPTS; attempt++) {
            BlockPos center = gridCenter(
                    random.nextInt(-RANDOM_GRID_RADIUS, RANDOM_GRID_RADIUS + 1),
                    random.nextInt(-RANDOM_GRID_RADIUS, RANDOM_GRID_RADIUS + 1));
            if (!isArenaInUse(center)) {
                return center;
            }
        }
        for (int gridX = -RANDOM_GRID_RADIUS; gridX <= RANDOM_GRID_RADIUS; gridX++) {
            for (int gridZ = -RANDOM_GRID_RADIUS; gridZ <= RANDOM_GRID_RADIUS; gridZ++) {
                BlockPos center = gridCenter(gridX, gridZ);
                if (!isArenaInUse(center)) {
                    return center;
                }
            }
        }
        throw new IllegalStateException("NPC AI 测试维度没有可分配的场地区域");
    }

    private static BlockPos gridCenter(int gridX, int gridZ) {
        return new BlockPos(gridX * ARENA_GRID_SPACING, NpcAiTestEnvironment.ARENA_FLOOR_Y,
                gridZ * ARENA_GRID_SPACING);
    }

    private static boolean isArenaInUse(BlockPos center) {
        return TEST_SESSIONS.values().stream().anyMatch(session -> session.center().equals(center));
    }

    private static void prepareArena(ServerLevel level, BlockPos center, int width, int length) {
        AABB allocationRegion = NpcAiTestEnvironment.createRegion(
                center, NpcAiTestEnvironment.MAX_ARENA_SIZE, NpcAiTestEnvironment.MAX_ARENA_SIZE);
        for (int x = Mth.floor(allocationRegion.minX); x < Mth.ceil(allocationRegion.maxX); x++) {
            for (int y = Mth.floor(allocationRegion.minY); y < Mth.ceil(allocationRegion.maxY); y++) {
                for (int z = Mth.floor(allocationRegion.minZ); z < Mth.ceil(allocationRegion.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.getBlockState(pos).isAir()) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        AABB region = NpcAiTestEnvironment.createRegion(center, width, length);
        for (int x = Mth.floor(region.minX); x < Mth.ceil(region.maxX); x++) {
            for (int z = Mth.floor(region.minZ); z < Mth.ceil(region.maxZ); z++) {
                level.setBlockAndUpdate(new BlockPos(x, center.getY(), z), Blocks.STONE.defaultBlockState());
            }
        }
    }

    private static void resizeArena(ServerPlayer player, ServerLevel level, TestSession session,
                                    int width, int length) {
        int normalizedWidth = NpcAiTestEnvironment.normalizeArenaSize(width);
        int normalizedLength = NpcAiTestEnvironment.normalizeArenaSize(length);
        if (session.width() == normalizedWidth && session.length() == normalizedLength) {
            return;
        }
        AABB resizedRegion = NpcAiTestEnvironment.createRegion(
                session.center(), normalizedWidth, normalizedLength);
        resizeArenaBlocks(level, session.region(), resizedRegion);
        TEST_SESSIONS.put(player.getUUID(), new TestSession(
                session.center(), resizedRegion, normalizedWidth, normalizedLength, session.returnLocation()));
        if (!NpcAiTestEnvironment.isEditable(resizedRegion, player.getBoundingBox())) {
            teleportPlayerIntoArena(player, level, resizedRegion);
        }
    }

    private static void resizeArenaBlocks(ServerLevel level, AABB oldRegion, AABB resizedRegion) {
        int minX = Mth.floor(Math.min(oldRegion.minX, resizedRegion.minX));
        int maxX = Mth.ceil(Math.max(oldRegion.maxX, resizedRegion.maxX));
        int minZ = Mth.floor(Math.min(oldRegion.minZ, resizedRegion.minZ));
        int maxZ = Mth.ceil(Math.max(oldRegion.maxZ, resizedRegion.maxZ));
        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                boolean wasInside = containsColumn(oldRegion, x, z);
                boolean isInside = containsColumn(resizedRegion, x, z);
                if (wasInside == isInside) {
                    continue;
                }
                for (int y = NpcAiTestEnvironment.ARENA_FLOOR_Y;
                     y < NpcAiTestEnvironment.ARENA_FLOOR_Y + NpcAiTestEnvironment.REGION_HEIGHT; y++) {
                    level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
                }
                if (isInside) {
                    level.setBlockAndUpdate(new BlockPos(x, NpcAiTestEnvironment.ARENA_FLOOR_Y, z),
                            Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    private static boolean containsColumn(AABB region, int x, int z) {
        return x + 0.5D >= region.minX && x + 0.5D < region.maxX
                && z + 0.5D >= region.minZ && z + 0.5D < region.maxZ;
    }

    private static void teleportPlayerIntoArena(ServerPlayer player, ServerLevel level, AABB region) {
        double x = Math.max(region.minX + 0.5D, region.maxX - 1.5D);
        double z = Math.max(region.minZ + 0.5D, region.maxZ - 1.5D);
        player.teleportTo(level, x, region.minY + 1.0D, z, 0.0F, 0.0F);
    }

    private static void placeBlock(ServerLevel level, AABB region, CompoundTag payload) {
        BlockPos target = readPlacementPos(payload);
        if (target == null || !NpcAiTestEnvironment.isEditable(region, target)) {
            return;
        }
        ResourceLocation blockId = ResourceLocation.tryParse(payload.getString("block"));
        Block block = blockId == null ? Blocks.STONE : BuiltInRegistries.BLOCK.get(blockId);
        BlockState state = block.defaultBlockState();
        if (!state.isAir() && level.getBlockState(target).canBeReplaced()) {
            level.setBlockAndUpdate(target, state);
        }
    }

    private static void breakBlock(ServerLevel level, AABB region, CompoundTag payload) {
        BlockPos target = readBlockPos(payload);
        if (target != null && NpcAiTestEnvironment.isEditable(region, target)
                && !level.getBlockState(target).is(Blocks.BEDROCK)) {
            level.setBlockAndUpdate(target, Blocks.AIR.defaultBlockState());
        }
    }

    private static void placeNpc(ServerLevel level, AABB region, CompoundTag payload) {
        Vec3 position = readPosition(payload);
        String npcFile = payload.getString("npcFile");
        if (position == null || npcFile.isBlank() || !isEditablePosition(region, position, 2.0D)) {
            return;
        }
        CompoundTag npcData = NpcRuntimeFiles.load(npcFile);
        if (npcData.isEmpty()) return;
        CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(level);
        if (npc == null) {
            return;
        }
        npc.moveTo(position.x(), position.y(), position.z(), 0.0F, 0.0F);
        npc.readAdditionalSaveData(npcData.copy());
        npc.setPersistenceRequired();
        level.addFreshEntity(npc);
    }

    private static void placeEntity(ServerLevel level, AABB region, CompoundTag payload) {
        Vec3 position = readPosition(payload);
        EntityType<?> entityType = EntityType.byString(payload.getString("entity")).orElse(EntityType.PIG);
        if (position == null || entityType == EntityType.PLAYER
                || !isEditablePosition(region, position, 3.0D)) {
            return;
        }
        Entity entity = entityType.create(level);
        if (entity == null) {
            return;
        }
        entity.moveTo(position.x(), position.y(), position.z(), 0.0F, 0.0F);
        if (entity instanceof Mob mob) {
            EventHooks.finalizeMobSpawn(mob, level,
                    level.getCurrentDifficultyAt(BlockPos.containing(position)), MobSpawnType.COMMAND, null);
            mob.setPersistenceRequired();
            mob.setNoAi(false);
        }
        level.addFreshEntity(entity);
    }

    private static void placePlayer(ServerPlayer player, AABB region, CompoundTag payload) {
        Vec3 position = readPosition(payload);
        if (position != null && isEditablePosition(region, position, 2.0D)) {
            player.teleportTo(player.serverLevel(), position.x(), position.y(), position.z(),
                    player.getYRot(), player.getXRot());
        }
    }

    private static void moveNpc(ServerLevel level, AABB region, CompoundTag payload) {
        CustomNpc npc = getNpc(level, payload);
        Vec3 position = readPosition(payload);
        if (npc == null || position == null || !NpcAiTestEnvironment.isEditable(region, npc.getBoundingBox())
                || !isEditablePosition(region, position, 2.0D)) {
            return;
        }
        npc.getNavigation().stop();
        npc.teleportTo(position.x(), position.y(), position.z());
        npc.setDeltaMovement(Vec3.ZERO);
        npc.fallDistance = 0.0F;
    }

    private static void moveEntity(ServerLevel level, AABB region, CompoundTag payload) {
        Entity entity = level.getEntity(payload.getInt("entityId"));
        Vec3 position = readPosition(payload);
        if (entity == null || position == null || entity.isRemoved()
                || !NpcAiTestEnvironment.isEditable(region, entity.getBoundingBox())
                || !isEditableEntityPosition(region, entity, position)) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
        }
        entity.teleportTo(position.x(), position.y(), position.z());
        entity.setDeltaMovement(Vec3.ZERO);
        entity.fallDistance = 0.0F;
    }

    private static void deleteEntity(ServerLevel level, AABB region, CompoundTag payload) {
        Entity entity = level.getEntity(payload.getInt("entityId"));
        if (entity == null || entity.isRemoved() || entity instanceof Player
                || !NpcAiTestEnvironment.isEditable(region, entity.getBoundingBox())) {
            return;
        }
        entity.discard();
    }

    private static void pathfindNpc(ServerLevel level, AABB region, CompoundTag payload) {
        CustomNpc npc = getNpc(level, payload);
        Vec3 position = readPosition(payload);
        if (npc != null && npc.getNpcAI().isEnabled() && position != null
                && NpcAiTestEnvironment.isEditable(region, npc.getBoundingBox())
                && isEditablePosition(region, position, 2.0D)) {
            int groundY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    Mth.floor(position.x()), Mth.floor(position.z()));
            MindMachine mind = npc.getMind();
            if (mind == null) {
                npc.initMind();
                mind = npc.getMind();
            }
            if (mind != null) {
                mind.submit(MoveIntention.TYPE, IntentionPriority.URGENT, Map.of(
                        "position", MindPosition.of(position.x(), groundY, position.z())));
            }
        }
    }

    @Nullable
    private static CustomNpc getNpc(ServerLevel level, CompoundTag payload) {
        Entity entity = level.getEntity(payload.getInt("entityId"));
        return entity instanceof CustomNpc npc ? npc : null;
    }

    @Nullable
    private static BlockPos readBlockPos(CompoundTag payload) {
        return payload.contains("blockPos") ? BlockPos.of(payload.getLong("blockPos")) : null;
    }

    @Nullable
    private static BlockPos readPlacementPos(CompoundTag payload) {
        BlockPos blockPos = readBlockPos(payload);
        if (blockPos == null) {
            return null;
        }
        Direction direction;
        try {
            direction = Direction.valueOf(payload.getString("direction"));
        } catch (IllegalArgumentException exception) {
            direction = Direction.UP;
        }
        return blockPos.relative(direction);
    }

    @Nullable
    private static Vec3 readPosition(CompoundTag payload) {
        if (!payload.contains("x") || !payload.contains("y") || !payload.contains("z")) {
            return null;
        }
        return new Vec3(payload.getDouble("x"), payload.getDouble("y"), payload.getDouble("z"));
    }

    private static boolean isEditablePosition(AABB region, Vec3 position, double height) {
        return NpcAiTestEnvironment.isEditable(region, new AABB(
                position.x() - 0.5D, position.y(), position.z() - 0.5D,
                position.x() + 0.5D, position.y() + height, position.z() + 0.5D));
    }

    private static boolean isEditableEntityPosition(AABB region, Entity entity, Vec3 position) {
        double halfWidth = Math.max(0.25D, entity.getBbWidth() * 0.5D);
        double height = Math.max(0.5D, entity.getBbHeight());
        return NpcAiTestEnvironment.isEditable(region, new AABB(
                position.x() - halfWidth, position.y(), position.z() - halfWidth,
                position.x() + halfWidth, position.y() + height, position.z() + halfWidth));
    }

    private record TestSession(BlockPos center, AABB region, int width, int length,
                               @Nullable ReturnLocation returnLocation) {
    }

    private record ReturnLocation(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
    }
}
