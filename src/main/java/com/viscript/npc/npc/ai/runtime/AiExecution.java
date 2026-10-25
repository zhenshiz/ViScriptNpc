package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.data.*;
import net.minecraft.world.entity.LivingEntity;

import java.util.*;
import java.util.function.Function;

/** 单个树或行为实例；所有修改与 tick 必须在服务器线程执行。 */
public final class AiExecution {
    public final CustomNpc npc;
    public final AiProgram program;
    public final Map<String, AiValue> blackboard;
    public final Map<String, AiValue> local = new LinkedHashMap<>();
    public final Map<String, AiValue> parameters = new LinkedHashMap<>();
    private final Map<String, AiNodeData> nodes = new HashMap<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    private final Map<AiProgram, Boolean> validatedActions = new IdentityHashMap<>();
    private final Function<String, AiProgram> actions;
    private final int callDepth;
    private Frame root;
    private long cancelVersion;
    public final Map<String, AiValue> resolvedInputs = new LinkedHashMap<>();
    private final Map<String, AiValue> context = new HashMap<>();
    private int[] budget = {128};
    private AiStatus finished;
    private AiDebugger debugger = new AiDebugger();
    private long executionTicks;
    private boolean evaluating;
    private String actionId = "";
    public void attachDebugger(AiDebugger value) { debugger = value; }
    public long time() { return executionTicks; }
    private static final class NavigationControl { Frame owner; }
    private NavigationControl navigation = new NavigationControl();
    private AiExecution caller;
    private net.minecraft.world.phys.Vec3 patrolOrigin;
    private final Map<String, AiPerceptionVolume> perceptions = new LinkedHashMap<>();
    private final Set<String> livePerceptions = new HashSet<>();

    /** @return 此执行实例首次运行时固定的巡逻中心。 */
    public net.minecraft.world.phys.Vec3 patrolOrigin() {
        if (patrolOrigin == null) patrolOrigin = npc.position();
        return patrolOrigin;
    }
    public void recordPerception(String id, AiPerceptionVolume volume) {
        perceptions.put(id, volume);
        livePerceptions.add(id);
    }
    /** @return 当前持续感知与最近一次单次查询的服务器范围。 */
    public List<AiPerceptionVolume> perceptions() { return List.copyOf(perceptions.values()); }

    public String reason = "";
    public String phase = "";
    public net.minecraft.world.phys.Vec3 expectedLanding;

    public AiExecution(CustomNpc npc, AiProgram program, Map<String, AiValue> blackboard,
                       Map<String, AiValue> inputs, Function<String, AiProgram> actions) {
        this(npc, program, blackboard, inputs, program.kind.equals("tree") ? actions : freeze(actions, npc), 0);
    }

    private static Function<String, AiProgram> freeze(Function<String, AiProgram> resolver, CustomNpc npc) {
        Map<String, AiProgram> definitions = new HashMap<>();
        return id -> {
            if (!definitions.containsKey(id)) {
                var definition = resolver.apply(id);
                definitions.put(id, definition == null ? null : definition.copy(npc.registryAccess()));
            }
            return definitions.get(id);
        };
    }

    private AiExecution(CustomNpc npc, AiProgram program, Map<String, AiValue> blackboard,
                        Map<String, AiValue> inputs, Function<String, AiProgram> actions, int callDepth) {
        this.callDepth = callDepth;
        this.npc = npc;
        this.program = program.copy(npc.registryAccess());
        this.blackboard = blackboard;
        this.actions = actions;
        this.program.nodes.forEach(n -> nodes.put(n.id, n));
        // 禁用分支视为未参与组合；二路分支保留槽位，避免真假出口换位。
        this.program.nodes.forEach(n -> {
            if (!n.type.equals("branch")) n.children.removeIf(id -> nodes.containsKey(id) && nodes.get(id).disabled);
        });
        this.program.variables.forEach(v -> local.put(v.id, v.initial.copy()));
        this.program.parameters.forEach(v -> parameters.put(v.id, inputs.getOrDefault(v.id, v.initial).copy()));
    }

