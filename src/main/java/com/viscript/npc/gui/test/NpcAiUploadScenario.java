package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

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
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript.npc.util.NpcEditorFiles;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** 验证 VSL 上传菜单分别提供功能文件和联合上传，并写入同名工程及最新画布快照。 */
@LDLRegisterClient(name = "npc_ai_upload", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiUploadScenario implements UIScenario {
    private static final String PREFIX = "viscript_lib.editor.";
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression"); }

    @Override public void define(ScenarioBuilder s) {
        String id = "__ldlib2_tests_upload_" + UUID.randomUUID();
        String runtimeOnly = id + "_runtime";
        var fixture = NpcAiFixtures.action("wait");
        String wait = fixture.nodes.stream().filter(n -> n.type.equals("wait")).findFirst().orElseThrow().id;
        s.server("打开上传测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("载入行为并修改未保存的画布参数", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    var editor = window.createNewEditor(NpcEditor::new); editor.setId("npc_ai_upload_editor");
                    editor.loadProject(project, null);
                    var view = c.el("#npc_ai_graph_action").as(AiGraphView.class);
                    view.edit("parameter", () -> ((AiNodeModel) view.model().getModel(UUID.fromString(wait))).data.input("seconds", AiValue.number(2.5)));
                    c.query("#npc_ai_upload_editor *").type(TextElement.class).visible()
                            .withText(Component.translatable(PREFIX + "menu.upload").getString()).one().element().setId("npc_ai_upload_menu");
                }).click("#npc_ai_upload_menu").ticks(2)
                .check("上传菜单三项独立且各出现一次", c -> List.of("upload_project_file", "upload_runtime_file", "upload_project_and_runtime_file")
                        .stream().allMatch(key -> text(c, PREFIX + "menu." + key).count() == 1))
                .screenshot("action_upload_three_options")
                .step("选择单独上传功能文件", c -> choose(c, PREFIX + "menu.upload_runtime_file")).ticks(2)
                .check("单独上传使用功能文件弹窗标题", c -> text(c, PREFIX + "dialog.upload_runtime_file").count() == 1)
                .step("填写功能文件名", c -> filename(c, runtimeOnly))
                .step("确认上传功能文件", NpcAiUploadScenario::confirm)
                .waitUntilServer("单独上传功能文件落盘", server -> Files.isRegularFile(runtime(runtimeOnly)))
                .checkServer("单独上传不附带工程文件", server -> !Files.exists(project(runtimeOnly)))
                .awaitElement("#npc_ai_upload_editor .__confirm-button__")
                .step("关闭单独上传结果提示", c -> {
                    var bounds = c.el("#npc_ai_upload_editor .__confirm-button__").bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                })
                .ticks(3).click("#npc_ai_upload_menu").ticks(2)
                .step("选择联合上传菜单项", c -> choose(c, PREFIX + "menu.upload_project_and_runtime_file")).ticks(2)
                .check("联合上传使用 VSL 的联合弹窗标题", c -> text(c, PREFIX + "dialog.upload_project_and_runtime_file").count() == 1)
                .step("填写带工程后缀的同一基础名称", c -> filename(c, id + NpcActionProject.FORMAT.projectSuffix()))
                .screenshot("action_upload_both_dialog")
                .step("确认联合上传", NpcAiUploadScenario::confirm)
                .waitUntilServer("工程和功能文件均已落盘", server -> Files.isRegularFile(runtime(id)) && Files.isRegularFile(project(id)))
                .server("验证联合上传包含当前画布数据", server -> {
                    try {
                        var provider = server.level().registryAccess();
                        var runtime = new AiProgram(); runtime.deserializeNBT(provider, NbtIo.readCompressed(runtime(id), NbtAccounter.unlimitedHeap()));
                        var project = new NpcActionProject(); project.deserializeNBT(provider, NbtIo.read(project(id)));
                        server.check("工程与功能文件都保留未保存参数", runtime.node(wait).inputs.get("seconds").value.number == 2.5
                                && project.program.node(wait).inputs.get("seconds").value.number == 2.5 && !project.graph.isEmpty());
                    } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                }).closeScreen()
                .teardownServer("清理仅由本次测试创建的上传文件", server -> {
                    for (String name : List.of(id, runtimeOnly)) for (var path : List.of(runtime(name), project(name))) {
                        try { Files.deleteIfExists(path); }
                        catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
                    }
                });
    }

    private static void choose(TestContext c, String key) {
        var bounds = text(c, key).one().bounds();
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
    private static void confirm(TestContext c) {
        var bounds = c.el(".__confirm-button__").bounds();
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
    private static ElementQuery text(TestContext c, String key) {
        return c.query().type(TextElement.class).visible()
                .where(element -> ((TextElement) element).getText().getString().equals(Component.translatable(key).getString()));
    }
    private static void filename(TestContext c, String name) { c.query(".__dialog_content-container__ *").type(TextField.class).visible().one().as(TextField.class).setText(name, true); }
    private static Path runtime(String name) { return NpcEditorFiles.resolveRuntimeFile(NpcActionProject.FORMAT, name, true); }
    private static Path project(String name) { return NpcEditorFiles.resolveProjectFile(NpcActionProject.FORMAT, name, true); }
}
