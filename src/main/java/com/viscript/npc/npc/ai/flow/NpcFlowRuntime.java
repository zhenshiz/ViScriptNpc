package com.viscript.npc.npc.ai.flow;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.compat.team.NpcFactionBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.thexeler.MindMachine;
import org.thexeler.api.IntentionHandle;
import org.thexeler.api.IntentionPriority;
import org.thexeler.api.IntentionStatus;
import org.thexeler.api.SubmissionResult;
import org.thexeler.api.world.ActorReference;
import org.thexeler.api.world.ItemReference;
import org.thexeler.api.world.MindPosition;
import org.thexeler.api.parameter.ParameterSchema;
import org.thexeler.api.parameter.ParameterDefinition;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 顺序执行外层流程；每个分支只选择一个子节点，并且不会复制执行流。 */
public final class NpcFlowRuntime {
    private static final int MAX_NODE_STEPS_PER_TICK = 64;
    private static final int MAX_ACTIVE_RULES = 32;

    private final CustomNpc npc;
    private final MindMachine mind;
    private final Map<UUID, ActiveRule> active = new java.util.LinkedHashMap<>();
    private final Map<UUID, Set<UUID>> rangeMembers = new HashMap<>();
    private final Deque<TriggerEvent> pending = new ArrayDeque<>();
    private NpcFlowDefinition definition = new NpcFlowDefinition(1, List.of());
    private CompoundTag publicParameters = new CompoundTag();
    private Set<ResourceLocation> dependencies = Set.of();
    private CompoundTag variableDeclarations = new CompoundTag();
    private final Map<String, Tag> variables = new HashMap<>();
    private final Deque<CompoundTag> errors = new ArrayDeque<>();
    private Set<UUID> breakpoints = Set.of();
    private boolean debugPaused;

    public NpcFlowRuntime(CustomNpc npc, MindMachine mind) {
        this.npc = npc;
        this.mind = mind;
        reloadDefinition();
    }

    public void reloadDefinition() {
        var resolved = NpcFlowTemplateRegistry.getInstance().resolve(npc.getNpcAI());
        definition = NpcFlowDefinition.read(resolved.flow());
        publicParameters = resolved.parameters();
        dependencies = resolved.dependencies();
        variableDeclarations = resolved.flow().getCompound("variables").copy();
        variables.clear();
        for (String name : variableDeclarations.getAllKeys()) {
            CompoundTag declaration = variableDeclarations.getCompound(name);
            Tag defaultValue = declaration.get("default");
            if (defaultValue != null && typeCompatible(declaration.getString("type"), defaultValue)) {
                variables.put(name, defaultValue.copy());
            }
        }
        if (npc.getNpcAI().usesTemplate()) npc.getNpcAI().setResolvedHash(resolved.resolvedHash());
        else npc.getNpcAI().setEmbeddedDependencyHash(resolved.resolvedHash());
        active.clear();
        pending.clear();
    }

    public Set<ResourceLocation> dependencies() {
        return dependencies;
    }

    public void trigger(String trigger, @Nullable LivingEntity target) {
        pending.addLast(new TriggerEvent(trigger, target == null ? null : target.getUUID()));
    }

    public void tick() {
        if (!npc.isAlive()) return;
        for (NpcFlowRule rule : definition.rules()) {
            int interval = Math.max(1, rule.options().contains("interval") ? rule.options().getInt("interval") : 1);
            if ("tick".equals(rule.trigger()) && npc.level().getGameTime() % interval == 0
                    && active.size() < MAX_ACTIVE_RULES) {
                start(rule, null);
            }
        }
        while (!pending.isEmpty() && active.size() < MAX_ACTIVE_RULES) {
            TriggerEvent event = pending.removeFirst();
            for (NpcFlowRule rule : definition.rules()) {
                if (rule.trigger().equals(event.trigger())) start(rule, event.targetUuid());
            }
        }
        scanRangeTriggers();
        for (ActiveRule state : active.values()) {
            if (state.targetUuid != null && !state.targetInvalidNotified) {
                LivingEntity target = target(state.targetUuid);
                if (target == null || !target.isAlive()) {
                    pending.addLast(new TriggerEvent("target_invalid", state.targetUuid));
                    state.targetInvalidNotified = true;
                }
            }
        }
        for (ActiveRule state : List.copyOf(active.values())) step(state, MAX_NODE_STEPS_PER_TICK, true, false);
    }

