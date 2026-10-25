package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TreeList;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.utils.HistoryStack;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.command.*;
import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.Graph;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.NodeModelLibraryItem;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.ItemLibraryItem;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.PortLibraryItem;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.NodeAttribute;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.gui.util.TreeBuilder;
import com.lowdragmc.lowdraglib2.gui.util.TreeNode;
import com.viscript.npc.util.NpcActionFiles;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import com.lowdragmc.lowdraglib2.configurator.IConfigurableHistory;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.StringConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.SelectorConfigurator;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableDeclarationModelBase;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableCreationInfos;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.VariableScope;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.VariableNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.util.RenameColorConfigurableHelper;
import java.util.*;
import java.util.function.Consumer;

/** 树编辑规则在同一条撤销命令内完成，包含子树移动与显式执行顺序。 */
public final class AiGraphView extends GraphView {
    private static final List<String> ACTION_CATEGORIES = List.of(
            "entity", "movement", "combat", "presentation", "timing", "data", "other");
    public final AiParametersView parametersView = new AiParametersView();
    public final UIElement debugToolbar = new UIElement().layout(l -> l
            .flexDirection(dev.vfyjxf.taffy.style.FlexDirection.ROW).gapAll(2).flex(1)
            .justifyContent(dev.vfyjxf.taffy.style.AlignContent.FLEX_END));
    public java.util.function.Function<String, com.viscript.npc.npc.ai.data.AiProgram> actionResolver = NpcActionFiles::loadLocal;
    private AiNodeModel inspectedNode;
    private VariableDeclarationModelBase inspectedVariable;
    private GraphPanel appearancePanel;
    private GraphPanel blackboardPanel;
    private UIElement libraryList;
    public boolean loadingActionDefinitions;
    private String pendingDebugNode = "";

