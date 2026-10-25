package com.viscript.npc.gui.ai;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.node.Node;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortDirection;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortOrientation;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.port.PortType;
import com.lowdragmc.lowdraglib2.nodegraphtookit.api.type.TypeHandles;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.variable.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.graph.CustomGraphModelImpl;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.GraphElementModel;
import com.lowdragmc.lowdraglib2.nodegraphtookit.gui.itemlibrary.GraphNodeCreationData;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.node.*;
import com.lowdragmc.lowdraglib2.nodegraphtookit.model.wire.WireModel;
import com.lowdragmc.lowdraglib2.syncdata.annotation.Persisted;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiOperations;
import org.joml.Vector2f;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.CompoundTag;

import java.util.*;

/** 校验控制流连接，并按独立于画布位置的子节点顺序编译行为。 */
public final class AiGraphModel extends CustomGraphModelImpl {
    @Persisted public AiProgram metadata = new AiProgram();
    @Persisted public Set<String> breakpoints = new LinkedHashSet<>();
    @Persisted public Map<String, AiVariablePresentation> variablePresentation = new LinkedHashMap<>();
    public AiGraphModel(AiGraph graph) { super(graph); }

    public AiNodeModel add(String operation, Vector2f position) {
        return add(operation, position, null);
    }
    public AiNodeModel add(String operation, Vector2f position, UUID id) {
        return createLibraryNode(operation, new GraphNodeCreationData(this, position, null, id));
    }

    /**
     * 按节点库的创建上下文创建 AI 节点，包括不写入工程的说明预览节点。
     *
     * @param operation 字符串形式的操作标识
     * @param creation 包含位置、节点标识和预览标记的创建上下文
     * @return 使用 VSN 数据和参数定义的节点模型
     */
    public AiNodeModel createLibraryNode(String operation, GraphNodeCreationData creation) {
        var holder = AiGraph.REGISTRY.get("viscript_npc:" + operation);
        if (holder == null) throw new IllegalArgumentException("Unknown node: " + operation);
        try {
            var node = holder.value().getDeclaredConstructor().newInstance();
            return createNodeWithType(AiNodeModel.class, "", creation.position(), creation.uuid(), model -> {
                model.initCustomNode(node); model.data.type = operation;
                model.data.id = model.getUid().toString();
            }, creation.spawnFlags());
        }
        catch (ReflectiveOperationException exception) { throw new IllegalStateException(exception); }
    }

    public void importProgram(AiProgram program) {
        metadata = program.copy(Platform.getFrozenRegistry());
        deleteVariableDeclarations(getGraphVariableModels().stream().filter(Objects::nonNull).toList(), true);
        variablePresentation.clear();
        importVariables();
        Map<String, AiNodeModel> models = new HashMap<>();
        for (AiNodeData data : program.nodes) {
            AiNodeModel node = add(data.type, new Vector2f(), UUID.fromString(data.id));
            node.data.deserializeNBT(Platform.getFrozenRegistry(), data.serializeNBT(Platform.getFrozenRegistry()));
            node.defineNode();
            models.put(data.id, node);
        }
        for (AiNodeData data : program.nodes) for (String id : data.children) {
            var parent = models.get(data.id);
            var child = models.get(id);
            if (child != null) createWire(child.getInputsById().get("parent"), parent.getOutputsById().get("children"), null);
        }
        for (AiNodeData data : program.nodes) if (!data.onHit.isEmpty()) {
            var child = models.get(data.onHit);
            if (child != null) createWire(child.getInputsById().get("parent"), models.get(data.id).getOutputsById().get("hit"), null);
        }
        arrange();
    }

    @Override public void deserializeAdditionalNBT(Tag tag, HolderLookup.Provider provider) {
        super.deserializeAdditionalNBT(tag, provider);
        getGraphVariableModels().stream().filter(Objects::nonNull).forEach(this::refreshVariableTips);
    }

    private void importVariables() {
        metadata.parameters.forEach(v -> addVariable(v, "parameter"));
        metadata.variables.forEach(v -> addVariable(v, metadata.kind.equals("tree") ? "blackboard" : "local"));
        metadata.npcVariables.forEach(v -> addVariable(v, "blackboard"));
        metadata.parameters.clear(); metadata.variables.clear(); metadata.npcVariables.clear();
    }

