package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.editor.ui.View;
import com.lowdragmc.lowdraglib2.gui.texture.Icons;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollDisplay;
import com.lowdragmc.lowdraglib2.gui.ui.data.ScrollerMode;
import com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ScrollerView;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.gui.edit.page.INpcEditorPage;
import com.viscript.npc.network.c2s.C2SPayload;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.runtime.NpcBehaviorProgramCompiler;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import dev.vfyjxf.taffy.style.AlignItems;
import dev.vfyjxf.taffy.style.FlexDirection;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.function.Supplier;

public class NpcBehaviorDebugView extends View implements INpcEditorSlotView {
    private static final int SNAPSHOT_REQUEST_INTERVAL = 5;
    private static final int AI_SYNC_INTERVAL = 5;

    private final NpcEditor editor;
    private final Supplier<NpcBehaviorGraphView> graphViewSupplier;
    private final UIElement toolbar = new UIElement();
    private final ScrollerView body = new ScrollerView();
    private final Label debugStatusLabel = new Label();
    private final Label selectedLabel = new Label();
    private final Label compileLabel = new Label();
    private CompoundTag latestPayload = new CompoundTag();
    private int tickCounter;
    private int lastSnapshotHash;
    private int lastTargetEntityId = -1;
    private int lastSyncedTargetEntityId = -1;
    private int lastSyncedAiHash;