    private void scanRangeTriggers() {
        for (NpcFlowRule rule : definition.rules()) {
            if (!"range_enter".equals(rule.trigger()) && !"range_leave".equals(rule.trigger())) continue;
            int interval = Math.max(1, rule.options().contains("interval") ? rule.options().getInt("interval") : 10);
            if (npc.level().getGameTime() % interval != 0) continue;
            double range = Math.max(0.0D, rule.options().getDouble("range"));
            Set<UUID> current = new java.util.LinkedHashSet<>();
            npc.level().getEntitiesOfClass(LivingEntity.class, npc.getBoundingBox().inflate(range),
                    entity -> entity != npc && entity.isAlive()).forEach(entity -> current.add(entity.getUUID()));
            Set<UUID> previous = rangeMembers.getOrDefault(rule.id(), Set.of());
            Set<UUID> delta = new java.util.LinkedHashSet<>("range_enter".equals(rule.trigger()) ? current : previous);
            delta.removeAll("range_enter".equals(rule.trigger()) ? previous : current);
            for (UUID target : delta) start(rule, target);
            rangeMembers.put(rule.id(), Set.copyOf(current));
        }
    }

    private void start(NpcFlowRule rule, @Nullable UUID target) {
        if (active.containsKey(rule.id())) return;
        if (active.size() >= MAX_ACTIVE_RULES) {
            error("ACTIVE_FLOW_LIMIT", "Active flow limit exceeded", rule.id());
            return;
        }
        ActiveRule state = new ActiveRule(rule, target);
        state.paused = debugPaused;
        active.put(rule.id(), state);
    }

    private void step(ActiveRule state, int maxSteps, boolean enforceBudget, boolean debugStep) {
        if (state.paused && !debugStep) return;
        if (state.waitingHandle != null) {
            var handle = mind.getHandle(state.waitingHandle);
            if (handle.isPresent() && !handle.orElseThrow().status().isTerminal()) return;
            Outcome outcome = handle.isPresent() && handle.orElseThrow().status() == IntentionStatus.COMPLETED
                    ? Outcome.SUCCESS : Outcome.FAILURE;
            state.waitingHandle = null;
            completeFrame(state, outcome);
        }
        if (state.delay > 0) {
            state.delay--;
            return;
        }
        int steps = 0;
        while (!state.stack.isEmpty() && steps++ < maxSteps) {
            Frame frame = state.stack.peek();
            NpcFlowNode node = frame.node;
            if (!debugStep && breakpoints.contains(node.id()) && !node.id().equals(state.skipBreakpoint)) {
                state.paused = true;
                state.skipBreakpoint = node.id();
                return;
            }
            if (node.id().equals(state.skipBreakpoint)) state.skipBreakpoint = null;
            String type = node.type();
            if ("sequence".equals(type)) {
                if (frame.childOutcome == Outcome.FAILURE) {
                    completeFrame(state, Outcome.FAILURE);
                    continue;
                }
                frame.childOutcome = null;
                if (frame.childIndex < node.children().size()) {
                    state.stack.push(new Frame(node.children().get(frame.childIndex++), 0));
                } else {
                    completeFrame(state, Outcome.SUCCESS);
                }
                continue;
            }
            if ("branch".equals(type)) {
                NpcFlowNode selected = selectBranch(node, state.targetUuid);
                state.stack.pop();
                if (selected != null) state.stack.push(new Frame(selected, 0));
                continue;
            }
            if ("delay".equals(type)) {
                state.delay = Math.max(0, node.options().getInt("ticks"));
                completeFrame(state, Outcome.SUCCESS);
                return;
            }
            if ("loop".equals(type)) {
                int limit = Math.max(0, Math.min(1024, node.options().getInt("max_iterations")));
                frame.childOutcome = null;
                if (!node.children().isEmpty() && frame.childIndex < limit) {
                    frame.childIndex++;
                    state.stack.push(new Frame(node.children().getFirst(), 0));
                } else {
                    completeFrame(state, Outcome.SUCCESS);
                }
                continue;
            }
            if ("retry".equals(type)) {
                int limit = Math.max(1, Math.min(1024, node.options().getInt("max_iterations")));
                if (frame.childOutcome == Outcome.SUCCESS) {
                    completeFrame(state, Outcome.SUCCESS);
                } else if (!node.children().isEmpty() && frame.childIndex < limit) {
                    frame.childOutcome = null;
                    frame.childIndex++;
                    state.stack.push(new Frame(node.children().getFirst(), 0));
                } else {
                    completeFrame(state, Outcome.FAILURE);
                }
                continue;
            }
            if ("set_variable".equals(type)) {
                String name = node.options().getString("name");
                Tag value = node.options().get("value");
                if (!name.isBlank() && value != null) variables.put(name, value.copy());
                completeFrame(state, Outcome.SUCCESS);
                continue;
            }
            if ("submit".equals(type)) {
                SubmissionResult result = submit(node, state.targetUuid);
                if (result instanceof SubmissionResult.Accepted accepted) {
                    state.waitingHandle = accepted.handle().id();
                    return;
                }
                completeFrame(state, Outcome.FAILURE);
                if (result instanceof SubmissionResult.Rejected rejected) {
                    error("INTENTION_SUBMISSION_REJECTED", rejected.diagnostics().toString(), state.rule.id());
                }
                continue;
            }
            if ("end".equals(type)) {
                state.stack.clear();
                break;
            }
            if ("fail".equals(type)) {
                completeFrame(state, Outcome.FAILURE);
                continue;
            }
            completeFrame(state, Outcome.FAILURE);
        }
        if (!state.stack.isEmpty() && steps >= maxSteps && enforceBudget) {
            state.paused = true;
            error("FLOW_STEP_BUDGET_EXCEEDED", "Flow exceeded " + maxSteps
                    + " node steps in one tick", state.rule.id());
        } else if (state.stack.isEmpty()) active.remove(state.rule.id());
    }