    public VariableDeclarationModelBase addVariable(AiVariable variable, String scope) {
        var id = UUID.fromString(variable.id);
        if (getModel(id) instanceof VariableDeclarationModelBase existing) return existing;
        var declaration = createGraphVariableDeclaration(AiVariables.type(variable.initial.type), variable.name,
                scope.equals("parameter") ? ModifierFlags.READ : ModifierFlags.NONE,
                scope.equals("blackboard") ? VariableScope.EXPOSED : VariableScope.LOCAL,
                null, Integer.MAX_VALUE, null, id, null);
        declaration.getInitializationModel().setValue(AiVariables.value(variable.initial));
        var presentation = new AiVariablePresentation();
        presentation.displayNameKey = variable.displayNameKey;
        presentation.tipsKey = variable.tipsKey;
        variablePresentation.put(variable.id, presentation);
        refreshVariableTips(declaration);
        return declaration;
    }

    void refreshVariableTips(VariableDeclarationModelBase declaration) {
        var variable = AiVariables.compile(declaration);
        declaration.setTooltips(com.lowdragmc.lowdraglib2.gui.ui.data.Tooltips.of(AiVariables.displayName(variable))
                .merge(AiVariables.tips(variable)));
    }

    @Override public CopyPasteData copyElements(List<? extends GraphElementModel> elements, HolderLookup.Provider provider) {
        var data = super.copyElements(elements, provider);
        var presentation = new CompoundTag();
        for (var element : elements) if (element instanceof VariableNodeModel variable) {
            var id = variable.getDeclarationModelUid();
            if (id != null && variablePresentation.containsKey(id.toString()))
                presentation.put(id.toString(), variablePresentation.get(id.toString()).serializeNBT(provider));
        }
        data.tag().put("vsnVariablePresentation", presentation);
        return data;
    }

    @Override public PasteResult pasteElementsWithMap(CopyPasteData data, Vector2f positionOffset) {
        var presentation = data.tag().getCompound("vsnVariablePresentation");
        for (var id : presentation.getAllKeys()) if (getModel(UUID.fromString(id)) == null) {
            var value = new AiVariablePresentation();
            value.deserializeNBT(data.provider(), presentation.getCompound(id));
            variablePresentation.put(id, value);
        }
        var result = super.pasteElementsWithMap(data, positionOffset);
        getGraphVariableModels().stream().filter(Objects::nonNull).forEach(this::refreshVariableTips);
        return result;
    }

    public AiProgram declarations() {
        var program = new AiProgram(); program.kind = metadata.kind;
        program.displayNameKey = metadata.displayNameKey;
        for (var declaration : getGraphVariableModels()) {
            // LDLib2 删除声明后保留 null 占位，编译时只处理仍然存在的变量。
            if (declaration == null) continue;
            var variable = AiVariables.compile(declaration);
            switch (AiVariables.scope(declaration, metadata.kind)) {
                case "parameter" -> program.parameters.add(variable);
                case "blackboard" -> (metadata.kind.equals("tree") ? program.variables : program.npcVariables).add(variable);
                default -> program.variables.add(variable);
            }
        }
        return program;
    }

    public List<AiOperations.Field> fields(AiNodeModel node) {
        var fields = new ArrayList<>(AiOperations.fields(node.data, declarations()));
        if (node.operation().equals("call")) {
            var action = ((AiGraph) getGraph()).actionResolver.apply(node.data.literal("file", AiValue.text("")).text);
            if (action != null) action.parameters.forEach(v -> fields.add(new AiOperations.Field("param:" + v.id, v.initial)));
        }
        return fields;
    }

    public AiBinding wiredBinding(AiNodeModel node, String field) {
        var port = node.getInputsById().get("value_" + field);
        if (port == null || port.getConnectedWires().isEmpty()) return null;
        var wires = port.getConnectedWires();
        if (wires.size() != 1 || wires.getFirst().getFromPort() == null) return invalidBinding();
        var source = wires.getFirst().getFromPort();
        if (source.getPortType() == PortType.MISSING_PORT || !port.getDataTypeHandle().equals(source.getDataTypeHandle()))
            return invalidBinding();
        if (source.getNodeModel() instanceof VariableNodeModel variable && variable.getVariableDeclarationModel() != null) {
            var declaration = variable.getVariableDeclarationModel();
            if (getModel(declaration.getUid()) != declaration) return invalidBinding();
            var binding = new AiBinding();
            binding.scope = AiVariables.scope(declaration, metadata.kind);
            binding.variableId = declaration.getUid().toString();
            binding.value = AiVariables.compile(declaration).initial;
            return binding;
        }
        if (source.getNodeModel() instanceof ConstantNodeModel constant && constant.getConstant() != null
                && !constant.getConstant().isDeserializeFailed()) {
            var value = new AiValue();
            var raw = constant.getConstant().getValue();
            var expected = fields(node).stream().filter(f -> f.id().equals(field)).findFirst().orElseThrow().initial();
            value.type = expected.type;
            if (expected.type.equals("number") && raw instanceof Number number) value.number = number.doubleValue();
            else if (expected.type.equals("boolean") && raw instanceof Boolean flag) value.flag = flag;
            else if (expected.type.equals("position") && raw instanceof net.minecraft.world.phys.Vec3 position) value.position = position;
            else if (expected.type.equals("entity") && raw instanceof AiEntityRef entity) value.entity = entity.copy();
            else if (expected.textual() && raw instanceof String text) value.text = text;
            else if (raw instanceof AiEntityRef ref) value.entity = ref.copy();
            else return invalidBinding();
            return AiBinding.literal(value);
        }
        return invalidBinding();
    }

