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
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.test.NpcAiTestLevelService;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** 验证服务端断点、RPC 单步和恢复、红绿状态块及调试面板。 */
@LDLRegisterClient(name = "npc_ai_debug", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiDebugScenario implements UIScenario {
    @Override public void configure(ScenarioOptions o) {
        o.requiresWorld(true).guiScale(2).scenarioTimeoutMs(120_000).tags("ui", "ai", "regression");
    }
    @Override public void define(ScenarioBuilder s) {
        var p = new AiProgram(); p.kind = "action";
        var first = new AiNodeData(); first.type = "debug_log"; first.input("message", AiValue.text("first"));
        var second = new AiNodeData(); second.type = "debug_log"; second.input("message", AiValue.text("second"));
        var wait = new AiNodeData(); wait.type = "wait"; wait.input("seconds", AiValue.number(30));
        var sequence = new AiNodeData(); sequence.type = "sequence";
        sequence.children.addAll(List.of(first.id, second.id, wait.id));
        var root = new AiNodeData(); root.type = "root"; root.children.add(sequence.id); p.root = root.id;
        p.nodes.addAll(List.of(root, sequence, first, second, wait));
        String actionId = "test/node_step_nested";
        var tree = new AiProgram();
        var call = new AiNodeData(); call.type = "call"; call.input("file", AiValue.text(actionId));
        var treeRoot = new AiNodeData(); treeRoot.type = "root"; treeRoot.children.add(call.id);
        tree.root = treeRoot.id; tree.nodes.addAll(List.of(treeRoot, call));
        var id = new AtomicInteger(-1);
        s.server("创建测试 NPC 和执行前断点", server -> {
                    NpcAiTestLevelService.enter(server.player());
                    var center = NpcAiTestLevelService.getArenaCenter(server.player());
                    var npc = NpcRegister.CUSTOM_NPC.get().create(server.player().serverLevel());
                    npc.moveTo(center.getX() + 2.5, center.getY() + 1, center.getZ() + .5);
                    npc.getAiController().preview(p, Map.of()); npc.getAiController().debugger.breakpoints.add(first.id);
                    server.player().serverLevel().addFreshEntity(npc); id.set(npc.getId());
                    ViScriptNpcServerUtil.openNpcEditor(server.player(), new NPCProject().serializeNBT(server.player().registryAccess()));
                }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开对应行为并选择调试 NPC", c -> {
                    var project = new NpcActionProject(); project.program = p;
                    var graph = new AiGraph("action"); graph.model().importProgram(p); graph.model().breakpoints.add(first.id);
                    project.graph = graph.model().serializeNBT(Platform.getFrozenRegistry());
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                    AiClientDebug.select(id.get());
                }).awaitElement("#npc_ai_graph_action")
                .waitUntil("服务端断点快照已到达", c -> !view(c).isReadOnly() && AiClientDebug.latest.paused && AiClientDebug.latest.breakpoint.equals(first.id))
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2)
                .check("运行和暂停合为右侧一个按钮", c -> c.count("#npc_ai_graph_action #npc_ai_run_pause") == 1
                        && c.count("#npc_ai_graph_action #npc_ai_run") == 0
                        && c.count("#npc_ai_graph_action #npc_ai_pause") == 0
                        && c.el("#npc_ai_run_pause").bounds().x() > c.el("#npc_ai_graph_action").bounds().centerX())
                .check("断点暂停显示红块且节点主体无运行遮罩", c -> !view(c).getModelElement(node(c, wait.id)).hasClass("npc_ai_running")
                        && !c.el("#npc_ai_state_" + wait.id).element().hasClass("npc_ai_state_running"))
                .check("调试目录提供三个原生节点", c -> view(c).itemLibrary.nodeTree.getRoot().getChildren().stream()
                        .anyMatch(g -> g.getKey().getPath().equals("debug") && g.getChildren().size() == 3))
                .screenshot("breakpoint_red_indicator")
                .check("两种单步按钮都可用且下一节点显示箭头", c -> c.count("#npc_ai_graph_action #npc_ai_step") == 1
                        && c.count("#npc_ai_graph_action #npc_ai_step_tick") == 1
                        && c.el("#npc_ai_node_" + first.id).element().hasClass("npc_ai_node_next")
                        && c.el("#npc_ai_cursor_" + first.id).element().isDisplayed())
                .step("记住暂停时钟", c -> c.put("debug_tick", AiClientDebug.latest.tick))
                .click("#npc_ai_graph_action #npc_ai_step")
                .waitUntil("节点单步 RPC 停在第二个日志前且时钟不变", c -> AiClientDebug.latest.paused
                        && AiClientDebug.latest.currentNode.equals(second.id) && AiClientDebug.latest.tick == c.<Long>get("debug_tick")
                        && AiClientDebug.latest.lastNode.equals(first.id) && AiClientDebug.latest.lastResult.equals("success"))
                .check("单步尚未执行下一节点的日志副作用", c -> AiClientDebug.latest.events.stream().filter(e -> e.status.equals("value")).count() == 1)
                .click("#npc_ai_graph_action #npc_ai_debug").awaitElement("#npc_ai_debug_next").ticks(2)
                .check("调试面板显示上一步和下一节点输入", c -> c.count("#npc_ai_debug_last") == 1
                        && AiClientDebug.latest.inputs.containsKey(second.id + ":message"))
                .screenshot("node_step_before_second_log")
                .click("#npc_ai_graph_action #npc_ai_step")
                .waitUntil("第二次节点单步停在等待节点前", c -> AiClientDebug.latest.currentNode.equals(wait.id)
                        && AiClientDebug.latest.tick == c.<Long>get("debug_tick"))
                .click("#npc_ai_graph_action #npc_ai_step_tick")
                .waitUntil("tick 单步先完成当前轮并暂停", c -> AiClientDebug.latest.paused && !AiClientDebug.latest.tickInProgress
                        && AiClientDebug.latest.currentNode.isEmpty() && AiClientDebug.latest.tick == c.<Long>get("debug_tick"))
                .awaitElement("#npc_ai_debug_tick_complete")
                .check("两个日志各执行一次且等待继续运行", c -> AiClientDebug.latest.events.stream().filter(e -> e.status.equals("value")).count() == 2
                        && AiClientDebug.latest.activeNodes.contains(wait.id))
                .click("#npc_ai_graph_action #npc_ai_step_tick")
                .waitUntil("下一次 tick 单步才推进新的 AI tick", c -> AiClientDebug.latest.paused && !AiClientDebug.latest.tickInProgress
                        && AiClientDebug.latest.tick == c.<Long>get("debug_tick") + 1)
                .screenshot("ai_tick_step_complete")
                .click("#npc_ai_graph_action #npc_ai_run_pause")
                .waitUntil("继续 RPC 显示绿色状态块", c -> !AiClientDebug.latest.paused
                        && c.el("#npc_ai_state_" + wait.id).element().hasClass("npc_ai_state_running"))
                .screenshot("running_green_indicator")
                .click("#npc_ai_graph_action #npc_ai_run_pause")
                .waitUntil("暂停 RPC 保留等待节点", c -> AiClientDebug.latest.paused && AiClientDebug.latest.activeNodes.contains(wait.id))
                .click("#npc_ai_graph_action #npc_ai_debug").awaitElement("#npc_ai_debug_live").ticks(2)
                .check("调试面板显示真实执行日志", c -> !AiClientDebug.latest.events.isEmpty() && c.count("#npc_ai_debug_live button") > 0)
                .screenshot("debug_panel")
                .click("#npc_ai_node_" + wait.id + " .__node-title-element__")
                .rightClick("#npc_ai_node_" + wait.id + " .__node-title-element__").ticks(2)
                .check("右键菜单提供断点和禁用操作", c -> c.query(".__node-graph-view_context-menu__ .__menu_leaf-node__")
                        .where(e -> e.select("*").anyMatch(child -> com.lowdragmc.lowdraglib2.uitest.target.Texts.of(child).equals(
                                net.minecraft.network.chat.Component.translatable("viscript_npc.ai.debug.toggle_disabled").getString()))).count() == 1)
                .step("点击禁用分支菜单", c -> {
                    var b = c.query(".__node-graph-view_context-menu__ .__menu_leaf-node__").where(e -> e.select("*").anyMatch(child ->
                            com.lowdragmc.lowdraglib2.uitest.target.Texts.of(child).equals(net.minecraft.network.chat.Component.translatable(
                                    "viscript_npc.ai.debug.toggle_disabled").getString()))).one().bounds();
                    c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT); c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .check("禁用状态和断点随工程保存", c -> {
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    return graph.model().breakpoints.contains(first.id) && ((AiNodeModel) graph.model().getModel(UUID.fromString(wait.id))).data.disabled;
                }).click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("禁用节点可撤销", c -> !node(c, wait.id).data.disabled)
                .server("切换为调用行为内部的断点", server -> {
                    var npc = (com.viscript.npc.npc.CustomNpc) server.player().serverLevel().getEntity(id.get());
                    npc.getAiController().preview(tree, Map.of(actionId, p));
                    npc.getAiController().debugger.breakpoints.clear();
                    npc.getAiController().debugger.breakpoints.add(second.id);
                }).step("打开引用该行为的主树", c -> {
                    var project = new NPCProject();
                    var ai = project.npc.getNpcData(com.viscript.npc.npc.data.ai.NpcAI.class);
                    ai.setProgram(tree); ai.setActionDefinitions(new LinkedHashMap<>(Map.of(actionId, p)));
                    var editor = (NpcEditor) window(c).createNewEditor(NpcEditor::new);
                    editor.loadProject(project, null);
                    editor.selectNpcEditorPage(new com.viscript.npc.gui.edit.page.AiNpcEditorPage());
                }).awaitElement("#npc_ai_graph_tree")
                .waitUntil("快照显示被调用行为内部的暂停位置", c -> AiClientDebug.latest.paused
                        && AiClientDebug.latest.currentNode.equals(second.id) && AiClientDebug.latest.currentAction.equals(actionId)
                        && AiClientDebug.latest.currentDepth == 1)
                .click("#npc_ai_graph_tree #npc_ai_debug").awaitElement("#npc_ai_debug_next")
                .step("点击定位并切换到被调用的行为标签", c -> NpcTestScenePersistenceScenario.clickTransition(c, "#npc_ai_debug_next"))
                .awaitElement("#npc_ai_graph_action").ticks(2)
                .step("标记刚打开的行为画布", c -> view(c).addClass("npc_ai_nested_graph"))
                .waitUntil("等待行为定义同步和节点定位完成", c -> !view(c).loadingActionDefinitions
                        && view(c).getSelected().stream().anyMatch(n -> n.getUid().toString().equals(second.id)))
                .check("定位打开正确行为并选中暂停节点", c -> window(c).getCurrentEditor().getCurrentProject() instanceof NpcActionProject project
                        && project.sourceActionId.equals(actionId)
                        && view(c).getSelected().stream().anyMatch(n -> n.getUid().toString().equals(second.id)))
                .step("记住嵌套暂停的 AI tick", c -> c.put("nested_tick", AiClientDebug.latest.tick))
                .click(".npc_ai_nested_graph #npc_ai_step")
                .waitUntil("子行为节点单步不会重新进入调用或推进时钟", c -> AiClientDebug.latest.currentNode.equals(wait.id)
                        && AiClientDebug.latest.currentAction.equals(actionId) && AiClientDebug.latest.currentDepth == 1
                        && AiClientDebug.latest.tick == c.<Long>get("nested_tick"))
                .click(".npc_ai_nested_graph #npc_ai_debug").awaitElement("#npc_ai_debug_next")
                .check("子行为输入值同步到面板且箭头标记下一节点", c -> AiClientDebug.latest.inputs.get(wait.id + ":seconds").number == 30
                        && c.el(".npc_ai_nested_graph #npc_ai_node_" + wait.id).element().hasClass("npc_ai_node_next"))
                .screenshot("node_step_inside_called_action")
                .closeScreen()
                .teardownServer("清理测试 NPC", server -> {
                    var entity = server.player().serverLevel().getEntity(id.get()); if (entity != null) entity.discard();
                    NpcAiTestLevelService.leave(server.player());
                });
    }
    private static AiNodeModel node(TestContext c, String id) { return (AiNodeModel) view(c).model().getModel(UUID.fromString(id)); }
    private static AiGraphView view(TestContext c) {
        return window(c).getCurrentEditor().select("#npc_ai_graph_action").filter(AiGraphView.class::isInstance)
                .map(AiGraphView.class::cast).findFirst().orElseThrow();
    }
    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
