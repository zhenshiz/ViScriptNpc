package com.viscript.npc.gui.test;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.data.AiVariable;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.npc.ai.runtime.AiValidation;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import net.minecraft.nbt.CompoundTag;

/** 验证内置示例直接复制编辑、保存与原工程隔离。 */
@LDLRegisterClient(name = "npc_ai_templates", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiTemplateScenario implements UIScenario {
    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai");
    }

    @Override public void define(ScenarioBuilder s) {
        s.server("打开空白 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .step("打开空白行为工程", c -> window(c).createNewEditor(NpcEditor::new).loadProject(new NpcActionProject(), null))
                .awaitElement("#npc_ai_graph_action")
                .step("保留已有行为、变量和视角", c -> {
                    var view = view(c);
                    view.edit("add", () -> {
                        var variable = new AiVariable(); variable.name = "existing_state"; variable.initial = AiValue.bool(true);
                        view.model().addVariable(variable, "local");
                    });
                    view.setId("npc_ai_template_original_graph");
                    c.put("original_editor", window(c).getCurrentEditor());
                    c.put("original_graph", view.model().serializeNBT(Platform.getFrozenRegistry()));
                }).click("#npc_ai_examples").awaitElement("#npc_ai_example_melee")
                .check("官方目录只保留复合方案按钮", c -> AiExamples.NAMES.stream().allMatch(name ->
                        c.count("#npc_ai_example_" + name) == 1)
                        && c.count("#npc_ai_example_idle") == 0 && c.count("#npc_ai_example_acquire") == 0
                        && c.count("#npc_ai_example_approach") == 0)
                .screenshot("builtin_compound_examples");
        for (String name : AiExamples.NAMES) {
            s.step("复制示例到编辑标签 " + name, c -> activate(c, "#npc_ai_example_" + name)).awaitElement("#npc_ai_graph_action")
                    .check("示例直接创建独立可编辑工程 " + name, c -> {
                        var action = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                        var view = view(c);
                        return action.sourceActionId.isEmpty() && !view.isReadOnly()
                                && view.parametersView.inspector.isActive()
                                && view.model().compile().nodes.size() == AiExamples.action(name).nodes.size()
                                && AiValidation.validate(view.model().compile(), action.actionResolver).isEmpty();
                    }).step("示例副本支持编辑和撤销 " + name, c -> {
                        var view = view(c);
                        var before = view.model().serializeNBT(Platform.getFrozenRegistry());
                        view.edit("action_info", () -> view.model().metadata.displayNameKey = "my.custom." + name);
                        c.check("示例副本可修改 " + name, view.model().compile().displayNameKey.equals("my.custom." + name));
                        view.getHistoryStack().undo();
                        c.check("示例副本撤销恢复 " + name, before.equals(view.model().serializeNBT(Platform.getFrozenRegistry())));
                        if (name.equals("melee")) c.put("first_melee_project", window(c).getCurrentEditor().getCurrentProject());
                        window(c).showEditor(c.get("original_editor"));
                    }).awaitElement("#npc_ai_template_original_graph")
                    .check("原画布及黑板没有替换 " + name, c -> c.<CompoundTag>get("original_graph").equals(
                            view(c).model().serializeNBT(Platform.getFrozenRegistry())));
        }
        for (String name : AiExamples.NAMES) {
            s.step("记录官方方案拖拽前的状态 " + name, c -> {
                        c.put("before_drag_editors", window(c).getEditors().size());
                        c.put("before_drag_nodes", view(c).model().getNodeModels().size());
                    }).drag("#npc_ai_example_" + name, "#npc_ai_template_original_graph").ticks(2)
                    .check("拖拽官方方案仅插入调用节点 " + name, c -> {
                        var tree = view(c);
                        return window(c).getCurrentEditor() == c.get("original_editor")
                                && window(c).getEditors().size() == c.<Integer>get("before_drag_editors")
                                && tree.model().getNodeModels().size() == c.<Integer>get("before_drag_nodes") + 1
                                && tree.model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel a && a.operation().equals("call"))
                                .map(n -> (AiNodeModel)n).anyMatch(n -> n.data.literal("file", AiValue.text("")).text.equals("examples/" + name)
                                        && AiExamples.action(name).parameters.stream().allMatch(v -> n.getInputsById().containsKey("value_param:" + v.id)));
                    }).step("撤销官方方案拖入 " + name, c -> {
                        var tree = view(c);
                        tree.getHistoryStack().undo();
                        c.check("拖拽撤销恢复原工程 " + name, c.<CompoundTag>get("original_graph").equals(
                                tree.model().serializeNBT(Platform.getFrozenRegistry())));
                    });
        }
        s.screenshot("builtin_example_drag_and_undo")
                .step("打开调用节点引用的近战示例", c -> view(c).openAction("examples/melee"))
                .awaitElement("#npc_ai_graph_action")
                .check("打开内置行为同样创建新的可编辑副本", c -> {
                    var action = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                    return action != c.<NpcActionProject>get("first_melee_project") && action.sourceActionId.isEmpty()
                            && !view(c).isReadOnly();
                }).step("副本可修改、撤销并保存重开", c -> {
                    var view = view(c);
                    var before = view.model().serializeNBT(Platform.getFrozenRegistry());
                    view.edit("action_info", () -> view.model().metadata.displayNameKey = "my.custom.melee");
                    c.check("副本可修改", view.model().compile().displayNameKey.equals("my.custom.melee"));
                    view.getHistoryStack().undo();
                    c.check("副本撤销恢复", before.equals(view.model().serializeNBT(Platform.getFrozenRegistry())));
                    var project = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                    var reopened = new NpcActionProject();
                    reopened.deserializeNBT(Platform.getFrozenRegistry(), project.serializeNBT(Platform.getFrozenRegistry()));
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), reopened.graph);
                    c.check("保存保留输入参数和节点", graph.model().compile().parameters.size() == 2
                            && graph.model().compile().nodes.size() == AiExamples.action("melee").nodes.size());
                    var runtime = new AiProgram(); runtime.deserializeNBT(Platform.getFrozenRegistry(), project.serializeRuntimeFile(Platform.getFrozenRegistry()));
                    c.check("可导出副本", runtime.parameters.size() == 2);
                    c.check("内置定义保持原样", AiExamples.action("melee").displayNameKey.equals("viscript_npc.ai.action.melee"));
                }).step("适应副本画布", c -> activate(c, "#npc_ai_graph_action #npc_ai_fit")).ticks(2)
                .screenshot("builtin_melee_independent_copy").closeScreen();
    }

    private static AiGraphView view(TestContext c) {
        return (AiGraphView) window(c).getCurrentEditor().select("#npc_ai_graph_action").findFirst()
                .or(() -> window(c).getCurrentEditor().select("#npc_ai_template_original_graph").findFirst()).orElseThrow();
    }

    private static void activate(TestContext c, String selector) {
        var target = window(c).getCurrentEditor().select(selector).findFirst().orElseThrow();
        var bounds = ElementBounds.of(target);
        // 打开标签会立即移走原按钮；同一步完成按下和释放，避免再次解析已离开的旧画布。
        c.input().mouseDown(bounds.centerX(), bounds.centerY(), com.lowdragmc.lowdraglib2.uitest.input.Keys.MOUSE_LEFT);
        c.input().mouseUp(bounds.centerX(), bounds.centerY(), com.lowdragmc.lowdraglib2.uitest.input.Keys.MOUSE_LEFT);
    }

    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }
}