    public AiGraphView() {
        header.layout(l -> l.height(24).paddingAll(2).gapAll(2));
        var libraryTrees = List.of(itemLibrary.nodeTree, itemLibrary.constantTree, itemLibrary.contextTree,
                itemLibrary.recommendationTree, itemLibrary.searchTree);
        // 执行流每个方向只有一个端口，直接选节点并沿用原生自动连接，不再展示空白端口子项。
        var rowTemplate = TreeList.<TreeNode<ItemLibraryItem, Void>>iconTextTemplate(
                node -> node.getKey().getIcon(), node -> node.getKey().getDisplayName());
        libraryTrees.forEach(tree -> tree.setNodeUISupplier(node -> {
            var row = rowTemplate.apply(node);
            row.addEventListener(UIEvents.ADDED, event -> omitLibraryPorts(tree, node));
            return row;
        }));
        graphView.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_WHEEL, event -> {
            // 保留 LDLib2 的鼠标锚点计算，按比例限制每格缩放幅度。
            if (event.target == graphView) event.deltaY = graphView.getScale()
                    * ((float) Math.pow(1.06, net.minecraft.util.Mth.clamp(event.deltaY, -1f, 1f)) - 1f) / 0.1f;
        }, true);
        header.addChild(debugToolbar);
        debugToolbar.addChildren(runPauseButton(),
                icon("step", () -> debugCommand("ai_step")),
                icon("step_tick", () -> debugCommand("ai_step_tick")),
                icon("debug", () -> inspectBehavior(IConfigurable.create(group -> AiDebugPanel.build(this, group)))));
    }

    private Button runPauseButton() {
        var button = icon("run", () -> debugCommand("ai_toggle"));
        button.setId("npc_ai_run_pause");
        var runIcon = AiIcons.of("run");
        var pauseIcon = AiIcons.of("pause");
        button.getChildren().getLast().style(style -> style.backgroundTexture(
                com.lowdragmc.lowdraglib2.gui.texture.DynamicTexture.of(() -> isAiRunning() ? pauseIcon : runIcon)));
        button.addEventListener(UIEvents.HOVER_TOOLTIPS, event -> event.hoverTooltips =
                new com.lowdragmc.lowdraglib2.gui.ui.event.HoverTooltips(List.of(Component.translatable(
                        "viscript_npc.ai.tip." + (isAiRunning() ? "pause" : "run"))), null, null, null));
        return button;
    }

    private static boolean isAiRunning() {
        return AiClientDebug.latest.enabled && !AiClientDebug.latest.paused;
    }

    private static void omitLibraryPorts(TreeList<TreeNode<ItemLibraryItem, Void>> tree,
                                         TreeNode<ItemLibraryItem, Void> node) {
        var ports = node.getChildren().stream().filter(child -> child.getKey() instanceof PortLibraryItem port
                && port.getPort().getDataTypeHandle().equals(TypeHandles.EXECUTION_FLOW)).toList();
        if (ports.isEmpty()) return;
        tree.collapseNode(node);
        ports.forEach(node::removeChild);
    }

    public void configureForKind(String kind) {
        blackboard.setLastVariableInfos(new VariableCreationInfos()
                .setTypeHandle(AiVariables.TYPES.getFirst())
                .setScope(VariableScope.LOCAL));
        if (kind.equals("action")) header.addChildAt(icon("action_info", this::showActionInfo),
                header.getChildren().indexOf(debugToolbar));
    }

    /**
     * 在节点图工具栏前端添加明确的 AI 提交入口。
     *
     * @param save 点击后提交完整草稿的操作
     */
    public void addSaveButton(Runnable save) {
        header.addChildAt(icon("save", save), 0);
    }

    public void inspectNode(AiNodeModel node) {
        inspectedNode = node;
        inspectBehavior(IConfigurable.create(group -> AiInspector.build(this, node, group)));
        inspectedNode = node;
    }

    public void inspectBehavior(IConfigurable configurable) {
        libraryList = null;
        inspectedNode = null;
        inspectedVariable = null;
        parametersView.inspect(configurable);
    }

    @Override public void endBatchSelection() {
        super.endBatchSelection();
        if (getGraph() == null || getSelected().size() != 1) return;
        var selected = getSelected().iterator().next();
        if (selected instanceof AiNodeModel node) {
            // 节点外观由原生 NodeElement 检查器处理，此处只更新右侧行为参数。
            inspectNode(node);
        } else if (selected instanceof VariableDeclarationModelBase variable) {
            inspector.clear(); inspectVariable(variable);
        } else if (selected instanceof VariableNodeModel variableNode) {
            inspector.inspect(RenameColorConfigurableHelper.build(variableNode, this));
            if (variableNode.getVariableDeclarationModel() != null) inspectVariable(variableNode.getVariableDeclarationModel());
        }
    }

    private void inspectVariable(VariableDeclarationModelBase variable) {
        inspectedNode = null;
        // 快照恢复会替换声明对象；重新绑定同一变量时不记录查看操作，避免清空重做栈。
        boolean rebinding = inspectedVariable != null && inspectedVariable.getUid().equals(variable.getUid());
        parametersView.inspector.setHistoryStack(rebinding ? null : historyStack);
        parametersView.inspector.inspect(new IConfigurable() {
            @Override public IConfigurableHistory createHistoryRecorder() {
                return IConfigurableHistory.ofSerializable(model());
            }
            @Override public void buildConfigurator(ConfiguratorGroup group) {
                group.addConfigurator(new StringConfigurator("graph.variable_name", variable::getName,
                        value -> { variable.setName(value); model().refreshVariableTips(variable); }, variable.getName(), false));
                var displayName = new StringConfigurator("viscript_npc.ai.variable.display_name_key",
                        () -> AiVariables.compile(variable).displayNameKey, value -> {
                            model().variablePresentation.computeIfAbsent(variable.getUid().toString(), ignored -> new AiVariablePresentation())
                                    .displayNameKey = value.trim();
                            model().refreshVariableTips(variable);
                        }, "", false);
                displayName.textField.setId("npc_ai_variable_display_name_key");
                displayName.setTips("viscript_npc.ai.tip.variable_display_name_key");
                group.addConfigurator(displayName);
                var tips = new StringConfigurator("viscript_npc.ai.variable.tips_key",
                        () -> AiVariables.compile(variable).tipsKey, value -> {
                            model().variablePresentation.computeIfAbsent(variable.getUid().toString(), ignored -> new AiVariablePresentation())
                                    .tipsKey = value.trim();
                            model().refreshVariableTips(variable);
                        }, "", false);
                tips.textField.setId("npc_ai_variable_tips_key");
                tips.setTips("viscript_npc.ai.tip.variable_tips_key");
                group.addConfigurator(tips);
                var defaults = new ConfiguratorGroup("graph.default_value", false);
                variable.buildConfigurator(defaults);
                group.addConfigurator(defaults);
                if (model().metadata.kind.equals("action")) {
                    var scope = new SelectorConfigurator<>("viscript_npc.ai.variable_scope",
                            () -> AiVariables.scope(variable, "action"), value -> AiVariables.setScope(variable, value),
                            "local", false, List.of("local", "parameter", "blackboard"),
                            value -> Component.translatable("viscript_npc.ai.variable_scope." + value).getString());
                    scope.selector.setId("npc_ai_variable_scope");
                    group.addConfigurator(scope);
                }
            }
        }, changed -> refreshGraphLogger(), null, this::rebuildGraphUI);
        parametersView.inspector.setHistoryStack(historyStack);
        inspectedVariable = variable;
    }

    public Set<String> activeNodes = Set.of();
    public java.util.function.Function<String, AiGraphView> actionOpener = ignored -> null;
    public Consumer<String> exampleOpener = id -> {};
    private final Map<UUID, UIElement> stageFrames = new HashMap<>();
    private record ActionDrag(String file) {}

    @Override protected void onGraphViewDragPerform(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvent event) {
        if (event.dragHandler.getDraggingObject() instanceof ActionDrag drag) {
            var position = getContentViewContainer().worldToLocalLayoutOffset(new Vector2f(event.x, event.y));
            addAction(drag.file, position); event.stopPropagation();
        } else super.onGraphViewDragPerform(event);
    }

    private void addAction(String id, Vector2f position) {
        edit("add", () -> {
            var node = model().add("call", position); node.data.input("file", AiValue.text(id));
            var action = actionResolver.apply(id);
            if (action != null) for (var variable : action.npcVariables) {
                model().addVariable(variable, "blackboard");
            }
        });
    }

    @Override protected void initHeaders() {
        header.addChildren(icon("undo", () -> historyStack.undo()),
                icon("redo", () -> historyStack.redo()),
                icon("arrange", () -> edit("arrange", () -> model().arrange())),
                icon("fit", this::fitGraphChildren),
                icon("variables", () -> blackboardPanel.setCollapsed(!blackboardPanel.isCollapsed())),
                icon("library", this::showLibrary),
                icon("examples", this::showExamples));
    }

    @Override protected void initPanels() {
        blackboardPanel = new GraphPanel(this, blackboard);
        blackboardPanel.setId("npc_ai_blackboard_panel");
        blackboard.setId("npc_ai_blackboard");
        blackboardPanel.layout(l -> l.width(190).height(240));
        blackboardPanel.setCollapsed(true);
        getPanelLayer().addChild(blackboardPanel);
        dockManager.register(blackboardPanel, DockSlot.TOP_LEFT);
        var panel = new GraphPanel(this, inspector);
        appearancePanel = panel;
        panel.setId("npc_ai_appearance");
        inspector.setId("npc_ai_appearance_inspector");
        panel.layout(l -> l.width(150).height(180));
        panel.setCollapsed(true);
        getPanelLayer().addChild(panel);
        dockManager.register(panel, DockSlot.TOP_RIGHT);
    }

    public static Button icon(String key, Runnable click) {
        var button = new Button().noText().setOnClick(event -> click.run());
        button.setId("npc_ai_" + key);
        button.style(s -> s.tooltips("viscript_npc.ai.tip." + key));
        AiIcons.decorate(button, key);
        return button;
    }

    public AiGraphModel model() { return ((AiGraph) getGraph()).model(); }

    @Override public AiGraphView loadGraph(Graph graph) {
        super.loadGraph(graph);
        if (graph == null) return this;
        // 功能子组用于组织节点目录，不改变节点的注册标识和运行时分组。
        var builder = TreeBuilder.<ItemLibraryItem, Void>start(itemLibrary.nodeTree.getRoot().getKey());
        Map<String, ItemLibraryItem> groups = new LinkedHashMap<>();
        for (var nodeType : graph.graphModel.getLibrarySupportNodes()) {
            var attribute = nodeType.getAnnotation(NodeAttribute.class);
            String operation = attribute.name().substring("viscript_npc:".length());
            var title = Component.translatable("viscript_npc.ai.node." + operation);
            var item = new NodeModelLibraryItem(attribute.name(),
                    creation -> ((AiGraphModel) creation.graphModel()).createLibraryNode(operation, creation));
            item.setDisplayName(title);
            item.setSearchableName(attribute.name() + " " + operation.replace('_', ' ') + " " + title.getString());
            var path = new ArrayList<ItemLibraryItem>();
            String category = libraryCategory(operation);
            path.add(groups.computeIfAbsent(category, AiGraphView::libraryGroup));
            if (category.equals("action")) {
                path.add(groups.computeIfAbsent("action." + actionCategory(operation), AiGraphView::libraryGroup));
            }
            builder.diveBranch(path, branch -> branch.leaf(item, null));
        }
        var root = builder.build();
        root.getChildren().sort(Comparator.comparingInt(child -> List.of("composite", "decorator", "condition", "action", "debug").indexOf(child.getKey().getPath())));
        root.getChildren().stream().filter(group -> group.getKey().getPath().equals("action")).forEach(group ->
                group.getChildren().sort(Comparator.comparingInt(child -> ACTION_CATEGORIES.indexOf(
                        child.getKey().getPath().substring("action.".length())))));
        itemLibrary.nodeTree.setRoot(root);
        itemLibrary.setId("npc_ai_node_library");
        itemLibrary.searchField.setId("npc_ai_node_search");
        itemLibrary.setPopupSize(210, 220);
        itemLibrary.setDescriptionWidth(220);
        return this;
    }

    private static ItemLibraryItem libraryGroup(String path) {
        var title = Component.translatable("viscript_npc.ai.category." + path);
        return new ItemLibraryItem().setPath(path).setIcon(Icons.FOLDER)
                .setDisplayName(title).setSearchableName(path + " " + title.getString());
    }

    private static String libraryCategory(String operation) {
        if (AiOperations.get(operation).group().equals("debug")) return "debug";
        if (Set.of("root", "sequence", "selector", "reactive_sequence", "reactive_selector", "parallel").contains(operation)) return "composite";
        if (Set.of("invert", "repeat", "until_success", "until_failure", "force_success", "force_failure", "timeout", "cooldown", "sense_targets").contains(operation)) return "decorator";
        if (AiOperations.get(operation).group().equals("condition")) return "condition";
        return "action";
    }

    private static String actionCategory(String operation) {
        return switch (operation) {
            case "find_target", "set_target", "clear_target", "get_entity", "entity_info" -> "entity";
            case "patrol", "position", "navigate", "approach", "stop", "look", "jump", "wait_ground" -> "movement";
            case "melee", "ranged", "area_damage", "knockback", "swing", "effect" -> "combat";
            case "particle", "sound" -> "presentation";
            case "stage", "branch", "wait" -> "timing";
            case "set_variable", "success", "failure", "finish_success", "finish_failure", "call" -> "data";
            default -> "other";
        };
    }

    @Override protected UIElement createGraphLogRow(com.lowdragmc.lowdraglib2.nodegraphtookit.api.graph.GraphLogger.Entry entry) {
        var row = super.createGraphLogRow(entry);
        if (entry.context() instanceof AiNodeModel node) {
            row.style(style -> style.tooltips(entry.message()));
            row.addEventListener(com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents.MOUSE_DOWN, event -> {
                clearAllSelected(); addSelected(node);
                var position = node.getPosition();
                fitGraph(position.x - 120, position.y - 80, position.x + 300, position.y + 220, 20);
                inspectNode(node);
                event.stopPropagation();
            });
        }
        return row;
    }

    private void selectDescendants() {
        var roots = List.copyOf(getSelected());
        batchSelection(() -> roots.forEach(n -> {
            if (n instanceof AiNodeModel a) model().descendants(a).forEach(this::addSelected);
        }));
    }
    @Override public void copySelectedElements() { selectDescendants(); super.copySelectedElements(); }
    @Override public void duplicateSelectedElements() { selectDescendants(); super.duplicateSelectedElements(); }
    @Override public void deleteSelectedElements() { selectDescendants(); super.deleteSelectedElements(); }

    @Override public void screenTick() {
        super.screenTick();
        AiClientDebug.poll();
        activeNodes = Set.copyOf(AiClientDebug.latest.activeNodes);
        if (getGraph() == null) return;
        if (!loadingActionDefinitions && !pendingDebugNode.isBlank()) {
            String id = pendingDebugNode; pendingDebugNode = "";
            var node = model().getNodeModels().stream().filter(n -> n != null && n.getUid().toString().equals(id)).findFirst().orElse(null);
            if (node != null) {
                clearAllSelected(); addSelected(node);
                var p = node.getPosition(); fitGraph(p.x - 100, p.y - 60, p.x + 260, p.y + 230, 16);
            }
        }
        if (appearancePanel != null && !appearancePanel.isCollapsed())
            appearancePanel.layout(l -> l.height(Math.max(60, Math.min(180, canvas.getSizeHeight() - 8))));
        if (inspectedNode != null) {
            var current = model().getModel(inspectedNode.getUid()) instanceof AiNodeModel node ? node : null;
            if (current == null) { inspectedNode = null; parametersView.inspector.clear(); }
            else if (current != inspectedNode) inspectNode(current);
        }
        if (inspectedVariable != null) {
            var current = model().getModel(inspectedVariable.getUid());
            if (!(current instanceof VariableDeclarationModelBase variable)) {
                inspectedVariable = null; parametersView.inspector.clear();
            } else if (variable != inspectedVariable) inspectVariable(variable);
        }
        Set<AiNodeModel> hidden = new HashSet<>();
        model().getNodeModels().forEach(n -> { if (n instanceof AiNodeModel a && a.isCollapsed()) hidden.addAll(model().descendants(a)); });
        for (var node : model().getNodeModels()) if (node instanceof AiNodeModel a) {
            var element = getModelElement(node);
            if (element != null) element.setDisplay(!hidden.contains(a));
            if (a.operation().equals("stage")) {
                var frame = stageFrames.computeIfAbsent(a.getUid(), ignored -> new UIElement().addClass("npc_ai_stage_frame"));
                frame.setAllowHitTest(false);
                if (!frame.hasParent()) getLayer("Placemat").addChild(frame);
                frame.setDisplay(!hidden.contains(a) && !a.isCollapsed());
                var all = model().descendants(a);
                float left = a.getPosition().x, top = a.getPosition().y;
                float right = a.getPosition().x + (element == null ? 146 : element.getSizeWidth());
                float bottom = a.getPosition().y + (element == null ? model().layoutHeight(a) : element.getSizeHeight());
                for (var child : all) if (!hidden.contains(child)) {
                    var childElement = getModelElement(child);
                    left = Math.min(left, child.getPosition().x);
                    top = Math.min(top, child.getPosition().y);
                    right = Math.max(right, child.getPosition().x + (childElement == null ? 146 : childElement.getSizeWidth()));
                    bottom = Math.max(bottom, child.getPosition().y + (childElement == null ? model().layoutHeight(child) : childElement.getSizeHeight()));
                }
                float frameLeft = left - 12, frameTop = top - 12;
                float width = right - frameLeft + 12, height = bottom - frameTop + 12;
                frame.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE)
                        .left(frameLeft).top(frameTop).width(width).height(height));
            }
        }
        stageFrames.entrySet().removeIf(entry -> {
            boolean removed = model().getModel(entry.getKey()) == null;
            if (removed) entry.getValue().removeSelf(); return removed;
        });
        for (var wire : model().getWireModels()) {
            if (wire == null) continue;
            var element = getModelElement(wire);
            if (element != null) element.setDisplay(wire.getFromPort() != null && wire.getToPort() != null
                    && !hidden.contains(wire.getFromPort().getNodeModel()) && !hidden.contains(wire.getToPort().getNodeModel())
                    && (!(wire.getToPort().getNodeModel() instanceof AiNodeModel ai)
                        || !wire.getToPort().getUniqueName().equals("value_entity_type") || model().usesEntityTypeFilter(ai)));
        }
    }

    public void edit(String key, Runnable mutation) {
        if (isReadOnly()) return;
        super.dispatchCommand(new UndoableGraphCommand() {
            @Override public Component getCommandName() { return Component.translatable("viscript_npc.ai.edit." + key); }
            @Override public void execute() {
                mutation.run();
                // 普通值编辑保留正在输入的原生控件；结构和绑定变化才重建端口。
                if (key.equals("parameter")) { refreshGraphLogger(); return; }
                model().getNodeModels().forEach(node -> { if (node instanceof AiNodeModel ai) ai.defineNode(); });
                view.rebuildGraphUI();
                if (inspectedNode != null && model().getModel(inspectedNode.getUid()) instanceof AiNodeModel node)
                    inspectNode(node);
            }
        });
    }

    @Override public boolean dispatchCommand(IGraphCommand command) {
        if (getGraph() == null || isReadOnly()) return false;
        if (command instanceof VariableDeclarationCommands.CreateGraphVariableDeclarationCommand
                || command instanceof VariableDeclarationCommands.CreateGroupCommand
                || command instanceof VariableDeclarationCommands.MoveGroupItemCommand
                || command instanceof VariableDeclarationCommands.ChangeVariableModifiersCommand)
            return super.dispatchCommand(command);
        // 组合规则仅约束 AI 控制流；变量声明和数据节点沿用 LDLib2。
        if (!(command instanceof GraphCommands.MoveElementsCommand
                || command instanceof GraphCommands.DeleteElementsCommand
                || command instanceof GraphCommands.PasteElementsCommand
                || command instanceof NodeCommands.CreateNodeCommand
                || command instanceof WireCommands.CreateWireCommand
                || command instanceof WireCommands.InsertReroutePointCommand
                || command instanceof WireCommands.ReSourceReroutePointCommand
                || command instanceof ElementRenameColorCommands.SetElementColorCommand
                || command instanceof ElementRenameColorCommands.ResetElementColorCommand
                || command instanceof ElementRenameColorCommands.RenameElementCommand
                || command.getClass().getSimpleName().contains("Collapse"))) return false;
        var before = model().serializeNBT(Platform.getFrozenRegistry());
        edit("graph", () -> {
            Map<UUID, Vector2f> positions = new HashMap<>();
            model().getNodeModels().stream().filter(Objects::nonNull)
                    .forEach(n -> positions.put(n.getUid(), new Vector2f(n.getPosition())));
            if (command instanceof IUndoableGraphCommand undoable) undoable.execute(this, model(), new HistoryStack());
            else command.execute(this, model());
            if (command instanceof GraphCommands.PasteElementsCommand) {
                Map<String, String> copied = new HashMap<>();
                for (var n : model().getNodeModels()) if (n instanceof AiNodeModel a && !positions.containsKey(a.getUid())) copied.put(a.data.id, a.getUid().toString());
                for (var n : model().getNodeModels()) if (n instanceof AiNodeModel a && !positions.containsKey(a.getUid())) {
                    a.data.children.replaceAll(id -> copied.getOrDefault(id, id));
                    a.data.onHit = copied.getOrDefault(a.data.onHit, a.data.onHit); a.data.id = a.getUid().toString();
                }
            }
            if (model().getNodeModels().stream().filter(n -> n instanceof AiNodeModel a && a.operation().equals("root")).count() > 1) {
                model().deserializeNBT(Platform.getFrozenRegistry(), before);
            }
        });
        return true;
    }

    /**
     * 打开或激活指定行为的编辑标签。
     *
     * @param id 行为文件的逻辑标识
     * @return 对应行为画布；标识为空或无法打开时返回 {@code null}
     */
    public AiGraphView openAction(String id) { return id.isBlank() ? null : actionOpener.apply(id); }

    public void debugCommand(String command) { AiClientDebug.command(command, AiClientDebug.selected()); }

    /**
     * 定位调试快照中的节点；调用行为位于其他工程时先打开其行为标签。
     *
     * @param id 运行节点的稳定标识
     * @param action 行为文件 ID；空字符串表示当前图
     */
    public void locateDebugNode(String id, String action) {
        var node = model().getNodeModels().stream().filter(n -> n != null && n.getUid().toString().equals(id)).findFirst().orElse(null);
        if (node != null) {
            // 行为定义同步会重建画布，原生首帧也会适应全图；两者完成后再选中并定位。
            pendingDebugNode = id;
        } else if (!action.isBlank()) {
            var destination = openAction(action);
            if (destination != null && destination != this) destination.locateDebugNode(id, "");
        }
    }

    public void toggleBreakpoint(AiNodeModel node) {
        String id = node.getUid().toString();
        edit("breakpoint", () -> { if (!model().breakpoints.remove(id)) model().breakpoints.add(id); });
        var payload = new net.minecraft.nbt.CompoundTag(); payload.putString("nodeId", id);
        payload.putBoolean("enabled", model().breakpoints.contains(id));
        AiClientDebug.command("ai_breakpoint", AiClientDebug.selected(), payload);
    }

    @Override protected TreeBuilder.Menu createMenu(float x, float y) {
        var menu = super.createMenu(x, y);
        if (!isReadOnly() && getSelected().size() == 1 && getSelected().iterator().next() instanceof AiNodeModel node) {
            menu.crossLine();
            menu.leaf("viscript_npc.ai.debug.toggle_breakpoint", () -> toggleBreakpoint(node));
            menu.leaf("viscript_npc.ai.debug.toggle_disabled", () -> edit("disabled", () -> node.data.disabled = !node.data.disabled));
        }
        return menu;
    }

    public void refreshActionDefinitions() {
        if (getGraph() == null) return;
        model().getNodeModels().forEach(node -> { if (node instanceof AiNodeModel ai) ai.defineNode(); });
        rebuildGraphUI();
        if (inspectedNode != null && model().getModel(inspectedNode.getUid()) instanceof AiNodeModel node)
            inspectNode(node);
    }

    private void showActionInfo() {
        inspectBehavior(IConfigurable.create(group -> {
            var name = new StringConfigurator("viscript_npc.ai.action.display_name_key",
                    () -> model().metadata.displayNameKey,
                    value -> edit("action_info", () -> model().metadata.setDisplayNameKey(value.trim())), "", false);
            name.textField.setId("npc_ai_action_display_name_key");
            name.setTips("viscript_npc.ai.action.display_name_key.help");
            group.addConfigurator(name);
        }));
    }

    public java.util.function.Consumer<String> treeExampleOpener = ignored -> {};

    public void showExamples() {
        if (model().metadata.kind.equals("tree")) {
            inspectBehavior(IConfigurable.create(group -> {
                for (String name : com.viscript.npc.npc.ai.runtime.AiExamples.TREE_NAMES) {
                    var row = new com.lowdragmc.lowdraglib2.configurator.ui.Configurator();
                    var button = new Button().setText(Component.translatable("viscript_npc.ai.tree_example." + name));
                    button.setId("npc_ai_tree_example_" + name);
                    button.layout(l -> l.widthPercent(100));
                    button.style(style -> style.tooltips("viscript_npc.ai.tip.tree_example"));
                    button.setOnClick(event -> treeExampleOpener.accept(name));
                    row.inlineContainer.addChild(button);
                    group.addConfigurator(row);
                }
            }));
            return;
        }
        inspectBehavior(IConfigurable.create(group -> {
            for (String name : com.viscript.npc.npc.ai.runtime.AiExamples.NAMES) {
                var row = new com.lowdragmc.lowdraglib2.configurator.ui.Configurator();
                var button = actionButton("examples/" + name,
                        Component.translatable("viscript_npc.ai.example." + name),
                        () -> exampleOpener.accept("examples/" + name));
                button.setId("npc_ai_example_" + name);
                button.style(s -> s.tooltips("viscript_npc.ai.tip.open_or_drag_example"));
                row.inlineContainer.addChild(button);
                group.addConfigurator(row);
            }
        }));
    }

    public void showLibrary() {
        inspectBehavior(IConfigurable.create(group -> {
            var container = new com.lowdragmc.lowdraglib2.configurator.ui.Configurator();
            var list = new UIElement().layout(l -> l.widthPercent(100).gapAll(2));
            list.setId("npc_ai_action_library");
            libraryList = list;
            Map<String, Button> rows = new LinkedHashMap<>();
            Map<String, String> searchNames = new LinkedHashMap<>();
            var filter = new com.lowdragmc.lowdraglib2.gui.ui.elements.TextField();
            filter.setId("npc_ai_action_library_search");
            filter.layout(l -> l.widthPercent(100));
            filter.setTextResponder(word -> {
                String query = word.toLowerCase(Locale.ROOT);
                rows.forEach((id, row) -> row.setDisplay(searchNames.get(id).contains(query)));
            });
            list.addChild(filter);
            var status = new com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement()
                    .setText(Component.translatable("viscript_npc.ai.library.loading"));
            status.setId("npc_ai_action_library_status");
            list.addChild(status);
            AiServerActions.request(() -> {
                refreshActionDefinitions();
                if (libraryList != list) return;
                status.setText(Component.translatable("viscript_npc.ai.library.empty"));
                status.setDisplay(AiServerActions.ids().isEmpty());
                for (String id : AiServerActions.ids()) {
                    var title = AiActionNames.displayName(id, AiServerActions.load(id));
                    searchNames.put(id, (id + " " + title.getString()).toLowerCase(Locale.ROOT));
                    var row = actionButton(id, title, () -> addAction(id, new Vector2f(0, 100)));
                    row.setId("npc_ai_action_library_" + id.replace('/', '_').replace(':', '_'));
                    row.style(s -> s.tooltips("viscript_npc.ai.tip.drag_action"));
                    row.setDisplay(searchNames.get(id).contains(filter.getText().toLowerCase(Locale.ROOT)));
                    rows.put(id, row);
                    list.addChild(row);
                }
            });
            container.inlineContainer.addChild(list); group.addConfigurator(container);
        }));
    }

    private Button actionButton(String id, Component title, Runnable click) {
        // LDLib2 的 Button 在按下时触发回调；这里等释放后再点击，避免开始拖拽时先插入或打开工程。
        var row = new Button().setText(title);
        row.addClass("npc_ai_action_library_row");
        row.addPreIcon(Icons.NODE);
        row.layout(l -> l.widthPercent(100).height(18));
        boolean[] pressed = {false};
        row.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            if (event.button == 0) pressed[0] = true;
        });
        row.addEventListener(UIEvents.MOUSE_UP, event -> {
            boolean activate = pressed[0] && event.button == 0;
            pressed[0] = false;
            if (activate) click.run();
        });
        row.addEventListener(UIEvents.MOUSE_LEAVE, event -> {
            if (pressed[0] && row.isMouseDown(0) && !row.isMouseOverElement(event.x, event.y)) {
                pressed[0] = false;
                row.startDrag(new ActionDrag(id), Icons.NODE);
            }
        }, true);
        return row;
    }
}
