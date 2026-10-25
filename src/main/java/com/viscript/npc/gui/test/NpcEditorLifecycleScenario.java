package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.NpcActionProject;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.util.ViScriptNpcServerUtil;

@LDLRegisterClient(name = "npc_editor_lifecycle", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcEditorLifecycleScenario implements UIScenario {
    @Override
    public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "editor");
    }

    @Override
    public void define(ScenarioBuilder scenario) {
        scenario.server("打开未保存的 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(
                        server.player(), new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .awaitElement("#npc_editor_page_tab_basic")
                .step("记住窗口并点击 X", context -> {
                    context.put("original_window", window(context));
                    click(context, window(context).getCurrentEditor().closeButton);
                })
                .awaitElement(".__cancel-button__")
                .step("取消关闭", context -> click(context, context.el(".__cancel-button__").element()))
                .check("取消关闭保留工程", context -> window(context) == context.get("original_window")
                        && window(context).getCurrentEditor().getCurrentProject() instanceof NPCProject)
                .step("再次点击 X", context -> click(context, window(context).getCurrentEditor().closeButton))
                .awaitElement(".__reject-button__")
                .step("放弃保存并关闭", context -> click(context, context.el(".__reject-button__").element()))
                .waitUntil("关闭回到游戏", context -> context.mc().screen == null)
                .server("执行无参数编辑器命令", NpcEditorLifecycleScenario::openWithCommand)
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .check("关闭后重开使用新窗口且不恢复旧工程", context -> window(context) != context.get("original_window")
                        && window(context).getEditors().size() == 1
                        && window(context).getCurrentEditor().getCurrentProject() == null)
                .step("准备两个未保存的工程标签", context -> {
                    var window = window(context);
                    window.getCurrentEditor().loadProject(new NPCProject(), null);
                    window.createNewEditor(NpcEditor::new).loadProject(new NpcActionProject(), null);
                    context.put("minimized_window", window);
                    context.put("active_editor", window.getCurrentEditor());
                    context.put("active_project", window.getCurrentEditor().getCurrentProject());
                })
                .awaitElement("#npc_ai_graph_action")
                .step("点击最小化按钮", context -> click(context,
                        window(context).getCurrentEditor().buttonContainer.getChildren().stream()
                                .filter(button -> !EditorWindow.HOST_TOGGLE_ID.equals(button.getId())).findFirst().orElseThrow()))
                .waitUntil("最小化回到游戏", context -> context.mc().screen == null)
                .server("执行命令恢复最小化窗口", NpcEditorLifecycleScenario::openWithCommand)
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .check("最小化保留窗口、两个标签和当前行为工程", context -> {
                    var window = window(context);
                    return window == context.get("minimized_window") && window.getEditors().size() == 2
                            && window.getCurrentEditor() == context.get("active_editor")
                            && window.getCurrentEditor().getCurrentProject() == context.get("active_project")
                            && context.count(".__confirm-button__") == 0;
                })
                .screenshot("editor_restored_after_minimize")
                .closeScreen();
    }

    private static void openWithCommand(ServerContext context) {
        context.server().getCommands().performPrefixedCommand(
                context.player().createCommandSourceStack().withPermission(4), "viscript_npc editor");
    }

    private static EditorWindow window(TestContext context) {
        return (EditorWindow) ((ModularUIContainerScreen) context.mc().screen)
                .getMenu().getModularUI().ui.rootElement;
    }

    private static void click(TestContext context, UIElement element) {
        var bounds = ElementBounds.of(element);
        context.input().moveTo(bounds.centerX(), bounds.centerY());
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
}
