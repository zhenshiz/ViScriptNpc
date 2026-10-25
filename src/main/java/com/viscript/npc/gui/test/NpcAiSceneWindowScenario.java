package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.editor.ui.floating.FloatingViewWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.scene.RealLevelSceneViewport;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.test.NpcAiFixtures;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import org.lwjgl.glfw.GLFW;

/** 验证测试场景独立窗口的真实渲染、输入和主画布调试同步。 */
@LDLRegisterClient(name = "npc_ai_scene_window", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiSceneWindowScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).defaultTimeoutMs(15_000).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        var program = NpcAiFixtures.action("wait");
        var wait = program.node(program.node(program.root).children.getFirst());
        wait.input("seconds", AiValue.number(600));
        s.server("打开行为编辑器", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("创建可持续调试的行为工程", c -> {
                    var project = new NpcActionProject(); project.program = program;
                    var editor = (NpcEditor) host(c).createNewEditor(NpcEditor::new);
                    editor.loadProject(project, null); c.put("scene_window_source", editor);
                }).awaitElement("#npc_ai_graph_action")
                .step("点击独立场景入口", c -> NpcTestScenePersistenceScenario.clickTransition(c,
                        "#npc_ai_graph_action #npc_ai_scene_window"))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .waitUntil("原行为画布与独立场景同时打开", c -> {
                    for (var editor : host(c).getEditors().keySet()) {
                        if (!(editor instanceof NpcEditor npc) || !(npc.getCurrentProject() instanceof NpcTestSceneProject)) continue;
                        for (var view : npc.getFloatingViews().allViews()) if (view instanceof NpcAiTestSceneView scene) {
                            c.put("scene_window_scene", scene); c.put("scene_window_editor", npc);
                            return npc.getFloatingViews().windowOf(scene) != null
                                    && host(c).getCurrentEditor() == c.get("scene_window_source");
                        }
                    }
                    return false;
                }).ticks(4)
                .check("只有独立场景入口且首次只创建一个场景工程", c ->
                        c.count("#npc_ai_graph_action #npc_ai_test") == 0
                                && host(c).getEditors().keySet().stream()
                                .filter(editor -> editor.getCurrentProject() instanceof NpcTestSceneProject).count() == 1)
                .check("场景窗口包含全部三块视图并置顶", c -> floating(c).views().size() == 3
                        && floating(c).isAlwaysOnTop() && floating(c).getModularUI() != c.requireUI())
                .check("场景占据窗口主要区域", c -> viewport(c).getSizeWidth() > floating(c).getModularUI().getScreenWidth() * .6f
                        && viewport(c).getSizeHeight() > floating(c).getModularUI().getScreenHeight() * .6f)
                .check("打开独立场景保留原界面缩放", c -> c.mc().options.guiScale().get() == 2)
                .waitUntil("独立场景已渲染真实方块和 NPC", c -> viewport(c).getVisibleBlockCount()
                        >= viewport(c).getArenaWidth() * viewport(c).getArenaLength()
                        && viewport(c).getVisibleEntityCount() >= 1
                        && viewport(c).getEntityScreenPosition(scene(c).getSelectedNpcId()) != null)
                .frames(20)
                .step("记录独立场景截图", c -> c.screenshotSurface("detached_test_scene", floating(c).surface()))
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .checkExists("#npc_ai_state_" + wait.id)
                .screenshot("main_graph_with_scene_detached")
                .waitUntil("主画布收到同一 NPC 的执行状态", c -> AiClientDebug.latest.entityId == scene(c).getSelectedNpcId()
                        && AiClientDebug.latest.activeNodes.contains(wait.id))
                .click("#npc_ai_graph_action #npc_ai_run_pause")
                .waitUntil("主画布暂停同步到场景", c -> AiClientDebug.latest.paused)
                .step("瞄准独立窗口的继续按钮", c -> c.input(floating(c)).moveTo(element(c, "npc_ai_run")))
                .step("按下独立窗口继续按钮", c -> c.input(floating(c)).mouseDown(GLFW.GLFW_MOUSE_BUTTON_LEFT))
                .step("释放独立窗口继续按钮", c -> c.input(floating(c)).mouseUp(GLFW.GLFW_MOUSE_BUTTON_LEFT))
                .waitUntil("独立窗口继续同步到主画布", c -> !AiClientDebug.latest.paused)
                .step("在独立工具栏按数字键切换方块工具", c -> c.input(floating(c)).key(GLFW.GLFW_KEY_2, 0))
                .check("独立窗口快捷键跟随工具栏焦点", c -> scene(c).getActiveToolName().equals("PLACE_BLOCK"))
                .step("切回选择工具", c -> c.input(floating(c)).key(GLFW.GLFW_KEY_1, 0))
                .step("指向独立场景中的 NPC", c -> {
                    var point = viewport(c).getEntityScreenPosition(scene(c).getSelectedNpcId());
                    c.input(floating(c)).moveTo(point.x, point.y);
                }).frames(2)
                .check("独立场景拾取命中真实 NPC", c -> viewport(c).getHoveredEntityId() == scene(c).getSelectedNpcId())
                .step("记录调整前摄像机", c -> c.put("scene_window_eye", new org.joml.Vector3f(viewport(c).getCameraController().getEyePosition())))
                .step("瞄准独立场景", c -> c.input(floating(c)).moveTo(viewport(c)))
                .step("在独立窗口滚轮缩放", c -> c.input(floating(c)).scroll(0, 1)).ticks(2)
                .check("独立窗口摄像机响应鼠标输入", c -> !viewport(c).getCameraController().getEyePosition().equals(c.<org.joml.Vector3f>get("scene_window_eye")))
                .step("调整独立窗口尺寸", c -> floating(c).window().setSize(800, 600)).ticks(3)
                .check("调整窗口后场景重新布局且继续渲染", c -> floating(c).window().getWindowWidth() == 800
                        && floating(c).window().getWindowHeight() == 600
                        && viewport(c).getSizeWidth() > floating(c).getModularUI().getScreenWidth() * .6f
                        && viewport(c).getEntityScreenPosition(scene(c).getSelectedNpcId()) != null)
                .step("记录调整后的场景", c -> c.screenshotSurface("resized_test_scene", floating(c).surface()))
                .step("关闭独立场景窗口", c -> c.input(floating(c)).closeRequest()).ticks(3)
                .check("关闭后视图收回且主画布保留", c -> {
                    NpcEditor editor = c.get("scene_window_editor");
                    return editor.getFloatingViews().allViews().isEmpty() && scene(c).getViewContainer() != null
                            && host(c).getCurrentEditor() == c.get("scene_window_source");
                })
                .click("#npc_ai_graph_action #npc_ai_scene_window").ticks(3)
                .check("再次打开复用同一场景和 NPC", c -> floating(c).isOpen()
                        && AiClientDebug.latest.entityId == scene(c).getSelectedNpcId()
                        && host(c).getEditors().keySet().stream()
                        .filter(editor -> editor.getCurrentProject() instanceof NpcTestSceneProject).count() == 1)
                .step("关闭场景工程", c -> c.<NpcEditor>get("scene_window_editor").exit())
                .awaitElement(".__reject-button__")
                .step("放弃保存并关闭场景工程", c -> NpcTestScenePersistenceScenario.clickTransition(c, ".__reject-button__")).ticks(3)
                .check("关闭工程不留下系统窗口", c -> c.<NpcEditor>get("scene_window_editor").getFloatingViews().allViews().isEmpty())
                .closeScreen()
                .teardownServer("离开测试维度", server -> NpcAiTestLevelService.leave(server.player()));
    }

    private static EditorWindow host(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
    private static NpcAiTestSceneView scene(TestContext c) { return c.get("scene_window_scene"); }
    private static FloatingViewWindow floating(TestContext c) {
        return c.<NpcEditor>get("scene_window_editor").getFloatingViews().windowOf(scene(c));
    }
    private static UIElement element(TestContext c, String id) {
        return floating(c).getModularUI().ui.rootElement.selectId(id).findFirst().orElseThrow();
    }
    private static RealLevelSceneViewport viewport(TestContext c) {
        return (RealLevelSceneViewport) element(c, "npc_ai_test_scene");
    }
}
