package com.viscript.npc.gui.edit.view;

import com.lowdragmc.lowdraglib2.gui.ui.Style;
import com.lowdragmc.lowdraglib2.gui.ui.rendering.GUIContext;
import com.lowdragmc.lowdraglib2.gui.util.DrawerHelper;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.CollapsibleInOutNodeElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.CollapsibleNodeTitleElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.InOutPortContainerElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.PortContainerElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.node.VerticalPortContainerElement;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ModelState;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.AbstractNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortNodeModel;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorCustomNodeModel;
import org.jetbrains.annotations.NotNull;

public class NpcBehaviorNodeElement extends CollapsibleInOutNodeElement {
    private static final int DISABLED_FILL = 0x44000000;
    private static final int DISABLED_STRIPE = 0xff777777;
    private static final int DISABLED_BORDER = 0xff555555;
    private static final int DEBUG_SUCCESS = 0xff33c957;
    private static final int DEBUG_SUCCESS_FILL = 0x2433ff00;
    private static final int DEBUG_RUNNING = 0xff33ff66;
    private static final int DEBUG_RUNNING_FILL = 0x3833ff00;
    private static final int DEBUG_SKIPPED = 0xff777777;
    private static final int DEBUG_SKIPPED_FILL = 0x30777777;
    private static final int DEBUG_FAILURE = 0xffff3048;
    private static final int DEBUG_FAILURE_FILL = 0x30ff3048;
    private static final int DEBUG_ABORT = 0xffffaa00;
    private static final int DEBUG_ABORT_FILL = 0x30ffaa00;
    private static final int DEBUG_BREAKPOINT_HIT = 0xffffff00;
    private static final int DEBUG_BREAKPOINT_HIT_FILL = 0x40ffff00;
    private static final int BREAKPOINT_DOT = 0xffff2020;
    private static final int BREAKPOINT_DOT_BORDER = 0xffffffff;
    private static final int RESULT_BADGE_SIZE = 9;
    private static final int RESULT_BADGE_BORDER = 0xffffffff;
    private static final int RESULT_SUCCESS = 0xff33ff00;
    private static final int RESULT_FAILURE = 0xffff3048;
    private static final int RESULT_ABORT = 0xffffaa00;

    public NpcBehaviorNodeElement(AbstractNodeModel nodeModel) {
        super(nodeModel);
    }

    @Override
    protected void buildPartList() {
        parts.add(this.nodeTittle = new CollapsibleNodeTitleElement(getModel()));
        if (getModel() instanceof PortNodeModel portNodeNode) {
            parts.add(this.topPortContainer = new VerticalPortContainerElement(portNodeNode,
                    PortContainerElement.VERTICAL_PORT_FILTER.and(PortContainerElement.INPUT_PORT_FILTER)));
            parts.add(this.portContainerElement = new InOutPortContainerElement(portNodeNode, PortContainerElement.HORIZONTAL_PORT_FILTER));
            parts.add(this.bottomPortContainer = new VerticalPortContainerElement(portNodeNode,
                    PortContainerElement.VERTICAL_PORT_FILTER.and(PortContainerElement.OUTPUT_PORT_FILTER)));
        }
        buildPreviewPart();
    }

    @Override
    protected void buildUI() {
        super.buildUI();
        if (portContainerElement != null) {
            Style.importantPipeline(portContainerElement.getLayout(), layout -> layout.height(0));
        }
    }

    @Override
    public void drawBackgroundAdditional(@NotNull GUIContext guiContext) {
        super.drawBackgroundAdditional(guiContext);
        drawDebugBackground(guiContext);
        if (isEffectivelyDisabled()) {
            drawDisabledBackground(guiContext);
        }
    }

    @Override
    public void drawBackgroundOverlay(@NotNull GUIContext guiContext) {
        super.drawBackgroundOverlay(guiContext);
        if (getModel() instanceof NpcBehaviorCustomNodeModel behaviorNode && behaviorNode.isBreakpoint()) {
            float x = getPositionX() + getSizeWidth() - 8;
            float y = getPositionY() + 3;
            DrawerHelper.drawSolidRect(guiContext.graphics, x - 1, y - 1, 6, 6, BREAKPOINT_DOT_BORDER);
            DrawerHelper.drawSolidRect(guiContext.graphics, x, y, 4, 4, BREAKPOINT_DOT);
        }
        drawDebugResult(guiContext);
    }

