package com.viscript.npc.event.neoforge;

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

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID, value = Dist.CLIENT)
public class NpcEditorClientEvents {
    private static final double CAMERA_DRAG_THRESHOLD_SQR = 9.0D;
    private static int worldMouseButton = -1;
    private static double worldMousePressX;
    private static double worldMousePressY;
    private static boolean worldMouseDragging;

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
    public static void onScreenMousePressed(ScreenEvent.MouseButtonPressed.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        if (shouldForwardToWorld(event.getScreen(), event.getMouseX(), event.getMouseY())) {
            if (event.getButton() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_RIGHT
                    || event.getButton() == GLFW.GLFW_MOUSE_BUTTON_MIDDLE) {
                worldMouseButton = event.getButton();
                worldMousePressX = event.getMouseX();
                worldMousePressY = event.getMouseY();
                worldMouseDragging = false;
            }
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onScreenMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        ViScriptNpcClientUtil.updateNpcEditorMousePosition(event.getMouseX(), event.getMouseY());
        boolean activeButtonReleased = worldMouseButton == event.getButton();
        if (activeButtonReleased) {
            if (!worldMouseDragging) {
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
        if (pos == null) {
            return;
        }
        Vec3 cameraPos = event.getCamera().getPosition();
        AABB box = new AABB(pos).inflate(0.003D).move(-cameraPos.x(), -cameraPos.y(), -cameraPos.z());
        MultiBufferSource.BufferSource buffer = Minecraft.getInstance().renderBuffers().bufferSource();
        LevelRenderer.renderLineBox(event.getPoseStack(), buffer.getBuffer(RenderType.lines()), box,
                1.0F, 0.86F, 0.0F, 1.0F);
        buffer.endBatch(RenderType.lines());
    }

    private static boolean shouldForwardToWorld(Screen screen, double mouseX, double mouseY) {
        return ViScriptNpcClientUtil.shouldForwardNpcAiWorldInput(screen, mouseX, mouseY);
    }

    private static void releaseWorldInputs() {
        worldMouseButton = -1;
        worldMouseDragging = false;
    }
}