    public NpcBehaviorDebugView(NpcEditor editor, Supplier<NpcBehaviorGraphView> graphViewSupplier) {
        super(String.format("%s.editor.view.ai_debug", ViScriptNpc.MOD_ID), Icons.INFORMATION);
        this.editor = editor;
        this.graphViewSupplier = graphViewSupplier;

        layout(layout -> {
            layout.widthPercent(100);
            layout.heightPercent(100);
            layout.paddingAll(2);
            layout.gapAll(2);
        });

        toolbar.layout(layout -> {
            layout.widthPercent(100);
            layout.height(18);
            layout.flexDirection(FlexDirection.ROW);
            layout.alignItems(AlignItems.CENTER);
            layout.gapAll(2);
        });
        addChild(toolbar);

        selectedLabel.layout(layout -> {
            layout.width(170);
            layout.heightPercent(100);
        });
        selectedLabel.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        toolbar.addChild(selectedLabel);

        compileLabel.layout(layout -> {
            layout.flex(1);
            layout.heightPercent(100);
        });
        compileLabel.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        toolbar.addChild(compileLabel);

        toolbar.addChild(commandButton("viscript_npc.editor.ai_debug.refresh", Icons.REPLAY, 52, () -> {
            refreshCompileSummary(currentProject());
            rebuildSnapshot();
        }));
        toolbar.addChild(commandButton("viscript_npc.editor.ai_debug.continue", Icons.PLAY, 62,
                () -> sendDebugControl(C2SPayload.CONTINUE_NPC_AI_DEBUG)));
        toolbar.addChild(commandButton("viscript_npc.editor.ai_debug.step", Icons.RIGHT_ARROW_NO_BAR, 48,
                () -> sendDebugControl(C2SPayload.STEP_NPC_AI_DEBUG)));
        toolbar.addChild(commandButton("viscript_npc.editor.ai_debug.stop", Icons.STOP, 48,
                () -> sendDebugControl(C2SPayload.STOP_NPC_AI_DEBUG)));
        toolbar.addChild(commandButton("viscript_npc.editor.ai_debug.world_test", Icons.CAMERA, 66,
                this::enterWorldTestMode));

        body.layout(layout -> {
            layout.widthPercent(100);
            layout.flex(1);
        });
        body.viewContainer(container -> container.layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.COLUMN);
            layout.gapAll(2);
        }));
        body.scrollerStyle(style -> style
                .mode(ScrollerMode.VERTICAL)
                .verticalScrollDisplay(ScrollDisplay.ALWAYS));
        addChild(body);

        debugStatusLabel.layout(layout -> {
            layout.widthPercent(100);
            layout.height(24);
        });
        debugStatusLabel.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));

        rebuildSnapshot();
    }

    @Override
    public void onViewSelected(NpcEditor editor, NPCProject project, INpcEditorPage page) {
        clearDebugSnapshot();
        lastTargetEntityId = -1;
        lastSyncedTargetEntityId = -1;
        lastSyncedAiHash = 0;
        refreshCompileSummary(project);
        rebuildSnapshot();
    }

    @Override
    public void screenTick() {
        super.screenTick();
        tickCounter++;
        pollDebugTarget();
        if (tickCounter % 20 == 0) {
            refreshCompileSummary(currentProject());
        }
        int hash = latestPayload.hashCode();
        if (hash != lastSnapshotHash) {
            lastSnapshotHash = hash;
            rebuildSnapshot();
        }
    }

    private Button commandButton(String key, com.lowdragmc.lowdraglib2.gui.texture.IGuiTexture icon, int width, Runnable action) {
        Button button = new Button()
                .addPreIcon(icon)
                .setText(key)
                .setOnClick(event -> action.run());
        button.layout(layout -> {
            layout.width(width);
            layout.height(14);
        });
        return button;
    }

    private UIElement sectionPanel(String key) {
        UIElement panel = new UIElement();
        panel.layout(layout -> {
            layout.widthPercent(100);
            layout.paddingAll(4);
            layout.gapAll(2);
            layout.flexDirection(FlexDirection.COLUMN);
        });
        panel.style(style -> style.backgroundTexture(Sprites.BORDER1_RT1_DARK));
        panel.addChild(sectionLabel(key));
        return panel;
    }

    private Label sectionLabel(String key) {
        Label label = new Label();
        label.setText(Component.translatable(key));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(12);
        });
        return label;
    }

    private void rebuildSnapshot() {
        updateSelectedLabel();
        CompoundTag snapshot = latestPayload.getCompound("snapshot");
        if (snapshot.isEmpty()) {
            graphViewSupplier.get().clearDebugSnapshot();
        } else {
            graphViewSupplier.get().applyDebugSnapshot(snapshot);
        }
        refreshDebugPanel(snapshot);
    }

    private void clearDebugSnapshot() {
        latestPayload = new CompoundTag();
        lastSnapshotHash = 0;
        graphViewSupplier.get().clearDebugSnapshot();
    }

    private void pollDebugTarget() {
        int targetEntityId = selectedDebugEntityId();
        boolean targetChanged = targetEntityId != lastTargetEntityId;
        if (targetChanged) {
            lastTargetEntityId = targetEntityId;
            lastSyncedTargetEntityId = -1;
            lastSyncedAiHash = 0;
            latestPayload = new CompoundTag();
            graphViewSupplier.get().clearDebugSnapshot();
        }
        if (targetEntityId < 0) {
            return;
        }
        if (targetChanged || tickCounter % AI_SYNC_INTERVAL == 0) {
            syncDebugTargetAi(targetEntityId);
        }
        if (targetChanged || tickCounter % SNAPSHOT_REQUEST_INTERVAL == 0) {
            requestDebugSnapshot(targetEntityId);
        }
        CompoundTag payload = ViScriptNpcClientUtil.getNpcAiDebugSnapshot(targetEntityId);
        if (!payload.isEmpty()) {
            latestPayload = payload;
        }
    }

    private void syncDebugTargetAi(int targetEntityId) {
        NPCProject project = currentProject();
        if (project == null) {
            return;
        }
        try {
            graphViewSupplier.get().syncGraphToProject();
            NpcAI ai = project.npc.getNpcData(NpcAI.class);
            if (ai == null) {
                return;
            }
            var provider = Platform.getFrozenRegistry();
            CompoundTag aiTag = ai.serializeNBT(provider);
            CompoundTag programTag = ai.getCompiledBehaviorProgram();
            if (aiTag.contains("data", Tag.TAG_COMPOUND)) {
                aiTag.getCompound("data").put("behaviorProgram", programTag);
            } else {
                aiTag.put("behaviorProgram", programTag);
            }
            int hash = aiTag.hashCode();
            if (lastSyncedTargetEntityId == targetEntityId && lastSyncedAiHash == hash) {
                return;
            }
            lastSyncedTargetEntityId = targetEntityId;
            lastSyncedAiHash = hash;
            RPCPacketDistributor.rpcToServer(C2SPayload.SYNC_NPC_AI_DEBUG_CONFIG, targetEntityId, aiTag);
        } catch (Throwable ignored) {
        }
    }

    private void requestDebugSnapshot(int targetEntityId) {
        RPCPacketDistributor.rpcToServer(C2SPayload.REQUEST_NPC_AI_DEBUG_SNAPSHOT, targetEntityId);
    }

    private void sendDebugControl(String packet) {
        int targetEntityId = selectedDebugEntityId();
        if (targetEntityId < 0) {
            return;
        }
        syncDebugTargetAi(targetEntityId);
        RPCPacketDistributor.rpcToServer(packet, targetEntityId);
        requestDebugSnapshot(targetEntityId);
    }

    private void enterWorldTestMode() {
        int targetEntityId = selectedDebugEntityId();
        if (targetEntityId >= 0) {
            syncDebugTargetAi(targetEntityId);
            requestDebugSnapshot(targetEntityId);
        }
        ViScriptNpcClientUtil.enterNpcAiWorldTestMode(targetEntityId);
    }

    private void refreshCompileSummary(NPCProject project) {
        if (project == null) {
            compileLabel.setText(Component.translatable("viscript_npc.editor.ai_debug.no_project"));
            return;
        }
        project.serializeNpcConfig(Platform.getFrozenRegistry());
        NpcAI ai = project.npc.getNpcData(NpcAI.class);
        if (ai == null) {
            compileLabel.setText(Component.translatable("viscript_npc.editor.ai_debug.no_ai_config"));
            return;
        }
        try {
            var program = NpcBehaviorProgramCompiler.compile(ai.getBehaviorGraph());
            Component compiled = Component.translatable("viscript_npc.editor.ai_debug.compile_ok",
                    program.getNodes().size(), shortId(program.getRootNode().toString()));
            compileLabel.setText(ai.isEnabled()
                    ? compiled
                    : Component.translatable("viscript_npc.editor.ai_debug.compile_disabled", compiled));
        } catch (Throwable throwable) {
            compileLabel.setText(Component.translatable("viscript_npc.editor.ai_debug.compile_failed",
                    throwable.getClass().getSimpleName()));
        }
    }

    private void updateSelectedLabel() {
        NPCProject project = currentProject();
        int targetEntityId = selectedDebugEntityId();
        selectedLabel.setText(Component.translatable("viscript_npc.editor.ai_debug.selected",
                project == null ? Component.translatable("viscript_npc.editor.ai_debug.no_project") : Component.literal(project.getCurrentNpcType()),
                targetEntityId < 0
                        ? Component.translatable("viscript_npc.editor.ai_debug.no_debug_target")
                        : Component.literal(debugTargetName(targetEntityId))));
    }

    private void refreshDebugPanel(CompoundTag snapshot) {
        body.clearAllScrollViewChildren();
        UIElement statusPanel = sectionPanel("viscript_npc.editor.ai_debug.runtime_status");
        UIElement blackboardPanel = sectionPanel("viscript_npc.editor.ai_debug.blackboard");
        UIElement logsPanel = sectionPanel("viscript_npc.editor.ai_debug.logs");
        if (snapshot == null || snapshot.isEmpty()) {
            debugStatusLabel.setText(Component.translatable("viscript_npc.editor.ai_debug.no_snapshot"));
            statusPanel.addChild(debugStatusLabel);
            addLine(blackboardPanel, Component.translatable("viscript_npc.editor.ai_debug.blackboard_empty"));
            addLine(logsPanel, Component.translatable("viscript_npc.editor.ai_debug.log_empty"));
            body.addScrollViewChild(statusPanel);
            body.addScrollViewChild(blackboardPanel);
            body.addScrollViewChild(logsPanel);
            return;
        }
        boolean breakpoint = snapshot.getBoolean("breakpointPaused");
        String activeNode = breakpoint ? snapshot.getString("breakpointNode") : snapshot.getString("currentNode");
        debugStatusLabel.setText(Component.translatable(
                breakpoint ? "viscript_npc.editor.ai_debug.breakpoint_status" : "viscript_npc.editor.ai_debug.status",
                shortId(activeNode),
                emptyToDash(snapshot.getString("lastFailureReason"))));
        statusPanel.addChild(debugStatusLabel);
        addLines(blackboardPanel, snapshot.getList("blackboardLines", Tag.TAG_STRING), "viscript_npc.editor.ai_debug.blackboard_empty");
        addLines(logsPanel, snapshot.getList("logs", Tag.TAG_STRING), "viscript_npc.editor.ai_debug.log_empty");
        body.addScrollViewChild(statusPanel);
        body.addScrollViewChild(blackboardPanel);
        body.addScrollViewChild(logsPanel);
    }

    private void addLines(UIElement container, ListTag lines, String emptyKey) {
        if (lines == null || lines.isEmpty()) {
            addLine(container, Component.translatable(emptyKey));
            return;
        }
        int start = Math.max(0, lines.size() - 120);
        for (int i = start; i < lines.size(); i++) {
            addLine(container, Component.literal(lines.getString(i)));
        }
    }

    private void addLine(UIElement container, Component text) {
        container.addChild(lineLabel(text));
    }

    private Label lineLabel(Component text) {
        Label label = new Label();
        label.setText(text);
        label.textStyle(style -> style.textWrap(TextWrap.HOVER_ROLL));
        label.layout(layout -> {
            layout.widthPercent(100);
            layout.height(11);
        });
        return label;
    }

    private NPCProject currentProject() {
        return editor.getCurrentProject() instanceof NPCProject project ? project : null;
    }

    private int selectedDebugEntityId() {
        NPCProject project = currentProject();
        return project == null ? -1 : ViScriptNpcClientUtil.findNearestNpcAiDebugTarget(project.getCurrentNpcType());
    }

    private String debugTargetName(int targetEntityId) {
        String clientName = ViScriptNpcClientUtil.getClientNpcDebugName(targetEntityId);
        if (clientName != null && !clientName.isBlank()) {
            return clientName;
        }
        CompoundTag snapshot = latestPayload.getCompound("snapshot");
        String name = snapshot.getString("gameObjectName");
        if (name == null || name.isBlank()) {
            name = latestPayload.getString("npcType");
        }
        if (name == null || name.isBlank()) {
            name = "entity";
        }
        return name + " #" + targetEntityId;
    }

    private static String shortId(String id) {
        return id == null || id.length() <= 8 ? emptyToDash(id) : id.substring(0, 8);
    }

    private static String emptyToDash(String value) {
        return value == null || value.isEmpty() ? "-" : value;
    }

}
