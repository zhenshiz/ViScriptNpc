package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.scene.ISceneEntityRenderHook;
import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.sceneobject.utils.TransformGizmo.Mode;
import com.lowdragmc.lowdraglib2.gui.ColorPattern;
import com.lowdragmc.lowdraglib2.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Toggle;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.utils.virtuallevel.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.components.SceneToggleBuilder;
import com.viscript.npc.gui.edit.data.ClientNpcConfig;
import com.viscript.npc.gui.edit.data.NpcConfig;
import com.viscript.npc.mixin.WorldSceneRendererAccessor;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.data.model.NpcDynamicModel;
import dev.vfyjxf.taffy.style.FlexDirection;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

@Getter
public class NPCPreviewView extends View implements INpcEditorSlotView {
    private static final float MIN_COLLISION_BOX_SIZE = 0.01f;

    public final NpcEditor editor;
    public final NpcSceneEditor sceneEditor;
    public final TrackedDummyWorld level = new TrackedDummyWorld();
    private final CustomNpc customNpc;
    private int sceneRange = 10;
    @Setter
    private boolean cullBoxVisible = false;

    //runtime
    private boolean isSceneLoaded = false;

    public NPCPreviewView(NpcEditor editor) {
        super(String.format("%s.editor.view.preview", ViScriptNpc.MOD_ID), Icons.CAMERA);
        this.editor = editor;
        customNpc = NpcRegister.CUSTOM_NPC.get().create(level);

        this.getLayout().widthPercent(100);
        this.getLayout().heightPercent(100);
        sceneEditor = new NpcSceneEditor();
        sceneEditor.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        sceneEditor.scene
                .createScene(level)
                .setTickWorld(true)
                .useCacheBuffer();
        if (sceneEditor.scene.getRenderer() != null) {
            sceneEditor.scene.getRenderer().setSceneEntityRenderHook(new ISceneEntityRenderHook() {
                @Override
                public void applyEntity(net.minecraft.world.level.Level world, Entity entity, PoseStack poseStack, float partialTicks) {
                    if (entity == customNpc && customNpc != null) {
                        customNpc.setPreviewCameraOrientation(sceneEditor.getPreviewCameraOrientation());
                    }
                }
            });
        }
        this.addChild(sceneEditor);
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, com.viscript.npc.gui.edit.page.INpcEditorPage page) {
        if (!isSceneLoaded()) {
            loadScene();
        }
    }

    public void loadScene() {
        level.clear();
        isSceneLoaded = false;
        int i = 0;
        for (int x = -sceneRange + 1; x < sceneRange; x++) {
            for (int z = -sceneRange + 1; z < sceneRange; z++) {
                var blockState = (i % 2 == 0 ? Blocks.GRAY_CONCRETE : Blocks.LIGHT_GRAY_CONCRETE).defaultBlockState();
                level.setBlockAndUpdate(new BlockPos(x, 0, z), blockState);
                i++;
            }
        }
        customNpc.setPos(0, 1, 0);
        level.addEntity(customNpc);
        sceneEditor.scene.setRenderedCore(level.getFilledBlocks().longStream().mapToObj(BlockPos::of).toList());
        if (editor.getCurrentProject() instanceof NPCProject npcProject) {
            ClientNpcConfig npcConfig = new ClientNpcConfig(npcProject.npc.npcConfig);
            AABB aabb = npcConfig.getNpcData(NpcDynamicModel.class).getAabb();
            npcConfig.transform.scale(collisionBoxGizmoScale(aabb));

            npcConfig.transform().position(collisionBoxGizmoPosition(aabb));
            sceneEditor.addSceneObject(npcConfig);
            sceneEditor.setTransformGizmoTarget(npcConfig.transform(), () -> {
                applyCollisionBoxTransform(npcConfig);
                editor.historyView.recordSerializableObject(Component.translatable("npcObject.transform"), npcConfig.transform(), npcConfig);
            });
        }
        isSceneLoaded = true;
    }

    private Vector3f collisionBoxGizmoPosition(AABB aabb) {
        return customNpc.position().toVector3f().add(
                (float) ((aabb.minX + aabb.maxX) * 0.5D),
                (float) aabb.minY,
                (float) ((aabb.minZ + aabb.maxZ) * 0.5D)
        );
    }

    private Vector3f collisionBoxGizmoScale(AABB aabb) {
        return new Vector3f(
                (float) Math.max(MIN_COLLISION_BOX_SIZE, aabb.getXsize()),
                (float) Math.max(MIN_COLLISION_BOX_SIZE, aabb.getYsize()),
                (float) Math.max(MIN_COLLISION_BOX_SIZE, aabb.getZsize())
        );
    }

    private void applyCollisionBoxTransform(ClientNpcConfig npcConfig) {
        NpcDynamicModel dynamicModel = npcConfig.getNpcData(NpcDynamicModel.class);
        AABB currentAabb = dynamicModel.getAabb();
        Vector3f position = npcConfig.transform().position();
        Vector3f scale = npcConfig.transform().scale();
        double width = Math.max(MIN_COLLISION_BOX_SIZE, scale.x);
        double height = Math.max(MIN_COLLISION_BOX_SIZE, scale.y);
        double depth = Math.max(MIN_COLLISION_BOX_SIZE, scale.z);
        double centerX = position.x - customNpc.getX();
        double centerZ = position.z - customNpc.getZ();

        dynamicModel.setAabb(new AABB(
                centerX - width * 0.5D, currentAabb.minY, centerZ - depth * 0.5D,
                centerX + width * 0.5D, currentAabb.minY + height, centerZ + depth * 0.5D
        ));
        npcConfig.transform().position(new Vector3f(position.x, (float) (customNpc.getY() + currentAabb.minY), position.z));
        npcConfig.transform().scale(collisionBoxGizmoScale(dynamicModel.getAabb()));
    }

