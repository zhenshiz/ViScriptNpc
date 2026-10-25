package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.project.IProject;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.*;
import com.lowdragmc.lowdraglib2.gui.util.FileNode;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.NpcActionProject;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** 验证分类目录、本地读写、功能导出和服务端命令使用同一套文件路径。 */
@LDLRegisterClient(name = "npc_editor_storage", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcEditorStorageScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "editor", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        String id = "__ldlib2_storage_" + UUID.randomUUID();
        s.server("打开文件存储测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("验证三类文件的保存路径和本地读写", c -> {
                    var npc = new NPCProject();
                    var action = new NpcActionProject(); action.program = NpcAiFixtures.action("wait");
                    var scene = new NpcTestSceneProject(); scene.setArenaSize(24, 28);
                    for (IProject project : List.of(npc, action, scene)) {
                        String domain = project instanceof NPCProject ? "npc" : project instanceof NpcActionProject ? "action" : "test_scene";
                        String directory = project instanceof NpcTestSceneProject ? "function" : "project";
                        var path = path(domain, directory, id + "_local" + project.getSuffix());
                        try {
                            c.check(domain + " 的另存为根目录正确", project.getProjectType().getRootSavePath(project, LDLib2.getAssetsDir())
                                    .toPath().toAbsolutePath().normalize().equals(path.getParent()));
                            Files.createDirectories(path.getParent());
                            project.getProjectType().saveProjectToFile(project, path.toFile());
                            var loaded = project.getProjectType().loadProjectFromFile(path.toFile());
                            c.check(domain + " 的本地文件读写一致", project.serializeProject(Platform.getFrozenRegistry())
                                    .equals(loaded.serializeProject(Platform.getFrozenRegistry())));
                            if (project instanceof NpcTestSceneProject testScene) {
                                c.check("测试场地保存为功能文件且保存后无修改", testScene.getSuffix().equals(".npcscene")
                                        && !testScene.getProjectType().isProjectDirty(testScene, path.toFile()));
                                testScene.setArenaSize(32, 36);
                                c.check("测试场地尺寸变化会触发未保存状态", testScene.getProjectType().isProjectDirty(testScene, path.toFile()));
                            }
                        } catch (Exception exception) { throw new IllegalStateException(exception); }
                    }
                    var editor = (NpcEditor) window(c).getCurrentEditor();
                    var program = AiExamples.tree("patroller");
                    var current = (NPCProject) editor.getCurrentProject();
                    current.aiCompiler = null;
                    current.npc.getNpcData(NpcAI.class).setProgram(program);
                    current.serializeRuntimeFile(Platform.getFrozenRegistry());
                    text(c, "editor.file").one().element().setId("npc_storage_file_menu");
                }).click("#npc_storage_file_menu").ticks(2)
                .step("选择本地功能文件导出", c -> choose(c, "viscript_lib.editor.menu.export_runtime_file")).ticks(2)
                .check("NPC 导出弹窗定位到 npc/function", c -> fileTree(c).getRoot().getKey().toPath().toAbsolutePath().normalize()
                        .equals(path("npc", "function", "placeholder").getParent()))
                .step("填写本地导出文件名", c -> c.query(".__dialog_content-container__ *").type(TextField.class).visible()
                        .one().as(TextField.class).setText(id + "_export", true))
                .step("确认本地导出", NpcEditorStorageScenario::confirm)
                .check("本地功能文件在新目录且可由 NPC 加载器读取", c -> Files.isRegularFile(path("npc", "function", id + "_export.npc"))
                        && NpcRuntimeFiles.listFileIds().contains(id + "_export")
                        && !NpcRuntimeFiles.load(id + "_export").isEmpty())
                .click("#npc_storage_file_menu").ticks(2)
                .step("选择打开文件", c -> choose(c, "ldlib.gui.editor.menu.open")).ticks(2)
                .check("打开弹窗从统一 viscript_npc 根目录浏览", c -> fileTree(c).getRoot().getKey().toPath().toAbsolutePath().normalize()
                        .equals(path("npc", "function", "placeholder").getParent().getParent().getParent()))
                .screenshot("storage_open_three_categories")
                .step("关闭打开弹窗", c -> click(c, ".__cancel-button__"))
                .closeScreen()
                .server("验证上传落盘、文件扫描和草稿加载", server -> {
                    try {
                        var npc = new NPCProject();
                        var program = AiExamples.tree("patroller");
                        npc.npc.getNpcData(NpcAI.class).setProgram(program);
                        var request = new CompoundTag(); request.putString("kind", "npc"); request.putString("name", id + "_uploaded");
                        request.put("runtime", npc.serializeNpcConfig(server.level().registryAccess()));
                        request.put("project", npc.serializeNBT(server.level().registryAccess()));
                        NpcAiPublication.install(request);
                        server.check("NPC 联合上传写入 npc 分类的两个目录", Files.isRegularFile(path("npc", "function", id + "_uploaded.npc"))
                                && Files.isRegularFile(path("npc", "project", id + "_uploaded.npcproj")));
                        server.check("行为工程扫描和草稿加载使用 action/project", NpcActionFiles.listCustomLocal().contains(id + "_local")
                                && NpcActionFiles.loadLocal(id + "_local") != null);
                        for (String kind : List.of("npc", "action")) {
                            var project = kind.equals("npc") ? npc : new NpcActionProject();
                            request = new CompoundTag(); request.putString("kind", kind); request.putString("name", id + "_project_only");
                            request.put("project", project.serializeNBT(server.level().registryAccess()));
                            NpcAiPublication.install(request);
                            String suffix = kind.equals("npc") ? ".npc" : ".npcaction";
                            server.check(kind + " 单独工程上传只写工程目录", Files.isRegularFile(path(kind, "project", id + "_project_only" + suffix + "proj"))
                                    && !Files.exists(path(kind, "function", id + "_project_only" + suffix)));
                        }
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                })
                .server("通过命令打开已保存的测试场地功能文件", server -> {
                    server.server().getCommands().performPrefixedCommand(server.player().createCommandSourceStack().withPermission(4),
                            "viscript_npc test " + id + "_local");
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .check("命令重开保留测试场地尺寸", c -> window(c).getCurrentEditor().getCurrentProject() instanceof NpcTestSceneProject scene
                        && scene.getArenaWidth() == 24 && scene.getArenaLength() == 28)
                .closeScreen()
                .teardownServer("清理本次测试文件", server -> {
                    for (String domain : List.of("npc", "action", "test_scene")) for (String directory : List.of("project", "function")) {
                        var root = path(domain, directory, "placeholder").getParent();
                        if (!Files.isDirectory(root)) continue;
                        try (var files = Files.list(root)) {
                            for (var file : files.filter(p -> p.getFileName().toString().startsWith(id)).toList()) Files.deleteIfExists(file);
                        } catch (Exception exception) { throw new IllegalStateException(exception); }
                    }
                    NpcActionFiles.invalidate();
                });
    }

    private static Path path(String domain, String directory, String name) {
        return LDLib2.getAssetsDir().toPath().toAbsolutePath().normalize().resolve("viscript_npc").resolve(domain).resolve(directory).resolve(name);
    }
    @SuppressWarnings("unchecked") private static TreeList<FileNode> fileTree(TestContext c) {
        return (TreeList<FileNode>) c.query(".__dialog_content-container__ *").type(TreeList.class).one().element();
    }
    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
    private static ElementQuery text(TestContext c, String key) {
        return c.query().type(TextElement.class).visible().where(element ->
                ((TextElement) element).getText().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents text
                        && text.getKey().equals(key));
    }
    private static void choose(TestContext c, String key) { click(c, text(c, key).one().bounds()); }
    private static void confirm(TestContext c) { click(c, ".__confirm-button__"); }
    private static void click(TestContext c, String query) { click(c, c.el(query).bounds()); }
    private static void click(TestContext c, ElementBounds bounds) {
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
}
