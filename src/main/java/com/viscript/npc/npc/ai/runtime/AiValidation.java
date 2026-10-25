package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.ai.data.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.*;
import java.util.function.Function;

/** 在加载、导出和发布之前验证服务端语义，不执行图中动作。 */
public final class AiValidation {
    public record Issue(String nodeId, String key, String detail) {}
    private static final Set<String> TYPES = Set.of("number", "boolean", "string", "position", "entity", "mob_type");

    public static void requireValid(AiProgram program, Function<String, AiProgram> actions) {
        var issues = validate(program, actions);
        if (!issues.isEmpty()) {
            var issue = issues.getFirst();
            throw new IllegalArgumentException(net.minecraft.network.chat.Component.translatable(
                    "viscript_npc.ai.error." + issue.key(), issue.detail()).getString() + " [" + issue.nodeId() + "]");
        }
    }

    public static List<Issue> validate(AiProgram program, Function<String, AiProgram> actions) {
        return validate(program, new Dependencies(actions), false);
    }

    /** 只验证单个定义的结构；文件加载不递归读取依赖，完整校验由发布及调用入口负责。 */
    public static List<Issue> validateDefinition(AiProgram program) {
        return validate(program, new Dependencies(ignored -> null), true);
    }

    private static final class Dependencies {
        final Function<String, AiProgram> resolver;
        final Map<String, AiProgram> definitions = new HashMap<>();
        final Map<String, List<Issue>> checked = new HashMap<>();
        final Set<String> path = new LinkedHashSet<>();
        Dependencies(Function<String, AiProgram> resolver) { this.resolver = resolver; }
        AiProgram resolve(String id) {
            if (!definitions.containsKey(id)) {
                try { definitions.put(id, resolver.apply(id)); }
                catch (IllegalArgumentException exception) { definitions.put(id, null); }
            }
            return definitions.get(id);
        }
    }

    private static List<Issue> validate(AiProgram program, Dependencies dependencies, boolean definitionOnly) {
        List<Issue> issues = new ArrayList<>();
        if (program == null) { issues.add(new Issue("", "missing_action", "")); return issues; }
        if (program.version != 1 || !Set.of("tree", "action").contains(program.kind)) {
            issues.add(new Issue("", "version", String.valueOf(program.version))); return issues;
        }
        if (program.nodes.size() > 512) { issues.add(new Issue("", "limit", "512")); return issues; }
        for (var declarations : List.of(program.variables, program.parameters, program.npcVariables)) {
            Set<String> ids = new HashSet<>();
            if (declarations.size() > 128) issues.add(new Issue("", "limit", "128 variables"));
            for (var v : declarations) {
                if (v.id.isBlank() || !ids.add(v.id)) issues.add(new Issue("", "duplicate", v.name));
                if (!validValue(v.initial)) issues.add(new Issue("", "type", v.name));
            }
        }
        if (program.root.isEmpty() && program.kind.equals("tree") && program.nodes.isEmpty()) return issues;
        Map<String, AiNodeData> nodes = new LinkedHashMap<>();
        for (AiNodeData node : program.nodes) {
            if (node.id.isBlank() || nodes.putIfAbsent(node.id, node) != null) issues.add(new Issue(node.id, "duplicate", node.id));
        }
        if (!nodes.containsKey(program.root) || !nodes.get(program.root).type.equals("root")
                || program.nodes.stream().filter(n -> n.type.equals("root")).count() != 1) {
            issues.add(new Issue("", "root", "")); return issues;
        }
        Map<String, String> parents = new HashMap<>();
        walk(program.root, nodes, program, new HashSet<>(), parents, issues, dependencies, definitionOnly, 0);
        if (parents.size() + 1 != nodes.size()) issues.add(new Issue("", "root", "unreachable runtime nodes"));
        return issues;
    }

    private static boolean validValue(AiValue value) {
        var choices = AiValue.choices(value.type);
        return (TYPES.contains(value.type) || !choices.isEmpty()) && validRef(value) && Double.isFinite(value.number)
                && Double.isFinite(value.position.lengthSqr()) && value.text.length() <= 2048
                && (choices.isEmpty() || choices.contains(value.text));
    }