    public AiStatus tick() {
        if (!evaluating) budget[0] = 128;
        try { return tick(budget); }
        catch (InputException exception) {
            cancel(); reason = exception.issue; return AiStatus.FAILURE;
        }
        catch (AiDebugger.Suspended ignored) { return AiStatus.RUNNING; }
        finally { captureValues(); }
    }

    private void captureValues() {
        if (!debugger.enabled) return;
        program.variables.forEach(v -> {
            var value = local.get(v.id);
            if (value != null && debugger.values.size() < 128) debugger.values.put("local:" + v.name, value.copy());
        });
        program.parameters.forEach(v -> {
            var value = parameters.get(v.id);
            if (value != null && debugger.values.size() < 128) debugger.values.put("parameter:" + v.name, value.copy());
        });
        if (root != null) root.captureValues();
    }

    private AiStatus tick(int[] sharedBudget) {
        budget = sharedBudget;
        if (!evaluating) {
            executionTicks++;
            resolvedInputs.clear();
            livePerceptions.clear();
            patrolOrigin();
            finished = null;
            reason = "";
            evaluating = true;
        }
        if (program.root.isBlank()) { evaluating = false; return AiStatus.SUCCESS; }
        if (root == null) root = new Frame(nodes.get(program.root));
        AiStatus status = root.tick();
        evaluating = false;
        // 单次查询保留结果便于调试；停止运行的持续感知不残留在线框中。
        perceptions.keySet().removeIf(id -> !livePerceptions.contains(id)
                && (!nodes.containsKey(id) || AiOperations.isContinuousPerception(nodes.get(id).type)));
        if (status != AiStatus.RUNNING) root = null;
        return status;
    }

    public void cancel() {
        cancelVersion++;
        if (root != null) root.cancel();
        if (navigation.owner != null) {
            for (AiExecution owner = navigation.owner.execution(); owner != null; owner = owner.caller) {
                if (owner == this) { stopNavigation(); break; }
            }
        }
        root = null;
        evaluating = false;
        perceptions.clear();
        expectedLanding = null;
        phase = "";
    }

    void claimNavigation(Frame frame) { navigation.owner = frame; }
    boolean ownsNavigation(Frame frame) { return navigation.owner == frame; }
    void cancelNavigation(Frame frame) {
        if (ownsNavigation(frame)) { npc.getNpcNavigation().cancel(); navigation.owner = null; }
    }
    void stopNavigation() { npc.getNpcNavigation().cancel(); navigation.owner = null; }

    public boolean interruptible() { return root == null || root.interruptible(); }
    public boolean isRunning() { return root != null; }
    public AiStatus finish(AiStatus status) { finished = status; return status; }
    public List<String> activeNodes() {
        List<String> ids = new ArrayList<>();
        if (root != null) root.collect(ids);
        return ids;
    }

    public AiValue resolve(AiNodeData node, String key) {
        var field = AiOperations.fields(node, program).stream().filter(f -> f.id().equals(key)).findFirst().orElse(null);
        if (field == null && node.type.equals("call") && key.startsWith("param:")) {
            var definition = actions.apply(node.literal("file", AiValue.text("")).text);
            if (definition != null) field = definition.parameters.stream().filter(p -> key.equals("param:" + p.id))
                    .map(p -> new AiOperations.Field(key, p.initial)).findFirst().orElse(null);
        }
        if (field == null) throw new InputException("binding");
        AiBinding binding = node.inputs.get(key);
        var value = binding == null ? field.initial() : switch (binding.scope) {
            case "context" -> binding.variableId.equals("self") ? AiValue.entity(npc)
                    : binding.variableId.equals("target") ? context.getOrDefault("target", AiValue.entity(npc.getTarget()))
                    : binding.variableId.equals("source") ? context.getOrDefault("source", AiValue.entity(npc))
                    : binding.variableId.equals("hit") ? context.getOrDefault("hit", AiValue.entity(new AiEntityRef())) : null;
            case "parameter" -> parameters.get(binding.variableId);
            case "local" -> local.get(binding.variableId);
            case "blackboard" -> blackboard.get(binding.variableId);
            case "literal" -> binding.value;
            default -> null;
        };
        if (value == null) throw new InputException("binding");
        String issue = AiValidation.valueIssue(field, value);
        if (issue != null) throw new InputException(issue);
        if (resolvedInputs.size() < 1024) resolvedInputs.put(node.id + ":" + key, value.copy());
        return value;
    }

