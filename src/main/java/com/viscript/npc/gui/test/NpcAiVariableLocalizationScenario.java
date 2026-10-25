package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import java.util.List;
import java.util.UUID;

/** 验证变量翻译信息的编辑、撤销、工程和运行文件保存，以及调用参数和端口的显示。 */
@LDLRegisterClient(name = "npc_ai_variable_localization", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiVariableLocalizationScenario implements UIScenario {
    private static final String NAME_KEY = "viscript_npc.ai.parameter.damage";
    private static final String TIPS_KEY = NAME_KEY + ".tips";

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai", "regression");
    }

    @Override public void define(ScenarioBuilder s) {
        var fixture = AiExamples.action("melee");
        var damage = fixture.parameters.getFirst();
        damage.displayNameKey = ""; damage.tipsKey = "";
        s.server("打开变量翻译设置测试工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开含无翻译变量的行为", c -> {
                    var project = new NpcActionProject(); project.program = fixture;
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action #npc_ai_variables")
                .check("无翻译信息仍使用原变量名", c -> {
                    var variable = view(c).model().compile().parameters.getFirst();
                    return variable.name.equals("damage") && AiVariables.displayName(variable).getString().equals("damage")
                            && AiVariables.tips(variable).isEmpty();
                }).click("#npc_ai_graph_action #npc_ai_variables").ticks(2)
                .step("标记伤害变量", c -> {
                    var v = view(c); var declaration = v.model().getModel(UUID.fromString(damage.id));
                    v.blackboard.getItemUIs().get(declaration).setId("npc_ai_localized_declaration");
                }).click("#npc_ai_localized_declaration")
                .awaitElement("#npc_ai_variable_display_name_key")
                .typeInto("#npc_ai_variable_display_name_key", NAME_KEY).key(Keys.ENTER).ticks(2)
                .typeInto("#npc_ai_variable_tips_key", TIPS_KEY).key(Keys.ENTER).ticks(2)
                .check("翻译设置进入行为输入且稳定绑定不变", c -> {
                    var program = view(c).model().compile(); var variable = program.parameters.getFirst();
                    return variable.id.equals(damage.id) && variable.name.equals("damage") && variable.initial.number == 4
                            && variable.displayNameKey.equals(NAME_KEY) && variable.tipsKey.equals(TIPS_KEY)
                            && program.nodes.stream().filter(n -> n.type.equals("melee")).findFirst().orElseThrow()
                            .inputs.get("damage").variableId.equals(damage.id);
                }).screenshot("variable_translation_settings")
                .step("撤销用途说明设置", c -> view(c).getHistoryStack().undo()).ticks(2)
                .check("撤销仅恢复说明字段", c -> {
                    var variable = view(c).model().compile().parameters.getFirst();
                    return variable.displayNameKey.equals(NAME_KEY) && variable.tipsKey.isEmpty();
                }).step("重做用途说明设置", c -> view(c).getHistoryStack().redo()).ticks(2)
                .check("重做恢复翻译和说明", c -> view(c).model().compile().parameters.getFirst().tipsKey.equals(TIPS_KEY))
                .step("保存工程和导出行为文件", c -> {
                    var project = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                    var saved = new NpcActionProject();
                    saved.deserializeNBT(Platform.getFrozenRegistry(), project.serializeNBT(Platform.getFrozenRegistry()));
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), saved.graph);
                    var variable = graph.model().compile().parameters.getFirst();
                    c.check("工程重开保留变量显示信息", variable.displayNameKey.equals(NAME_KEY) && variable.tipsKey.equals(TIPS_KEY));
                    var runtime = new AiProgram(); runtime.deserializeNBT(Platform.getFrozenRegistry(),
                            project.serializeRuntimeFile(Platform.getFrozenRegistry()));
                    var copy = runtime.copy(Platform.getFrozenRegistry()).parameters.getFirst();
                    c.check("运行文件和实例快照保留翻译信息", copy.displayNameKey.equals(NAME_KEY) && copy.tipsKey.equals(TIPS_KEY));
                    var nativeVariable = (VariableDeclarationModelBase) graph.model().getModel(UUID.fromString(damage.id));
                    c.check("重开后变量提示完整", nativeVariable.getTooltips().asList().stream()
                            .anyMatch(t -> t.getString().equals(Component.translatable(TIPS_KEY).getString())));
                    var node = graph.model().createVariableNode(nativeVariable, new Vector2f(), null, null);
                    var clipboard = graph.model().copyElements(List.of(node), Platform.getFrozenRegistry());
                    var pasted = new AiGraph("action"); pasted.model().pasteElements(clipboard, new Vector2f());
                    var pastedVariable = pasted.model().compile().parameters.getFirst();
                    c.check("跨图复制变量携带名称和说明", pastedVariable.id.equals(damage.id)
                            && pastedVariable.displayNameKey.equals(NAME_KEY) && pastedVariable.tipsKey.equals(TIPS_KEY));
                    var missingTranslation = new AiVariable(); missingTranslation.name = "damage";
                    missingTranslation.displayNameKey = "missing.translation"; missingTranslation.tipsKey = "missing.description";
                    c.check("不存在的翻译键回退且不泄漏键名", AiVariables.displayName(missingTranslation).getString().equals("damage") && AiVariables.tips(missingTranslation).isEmpty());
                }).step("打开行为调用参数", c -> {
                    var project = new NpcActionProject();
                    var graph = new AiGraph("action"); var model = graph.model();
                    var root = model.add("root", new Vector2f(0, -120));
                    var call = model.add("call", new Vector2f());
                    call.data.input("file", AiValue.text("examples/melee")); call.defineNode();
                    model.createWire(call.getInputsById().get("parent"), root.getOutputsById().get("children"), null);
                    project.graph = model.serializeNBT(Platform.getFrozenRegistry()); project.program = model.compile();
                    c.put("localized_call", call.getUid());
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action")
                .step("选择调用节点", c -> {
                    var v = view(c); var call = (AiNodeModel)v.model().getModel(c.get("localized_call"));
                    v.clearAllSelected(); v.addSelected(call); v.inspectNode(call); v.fitGraphChildren();
                }).awaitElement("#npc_ai_field_param_" + damage.id)
                .check("参数表单使用翻译名称及用途提示", c -> {
                    var row = c.el("#npc_ai_field_param_" + damage.id).as(Configurator.class);
                    row.tip.setId("npc_ai_parameter_translation_tip");
                    return row.label.getText().getString().equals(Component.translatable(NAME_KEY).getString())
                            && row.tip.getStyle().tooltips().asList().stream()
                            .anyMatch(t -> t.getString().equals(Component.translatable(TIPS_KEY).getString()));
                }).check("蓝图端口保留用途说明及默认值机制", c -> {
                    var call = (AiNodeModel)view(c).model().getModel(c.get("localized_call"));
                    var port = call.getInputsById().get("value_param:" + damage.id);
                    return port.getTitle().getString().equals(Component.translatable(NAME_KEY).getString())
                            && port.getTooltips().asList().stream().anyMatch(t -> t.getString().equals(Component.translatable(TIPS_KEY).getString()))
                            && !call.data.inputs.containsKey("param:" + damage.id)
                            && !c.query("#npc_ai_field_param_" + damage.id + " *").type(NumberConfigurator.class)
                            .one().as(NumberConfigurator.class).isActive();
                }).hover("#npc_ai_parameter_translation_tip").ticks(14)
                .screenshot("localized_behavior_parameters_and_tips").closeScreen();
    }

    private static EditorWindow window(TestContext c) {
        return (EditorWindow)((ModularUIContainerScreen)c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }

    private static AiGraphView view(TestContext c) {
        return (AiGraphView)window(c).getCurrentEditor().select("#npc_ai_graph_action").findFirst().orElseThrow();
    }
}
