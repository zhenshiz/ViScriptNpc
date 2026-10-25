package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.view.NPCPreviewView;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.basics_setting.NpcBasicsSetting;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;

@LDLRegisterClient(name = "npc_preview_name", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcPreviewNameScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "editor", "render");
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.teleportPlayer(0.5, 256, 0.5, 0, 0)
                .waitUntil("真实摄像机离开预览原点", context -> context.requirePlayer().getY() > 200)
                .server("打开含主名与副名的 NPC 工程", server -> {
                    var project = new NPCProject();
                    project.npc.getNpcData(NpcBasicsSetting.class).setCustomSubName("Preview");
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
                })
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .awaitElement("#npc_editor_page_tab_basic")
                .waitUntil("预览实体使用预览摄像机", context -> preview(context).getCustomNpc().getPreviewCameraOrientation() != null)
                .check("真实世界摄像机距离超出名称显示范围", context -> context.mc().getEntityRenderDispatcher()
                        .distanceToSqr(preview(context).getCustomNpc()) > 64 * 64)
                .screenshot("npc_preview_name_visible_far_from_origin")
                .check("预览实际提交名称文字渲染", context -> rendersText(context))
                .step("关闭名称显示", context -> project(context).npc.getNpcData(NpcBasicsSetting.class).setNameVisible(false))
                .waitUntil("名称开关应用到预览实体", context -> !preview(context).getCustomNpc().getNpcBasicsSetting().isNameVisible())
                .check("隐藏名称时不提交文字渲染", context -> !rendersText(context))
                .screenshot("npc_preview_name_hidden")
                .step("恢复名称显示", context -> project(context).npc.getNpcData(NpcBasicsSetting.class).setNameVisible(true))
                .waitUntil("名称开关重新生效", context -> preview(context).getCustomNpc().getNpcBasicsSetting().isNameVisible())
                .check("重新打开名称后恢复文字渲染", NpcPreviewNameScenario::rendersText)
                .step("验证真实世界仍遵守名称距离限制", context -> {
                    var npc = preview(context).getCustomNpc();
                    var orientation = npc.getPreviewCameraOrientation();
                    try {
                        npc.setPreviewCameraOrientation(null);
                        boolean rendered = rendersText(context);
                        context.check("非预览实体超出距离时不显示名字", !rendered, false, rendered);
                    } finally {
                        npc.setPreviewCameraOrientation(orientation);
                    }
                })
                .closeScreen();
    }

    private static NpcEditor editor(TestContext context) {
        return (NpcEditor) ((EditorWindow) ((ModularUIContainerScreen) context.mc().screen)
                .getMenu().getModularUI().ui.rootElement).getCurrentEditor();
    }

    private static NPCProject project(TestContext context) {
        return (NPCProject) editor(context).getCurrentProject();
    }

    private static NPCPreviewView preview(TestContext context) {
        return editor(context).getPreviewView();
    }

    private static boolean rendersText(TestContext context) {
        var npc = preview(context).getCustomNpc();
        boolean[] text = {false};
        try (var storage = new ByteBufferBuilder(4096)) {
            var buffers = MultiBufferSource.immediate(storage);
            MultiBufferSource observed = type -> {
                if (type.format() == DefaultVertexFormat.POSITION_COLOR_TEX_LIGHTMAP) text[0] = true;
                return buffers.getBuffer(type);
            };
            EntityRenderer<? super CustomNpc> renderer = context.mc().getEntityRenderDispatcher().getRenderer(npc);
            renderer.render(npc, 0, 0, new PoseStack(), observed, 15728880);
            buffers.endBatch();
        }
        return text[0];
    }
}
