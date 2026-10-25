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
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import org.joml.Vector2f;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** 验证独立条件目录、原生创建、单一职责参数表单及连接后的保存和撤销。 */
@LDLRegisterClient(name = "npc_ai_conditions", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiConditionScenario implements UIScenario {
    private static final List<String> CONDITIONS = List.of("detect_target", "target_exists", "target_distance",
            "target_sight", "self_health", "number_compare");

    @Override public void configure(ScenarioOptions o) {
        o.requiresWorld(true).guiScale(2).scenarioTimeoutMs(120_000).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开空白工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开行为工程", c -> window(c).createNewEditor(NpcEditor::new).loadProject(new NpcActionProject(), null))
                .awaitElement("#npc_ai_graph_action")
                .check("条件目录只列出六个独立条件且不提供旧复合门", c -> {
                    var root = view(c).itemLibrary.nodeTree.getRoot();
                    var group = root.getChildren().stream().filter(n -> n.getKey().getPath().equals("condition"))
                            .findFirst().orElseThrow();
                    return group.getChildren().size() == CONDITIONS.size()
                            && CONDITIONS.stream().allMatch(id -> group.getChildren().stream().anyMatch(n ->
                            n.getKey().getSearchableName().startsWith("viscript_npc:" + id + " ")))
                            && view(c).itemLibrary.getAllItems().noneMatch(item ->
                            item.getSearchableName().startsWith("viscript_npc:condition "));
                });
        for (String id : CONDITIONS) {
            s.step("打开节点目录 " + id, c -> {
                        var b = ElementBounds.of(view(c).graphView);
                        float x = b.x() + 25, y = b.y() + 30;
                        c.input().mouseDown(x, y, Keys.MOUSE_RIGHT); c.input().mouseUp(x, y, Keys.MOUSE_RIGHT);
                    }).ticks(2)
                    .step("点击添加节点", c -> {
                        var b = c.query(".__node-graph-view_context-menu__ .__menu_leaf-node__").nth(0).one().bounds();
                        c.input().mouseDown(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                        c.input().mouseUp(b.centerX(), b.centerY(), Keys.MOUSE_LEFT);
                    }).awaitElement("#npc_ai_node_library")
                    .step("清空搜索", c -> view(c).itemLibrary.searchField.setText("", true))
                    .typeInto("#npc_ai_node_search", id).key(Keys.DOWN).key(Keys.ENTER).ticks(2)
                    .check("原生目录创建独立条件 " + id, c -> node(c, id) != null)
                    .step("查看条件参数 " + id, c -> view(c).inspectNode(node(c, id))).ticks(2)
                    .check("只展示当前条件自己的参数 " + id, c -> {
                        var actual = c.query("#npc_ai_parameters_view .npc_ai_parameter_row").visible().list().stream()
                                .map(e -> e.element().getId()).collect(Collectors.toSet());
                        var expected = AiOperations.get(id).fields().stream().map(AiOperations.Field::id)
                                .filter(field -> !field.equals("entity_type")).map(field -> "npc_ai_field_" + field)
                                .collect(Collectors.toSet());
                        return !node(c, id).getOutputsById().containsKey("children") && actual.equals(expected)
                                && !actual.contains("npc_ai_field_require_target");
                    });
            if (Set.of("target_distance", "number_compare").contains(id)) s.screenshot(id + "_only_parameters");
        }
        s.step("在顺序下并列连接条件与动作", c -> view(c).edit("add", () -> {
                    var model = view(c).model();
                    var sequence = model.add("sequence", new Vector2f());
                    model.createWire(sequence.getInputsById().get("parent"), node(c, "root").getOutputsById().get("children"), null);
                    for (String id : CONDITIONS) model.createWire(node(c, id).getInputsById().get("parent"), sequence.getOutputsById().get("children"), null);
                    var wait = model.add("wait", new Vector2f()); wait.data.input("seconds", AiValue.number(0));
                    model.createWire(wait.getInputsById().get("parent"), sequence.getOutputsById().get("children"), null);
                    model.arrange();
                })).ticks(2)
                .check("标准条件叶子与动作可按顺序导出", c -> view(c).model().compile().nodes.size() == 9
                        && AiValidation.validate(view(c).model().compile(), NpcActionFiles::loadLocal).isEmpty())
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销恢复所有新连线与组合节点", c -> view(c).model().getNodeModels().size() == 7
                        && view(c).model().getWireModels().isEmpty())
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("保存重开保留并列条件和顺序", c -> {
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var p = reopened.model().compile(); var sequence = p.node(p.node(p.root).children.getFirst());
                    for (int i = 0; i < CONDITIONS.size(); i++) if (!p.node(sequence.children.get(i)).type.equals(CONDITIONS.get(i))) return false;
                    return p.nodes.size() == 9 && sequence.children.size() == 7 && AiValidation.validate(p, NpcActionFiles::loadLocal).isEmpty();
                })
                .check("组合装饰条件动作分组与新增控制节点可搜索", c -> {
                    var paths = view(c).itemLibrary.nodeTree.getRoot().getChildren().stream().map(n -> n.getKey().getPath()).collect(Collectors.toSet());
                    return paths.containsAll(Set.of("composite", "decorator", "condition", "action"))
                            && List.of("parallel", "reactive_sequence", "reactive_selector", "until_success", "until_failure", "force_success", "force_failure", "finish_success", "finish_failure").stream()
                            .allMatch(id -> view(c).itemLibrary.getAllItems().anyMatch(item -> item.getSearchableName().startsWith("viscript_npc:" + id + " ")));
                })
                .screenshot("standard_condition_leaves").closeScreen();
    }



    private static AiNodeModel node(TestContext c, String id) {
        return view(c).model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai && ai.operation().equals(id))
                .map(n -> (AiNodeModel) n).findFirst().orElse(null);
    }

    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }

    private static AiGraphView view(TestContext c) {
        return (AiGraphView) window(c).getCurrentEditor().select("#npc_ai_graph_action").findFirst().orElseThrow();
    }
}
