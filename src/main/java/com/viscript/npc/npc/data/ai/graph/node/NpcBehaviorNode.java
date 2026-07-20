package com.viscript.npc.npc.data.ai.graph.node;

import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortCapacity;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortOrientation;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IOptionDefinitionContext;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.definition.IPortDefinitionContext;
import net.minecraft.network.chat.Component;

public abstract class NpcBehaviorNode extends Node {
    public static final String CATEGORY_FLOW = "viscript_npc.ai.category.flow";
    public static final String CATEGORY_COMPOSITE = "viscript_npc.ai.category.composite";
    public static final String CATEGORY_DECORATOR = "viscript_npc.ai.category.decorator";
    public static final String CATEGORY_CONDITION = "viscript_npc.ai.category.condition";
    public static final String CATEGORY_ACTION = "viscript_npc.ai.category.action";

    public static final String NODE_ROOT = "viscript_npc.ai.node.root";
    public static final String NODE_SEQUENCE = "viscript_npc.ai.node.sequence";
    public static final String NODE_SELECTOR = "viscript_npc.ai.node.selector";
    public static final String NODE_INVERTER = "viscript_npc.ai.node.inverter";
    public static final String NODE_HAS_TARGET = "viscript_npc.ai.node.has_target";
    public static final String NODE_IDLE = "viscript_npc.ai.node.idle";
    public static final String NODE_MOVE_TO_POSITION = "viscript_npc.ai.node.move_to_position";
    public static final String NODE_MELEE_ATTACK_TARGET = "viscript_npc.ai.node.melee_attack_target";
    public static final String NODE_FIND_NEAREST_PLAYER = "viscript_npc.ai.node.find_nearest_player";
    public static final String NODE_IS_TARGET_IN_RANGE = "viscript_npc.ai.node.is_target_in_range";
    public static final String NODE_FOLLOW_TARGET = "viscript_npc.ai.node.follow_target";
    public static final String NODE_LOOK_AT_TARGET = "viscript_npc.ai.node.look_at_target";

    public static final String PORT_IN = "in";
    public static final String PORT_CHILD = "child";
    public static final String PORT_CHILDREN = "children";

    protected Component nodeName(String key) {
        return Component.translatable(nodeKey(key));
    }

    protected Component portName(String key) {
        return Component.translatable("viscript_npc.ai.port." + key);
    }

    protected Component optionName(String key) {
        return Component.translatable("viscript_npc.ai.option." + key);
    }

    private String nodeKey(String key) {
        return "viscript_npc.ai.node." + key;
    }

    protected void addFlowInput(IPortDefinitionContext context) {
        IPort port = context.addInputPort(PORT_IN, TypeHandles.EXECUTION_FLOW)
                .withDisplayName(portName(PORT_IN))
                .withOrientation(PortOrientation.Vertical)
                .withoutConfigurator()
                .build();
        setCapacity(port, PortCapacity.SINGLE);
    }

    protected void addChildOutput(IPortDefinitionContext context) {
        IPort port = context.addOutputPort(PORT_CHILD, TypeHandles.EXECUTION_FLOW)
                .withDisplayName(portName(PORT_CHILD))
                .withOrientation(PortOrientation.Vertical)
                .build();
        setCapacity(port, PortCapacity.SINGLE);
    }

    protected void addChildrenOutput(IPortDefinitionContext context) {
        IPort port = context.addOutputPort(PORT_CHILDREN, TypeHandles.EXECUTION_FLOW)
                .withDisplayName(portName(PORT_CHILDREN))
                .withOrientation(PortOrientation.Vertical)
                .build();
        setCapacity(port, PortCapacity.MULTIPLE);
    }

    private void setCapacity(IPort port, PortCapacity capacity) {
        if (port instanceof PortModel portModel) {
            portModel.setPortCapacity(capacity);
        }
    }

    protected void intOption(IOptionDefinitionContext context, String id, int defaultValue) {
        context.addOption(id, TypeHandles.INT)
                .withDisplayName(optionName(id))
                .withDefaultValue(defaultValue);
    }

    protected void floatOption(IOptionDefinitionContext context, String id, float defaultValue) {
        context.addOption(id, TypeHandles.FLOAT)
                .withDisplayName(optionName(id))
                .withDefaultValue(defaultValue);
    }

    protected void boolOption(IOptionDefinitionContext context, String id, boolean defaultValue) {
        context.addOption(id, TypeHandles.BOOL)
                .withDisplayName(optionName(id))
                .withDefaultValue(defaultValue);
    }
}
