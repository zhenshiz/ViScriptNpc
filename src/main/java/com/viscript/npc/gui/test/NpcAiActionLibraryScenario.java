package com.viscript.npc.gui.test;

import com.viscript.npc.npc.ai.test.NpcAiFixtures;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIContainerScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.SearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextField;
import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegisterClient;
import com.lowdragmc.lowdraglib2.uitest.*;
import com.lowdragmc.lowdraglib2.uitest.input.Keys;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.ai.*;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.npc.ai.data.AiProgram;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.NpcEditorFormats;
import com.viscript.npc.util.ViScriptNpcServerUtil;
import com.viscript.npc.util.NpcEditorFiles;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** 验证行为名称、文件引用、搜索以及工程和运行文件中的名称持久化。 */
@LDLRegisterClient(name = "npc_ai_action_library", group = ViScriptNpc.MOD_ID,
        registry = UIScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiActionLibraryScenario implements UIScenario {
    private static final String NAMED = "__ldlib2_tests__/action_library_named";
    private static final String PLAIN = "__ldlib2_tests__/action_library_plain";
    private static final String MISSING = "__ldlib2_tests__/action_library_missing";
    private static final String DRAFT = "__ldlib2_tests__/action_library_draft_only";
    private static final String KEY = "viscript_npc.ai.action.ranged";

    @Override public void configure(ScenarioOptions options) {
        options.requiresWorld(true).guiScale(2).tags("ui", "ai");
    }

    @Override public void define(ScenarioBuilder s) {
        Map<Path, byte[]> originals = new LinkedHashMap<>();
        s.server("打开空白 NPC 工程", server -> ViScriptNpcServerUtil.openNpcEditor(server.player(),
                        new NPCProject().serializeNBT(Platform.getFrozenRegistry())))
                .awaitScreen(ModularUIContainerScreen.class).awaitModularUI()
                .click("#npc_editor_page_tab_ai").awaitElement("#npc_ai_graph_tree")
                .server("准备服务端行为功能文件和仅本地工程", c -> {
                    for (String id : List.of(NAMED, PLAIN, MISSING)) {
                        var path = NpcEditorFiles.resolveRuntimeFile(NpcEditorFormats.NPC_ACTION, id, true);
                        try {
                            originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                            Files.createDirectories(path.getParent());
                            var project = new NpcActionProject();
                            project.program = (id.equals(PLAIN) ? NpcAiFixtures.movementAction() : NpcAiFixtures.action("wait")).setDisplayNameKey(id.equals(NAMED)
                                    ? "viscript_npc.ai.action.melee" : id.equals(PLAIN) ? "" : "missing.action.name");
                            net.minecraft.nbt.NbtIo.writeCompressed(project.serializeRuntimeFile(Platform.getFrozenRegistry()), path);
                        } catch (Exception exception) { throw new IllegalStateException(exception); }
                    }
                    for (String id : List.of(DRAFT, NAMED)) {
                        var path = NpcEditorFiles.resolveProjectFile(NpcEditorFormats.NPC_ACTION, id, true);
                        try {
                            originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                            Files.createDirectories(path.getParent());
                            var shadow = new NpcActionProject();
                            shadow.program = AiExamples.action("ranged");
                            shadow.getProjectType().saveProjectToFile(shadow, path.toFile());
                        } catch (Exception exception) { throw new IllegalStateException(exception); }
                    }
                    NpcActionFiles.invalidate();
                    var provider = Platform.getFrozenRegistry();
                    var named = NpcActionFiles.load(NAMED);
                    c.check("行为副本保留名称键", named.copy(provider).displayNameKey.equals(named.displayNameKey));
                })
                .click("#npc_ai_graph_tree #npc_ai_library").awaitElement(selector(NAMED))
                .check("行为库排除仅有工程文件的行为", c -> c.count(selector(DRAFT)) == 0)
                .check("服务端定义和参数已通过 RPC 到达客户端", c -> AiServerActions.ids().containsAll(List.of(NAMED, PLAIN, MISSING))
                        && AiServerActions.load(PLAIN).parameters.size() == 2)
                .check("白色按钮上的节点图标为黑色", c -> c.query(".npc_ai_action_library_row > .__icon__").list()
                        .stream().allMatch(ref -> ref.element().getStyle().color() == 0xff000000))
                .check("自定义行为可使用翻译键", c -> row(c, NAMED).text.getText().getString().equals(
                        Component.translatable("viscript_npc.ai.action.melee").getString()))
                .check("未命名和缺少翻译均回退到文件路径", c -> row(c, PLAIN).text.getText().getString().equals(PLAIN)
                        && row(c, MISSING).text.getText().getString().equals(MISSING))
                .screenshot("localized_action_library");

        search(s, Component.translatable("viscript_npc.ai.action.melee").getString())
                .check("中文名称可以过滤行为库", c -> row(c, NAMED).isDisplayed()
                        && !row(c, PLAIN).isDisplayed())
                .screenshot("search_action_by_name");
        search(s, NAMED)
                .check("原有文件 ID 仍可搜索", c -> row(c, NAMED).isDisplayed()
                        && !row(c, PLAIN).isDisplayed())
                .click(selector(NAMED)).ticks(2)
                .check("点击译名仍创建原有文件引用", c -> tree(c).model().getNodeModels().stream()
                        .anyMatch(node -> node instanceof AiNodeModel ai && ai.operation().equals("call")
                                && ai.data.literal("file", AiValue.text("")).text.equals(NAMED)))
                .step("打开调用节点的行为文件搜索", c -> {
                    var node = tree(c).model().getNodeModels().stream()
                            .filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("call"))
                            .map(n -> (AiNodeModel) n).findFirst().orElseThrow();
                    tree(c).inspectNode(node);
                }).ticks(2)
                .check("文件选择器当前值显示译名", c -> c.query("#npc_ai_resource_file *")
                        .type(TextElement.class).withText(Component.translatable("viscript_npc.ai.action.melee").getString()).count() > 0)
                .step("文件选择器搜索译名返回稳定 ID", c -> {
                    @SuppressWarnings("unchecked") var picker = (SearchComponent<String>) c.el("#npc_ai_resource_file").element();
                    List<String> results = new ArrayList<>();
                    picker.getSearchUI().search(Component.translatable("viscript_npc.ai.action.ranged").getString(), results::add);
                    c.check("中文搜索定位远程攻击的文件 ID", results.contains("examples/ranged") && !results.contains("examples/patrol"));
                })
                .step("打开自定义行为草稿", c -> {
                    c.put("tree_editor", window(c).getCurrentEditor());
                    var project = new NpcActionProject();
                    project.program = AiServerActions.load(NAMED).copy(Platform.getFrozenRegistry());
                    project.sourceActionId = NAMED;
                    window(c).createNewEditor(NpcEditor::new).loadProject(project, null);
                }).awaitElement("#npc_ai_graph_action")
                .check("节点初始布局不显示 Label 占位文本", c -> c.query("#npc_ai_graph_action")
                        .type(Label.class).withText("Label").visible().count() == 0)
                .click("#npc_ai_action_info").awaitElement("#npc_ai_action_display_name_key")
                .step("清空原有翻译键", c -> c.el("#npc_ai_action_display_name_key").as(TextField.class).setText("", true))
                .typeInto("#npc_ai_action_display_name_key", KEY).key(Keys.ENTER).ticks(2)
                .check("输入名称翻译键时不生成无用 Label", c -> c.query("#npc_ai_graph_action")
                        .type(Label.class).withText("Label").visible().count() == 0)
                .check("行为信息面板可以设置名称键", c -> action(c).model().compile().displayNameKey.equals(KEY))
                .click("#npc_ai_graph_action #npc_ai_undo").ticks(2)
                .check("名称修改支持撤销", c -> !action(c).model().compile().displayNameKey.equals(KEY))
                .click("#npc_ai_graph_action #npc_ai_redo").ticks(2)
                .check("名称修改支持重做", c -> action(c).model().compile().displayNameKey.equals(KEY))
                .screenshot("action_display_name_setting")
                .step("保存和导出保留名称元数据", c -> {
                    var provider = Platform.getFrozenRegistry();
                    var project = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                    var reopened = new NpcActionProject(); reopened.deserializeProject(provider, project.serializeProject(provider));
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(provider, reopened.graph);
                    c.check("工程保存重开保留名称键", reopened.program.displayNameKey.equals(KEY)
                            && graph.model().compile().displayNameKey.equals(KEY));
                    var exported = new AiProgram(); exported.deserializeNBT(provider, project.serializeRuntimeFile(provider));
                    c.check("运行文件导出保留名称键", exported.displayNameKey.equals(KEY));
                }).click("#npc_ai_graph_action #npc_ai_library").awaitElement(selector(PLAIN))
                .step("筛选可复用子行为", c -> c.el("#npc_ai_action_library_search").as(TextField.class).setText(PLAIN, true))
                .click(selector(PLAIN)).ticks(2)
                .check("行为内部插入调用节点和专属参数端口", c -> action(c).model().getNodeModels().stream()
                        .filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("call")).map(n -> (AiNodeModel)n)
                        .anyMatch(n -> n.data.literal("file", AiValue.text("")).text.equals(PLAIN)
                                && NpcAiFixtures.movementAction().parameters.stream().allMatch(v -> n.getInputsById().containsKey("value_param:" + v.id))))
                .step("连接子行为并打开参数面板", c -> {
                    var view = action(c);
                    var call = view.model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("call"))
                            .map(n -> (AiNodeModel)n).findFirst().orElseThrow();
                    view.edit("graph", () -> {
                        view.model().deleteNodes(view.model().getNodeModels().stream()
                                .filter(n -> n instanceof AiNodeModel ai && !ai.operation().equals("root") && n != call).toList(), true, true);
                        var root = view.model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("root"))
                                .map(n -> (AiNodeModel)n).findFirst().orElseThrow();
                        view.model().createWire(call.getInputsById().get("parent"), root.getOutputsById().get("children"), null);
                    });
                    c.put("nested_call", call.getUid()); view.inspectNode(call); view.fitGraphChildren();
                }).ticks(2)
                .click("#npc_ai_source_param_" + NpcAiFixtures.movementAction().parameters.getFirst().id).ticks(1)
                .step("选择覆盖子行为参数", c -> {
                    var bounds = c.query().type(com.lowdragmc.lowdraglib2.gui.ui.elements.Label.class)
                            .withText(Component.translatable("viscript_npc.ai.parameter_override").getString()).visible()
                            .where(e -> e.getChildren().stream().anyMatch(child -> child.getId().equals("selector#overlayButton"))).one().bounds();
                    c.input().mouseDown(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                    c.input().mouseUp(bounds.centerX(), bounds.centerY(), Keys.MOUSE_LEFT);
                }).ticks(2)
                .step("清空子行为速度", c -> c.el("#npc_ai_value_param_" + NpcAiFixtures.movementAction().parameters.getFirst().id)
                        .as(TextField.class).setText("", false))
                .typeInto("#npc_ai_value_param_" + NpcAiFixtures.movementAction().parameters.getFirst().id, "1.75").key(Keys.ENTER).ticks(2)
                .step("导出收集子行为且保存调用参数", c -> {
                    var project = (NpcActionProject) window(c).getCurrentEditor().getCurrentProject();
                    var tag = project.serializeRuntimeFile(Platform.getFrozenRegistry());
                    var dependencies = com.viscript.npc.util.NpcAiPublication.readDependencies(tag.getCompound(com.viscript.npc.util.NpcAiPublication.DEPENDENCIES));
                    var compiled = new AiProgram(); compiled.deserializeNBT(Platform.getFrozenRegistry(), tag);
                    c.check("运行文件包含子行为依赖", dependencies.containsKey(PLAIN));
                    c.check("调用覆盖参数正确保存", compiled.nodes.stream().filter(n -> n.type.equals("call")).findFirst().orElseThrow()
                            .inputs.get("param:" + NpcAiFixtures.movementAction().parameters.getFirst().id).value.number == 1.75);
                    var reopened = new NpcActionProject(); reopened.deserializeProject(Platform.getFrozenRegistry(), project.serializeProject(Platform.getFrozenRegistry()));
                    var graph = new AiGraph("action"); graph.model().deserializeNBT(Platform.getFrozenRegistry(), reopened.graph);
                    c.check("保存重开保留调用及参数", com.viscript.npc.npc.ai.runtime.AiValidation.validate(graph.model().compile(), NpcActionFiles::loadLocal).isEmpty());
                }).click("#npc_ai_graph_action #npc_ai_fit").ticks(2).screenshot("nested_action_call_parameters")
                .step("自引用在原生图日志中报错且不崩溃", c -> {
                    var view = action(c);
                    var call = (AiNodeModel)view.model().getModel(c.get("nested_call"));
                    view.edit("graph", () -> { call.data.inputs.clear(); call.data.input("file", AiValue.text(NAMED)); });
                    var issues = com.viscript.npc.npc.ai.runtime.AiValidation.validate(view.model().compile(), view.actionResolver);
                    c.check("循环定位当前调用节点", issues.stream().anyMatch(i -> i.key().equals("cycle") && i.nodeId().equals(call.getUid().toString())));
                    view.getHistoryStack().undo();
                    window(c).showEditor(c.get("tree_editor"));
                }).awaitElement("#npc_ai_graph_tree")
                .click("#npc_ai_graph_tree #npc_ai_library").awaitElement(selector(NAMED))
                .check("服务端行为库名称不被本地草稿覆盖", c -> row(c, NAMED).text.getText().getString().equals(
                        Component.translatable("viscript_npc.ai.action.melee").getString()))
                .step("记录拖拽前的节点数量", c -> c.put("before_drag", tree(c).model().getNodeModels().size()))
                .drag(selector(PLAIN), "#npc_ai_graph_tree").ticks(2)
                .check("服务端行为拖入后只生成一个调用节点", c -> tree(c).model().getNodeModels().size() == c.<Integer>get("before_drag") + 1
                        && tree(c).model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel ai && ai.operation().equals("call"))
                        .map(n -> (AiNodeModel)n).anyMatch(n -> n.data.literal("file", AiValue.text("")).text.equals(PLAIN)
                                && n.getInputsById().containsKey("value_param:" + NpcAiFixtures.movementAction().parameters.getFirst().id)))
                .screenshot("server_action_dragged_into_blueprint")
                .closeScreen()
                .teardown("恢复行为测试文件", c -> {
                    originals.forEach((path, bytes) -> {
                        try {
                            if (bytes == null) Files.deleteIfExists(path); else Files.write(path, bytes);
                        } catch (Exception exception) { throw new IllegalStateException(exception); }
                    });
                    NpcActionFiles.invalidate();
                });
    }

    private static String selector(String id) {
        return "#npc_ai_action_library_" + id.replace('/', '_').replace(':', '_');
    }

    private static Button row(TestContext c, String id) { return c.el(selector(id)).as(Button.class); }
    private static AiGraphView tree(TestContext c) { return c.el("#npc_ai_graph_tree").as(AiGraphView.class); }
    private static AiGraphView action(TestContext c) { return c.el("#npc_ai_graph_action").as(AiGraphView.class); }
    private static EditorWindow window(TestContext c) {
        return (EditorWindow) ((ModularUIContainerScreen) c.mc().screen).getMenu().getModularUI().ui.rootElement;
    }

    private static ScenarioBuilder search(ScenarioBuilder s, String word) {
        return s.step("清空行为搜索", c -> c.el("#npc_ai_action_library_search").as(TextField.class).setText("", true))
                .typeInto("#npc_ai_action_library_search", word).ticks(2);
    }
}
