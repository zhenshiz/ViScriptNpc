package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.AMIntentionProject;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.ai.editor.NpcAiDebugClientState;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

public class NpcAiDebugView extends View {
    @Nullable
    private final NPCProject project;
    @Nullable
    private final AMIntentionProject amProject;
    private final Label status = new Label();
    private int targetId = -1;
    private int ticks;

    public NpcAiDebugView(NPCProject project) {
        this(project, null);
    }

    public NpcAiDebugView(AMIntentionProject project) {
        this(null, project);
    }

    private NpcAiDebugView(@Nullable NPCProject project, @Nullable AMIntentionProject amProject) {
        super(ViScriptNpc.MOD_ID + ".editor.view.ai_debug");
        this.project = project;
        this.amProject = amProject;
        layout(layout -> layout.widthPercent(100).heightPercent(100).paddingAll(3).gapAll(2));
        UIElement toolbar = new UIElement();
        toolbar.layout(layout -> layout.widthPercent(100).height(18).flexDirection(FlexDirection.ROW).gapAll(2));
        toolbar.addChild(button("viscript_npc.editor.ai.debug.pause", () -> send(C2SPayload.SET_NPC_AI_DEBUG_PAUSED, true)));
        toolbar.addChild(button("viscript_npc.editor.ai.debug.continue", () -> send(C2SPayload.CONTINUE_NPC_AI_DEBUG)));
        toolbar.addChild(button("viscript_npc.editor.ai.debug.step", () -> send(C2SPayload.STEP_NPC_AI_DEBUG)));
        toolbar.addChild(button("viscript_npc.editor.ai.debug.breakpoint", this::toggleSelectedBreakpoints));
        toolbar.addChild(button("viscript_npc.editor.ai.debug.refresh", this::request));
        addChild(toolbar);
        status.setText(Component.empty());
        status.layout(layout -> layout.widthPercent(100).flex(1));
        addChild(status);
        addEventListener(UIEvents.TICK, event -> {
            if (++ticks % 10 == 0) {
                request();
                refreshText();
            }
        });
        refreshText();
    }

    private Button button(String key, Runnable action) {
        Button button = new Button().setText(key).setOnClick(event -> action.run());
        button.layout(layout -> layout.flex(1).height(16));
        return button;
    }

