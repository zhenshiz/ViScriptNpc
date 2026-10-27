package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.ai.data.AiValue;
import com.viscript.npc.npc.ai.data.AiNodeData;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 节点编译和编辑器共用的参数定义；不引用任何客户端类型。 */
public final class AiOperations {
    public record Field(String id, AiValue initial, List<String> choices) {
        public Field(String id, AiValue initial) { this(id, initial, List.of()); }
    }
    public record Operation(String id, String group, int maxChildren, List<Field> fields) {}
    private static final Map<String, Operation> OPERATIONS = new LinkedHashMap<>();
    static {
        add("root", "flow", 1);
        add("sequence", "flow", 64);
        add("selector", "flow", 64);
        add("reactive_sequence", "flow", 64);
        add("reactive_selector", "flow", 64);
        add("parallel", "flow", 64, choice("policy", "all_success", "any_success"));
        add("weighted_selector", "flow", 64);
        add("weight", "flow", 1, number("weight", 1));
        add("target_exists", "condition", 0);
        add("target_distance", "condition", 0, number("min_distance", 0), number("max_distance", 32));
        add("target_sight", "condition", 0);
        add("self_health", "condition", 0, number("max_health_ratio", .5));
        add("number_compare", "condition", 0, number("left", 0), choice("compare", "equal", "less", "greater"), number("right", 0));
        add("chance", "condition", 0, number("probability", .5));
        add("returning_home", "condition", 0);
        add("home_distance", "condition", 0, number("min_distance", 0), number("max_distance", 32));
        var detectionFields = new java.util.ArrayList<>(List.of(perceptionFields(true)));
        detectionFields.replaceAll(field -> field.id().equals("filter")
                ? new Field("filter", AiValue.choice("player", field.choices()), field.choices()) : field);
        add("detect_target", "condition", 0, detectionFields.toArray(Field[]::new));
        add("cooldown", "flow", 1, number("seconds", 2));
        add("sense_targets", "tree", 1, perceptionFields(true));
        var guardFields = new java.util.ArrayList<>(List.of(perceptionFields(true)));
        guardFields.addAll(List.of(number("leash_range", 24), number("memory_seconds", 3), number("home_range", .8), flag("retaliate", true)));
        add("guard_area", "tree", 1, guardFields.toArray(Field[]::new));
        add("timeout", "flow", 1, number("seconds", 10));
        add("invert", "flow", 1);
        add("repeat", "flow", 1, number("count", 2));
        add("until_success", "flow", 1);
        add("until_failure", "flow", 1);
        add("force_success", "flow", 1);
        add("force_failure", "flow", 1);
        add("call", "flow", 0, text("file", ""), choice("target_binding", "current", "entity"), text("target_entity", ""));
        add("stage", "action", 64, text("name", ""), flag("interruptible", true));
        add("branch", "action", 2, flag("value", true));
        add("wait", "action", 0, number("seconds", 1));
        add("wait_ground", "action", 0, number("seconds", 3));
        var findFields = new java.util.ArrayList<>(List.of(perceptionFields(false)));
        findFields.add(flag("set_current_target", true));
        findFields.add(text("output", ""));
        add("find_target", "action", 0, findFields.toArray(Field[]::new));
        movement("patrol", number("patrol_radius", 6), number("speed", .5), number("seconds", 20), number("pause_seconds", 1));
        add("set_target", "action", 0, text("entity", ""), entity("recipient"));
        add("get_entity", "action", 0, entity("recipient"), text("output", ""));
        add("entity_info", "action", 0, entity("recipient"), choice("property", "health", "max_health", "health_ratio", "alive", "position", "entity_type"), text("output", ""));
        add("clear_target", "action", 0);
        add("position", "action", 0, choice("origin", "target", "self", "fixed"),
                new Field("position", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)),
                number("forward", 0), number("right", 0), number("up", 0), text("output", "landing"));
        movement("navigate", new Field("position", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)),
                number("speed", .5), number("range", .75), number("seconds", 30));
        movement("approach", number("speed", .5), number("range", 2), number("seconds", 30), entity("recipient"));
        movement("retreat", number("speed", .5), number("range", 6), number("seconds", 30), entity("recipient"));
        movement("keep_distance", number("speed", .5), number("min_distance", 4), number("max_distance", 10), number("seconds", 30), entity("recipient"));
        movement("return_home", number("speed", .5), number("range", .8), number("seconds", 30));
        add("set_home", "action", 0, flag("use_current", true), new Field("position", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)));
        add("home_position", "action", 0, text("output", ""));
        add("stop", "action", 0);
        add("look", "action", 0, entity("recipient"));
        add("jump", "action", 0, new Field("position", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)), number("range", 4));
        add("melee", "action", 0, number("damage", 2), number("range", 2), number("knockback", .2), entity("recipient"));
        add("ranged", "action", 0, number("damage", 2), number("range", 16), number("speed", 1.6), text("item", "minecraft:arrow"), entity("recipient"));
        add("area_damage", "action", 0, number("damage", 4), number("range", 3), number("knockback", .4));
        add("knockback", "action", 0, number("strength", .5), entity("recipient"));
        add("effect", "action", 0, text("effect", "minecraft:slowness"), number("seconds", 3), number("amplifier", 0), entity("recipient"));
        add("swing", "action", 0);
        add("particle", "action", 0, text("particle", "minecraft:crit"), number("count", 12), number("spread", .5), entity("recipient"));
        add("sound", "action", 0, text("sound", "minecraft:entity.player.attack.strong"), number("volume", 1), number("pitch", 1), entity("recipient"));
        add("set_variable", "action", 0, choice("scope", "local", "blackboard"), text("variable", ""), number("value", 0));
        add("number_math", "action", 0, number("left", 0), choice("operator", "add", "subtract", "multiply", "divide", "modulo", "minimum", "maximum"), number("right", 0), text("output", ""));
        add("vector_math", "action", 0, new Field("vector_left", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)),
                choice("vector_operator", "add", "subtract", "scale", "normalize", "dot", "distance", "length"),
                new Field("vector_right", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)), number("factor", 1), text("output", ""));
        add("random_number", "action", 0, number("minimum", 0), number("maximum", 1), flag("integer", false), text("output", ""));
        add("debug_log", "debug", 0, text("message", ""), number("value", 0));
        add("debug_line", "debug", 0, new Field("from", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)),
                new Field("to", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)), number("seconds", 1));
        add("debug_ray", "debug", 0, new Field("from", AiValue.position(net.minecraft.world.phys.Vec3.ZERO)),
                new Field("direction", AiValue.position(new net.minecraft.world.phys.Vec3(0, 0, 1))), number("seconds", 1));
        add("success", "flow", 0);
        add("failure", "flow", 0);
        add("finish_success", "action", 0);
        add("finish_failure", "action", 0);
    }
    public static boolean hasHitBranch(String type) { return List.of("melee", "ranged", "area_damage").contains(type); }
    public static String outputType(AiNodeData node) {
        if (node.type.equals("position") || node.type.equals("home_position")) return "position";
        if (node.type.equals("number_math") || node.type.equals("random_number")) return "number";
        if (node.type.equals("vector_math")) return List.of("dot", "distance", "length").contains(node.literal("vector_operator", AiValue.text("add")).text) ? "number" : "position";
        if (node.type.equals("entity_info")) return switch (node.literal("property", AiValue.text("health")).text) {
            case "alive" -> "boolean"; case "position" -> "position"; case "entity_type" -> "string"; default -> "number";
        };
        return "entity";
    }
    private static Field[] perceptionFields(boolean continuous) {
        return new Field[]{new Field("perception", AiValue.choice(continuous ? "combined" : "range", List.of("range", "view", "combined")), List.of("range", "view", "combined")),
                number("range", continuous ? 3 : 24), number("view_radius", 16), number("view_angle", 90), number("height", 6),
                flag("require_sight", continuous), choice("filter", "hostile", "player", "living"), new Field("entity_type", AiValue.mobType(""))};
    }
    public static boolean isPerception(String type) {
        return type.equals("find_target") || isContinuousPerception(type);
    }
    public static boolean isContinuousPerception(String type) {
        return type.equals("sense_targets") || type.equals("detect_target") || type.equals("guard_area");
    }
    public static boolean hasOutput(String type) {
        return List.of("position", "find_target", "get_entity", "entity_info", "home_position", "number_math", "vector_math", "random_number").contains(type);
    }
    public static boolean isMovement(String type) {
        return List.of("navigate", "approach", "retreat", "keep_distance", "return_home", "jump", "patrol").contains(type);
    }
    private static void movement(String id, Field... fields) {
        var result = new java.util.ArrayList<>(List.of(fields));
        result.addAll(List.of(flag("open_doors", false), flag("swim", false), flag("climb", false)));
        add(id, "action", 0, result.toArray(Field[]::new));
    }
    private static Field entity(String key) { return new Field(key, AiValue.entity(new com.viscript.npc.npc.ai.data.AiEntityRef())); }
    private static Field number(String key, double value) { return new Field(key, AiValue.number(value)); }
    private static Field text(String key, String value) { return new Field(key, AiValue.text(value)); }
    private static Field flag(String key, boolean value) { return new Field(key, AiValue.bool(value)); }
    private static Field choice(String key, String... values) { return new Field(key, AiValue.choice(values[0], List.of(values)), List.of(values)); }
    private static void add(String id, String group, int children, Field... fields) {
        OPERATIONS.put(id, new Operation(id, group, children, List.of(fields)));
    }
    public static Operation get(String id) { return OPERATIONS.get(id); }
    public static boolean hasDataInput(Field field) {
        return !java.util.Set.of("file", "variable", "output", "scope", "property", "vector_operator").contains(field.id());
    }
    /**
     * 判断选敌节点是否可能使用实体类型筛选；动态选敌模式在运行时决定是否生效。
     *
     * @param node 待检查的节点
     * @return 固定为可攻击生物或使用动态模式绑定时返回 {@code true}
     */
    public static boolean usesEntityTypeFilter(AiNodeData node) {
        if (!isPerception(node.type)) return false;
        var filter = node.inputs.get("filter");
        return filter != null && (!filter.scope.equals("literal") || filter.value.text.equals("living"));
    }
    public static List<Field> fields(com.viscript.npc.npc.ai.data.AiNodeData node, com.viscript.npc.npc.ai.data.AiProgram program) {
        Operation operation = get(node.type);
        if (operation == null) return List.of();
        if (node.type.equals("debug_log")) {
            var binding = node.inputs.get("value");
            if (binding != null) {
                var declarations = switch (binding.scope) {
                    case "local" -> program.variables;
                    case "blackboard" -> program.kind.equals("tree") ? program.variables : program.npcVariables;
                    case "parameter" -> program.parameters;
                    default -> List.<com.viscript.npc.npc.ai.data.AiVariable>of();
                };
                var initial = declarations.stream().filter(v -> v.id.equals(binding.variableId)).map(v -> v.initial).findFirst()
                        .orElse(binding.value);
                return operation.fields().stream().map(f -> f.id().equals("value") ? new Field("value", initial) : f).toList();
            }
        }
        if (!node.type.equals("set_variable")) return operation.fields();
        var declarations = node.literal("scope", AiValue.text("local")).text.equals("blackboard") ? program.npcVariables : program.variables;
        var variable = declarations.stream().filter(v -> v.id.equals(node.literal("variable", AiValue.text("")).text)).findFirst();
        return operation.fields().stream().map(field -> field.id().equals("value") && variable.isPresent()
                ? new Field("value", variable.get().initial.copy()) : field).toList();
    }
    public static synchronized void register(Operation operation) {
        if (!operation.group().equals("action") || operation.maxChildren() != 0
                || OPERATIONS.putIfAbsent(operation.id(), operation) != null)
            throw new IllegalArgumentException("Invalid or duplicate atomic action: " + operation.id());
    }
    public static List<Operation> all() { return List.copyOf(OPERATIONS.values()); }
    public static boolean supports(String kind, String id) {
        Operation operation = get(id);
        return operation != null && (operation.group.equals("flow") || operation.group.equals("condition") || operation.group.equals("debug") || operation.group.equals(kind));
    }
}