    private static void completeFrame(ActiveRule state, Outcome outcome) {
        if (!state.stack.isEmpty()) state.stack.pop();
        if (!state.stack.isEmpty()) state.stack.peek().childOutcome = outcome;
    }

    private NpcFlowNode selectBranch(NpcFlowNode node, @Nullable UUID targetUuid) {
        for (NpcFlowNode child : node.children()) {
            String condition = child.options().getString("condition");
            if (condition.isEmpty() || "always".equals(condition)
                    || conditionMatches(condition, child.options(), targetUuid)) return child;
        }
        return null;
    }

    private boolean conditionMatches(String condition, CompoundTag options, @Nullable UUID targetUuid) {
        LivingEntity target = target(targetUuid);
        return switch (condition) {
            case "alive" -> npc.isAlive();
            case "target_alive" -> target != null && target.isAlive();
            case "line_of_sight" -> target != null && npc.hasLineOfSight(target);
            case "hostile" -> target != null && NpcFactionBridge.canHurt(npc, target);
            case "friendly" -> target != null && !NpcFactionBridge.canHurt(npc, target);
            case "has_faction" -> NpcFactionBridge.hasConfiguredFaction(npc);
            case "distance" -> target != null && npc.distanceToSqr(target)
                    <= Math.pow(Math.max(0.0D, options.getDouble("range")), 2.0D);
            case "health_ratio_below" -> npc.getHealth() / Math.max(1.0F, npc.getMaxHealth())
                    < options.getFloat("ratio");
            case "health_below" -> npc.getHealth() < options.getFloat("health");
            case "target_health_ratio_below" -> target != null
                    && target.getHealth() / Math.max(1.0F, target.getMaxHealth()) < options.getFloat("ratio");
            case "target_health_below" -> target != null && target.getHealth() < options.getFloat("health");
            case "entity_type" -> target != null && BuiltInRegistries.ENTITY_TYPE.getKey(target.getType()).toString()
                    .equals(options.getString("entity_type"));
            case "entity_tag" -> target != null && ResourceLocation.tryParse(options.getString("tag")) != null
                    && target.getType().is(TagKey.create(Registries.ENTITY_TYPE,
                    ResourceLocation.parse(options.getString("tag"))));
            case "dimension" -> npc.level().dimension().location().toString().equals(options.getString("dimension"));
            case "world" -> npc.level().dimension().location().toString().equals(options.getString("world"));
            case "variable_true" -> variables.get(options.getString("variable")) instanceof ByteTag value
                    && value.getAsByte() != 0;
            case "random" -> npc.getRandom().nextFloat() < options.getFloat("probability");
            default -> NpcFlowExtensionRegistry.condition(condition)
                    .map(value -> value.test(npc, target, options)).orElse(false);
        };
    }