    /** 无效动态输入使当前实例失败并清理控制权，不回退到节点常量。 */
    static final class InputException extends IllegalArgumentException {
        final String issue;
        InputException(String issue) { super(issue); this.issue = issue; }
    }
    /** 解析指定作用对象；旧节点未配置时沿用当前目标或自身。 */
    public net.minecraft.world.entity.Entity recipient(AiNodeData node, boolean defaultSelf) {
        if (!node.inputs.containsKey("recipient")) {
            var target = defaultSelf ? npc : npc.getTarget();
            return target != null && target.isAlive() && target.level() == npc.level() ? target : null;
        }
        return resolve(node, "recipient").entity.resolve((net.minecraft.server.level.ServerLevel) npc.level());
    }

    /** 捕获施放时的局部值；每次实际命中创建独立分支实例。 */
    public java.util.function.Consumer<net.minecraft.world.entity.LivingEntity> hitHandler(AiNodeData node) {
        if (node.onHit.isEmpty()) return ignored -> {};
        var locals = new LinkedHashMap<String, AiValue>(); local.forEach((k, v) -> locals.put(k, v.copy()));
        var inputs = new LinkedHashMap<String, AiValue>(); parameters.forEach((k, v) -> inputs.put(k, v.copy()));
        var capturedTarget = AiValue.entity(npc.getTarget());
        long generation = npc.getAiController().generation();
        long version = cancelVersion;
        long expires = npc.level().getGameTime() + 1200;
        return hit -> {
            if (cancelVersion != version || !npc.isAlive() || npc.level().getGameTime() > expires
                    || generation != npc.getAiController().generation()) return;
            var branch = new AiExecution(npc, program, blackboard, inputs, actions, callDepth);
            branch.local.clear(); locals.forEach((k, v) -> branch.local.put(k, v.copy()));
            branch.context.putAll(context);
            branch.context.put("target", capturedTarget.copy());
            branch.context.put("hit", AiValue.entity(hit));
            branch.context.put("source", AiValue.entity(npc));
            branch.program.root = node.onHit;
            branch.debugger = debugger;
            branch.actionId = actionId;
            npc.getAiController().enqueueHit(branch, () -> cancelVersion == version && generation == npc.getAiController().generation());
        };
    }
    public double number(AiNodeData node, String key) { return resolve(node, key).number; }
    public String text(AiNodeData node, String key) { return resolve(node, key).text; }
    public boolean flag(AiNodeData node, String key) { return resolve(node, key).flag; }
    public int ticks(AiNodeData node) { return Math.max(1, (int) Math.min(72000, Math.ceil(number(node, "seconds") * 20))); }

    private boolean testCondition(AiNodeData node, LivingEntity target) {
        boolean valid = target != null && target.isAlive() && target.level() == npc.level();
        return switch (node.type) {
            case "target_exists" -> valid;
            case "target_distance" -> valid && npc.distanceTo(target) >= number(node, "min_distance")
                    && npc.distanceTo(target) <= number(node, "max_distance");
            case "target_sight" -> valid && npc.hasLineOfSight(target);
            case "self_health" -> npc.getHealth() / npc.getMaxHealth() <= number(node, "max_health_ratio");
            case "number_compare" -> switch (text(node, "compare")) {
                case "equal" -> number(node, "left") == number(node, "right");
                case "less" -> number(node, "left") < number(node, "right");
                case "greater" -> number(node, "left") > number(node, "right");
                default -> false;
            };
            default -> false;
        };
    }

    private LivingEntity detectTarget(AiNodeData node) {
        var volume = AiPerception.volume(this, node);
        recordPerception(node.id, volume);
        return AiPerception.find(this, node, volume, true);
    }

    /** 活动节点的执行状态，不在共享定义上保存计时或已触发标记。 */
    public final class Frame {
        public final AiNodeData node;
        public int age;
        public boolean started;
        public Object state;
        private int cursor, repeats;
        private boolean evaluating;
        private int branchCursor, lockedBranch = -1;
        private boolean allSuccess;
        private Frame child;
        private AiExecution action;
        private final Map<Integer, Frame> branches = new LinkedHashMap<>();
        private final Map<Integer, AiStatus> completed = new HashMap<>();