    private static boolean validRef(AiValue value) {
        if (!value.type.equals("entity")) return true;
        if (value.entity.uuid.isEmpty()) return value.entity.dimension.isEmpty();
        try { java.util.UUID.fromString(value.entity.uuid); return ResourceLocation.tryParse(value.entity.dimension) != null; }
        catch (IllegalArgumentException exception) { return false; }
    }
    /** 按字段声明检查实际输入值的类型、选项和取值范围。 */
    public static String valueIssue(AiOperations.Field field, AiValue value) {
        String expected = field.initial().type;
        if (value == null || !validValue(value) || !value.type.equals(expected)) return "type";
        var choices = field.choices().isEmpty() ? AiValue.choices(expected) : field.choices();
        if (!choices.isEmpty() && !choices.contains(value.text)) return "choice";
        if (expected.equals("number") && field.id().equals("max_health_ratio") && (value.number < 0 || value.number > 1)) return "number";
        if (expected.equals("number") && field.id().equals("view_angle") && (value.number < 0 || value.number > 360)) return "number";
        if (expected.equals("number") && field.id().equals("view_radius") && (value.number < 0 || value.number > 64)) return "number";
        if (expected.equals("number") && Set.of("seconds", "range", "damage", "speed", "count", "knockback", "strength", "min_distance", "max_distance", "height", "patrol_radius", "pause_seconds").contains(field.id())
                && (value.number < 0 || value.number > 72000)) return "number";
        return validResource(expected.equals("mob_type") ? "entity_type" : field.id(), value.text) ? null : "resource";
    }

    private static void binding(AiBinding binding, AiOperations.Field field, AiProgram program, String id, List<Issue> issues) {
        String expected = field.initial().type;
        if (binding.scope.equals("literal")) {
            String issue = valueIssue(field, binding.value);
            if (issue != null) issues.add(new Issue(id, issue, field.id()));
        } else if (binding.scope.equals("context")) {
            if (!expected.equals("entity") || !Set.of("self", "target", "hit", "source").contains(binding.variableId))
                issues.add(new Issue(id, "binding", field.id()));
        } else {
            List<AiVariable> declarations = switch (binding.scope) {
                case "parameter" -> program.parameters;
                case "local" -> program.kind.equals("action") ? program.variables : List.of();
                case "blackboard" -> program.kind.equals("tree") ? program.variables : program.npcVariables;
                default -> List.of();
            };
            var variable = declarations.stream().filter(v -> v.id.equals(binding.variableId)).findFirst();
            if (variable.isEmpty() || !variable.get().initial.type.equals(expected)) issues.add(new Issue(id, "binding", field.id()));
            else {
                String issue = valueIssue(field, variable.get().initial);
                if (issue != null) issues.add(new Issue(id, issue, field.id()));
            }
        }
    }