    @Nullable
    private LivingEntity target(@Nullable UUID targetUuid) {
        if (targetUuid == null || npc.getServer() == null) return null;
        var level = npc.getServer().getLevel(npc.level().dimension());
        Entity entity = level == null ? null : level.getEntity(targetUuid);
        return entity instanceof LivingEntity living ? living : null;
    }

    private SubmissionResult submit(NpcFlowNode node, @Nullable UUID targetUuid) {
        ResourceLocation id = ResourceLocation.tryParse(node.options().getString("intention"));
        if (id == null) {
            return new SubmissionResult.Rejected(List.of(new org.thexeler.api.IntentionDiagnostic(
                    "INVALID_FLOW_INTENTION", "Flow submit node has no valid intention id")));
        }
        IntentionPriority priority;
        String priorityName = node.options().contains("priority") ? node.options().getString("priority") : "NORMAL";
        try { priority = IntentionPriority.valueOf(priorityName); }
        catch (IllegalArgumentException exception) { priority = IntentionPriority.NORMAL; }
        Map<String, Object> params = new HashMap<>();
        CompoundTag values = node.options().getCompound("parameters");
        ParameterSchema schema = org.thexeler.AttentionMind.intentionTypes().get(id)
                .map(type -> type.parameters())
                .orElseGet(() -> {
                    var asset = org.thexeler.AttentionMind.intentionAssets().generation().assets().get(id);
                    return asset == null ? ParameterSchema.EMPTY : asset.parameters();
                });
        for (String key : values.getAllKeys()) {
            Object raw = value(values.get(key), targetUuid);
            params.put(key, coerce(raw, schema.definitions().get(key)));
        }
        return mind.submit(id, priority, params);
    }

