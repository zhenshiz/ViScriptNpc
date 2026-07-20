package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.IPort;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.constant.Constant;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.ModelState;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.ICustomNodeModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.NodeOption;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.PortModel;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorAbortType;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorCustomNodeModel;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorGraph;
import com.viscript.npc.npc.data.ai.graph.node.FindNearestPlayerNode;
import com.viscript.npc.npc.data.ai.graph.node.FollowTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.HasTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.IdleNode;
import com.viscript.npc.npc.data.ai.graph.node.InverterNode;
import com.viscript.npc.npc.data.ai.graph.node.IsTargetInRangeNode;
import com.viscript.npc.npc.data.ai.graph.node.LookAtTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.MeleeAttackTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.MoveToPositionNode;
import com.viscript.npc.npc.data.ai.graph.node.NpcBehaviorNode;
import com.viscript.npc.npc.data.ai.graph.node.RootNode;
import com.viscript.npc.npc.data.ai.graph.node.SelectorNode;
import com.viscript.npc.npc.data.ai.graph.node.SequenceNode;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class NpcBehaviorProgramCompiler {
    private NpcBehaviorProgramCompiler() {
    }

    public static CompoundTag compileToTag(CompoundTag graphTag, HolderLookup.Provider provider) {
        NpcBehaviorDataSerializers.register();
        return compile(graphTag).serializeNBT(provider);
    }

    public static NpcBehaviorProgram compile(CompoundTag graphTag) {
        NpcBehaviorGraph graph = new NpcBehaviorGraph();
        if (graphTag != null && !graphTag.isEmpty()) {
            graph.graphModel.deserializeNBT(Platform.getFrozenRegistry(), graphTag.copy());
        }
        graph.ensureDefaultRoot();
        return compile(graph);
    }

    public static NpcBehaviorProgram compile(NpcBehaviorGraph graph) {
        NpcBehaviorProgram program = new NpcBehaviorProgram();
        RootNode root = findRootNode(graph);
        if (root == null) {
            return program;
        }

        Map<UUID, NpcBehaviorProgramNode> nodes = new LinkedHashMap<>();
        visit(root, nodes, new TaskIndex(), false);
        program.setRootNode(root.getNodeModel().getUid());
        program.setNodes(new ArrayList<>(nodes.values()));
        return program;
    }

    private static void visit(Node node, Map<UUID, NpcBehaviorProgramNode> nodes, TaskIndex taskIndex, boolean parentDisabled) {
        UUID uid = node.getNodeModel().getUid();
        if (nodes.containsKey(uid)) {
            return;
        }

        boolean disabled = parentDisabled || node.getNodeModel().getState() == ModelState.DISABLED;
        NpcBehaviorProgramNode programNode = new NpcBehaviorProgramNode(uid, nodeType(node));
        programNode.setTaskIndex(taskIndex.next());
        programNode.setDisplayName(node.getNodeModel().getTitle().getString());
        programNode.setDisabled(disabled);
        if (node.getNodeModel() instanceof NpcBehaviorCustomNodeModel behaviorNode) {
            programNode.setComment(behaviorNode.getComment());
            programNode.setBreakpoint(behaviorNode.isBreakpoint());
            programNode.setAbortType(behaviorNode.getAbortType().getSerializedName());
        } else {
            programNode.setAbortType(NpcBehaviorAbortType.NONE.getSerializedName());
        }
        programNode.setOptions(options(node));
        nodes.put(uid, programNode);

        for (Node child : behaviorChildren(node)) {
            programNode.getChildren().add(child.getNodeModel().getUid());
            visit(child, nodes, taskIndex, disabled);
        }
    }

    private static RootNode findRootNode(NpcBehaviorGraph graph) {
        for (var node : graph.getNodes()) {
            if (node instanceof RootNode root) {
                return root;
            }
        }
        return null;
    }

    private static List<Node> behaviorChildren(Node node) {
        if (node instanceof RootNode || node instanceof InverterNode) {
            return firstChild(node, NpcBehaviorNode.PORT_CHILD);
        }
        if (node instanceof SequenceNode || node instanceof SelectorNode) {
            return children(node, NpcBehaviorNode.PORT_CHILDREN);
        }
        return List.of();
    }

    private static List<Node> firstChild(Node node, String outputPortId) {
        List<Node> children = children(node, outputPortId);
        return children.isEmpty() ? List.of() : List.of(children.getFirst());
    }

    private static List<Node> children(Node node, String outputPortId) {
        IPort outputPort = node.getOutputPortById(outputPortId);
        if (!(outputPort instanceof PortModel portModel)) {
            return List.of();
        }
        ArrayList<IPort> connectedPorts = new ArrayList<>();
        portModel.getConnectedPorts(connectedPorts);
        List<Node> nodes = new ArrayList<>();
        for (IPort connectedPort : connectedPorts) {
            if (connectedPort instanceof PortModel connectedPortModel
                    && connectedPortModel.getNodeModel() instanceof ICustomNodeModel customNodeModel
                    && customNodeModel.getNode() != null) {
                nodes.add(customNodeModel.getNode());
            }
        }
        return nodes;
    }

    private static String nodeType(Node node) {
        var handler = NpcBehaviorNodeHandlers.forNode(node);
        if (handler.isPresent()) {
            return handler.get().nodeType();
        }
        if (node instanceof RootNode) return NpcBehaviorNodeType.ROOT;
        if (node instanceof SequenceNode) return NpcBehaviorNodeType.SEQUENCE;
        if (node instanceof SelectorNode) return NpcBehaviorNodeType.SELECTOR;
        if (node instanceof InverterNode) return NpcBehaviorNodeType.INVERTER;
        if (node instanceof HasTargetNode) return NpcBehaviorNodeType.HAS_TARGET;
        if (node instanceof FindNearestPlayerNode) return NpcBehaviorNodeType.FIND_NEAREST_PLAYER;
        if (node instanceof IsTargetInRangeNode) return NpcBehaviorNodeType.IS_TARGET_IN_RANGE;
        if (node instanceof IdleNode) return NpcBehaviorNodeType.IDLE;
        if (node instanceof FollowTargetNode) return NpcBehaviorNodeType.FOLLOW_TARGET;
        if (node instanceof LookAtTargetNode) return NpcBehaviorNodeType.LOOK_AT_TARGET;
        if (node instanceof MoveToPositionNode) return NpcBehaviorNodeType.MOVE_TO_POSITION;
        if (node instanceof MeleeAttackTargetNode) return NpcBehaviorNodeType.MELEE_ATTACK_TARGET;
        return "";
    }

    private static CompoundTag options(Node node) {
        CompoundTag options = new CompoundTag();
        var handler = NpcBehaviorNodeHandlers.forNode(node);
        if (handler.isPresent()) {
            handler.get().compileOptions(node, options);
        } else if (node instanceof FindNearestPlayerNode) {
            putFloat(options, node, FindNearestPlayerNode.OPTION_RANGE, 16.0f);
            putBool(options, node, FindNearestPlayerNode.OPTION_IGNORE_CREATIVE, true);
        } else if (node instanceof IsTargetInRangeNode) {
            putFloat(options, node, IsTargetInRangeNode.OPTION_RANGE, 4.0f);
        } else if (node instanceof IdleNode) {
            putInt(options, node, IdleNode.OPTION_DURATION, 20);
        } else if (node instanceof FollowTargetNode) {
            putFloat(options, node, FollowTargetNode.OPTION_SPEED, 1.0f);
            putFloat(options, node, FollowTargetNode.OPTION_STOPPING_DISTANCE, 2.0f);
        } else if (node instanceof LookAtTargetNode) {
            putFloat(options, node, LookAtTargetNode.OPTION_YAW_SPEED, 30.0f);
            putFloat(options, node, LookAtTargetNode.OPTION_PITCH_SPEED, 30.0f);
        } else if (node instanceof MoveToPositionNode) {
            putFloat(options, node, MoveToPositionNode.OPTION_X, 0.0f);
            putFloat(options, node, MoveToPositionNode.OPTION_Y, 0.0f);
            putFloat(options, node, MoveToPositionNode.OPTION_Z, 0.0f);
            putFloat(options, node, MoveToPositionNode.OPTION_SPEED, 1.0f);
            putFloat(options, node, MoveToPositionNode.OPTION_STOPPING_DISTANCE, 1.0f);
        } else if (node instanceof MeleeAttackTargetNode) {
            putFloat(options, node, MeleeAttackTargetNode.OPTION_RANGE, 2.0f);
            putInt(options, node, MeleeAttackTargetNode.OPTION_COOLDOWN, 20);
        }
        return options;
    }

    private static void putFloat(CompoundTag tag, Node node, String id, float fallback) {
        tag.putFloat(id, optionFloat(node, id, fallback));
    }

    private static void putInt(CompoundTag tag, Node node, String id, int fallback) {
        tag.putInt(id, optionInt(node, id, fallback));
    }

    private static void putBool(CompoundTag tag, Node node, String id, boolean fallback) {
        tag.putBoolean(id, optionBool(node, id, fallback));
    }

    private static int optionInt(Node node, String id, int fallback) {
        Object value = optionValue(node, id);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String string) {
            try {
                return Integer.parseInt(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static float optionFloat(Node node, String id, float fallback) {
        Object value = optionValue(node, id);
        if (value instanceof Number number) {
            return number.floatValue();
        }
        if (value instanceof String string) {
            try {
                return Float.parseFloat(string);
            } catch (NumberFormatException ignored) {
            }
        }
        return fallback;
    }

    private static boolean optionBool(Node node, String id, boolean fallback) {
        Object value = optionValue(node, id);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String string) {
            return Boolean.parseBoolean(string);
        }
        return fallback;
    }

    private static Object optionValue(Node node, String id) {
        if (!(node.getNodeOptionById(id) instanceof NodeOption option)) {
            return null;
        }
        Constant embeddedValue = option.getPortModel().getEmbeddedValue();
        if (embeddedValue == null) {
            return null;
        }
        Object value = embeddedValue.getValue();
        return value == null ? embeddedValue.getDefaultValue() : value;
    }

    private static final class TaskIndex {
        private int next;

        private int next() {
            return next++;
        }
    }
}
