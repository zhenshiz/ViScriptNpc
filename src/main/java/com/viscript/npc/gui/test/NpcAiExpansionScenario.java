package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.BooleanConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

/** 通过真实下拉框和开关验证运算类型切换、撤销和导航能力保存。 */
@LDLRegisterClient(name = "npc_ai_expansion", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiExpansionScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) { options.requiresWorld(true).guiScale(2).tags("ui", "ai"); }
    @Override public void define(ScenarioBuilder s) {
        var p = new AiProgram(); p.kind = "action";
        var vector = new AiVariable(); vector.name = "vector_result"; vector.initial = AiValue.position(Vec3.ZERO); p.variables.add(vector);
        var number = new AiVariable(); number.name = "length_result"; number.initial = AiValue.number(0); p.variables.add(number);
        var math = new AiNodeData(); math.type = "vector_math"; math.input("output", AiValue.text(vector.id));
        var move = new AiNodeData(); move.type = "navigate";
        var sequence = new AiNodeData(); sequence.type = "sequence"; sequence.children.add(math.id); sequence.children.add(move.id);
        var root = new AiNodeData(); root.type = "root"; root.children.add(sequence.id); p.root = root.id;
        p.nodes.add(root); p.nodes.add(sequence); p.nodes.add(math); p.nodes.add(move);
        s.server("打开新增能力测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(), new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开向量运算行为", c -> {
                    var project = new NpcActionProject(); project.program = p;
                    ((EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement)
                            .createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action")
                .click("#npc_ai_graph_action #npc_ai_fit").ticks(2).click("#npc_ai_node_" + math.id).ticks(2)
                .step("标记向量运算下拉框", c -> {
                    var selector = c.query("#npc_ai_field_vector_operator *").type(SelectorConfigurator.class).one().as(SelectorConfigurator.class).selector;
                    selector.setId("npc_ai_expansion_operator"); selector.scrollerView.setId("npc_ai_expansion_operator_scroll");
                })
                .click("#npc_ai_expansion_operator").ticks(1)
                .screenshot("vector_operation_menu")
                .step("选择长度运算", c -> choose(c, Component.translatable("viscript_npc.ai.choice.length").getString())).ticks(2)
                .step("检查运算选择的实际值", c -> {
                    String actual = view(c).model().compile().node(math.id).literal("vector_operator", AiValue.text("add")).text;
                    c.check("运算选择写回为 length，实际=" + actual, actual.equals("length"));
                })
                .check("数值运算提示旧位置输出类型不匹配", c -> !AiValidation.validate(view(c).model().compile(), NpcActionFiles::load).isEmpty())
                .step("标记输出变量下拉框", c -> c.query("#npc_ai_field_output *").type(SelectorConfigurator.class)
                        .one().as(SelectorConfigurator.class).selector.setId("npc_ai_expansion_output"))
                .click("#npc_ai_expansion_output").ticks(1)
                .step("选择数值输出", c -> choose(c, "length_result")).ticks(2)
                .check("对应类型的输出通过完整校验", c -> AiValidation.validate(view(c).model().compile(), NpcActionFiles::load).isEmpty())
                .screenshot("vector_length_output")
                .click("#npc_ai_graph_action #npc_ai_undo").click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("撤销恢复向量运算与位置输出", c -> view(c).model().compile().node(math.id).literal("output", AiValue.text("")).text.equals(vector.id)
                        && AiValidation.validate(view(c).model().compile(), NpcActionFiles::load).isEmpty())
                .click("#npc_ai_graph_action #npc_ai_redo").click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .click("#npc_ai_node_" + move.id).ticks(2);
        for (String capability : new String[]{"open_doors", "swim", "climb"}) {
            s.step("标记移动能力开关 " + capability, c -> c.query("#npc_ai_field_" + capability + " *")
                            .type(BooleanConfigurator.class).one().as(BooleanConfigurator.class).toggle.toggleButton.setId("npc_ai_expansion_" + capability))
                    .click("#npc_ai_expansion_" + capability).ticks(2);
        }
        s.check("移动节点保存全部已选能力", c -> {
                    var n = view(c).model().compile().node(move.id);
                    return n.literal("open_doors", AiValue.bool(false)).flag && n.literal("swim", AiValue.bool(false)).flag && n.literal("climb", AiValue.bool(false)).flag;
                }).screenshot("navigation_capabilities")
                .step("验证保存重开保持类型和能力", c -> {
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), view(c).model().serializeNBT(Platform.getFrozenRegistry()));
                    var saved = graph.model().compile();
                    c.check("完整工程校验通过", AiValidation.validate(saved, NpcActionFiles::load).isEmpty());
                    c.check("数值输出与爬梯能力保留", saved.node(math.id).literal("output", AiValue.text("")).text.equals(number.id)
                            && saved.node(move.id).literal("climb", AiValue.bool(false)).flag);
                }).closeScreen();
    }
    private static AiGraphView view(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
    private static void choose(TestContext c, String text) {
        var bounds = c.query().type(Label.class).withText(text).visible()
                .where(e -> e.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton"))).one().bounds();
        c.input().moveTo(bounds.centerX(), bounds.centerY());
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT); c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
    }
}
