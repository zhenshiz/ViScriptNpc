package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.CollapsibleInOutNodeElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.viscript.npc.npc.ai.data.AiValue;
import net.minecraft.network.chat.Component;

/** 画布只显示行为名和简短摘要；详细编辑进入属性面板。 */
public final class AiNodeElement extends CollapsibleInOutNodeElement {
    private final Label summary = new Label();
    private final Label error = new Label();
    private final UIElement state = new UIElement().addClass("npc_ai_state");
    private final UIElement breakpoint = new UIElement().addClass("npc_ai_breakpoint");
    private final Label cursor = new Label();
    public AiNodeElement(AiNodeModel model) {
        super(model);
        setId("npc_ai_node_" + model.getUid());
        addClass("npc_ai_node");
        addEventListener(UIEvents.DOUBLE_CLICK, event -> {
            if (getGraphView() instanceof AiGraphView view && model.operation().equals("call"))
                view.openAction(model.data.literal("file", AiValue.text("")).text);
        });
    }
    @Override protected void buildUI() {
        super.buildUI();
        // LDLib2's Label constructor uses the placeholder text "Label".  The summary is
        // optional (only call/stage nodes use it), so clear and hide it before the first
        // layout pass.  Waiting for screenTick left that placeholder visible for one frame
        // when selecting a node or editing the action display-name key.
        summary.setText(Component.empty());
        summary.setDisplay(false);
        summary.layout(l -> l.paddingHorizontal(5).widthPercent(100).minWidth(0));
        summary.textStyle(s -> s.adaptiveWidth(false).textWrap(com.lowdragmc.lowdraglib2.gui.ui.data.TextWrap.HIDE));
        addChild(summary);
        error.setText(Component.literal("!"));
        error.textStyle(s -> s.textColor(0xFFFF6666));
        error.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).right(2).top(2).width(10).height(10));
        addChild(error);
        state.setId("npc_ai_state_" + getModel().getUid());
        state.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).right(3).bottom(3).width(6).height(6));
        addChild(state);
        breakpoint.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).right(12).bottom(3).width(6).height(6));
        breakpoint.setDisplay(false);
        addChild(breakpoint);
        cursor.setId("npc_ai_cursor_" + getModel().getUid());
        cursor.setText(Component.literal("▶"));
        cursor.layout(l -> l.positionType(dev.vfyjxf.taffy.style.TaffyPosition.ABSOLUTE).right(22).bottom(2).width(10).height(10));
        cursor.addClass("npc_ai_debug_cursor"); cursor.setDisplay(false);
        cursor.style(s -> s.tooltips(Component.translatable("viscript_npc.ai.debug.before_node")));
        addChild(cursor);
    }
    @Override public void screenTick() {
        super.screenTick();
        AiNodeModel model = (AiNodeModel) getModel();
        String text = switch (model.operation()) {
            case "call" -> model.data.literal("file", AiValue.text("")).text;
            case "stage" -> model.data.literal("name", AiValue.text("")).text;
            default -> "";
        };
        boolean showSummary = !text.isEmpty() && !model.isCollapsed();
        summary.setDisplay(showSummary);
        if (!showSummary) summary.setText(Component.empty());
        if (getGraphView() instanceof AiGraphView view) {
            var port = model.getInputsById().get("value_entity_type");
            if (com.viscript.npc.npc.ai.runtime.AiOperations.isPerception(model.operation()) && port != null) {
                boolean visible = view.model().usesEntityTypeFilter(model);
                var element = view.getModelElement(port);
                if (element != null) element.setDisplay(visible);
            }
            String caption = text;
            if (model.operation().equals("call") && !text.isBlank()) {
                caption = AiActionNames.displayName(text, view.actionResolver.apply(text)).getString();
                if (caption.equals(text)) caption = text.substring(text.lastIndexOf('/') + 1);
            }
            summary.setText(Component.literal(caption));
            summary.style(s -> s.tooltips(Component.literal(text)));
            var issue = ((AiGraph) view.getGraph()).nodeErrors.get(model.getUid().toString());
            error.setDisplay(issue != null);
            if (issue != null) error.style(s -> s.tooltips(issue));
            String id = model.getUid().toString();
            boolean running = view.activeNodes.contains(id) && !AiClientDebug.latest.paused && !model.data.disabled;
            if (running) state.addClass("npc_ai_state_running"); else state.removeClass("npc_ai_state_running");
            var statusTip = Component.translatable("viscript_npc.ai.debug." + (model.data.disabled ? "disabled" : running ? "running" : "inactive"));
            var result = AiClientDebug.latest.nodeStates.get(id);
            if (result != null) statusTip.append(" · ").append(Component.translatable("viscript_npc.ai.debug." + result));
            state.style(s -> s.tooltips(statusTip));
            boolean next = AiClientDebug.latest.paused && AiClientDebug.latest.currentNode.equals(id);
            cursor.setDisplay(next);
            if (next) addClass("npc_ai_node_next"); else removeClass("npc_ai_node_next");
            breakpoint.setDisplay(view.model().breakpoints.contains(id));
            breakpoint.style(s -> s.tooltips(Component.translatable("viscript_npc.ai.debug." +
                    (AiClientDebug.latest.breakpoint.equals(id) ? "breakpoint_hit" : "breakpoint"))));
            if (model.data.disabled) addClass("npc_ai_node_disabled"); else removeClass("npc_ai_node_disabled");
            if (running) style(s -> s.tooltips(Component.literal(AiClientDebug.latest.status + " · " + AiClientDebug.latest.phase
                    + (AiClientDebug.latest.reason.isBlank() ? "" : " · " + AiClientDebug.latest.reason))));
            else style(s -> s.tooltips(Component.translatable("viscript_npc.ai.node." + model.operation())));
        }
    }
}
