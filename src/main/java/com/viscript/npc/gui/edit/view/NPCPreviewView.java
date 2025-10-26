package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.client.utils.RenderBufferUtils;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.editor.ui.sceneeditor.SceneEditor;
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
import com.viscript.npc.gui.edit.data.NpcConfig;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;
import com.viscript.npc.util.common.StrUtil;
import lombok.Getter;
import lombok.Setter;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.appliedenergistics.yoga.YogaEdge;
import org.appliedenergistics.yoga.YogaFlexDirection;
import org.appliedenergistics.yoga.YogaGutter;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

@Getter
public class NPCPreviewView extends View {
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
        super(ViScriptNpc.formattedMod("%s.editor.view.preview"), Icons.CAMERA);
        this.editor = editor;
        customNpc = NpcRegister.CUSTOM_NPC.get().create(level);

        this.getLayout().setWidthPercent(100);
        this.getLayout().setHeightPercent(100);
        sceneEditor = new NpcSceneEditor();
        sceneEditor.layout(layout -> {
            layout.setWidthPercent(100);
            layout.setFlex(1);
        });
        sceneEditor.scene
                .createScene(level)
                .setTickWorld(true)
                .useCacheBuffer();
        this.addChild(sceneEditor);
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
        if (customNpc != null) {
            customNpc.setPos(0, 1, 0);
            level.addEntity(customNpc);
        }
        sceneEditor.scene.setRenderedCore(level.getFilledBlocks().longStream().mapToObj(BlockPos::of).toList());
        if (editor.project instanceof NPCProject npcProject) {
            NpcConfig npcConfig = npcProject.npc.npcConfig;
            AABB aabb = npcConfig.getNpcDynamicModel().getAabb();
            npcConfig.transform.scale(new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize()));
        }
        if (editor.project instanceof NPCProject npcProject) {
            NpcConfig npcConfig = npcProject.npc.npcConfig;
            npcConfig.transform().position(customNpc.position().toVector3f().add(0, 1, 0));
            sceneEditor.addSceneObject(npcConfig);
            sceneEditor.setTransformGizmoTarget(npcConfig.transform(), () -> {
                Vector3f position = npcConfig.transform().position();
                customNpc.setPos(position.x, position.y - 1, position.z);
                Vector3f scale = npcConfig.transform().scale();
                npcConfig.npcDynamicModel.setAabb(AABB.ofSize(customNpc.position(), scale.x, scale.y, scale.z));
                editor.historyView.recordSerializableObject(Component.translatable("npcObject.transform"), npcConfig.transform(), npcConfig);
            });
        }
        isSceneLoaded = true;
    }

    public void clearScene() {
        level.clear();
        isSceneLoaded = false;
    }

    public class NpcSceneEditor extends SceneEditor {
        public static final IGuiTexture CULL_BOX = Icons.icon(ViScriptNpc.MOD_ID, "cull_box");

        public NPCPreviewView sceneView() {
            return NPCPreviewView.this;
        }

        @Override
        protected void renderAfterWorld(@NotNull MultiBufferSource bufferSource, float partialTicks) {
            super.renderAfterWorld(bufferSource, partialTicks);
            if (customNpc != null && editor.project instanceof NPCProject npcProject) {
                NpcConfig npcConfig = npcProject.npc.npcConfig;
                CompoundTag compoundTag = new CompoundTag();
                compoundTag.put(StrUtil.toCamelCase(NpcBasicsSetting.class.getSimpleName()), npcConfig.getNpcBasicsSetting().serializeNBT(Platform.getFrozenRegistry()));
                compoundTag.put(StrUtil.toCamelCase(NpcDynamicModel.class.getSimpleName()), npcConfig.getNpcDynamicModel().serializeNBT(Platform.getFrozenRegistry()));
                compoundTag.put(StrUtil.toCamelCase(NpcAttributes.class.getSimpleName()), npcConfig.getNpcAttributes().serializeNBT(Platform.getFrozenRegistry()));
                compoundTag.put(StrUtil.toCamelCase(NpcInventory.class.getSimpleName()), npcConfig.getNpcInventory().serializeNBT(Platform.getFrozenRegistry()));
                compoundTag.put(StrUtil.toCamelCase(NpcModIntegrations.class.getSimpleName()), npcConfig.getNpcModIntegrations().serializeNBT(Platform.getFrozenRegistry()));
                customNpc.readAdditionalSaveData(compoundTag);
                customNpc.updateNpcState();

                AABB aabb = npcConfig.getNpcDynamicModel().getAabb();
                npcConfig.transform.scale(new Vector3f((float) aabb.getXsize(), (float) aabb.getYsize(), (float) aabb.getZsize()));
            }

            if (sceneView().cullBoxVisible && sceneView().editor.project instanceof NPCProject npcProject) {
                AABB cullBox = npcProject.npc.npcConfig.getNpcDynamicModel().getAabb();
                if (cullBox != AABB.INFINITE) {
                    RenderSystem.enableBlend();
                    RenderSystem.disableDepthTest();
                    RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

                    RenderSystem.disableCull();
                    RenderSystem.setShader(GameRenderer::getRendertypeLinesShader);
                    var buffer = Tesselator.getInstance().begin(VertexFormat.Mode.LINES, DefaultVertexFormat.POSITION_COLOR_NORMAL);
                    RenderSystem.lineWidth(3);

                    RenderBufferUtils.drawCubeFrame(new PoseStack(), buffer,
                            (float) cullBox.minX, (float) cullBox.minY + 1, (float) cullBox.minZ,
                            (float) cullBox.maxX, (float) cullBox.maxY + 1, (float) cullBox.maxZ,
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
                layout.setHeightPercent(100);
                layout.setFlexDirection(YogaFlexDirection.ROW_REVERSE);
                layout.setGap(YogaGutter.ALL, 1);
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
            gizmoBar.addChild(createTransformToggle(toggleGroup, TransformGizmoMode.TRANSLATE, Icons.TRANSFORM_TRANSLATE));
            // scale
            gizmoBar.addChild(createTransformToggle(toggleGroup, TransformGizmoMode.SCALE, Icons.TRANSFORM_SCALE));
        }

        private Toggle createTransformToggle(Toggle.ToggleGroup toggleGroup, TransformGizmoMode mode, IGuiTexture icon) {
            return (Toggle) new Toggle()
                    .setToggleGroup(toggleGroup)
                    .setText("")
                    .setOn(transformGizmoMode == mode, false)
                    .toggleButton(button -> button.layout(layout -> {
                        layout.setWidthPercent(100);
                        layout.setHeightPercent(100);
                    }))
                    .setOnToggleChanged(isOn -> {
                        setTransformGizmoMode(isOn ? mode : TransformGizmoMode.NONE);
                    })
                    .toggleStyle(style -> {
                        style.baseTexture(IGuiTexture.EMPTY);
                        style.hoverTexture(ColorPattern.T_BLUE.rectTexture());
                        style.unmarkTexture(icon);
                        style.markTexture(new GuiTextureGroup(ColorPattern.T_BLUE.rectTexture(), icon));
                    })
                    .layout(layout -> {
                        layout.setPadding(YogaEdge.ALL, 0);
                        layout.setWidthPercent(100);
                        layout.setAspectRatio(1f);
                    }).addEventListener(UIEvents.TICK, event -> {
                        if (event.currentElement instanceof Toggle toggle) {
                            if (toggle.getValue() != (transformGizmoMode == mode)) {
                                toggle.setValue(transformGizmoMode == mode, false);
                            }
                        }
                    });
        }
    }
}