        Frame(AiNodeData node) { this.node = node; }
        private AiExecution execution() { return AiExecution.this; }
        String actionId() { return actionId; }
        int callDepth() { return callDepth; }

        private boolean interruptible() {
            if (node == null) return true;
            if (node.type.equals("stage") && !flag(node, "interruptible")) return false;
            if (child == null && (node.type.equals("root") || node.type.equals("sequence")) && cursor < node.children.size()) {
                var next = nodes.get(node.children.get(cursor));
                if (next != null && next.type.equals("stage") && !flag(next, "interruptible")) return false;
            }
            return (child == null || child.interruptible()) && (action == null || action.interruptible())
                    && branches.values().stream().allMatch(Frame::interruptible);
        }

        private void collect(List<String> ids) {
            if (node == null || node.disabled) return;
            ids.add(node.id);
            if (child != null) child.collect(ids);
            if (action != null) ids.addAll(action.activeNodes());
            branches.values().forEach(frame -> frame.collect(ids));
        }

        private void cancel() {
            if (child != null) child.cancel();
            if (action != null) action.cancel();
            branches.values().forEach(Frame::cancel);
            branches.clear();
            if (node != null) AiActions.cancel(AiExecution.this, this);
            if (node != null && age > 0) {
                debugger.states.put(node.id, "cancelled");
                debugger.event(node, "cancelled", "");
            }
        }

        private void captureValues() {
            if (action != null) {
                action.captureValues();
                resolvedInputs.putAll(action.resolvedInputs);
            }
            if (child != null) child.captureValues();
            branches.values().forEach(Frame::captureValues);
        }

        private AiStatus tick() {
            if (node != null && node.disabled) return AiStatus.SUCCESS;
            if (!evaluating && node != null) try { debugger.before(this); }
            catch (AiDebugger.Suspended suspended) {
                for (var field : AiOperations.fields(node, program)) {
                    try { resolve(node, field.id()); } catch (InputException ignored) {}
                }
                throw suspended;
            }
            boolean entering = !evaluating;
            if (entering) {
                if (--budget[0] < 0) { reason = "step_budget"; return AiStatus.RUNNING; }
                if (node == null) { reason = "missing_node"; return AiStatus.FAILURE; }
                age++;
                branchCursor = 0;
                lockedBranch = -1;
                evaluating = true;
            }
            // 暂停只展开 Java 栈；父帧保留本轮游标和计时，继续时不重做已执行的前置逻辑。
            AiStatus result = execute(entering);
            evaluating = false;
            if (node != null) debugger.result(this, result);
            return result;
        }

