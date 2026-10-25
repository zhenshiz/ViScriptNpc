package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Button;
import com.lowdragmc.lowdraglib2.gui.ui.elements.TextElement;
import com.viscript.npc.npc.ai.data.AiDebugSnapshot;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/** 当前测试 NPC 的实时状态、变量和可定位节点的执行日志。 */
public final class AiDebugPanel {
    private AiDebugPanel() {}

    public static void build(AiGraphView view, ConfiguratorGroup group) {
        var controls = new Configurator();
        controls.inlineContainer.addChild(new Button().setText(Component.translatable("viscript_npc.ai.debug.toggle_logging"))
                .setOnClick(event -> {
                    var payload = new CompoundTag(); payload.putBoolean("enabled", !AiClientDebug.latest.logging);
                    AiClientDebug.command("ai_logging", AiClientDebug.selected(), payload);
                }));
        controls.inlineContainer.addChild(new Button().setText(Component.translatable("viscript_npc.ai.debug.clear_log"))
                .setOnClick(event -> view.debugCommand("ai_clear_log")));
        group.addConfigurator(controls);
        var live = new Configurator() {
            private AiDebugSnapshot previous;
            @Override public void screenTick() {
                super.screenTick();
                var data = AiClientDebug.latest;
                if (previous == data) return;
                previous = data; inlineContainer.clearAllChildren();
                inlineContainer.layout(l -> l.flexDirection(dev.vfyjxf.taffy.style.FlexDirection.COLUMN).gapAll(3));
                String status = data.status.isBlank() ? "disconnected" : data.status;
                inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.summary", data.entityId,
                        Component.translatable("viscript_npc.ai.debug." + status), data.tick)));
                if (data.paused && !data.currentNode.isBlank()) {
                    var next = new Button().setText(Component.translatable("viscript_npc.ai.debug.next_node",
                                    Component.translatable("viscript_npc.ai.node." + data.currentOperation)))
                            .setOnClick(event -> view.locateDebugNode(data.currentNode, data.currentAction));
                    next.setId("npc_ai_debug_next");
                    inlineContainer.addChild(next);
                    inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.location",
                            data.currentAction.isBlank() ? Component.translatable("viscript_npc.ai.debug.current_graph")
                                    : AiActionNames.displayName(data.currentAction, view.actionResolver.apply(data.currentAction)), data.currentDepth)));
                } else if (data.paused && data.tick > 0 && !data.tickInProgress) {
                    inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.tick_complete"))
                            .setId("npc_ai_debug_tick_complete"));
                }
                if (!data.lastNode.isBlank()) inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.last_node",
                        Component.translatable("viscript_npc.ai.node." + data.lastOperation),
                        Component.translatable("viscript_npc.ai.debug." + data.lastResult))).setId("npc_ai_debug_last"));
                if (!data.breakpoint.isBlank()) inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.breakpoint_hit")));
                if (!data.reason.isBlank()) inlineContainer.addChild(text(Component.literal(data.reason)));
                inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.variables")));
                data.variables.forEach((id, value) -> {
                    String name = view.model().declarations().variables.stream().filter(v -> v.id.equals(id))
                            .map(v -> v.name).findFirst().orElse(id);
                    inlineContainer.addChild(text(Component.literal(name + " = " + value)));
                });
                data.values.forEach((name, value) -> inlineContainer.addChild(text(Component.literal(name + " = " + value))));
                data.inputs.forEach((name, value) -> {
                    if (name.startsWith(data.currentNode + ":") && !data.currentNode.isBlank())
                        inlineContainer.addChild(text(AiGraphNode.fieldName(data.currentOperation, name.substring(data.currentNode.length() + 1))
                                .copy().append(" = " + value)));
                });
                inlineContainer.addChild(text(Component.translatable("viscript_npc.ai.debug.events", data.logging)));
                for (int i = data.events.size() - 1; i >= 0; i--) {
                    var entry = data.events.get(i);
                    var title = Component.literal("[" + entry.tick + "] ")
                            .append(Component.translatable("viscript_npc.ai.node." + entry.operation)).append(" · ")
                            .append(Component.translatable("viscript_npc.ai.debug." + entry.status));
                    if (!entry.message.isBlank()) title.append(" · " + entry.message);
                    var row = new Button().setText(title).setOnClick(event -> view.locateDebugNode(entry.nodeId, ""));
                    row.layout(l -> l.widthPercent(100).minHeight(16).flexShrink(0));
                    row.style(s -> s.tooltips(title));
                    inlineContainer.addChild(row);
                }
            }
        };
        live.setId("npc_ai_debug_live");
        group.addConfigurator(live);
    }

    private static TextElement text(Component text) {
        var element = new TextElement().setText(text);
        element.layout(l -> l.widthPercent(100).minHeight(10).flexShrink(0));
        return element;
    }
}