    private static AiBinding invalidBinding() { var binding = new AiBinding(); binding.scope = "invalid"; return binding; }

    public boolean usesEntityTypeFilter(AiNodeModel node) {
        return AiOperations.usesEntityTypeFilter(node.data) || wiredBinding(node, "filter") != null;
    }

    @Override public NodeModel createNodeModel(Node node, Vector2f position) {
        return createNodeWithType(AiNodeModel.class, "", position, null, model -> {
            model.initCustomNode(node);
            model.data.type = ((AiGraphNode) node).operation;
            model.data.id = model.getUid().toString();
        }, null);
    }
    @Override protected AbstractNodeModel createNodeFromDiscriminator(String type) {
        return type.equals("custom") ? new AiNodeModel() : super.createNodeFromDiscriminator(type);
    }

    public List<AiNodeModel> children(AiNodeModel parent) { return children(parent, "children"); }
    private List<AiNodeModel> allChildren(AiNodeModel parent) {
        var all = new ArrayList<>(children(parent)); all.addAll(children(parent, "hit")); return all;
    }
    private List<AiNodeModel> children(AiNodeModel parent, String port) {
        List<AiNodeModel> children = new ArrayList<>();
        for (WireModel wire : getWireModels()) if (wire != null && wire.getFromPort() != null && wire.getToPort() != null
                && wire.getFromPort().getDataTypeHandle().equals(TypeHandles.EXECUTION_FLOW)
                && wire.getFromPort().getNodeModel() == parent && wire.getFromPort().getUniqueName().equals(port) && wire.getToPort().getNodeModel() instanceof AiNodeModel child)
            children.add(child);
        children.sort(Comparator.comparingInt(child -> {
            int index = parent.data.children.indexOf(child.getUid().toString());
            return index < 0 ? Integer.MAX_VALUE : index;
        }));
        return children;
    }

    private boolean descends(AiNodeModel node, AiNodeModel search, Set<AiNodeModel> visited) {
        if (node == search) return true;
        if (!visited.add(node)) return false;
        return allChildren(node).stream().anyMatch(child -> descends(child, search, visited));
    }

    @Override public boolean canAssignTo(PortModel destination, PortModel source) {
        if (destination == null || source == null) return false;
        if (destination.getDirection() == PortDirection.OUTPUT) { var swap = destination; destination = source; source = swap; }
        if (destination.getDirection() != PortDirection.INPUT || source.getDirection() != PortDirection.OUTPUT) return false;
        if (!destination.getDataTypeHandle().equals(TypeHandles.EXECUTION_FLOW))
            return destination.getNodeModel() instanceof AiNodeModel && destination.getUniqueName().startsWith("value_")
                    && destination.getConnectedWires().isEmpty() && source.getPortType() != PortType.MISSING_PORT
                    && destination.getDataTypeHandle().equals(source.getDataTypeHandle())
                    && (source.getNodeModel() instanceof VariableNodeModel || source.getNodeModel() instanceof ConstantNodeModel)
                    && super.canAssignTo(destination, source);
        if (!(destination.getNodeModel() instanceof AiNodeModel child) || !(source.getNodeModel() instanceof AiNodeModel parent)) return false;
        if (child.operation().equals("root") || descends(child, parent, new HashSet<>())) return false;
        if (!destination.getConnectedWires().isEmpty()) return false;
        var operation = AiOperations.get(parent.operation());
        return operation != null && (source.getUniqueName().equals("hit") ? AiOperations.hasHitBranch(parent.operation()) && children(parent, "hit").isEmpty()
                : source.getUniqueName().equals("children") && children(parent).size() < operation.maxChildren());
    }

