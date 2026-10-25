package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.WireElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.GraphCommands;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.WireCommands;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.PortElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import org.joml.Vector2f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** 验证真实端口连线、自由布局、显式子节点顺序和分支保存。 */
@LDLRegisterClient(name = "npc_ai_flow_layout", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiFlowLayoutScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai");
    }

    @Override public void define(ScenarioBuilder s) {
        var fixture = AiExamples.action("melee");
        var sequence = fixture.nodes.stream().filter(n -> n.type.equals("sequence")).findFirst().orElseThrow();
        String first = sequence.children.getFirst(), second = sequence.children.get(1);
        s.server("打开空白 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开近战行为副本", c -> {
                    var project = new NpcActionProject(); project.program = fixture;
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action")
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(3)
                .check("顺序节点直接分支连接每个动作", c -> {
                    var model = view(c).model();
                    return model.children(node(c, sequence.id)).size() == 4
                            && model.getWireModels().stream().filter(w -> w != null && w.getToPort() != null
                            && sequence.children.contains(w.getToPort().getNodeModel().getUid().toString()))
                            .allMatch(w -> w.getFromPort().getNodeModel().getUid().toString().equals(sequence.id))
                            && sequence.children.stream().allMatch(id -> !node(c, id).getOutputsById().containsKey("children"));
                })
                .check("所有线段端点贴合真实端口", NpcAiFlowLayoutScenario::endpointsMatch)
                .screenshot("melee_real_port_branches")
                .step("向上移动动作并保留其他节点", c -> {
                    var view = view(c); var moved = node(c, second);
                    c.put("flow_positions", positions(view));
                    c.put("flow_runtime", view.model().compile().serializeNBT(Platform.getFrozenRegistry()));
                    var offset = new Vector2f(100, -180);
                    c.put("flow_moved", view.snapPosition(moved.getPosition().add(offset, new Vector2f())));
                    view.dispatchCommand(new GraphCommands.MoveElementsCommand(List.of(moved), offset));
                }).ticks(2)
                .check("移动不会吸附回纵列、移动其他节点或改执行顺序", c -> {
                    Map<UUID, Vector2f> before = c.get("flow_positions");
                    return node(c, second).getPosition().equals(c.<Vector2f>get("flow_moved"))
                            && view(c).model().getNodeModels().stream().filter(n -> !n.getUid().toString().equals(second))
                            .allMatch(n -> n.getPosition().equals(before.get(n.getUid())))
                            && c.get("flow_runtime").equals(view(c).model().compile().serializeNBT(Platform.getFrozenRegistry()));
                })
                .check("任意方向摆放后连线仍贴合端口", NpcAiFlowLayoutScenario::endpointsMatch)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销恢复布局", c -> positions(view(c)).equals(c.get("flow_positions")))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .step("检查显式步骤列表", c -> view(c).inspectNode(node(c, sequence.id)))
                .awaitElement("#npc_ai_child_order")
                .checkNotExists("#npc_ai_child_later_" + first)
                .checkNotExists("#npc_ai_child_earlier_" + first)
                .drag("#npc_ai_child_" + first, "#npc_ai_child_" + second).ticks(2)
                .check("拖动文本改变执行顺序并保留自由布局", c -> {
                    var order = view(c).model().compile().node(sequence.id).children;
                    return order.getFirst().equals(second) && order.get(1).equals(first)
                            && node(c, second).getPosition().equals(c.<Vector2f>get("flow_moved"));
                })
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销顺序调整", c -> view(c).model().compile().node(sequence.id).children.equals(sequence.children))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(3)
                .screenshot("melee_free_layout_and_child_order")
                .step("接入条件与命中分支", c -> {
                    var view = view(c);
                    var existing = positions(view);
                    var branch = view.model().add("branch", new Vector2f(360, -130));
                    var yes = view.model().add("swing", new Vector2f(560, -250));
                    var no = view.model().add("wait", new Vector2f(160, -250));
                    var hit = view.model().add("particle", new Vector2f(-240, -120));
                    c.put("branch_id", branch.getUid().toString()); c.put("yes_id", yes.getUid().toString());
                    c.put("no_id", no.getUid().toString()); c.put("hit_id", hit.getUid().toString());
                    view.dispatchCommand(new WireCommands.CreateWireCommand(branch.getInputsById().get("parent"),
                            node(c, sequence.id).getOutputsById().get("children")));
                    view.dispatchCommand(new WireCommands.CreateWireCommand(yes.getInputsById().get("parent"), branch.getOutputsById().get("children")));
                    view.dispatchCommand(new WireCommands.CreateWireCommand(no.getInputsById().get("parent"), branch.getOutputsById().get("children")));
                    var attack = view.model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel a && a.operation().equals("melee"))
                            .map(n -> (AiNodeModel) n).findFirst().orElseThrow();
                    view.dispatchCommand(new WireCommands.CreateWireCommand(hit.getInputsById().get("parent"), attack.getOutputsById().get("hit")));
                    c.check("创建和接线保持已有布局", existing.entrySet().stream().allMatch(e ->
                            node(c, e.getKey().toString()).getPosition().equals(e.getValue())));
                    view.inspectNode(branch);
                }).ticks(2)
                .check("分支与事件连接均可编译且不依赖位置", c -> {
                    var runtime = view(c).model().compile();
                    var branch = runtime.node(c.get("branch_id"));
                    return branch.children.equals(List.of(c.<String>get("yes_id"), c.<String>get("no_id")))
                            && runtime.nodes.stream().anyMatch(n -> n.type.equals("melee") && n.onHit.equals(c.<String>get("hit_id")))
                            && AiValidation.validate(runtime, view(c).actionResolver).isEmpty();
                })
                .check("分支列表明确标出为真与为假", c -> branchLabelsMatch(c, c.get("yes_id"), c.get("no_id")))
                .step("标记分支拖动文本", c -> {
                    c.el("#npc_ai_child_" + c.<String>get("yes_id")).element().setId("npc_ai_branch_yes_row");
                    c.el("#npc_ai_child_" + c.<String>get("no_id")).element().setId("npc_ai_branch_no_row");
                }).drag("#npc_ai_branch_yes_row", "#npc_ai_branch_no_row").ticks(2)
                .check("拖动分支文本同步交换真假含义", c -> view(c).model().compile().node(c.get("branch_id")).children
                        .equals(List.of(c.<String>get("no_id"), c.<String>get("yes_id")))
                        && branchLabelsMatch(c, c.get("no_id"), c.get("yes_id")))
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("一次撤销恢复整次分支拖动", c -> view(c).model().compile().node(c.get("branch_id")).children
                        .equals(List.of(c.<String>get("yes_id"), c.<String>get("no_id")))
                        && branchLabelsMatch(c, c.get("yes_id"), c.get("no_id")))
                .step("移动分支父节点", c -> {
                    var view = view(c);
                    c.put("yes_position", new Vector2f(node(c, c.get("yes_id")).getPosition()));
                    c.put("no_position", new Vector2f(node(c, c.get("no_id")).getPosition()));
                    view.dispatchCommand(new GraphCommands.MoveElementsCommand(List.of(node(c, c.get("branch_id"))), new Vector2f(-200, 320)));
                }).ticks(2)
                .check("父节点移动保留两支位置和真假含义", c -> node(c, c.get("yes_id")).getPosition().equals(c.<Vector2f>get("yes_position"))
                        && node(c, c.get("no_id")).getPosition().equals(c.<Vector2f>get("no_position"))
                        && view(c).model().compile().node(c.get("branch_id")).children.equals(List.of(c.<String>get("yes_id"), c.<String>get("no_id"))))
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(3)
                .check("分支和事件端点均贴合真实端口", NpcAiFlowLayoutScenario::endpointsMatch)
                .screenshot("free_layout_condition_and_hit_branches")
                .step("保存重开保留位置、顺序和分支", c -> {
                    var model = view(c).model(); var graph = new AiGraph("action");
                    graph.model().deserializeNBT(Platform.getFrozenRegistry(), model.serializeNBT(Platform.getFrozenRegistry()));
                    c.check("自由布局已保存", model.getNodeModels().stream().allMatch(n ->
                            n.getPosition().equals(((AiNodeModel) graph.model().getModel(n.getUid())).getPosition())));
                    c.check("编译语义已保存", graph.model().compile().serializeNBT(Platform.getFrozenRegistry())
                            .equals(model.compile().serializeNBT(Platform.getFrozenRegistry())));
                    var order = List.copyOf(model.compile().node(sequence.id).children);
                    view(c).edit("arrange", model::arrange);
                    c.check("手动整理保留步骤及分支语义", order.equals(model.compile().node(sequence.id).children)
                            && model.compile().node(c.get("branch_id")).children.equals(List.of(c.<String>get("yes_id"), c.<String>get("no_id"))));
                }).ticks(3)
                .check("手动整理后线段仍贴合真实端口", NpcAiFlowLayoutScenario::endpointsMatch)
                .closeScreen();
    }

    private static boolean branchLabelsMatch(TestContext c, String yes, String no) {
        for (var entry : Map.of("true", yes, "false", no).entrySet()) {
            var child = node(c, entry.getValue());
            var name = net.minecraft.network.chat.Component.translatable("viscript_npc.ai.node." + child.operation());
            String expected = net.minecraft.network.chat.Component.translatable("viscript_npc.ai.child." + entry.getKey(), name).getString();
            if (!c.el("#npc_ai_child_" + entry.getValue()).as(com.lowdragmc.lowdraglib2.gui.ui.elements.Label.class).getText().getString().equals(expected)) return false;
        }
        return true;
    }

    private static boolean endpointsMatch(TestContext context) {
        var view = view(context);
        return view.model().getWireModels().stream().filter(w -> w != null).allMatch(model -> {
            if (!(view.getModelElement(model) instanceof WireElement wire) || wire.getParent() == null) return false;
            return endpoint(view, wire, model.getFromPort()).distance(wire.getFrom()) < .1f
                    && endpoint(view, wire, model.getToPort()).distance(wire.getTo()) < .1f;
        });
    }

    private static Vector2f endpoint(AiGraphView view, WireElement wire, PortModel port) {
        var element = (PortElement) view.getModelElement(port);
        var icon = element.getConnector().getConnectorIcon();
        return wire.getParent().worldToLocalLayoutOffset(element.getWorldMouse(
                icon.getPositionX() + icon.getSizeWidth() / 2, icon.getPositionY() + icon.getSizeHeight() / 2));
    }

    private static Map<UUID, Vector2f> positions(AiGraphView view) {
        return view.model().getNodeModels().stream().collect(Collectors.toMap(n -> n.getUid(), n -> new Vector2f(n.getPosition())));
    }

    private static AiNodeModel node(TestContext context, String id) {
        return (AiNodeModel) view(context).model().getModel(UUID.fromString(id));
    }

    private static AiGraphView view(TestContext context) {
        return (AiGraphView) window(context).getCurrentEditor().select("#npc_ai_graph_action").findFirst().orElseThrow();
    }

    private static EditorWindow window(TestContext context) {
        return (EditorWindow) ((ModularUIContainerScreen) context.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