    private static boolean validResource(String key, String value) {
        if (!Set.of("item", "sound", "effect", "particle", "entity_type").contains(key)) return true;
        if (key.equals("entity_type") && value.isBlank()) return true;
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) return false;
        return switch (key) {
            case "item" -> BuiltInRegistries.ITEM.containsKey(id);
            case "sound" -> BuiltInRegistries.SOUND_EVENT.containsKey(id);
            case "effect" -> BuiltInRegistries.MOB_EFFECT.containsKey(id);
            case "entity_type" -> BuiltInRegistries.ENTITY_TYPE.containsKey(id);
            default -> BuiltInRegistries.PARTICLE_TYPE.containsKey(id)
                    && BuiltInRegistries.PARTICLE_TYPE.get(id) instanceof net.minecraft.core.particles.SimpleParticleType;
        };
    }

    private static void walk(String id, Map<String, AiNodeData> nodes, AiProgram program, Set<String> path,
                             Map<String, String> parents, List<Issue> issues, Dependencies dependencies, boolean definitionOnly, int depth) {
        AiNodeData node = nodes.get(id);
        if (node == null) { issues.add(new Issue(id, "missing_node", id)); return; }
        if (depth > 64 || !path.add(id)) { issues.add(new Issue(id, "cycle", id)); return; }
        var operation = AiOperations.get(node.type);
        if (!AiOperations.supports(program.kind, node.type)) { issues.add(new Issue(id, "unknown_node", node.type)); path.remove(id); return; }
        node.inputs.forEach((key, input) -> {
            if (input.scope.equals("invalid")) issues.add(new Issue(id, "binding", key));
        });
        if (node.children.size() > operation.maxChildren()
                || (operation.maxChildren() == 1 && node.children.size() != 1 && !(node.type.equals("root") && program.kind.equals("tree")))
                || (node.type.equals("branch") && node.children.size() != 2)) issues.add(new Issue(id, "children", node.type));
        for (var field : AiOperations.fields(node, program)) {
            if (AiOperations.isPerception(node.type) && field.id().equals("entity_type") && !AiOperations.usesEntityTypeFilter(node)) continue;
            AiBinding value = node.inputs.get(field.id());
            if (value != null) binding(value, field, program, id, issues);
        }
        if (Set.of("find_target", "get_entity", "entity_info").contains(node.type)) {
            String output = node.literal("output", AiValue.text("")).text;
            if (!output.isEmpty() && program.variables.stream().noneMatch(v -> v.id.equals(output) && v.initial.type.equals(AiOperations.outputType(node))))
                issues.add(new Issue(id, "binding", "output"));
        }
        if (node.type.equals("entity_info") && node.inputs.containsKey("property") && !node.inputs.get("property").scope.equals("literal"))
            issues.add(new Issue(id, "binding", "property"));
        if (!node.onHit.isEmpty() && !AiOperations.hasHitBranch(node.type)) issues.add(new Issue(id, "children", "on_hit"));
        if (node.type.equals("position") || node.type.equals("set_variable")) {
            String field = node.type.equals("position") ? "output" : "variable";
            var declarations = node.type.equals("set_variable") && node.literal("scope", AiValue.text("local")).text.equals("blackboard")
                    ? program.npcVariables : program.variables;
            var variable = declarations.stream().filter(v -> v.id.equals(node.literal(field, AiValue.text("")).text)).findFirst();
            if (variable.isEmpty() || (field.equals("output") && !variable.get().initial.type.equals("position")))
                issues.add(new Issue(id, "binding", field));
        }
        if (node.type.equals("call") && !definitionOnly) {
            AiBinding fileBinding = node.inputs.get("file");
            if (fileBinding != null && !fileBinding.scope.equals("literal")) issues.add(new Issue(id, "binding", "file"));
            String file = node.literal("file", AiValue.text("")).text;
            AiProgram action = dependencies.resolve(file);
            if (action == null || !action.kind.equals("action")) issues.add(new Issue(id, "missing_action", file));
            else {
                for (String key : node.inputs.keySet()) if (key.startsWith("param:") && action.parameters.stream().noneMatch(v -> key.equals("param:" + v.id)))
                    issues.add(new Issue(id, "binding", key));
                for (AiVariable parameter : action.parameters) {
                    AiBinding value = node.inputs.get("param:" + parameter.id);
                    if (value != null) binding(value, new AiOperations.Field("param:" + parameter.id, parameter.initial), program, id, issues);
                }
                for (AiVariable required : action.npcVariables) {
                    if ((program.kind.equals("tree") ? program.variables : program.npcVariables).stream().noneMatch(v -> v.id.equals(required.id) && v.initial.type.equals(required.initial.type)))
                        issues.add(new Issue(id, "binding", required.name));
                }
                if (dependencies.path.contains(file)) {
                    issues.add(new Issue(id, "cycle", String.join(" → ", dependencies.path) + " → " + file));
                } else if (dependencies.path.size() >= 32 || dependencies.definitions.size() > 128) {
                    issues.add(new Issue(id, "limit", "32 call levels / 128 actions"));
                } else {
                    var nested = dependencies.checked.get(file);
                    if (nested == null) {
                        dependencies.path.add(file);
                        nested = validate(action, dependencies, false);
                        dependencies.path.remove(file);
                        dependencies.checked.put(file, nested);
                    }
                    for (var issue : nested) issues.add(new Issue(id, issue.key(),
                            file + " [" + issue.nodeId() + "]: " + issue.detail()));
                }
            }
        }
        var edges = new ArrayList<>(node.children); if (!node.onHit.isEmpty()) edges.add(node.onHit);
        for (String child : edges) {
            if (parents.putIfAbsent(child, id) != null) issues.add(new Issue(child, "parent", child));
            else walk(child, nodes, program, path, parents, issues, dependencies, definitionOnly, depth + 1);
        }
        path.remove(id);
    }
}