    private void request() {
        targetId = ViScriptNpcClientUtil.findNearestNpcAiDebugTarget(
                project == null ? null : project.getCurrentNpcType());
        if (targetId >= 0) {
            RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_NPC_AI_DEBUG_SNAPSHOT, targetId);
        } else {
            NpcAiDebugClientState.clear();
        }
    }

    private void toggleSelectedBreakpoints() {
        Set<UUID> selected = project != null ? project.getSelectedFlowNodes()
                : amProject == null ? Set.of() : amProject.getSelectedNodes();
        if (selected.isEmpty()) return;
        if (targetId < 0) request();
        if (targetId < 0) return;
        LinkedHashSet<UUID> updated = new LinkedHashSet<>(NpcAiDebugClientState.breakpoints());
        for (UUID node : selected) {
            if (!updated.remove(node)) updated.add(node);
        }
        Set<String> ids = updated.stream().map(UUID::toString)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        RPCPacketDistributor.rpcToServer(C2SPayload.SET_NPC_AI_DEBUG_BREAKPOINTS, targetId,
                com.viscript.npc.network.s2c.S2CPayload.encodeStrings(ids));
    }

    private void send(String packet, Object... arguments) {
        if (targetId < 0) request();
        if (targetId < 0) return;
        Object[] payload = new Object[arguments.length + 1];
        payload[0] = targetId;
        System.arraycopy(arguments, 0, payload, 1, arguments.length);
        RPCPacketDistributor.rpcToServer(packet, payload);
    }

    private void refreshText() {
        if (targetId < 0) {
            status.setText(Component.translatable("viscript_npc.editor.ai.debug.no_target"));
            return;
        }
        CompoundTag payload = ViScriptNpcClientUtil.getNpcAiDebugSnapshot(targetId);
        NpcAiDebugClientState.update(payload);
        CompoundTag snapshot = payload.getCompound("snapshot");
        CompoundTag mind = snapshot.getCompound("mind_debug");
        CompoundTag flow = snapshot.getCompound("flow");
        StringBuilder text = new StringBuilder(ViScriptNpcClientUtil.getClientNpcDebugName(targetId));
        text.append(" | ").append(payload.getBoolean("paused") ? "PAUSED" : "RUNNING");
        if (mind.contains("active", Tag.TAG_COMPOUND)) {
            CompoundTag active = mind.getCompound("active");
            text.append("\nAM: ").append(active.getString("type")).append(" [")
                    .append(active.getString("status")).append("] priority=")
                    .append(active.getString("priority"));
            if (active.contains("current_node")) text.append(" @ ").append(active.getString("current_node"));
            if (!active.getCompound("parameters").isEmpty()) {
                text.append("\nAM parameters: ").append(active.getCompound("parameters"));
            }
            if (!active.getList("call_stack", Tag.TAG_STRING).isEmpty()) {
                text.append("\nAM call stack: ").append(active.getList("call_stack", Tag.TAG_STRING));
            }
            if (active.contains("current_guard")) {
                text.append("\nAM guard: ").append(active.getString("current_guard"));
            }
            if (active.contains("current_child")) {
                text.append("\nAM child: ").append(active.getString("current_child"));
            }
            if (active.contains("failure", Tag.TAG_COMPOUND)) {
                appendDiagnostic(text, "AM failure", active.getCompound("failure"));
            }
        } else {
            text.append("\nAM: Idle");
        }
        var queued = mind.getList("queued", Tag.TAG_COMPOUND);
        text.append(" | queue=").append(queued.size());
        for (int index = 0; index < queued.size(); index++) {
            CompoundTag item = queued.getCompound(index);
            text.append("\n  queued ").append(index + 1).append(": ").append(item.getString("type"))
                    .append(" [").append(item.getString("status")).append("] priority=")
                    .append(item.getString("priority"));
        }
        var diagnostics = mind.getList("diagnostics", Tag.TAG_COMPOUND);
        for (int index = 0; index < diagnostics.size(); index++) {
            appendDiagnostic(text, "AM diagnostic", diagnostics.getCompound(index));
        }
        var current = flow.getList("current_nodes", Tag.TAG_COMPOUND);
        text.append("\nFlow: active=").append(flow.getInt("active_count"));
        if (!current.isEmpty()) text.append(" node=").append(current.getCompound(0).getUUID("node"));
        var activeFlows = flow.getList("active", Tag.TAG_COMPOUND);
        for (int index = 0; index < activeFlows.size(); index++) {
            CompoundTag active = activeFlows.getCompound(index);
            text.append("\n  rule=").append(active.hasUUID("rule") ? active.getUUID("rule") : "?");
            if (active.hasUUID("target")) text.append(" target=").append(active.getUUID("target"));
            if (active.hasUUID("waiting_handle")) {
                text.append(" waiting=").append(active.getUUID("waiting_handle"));
            }
            text.append(" delay=").append(active.getInt("delay"))
                    .append(" paused=").append(active.getBoolean("paused"));
        }
        text.append("\nVariables: ").append(flow.getCompound("variables"));
        var errors = flow.getList("errors", Tag.TAG_COMPOUND);
        if (!errors.isEmpty()) {
            CompoundTag error = errors.getCompound(errors.size() - 1);
            text.append("\nError: ").append(error.getString("code")).append(" - ")
                    .append(error.getString("detail"));
        }
        NPCProject.FlowEditorSnapshot editorSnapshot = project == null ? null : project.getFlowEditorSnapshot();
        if (editorSnapshot != null) {
            var compileErrors = editorSnapshot.compiledFlow().getList("errors", Tag.TAG_STRING);
            for (int index = 0; index < compileErrors.size(); index++) {
                text.append("\nCompile: ").append(compileErrors.getString(index));
            }
        }
        if (amProject != null) {
            for (String error : amProject.compile().errors()) text.append("\nCompile: ").append(error);
        }
        status.setText(Component.literal(text.toString()));
    }

    private static void appendDiagnostic(StringBuilder text, String label, CompoundTag diagnostic) {
        text.append('\n').append(label).append(": ")
                .append(diagnostic.getString("code"));
        String message = diagnostic.getString("message_key");
        if (message.isBlank()) message = diagnostic.getString("detail");
        if (!message.isBlank()) text.append(" - ").append(message);
        if (!diagnostic.getCompound("arguments").isEmpty()) {
            text.append(' ').append(diagnostic.getCompound("arguments"));
        }
        if (diagnostic.contains("json_pointer")) {
            text.append(" [").append(diagnostic.getString("json_pointer")).append(']');
        }
    }
}
