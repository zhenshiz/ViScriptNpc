package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.Editor;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.mojang.blaze3d.platform.Window;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.CustomNpc;
import dev.latvian.mods.kubejs.typings.Info;
import dev.vfyjxf.taffy.style.TaffyPosition;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.ClipContext;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ViScriptNpcClientUtil {
    private static final double NPC_AI_WORLD_TEST_TARGET_RADIUS = 64.0D;
    private static final double NPC_AI_WORLD_CAMERA_DRAG_SCALE = 1.0D / 0.15D;
    public static NPCProject cacheNpcProject;
    private static int npcAiWorldTestActiveEntityId = -1;
    private static boolean npcAiWorldInteractionMode;
    private static int npcAiWorldTestEntityId = -1;
    private static float npcAiWorldCameraDistance = 12.0F;
    private static boolean npcAiWorldFixedCamera;
    @Nullable
    private static Vec3 npcAiWorldFixedCameraPosition;
    private static float npcAiWorldFixedCameraYaw;
    private static float npcAiWorldFixedCameraPitch;
    private static boolean npcEditorMouseKnown;
    private static double npcEditorMouseX;
    private static double npcEditorMouseY;
    @Nullable
    private static BlockHitResult npcAiWorldBlockHit;
    private static int npcAiWorldDraggedMobId = -1;
    @Nullable
    private static Vec3 npcAiWorldDraggedMobTarget;
    @Nullable
    private static Vec3 npcAiWorldLastSentMobDragTarget;
    private static int npcAiWorldPathEntityId = -1;
    private static long npcAiWorldPathExpireTick;
    private static List<Vec3> npcAiWorldPathPoints = List.of();
    private static boolean npcAiWorldPathVisible = true;
    private static final Map<Integer, CompoundTag> NPC_AI_DEBUG_SNAPSHOTS = new HashMap<>();

    @Info("客户端打开NPC编辑器")
    public static void openNpcEditor(@Nullable CompoundTag tag) {
        Minecraft.getInstance().options.setCameraType(CameraType.THIRD_PERSON_BACK);
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow == null) return;
        Editor editor = editorWindow.getCurrentEditor();
        if (editor == null) return;
        if (tag != null && !tag.isEmpty()) {
            if (!isProjectFileTag(tag)) {
                showEditorOpenFailure(Component.translatable("viscript_npc.editor.open_project.invalid_file"));
                return;
            }
            var project = (NPCProject) NPCProject.PROVIDER.projectCreator.get();
            project.initNewProject();
            try {
                project.deserializeNBT(Platform.getFrozenRegistry(), tag);
                editor.loadProject(project, null);
                return;
            } catch (Exception exception) {
                showEditorOpenFailure(Component.translatable("viscript_npc.editor.open_project.failed",
                        exception.getClass().getSimpleName()));
                return;
            }
        }
        if (cacheNpcProject != null) {
            editor.loadProject(cacheNpcProject, null);
        }
    }

    private static boolean isProjectFileTag(CompoundTag tag) {
        return tag.contains("data", Tag.TAG_COMPOUND)
                && tag.getCompound("data").contains("npc", Tag.TAG_COMPOUND);
    }

    private static void showEditorOpenFailure(Component message) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(message, false);
        }
    }

    public static int findNearestNpcAiWorldTestTarget(@Nullable String npcType) {
        CustomNpc npc = findNearestNpcAiWorldTestTargetEntity(npcType);
        return npc == null ? -1 : npc.getId();
    }

    public static int findNearestNpcAiDebugTarget(@Nullable String npcType) {
        return findNearestNpcAiWorldTestTarget(npcType);
    }

    public static String getClientNpcWorldTestName(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getEntity(entityId) instanceof CustomNpc npc)) {
            return "";
        }
        return npc.getNpcType() + " #" + entityId;
    }

    public static String getClientNpcDebugName(int entityId) {
        return getClientNpcWorldTestName(entityId);
    }

    public static void setNpcAiDebugSnapshot(CompoundTag payload) {
        if (payload == null || !payload.contains("entityId")) {
            return;
        }
        NPC_AI_DEBUG_SNAPSHOTS.put(payload.getInt("entityId"), payload.copy());
    }

    public static CompoundTag getNpcAiDebugSnapshot(int entityId) {
        CompoundTag payload = NPC_AI_DEBUG_SNAPSHOTS.get(entityId);
        return payload == null ? new CompoundTag() : payload.copy();
    }

    public static void enterNpcAiWorldTestMode(int targetEntityId) {
        Minecraft minecraft = Minecraft.getInstance();
        if (targetEntityId < 0) {
            targetEntityId = resolveNpcAiWorldTestEntityId();
        }
        npcAiWorldTestActiveEntityId = targetEntityId;
        boolean targetChanged = npcAiWorldTestEntityId != targetEntityId;
        boolean wasActive = isNpcAiWorldInteractionMode();
        if (npcAiWorldTestEntityId != targetEntityId) {
            npcAiWorldTestEntityId = targetEntityId;
            clearNpcAiWorldFixedCamera();
        }
        minecraft.options.setCameraType(CameraType.THIRD_PERSON_BACK);
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow != null) {
            restoreEditorWindowForWorldTest(editorWindow);
            if (editorWindow.getCurrentEditor() instanceof NpcEditor npcEditor) {
                npcEditor.applyNpcAiWorldTestLayout();
            }
        }
        setNpcAiWorldInteractionMode(true);
    }

    public static void toggleNpcAiWorldInteractionMode() {
        if (isNpcAiWorldInteractionMode()) {
            setNpcAiWorldInteractionMode(false);
        } else {
            enterNpcAiWorldTestMode(resolveNpcAiWorldTestEntityId());
        }
    }

    public static void setNpcAiWorldInteractionMode(boolean enabled) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean active = enabled && isNpcEditorScreen(minecraft.screen);
        if (npcAiWorldInteractionMode == active) {
            return;
        }
        if (active) {
            ensureNpcAiWorldTestEntityId();
        }
        npcAiWorldInteractionMode = active;
        if (!active) {
            KeyMapping.releaseAll();
            npcAiWorldBlockHit = null;
            clearNpcAiWorldPath();
            stopNpcAiWorldMobDrag();
            restoreEditorAfterWorldTest();
        }
    }

    public static boolean isNpcAiWorldInteractionMode() {
        return npcAiWorldInteractionMode && isNpcEditorScreen(Minecraft.getInstance().screen);
    }

    public static void stopNpcAiWorldInteractionMode() {
        setNpcAiWorldInteractionMode(false);
    }

    public static void clearNpcAiWorldTestMode() {
        setNpcAiWorldInteractionMode(false);
        npcAiWorldTestEntityId = -1;
        npcAiWorldBlockHit = null;
        clearNpcAiWorldPath();
        stopNpcAiWorldMobDrag();
        clearNpcAiWorldFixedCamera();
    }

    public static boolean isNpcEditorScreen(@Nullable Screen screen) {
        EditorWindow editorWindow = getEditorWindow(screen);
        return editorWindow != null && NpcEditor.EDITOR_ID.equals(editorWindow.windowID);
    }

    public static boolean isMouseOverNpcEditorUi() {
        return isMouseOverNpcEditorWindow();
    }

    public static void updateNpcEditorMousePosition(double mouseX, double mouseY) {
        npcEditorMouseKnown = true;
        npcEditorMouseX = mouseX;
        npcEditorMouseY = mouseY;
        updateNpcAiWorldBlockHit();
    }

    public static void requestNpcAiWorldPath() {
        if (!isNpcAiWorldInteractionMode() || !npcAiWorldPathVisible) {
            clearNpcAiWorldPath();
            return;
        }
        int entityId = ensureNpcAiWorldTestEntityId();
        if (entityId < 0) {
            clearNpcAiWorldPath();
            return;
        }
        RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_NPC_AI_WORLD_TEST_PATH, entityId);
    }

    public static void receiveNpcAiWorldPath(CompoundTag tag) {
        int entityId = tag.getInt("entityId");
        if (entityId != npcAiWorldTestEntityId) {
            return;
        }
        ListTag pointsTag = tag.getList("points", Tag.TAG_COMPOUND);
        List<Vec3> points = new ArrayList<>(pointsTag.size());
        for (Tag pointTag : pointsTag) {
            if (pointTag instanceof CompoundTag point) {
                points.add(new Vec3(point.getDouble("x"), point.getDouble("y"), point.getDouble("z")));
            }
        }
        npcAiWorldPathEntityId = entityId;
        npcAiWorldPathPoints = points;
        Minecraft minecraft = Minecraft.getInstance();
        npcAiWorldPathExpireTick = minecraft.level == null ? 0L : minecraft.level.getGameTime() + 10L;
    }

    public static List<Vec3> getNpcAiWorldPathPoints() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNpcAiWorldInteractionMode()
                || !npcAiWorldPathVisible
                || npcAiWorldPathEntityId != npcAiWorldTestEntityId
                || minecraft.level == null
                || minecraft.level.getGameTime() > npcAiWorldPathExpireTick) {
            return List.of();
        }
        return npcAiWorldPathPoints;
    }

    public static void toggleNpcAiWorldPathVisible() {
        npcAiWorldPathVisible = !npcAiWorldPathVisible;
        if (!npcAiWorldPathVisible) {
            clearNpcAiWorldPath();
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null) {
            player.displayClientMessage(Component.translatable(npcAiWorldPathVisible
                    ? "viscript_npc.editor.world_test.path.enabled"
                    : "viscript_npc.editor.world_test.path.disabled"), true);
        }
    }

    public static boolean shouldForwardNpcAiWorldInput(@Nullable Screen screen, double mouseX, double mouseY) {
        return isNpcAiWorldInteractionMode()
                && isNpcEditorScreen(screen)
                && !isMouseOverNpcEditorWindow(mouseX, mouseY);
    }

    public static boolean shouldForwardNpcAiWorldInput(@Nullable Screen screen) {
        return isNpcAiWorldInteractionMode()
                && isNpcEditorScreen(screen)
                && !isMouseOverNpcEditorWindow();
    }

    public static boolean isMouseOverNpcEditorWindow() {
        if (!npcEditorMouseKnown) {
            return false;
        }
        return isMouseOverNpcEditorWindow(npcEditorMouseX, npcEditorMouseY);
    }

    public static boolean isMouseOverNpcEditorWindow(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        EditorWindow editorWindow = getEditorWindow(minecraft.screen);
        if (editorWindow == null) {
            return false;
        }
        return editorWindow.window.isMouseOver((float) mouseX, (float) mouseY);
    }

    public static void rotateNpcAiWorldCamera(double dragX, double dragY) {
        clearNpcAiWorldFixedCamera();
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        minecraft.player.turn(dragX * NPC_AI_WORLD_CAMERA_DRAG_SCALE, dragY * NPC_AI_WORLD_CAMERA_DRAG_SCALE);
        updateNpcAiWorldBlockHit();
    }

    public static void lookNpcAiWorldCameraInPlace(double dragX, double dragY) {
        if (!ensureNpcAiWorldFixedCamera()) {
            return;
        }
        Vector3f worldUp = new Vector3f(0.0F, 1.0F, 0.0F);
        Vector3f lookDir = lookVector(npcAiWorldFixedCameraYaw, npcAiWorldFixedCameraPitch).toVector3f();
        Vector3f pitchAxis = new Vector3f(lookDir).cross(worldUp);
        if (pitchAxis.lengthSquared() < 1.0E-6F) {
            pitchAxis.set(1.0F, 0.0F, 0.0F);
        } else {
            pitchAxis.normalize();
        }
        lookDir.rotate(new Quaternionf(new AxisAngle4f((float) Math.toRadians(-dragY), pitchAxis)));
        lookDir.rotate(new Quaternionf(new AxisAngle4f((float) Math.toRadians(-dragX), worldUp)));
        applyNpcAiWorldFixedCameraLookDirection(lookDir);
        updateNpcAiWorldBlockHit();
    }

    public static boolean selectNpcAiWorldHotbarSlot(int keyCode, int scanCode) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        for (int i = 0; i < minecraft.options.keyHotbarSlots.length; i++) {
            if (minecraft.options.keyHotbarSlots[i].matches(keyCode, scanCode)) {
                minecraft.player.getInventory().selected = i;
                return true;
            }
        }
        return false;
    }

    public static void adjustNpcAiWorldCameraDistance(double scrollDelta) {
        if (scrollDelta == 0.0D) {
            return;
        }
        if (shouldOverrideNpcAiWorldCamera()) {
            double direction = scrollDelta > 0.0D ? 1.0D : -1.0D;
            npcAiWorldFixedCameraPosition = npcAiWorldFixedCameraPosition.add(lookVector(npcAiWorldFixedCameraYaw, npcAiWorldFixedCameraPitch).scale(direction));
            updateNpcAiWorldBlockHit();
            return;
        }
        npcAiWorldCameraDistance = Mth.clamp(npcAiWorldCameraDistance + (scrollDelta > 0.0D ? -1.0F : 1.0F), 2.0F, 64.0F);
        updateNpcAiWorldBlockHit();
    }

    public static float getNpcAiWorldCameraDistance(float fallback) {
        return isNpcAiWorldInteractionMode() ? npcAiWorldCameraDistance : fallback;
    }

    public static boolean breakNpcAiWorldTargetBlock() {
        BlockHitResult hit = getNpcAiWorldBlockHit();
        int targetEntityId = ensureNpcAiWorldTestEntityId();
        if (hit == null || targetEntityId < 0) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        RPCPacketDistributor.rpcToServer(C2SPayload.BREAK_NPC_AI_WORLD_TEST_BLOCK, targetEntityId,
                pos.getX(), pos.getY(), pos.getZ(), hit.getDirection().ordinal(),
                location.x(), location.y(), location.z());
        return true;
    }

    public static boolean useNpcAiWorldTargetBlock() {
        BlockHitResult hit = getNpcAiWorldBlockHit();
        int targetEntityId = ensureNpcAiWorldTestEntityId();
        if (hit == null || targetEntityId < 0) {
            return false;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 location = hit.getLocation();
        RPCPacketDistributor.rpcToServer(C2SPayload.USE_NPC_AI_WORLD_TEST_BLOCK, targetEntityId,
                pos.getX(), pos.getY(), pos.getZ(), hit.getDirection().ordinal(),
                location.x(), location.y(), location.z());
        return true;
    }

    public static boolean tryStartNpcAiWorldMobDrag() {
        if (!canDragNpcAiWorldMob()) {
            return false;
        }
        EntityHitResult hit = getNpcAiWorldMobHit();
        if (hit == null || !(hit.getEntity() instanceof Mob)) {
            return false;
        }
        npcAiWorldDraggedMobId = hit.getEntity().getId();
        npcAiWorldDraggedMobTarget = null;
        npcAiWorldLastSentMobDragTarget = null;
        return true;
    }

    public static boolean updateNpcAiWorldMobDrag() {
        if (!isNpcAiWorldMobDragging()) {
            return false;
        }
        Vec3 target = getNpcAiWorldMobDragTarget();
        if (target == null) {
            return false;
        }
        npcAiWorldDraggedMobTarget = target;
        if (npcAiWorldLastSentMobDragTarget != null
                && npcAiWorldLastSentMobDragTarget.distanceToSqr(target) < 0.01D) {
            return true;
        }
        int targetEntityId = ensureNpcAiWorldTestEntityId();
        if (targetEntityId < 0) {
            stopNpcAiWorldMobDrag();
            return false;
        }
        npcAiWorldLastSentMobDragTarget = target;
        RPCPacketDistributor.rpcToServer(C2SPayload.MOVE_NPC_AI_WORLD_TEST_MOB,
                targetEntityId, npcAiWorldDraggedMobId, target.x(), target.y(), target.z());
        return true;
    }

    public static void stopNpcAiWorldMobDrag() {
        npcAiWorldDraggedMobId = -1;
        npcAiWorldDraggedMobTarget = null;
        npcAiWorldLastSentMobDragTarget = null;
    }

    public static boolean isNpcAiWorldMobDragging() {
        return npcAiWorldDraggedMobId >= 0 && isNpcAiWorldInteractionMode();
    }

    @Nullable
    public static AABB getNpcAiWorldMobHighlightBox() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNpcAiWorldInteractionMode() || minecraft.level == null) {
            return null;
        }
        Entity entity = null;
        if (isNpcAiWorldMobDragging()) {
            entity = minecraft.level.getEntity(npcAiWorldDraggedMobId);
        } else {
            EntityHitResult hit = getNpcAiWorldMobHit();
            if (hit != null) {
                entity = hit.getEntity();
            }
        }
        return entity instanceof Mob ? entity.getBoundingBox().inflate(0.08D) : null;
    }

    @Nullable
    public static BlockHitResult getNpcAiWorldBlockHit() {
        updateNpcAiWorldBlockHit();
        return npcAiWorldBlockHit;
    }

    @Nullable
    public static BlockPos getNpcAiWorldHighlightBlockPos() {
        BlockHitResult hit = getNpcAiWorldBlockHit();
        if (hit == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getMainHandItem().getItem() instanceof BlockItem) {
            return hit.getBlockPos().relative(hit.getDirection());
        }
        return hit.getBlockPos();
    }

    public static boolean shouldHideNpcAiWorldPlayer() {
        return isNpcAiWorldInteractionMode();
    }

    public static boolean shouldOverrideNpcAiWorldCamera() {
        return isNpcAiWorldInteractionMode() && npcAiWorldFixedCamera && npcAiWorldFixedCameraPosition != null;
    }

    public static Vec3 getNpcAiWorldFixedCameraPosition() {
        return npcAiWorldFixedCameraPosition == null ? Vec3.ZERO : npcAiWorldFixedCameraPosition;
    }

    public static float getNpcAiWorldFixedCameraYaw() {
        return npcAiWorldFixedCameraYaw;
    }

    public static float getNpcAiWorldFixedCameraPitch() {
        return npcAiWorldFixedCameraPitch;
    }

    private static void updateNpcAiWorldBlockHit() {
        Minecraft minecraft = Minecraft.getInstance();
        WorldRay ray = getNpcAiWorldMouseRay();
        if (ray == null || minecraft.level == null || minecraft.player == null) {
            npcAiWorldBlockHit = null;
            return;
        }
        HitResult hit = minecraft.level.clip(new ClipContext(ray.start(), ray.end(), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, minecraft.player));
        npcAiWorldBlockHit = hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK ? blockHit : null;
    }

    @Nullable
    private static EntityHitResult getNpcAiWorldMobHit() {
        Minecraft minecraft = Minecraft.getInstance();
        WorldRay ray = getNpcAiWorldMouseRay();
        if (ray == null || minecraft.level == null || minecraft.player == null || !canDragNpcAiWorldMob()) {
            return null;
        }
        Vec3 end = ray.end();
        AABB searchBox = new AABB(ray.start(), end).inflate(1.0D);
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(minecraft.level, minecraft.player,
                ray.start(), end, searchBox, entity -> entity instanceof Mob && entity.isAlive() && entity.isPickable(), 0.3F);
        if (entityHit == null) {
            return null;
        }
        BlockHitResult blockHit = getNpcAiWorldBlockHit();
        if (blockHit != null
                && blockHit.getLocation().distanceToSqr(ray.start()) + 0.01D < entityHit.getLocation().distanceToSqr(ray.start())) {
            return null;
        }
        return entityHit;
    }

    @Nullable
    private static Vec3 getNpcAiWorldMobDragTarget() {
        BlockHitResult hit = getNpcAiWorldBlockHit();
        if (hit == null) {
            return null;
        }
        Vec3 location = hit.getLocation();
        double y = mobDragFeetY(hit);
        return new Vec3(location.x(), y, location.z());
    }

    private static double mobDragFeetY(BlockHitResult hit) {
        Direction direction = hit.getDirection();
        BlockPos blockPos = hit.getBlockPos();
        if (direction == Direction.UP) {
            return blockPos.getY() + 1.0D;
        }
        if (direction == Direction.DOWN) {
            return blockPos.getY() - 0.05D;
        }
        return Math.floor(hit.getLocation().y());
    }

    private static boolean canDragNpcAiWorldMob() {
        Minecraft minecraft = Minecraft.getInstance();
        return isNpcAiWorldInteractionMode()
                && ensureNpcAiWorldTestEntityId() >= 0
                && minecraft.player != null
                && minecraft.player.getMainHandItem().isEmpty();
    }

    @Nullable
    private static WorldRay getNpcAiWorldMouseRay() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!isNpcAiWorldInteractionMode()
                || minecraft.level == null
                || minecraft.player == null
                || !npcEditorMouseKnown
                || isMouseOverNpcEditorWindow(npcEditorMouseX, npcEditorMouseY)) {
            return null;
        }
        Vec3 start;
        Vec3 direction;
        if (shouldOverrideNpcAiWorldCamera()) {
            start = getNpcAiWorldFixedCameraPosition();
            direction = mouseRayDirection(npcAiWorldFixedCameraYaw, npcAiWorldFixedCameraPitch);
        } else {
            Camera camera = minecraft.gameRenderer.getMainCamera();
            if (!camera.isInitialized()) {
                return null;
            }
            start = camera.getPosition();
            direction = camera.getNearPlane().getPointOnPlane(mouseLeftScale(), mouseUpScale()).normalize();
        }
        double reach = Math.max(64.0D, npcAiWorldCameraDistance + minecraft.player.blockInteractionRange());
        return new WorldRay(start, direction, reach);
    }

    private static boolean ensureNpcAiWorldFixedCamera() {
        if (shouldOverrideNpcAiWorldCamera()) {
            return true;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        Camera camera = minecraft.gameRenderer.getMainCamera();
        if (camera.isInitialized()) {
            npcAiWorldFixedCameraPosition = camera.getPosition();
            npcAiWorldFixedCameraYaw = camera.getYRot();
            npcAiWorldFixedCameraPitch = camera.getXRot();
        } else {
            npcAiWorldFixedCameraPosition = minecraft.player.getEyePosition();
            npcAiWorldFixedCameraYaw = minecraft.player.getYRot();
            npcAiWorldFixedCameraPitch = minecraft.player.getXRot();
        }
        npcAiWorldFixedCamera = true;
        return true;
    }

    private static void clearNpcAiWorldFixedCamera() {
        npcAiWorldFixedCamera = false;
        npcAiWorldFixedCameraPosition = null;
    }

    private static Vec3 mouseRayDirection(float yaw, float pitch) {
        Window window = Minecraft.getInstance().getWindow();
        double aspect = (double) window.getWidth() / (double) window.getHeight();
        double upSize = Math.tan((double) ((float) Minecraft.getInstance().options.fov().get().intValue() * (float) (Math.PI / 180.0)) / 2.0D) * 0.05D;
        double leftSize = upSize * aspect;
        Quaternionf rotation = rotation(yaw, pitch);
        Vec3 forward = new Vec3(new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation)).scale(0.05D);
        Vec3 left = new Vec3(new Vector3f(-1.0F, 0.0F, 0.0F).rotate(rotation)).scale(leftSize);
        Vec3 up = new Vec3(new Vector3f(0.0F, 1.0F, 0.0F).rotate(rotation)).scale(upSize);
        return forward.add(up.scale(mouseUpScale())).subtract(left.scale(mouseLeftScale())).normalize();
    }

    private static Vec3 lookVector(float yaw, float pitch) {
        return new Vec3(new Vector3f(0.0F, 0.0F, -1.0F).rotate(rotation(yaw, pitch))).normalize();
    }

    private static void applyNpcAiWorldFixedCameraLookDirection(Vector3f lookDir) {
        lookDir.normalize();
        double horizontal = Math.sqrt(lookDir.x() * lookDir.x() + lookDir.z() * lookDir.z());
        npcAiWorldFixedCameraYaw = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-lookDir.x(), lookDir.z())));
        npcAiWorldFixedCameraPitch = Mth.clamp((float) Math.toDegrees(Math.atan2(-lookDir.y(), horizontal)), -90.0F, 90.0F);
    }

    private static Quaternionf rotation(float yaw, float pitch) {
        return new Quaternionf().rotationYXZ((float) Math.PI - yaw * (float) (Math.PI / 180.0), -pitch * (float) (Math.PI / 180.0), 0.0F);
    }

    private static float mouseLeftScale() {
        Window window = Minecraft.getInstance().getWindow();
        double pixelX = npcEditorMouseX * window.getWidth() / (double) window.getGuiScaledWidth();
        return (float) (pixelX / window.getWidth() * 2.0D - 1.0D);
    }

    private static float mouseUpScale() {
        Window window = Minecraft.getInstance().getWindow();
        double pixelY = npcEditorMouseY * window.getHeight() / (double) window.getGuiScaledHeight();
        return (float) (1.0D - pixelY / window.getHeight() * 2.0D);
    }

    @Nullable
    private static EditorWindow getCurrentEditorWindow() {
        return getEditorWindow(Minecraft.getInstance().screen);
    }

    @Nullable
    private static EditorWindow getEditorWindow(@Nullable Screen currentScreen) {
        if (currentScreen instanceof ModularUIContainerScreen screen
                && screen.getMenu().getModularUI().ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        if (currentScreen instanceof ModularUIScreen screen
                && screen.modularUI.ui.rootElement instanceof EditorWindow editorWindow) {
            return editorWindow;
        }
        return null;
    }

    private static void restoreEditorWindowForWorldTest(EditorWindow editorWindow) {
        Minecraft minecraft = Minecraft.getInstance();
        editorWindow.retoreWindow();
        int guiWidth = minecraft.getWindow().getGuiScaledWidth();
        int guiHeight = minecraft.getWindow().getGuiScaledHeight();
        float width = Math.min(guiWidth, Math.max(360f, guiWidth * 0.40f));
        float height = guiHeight;
        float left = guiWidth / 2f - width;
        float top = -guiHeight / 2f;
        editorWindow.layout(layout -> layout.widthPercent(100).heightPercent(100));
        editorWindow.window.layout(layout -> layout
                .positionType(TaffyPosition.ABSOLUTE)
                .paddingAll(3)
                .left(left)
                .top(top)
                .width(width)
                .height(height));
    }

    private static void restoreEditorAfterWorldTest() {
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow != null && editorWindow.getCurrentEditor() instanceof NpcEditor npcEditor) {
            npcEditor.restoreSelectedNpcEditorPageLayout();
        }
    }

    private static int ensureNpcAiWorldTestEntityId() {
        if (isClientCustomNpc(npcAiWorldTestEntityId)) {
            return npcAiWorldTestEntityId;
        }
        npcAiWorldTestEntityId = -1;
        int targetEntityId = resolveNpcAiWorldTestEntityId();
        if (targetEntityId >= 0) {
            npcAiWorldTestEntityId = targetEntityId;
            npcAiWorldTestActiveEntityId = targetEntityId;
        }
        return npcAiWorldTestEntityId;
    }

    private static int resolveNpcAiWorldTestEntityId() {
        if (isClientCustomNpc(npcAiWorldTestActiveEntityId)) {
            return npcAiWorldTestActiveEntityId;
        }
        NPCProject project = getCurrentNpcProject();
        return project == null ? -1 : findNearestNpcAiWorldTestTarget(project.getCurrentNpcType());
    }

    private static boolean isClientCustomNpc(int entityId) {
        Minecraft minecraft = Minecraft.getInstance();
        return entityId >= 0
                && minecraft.level != null
                && minecraft.level.getEntity(entityId) instanceof CustomNpc;
    }

    @Nullable
    private static NPCProject getCurrentNpcProject() {
        EditorWindow editorWindow = getCurrentEditorWindow();
        if (editorWindow == null || !(editorWindow.getCurrentEditor() instanceof NpcEditor npcEditor)) {
            return null;
        }
        return npcEditor.getCurrentProject() instanceof NPCProject project ? project : null;
    }

    @Nullable
    private static CustomNpc findNearestNpcAiWorldTestTargetEntity(@Nullable String npcType) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return null;
        }
        String targetType = npcType == null ? "" : npcType.trim();
        return minecraft.level.getEntitiesOfClass(CustomNpc.class,
                        player.getBoundingBox().inflate(NPC_AI_WORLD_TEST_TARGET_RADIUS))
                .stream()
                .filter(npc -> targetType.isEmpty() || targetType.equals(npc.getNpcType()))
                .min(Comparator.comparingDouble(player::distanceToSqr))
                .orElse(null);
    }

    private static void clearNpcAiWorldPath() {
        npcAiWorldPathEntityId = -1;
        npcAiWorldPathExpireTick = 0L;
        npcAiWorldPathPoints = List.of();
    }

    private record WorldRay(Vec3 start, Vec3 direction, double reach) {
        private Vec3 end() {
            return start.add(direction.scale(reach));
        }
    }
}
