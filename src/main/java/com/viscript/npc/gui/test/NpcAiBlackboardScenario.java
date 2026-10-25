package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector2f;

import java.util.Objects;
import java.util.UUID;

/** 验证原生 Blackboard 的变量导入、类型化保存、接线，以及删除和断线后的编辑器诊断。 */
@LDLRegisterClient(name = "npc_ai_blackboard", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiBlackboardScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression"); }

    @Override public void define(ScenarioBuilder s) {
        var fixture = AiExamples.action("jump_slash");
        var flag = variable("enabled", AiValue.bool(true));
        var text = variable("message", AiValue.text("hello"));
        var shared = variable("stamina", AiValue.number(42));
        fixture.variables.add(flag); fixture.variables.add(text); fixture.npcVariables.add(shared);
        String damage = fixture.parameters.getFirst().id;
        String hit = fixture.nodes.stream().filter(n -> n.type.equals("area_damage")).findFirst().orElseThrow().id;
        s.server("打开带变量声明的行为工程", server -> {
            var project = new com.viscript.npc.gui.edit.NPCProject();
            ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
        }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("在编辑器打开行为工程", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = new NpcActionProject(); project.program = fixture;
                    window.createNewEditor(com.viscript.npc.gui.edit.NpcEditor::new).loadProject(project, null);
                })
                .awaitElement("#npc_ai_graph_action #npc_ai_variables").click("#npc_ai_graph_action #npc_ai_variables").ticks(2)
                .check("使用 LDLib2 原生变量列表", c -> view(c).blackboard.graphView == view(c)
                        && view(c).model().getGraphVariableModels().size() == 6
                        && view(c).blackboard.getItemUIs().size() == 6
                        && c.count("#npc_ai_blackboard") == 1)
                .check("导入后使用原生变量声明", c -> view(c).model().metadata.parameters.isEmpty()
                        && view(c).model().metadata.variables.isEmpty() && view(c).model().metadata.npcVariables.isEmpty()
                        && view(c).model().compile().parameters.getFirst().id.equals(damage))
                .step("标记 Vec3 默认值变量", c -> {
                    var v = view(c);
                    var declaration = v.model().getGraphVariableModels().stream()
                            .filter(item -> item.getDataTypeHandle().equals(AiVariables.POSITION)).findFirst().orElseThrow();
                    v.blackboard.getItemUIs().get(declaration).setId("npc_ai_position_declaration");
                }).click("#npc_ai_position_declaration").ticks(2)
                .step("标记 Vec3 三轴默认值控件", c -> {
                    var field = c.query("#npc_ai_parameters_view *").type(NumberConfigurator.class).list().getFirst().as(NumberConfigurator.class);
                    field.textField.setId("npc_ai_position_x");
                }).typeInto("#npc_ai_position_x", "3.5").key(Keys.ENTER).ticks(2)
                .check("Vec3 默认值可编辑并写入编译结果", c -> view(c).model().compile().variables.stream()
                        .anyMatch(variable -> variable.initial.type.equals("position")
                                && Math.abs(variable.initial.position.x - 3.5) < 0.0001
                                && variable.initial.position.y == 0 && variable.initial.position.z == 0))
                .step("用原生黑板创建变量", c -> view(c).blackboard.createVariable()).ticks(2)
                .check("原生创建命令有效", c -> view(c).model().getGraphVariableModels().size() == 7)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("原生创建可撤销", c -> view(c).model().getGraphVariableModels().size() == 6)
                .step("标记黑板中的行为输入参数", c -> {
                    var v = view(c); var declaration = v.model().getModel(UUID.fromString(damage));
                    v.blackboard.getItemUIs().get(declaration).setId("npc_ai_damage_declaration");
                }).click("#npc_ai_damage_declaration").ticks(2)
                .step("标记原生默认值控件", c -> c.query("#npc_ai_parameters_view *").type(NumberConfigurator.class)
                        .one().as(NumberConfigurator.class).textField.setId("npc_ai_native_default"))
                .typeInto("#npc_ai_native_default", "12").key(Keys.ENTER).ticks(2)
                .check("原生默认值进入编译结果", c -> view(c).model().compile().parameters.getFirst().initial.number == 12)
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("原生默认值修改可撤销", c -> view(c).model().compile().parameters.getFirst().initial.number == 6)
                .step("用原生命令将变量节点接到伤害输入", c -> {
                    var v = view(c);
                    var declaration = v.model().getGraphVariableModels().stream().filter(d -> d.getUid().toString().equals(damage)).findFirst().orElseThrow();
                    var node = (AiNodeModel) v.model().getModel(UUID.fromString(hit));
                    v.dispatchCommand(new NodeCommands.CreateNodeCommand().withNodeOnPort(declaration,
                            node.getInputsById().get("value_damage"), node.getPosition().add(-180, 0, new Vector2f()), null));
                }).ticks(2)
                .check("变量接线编译为稳定 ID 参数绑定", c -> {
                    var binding = view(c).model().compile().node(hit).inputs.get("damage");
                    return binding.scope.equals("parameter") && binding.variableId.equals(damage)
                            && view(c).model().getNodeModels().stream().anyMatch(VariableNodeModel.class::isInstance);
                })
                .step("保存并重开原生变量图", c -> {
                    var v = view(c); var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), v.model().serializeNBT(Platform.getFrozenRegistry()));
                    var program = reopened.model().compile();
                    c.check("保留布尔、文本、坐标和 NPC 共享变量类型", program.variables.stream().anyMatch(x -> x.id.equals(flag.id) && x.initial.flag)
                            && program.variables.stream().anyMatch(x -> x.id.equals(text.id) && x.initial.text.equals("hello"))
                            && program.variables.stream().anyMatch(x -> x.initial.type.equals("position")
                                    && Math.abs(x.initial.position.x - 3.5) < 0.0001
                                    && x.initial.position.y == 0 && x.initial.position.z == 0)
                            && program.npcVariables.getFirst().id.equals(shared.id) && program.npcVariables.getFirst().initial.number == 42, true, true);
                    c.check("原生变量节点接线重开后有效", program.node(hit).inputs.get("damage").variableId.equals(damage)
                            && AiValidation.validate(program, ignored -> null).isEmpty(), true, true);
                })
                .screenshot("native_blackboard")
                .step("删除选中的阶段节点及子树", c -> {
                    var v = view(c); var stage = v.model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("stage"))
                            .map(AiNodeModel.class::cast).findFirst().orElseThrow();
                    c.put("deleted_stage", stage.getUid());
                    v.clearAllSelected(); v.addSelected(stage); v.inspectNode(stage); v.deleteSelectedElements();
                }).ticks(4)
                .check("删除留下空位时画布继续工作", c -> view(c).model().getNodeModels().stream().anyMatch(Objects::isNull)
                        && view(c).model().getModel(c.get("deleted_stage")) == null)
                .step("删除后继续创建和整理节点", c -> view(c).edit("add", () -> {
                    view(c).model().add("wait", new Vector2f(200, 200)); view(c).model().arrange();
                })).ticks(2)
                .check("未连接草稿通过原生日志反馈", c -> {
                    var logger = new GraphLogger(); view(c).model().onGraphChanged(logger);
                    return logger.getEntries().stream().anyMatch(e -> e.level() == GraphLogger.Level.WARNING)
                            && c.count(".__node-graph-view_log-footer__") > 0;
                })
                .step("断开入口连接", c -> view(c).edit("graph", () -> {
                    var v = view(c); var root = (AiNodeModel) v.model().getModel(UUID.fromString(fixture.root));
                    v.model().deleteWires(java.util.List.copyOf(root.getOutputsById().get("children").getConnectedWires()));
                })).ticks(2)
                .check("断线只显示可定位错误", c -> {
                    var logger = new GraphLogger(); view(c).model().onGraphChanged(logger);
                    return logger.getEntries().stream().anyMatch(e -> e.level() == GraphLogger.Level.ERROR && e.context() instanceof AiNodeModel);
                }).click(".__node-graph-view_log-header__").ticks(2)
                .step("标记原生诊断首项", c -> c.query(".__node-graph-view_log-row__").visible().list()
                        .getFirst().element().setId("npc_ai_first_diagnostic"))
                .click("#npc_ai_first_diagnostic").ticks(2)
                .check("点击原生诊断定位入口节点", c -> view(c).getSelected().stream()
                        .anyMatch(model -> model.getUid().toString().equals(fixture.root)))
                .screenshot("disconnected_graph_diagnostics")
                .step("断线草稿仍可保存重开", c -> {
                    var window = (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
                    var project = (NpcActionProject) window.getCurrentEditor().getCurrentProject();
                    var draft = new NpcActionProject();
                    draft.deserializeNBT(Platform.getFrozenRegistry(), project.serializeNBT(Platform.getFrozenRegistry()));
                    var reopened = new AiGraph("action");
                    reopened.model().deserializeNBT(Platform.getFrozenRegistry(), draft.graph);
                    var logger = new GraphLogger(); reopened.model().onGraphChanged(logger);
                    c.check("错误图保留诊断且不崩溃", !logger.isEmpty(), true, true);
                }).closeScreen();
    }

    private static AiVariable variable(String name, AiValue value) {
        var variable = new AiVariable(); variable.name = name; variable.initial = value; return variable;
    }

    private static AiGraphView view(TestContext context) { return context.el("#npc_ai_graph_action").as(AiGraphView.class); }
}