        private AiStatus execute(boolean entering) {
            switch (node.type) {
                case "debug_log":
                    debugger.event(node, "value", text(node, "message") + " = " + resolve(node, "value"));
                    return AiStatus.SUCCESS;
                case "debug_line", "debug_ray": {
                    var from = resolve(node, "from").position;
                    var to = node.type.equals("debug_ray") ? from.add(resolve(node, "direction").position) : resolve(node, "to").position;
                    debugger.line(node, from, to, ticks(node));
                    return AiStatus.SUCCESS;
                }
                case "call": {
                    if (action == null) {
                        if (text(node, "target_binding").equals("entity")) {
                            try {
                                var entity = ((net.minecraft.server.level.ServerLevel) npc.level()).getEntity(UUID.fromString(text(node, "target_entity")));
                                if (!(entity instanceof net.minecraft.world.entity.LivingEntity living) || !living.isAlive()) return AiStatus.FAILURE;
                                npc.setTarget(living);
                            } catch (IllegalArgumentException exception) { reason = "invalid_target"; return AiStatus.FAILURE; }
                        }
                        if (callDepth >= 32) { reason = "call_depth"; return AiStatus.FAILURE; }
                        // 树的新调用读取最新发布版本；同一次行为调用的整个依赖闭包固定为快照。
                        var invocationActions = program.kind.equals("tree") ? freeze(actions, npc) : actions;
                        AiProgram definition = invocationActions.apply(text(node, "file"));
                        if (definition == null) { reason = "missing_action"; return AiStatus.FAILURE; }
                        if (validatedActions.size() >= 128) validatedActions.clear();
                        if (!validatedActions.computeIfAbsent(definition, value -> AiValidation.validate(value, invocationActions).isEmpty())) {
                            reason = "invalid_action"; return AiStatus.FAILURE;
                        }
                        Map<String, AiValue> inputs = new HashMap<>();
                        for (AiVariable parameter : definition.parameters)
                            if (node.inputs.containsKey("param:" + parameter.id)) inputs.put(parameter.id, resolve(node, "param:" + parameter.id));
                        for (var input : inputs.entrySet()) if (definition.parameters.stream().noneMatch(p -> p.id.equals(input.getKey()) && p.initial.type.equals(input.getValue().type))) {
                            reason = "type"; return AiStatus.FAILURE;
                        }
                        for (var required : definition.npcVariables) if (!blackboard.containsKey(required.id) || !blackboard.get(required.id).type.equals(required.initial.type)) {
                            reason = "binding"; return AiStatus.FAILURE;
                        }
                        action = new AiExecution(npc, definition, blackboard, inputs, invocationActions, callDepth + 1);
                        action.navigation = navigation;
                        action.caller = AiExecution.this;
                        action.actionId = text(node, "file");
                        action.debugger = debugger;
                        action.context.putAll(context);
                        action.patrolOrigin = patrolOrigin();
                    }
                    AiStatus status = action.tick(budget);
                    resolvedInputs.putAll(action.resolvedInputs);
                    action.perceptions.forEach((id, volume) -> recordPerception(node.id + ":" + id, volume));
                    phase = action.phase;
                    if (!action.reason.isEmpty()) reason = action.reason;
                    return status;
                }
                case "sense_targets": {
                    if (entering) {
                        var volume = AiPerception.volume(AiExecution.this, node);
                        recordPerception(node.id, volume);
                        if (!started || (npc.tickCount + npc.getId()) % 5 == 0) {
                            npc.setTarget(AiPerception.find(AiExecution.this, node, volume, true));
                            started = true;
                        }
                    }
                    return child(0);
                }
                case "detect_target":
                    return detectTarget(node) == null ? AiStatus.FAILURE : AiStatus.SUCCESS;
                case "target_exists", "target_distance", "target_sight", "self_health", "number_compare":
                    return testCondition(node, npc.getTarget()) ? AiStatus.SUCCESS : AiStatus.FAILURE;
                case "cooldown": {
                    if (child == null && cooldowns.getOrDefault(node.id, 0L) > time()) return AiStatus.FAILURE;
                    AiStatus status = child(0);
                    if (status != AiStatus.RUNNING) cooldowns.put(node.id, time() + ticks(node));
                    return status;
                }
                case "timeout":
                    if (entering && age > ticks(node)) { cancel(); reason = "timeout"; return AiStatus.FAILURE; }
                    return child(0);
                case "invert": {
                    AiStatus result = child(0);
                    if (finished != null) return finished;
                    return result == AiStatus.RUNNING ? result : result == AiStatus.SUCCESS ? AiStatus.FAILURE : AiStatus.SUCCESS;
                }
                case "force_success", "force_failure": {
                    AiStatus result = child(0);
                    if (finished != null) return finished;
                    return result == AiStatus.RUNNING ? result : node.type.equals("force_success") ? AiStatus.SUCCESS : AiStatus.FAILURE;
                }
                case "until_success", "until_failure": {
                    AiStatus result = child(0);
                    if (finished != null) return finished;
                    if (result == AiStatus.RUNNING) return result;
                    child = null;
                    return (node.type.equals("until_success") ? result == AiStatus.SUCCESS : result == AiStatus.FAILURE)
                            ? AiStatus.SUCCESS : AiStatus.RUNNING;
                }
                case "repeat": {
                    AiStatus result = child(0);
                    if (finished != null) return finished;
                    if (result != AiStatus.SUCCESS) return result;
                    child = null;
                    return ++repeats >= Math.max(1, Math.min(10000, number(node, "count"))) ? AiStatus.SUCCESS : AiStatus.RUNNING;
                }
                case "branch":
                    if (!started) { cursor = flag(node, "value") ? 0 : 1; started = true; }
                    return child(cursor);
                case "reactive_sequence", "reactive_selector":
                    return reactive(entering);
                case "parallel":
                    if (entering) allSuccess = text(node, "policy").equals("all_success");
                    return parallel();
                case "selector": {
                    while (cursor < node.children.size() && budget[0] > 0) {
                        AiStatus result = child(cursor);
                        if (finished != null) return finished;
                        if (result != AiStatus.FAILURE) return result;
                        child = null;
                        cursor++;
                    }
                    return cursor >= node.children.size() ? AiStatus.FAILURE : AiStatus.RUNNING;
                }
                case "root", "sequence", "stage":
                    if (entering && node.type.equals("stage")) phase = text(node, "name");
                    while (cursor < node.children.size() && budget[0] > 0) {
                        AiStatus result = child(cursor);
                        if (finished != null) return finished;
                        if (result != AiStatus.SUCCESS) return result;
                        child = null;
                        cursor++;
                        // 下一阶段在下一 tick 开始，让优先级门有机会取消旧动作。
                        if (node.type.equals("stage") && cursor == node.children.size()) return AiStatus.SUCCESS;
                        if (cursor < node.children.size() && nodes.get(node.children.get(cursor)).type.equals("stage")) return AiStatus.RUNNING;
                    }
                    return cursor >= node.children.size() ? AiStatus.SUCCESS : AiStatus.RUNNING;
                default:
                    return AiActions.tick(AiExecution.this, this);
            }
        }