    private void drawDebugBackground(@NotNull GUIContext guiContext) {
        if (!(graphView instanceof NpcBehaviorDebugGraphView debugGraphView)) {
            return;
        }
        NpcBehaviorDebugGraphView.DebugNodeState state = debugGraphView.getDebugState(getModel().getUid());
        if (state == null || !state.visible()) {
            return;
        }
        float x = getPositionX();
        float y = getPositionY();
        float width = getSizeWidth();
        float height = getSizeHeight();
        DrawerHelper.drawSolidRect(guiContext.graphics, x, y, width, height, debugFillColor(state));
        DrawerHelper.drawSolidRect(guiContext.graphics, x, y, Math.min(4, width), height, debugStripeColor(state));
        DrawerHelper.drawBorder(guiContext.graphics, x, y, width, height, debugBorderColor(state), state.breakpointHit() ? -3 : -2);
    }

    private void drawDisabledBackground(@NotNull GUIContext guiContext) {
        float x = getPositionX();
        float y = getPositionY();
        float width = getSizeWidth();
        float height = getSizeHeight();
        DrawerHelper.drawSolidRect(guiContext.graphics, x, y, width, height, DISABLED_FILL);
        DrawerHelper.drawSolidRect(guiContext.graphics, x, y, Math.min(4, width), height, DISABLED_STRIPE);
        DrawerHelper.drawBorder(guiContext.graphics, x, y, width, height, DISABLED_BORDER, -1);
    }

    private boolean isEffectivelyDisabled() {
        if (getModel().getState() == ModelState.DISABLED) {
            return true;
        }
        return getModel() instanceof NpcBehaviorCustomNodeModel behaviorNode && behaviorNode.isDisabledByParent();
    }

    private void drawDebugResult(@NotNull GUIContext guiContext) {
        if (!(graphView instanceof NpcBehaviorDebugGraphView debugGraphView)) {
            return;
        }
        NpcBehaviorDebugGraphView.DebugNodeState state = debugGraphView.getDebugState(getModel().getUid());
        if (state == null || !state.terminal()) {
            return;
        }
        float x = getPositionX() + getSizeWidth() - RESULT_BADGE_SIZE - 2;
        float y = getPositionY() + getSizeHeight() - RESULT_BADGE_SIZE - 2;
        DrawerHelper.drawSolidRect(guiContext.graphics, x - 1, y - 1, RESULT_BADGE_SIZE + 2, RESULT_BADGE_SIZE + 2, RESULT_BADGE_BORDER);
        DrawerHelper.drawSolidRect(guiContext.graphics, x, y, RESULT_BADGE_SIZE, RESULT_BADGE_SIZE, resultColor(state.status()));
    }

    private static int resultColor(String status) {
        if ("ABORTED".equals(status)) {
            return RESULT_ABORT;
        }
        return "FAILURE".equals(status) ? RESULT_FAILURE : RESULT_SUCCESS;
    }

    private static int debugFillColor(NpcBehaviorDebugGraphView.DebugNodeState state) {
        if (state.breakpointHit()) {
            return DEBUG_BREAKPOINT_HIT_FILL;
        }
        if (state.current() || "RUNNING".equals(state.status())) {
            return DEBUG_RUNNING_FILL;
        }
        if ("SKIPPED".equals(state.status())) {
            return DEBUG_SKIPPED_FILL;
        }
        if ("ABORTED".equals(state.status())) {
            return DEBUG_ABORT_FILL;
        }
        if ("FAILURE".equals(state.status())) {
            return DEBUG_FAILURE_FILL;
        }
        return DEBUG_SUCCESS_FILL;
    }

    private static int debugStripeColor(NpcBehaviorDebugGraphView.DebugNodeState state) {
        if (state.breakpointHit()) {
            return DEBUG_BREAKPOINT_HIT;
        }
        return debugBorderColor(state);
    }

    private static int debugBorderColor(NpcBehaviorDebugGraphView.DebugNodeState state) {
        if (state.breakpointHit()) {
            return DEBUG_BREAKPOINT_HIT;
        }
        if (state.current() || "RUNNING".equals(state.status())) {
            return DEBUG_RUNNING;
        }
        if ("SKIPPED".equals(state.status())) {
            return DEBUG_SKIPPED;
        }
        if ("ABORTED".equals(state.status())) {
            return DEBUG_ABORT;
        }
        if ("FAILURE".equals(state.status())) {
            return DEBUG_FAILURE;
        }
        return DEBUG_SUCCESS;
    }
}
