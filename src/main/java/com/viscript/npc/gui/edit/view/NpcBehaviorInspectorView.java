package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.configurator.IConfigurable;
import com.lowdragmc.lowdraglib2.configurator.ui.*;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.IFieldValueConfigurable;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ModelState;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeModel;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorAbortType;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorCustomNodeModel;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.Supplier;

public class NpcBehaviorInspectorView extends NpcInspectorView {
    private final Supplier<NpcBehaviorGraphView> graphViewSupplier;
    @Nullable
    private NPCProject project;
    @Nullable
    private INpcEditorPage page;
    @Nullable
    private AbstractNodeModel inspectedNode;
    private boolean breakpointInspectorLocked;
    private int breakpointInspectorHash;

    public NpcBehaviorInspectorView(NpcEditor editor, Supplier<NpcBehaviorGraphView> graphViewSupplier) {
        super(editor);
        this.graphViewSupplier = graphViewSupplier;
        setName("viscript_npc.editor.view.ai_inspector");
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
        this.project = project;
        this.page = page;
        graphViewSupplier.get().setSelectionListener((graphView, node) -> inspectSelectedNode(node));
    }

    @Override
    public void clear() {
        graphViewSupplier.get().setSelectionListener(null);
        inspectedNode = null;
        breakpointInspectorLocked = false;
        breakpointInspectorHash = 0;
        super.clear();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        refreshBreakpointInspector();
    }

    private void inspectSelectedNode(@Nullable AbstractNodeModel node) {
        if (breakpointInspectorLocked) {
            return;
        }
        if (node == inspectedNode && inspector.getInspectedConfigurable() != null) {
            return;
        }
        inspectedNode = node;
        if (node == null || !(node instanceof NpcBehaviorCustomNodeModel behaviorNode)) {
            if (project != null && page != null) {
                page.inspect((NpcEditor) editor, project, this);
            } else {
                clear();
            }
            return;
        }
        inspector.inspect(createNodeConfigurable(behaviorNode), configurable -> refreshGraph());
    }

    private void refreshBreakpointInspector() {
        int entityId = project == null ? -1 : ViScriptNpcClientUtil.findNearestNpcAiDebugTarget(project.getCurrentNpcType());
        CompoundTag payload = entityId < 0 ? new CompoundTag() : ViScriptNpcClientUtil.getNpcAiDebugSnapshot(entityId);
        CompoundTag snapshot = payload.getCompound("snapshot");
        if (!snapshot.isEmpty() && snapshot.getBoolean("breakpointPaused")) {
            int hash = snapshot.hashCode();
            if (!breakpointInspectorLocked || breakpointInspectorHash != hash) {
                breakpointInspectorLocked = true;
                breakpointInspectorHash = hash;
                inspectedNode = null;
                inspector.inspect(createBreakpointDebugConfigurable(snapshot));
            }
            return;
        }
        if (breakpointInspectorLocked) {
            breakpointInspectorLocked = false;
            breakpointInspectorHash = 0;
            inspectSelectedNode(graphViewSupplier.get().getSelectedNode());
        }
    }

    private IConfigurable createBreakpointDebugConfigurable(CompoundTag snapshot) {
        return IConfigurable.create(group -> {
            ConfiguratorGroup state = new ConfiguratorGroup("viscript_npc.editor.ai_debug.breakpoint_inspector", false);
            state.addConfigurator(readOnlyConfigurator("viscript_npc.editor.ai_debug.debug_target",
                    Component.literal(emptyToDash(snapshot.getString("gameObjectName")))));
            state.addConfigurator(readOnlyConfigurator("viscript_npc.editor.ai_debug.debug_node",
                    Component.literal(shortId(snapshot.getString("breakpointNode")))));
            state.addConfigurator(readOnlyConfigurator("viscript_npc.editor.ai_debug.debug_time",
                    Component.literal(String.valueOf(snapshot.getLong("gameTime")))));
            group.addConfigurator(state);

            ConfiguratorGroup blackboard = new ConfiguratorGroup("viscript_npc.editor.ai_debug.blackboard", false);
            ListTag lines = snapshot.getList("blackboardLines", Tag.TAG_STRING);
            if (lines.isEmpty()) {
                blackboard.addConfigurator(readOnlyConfigurator("", Component.translatable("viscript_npc.editor.ai_debug.blackboard_empty")));
            } else {
                for (int i = 0; i < lines.size(); i++) {
                    blackboard.addConfigurator(readOnlyConfigurator("", Component.literal(lines.getString(i))));
                }
            }
            group.addConfigurator(blackboard);
        });
    }

