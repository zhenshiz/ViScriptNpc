package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.ViScriptNpcServerUtil;

/** 通过真实编辑器菜单和画布验证工程快照、模板与独立行为页。 */
@LDLRegisterClient(name = "npc_ai_editor", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiEditorScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai"); }
    @Override public void define(ScenarioBuilder s) {
        var fixture = AiExamples.tree("melee");
        var selector = fixture.nodes.stream().filter(n -> n.type.equals("reactive_selector")).findFirst().orElseThrow();
        String first = selector.children.getFirst(), last = selector.children.getLast();
        s.server("打开 NPC 工程", server -> {
                    NPCProject project = new NPCProject();
                    project.npc.getNpcData(NpcAI.class).setProgram(AiExamples.tree("melee"));
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .awaitElement("#npc_editor_page_tab_ai").click("#npc_editor_page_tab_ai")
                .awaitElement("#npc_ai_fit").click("#npc_ai_fit").ticks(3)
                .check("画布包含根与决策节点", c -> c.count(".npc_ai_node") >= 6)
                .screenshot("ai_tree")
                .drag("#npc_ai_node_" + first, "#npc_ai_node_" + last).ticks(2)
                .check("拖动只修改布局并保留显式优先级", c -> {
                    var v = c.el("#npc_ai_graph_tree").as(AiGraphView.class);
                    return v.model().compile().node(selector.id).children.getFirst().equals(first);
                })
                .click("#npc_ai_undo").ticks(2)
                .check("撤销移动保持原执行顺序", c -> c.el("#npc_ai_graph_tree").as(AiGraphView.class)
                        .model().compile().node(selector.id).children.getFirst().equals(first))
                .step("复制子树生成独立节点标识", c -> {
                    var v = c.el("#npc_ai_graph_tree").as(AiGraphView.class);
                    var node = v.model().getNodeModels().stream().filter(n -> n.getUid().toString().equals(first)).findFirst().orElseThrow();
                    v.clearAllSelected(); v.addSelected(node);
                    int before = v.model().getNodeModels().size();
                    v.duplicateSelectedElements();
                    c.check("复制包含子节点", v.model().getNodeModels().size() == before + 4, before + 4, v.model().getNodeModels().size());
                    c.check("副本标识不共享", v.model().getNodeModels().stream().map(n -> n.getUid()).distinct().count() == v.model().getNodeModels().size(), true, true);
                    v.getHistoryStack().undo();
                })
                .click("#npc_ai_examples").ticks(2).screenshot("ai_examples")
                .step("普通工程保存包含最新画布", c -> {
                    var screen = (ModularUIContainerScreen) c.mc().screen;
                    var window = (EditorWindow) screen.getMenu().getModularUI().ui.rootElement;
                    var editor = (NpcEditor) window.getCurrentEditor();
                    var project = (NPCProject) editor.getCurrentProject();
                    var file = java.nio.file.Path.of("ai-editor-roundtrip.npcproj").toFile();
                    NPCProject reopened = (NPCProject) roundTrip(project, file);
                    c.check("图快照已保存", !reopened.aiGraph.isEmpty(), true, !reopened.aiGraph.isEmpty());
                    c.check("运行树保留", reopened.npc.getNpcData(NpcAI.class).getProgram().nodes.size() == project.npc.getNpcData(NpcAI.class).getProgram().nodes.size(), true, true);
                    var action = new NpcActionProject(); action.program = AiExamples.action("jump_slash");
                    action.sourceActionId = "examples/jump_slash";
                    window.createNewEditor(NpcEditor::new).loadProject(action, null);
                }).awaitElement("#npc_ai_graph_action #npc_ai_fit").click("#npc_ai_graph_action #npc_ai_fit").ticks(3)
                .check("阶段流程包含动作节点", c -> c.count(".npc_ai_node") >= 10)
                .screenshot("ai_jump_slash")
                .click("#npc_ai_graph_action #npc_ai_variables").ticks(2).screenshot("ai_action_parameters")
                .step("切换 GUI Scale 并检查参数提示", c -> {
                    c.mc().options.guiScale().set(3); c.mc().resizeDisplay();
                }).ticks(3)
                .hover("#npc_ai_graph_action #npc_ai_variables").ticks(15).screenshot("ai_scale3_tooltip")
                .step("校验独立行为工程保存重开", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var action = (NpcActionProject) window.getCurrentEditor().getCurrentProject();
                    var view = c.el("#npc_ai_graph_action").as(AiGraphView.class);
                    view.edit("parameter", () -> view.model().getGraphVariableModels().stream()
                            .filter(v -> AiVariables.scope(v, "action").equals("parameter")).findFirst().orElseThrow()
                            .getInitializationModel().setValue(9d));
                    var draft = AiDrafts.resolve((NpcEditor) window.getCurrentEditor(), "examples/jump_slash");
                    c.check("测试依赖读取尚未保存的标签快照", draft.parameters.getFirst().initial.number == 9, 9, draft.parameters.getFirst().initial.number);
                    var file = java.nio.file.Path.of("ai-action-roundtrip.npcactionproj").toFile();
                    var reopened = (NpcActionProject) roundTrip(action, file);
                    c.check("参数与布局保留", reopened.program.parameters.size() == 2 && reopened.program.parameters.getFirst().initial.number == 9 && !reopened.graph.isEmpty(), true, true);
                    reopened.serializeRuntimeFile(Platform.getFrozenRegistry());
                    c.mc().options.guiScale().set(2); c.mc().resizeDisplay();
                })
                .ticks(3)
                .hover("#npc_ai_graph_action #npc_ai_scene_window")
                .step("点击测试并保留源标签", c -> {
                    var button = c.el("#npc_ai_graph_action #npc_ai_scene_window").bounds();
                    c.input().mouseDown(button.centerX(), button.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(button.centerX(), button.centerY(), Keys.MOUSE_LEFT);
                })
                .waitUntilServer("草稿进入独立测试场地", server -> com.viscript.npc.npc.ai.test.NpcAiTestEnvironment.isTestLevel(server.player().serverLevel()))
                .waitUntil("独立场景工程已打开", c -> ((EditorWindow) ((ModularUIContainerScreen) c.mc().screen)
                        .getMenu().getModularUI().ui.rootElement).getEditors().keySet().stream()
                        .anyMatch(editor -> editor.getCurrentProject() instanceof NpcTestSceneProject))
                .step("关闭独立窗口并切到场景标签验证场景工具", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var scene = window.getEditors().keySet().stream()
                            .filter(editor -> editor.getCurrentProject() instanceof NpcTestSceneProject).findFirst().orElseThrow();
                    scene.getFloatingViews().closeAll();
                    window.showEditor(scene);
                })
                .awaitElement("#npc_ai_test_scene").ticks(10)
                .check("一次点击只创建一个测试场景并保留源标签", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    return window.getEditors().size() == 3 && window.getEditors().keySet().stream()
                            .filter(editor -> editor.getCurrentProject() instanceof NpcTestSceneProject).count() == 1;
                })
                .step("准备场景滚轮", c -> {
                    var viewport = c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class);
                    c.put("scene_distance", viewport.getCameraController().getDistance());
                    var bounds = c.el("#npc_ai_test_scene").bounds();
                    c.input().moveTo(bounds.centerX(), bounds.centerY());
                }).ticks(1)
                .step("场景滚动一格", c -> {
                    var bounds = c.el("#npc_ai_test_scene").bounds();
                    c.input().scroll(bounds.centerX(), bounds.centerY(), 1);
                }).ticks(1)
                .check("场景一格只靠近百分之六", c -> Math.abs(c.el("#npc_ai_test_scene")
                        .as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class).getCameraController().getDistance()
                        / c.<Float>get("scene_distance") - .94) < .002)
                .check("测试工具图标不溢出", c -> c.query("#npc_ai_test_toolbar .npc_ai_toolbar_icon").list().stream().allMatch(ref -> {
                    var icon = ref.bounds(); var button = ElementBounds.of(ref.element().getParent());
                    return icon.x() >= button.x() + 1 && icon.y() >= button.y() + 1
                            && icon.right() <= button.right() - 1 && icon.bottom() <= button.bottom() - 1;
                }))
                .check("默认工具区完整显示状态文字", c -> {
                    var label = c.el("#npc_ai_test_status").bounds();
                    var viewport = ElementBounds.of(c.el("#npc_ai_test_tools_scroller")
                            .as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class).viewPort);
                    boolean contained = label.y() >= viewport.y() && label.bottom() <= viewport.bottom() + 1
                            && label.bottom() <= c.mc().getWindow().getGuiScaledHeight() - 1;
                    c.put("status_bounds", label.toString()); c.put("tools_viewport_bounds", viewport.toString());
                    return contained;
                })
                .step("窄面板换行", c -> c.el("#npc_ai_test_tools_view").element().layout(l -> l.width(95))).ticks(3)
                .check("状态文字根据实际宽度自动增加行高", c -> {
                    var label = c.el("#npc_ai_test_status").as(com.lowdragmc.lowdraglib2.gui.ui.elements.Label.class);
                    return label.getSizeHeight() > label.getTextStyle().fontSize() * 1.5f;
                })
                .step("滚动查看完整状态", c -> c.el("#npc_ai_test_tools_scroller")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class)
                        .scrollToChild(c.el("#npc_ai_test_status").element())).ticks(2).screenshot("ai_tools_wrapped")
                .step("恢复测试工具宽度", c -> c.el("#npc_ai_test_tools_view").element().layout(l -> l.widthPercent(100))).ticks(3)
                .step("返回工具栏", c -> c.el("#npc_ai_test_tools_scroller")
                        .as(com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView.class)
                        .scrollToChild(c.el("#npc_ai_test_toolbar").element())).ticks(2)
                .awaitElement("#npc_ai_pause").click("#npc_ai_pause").ticks(5)
                .check("停止只作用于被测 NPC", c -> AiClientDebug.latest.status.equals("paused"))
                .server("为行为草稿放入目标", server -> {
                    var center = com.viscript.npc.npc.ai.test.NpcAiTestLevelService.getArenaCenter(server.player());
                    var target = net.minecraft.world.entity.EntityType.COW.create(server.level());
                    target.moveTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(center.above()).add(3.5, 0, -1.5));
                    target.setNoAi(true); target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).setBaseValue(100);
                    target.setHealth(100); server.level().addFreshEntity(target); server.put("ai_target_id", target.getId());
                })
                .waitUntil("目标同步到场景", c -> c.level().getEntity(c.<Integer>get("ai_target_id")) != null)
                .click("#npc_ai_test_tool_target").ticks(3)
                .step("悬停测试目标", c -> {
                    var position = c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class)
                            .getEntityScreenPosition(c.get("ai_target_id"));
                    c.put("ai_target_screen", position); c.input().moveTo(position.x, position.y);
                }).frames(2)
                .check("场景拾取确实命中目标", c -> c.el("#npc_ai_test_scene").as(com.viscript.npc.gui.scene.RealLevelSceneViewport.class)
                        .getHoveredEntityId() == c.<Integer>get("ai_target_id"))
                .step("点击目标绑定给被测 NPC", c -> {
                    org.joml.Vector2f position = c.get("ai_target_screen");
                    c.input().mouseDown(position.x, position.y, Keys.MOUSE_LEFT);
                    c.input().mouseUp(position.x, position.y, Keys.MOUSE_LEFT);
                })
                .waitUntil("调试快照显示所选目标", c -> AiClientDebug.latest.targetId == c.<Integer>get("ai_target_id"))
                .click("#npc_ai_run").ticks(5)
                .check("被测 NPC 恢复执行", c -> !AiClientDebug.latest.status.equals("paused"))
                .waitUntilServer("草稿跳劈造成覆盖后的九点伤害", server ->
                        ((net.minecraft.world.entity.LivingEntity) server.level().getEntity(server.<Integer>get("ai_target_id"))).getHealth() == 91)
                .screenshot("ai_draft_test_scene")
                .server("退出测试场地", server -> com.viscript.npc.npc.ai.test.NpcAiTestLevelService.leave(server.player()))
                .closeScreen()
                .teardownServer("清理测试会话", server -> com.viscript.npc.npc.ai.test.NpcAiTestLevelService.leave(server.player()));
    }
    private static com.lowdragmc.lowdraglib2.editor.project.IProject roundTrip(
            com.lowdragmc.lowdraglib2.editor.project.IProject project, java.io.File file) {
        try {
            project.getProjectType().saveProjectToFile(project, file);
            return project.getProjectType().loadProjectFromFile(file);
        } catch (Exception exception) { throw new IllegalStateException(exception); }
    }

}