    public void clearScene() {
        customNpc.setPreviewCameraOrientation(null);
        level.clear();
        isSceneLoaded = false;
    }

    public class NpcSceneEditor extends SceneEditor {
        public static final IGuiTexture CULL_BOX = Icons.icon(ViScriptNpc.MOD_ID, "cull_box");

        public NPCPreviewView sceneView() {
            return NPCPreviewView.this;
        }

        private Quaternionf getPreviewCameraOrientation() {
            var renderer = scene.getRenderer();
            if (renderer == null) {
                return new Quaternionf();
            }
            return ((WorldSceneRendererAccessor) renderer).viscript_npc$getCamera().rotation();
        }

        @Override
        public void screenTick() {
            super.screenTick();
            if (customNpc != null) {
                Entity entity = customNpc.getNpcDynamicModel().getEntity(customNpc);
                try { // 某些生物的AI会导致奇怪的崩溃
                    if (entity != null) entity.tick();
                } catch (Exception ignored) {
                }
            }
        }

        @Override
        protected void renderAfterWorld(@NotNull MultiBufferSource bufferSource, float partialTicks) {
            super.renderAfterWorld(bufferSource, partialTicks);
            if (customNpc != null && editor.getCurrentProject() instanceof NPCProject npcProject) {
                NpcConfig npcConfig = npcProject.npc.npcConfig;
                CompoundTag configTag = npcProject.serializeNpcConfig(Platform.getFrozenRegistry());
                customNpc.readAdditionalSaveData(configTag);

                AABB aabb = npcConfig.getNpcData(NpcDynamicModel.class).getAabb();
                new ClientNpcConfig(npcConfig).transform.scale(new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize()));
            }

            if (sceneView().cullBoxVisible && sceneView().editor.getCurrentProject() instanceof NPCProject npcProject) {
                AABB cullBox = npcProject.npc.npcConfig.getNpcData(NpcDynamicModel.class).getAabb();
                if (cullBox != AABB.INFINITE) {
                    RenderSystem.enableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    RenderSystem.disableCull();
                    RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                    var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                    RenderSystem.lineWidth(3);
                    float baseY = customNpc == null ? 1.0F : (float) customNpc.getY();

                    RenderBufferUtils.drawCubeFrame(new PoseStack(), buffer,
                            (float) cullBox.minX, (float) cullBox.minY + baseY, (float) cullBox.minZ,
                            (float) cullBox.maxX, (float) cullBox.maxY + baseY, (float) cullBox.maxZ,
                            1, 1, 1, 1);

                    BufferUploader.drawWithShader(buffer.buildOrThrow());
                    RenderSystem.enableDepthTest();
                    RenderSystem.enableCull();
                }
            }
        }

        @Override
        public void initTopBar() {
            super.initTopBar();
            UIElement toggleButton = new UIElement().layout(layout -> {
                layout.heightPercent(100);
                layout.flexDirection(FlexDirection.ROW_REVERSE);
                layout.gapAll(1);
            });
            toggleButton.addChild(
                    new SceneToggleBuilder(sceneView()::isCullBoxVisible,
                            sceneView()::setCullBoxVisible)
                            .icon(CULL_BOX)
                            .tooltipKey("viscript_npc.editor.view.button.is_cull_box_visible")
                            .build()
            );

            topBar.addChildren(toggleButton);
        }

        public void initGizmos() {
            var toggleGroup = new Toggle.ToggleGroup();
            // translate
            gizmoBar.addChild(createTransformToggle(toggleGroup, Mode.TRANSLATE, Icons.TRANSFORM_TRANSLATE));
            // scale
            gizmoBar.addChild(createTransformToggle(toggleGroup, Mode.SCALE, Icons.TRANSFORM_SCALE));
        }

        private Toggle createTransformToggle(Toggle.ToggleGroup toggleGroup, Mode mode, IGuiTexture icon) {
            return (Toggle) new Toggle()
                    .setToggleGroup(toggleGroup)
                    .setText("")
                    .setOn(getTransformGizmoMode() == mode, false)
                    .toggleButton(button -> button.layout(layout -> {
                        layout.widthPercent(100);
                        layout.heightPercent(100);
                    }))
                    .setOnToggleChanged(isOn -> {
                        setTransformGizmoMode(isOn ? mode : Mode.NONE);
                    })
                    .toggleStyle(style -> {
                        style.baseTexture(IGuiTexture.EMPTY);
                        style.hoverTexture(ColorPattern.T_BLUE.rectTexture());
                        style.unmarkTexture(icon);
                        style.markTexture(new GuiTextureGroup(ColorPattern.T_BLUE.rectTexture(), icon));
                    })
                    .layout(layout -> {
                        layout.paddingAll(0);
                        layout.widthPercent(100);
                        layout.setAspectRatio(1f);
                    }).addEventListener(UIEvents.TICK, event -> {
                        if (event.currentElement instanceof Toggle toggle) {
                            if (toggle.getValue() != (getTransformGizmoMode() == mode)) {
                                toggle.setValue(getTransformGizmoMode() == mode, false);
                            }
                        }
                    });
        }
    }
}
