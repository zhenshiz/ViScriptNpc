package com.viscript.npc.gui.scene;

import com.lowdragmc.lowdraglib2.client.scene.ImmediateWorldSceneRenderer;
import com.lowdragmc.lowdraglib2.client.scene.WorldSceneRenderer;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.viscript.npc.mixin.WorldSceneRendererAccessor;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.test.NpcAiTestEnvironment;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Consumer;

/**
 * 渲染并控制当前真实客户端世界中的有限区域。
 *
 * <p>该视口只负责渲染和摄像机输入。嵌入编辑器后，可以在此组件上继续叠加实体选择、方块交互、覆盖层和编辑器工具。
 */
public class RealLevelSceneViewport extends UIElement {
    private static final int REGION_MIN_Y_OFFSET = 4;
    private static final int REGION_MAX_Y_OFFSET = 8;
    private static final float SCENE_FOV_DEGREES = 60.0F;
    private static final double PICK_DISTANCE = 10000.0D;
    private static final Object ORBIT_DRAG = new Object();
    private static final Object LOOK_IN_PLACE_DRAG = new Object();
    private static final Object PAN_DRAG = new Object();
    private static final Object SCENE_INTERACTION_DRAG = new Object();
    // 原版线框会绕世界原点缩放，远离原点的随机测试场地会因此产生明显位置偏移。
    private static final RenderType SCENE_OUTLINE = RenderType.create(
            "viscript_npc_scene_outline",
            DefaultVertexFormat.POSITION_COLOR_NORMAL,
            VertexFormat.Mode.LINES,
            1536,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_LINES_SHADER)
                    .setLineState(new RenderStateShard.LineStateShard(OptionalDouble.empty()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_DEPTH_WRITE)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false));

    private final BlockPos sceneCenter;
    private final SceneCameraController cameraController;
    private int arenaWidth;
    private int arenaLength;
    private ClientLevel level;
    private WorldSceneRenderer renderer;
    private Region region;
    private SceneStats sceneStats;
    private Consumer<SceneStats> sceneChangedListener = ignored -> {
    };
    private boolean cameraModified;
    private boolean automaticFramePending;
    private boolean rightButtonDragged;
    private boolean renderLocalPlayer;
    private int highlightedEntityId = -1;
    private int hoveredEntityId = -1;
    private final Map<Integer, Vector2f> entityScreenPositions = new HashMap<>();
    private ScenePick currentScenePick = new ScenePick(null, null, null);
    @Nullable
    private BlockPos hoveredBlockPos;
    private int syncCheckFrames;
    private SceneInteractionHandler interactionHandler = SceneInteractionHandler.EMPTY;

    /**
     * 创建一个以当前客户端世界中指定方块位置为中心的视口。
     *
     * @param sceneCenter 用于收集渲染区域的中心方块位置
     * @throws IllegalStateException 当前没有活动的客户端世界时抛出
     */
    public RealLevelSceneViewport(BlockPos sceneCenter) {
        this(sceneCenter, NpcAiTestEnvironment.DEFAULT_ARENA_WIDTH,
                NpcAiTestEnvironment.DEFAULT_ARENA_LENGTH);
    }

    /**
     * 创建一个按测试场地尺寸收集真实世界内容的视口。
     *
     * @param sceneCenter 用于收集渲染区域的中心方块位置
     * @param arenaWidth 场地沿 X 轴的方块数
     * @param arenaLength 场地沿 Z 轴的方块数
     * @throws IllegalStateException 当前没有活动的客户端世界时抛出
     */
    public RealLevelSceneViewport(BlockPos sceneCenter, int arenaWidth, int arenaLength) {
        this.sceneCenter = sceneCenter.immutable();
        this.arenaWidth = NpcAiTestEnvironment.normalizeArenaSize(arenaWidth);
        this.arenaLength = NpcAiTestEnvironment.normalizeArenaSize(arenaLength);
        this.level = requireLevel();
        this.region = collectRegion(level, this.sceneCenter, this.arenaWidth, this.arenaLength);
        this.sceneStats = createSceneStats(level, region);
        this.cameraController = new EditorSceneCameraController(region.center(), region.cameraRadius());
        this.automaticFramePending = region.visibleBlockCount() < completeArenaBlockCount();
        addClass("preview_bg");
        setOverflowVisible(false);
        layout(layout -> layout.widthPercent(100).flex(1));
        addEventListener(UIEvents.MOUSE_DOWN, this::handleMouseDown);
        addEventListener(UIEvents.DRAG_SOURCE_UPDATE, this::handleCameraDrag);
        addEventListener(UIEvents.DRAG_END, this::handleDragEnd);
        addEventListener(UIEvents.MOUSE_WHEEL, this::handleMouseWheel);
        renderer = createRenderer(level, region);
    }

    /**
     * 更新视口收集和取景使用的场地尺寸。
     *
     * @param width 场地沿 X 轴的方块数
     * @param length 场地沿 Z 轴的方块数
     * @return 当前视口
     */
    public RealLevelSceneViewport setArenaSize(int width, int length) {
        int normalizedWidth = NpcAiTestEnvironment.normalizeArenaSize(width);
        int normalizedLength = NpcAiTestEnvironment.normalizeArenaSize(length);
        if (arenaWidth == normalizedWidth && arenaLength == normalizedLength) {
            return this;
        }
        arenaWidth = normalizedWidth;
        arenaLength = normalizedLength;
        Region resizedRegion = collectRegion(level, sceneCenter, arenaWidth, arenaLength);
        renderer.releaseResource();
        region = resizedRegion;
        cameraModified = false;
        automaticFramePending = region.visibleBlockCount() < completeArenaBlockCount();
        cameraController.frame(region.center(), region.cameraRadius());
        sceneStats = createSceneStats(level, region);
        sceneChangedListener.accept(sceneStats);
        renderer = createRenderer(level, region);
        syncCheckFrames = 0;
        return this;
    }

    /**
     * 获取视口当前使用的场地宽度。
     *
     * @return 沿 X 轴收集的方块数
     */
    public int getArenaWidth() {
        return arenaWidth;
    }

    /**
     * 获取视口当前使用的场地长度。
     *
     * @return 沿 Z 轴收集的方块数
     */
    public int getArenaLength() {
        return arenaLength;
    }

    /**
     * 注册一个接收当前场景内容统计的监听器。
     *
     * <p>注册时会立即调用一次，之后在方块、流体或实体数量变化时再次调用。
     *
     * @param listener 接收场景内容统计的回调
     * @return 当前视口
     */
    public RealLevelSceneViewport setSceneChangedListener(Consumer<SceneStats> listener) {
        sceneChangedListener = Objects.requireNonNull(listener);
        sceneChangedListener.accept(sceneStats);
        return this;
    }

    /**
     * 获取当前视口使用的摄像机控制器。
     *
     * @return 可供编辑器集成和测试使用的可变摄像机控制器
     */
    public SceneCameraController getCameraController() {
        return cameraController;
    }

    /**
     * 获取当前渲染区域中包含的非空气方块数量。
     *
     * @return 可见方块数量
     */
    public int getVisibleBlockCount() {
        return region.visibleBlockCount();
    }

    /**
     * 获取当前渲染区域中的流体方块数量。
     *
     * @return 流体方块数量
     */
    public int getVisibleFluidCount() {
        return region.visibleFluidCount();
    }

    /**
     * 获取当前渲染区域中的真实世界实体数量。
     *
     * @return 当前允许渲染的实体数量；是否包含本地玩家由视口设置决定
     */
    public int getVisibleEntityCount() {
        return collectRenderableEntities(level, region).size();
    }

    /**
     * 获取用于收集渲染区域的中心方块位置。
     *
     * @return 不可变的场景中心方块位置
     */
    public BlockPos getSceneCenter() {
        return sceneCenter;
    }

    /**
     * 注册场景拾取和编辑交互处理器。
     *
     * <p>处理器只接收客户端当前帧的拾取结果，实际世界修改仍应由服务器权威逻辑完成。
     *
     * @param interactionHandler 接收左键拖拽与右键单击的处理器
     * @return 当前视口
     */
    public RealLevelSceneViewport setInteractionHandler(SceneInteractionHandler interactionHandler) {
        this.interactionHandler = Objects.requireNonNull(interactionHandler);
        return this;
    }

    /**
     * 设置是否在场景副本中绘制当前客户端玩家。
     *
     * @param renderLocalPlayer 是否绘制当前客户端玩家
     * @return 当前视口
     */
    public RealLevelSceneViewport setRenderLocalPlayer(boolean renderLocalPlayer) {
        if (this.renderLocalPlayer == renderLocalPlayer) {
            return this;
        }
        this.renderLocalPlayer = renderLocalPlayer;
        SceneStats updatedStats = createSceneStats(level, region);
        if (!updatedStats.equals(sceneStats)) {
            sceneStats = updatedStats;
            sceneChangedListener.accept(sceneStats);
        }
        return this;
    }

    /**
     * 获取最近一次真实渲染帧计算出的场景拾取结果。
     *
     * @return 当前鼠标对应的实体、方块面和世界位置
     */
    public ScenePick getCurrentScenePick() {
        return currentScenePick;
    }

    /**
     * 设置需要在场景中绘制选择框的实体编号。
     *
     * @param entityId 客户端实体编号；传入 {@code -1} 时清除选择框
     * @return 当前视口
     */
    public RealLevelSceneViewport setHighlightedEntityId(int entityId) {
        highlightedEntityId = entityId;
        return this;
    }

    /**
     * 获取鼠标最近一次指向的方块位置。
     *
     * @return 当前悬停方块位置；鼠标没有指向方块时返回 {@code null}
     */
    @Nullable
    public BlockPos getHoveredBlockPos() {
        return hoveredBlockPos;
    }

    /**
     * 获取鼠标最近一次指向的实体编号。
     *
     * @return 当前悬停实体编号；鼠标没有指向实体时返回 {@code -1}
     */
    public int getHoveredEntityId() {
        return hoveredEntityId;
    }

    /**
     * 返回实体最近一帧投影到当前界面表面的屏幕坐标。
     *
     * <p>坐标指向实体插值边界盒的中心，可用于界面覆盖层和基于实体位置的输入操作。
     *
     * @param entityId 客户端实体编号
     * @return 屏幕坐标副本；实体未被当前场景渲染时返回 {@code null}
     */
    @Nullable
    public Vector2f getEntityScreenPosition(int entityId) {
        Vector2f position = entityScreenPositions.get(entityId);
        return position == null ? null : new Vector2f(position);
    }

    private WorldSceneRenderer createRenderer(ClientLevel level, Region region) {
        WorldSceneRenderer worldSceneRenderer = new ImmediateWorldSceneRenderer(level)
                .useCacheBuffer(true)
                .syncCompile(true)
                .setClipBlock(ClipContext.Block.VISUAL)
                .setClipFluid(ClipContext.Fluid.SOURCE_ONLY)
                .setFov(SCENE_FOV_DEGREES)
                .addRenderedBlocks(List.copyOf(region.blocks()), null);
        worldSceneRenderer.setBeforeBatchEnd((buffers, partialTicks) ->
                renderLevelEntities(worldSceneRenderer, level, region, buffers, partialTicks));
        worldSceneRenderer.setAfterWorldRender(this::renderSceneHighlights);
        applyCamera(worldSceneRenderer);
        return worldSceneRenderer;
    }

    private void renderLevelEntities(WorldSceneRenderer sceneRenderer, ClientLevel sourceLevel, Region sourceRegion,
                                     MultiBufferSource buffers, float partialTicks) {
        Minecraft minecraft = Minecraft.getInstance();
        var renderDispatcher = minecraft.getEntityRenderDispatcher();
        Camera previousCamera = renderDispatcher.camera;
        Entity previousCrosshairEntity = renderDispatcher.crosshairPickEntity;
        Camera sceneCamera = ((WorldSceneRendererAccessor) sceneRenderer).viscript_npc$getCamera();
        PoseStack poseStack = new PoseStack();
        renderDispatcher.prepare(sourceLevel, sceneCamera, null);

        try {
            for (Entity entity : collectRenderableEntities(sourceLevel, sourceRegion)) {
                poseStack.pushPose();
                double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
                double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
                double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
                float yaw = Mth.lerp(partialTicks, entity.yRotO, entity.getYRot());
                int packedLight = renderDispatcher.getPackedLightCoords(entity, partialTicks);
                renderDispatcher.render(entity, x, y, z, yaw, partialTicks, poseStack, buffers, packedLight);
                poseStack.popPose();
            }
        } finally {
            if (previousCamera != null) {
                renderDispatcher.prepare(sourceLevel, previousCamera, previousCrosshairEntity);
            } else {
                renderDispatcher.setLevel(null);
            }
        }
    }

    private void renderSceneHighlights(WorldSceneRenderer sceneRenderer) {
        Minecraft minecraft = Minecraft.getInstance();
        var buffers = minecraft.renderBuffers().bufferSource();
        PoseStack poseStack = new PoseStack();
        updateEntityScreenPositions(sceneRenderer);
        if (hoveredEntityId < 0 && hoveredBlockPos != null) {
            renderBlockBounds(poseStack, buffers, hoveredBlockPos);
        }
        if (hoveredEntityId >= 0) {
            Entity entity = level.getEntity(hoveredEntityId);
            if (entity != null) {
                renderEntityBounds(poseStack, buffers, entity, 0.2F, 0.8F, 1.0F);
            }
        }
        if (highlightedEntityId >= 0 && highlightedEntityId != hoveredEntityId) {
            Entity entity = level.getEntity(highlightedEntityId);
            if (entity != null) {
                renderEntityBounds(poseStack, buffers, entity, 0.2F, 1.0F, 0.3F);
            }
        }
        buffers.endBatch();
    }

    private static void renderBlockBounds(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                          BlockPos blockPos) {
        poseStack.pushPose();
        poseStack.translate(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(SCENE_OUTLINE),
                new AABB(BlockPos.ZERO).inflate(0.002D), 0.2F, 0.8F, 1.0F, 1.0F);
        poseStack.popPose();
    }

    private void updateEntityScreenPositions(WorldSceneRenderer sceneRenderer) {
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        entityScreenPositions.clear();
        for (Entity entity : collectRenderableEntities(level, region)) {
            double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
            double y = Mth.lerp(partialTicks, entity.yOld, entity.getY()) + entity.getBbHeight() * 0.5D;
            double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
            Vector3f projected = sceneRenderer.project(new Vector3f((float) x, (float) y, (float) z));
            if (projected.z() < 0.0F || projected.z() > 1.0F) {
                continue;
            }
            var screenPosition = sceneRenderer.getPositionRectRevert(
                    Math.round(projected.x()), Math.round(projected.y()), 0, 0).position;
            entityScreenPositions.put(entity.getId(), new Vector2f(screenPosition.x, screenPosition.y));
        }
    }

    private static void renderEntityBounds(PoseStack poseStack, MultiBufferSource.BufferSource buffers,
                                           Entity entity, float red, float green, float blue) {
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
        double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
        double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
        AABB bounds = entity.getBoundingBox()
                .move(-entity.getX(), -entity.getY(), -entity.getZ())
                .inflate(0.04D);
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        LevelRenderer.renderLineBox(poseStack, buffers.getBuffer(SCENE_OUTLINE),
                bounds, red, green, blue, 1.0F);
        poseStack.popPose();
    }

    private void handleMouseDown(UIEvent event) {
        if (event.target != this) {
            return;
        }
        if (event.button == 0) {
            if (interactionHandler.beginLeft(updateScenePick(event.x, event.y))) {
                startDrag(SCENE_INTERACTION_DRAG, null);
            } else {
                startDrag(ORBIT_DRAG, null);
            }
        } else if (event.button == 1) {
            rightButtonDragged = false;
            startDrag(LOOK_IN_PLACE_DRAG, null);
        } else if (event.button == 2) {
            startDrag(PAN_DRAG, null);
        } else {
            return;
        }
        event.stopPropagation();
    }

    private void handleCameraDrag(UIEvent event) {
        if (event.currentElement != this || event.dragHandler == null) {
            return;
        }
        var localDelta = getLocalMouseNormal(event.deltaX, event.deltaY);
        Object draggingObject = event.dragHandler.draggingObject;
        if (draggingObject == ORBIT_DRAG) {
            cameraController.orbit(localDelta.x, localDelta.y);
        } else if (draggingObject == LOOK_IN_PLACE_DRAG) {
            if (Math.abs(localDelta.x) + Math.abs(localDelta.y) > 0.5F) {
                rightButtonDragged = true;
            }
            cameraController.lookInPlace(localDelta.x, localDelta.y);
        } else if (draggingObject == PAN_DRAG) {
            cameraController.pan(localDelta.x, localDelta.y, getContentWidth(), getContentHeight());
        } else if (draggingObject == SCENE_INTERACTION_DRAG) {
            interactionHandler.dragLeft(updateScenePick(event.x, event.y));
            event.stopPropagation();
            return;
        } else {
            return;
        }
        cameraModified = true;
        applyCamera(renderer);
        event.stopPropagation();
    }

    private void handleDragEnd(UIEvent event) {
        if (event.currentElement != this || event.dragHandler == null) {
            return;
        }
        Object draggingObject = event.dragHandler.draggingObject;
        if (draggingObject == SCENE_INTERACTION_DRAG) {
            interactionHandler.endLeft(updateScenePick(event.x, event.y));
            event.stopPropagation();
        } else if (draggingObject == LOOK_IN_PLACE_DRAG && !rightButtonDragged) {
            interactionHandler.rightClick(updateScenePick(event.x, event.y));
            event.stopPropagation();
        }
    }

    private void handleMouseWheel(UIEvent event) {
        if (event.target != this) {
            return;
        }
        double scrollDelta = event.deltaY != 0.0f ? event.deltaY : event.deltaX;
        if (scrollDelta == 0.0) {
            return;
        }
        cameraController.zoom(scrollDelta);
        cameraModified = true;
        applyCamera(renderer);
        event.stopPropagation();
    }

    private void applyCamera(WorldSceneRenderer targetRenderer) {
        targetRenderer.setCameraLookAt(
                cameraController.getEyePosition(),
                cameraController.getLookAtPosition(),
                cameraController.getWorldUp());
    }

    private ScenePick updateScenePick(float screenX, float screenY) {
        currentScenePick = pickSceneAt(screenX, screenY);
        hoveredBlockPos = currentScenePick.blockHit() == null
                ? null
                : currentScenePick.blockHit().getBlockPos().immutable();
        hoveredEntityId = currentScenePick.entity() == null ? -1 : currentScenePick.entity().getId();
        return currentScenePick;
    }

    private ScenePick pickSceneAt(float screenX, float screenY) {
        PickRay ray = createPickRay(screenX, screenY);
        Minecraft minecraft = Minecraft.getInstance();
        if (ray == null || minecraft.player == null) {
            return new ScenePick(null, null, null);
        }

        BlockHitResult blockHit = level.clip(new ClipContext(
                ray.origin(), ray.end(), ClipContext.Block.VISUAL,
                ClipContext.Fluid.SOURCE_ONLY, minecraft.player));
        if (blockHit.getType() != HitResult.Type.BLOCK) {
            blockHit = null;
        }
        Vec3 entityRayEnd = blockHit == null ? ray.end() : blockHit.getLocation();
        EntityPick entityPick = findPickedEntity(ray.origin(), entityRayEnd);
        Vec3 worldPosition = blockHit != null
                ? blockHit.getLocation()
                : entityPick == null ? null : entityPick.location();
        return new ScenePick(entityPick == null ? null : entityPick.entity(), blockHit, worldPosition);
    }

    @Nullable
    private PickRay createPickRay(float screenX, float screenY) {
        Vector2f localMouse = getLocalMouse(screenX, screenY);
        float contentWidth = getContentWidth();
        float contentHeight = getContentHeight();
        if (contentWidth <= 0.0F || contentHeight <= 0.0F) {
            return null;
        }
        float horizontal = (localMouse.x - getContentX()) / contentWidth;
        float vertical = (localMouse.y - getContentY()) / contentHeight;
        if (horizontal < 0.0F || horizontal > 1.0F || vertical < 0.0F || vertical > 1.0F) {
            return null;
        }

        Vector3f viewportStart = getLocalToWorldPose().transformPosition(
                new Vector3f(getContentX(), getContentY(), 0.0F));
        Vector3f viewportEnd = getLocalToWorldPose().transformPosition(
                new Vector3f(getContentX() + contentWidth, getContentY() + contentHeight, 0.0F));
        float renderedWidth = Math.abs(viewportEnd.x() - viewportStart.x());
        float renderedHeight = Math.abs(viewportEnd.y() - viewportStart.y());
        if (renderedWidth < 1.0F || renderedHeight < 1.0F) {
            return null;
        }

        Vector3f eyeVector = cameraController.getEyePosition();
        Vector3f lookAtVector = cameraController.getLookAtPosition();
        Vector3f worldUpVector = cameraController.getWorldUp();
        Vec3 eye = new Vec3(eyeVector.x(), eyeVector.y(), eyeVector.z());
        Vec3 forward = new Vec3(
                lookAtVector.x() - eyeVector.x(),
                lookAtVector.y() - eyeVector.y(),
                lookAtVector.z() - eyeVector.z()).normalize();
        Vec3 right = forward.cross(new Vec3(
                worldUpVector.x(), worldUpVector.y(), worldUpVector.z())).normalize();
        Vec3 up = right.cross(forward).normalize();
        double tangent = Math.tan(Math.toRadians(SCENE_FOV_DEGREES * 0.5F));
        double ndcX = horizontal * 2.0D - 1.0D;
        double ndcY = 1.0D - vertical * 2.0D;
        double aspect = renderedWidth / renderedHeight;
        Vec3 direction = forward
                .add(right.scale(ndcX * aspect * tangent))
                .add(up.scale(ndcY * tangent))
                .normalize();
        return new PickRay(eye, eye.add(direction.scale(PICK_DISTANCE)));
    }

    @Nullable
    private EntityPick findPickedEntity(Vec3 rayStart, Vec3 rayEnd) {
        EntityPick closest = null;
        double closestDistance = Double.MAX_VALUE;
        float partialTicks = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(false);
        for (Entity entity : collectRenderableEntities(level, region)) {
            double x = Mth.lerp(partialTicks, entity.xOld, entity.getX());
            double y = Mth.lerp(partialTicks, entity.yOld, entity.getY());
            double z = Mth.lerp(partialTicks, entity.zOld, entity.getZ());
            AABB bounds = entity.getBoundingBox()
                    .move(x - entity.getX(), y - entity.getY(), z - entity.getZ())
                    .inflate(0.2D);
            var hit = bounds.clip(rayStart, rayEnd);
            if (hit.isEmpty()) {
                continue;
            }
            Vec3 location = hit.orElseThrow();
            double distance = rayStart.distanceToSqr(location);
            if (distance < closestDistance) {
                closest = new EntityPick(entity, location);
                closestDistance = distance;
            }
        }
        return closest;
    }

    private void refreshAfterClientSync() {
        if (++syncCheckFrames % 10 != 0) {
            return;
        }
        ClientLevel currentLevel = Minecraft.getInstance().level;
        if (currentLevel == null) {
            return;
        }
        Region syncedRegion = collectRegion(currentLevel, sceneCenter, arenaWidth, arenaLength);
        boolean levelChanged = currentLevel != level;
        if (levelChanged || !syncedRegion.hasSameContent(region)) {
            renderer.releaseResource();
            if (levelChanged) {
                automaticFramePending = true;
            }
            if (!cameraModified && automaticFramePending) {
                cameraController.frame(syncedRegion.center(), syncedRegion.cameraRadius());
            }
            level = currentLevel;
            region = syncedRegion;
            if (region.visibleBlockCount() >= completeArenaBlockCount()) {
                automaticFramePending = false;
            }
            renderer = createRenderer(level, region);
        }

        SceneStats updatedStats = createSceneStats(level, region);
        if (!updatedStats.equals(sceneStats)) {
            sceneStats = updatedStats;
            sceneChangedListener.accept(sceneStats);
        }
    }

    @Override
    public void drawBackgroundAdditional(GUIContext guiContext) {
        refreshAfterClientSync();
        guiContext.graphics.flush();
        updateScenePick(guiContext.mouseX, guiContext.mouseY);
        Vector2f rendererMouse = getRendererMousePosition(guiContext);
        renderer.render(guiContext.pose.pose, getContentX(), getContentY(), getContentWidth(),
                getContentHeight(), Math.round(rendererMouse.x), Math.round(rendererMouse.y));
    }

    private static Vector2f getRendererMousePosition(GUIContext guiContext) {
        Vector3f transformedOrigin = guiContext.pose.last().pose()
                .transformPosition(new Vector3f());
        return new Vector2f(guiContext.mouseX - transformedOrigin.x,
                guiContext.mouseY - transformedOrigin.y);
    }

    @Override
    protected void onRemoved() {
        super.onRemoved();
        if (RenderSystem.isOnRenderThread()) {
            renderer.releaseResource();
        } else {
            RenderSystem.recordRenderCall(renderer::releaseResource);
        }
    }

    private static ClientLevel requireLevel() {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("A real client level is required to create the scene viewport");
        }
        return level;
    }

    private static Region collectRegion(
            ClientLevel level, BlockPos sceneCenter, int arenaWidth, int arenaLength) {
        List<BlockPos> blocks = new ArrayList<>();
        int minX = sceneCenter.getX() - arenaWidth / 2;
        int maxX = minX + arenaWidth - 1;
        int minZ = sceneCenter.getZ() - arenaLength / 2;
        int maxZ = minZ + arenaLength - 1;
        int minY = Math.max(level.getMinBuildHeight(), sceneCenter.getY() - REGION_MIN_Y_OFFSET);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, sceneCenter.getY() + REGION_MAX_Y_OFFSET);
        int visibleBlockCount = 0;
        int visibleFluidCount = 0;
        int contentHash = 1;
        int boundsMinX = Integer.MAX_VALUE;
        int boundsMaxX = Integer.MIN_VALUE;
        int boundsMinY = Integer.MAX_VALUE;
        int boundsMaxY = Integer.MIN_VALUE;
        int boundsMinZ = Integer.MAX_VALUE;
        int boundsMaxZ = Integer.MIN_VALUE;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    blocks.add(pos);
                    visibleBlockCount++;
                    if (!state.getFluidState().isEmpty()) {
                        visibleFluidCount++;
                    }
                    contentHash = 31 * contentHash + pos.hashCode();
                    contentHash = 31 * contentHash + state.hashCode();
                    contentHash = appendLightHash(level, pos, contentHash);
                    boundsMinX = Math.min(boundsMinX, x);
                    boundsMaxX = Math.max(boundsMaxX, x);
                    boundsMinY = Math.min(boundsMinY, y);
                    boundsMaxY = Math.max(boundsMaxY, y);
                    boundsMinZ = Math.min(boundsMinZ, z);
                    boundsMaxZ = Math.max(boundsMaxZ, z);
                }
            }
        }

        if (blocks.isEmpty()) {
            blocks.add(sceneCenter.immutable());
            return new Region(blocks, 0, 0,
                    new Vector3f(sceneCenter.getX() + 0.5f, sceneCenter.getY() + 0.5f, sceneCenter.getZ() + 0.5f),
                    12.0f, contentHash, new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
        }

        Vector3f center = new Vector3f(
                (boundsMinX + boundsMaxX + 1) * 0.5f,
                (boundsMinY + boundsMaxY + 1) * 0.5f,
                (boundsMinZ + boundsMaxZ + 1) * 0.5f);
        int largestExtent = Math.max(boundsMaxX - boundsMinX,
                Math.max(boundsMaxY - boundsMinY, boundsMaxZ - boundsMinZ));
        float cameraRadius = Math.max(12.0f, largestExtent * 1.35f + 6.0f);
        return new Region(blocks, visibleBlockCount, visibleFluidCount, center, cameraRadius, contentHash,
                new AABB(minX, minY, minZ, maxX + 1, maxY + 1, maxZ + 1));
    }

    private int completeArenaBlockCount() {
        return arenaWidth * arenaLength;
    }

    private static int appendLightHash(ClientLevel level, BlockPos pos, int contentHash) {
        contentHash = 31 * contentHash + LevelRenderer.getLightColor(level, pos);
        for (Direction direction : Direction.values()) {
            contentHash = 31 * contentHash + LevelRenderer.getLightColor(level, pos.relative(direction));
        }
        return contentHash;
    }

    private List<Entity> collectRenderableEntities(ClientLevel level, Region region) {
        Minecraft minecraft = Minecraft.getInstance();
        List<Entity> entities = new ArrayList<>();
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof CustomNpc npc) {
                npc.updateBoundingBox();
            }
            if (entity.isRemoved() || (!renderLocalPlayer && entity == minecraft.player)
                    || !region.entityBounds().intersects(entity.getBoundingBox())) {
                continue;
            }
            entities.add(entity);
        }
        return entities;
    }

    private SceneStats createSceneStats(ClientLevel level, Region region) {
        return new SceneStats(region.visibleBlockCount(), region.visibleFluidCount(),
                collectRenderableEntities(level, region).size());
    }

    /**
     * 描述当前真实世界视口包含的可渲染内容。
     *
     * @param blockCount  方块数量
     * @param fluidCount  流体方块数量
     * @param entityCount 实体数量
     */
    public record SceneStats(int blockCount, int fluidCount, int entityCount) {
    }

    /**
     * 描述鼠标当前指向的真实世界对象。
     *
     * @param entity 鼠标射线首先命中的实体，没有命中时为 {@code null}
     * @param blockHit 鼠标射线命中的方块面，没有命中时为 {@code null}
     * @param worldPosition 命中位置，没有可用深度时为 {@code null}
     */
    public record ScenePick(@Nullable Entity entity, @Nullable BlockHitResult blockHit,
                            @Nullable Vec3 worldPosition) {
    }

    /**
     * 接收真实世界视口中的编辑器交互。
     */
    public interface SceneInteractionHandler {
        SceneInteractionHandler EMPTY = new SceneInteractionHandler() {
        };

        /**
         * 处理左键按下，并决定是否接管本次拖拽。
         *
         * @param pick 当前场景拾取结果
         * @return 接管左键拖拽时返回 {@code true}；返回 {@code false} 时执行摄像机环绕
         */
        default boolean beginLeft(ScenePick pick) {
            return false;
        }

        /**
         * 处理已接管的左键拖拽更新。
         *
         * @param pick 当前场景拾取结果
         */
        default void dragLeft(ScenePick pick) {
        }

        /**
         * 处理已接管的左键释放。
         *
         * @param pick 当前场景拾取结果
         */
        default void endLeft(ScenePick pick) {
        }

        /**
         * 处理没有形成摄像机拖拽的右键单击。
         *
         * @param pick 当前场景拾取结果
         */
        default void rightClick(ScenePick pick) {
        }
    }

    private record Region(List<BlockPos> blocks, int visibleBlockCount, int visibleFluidCount, Vector3f center,
                          float cameraRadius, int contentHash, AABB entityBounds) {
        private boolean hasSameContent(Region other) {
            return contentHash == other.contentHash && blocks.equals(other.blocks);
        }
    }

    private record PickRay(Vec3 origin, Vec3 end) {
    }

    private record EntityPick(Entity entity, Vec3 location) {
    }
}