    private static Object coerce(Object value, @Nullable ParameterDefinition<?> definition) {
        if (value == null || definition == null || definition.type().accepts(value)) return value;
        String type = definition.type().id().getPath();
        if (value instanceof Number number) {
            return switch (type) {
                case "int" -> number.intValue();
                case "long" -> number.longValue();
                case "float" -> number.floatValue();
                case "double" -> number.doubleValue();
                default -> value;
            };
        }
        if (value instanceof String string) {
            if ("resource_location".equals(type)) return ResourceLocation.parse(string);
            if ("item_reference".equals(type)) return new ItemReference(ResourceLocation.parse(string));
        }
        if (value instanceof CompoundTag compound) {
            if ("position".equals(type)) {
                return MindPosition.of(compound.getDouble("x"), compound.getDouble("y"), compound.getDouble("z"));
            }
            if ("actor_reference".equals(type) && compound.hasUUID("uuid")) {
                ResourceLocation dimension = ResourceLocation.tryParse(compound.getString("dimension"));
                if (dimension != null) {
                    return new ActorReference(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, dimension),
                            compound.getUUID("uuid"));
                }
            }
        }
        return value;
    }

    private Object value(Tag tag, @Nullable UUID targetUuid) {
        if (tag instanceof net.minecraft.nbt.StringTag string) return string.getAsString();
        if (tag instanceof net.minecraft.nbt.ByteTag numeric) return numeric.getAsByte() != 0;
        if (tag instanceof net.minecraft.nbt.ShortTag numeric) return (int) numeric.getAsShort();
        if (tag instanceof net.minecraft.nbt.IntTag numeric) return numeric.getAsInt();
        if (tag instanceof net.minecraft.nbt.LongTag numeric) return numeric.getAsLong();
        if (tag instanceof net.minecraft.nbt.FloatTag numeric) return numeric.getAsFloat();
        if (tag instanceof net.minecraft.nbt.DoubleTag numeric) return numeric.getAsDouble();
        if (tag instanceof CompoundTag compound && "target".equals(compound.getString("binding")) && targetUuid != null) {
            return new ActorReference(npc.level().dimension(), targetUuid);
        }
        if (tag instanceof CompoundTag compound && "parameter".equals(compound.getString("binding"))) {
            return value(publicParameters.get(compound.getString("name")), targetUuid);
        }
        if (tag instanceof CompoundTag compound && "variable".equals(compound.getString("binding"))) {
            return value(variables.get(compound.getString("name")), targetUuid);
        }
        return tag == null ? null : tag.toString();
    }

    public CompoundTag save() {
        CompoundTag root = new CompoundTag();
        root.putBoolean("debug_paused", debugPaused);
        ListTag states = new ListTag();
        for (ActiveRule state : active.values()) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("rule", state.rule.id());
            if (state.targetUuid != null) tag.putUUID("target", state.targetUuid);
            tag.putInt("delay", state.delay);
            tag.putBoolean("target_invalid_notified", state.targetInvalidNotified);
            tag.putBoolean("paused", state.paused);
            if (state.waitingHandle != null) tag.putUUID("waiting_handle", state.waitingHandle);
            ListTag stack = new ListTag();
            for (Frame frame : state.stack) {
                CompoundTag savedFrame = new CompoundTag();
                savedFrame.putUUID("node", frame.node.id());
                savedFrame.putInt("child_index", frame.childIndex);
                if (frame.childOutcome != null) savedFrame.putString("child_outcome", frame.childOutcome.name());
                stack.add(savedFrame);
            }
            tag.put("stack", stack);
            states.add(tag);
        }
        root.put("active", states);
        CompoundTag savedRanges = new CompoundTag();
        rangeMembers.forEach((rule, members) -> {
            ListTag ids = new ListTag();
            members.forEach(id -> ids.add(StringTag.valueOf(id.toString())));
            savedRanges.put(rule.toString(), ids);
        });
        root.put("range_members", savedRanges);
        CompoundTag savedVariables = new CompoundTag();
        variables.forEach((name, value) -> savedVariables.put(name, value.copy()));
        root.put("variables", savedVariables);
        ListTag savedErrors = new ListTag();
        errors.forEach(value -> savedErrors.add(value.copy()));
        root.put("errors", savedErrors);
        return root;
    }

    public void restore(CompoundTag root) {
        debugPaused = root.getBoolean("debug_paused");
        active.clear();
        variables.clear();
        CompoundTag savedVariables = root.getCompound("variables");
        savedVariables.getAllKeys().forEach(name -> variables.put(name, savedVariables.get(name).copy()));
        errors.clear();
        ListTag savedErrors = root.getList("errors", Tag.TAG_COMPOUND);
        for (int index = 0; index < savedErrors.size(); index++) errors.addLast(savedErrors.getCompound(index).copy());
        rangeMembers.clear();
        CompoundTag savedRanges = root.getCompound("range_members");
        for (String rule : savedRanges.getAllKeys()) {
            Set<UUID> ids = new java.util.LinkedHashSet<>();
            ListTag list = savedRanges.getList(rule, Tag.TAG_STRING);
            for (int item = 0; item < list.size(); item++) ids.add(UUID.fromString(list.getString(item)));
            rangeMembers.put(UUID.fromString(rule), Set.copyOf(ids));
        }
        ListTag states = root.getList("active", Tag.TAG_COMPOUND);
        for (int index = 0; index < states.size(); index++) {
            CompoundTag tag = states.getCompound(index);
            UUID id = tag.hasUUID("rule") ? tag.getUUID("rule") : null;
            NpcFlowRule rule = definition.rules().stream().filter(value -> value.id().equals(id)).findFirst().orElse(null);
            if (rule == null) continue;
            ActiveRule state = new ActiveRule(rule, tag.hasUUID("target") ? tag.getUUID("target") : null);
            state.delay = tag.getInt("delay");
            state.waitingHandle = tag.hasUUID("waiting_handle") ? tag.getUUID("waiting_handle") : null;
            state.targetInvalidNotified = tag.getBoolean("target_invalid_notified");
            state.paused = tag.getBoolean("paused");
            if (tag.contains("stack", Tag.TAG_LIST)) {
                state.stack.clear();
                ListTag stack = tag.getList("stack", Tag.TAG_COMPOUND);
                for (int frameIndex = 0; frameIndex < stack.size(); frameIndex++) {
                    CompoundTag savedFrame = stack.getCompound(frameIndex);
                    NpcFlowNode node = findNode(rule.root(), savedFrame.getUUID("node"));
                    if (node != null) {
                        Frame frame = new Frame(node, savedFrame.getInt("child_index"));
                        if (savedFrame.contains("child_outcome")) {
                            try { frame.childOutcome = Outcome.valueOf(savedFrame.getString("child_outcome")); }
                            catch (IllegalArgumentException ignored) {}
                        }
                        state.stack.addLast(frame);
                    } else {
                        error("FLOW_RESTORE_NODE_MISSING", "Saved flow node no longer exists", rule.id());
                    }
                }
            }
            active.put(rule.id(), state);
        }
    }

    public void pause() {
        debugPaused = true;
        active.values().forEach(state -> state.paused = true);
    }

    public void resume() {
        debugPaused = false;
        active.values().forEach(state -> state.paused = false);
    }

    public boolean isPaused() {
        return debugPaused || active.values().stream().anyMatch(state -> state.paused);
    }

    public void stepOnce() {
        ActiveRule state = active.values().stream().findFirst().orElse(null);
        if (state != null) step(state, 1, false, true);
    }

    public void setBreakpoints(Set<UUID> breakpoints) {
        this.breakpoints = breakpoints == null ? Set.of() : Set.copyOf(breakpoints);
    }

    public CompoundTag debugSnapshot() {
        CompoundTag tag = save();
        tag.putInt("active_count", active.size());
        ListTag currentNodes = new ListTag();
        active.values().forEach(state -> {
            if (state.stack.isEmpty()) return;
            CompoundTag current = new CompoundTag();
            current.putUUID("rule", state.rule.id());
            current.putUUID("node", state.stack.peek().node.id());
            currentNodes.add(current);
        });
        tag.put("current_nodes", currentNodes);
        ListTag savedBreakpoints = new ListTag();
        breakpoints.stream().sorted().forEach(id -> savedBreakpoints.add(StringTag.valueOf(id.toString())));
        tag.put("breakpoints", savedBreakpoints);
        return tag;
    }

    private void error(String code, String detail, UUID rule) {
        CompoundTag tag = new CompoundTag();
        tag.putString("code", code);
        tag.putString("detail", detail);
        tag.putUUID("rule", rule);
        tag.putLong("game_time", npc.level().getGameTime());
        if (errors.size() >= 64) errors.removeFirst();
        errors.addLast(tag);
    }

    public CompoundTag persistentVariables() {
        CompoundTag saved = new CompoundTag();
        for (String name : variableDeclarations.getAllKeys()) {
            CompoundTag declaration = variableDeclarations.getCompound(name);
            Tag value = variables.get(name);
            if (declaration.getBoolean("persistent") && value != null) saved.put(name, value.copy());
        }
        return saved;
    }

    public void restorePersistentVariables(CompoundTag saved) {
        for (String name : saved.getAllKeys()) {
            CompoundTag declaration = variableDeclarations.getCompound(name);
            Tag value = saved.get(name);
            if (!declaration.getBoolean("persistent") || value == null) continue;
            String type = declaration.getString("type");
            if (typeCompatible(type, value)) variables.put(name, value.copy());
        }
    }

    private static boolean typeCompatible(String type, Tag value) {
        return switch (type) {
            case "boolean" -> value instanceof ByteTag;
            case "integer" -> value instanceof ByteTag || value instanceof ShortTag || value instanceof IntTag;
            case "long" -> value instanceof LongTag;
            case "number" -> value instanceof NumericTag;
            case "string", "resource_location" -> value instanceof StringTag;
            default -> false;
        };
    }

    private static NpcFlowNode findNode(NpcFlowNode node, UUID id) {
        if (node.id().equals(id)) return node;
        for (NpcFlowNode child : node.children()) {
            NpcFlowNode found = findNode(child, id);
            if (found != null) return found;
        }
        return null;
    }

    private static final class ActiveRule {
        private final NpcFlowRule rule;
        private final UUID targetUuid;
        private final Deque<Frame> stack = new ArrayDeque<>();
        private int delay;
        private UUID waitingHandle;
        private boolean targetInvalidNotified;
        private boolean paused;
        private UUID skipBreakpoint;

        private ActiveRule(NpcFlowRule rule, UUID targetUuid) {
            this.rule = rule;
            this.targetUuid = targetUuid;
            stack.push(new Frame(rule.root(), 0));
        }
    }

    private static final class Frame {
        private final NpcFlowNode node;
        private int childIndex;
        private Outcome childOutcome;

        private Frame(NpcFlowNode node, int childIndex) {
            this.node = node;
            this.childIndex = childIndex;
        }
    }

    private enum Outcome { SUCCESS, FAILURE }

    private record TriggerEvent(String trigger, UUID targetUuid) {}
}