        private AiStatus reactive(boolean entering) {
            boolean sequence = node.type.equals("reactive_sequence");
            // 不可打断的技能阶段优先执行到边界，再重新检查前面的条件。
            if (entering) for (var entry : branches.entrySet()) if (!entry.getValue().interruptible()) {
                lockedBranch = entry.getKey(); break;
            }
            if (lockedBranch >= 0) {
                AiStatus result = branches.get(lockedBranch).tick();
                if (finished != null) return finished;
                if (result != AiStatus.RUNNING) branches.remove(lockedBranch);
                return AiStatus.RUNNING;
            }
            for (; branchCursor < node.children.size(); branchCursor++) {
                if (budget[0] <= 0) return AiStatus.RUNNING;
                Frame current = branches.computeIfAbsent(branchCursor, index -> new Frame(nodes.get(node.children.get(index))));
                AiStatus result = current.tick();
                if (finished != null) { cancelBranchesExcept(-1); return finished; }
                if (result != AiStatus.RUNNING) branches.remove(branchCursor);
                if (result == AiStatus.RUNNING || (sequence ? result == AiStatus.FAILURE : result == AiStatus.SUCCESS)) {
                    cancelBranchesExcept(result == AiStatus.RUNNING ? branchCursor : -1);
                    return result;
                }
            }
            return sequence ? AiStatus.SUCCESS : AiStatus.FAILURE;
        }

        private void cancelBranchesExcept(int keep) {
            var iterator = branches.entrySet().iterator();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                if (entry.getKey() != keep) { entry.getValue().cancel(); iterator.remove(); }
            }
        }

        private AiStatus parallel() {
            for (; branchCursor < node.children.size() && budget[0] > 0; branchCursor++) {
                if (completed.containsKey(branchCursor)) continue;
                Frame current = branches.computeIfAbsent(branchCursor, index -> new Frame(nodes.get(node.children.get(index))));
                AiStatus result = current.tick();
                if (finished != null) { cancelBranchesExcept(-1); return finished; }
                if (result != AiStatus.RUNNING) { branches.remove(branchCursor); completed.put(branchCursor, result); }
                if (allSuccess ? result == AiStatus.FAILURE : result == AiStatus.SUCCESS) {
                    cancelBranchesExcept(-1);
                    return result;
                }
            }
            if (completed.size() == node.children.size()) return allSuccess ? AiStatus.SUCCESS : AiStatus.FAILURE;
            return AiStatus.RUNNING;
        }

        private AiStatus child(int index) {
            if (index >= node.children.size()) return AiStatus.FAILURE;
            if (child == null) child = new Frame(nodes.get(node.children.get(index)));
            return child.tick();
        }
    }
}