    @Override public WireModel createWire(PortModel to, PortModel from, UUID uid) {
        if (to != null && to.getDirection() == PortDirection.OUTPUT) { var swap = to; to = from; from = swap; }
        if (!canAssignTo(to, from)) return null;
        var ordered = from.getNodeModel() instanceof AiNodeModel parent ? children(parent).stream()
                .map(child -> child.getUid().toString()).collect(java.util.stream.Collectors.toCollection(ArrayList::new)) : null;
        WireModel wire = super.createWire(to, from, uid);
        if (wire != null && from.getDataTypeHandle().equals(TypeHandles.EXECUTION_FLOW) && from.getNodeModel() instanceof AiNodeModel parent) {
            if (from.getUniqueName().equals("hit")) parent.data.onHit = to.getNodeModel().getUid().toString();
            else {
                ordered.add(to.getNodeModel().getUid().toString());
                parent.data.children = ordered;
            }
        }
        return wire;
    }

    public AiProgram compile() {
        AiProgram program = declarations();
        AiNodeModel root = getNodeModels().stream().filter(n -> n instanceof AiNodeModel a && a.operation().equals("root"))
                .map(n -> (AiNodeModel) n).findFirst().orElse(null);
        if (root != null) { program.root = root.getUid().toString(); compile(root, program, new HashSet<>()); }
        return program;
    }

    private void compile(AiNodeModel model, AiProgram program, Set<UUID> visited) {
        if (!visited.add(model.getUid())) return;
        AiNodeData data = new AiNodeData();
        data.deserializeNBT(Platform.getFrozenRegistry(), model.data.serializeNBT(Platform.getFrozenRegistry()));
        data.id = model.getUid().toString();
        data.type = model.operation();
        fields(model).forEach(field -> {
            var binding = wiredBinding(model, field.id());
            if (binding != null) data.inputs.put(field.id(), binding);
        });
        for (var port : model.getInputsById().values()) if (port.getUniqueName().startsWith("value_")
                && port.getPortType() == PortType.MISSING_PORT && !port.getConnectedWires().isEmpty())
            data.inputs.put(port.getUniqueName().substring(6), invalidBinding());
        data.children = children(model).stream().map(n -> n.getUid().toString()).collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        data.onHit = children(model, "hit").stream().map(n -> n.getUid().toString()).findFirst().orElse("");
        program.nodes.add(data);
        allChildren(model).forEach(child -> compile(child, program, visited));
    }

    public void arrange() {
        float[] next = {0};
        Set<UUID> visited = new HashSet<>();
        for (var node : getNodeModels()) if (node instanceof AiNodeModel a && a.operation().equals("root")) {
            Vector2f anchor = new Vector2f(a.getPosition());
            arrange(a, 0, next, visited);
            Vector2f offset = anchor.sub(a.getPosition());
            var all = descendants(a); all.add(a);
            all.forEach(n -> n.setPosition(n.getPosition().add(offset, new Vector2f())));
        }
    }

    public Set<AiNodeModel> descendants(AiNodeModel root) {
        Set<AiNodeModel> found = new LinkedHashSet<>();
        collectDescendants(root, found); found.remove(root); return found;
    }
    private void collectDescendants(AiNodeModel root, Set<AiNodeModel> found) {
        if (!found.add(root)) return;
        allChildren(root).forEach(child -> collectDescendants(child, found));
    }

    float layoutHeight(AiNodeModel node) {
        if (node.isCollapsed()) return 32;
        long inputs = node.getInputsById().values().stream().filter(p -> p.getOrientation() == PortOrientation.Horizontal)
                .filter(p -> !p.getUniqueName().equals("value_entity_type") || usesEntityTypeFilter(node)).count();
        return 58 + inputs * 20 + (Set.of("stage", "call").contains(node.operation()) ? 14 : 0);
    }

    private float arrange(AiNodeModel node, float y, float[] next, Set<UUID> visited) {
        if (!visited.add(node.getUid())) return node.getPosition().x;
        var children = allChildren(node);
        float x;
        if (children.isEmpty() || node.isCollapsed()) { x = next[0]; next[0] += 180; }
        else {
            float childY = y + layoutHeight(node) + 30;
            float first = arrange(children.getFirst(), childY, next, visited), last = first;
            for (int i = 1; i < children.size(); i++) last = arrange(children.get(i), childY, next, visited);
            x = (first + last) / 2;
        }
        node.setPosition(new Vector2f(x, y));
        return x;
    }

}