    private Configurator readOnlyConfigurator(String labelKey, Component value) {
        Configurator configurator = new Configurator(labelKey == null ? "" : labelKey);
        Label label = new Label();
        label.setText(value);
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
        });
        configurator.addInlineChild(label);
        return configurator;
    }

    private IConfigurable createNodeConfigurable(NpcBehaviorCustomNodeModel node) {
        return IConfigurable.create(group -> {
            ConfiguratorGroup common = new ConfiguratorGroup("viscript_npc.ai.inspector.common", false);
            common.addConfigurator(new StringConfigurator(
                    "viscript_npc.ai.inspector.name",
                    () -> emptyToString(node.getName()),
                    value -> {
                        node.setName(value == null ? "" : value);
                        refreshGraph();
                    },
                    "",
                    true));
            common.addConfigurator(new TextAreaConfigurator(
                    "viscript_npc.ai.inspector.comment",
                    () -> toLines(node.getComment()),
                    value -> {
                        node.setComment(String.join("\n", value == null ? new String[0] : value));
                        refreshGraph();
                    },
                    new String[]{""},
                    true));
            common.addConfigurator(new BooleanConfigurator(
                    "viscript_npc.ai.inspector.disabled",
                    () -> node.getState() == ModelState.DISABLED,
                    value -> {
                        node.setState(Boolean.TRUE.equals(value) ? ModelState.DISABLED : ModelState.ENABLED);
                        refreshGraph();
                    },
                    false,
                    true));
            common.addConfigurator(new BooleanConfigurator(
                    "viscript_npc.ai.inspector.breakpoint",
                    node::isBreakpoint,
                    value -> {
                        node.setBreakpoint(Boolean.TRUE.equals(value));
                        refreshGraph();
                    },
                    false,
                    true));
            if (node.supportsAbortType()) {
                common.addConfigurator(new SelectorConfigurator<>(
                        "viscript_npc.ai.inspector.abort_type",
                        node::getAbortType,
                        value -> {
                            node.setAbortType(value);
                            refreshGraph();
                        },
                        NpcBehaviorAbortType.NONE,
                        true,
                        Arrays.asList(NpcBehaviorAbortType.values()),
                        value -> Component.translatable(value.getSerializedName()).getString()));
            }
            group.addConfigurator(common);

            if (node instanceof NodeModel nodeModel) {
                addNodeOptions(group, nodeModel);
            }
        });
    }

    private void addNodeOptions(ConfiguratorGroup group, NodeModel nodeModel) {
        ConfiguratorGroup options = new ConfiguratorGroup("viscript_npc.ai.inspector.parameters", false);
        for (var option : nodeModel.getNodeOptions()) {
            if (!(option.getPortModel() instanceof IFieldValueConfigurable configurable)) {
                continue;
            }
            ConfiguratorGroup temporary = new ConfiguratorGroup().hideTitle();
            configurable.buildConfigurator(temporary);
            var configurators = new ArrayList<>(temporary.getConfigurators());
            for (int i = 0; i < configurators.size(); i++) {
                Configurator configurator = configurators.get(i);
                temporary.removeConfigurator(configurator);
                configurator.setLabel(i == 0 ? option.getDisplayName() : Component.empty());
                options.addConfigurator(configurator);
            }
        }
        if (!options.getConfigurators().isEmpty()) {
            group.addConfigurator(options);
        }
    }

    private void refreshGraph() {
        NpcBehaviorGraphView graphView = graphViewSupplier.get();
        graphView.syncGraphToProject();
        if (graphView.graphView instanceof NpcBehaviorDebugGraphView debugGraphView) {
            debugGraphView.refreshGraphLogger();
        }
    }

    private static String[] toLines(String value) {
        return value == null || value.isEmpty() ? new String[]{""} : value.split("\n", -1);
    }

    private static String emptyToString(String value) {
        return value == null ? "" : value;
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? emptyToDash(id) : id.substring(0, 8);
    }

    private static String emptyToDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }
}
