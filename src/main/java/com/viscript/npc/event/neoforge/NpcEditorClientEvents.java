package com.viscript.npc.event.neoforge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.lwjgl.glfw.GLFW;

import java.util.List;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID, value = Dist.CLIENT)
public class NpcEditorClientEvents {
    private static final double CAMERA_DRAG_THRESHOLD_SQR = 9.0D;
    private static int worldMouseButton = -1;
    private static double worldMousePressX;
    private static double worldMousePressY;
    private static boolean worldMouseDragging;
    private static boolean pathToggleKeyDown;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ViScriptNpcClientUtil.isNpcEditorScreen(minecraft.screen)) {
            ViScriptNpcClientUtil.clearNpcAiWorldTestMode();
            releaseWorldInputs();
            return;
        }
        if (!ViScriptNpcClientUtil.isNpcAiWorldInteractionMode()
                || minecraft.player == null
                || minecraft.level == null
                || minecraft.gameMode == null) {
            releaseWorldInputs();
            return;
        }
        ViScriptNpcClientUtil.getNpcAiWorldBlockHit();
        if (minecraft.level.getGameTime() % 5L == 0L) {
            ViScriptNpcClientUtil.requestNpcAiWorldPath();
        }
    }

    @SubscribeEvent
    public static void onScreenRenderPre(ScreenEvent.Render.Pre event) {
        if (ViScriptNpcClientUtil.isNpcEditorScreen(event.getScreen())) {
            ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onScreenKeyPressed(ScreenEvent.KeyPressed.Pre event) {
        if (!ViScriptNpcClientUtil.isNpcEditorScreen(event.getScreen())) {
            return;
        }
        if (ViScriptNpcClientUtil.isNpcAiWorldInteractionMode()
                && event.getKeyCode() == GLFW.GLFW_KEY_P
                && isF3Down()) {
            if (!pathToggleKeyDown) {
                ViScriptNpcClientUtil.toggleNpcAiWorldPathVisible();
                pathToggleKeyDown = true;
            }
            event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            ViScriptNpcClientUtil.toggleNpcAiWorldInteractionMode();
            event.setCanceled(true);
            return;
        }
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE && ViScriptNpcClientUtil.isNpcAiWorldInteractionMode()) {
            ViScriptNpcClientUtil.stopNpcAiWorldInteractionMode();
            releaseWorldInputs();
            event.setCanceled(true);
            return;
        }
        if (ViScriptNpcClientUtil.shouldForwardNpcAiWorldInput(event.getScreen())
                && ViScriptNpcClientUtil.selectNpcAiWorldHotbarSlot(event.getKeyCode(), event.getScanCode())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenKeyReleased(ScreenEvent.KeyReleased.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_P || event.getKeyCode() == GLFW.GLFW_KEY_F3) {
            pathToggleKeyDown = false;
        }
    }

    @SubscribeEvent
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        if (shouldForwardToWorld(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                worldMouseButton = event.getButton();
                worldMousePressX = event.getMouseX();
                worldMousePressY = event.getMouseY();
                worldMouseDragging = event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                        && ViScriptNpcClientUtil.tryStartNpcAiWorldMobDrag();
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        boolean activeButtonReleased = worldMouseButton == event.getButton();
        if (activeButtonReleased) {
            if (ViScriptNpcClientUtil.isNpcAiWorldMobDragging()) {
                ViScriptNpcClientUtil.stopNpcAiWorldMobDrag();
            } else if (!worldMouseDragging) {
                if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                    ViScriptNpcClientUtil.breakNpcAiWorldTargetBlock();
                } else if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    ViScriptNpcClientUtil.useNpcAiWorldTargetBlock();
                }
            }
            releaseWorldInputs();
            event.setCanceled(true);
            return;
        }
        if (shouldForwardToWorld(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        if (event.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && ViScriptNpcClientUtil.isNpcAiWorldMobDragging()) {
            worldMouseDragging = true;
            ViScriptNpcClientUtil.updateNpcAiWorldMobDrag();
            event.setCanceled(true);
            return;
        }
        if (worldMouseButton == event.getMouseButton()
                && (event.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                || event.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT)
                && ViScriptNpcClientUtil.isNpcAiWorldInteractionMode()) {
            double deltaX = event.getMouseX() - worldMousePressX;
            double deltaY = event.getMouseY() - worldMousePressY;
            if (worldMouseDragging || deltaX * deltaX + deltaY * deltaY >= CAMERA_DRAG_THRESHOLD_SQR) {
                worldMouseDragging = true;
                if (event.getMouseButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                    ViScriptNpcClientUtil.lookNpcAiWorldCameraInPlace(event.getDragX(), event.getDragY());
                } else {
                    ViScriptNpcClientUtil.rotateNpcAiWorldCamera(event.getDragX(), event.getDragY());
                }
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        if (shouldForwardToWorld(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            double scroll = event.getScrollDeltaY() == 0.0D ? -event.getScrollDeltaX() : event.getScrollDeltaY();
            ViScriptNpcClientUtil.adjustNpcAiWorldCameraDistance(scroll);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCalculateDetachedCameraDistance(CalculateDetachedCameraDistanceEvent event) {
        if (ViScriptNpcClientUtil.isNpcAiWorldInteractionMode()) {
            event.setDistance(ViScriptNpcClientUtil.getNpcAiWorldCameraDistance(event.getDistance()));
        }
    }

    @SubscribeEvent
    public static void onRenderPlayerPre(RenderPlayerEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (ViScriptNpcClientUtil.shouldHideNpcAiWorldPlayer() && event.getEntity() == minecraft.player) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        BlockPos pos = ViScriptNpcClientUtil.getNpcAiWorldHighlightBlockPos();
        AABB mobBox = ViScriptNpcClientUtil.getNpcAiWorldMobHighlightBox();
        List<Vec3> pathPoints = ViScriptNpcClientUtil.getNpcAiWorldPathPoints();
        if (pos == null && mobBox == null && pathPoints.size() < 2) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer lineBuffer = buffer.getBuffer(RenderType.lines());
        if (pathPoints.size() >= 2) {
            renderPath(event.getPoseStack(), lineBuffer, pathPoints, cameraPos);
        }
        if (pos != null) {
            AABB box = new AABB(pos).inflate(0.003D).move(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
            LevelRenderer.renderLineBox(event.getPoseStack(), lineBuffer, box,
                    1.0F, 0.86F, 0.0F, 1.0F);
        }
        if (mobBox != null) {
            AABB box = mobBox.move(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
            LevelRenderer.renderLineBox(event.getPoseStack(), lineBuffer, box,
                    0.3F, 1.0F, 0.55F, 1.0F);
        }
        buffer.endBatch(RenderType.lines());
    }

    private static void renderPath(PoseStack poseStack, VertexConsumer buffer, List<Vec3> points, Vec3 cameraPos) {
        for (int index = 0; index < points.size() - 1; index++) {
            Vec3 from = points.get(index).subtract(cameraPos);
            Vec3 to = points.get(index + 1).subtract(cameraPos);
            Vec3 normal = to.subtract(from).normalize();
            addPathVertex(poseStack, buffer, from, normal, index == 0);
            addPathVertex(poseStack, buffer, to, normal, false);
        }
    }

    private static void addPathVertex(PoseStack poseStack, VertexConsumer buffer, Vec3 pos, Vec3 normal, boolean start) {
        buffer.addVertex(poseStack.last(), (float) pos.x(), (float) pos.y(), (float) pos.z())
                .setColor(start ? 0.15F : 0.0F, 0.95F, 1.0F, 1.0F)
                .setNormal(poseStack.last(), (float) normal.x(), (float) normal.y(), (float) normal.z());
    }

    private static boolean shouldForwardToWorld(Screen screen, double mouseX, double mouseY) {
        return ViScriptNpcClientUtil.shouldForwardNpcAiWorldInput(screen, mouseX, mouseY);
    }

    private static boolean isF3Down() {
        long window = Minecraft.getInstance().getWindow().getWindow();
        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_F3) == GLFW.GLFW_PRESS;
    }

    private static void releaseWorldInputs() {
        ViScriptNpcClientUtil.stopNpcAiWorldMobDrag();
        worldMouseButton = -1;
        worldMouseDragging = false;
    }
}
