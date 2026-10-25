package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript.npc.util.NpcEditorFiles;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.util.List;
import java.util.UUID;

/** 验证测试场景只提供功能文件上传，并保存当前场地尺寸。 */
@LDLRegisterClient(name = "npc_test_scene_upload", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcTestSceneUploadScenario implements UIScenario {
    private static final String PREFIX = "viscript_lib.editor.";

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        String name = "__ldlib2_test_scene_upload_" + UUID.randomUUID();
        var runtime = NpcEditorFiles.resolveRuntimeFile(NpcTestSceneProject.FORMAT, name, true);
        var project = NpcEditorFiles.resolveProjectFile(NpcTestSceneProject.FORMAT, name, true);
        s.server("打开未保存的测试场景", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NpcTestSceneProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("修改场地尺寸并检查工程上传入口不可用", c -> {
                    var editor = (NpcEditor) window(c).getCurrentEditor();
                    ((NpcTestSceneProject) editor.getCurrentProject()).setArenaSize(24, 28);
                    editor.uploadProjectToServer(); editor.uploadProjectAndRuntimeToServer();
                    c.check("工程与联合上传均不打开弹窗", c.count(".__dialog_content-container__") == 0);
                    text(c, PREFIX + "menu.upload").one().element().setId("npc_test_scene_upload_menu");
                }).click("#npc_test_scene_upload_menu").ticks(2)
                .check("测试场景仅显示功能文件上传", c -> text(c, PREFIX + "menu.upload_runtime_file").count() == 1
                        && text(c, PREFIX + "menu.upload_project_file").count() == 0
                        && text(c, PREFIX + "menu.upload_project_and_runtime_file").count() == 0)
                .screenshot("test_scene_runtime_only_upload_menu")
                .step("选择上传功能文件", c -> {
                    var bounds = text(c, PREFIX + "menu.upload_runtime_file").one().bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .check("使用功能文件上传对话框", c -> text(c, PREFIX + "dialog.upload_runtime_file").count() == 1)
                .step("填写功能文件名", c -> c.query(".__dialog_content-container__ *").type(TextField.class).visible()
                        .one().as(TextField.class).setText(name + NpcTestSceneProject.FORMAT.runtimeSuffix(), true))
                .step("确认上传功能文件", c -> {
                    var bounds = c.el(".__confirm-button__").bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                })
                .waitUntilServer("服务端收到测试场景功能文件", server -> Files.isRegularFile(runtime))
                .server("验证上传内容且没有工程文件", server -> {
                    try {
                        var data = NbtIo.readCompressed(runtime, NbtAccounter.unlimitedHeap());
                        server.check("功能文件包含当前场地尺寸", data.getBoolean("test_scene")
                                && data.getInt("arena_width") == 24 && data.getInt("arena_length") == 28);
                        server.check("上传不产生工程文件", !Files.exists(project));
                    } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                }).closeScreen()
                .teardownServer("清理本次测试上传文件", server -> {
                    for (var file : List.of(runtime, project)) {
                        try { Files.deleteIfExists(file); }
                        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                    }
                });
    }

    private static ElementQuery text(TestContext context, String key) {
        return context.query().type(TextElement.class).visible().where(element ->
                ((TextElement) element).getText().getString().equals(Component.translatable(key).getString()));
    }

    private static EditorWindow window(TestContext context) {
        return (EditorWindow) ((ModularUIContainerScreen) context.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
