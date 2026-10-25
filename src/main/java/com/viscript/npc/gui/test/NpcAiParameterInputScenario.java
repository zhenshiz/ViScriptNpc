package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.NodeCommands;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import org.joml.Vector2f;

import java.util.UUID;

/** 验证原生数据连线优先级、目标枚举类型、断线恢复和工程快照。 */
@LDLRegisterClient(name = "npc_ai_parameter_inputs", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiParameterInputScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression"); }

    @Override public void define(ScenarioBuilder s) {
        var fixture = NpcAiFixtures.action("find_target");
        var find = fixture.nodes.stream().filter(n -> n.type.equals("find_target")).findFirst().orElseThrow();
        find.input("range", AiValue.number(24));
        var distance = variable("Search radius", AiValue.number(6.5));
        var filterValue = AiOperations.get("find_target").fields().stream().filter(f -> f.id().equals("filter")).findFirst().orElseThrow().initial().copy();
        filterValue.text = "living";
        var filter = variable("Target filter", filterValue);
        var mob = variable("Mob type", AiValue.mobType("minecraft:zombie"));
        fixture.parameters.add(distance); fixture.parameters.add(filter); fixture.parameters.add(mob);
        s.server("打开参数输入测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("载入带原生类型参数的行为", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    window.createNewEditor(NpcEditor::new).loadProject(project, null);
                    view(c).inspectNode(node(c, find.id));
                }).ticks(2)
                .check("目标筛选和 Mob 类型拥有独立端口类型", c -> {
                    var n = node(c, find.id);
                    return n.getInputsById().get("value_filter").getDataTypeHandle().equals(AiVariables.type(filterValue.type))
                            && n.getInputsById().get("value_entity_type").getDataTypeHandle().equals(AiVariables.type("mob_type"))
                            && !AiVariables.type(filterValue.type).equals(TypeHandles.STRING);
                })
                .check("固定玩家模式隐藏 Mob 端口与表单", c -> !view(c).getModelElement(node(c, find.id).getInputsById().get("value_entity_type")).isDisplayed()
                        && !c.el("#npc_ai_field_entity_type").element().isDisplayed())
                .step("用原生变量节点连接距离", c -> connect(c, find.id, "range", distance.id)).ticks(2)
                .check("连线优先但右侧配置仍为 24", c -> {
                    var n = node(c, find.id); var b = view(c).model().compile().node(find.id).inputs.get("range");
                    return b.scope.equals("parameter") && b.variableId.equals(distance.id) && b.value.number == 6.5
                            && n.data.inputs.get("range").value.number == 24
                            && c.count("#npc_ai_connected_range") == 1
                            && !c.query("#npc_ai_field_range *").type(NumberConfigurator.class).one().element().isActive();
                })
                .step("连接类型化目标筛选和 Mob 类型", c -> {
                    connect(c, find.id, "filter", filter.id); connect(c, find.id, "entity_type", mob.id);
                }).ticks(2)
                .check("动态筛选显示 Mob 端口并保留右侧玩家默认值", c -> view(c).getModelElement(node(c, find.id).getInputsById().get("value_entity_type")).isDisplayed()
                        && c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && node(c, find.id).data.literal("filter", AiValue.text("")).text.equals("player")
                        && view(c).model().compile().node(find.id).inputs.get("filter").variableId.equals(filter.id))
                .check("不同枚举和普通文本不能接入筛选", c -> {
                    var model = view(c).model(); var n = node(c, find.id);
                    var source = model.getNodeModels().stream().filter(x -> x instanceof VariableNodeModel v
                            && v.getVariableDeclarationModel().getUid().toString().equals(mob.id)).map(VariableNodeModel.class::cast).findFirst().orElseThrow();
                    return !model.canAssignTo(n.getInputsById().get("value_filter"), source.getOutputPort());
                })
                .screenshot("connected_parameter_defaults")
                .step("保存并重开所有数据连线", c -> {
                    var graph = new AiGraph("action");
                    graph.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var p = graph.model().compile();
                    c.check("原生枚举默认值和类型保存完整", p.parameters.stream().anyMatch(v -> v.id.equals(filter.id)
                            && v.initial.type.equals(filterValue.type) && v.initial.text.equals("living"))
                            && p.parameters.stream().anyMatch(v -> v.id.equals(mob.id) && v.initial.type.equals("mob_type")), true, true);
                    c.check("重开后连线仍以稳定 ID 绑定且编译有效", p.node(find.id).inputs.get("range").variableId.equals(distance.id)
                            && p.node(find.id).inputs.get("filter").variableId.equals(filter.id)
                            && p.node(find.id).inputs.get("entity_type").variableId.equals(mob.id)
                            && AiValidation.validate(p, ignored -> null).isEmpty(), true, true);
                })
                .step("断开距离输入", c -> disconnect(c, find.id, "range")).ticks(2)
                .check("断线立即恢复原默认值和编辑能力", c -> view(c).model().compile().node(find.id).inputs.get("range").value.number == 24
                        && c.count("#npc_ai_connected_range") == 0
                        && c.query("#npc_ai_field_range *").type(NumberConfigurator.class).one().element().isActive())
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销断线恢复参数绑定", c -> view(c).model().compile().node(find.id).inputs.get("range").variableId.equals(distance.id))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .step("连接原生常量节点", c -> view(c).edit("graph", () -> {
                    var constant = view(c).model().createConstantNode(TypeHandles.DOUBLE, "Range", new Vector2f(-180, 0), null,
                            n -> n.getConstant().setValue(0D), null);
                    view(c).model().createWire(node(c, find.id).getInputsById().get("value_range"), constant.getOutputPort(), null);
                })).ticks(2)
                .check("常量零值有效且不覆盖原配置", c -> view(c).model().compile().node(find.id).inputs.get("range").value.number == 0
                        && node(c, find.id).data.inputs.get("range").value.number == 24)
                .step("损坏常量不会编译成零值", c -> {
                    var graph = new AiGraph("action");
                    graph.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var constant = graph.model().getNodeModels().stream()
                            .filter(com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ConstantNodeModel.class::isInstance)
                            .map(com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ConstantNodeModel.class::cast).findFirst().orElseThrow();
                    constant.getConstant().setValue("wrong type");
                    c.check("错误常量类型阻止编译发布", graph.model().compile().node(find.id).inputs.get("range").scope.equals("invalid")
                            && !AiValidation.validate(graph.model().compile(), ignored -> null).isEmpty(), true, true);
                })
                .step("断开动态筛选", c -> disconnect(c, find.id, "filter")).ticks(2)
                .check("恢复玩家默认值后隐藏 Mob 输入但保留绑定", c -> !c.el("#npc_ai_field_entity_type").element().isDisplayed()
                        && node(c, find.id).getInputsById().get("value_entity_type").getConnectedWires().size() == 1)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销恢复动态筛选与 Mob 输入", c -> c.el("#npc_ai_field_entity_type").element().isDisplayed())
                .step("删除数据源声明", c -> view(c).edit("graph", () -> view(c).model().deleteVariableDeclarations(
                        java.util.List.of(view(c).model().getGraphVariableModels().stream().filter(v -> v != null && v.getUid().toString().equals(filter.id)).findFirst().orElseThrow()), false)))
                .ticks(2)
                .check("缺失数据源由原生图诊断定位且不回退", c -> {
                    var logger = new GraphLogger(); view(c).model().onGraphChanged(logger);
                    return logger.getEntries().stream().anyMatch(e -> e.level() == GraphLogger.Level.ERROR)
                            && view(c).model().compile().node(find.id).inputs.get("filter").scope.equals("invalid");
                }).screenshot("invalid_parameter_input_diagnostic").closeScreen();
    }

    private static AiVariable variable(String name, AiValue initial) { var v = new AiVariable(); v.name = name; v.initial = initial; return v; }
    private static AiGraphView view(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
    private static AiNodeModel node(TestContext c, String id) { return (AiNodeModel) view(c).model().getModel(UUID.fromString(id)); }
    private static void connect(TestContext c, String id, String key, String variable) {
        var v = view(c); var n = node(c, id);
        var declaration = v.model().getGraphVariableModels().stream().filter(d -> d != null && d.getUid().toString().equals(variable)).findFirst().orElseThrow();
        int offset = switch (key) { case "filter" -> 60; case "entity_type" -> 120; default -> 0; };
        v.dispatchCommand(new NodeCommands.CreateNodeCommand().withNodeOnPort(declaration,
                n.getInputsById().get("value_" + key), n.getPosition().add(-220, offset, new Vector2f()), null));
    }
    private static void disconnect(TestContext c, String id, String key) {
        view(c).edit("graph", () -> view(c).model().deleteWires(java.util.List.copyOf(node(c, id).getInputsById().get("value_" + key).getConnectedWires())));
    }
}
