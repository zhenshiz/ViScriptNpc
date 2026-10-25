package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.GraphPanel;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;

/** 用真实输入验证分区布局、调用参数、外观检查器与细粒度缩放。 */
@LDLRegisterClient(name = "npc_ai_editor_layout", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiEditorLayoutScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai"); }

    @Override public void define(ScenarioBuilder s) {
        var action = AiExamples.action("melee");
        String damage = action.parameters.getFirst().id;
        String interval = action.parameters.get(1).id;
        var tree = AiExamples.tree("melee");
        String callId = tree.nodes.stream().filter(n -> n.type.equals("call")
                && n.literal("file", AiValue.text("")).text.equals("examples/melee")).findFirst().orElseThrow().id;
        String call = "#npc_ai_node_" + callId + " .__node-title-element__";
        String source = "#npc_ai_source_param_" + damage;
        String value = "#npc_ai_value_param_" + damage;
        s.server("打开参数化攻击树", server -> {
            var project = new NPCProject(); project.npc.getNpcData(NpcAI.class).setProgram(tree);
            ViScriptNpcServerUtil.openNpcEditor(server.player(), project.serializeNBT(Platform.getFrozenRegistry()));
        }).awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").click("#npc_ai_fit").ticks(3)
                .check("隐藏底部分区后画布与右侧 View 底部对齐", c -> {
                    var canvas = c.el("#npc_ai_graph_tree").bounds();
                    var right = c.el("#npc_ai_parameters_view").bounds();
                    return Math.abs(canvas.bottom() - right.bottom()) < 4 && canvas.x() < 10 && right.width() > 60;
                })
                .check("蓝图工具栏没有文件保存按钮", c -> c.count("#npc_ai_save") == 0)
                .step("准备滚轮锚点", c -> {
                    var v = c.el("#npc_ai_graph_tree").as(AiGraphView.class);
                    var bounds = ElementBounds.of(v.graphView);
                    c.put("zoom", v.graphView.getScale());
                    c.put("wheel_x", bounds.x() + 20); c.put("wheel_y", bounds.y() + 20);
                    c.input().moveTo(bounds.x() + 20, bounds.y() + 20);
                }).ticks(1)
                .step("滚动一格", c -> c.input().scroll(c.get("wheel_x"), c.get("wheel_y"), 1)).ticks(1)
                .check("一格缩放约百分之六", c -> Math.abs(c.el("#npc_ai_graph_tree").as(AiGraphView.class)
                        .graphView.getScale() / c.<Float>get("zoom") - 1.06) < .002)
                .step("反向滚动恢复视角", c -> c.input().scroll(c.get("wheel_x"), c.get("wheel_y"), -1)).ticks(1)
                .check("正反缩放不会漂移", c -> Math.abs(c.el("#npc_ai_graph_tree").as(AiGraphView.class)
                        .graphView.getScale() - c.<Float>get("zoom")) < .001)
                .click(call).ticks(2)
                .check("右侧按行为定义显示伤害和攻击间隔", c ->
                        c.count("#npc_ai_parameters_view #npc_ai_field_param_" + damage) == 1
                        && c.count("#npc_ai_parameters_view #npc_ai_field_param_" + interval) == 1
                        && c.count("#npc_ai_appearance_inspector #npc_ai_field_param_" + damage) == 0)
                .step("展开节点外观检查器", c -> c.el("#npc_ai_appearance").as(GraphPanel.class).setCollapsed(false)).ticks(2)
                .check("画布检查器只包含外观项", c -> c.query("#npc_ai_appearance_inspector").withTextContaining("damage").count() == 0
                        && c.query("#npc_ai_appearance_inspector *").withText(Component.translatable("graph.min_width").getString()).count() > 0)
                .click(source).ticks(1)
                .step("选择本次调用覆盖", c -> choose(c, "viscript_npc.ai.parameter_override")).ticks(2)
                .typeInto(value, "11").key(Keys.ENTER).ticks(2)
                .check("覆盖只写入当前调用节点", c -> node(c, callId).data.inputs.get("param:" + damage).value.number == 11
                        && AiExamples.action("melee").parameters.getFirst().initial.number == 4)
                .click("#npc_ai_undo").ticks(2)
                .check("撤销改变调用值", c -> node(c, callId).data.inputs.get("param:" + damage).value.number != 11)
                .click("#npc_ai_redo").ticks(2)
                .check("重做恢复调用值", c -> node(c, callId).data.inputs.get("param:" + damage).value.number == 11)
                .step("通过文件选择器切换行为", c -> c.query("#npc_ai_field_file *")
                        .type(com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent.class).one()
                        .as(com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent.class).setSelected("examples/ranged"))
                .ticks(2)
                .check("切换行为清除旧覆盖并显示新专属参数", c -> !node(c, callId).data.inputs.containsKey("param:" + damage)
                        && c.count("#npc_ai_field_param_" + AiExamples.action("ranged").parameters.getFirst().id) == 1)
                .click("#npc_ai_undo").ticks(2)
                .check("撤销切换恢复原行为及覆盖", c -> node(c, callId).data.literal("file", AiValue.text("")).text.equals("examples/melee")
                        && node(c, callId).data.inputs.get("param:" + damage).value.number == 11)
                .step("打开相同参数标识但不同显示名的行为草稿", c -> {
                    var window = window(c); var owner = (NpcEditor) window.getCurrentEditor();
                    var draft = new NpcActionProject(); draft.program = action.copy(Platform.getFrozenRegistry());
                    draft.sourceActionId = "examples/melee";
                    draft.program.parameters.getFirst().name = "攻击伤害";
                    draft.program.parameters.getFirst().displayNameKey = "";
                    draft.program.parameters.get(1).name = "攻击间隔（秒）";
                    draft.program.parameters.get(1).displayNameKey = "";
                    window.createNewEditor(NpcEditor::new).loadProject(draft, null);
                    window.showEditor(owner);
                }).click("#npc_ai_node_" + tree.root).click(call).ticks(2)
                .check("参数重命名仍保留原绑定", c -> node(c, callId).data.inputs.get("param:" + damage).value.number == 11
                        && c.query("#npc_ai_parameters_view *").withText("攻击伤害").count() > 0)
                .screenshot("ai_call_parameters_and_appearance")
                .step("保存并重开 NPC 工程", c -> {
                    var project = (NPCProject) window(c).getCurrentEditor().getCurrentProject();
                    var file = java.nio.file.Path.of("ai-call-parameters.npcproj").toFile();
                    try {
                        project.getProjectType().saveProjectToFile(project, file);
                        var reopened = (NPCProject) project.getProjectType().loadProjectFromFile(file);
                        c.check("稳定参数标识与覆盖值保存", reopened.npc.getNpcData(NpcAI.class).getProgram()
                                .node(callId).inputs.get("param:" + damage).value.number == 11, 11, 11);
                    } catch (Exception exception) { throw new IllegalStateException(exception); }
                })
                .click(source).ticks(1).step("恢复行为默认值", c -> choose(c, "viscript_npc.ai.parameter_default")).ticks(2)
                .check("恢复默认值删除单次覆盖", c -> !node(c, callId).data.inputs.containsKey("param:" + damage))
                .step("缩放至 GUI Scale 3", c -> { c.mc().options.guiScale().set(3); c.mc().resizeDisplay(); }).ticks(3)
                .check("图标全部保持在按钮内部", c -> c.query("#npc_ai_graph_tree .npc_ai_toolbar_icon").where(ref -> ref.getParent().isDisplayed()).list().stream().allMatch(ref -> {
                    var icon = ref.bounds(); var button = ElementBounds.of(ref.element().getParent());
                    return icon.x() >= button.x() + 1 && icon.y() >= button.y() + 1
                            && icon.right() <= button.right() - 1 && icon.bottom() <= button.bottom() - 1;
                })).screenshot("ai_call_parameters_scale3")
                .closeScreen();
    }

    private static AiNodeModel node(TestContext context, String id) {
        return (AiNodeModel) context.el("#npc_ai_graph_tree").as(AiGraphView.class).model().getNodeModels()
                .stream().filter(n -> n.getUid().toString().equals(id)).findFirst().orElseThrow();
    }

    private static EditorWindow window(TestContext context) {
        return (EditorWindow) ((ModularUIContainerScreen) context.mc().screen).getMenu().getModularUI().ui.rootElement;
    }

    private static void choose(TestContext context, String key) {
        var bounds = context.query().type(Label.class).withText(Component.translatable(key).getString()).visible()
                .where(element -> element.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton"))).one().bounds();
        context.input().moveTo(bounds.centerX(), bounds.centerY());
        context.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
        context.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
}
