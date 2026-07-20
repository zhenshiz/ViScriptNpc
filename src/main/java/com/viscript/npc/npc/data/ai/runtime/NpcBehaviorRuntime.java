package com.viscript.npc.npc.data.ai.runtime;

import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.npc.data.ai.graph.NpcBehaviorAbortType;
import com.viscript.npc.npc.data.ai.graph.node.FindNearestPlayerNode;
import com.viscript.npc.npc.data.ai.graph.node.FollowTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.IdleNode;
import com.viscript.npc.npc.data.ai.graph.node.IsTargetInRangeNode;
import com.viscript.npc.npc.data.ai.graph.node.LookAtTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.MeleeAttackTargetNode;
import com.viscript.npc.npc.data.ai.graph.node.MoveToPositionNode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@RequiredArgsConstructor
public class NpcBehaviorRuntime {
    private static final int MAX_DEBUG_LOG_LINES = 200;

    private final CustomNpc npc;
    private final Map<UUID, Integer> runningTicks = new HashMap<>();
    private final Map<UUID, Integer> cooldownTicks = new HashMap<>();
    private final Map<UUID, UUID> activeChildByComposite = new HashMap<>();
    private final Set<UUID> runningNodes = new HashSet<>();
    private final Set<UUID> breakpointBypassActive = new HashSet<>();
    private final Deque<String> debugLogLines = new ArrayDeque<>();
    private CompoundTag graphTag = new CompoundTag();
    private CompoundTag programTag = new CompoundTag();
    private NpcBehaviorProgram program;
    private Map<UUID, NpcBehaviorProgramNode> programNodes = new LinkedHashMap<>();
    @Getter
    private NpcBehaviorDebugSnapshot lastDebugSnapshot = new NpcBehaviorDebugSnapshot();
    @Getter
    private boolean debugPaused;
    @Getter
    private boolean breakpointPaused;
    @Nullable
    private UUID breakpointNode;
    @Nullable
    private LivingEntity editorDebugTarget;
    @Nullable
    private UUID editorDebugIgnoredPlayer;
    private int debugStepTicks;
    private boolean enabled;
    private int tickRate = 5;
    private int tickCooldown;

    public void configure(NpcAI ai) {
        enabled = ai != null && ai.isEnabled();
        tickRate = Mth.clamp(ai == null ? 5 : ai.getTickRate(), 1, 200);
        CompoundTag nextProgramTag = ai == null ? new CompoundTag() : ai.getBehaviorProgram();
        CompoundTag nextGraphTag = ai == null ? new CompoundTag() : ai.getBehaviorGraph();
        if (!Objects.equals(programTag, nextProgramTag) || !Objects.equals(graphTag, nextGraphTag)) {
            programTag = nextProgramTag.copy();
            graphTag = nextGraphTag.copy();
            reloadProgram();
        }
    }

    public void tick() {
        tick(false);
    }

    public void tickForEditorDebug() {
        tick(true);
    }

    private void tick(boolean allowClientLevel) {
        if (!enabled || (!allowClientLevel && npc.level().isClientSide)) {
            return;
        }
        boolean stepping = debugPaused && debugStepTicks > 0;
        if (debugPaused && !stepping) {
            pauseNpcControls();
            return;
        }
        if (!stepping && tickCooldown-- > 0) {
            return;
        }
        if (stepping) {
            debugStepTicks--;
        }
        tickCooldown = tickRate;
        if (program == null) {
            reloadProgram();
        }
        if (program == null) {
            return;
        }

        tickCooldowns();
        NpcBehaviorProgramNode root = programNodes.get(program.getRootNode());
        if (root == null) {
            return;
        }

        DebugRecorder debug = new DebugRecorder();
        Set<UUID> previousRunningNodes = Set.copyOf(runningNodes);
        Set<UUID> visited = new HashSet<>();
        evaluate(root, 0, visited, debug);
        Set<UUID> currentRunningNodes = debug.currentRunningNodes();
        for (UUID nodeId : previousRunningNodes) {
            if (!currentRunningNodes.contains(nodeId) && !visited.contains(nodeId)) {
                abortRunningNode(nodeId, debug);
            }
        }
        runningNodes.clear();
        runningNodes.addAll(currentRunningNodes);
        runningTicks.keySet().retainAll(visited);
        lastDebugSnapshot = debug.finish();
    }

    public void setDebugPaused(boolean debugPaused) {
        this.debugPaused = debugPaused;
        if (!debugPaused) {
            breakpointPaused = false;
            breakpointNode = null;
        }
    }

    public void setEditorDebugTarget(@Nullable LivingEntity editorDebugTarget) {
        this.editorDebugTarget = editorDebugTarget;
    }

    public void setEditorDebugIgnoredPlayer(@Nullable UUID playerUuid) {
        this.editorDebugIgnoredPlayer = playerUuid;
        if (isEditorDebugIgnoredPlayer(npc.getTarget())) {
            npc.setTarget(null);
        }
    }

    public void clearEditorDebugIgnoredPlayer(UUID playerUuid) {
        if (Objects.equals(editorDebugIgnoredPlayer, playerUuid)) {
            setEditorDebugIgnoredPlayer(null);
        }
    }

    public void stepDebugTickNow() {
        stepDebugTickNow(false);
    }

    public void stepDebugTickNowForEditor() {
        stepDebugTickNow(true);
    }

    public void continueDebug() {
        if (breakpointNode != null) {
            breakpointBypassActive.add(breakpointNode);
        }
        breakpointPaused = false;
        breakpointNode = null;
        debugPaused = false;
        debugStepTicks = 0;
    }

    public void stopDebug() {
        debugPaused = true;
        debugStepTicks = 0;
        breakpointPaused = false;
        breakpointNode = null;
        breakpointBypassActive.clear();
        runningTicks.clear();
        cooldownTicks.clear();
        activeChildByComposite.clear();
        runningNodes.clear();
        debugLogLines.clear();
        npc.getNavigation().stop();
        reloadProgram();
        lastDebugSnapshot = createIdleSnapshot();
    }

    private void pauseNpcControls() {
        npc.getNavigation().stop();
    }

    private void stepDebugTickNow(boolean allowClientLevel) {
        if (breakpointNode != null) {
            breakpointBypassActive.add(breakpointNode);
        }
        breakpointPaused = false;
        breakpointNode = null;
        debugPaused = true;
        debugStepTicks = 1;
        try {
            tick(allowClientLevel);
        } finally {
            debugStepTicks = 0;
        }
    }

    private void reloadProgram() {
        runningTicks.clear();
        cooldownTicks.clear();
        activeChildByComposite.clear();
        runningNodes.clear();
        breakpointBypassActive.clear();
        breakpointPaused = false;
        breakpointNode = null;
        NpcBehaviorDataSerializers.register();
        program = null;
        programNodes = new LinkedHashMap<>();

        if (!programTag.isEmpty()) {
            NpcBehaviorProgram loadedProgram = new NpcBehaviorProgram();
            loadedProgram.deserializeNBT(Platform.getFrozenRegistry(), programTag.copy());
            if (!loadedProgram.isEmpty()) {
                program = loadedProgram;
            }
        }
        if (program == null) {
            program = NpcBehaviorProgramCompiler.compile(graphTag);
        }
        if (program == null || program.isEmpty()) {
            program = null;
            return;
        }
        programNodes = program.createNodeMap();
    }

    private Status evaluate(NpcBehaviorProgramNode node, int depth, Set<UUID> visited, DebugRecorder debug) {
        if (depth > 64 || node == null) {
            debug.fail("depth_limit");
            return Status.FAILURE;
        }
        if (node.isDisabled()) {
            return Status.SKIPPED;
        }
        visited.add(node.getUid());
        int visitIndex = debug.visit(node);
        long start = System.nanoTime();
        Status status = Status.FAILURE;
        try {
            if (shouldPauseAtBreakpoint(node)) {
                breakpointPaused = true;
                breakpointNode = node.getUid();
                debugPaused = true;
                pauseNpcControls();
                debug.hitBreakpoint(node);
                status = Status.RUNNING;
                return status;
            }
            var handler = NpcBehaviorNodeHandlers.forType(node.getType());
            if (handler.isPresent()) {
                status = handler.get().evaluate(new NpcBehaviorExecutionContext(npc, editorDebugTarget, tickRate, debug::fail), node);
            } else {
                status = switch (node.getType()) {
                    case NpcBehaviorNodeType.ROOT -> evaluateRoot(node, depth, visited, debug);
                    case NpcBehaviorNodeType.SEQUENCE -> evaluateSequence(node, depth, visited, debug);
                    case NpcBehaviorNodeType.SELECTOR -> evaluateSelector(node, depth, visited, debug);
                    case NpcBehaviorNodeType.INVERTER -> evaluateInverter(node, depth, visited, debug);
                    case NpcBehaviorNodeType.HAS_TARGET -> hasTarget(debug);
                    case NpcBehaviorNodeType.FIND_NEAREST_PLAYER -> findNearestPlayer(node, debug);
                    case NpcBehaviorNodeType.IS_TARGET_IN_RANGE -> isTargetInRange(node, debug);
                    case NpcBehaviorNodeType.IDLE -> idle(node);
                    case NpcBehaviorNodeType.FOLLOW_TARGET -> followTarget(node, debug);
                    case NpcBehaviorNodeType.LOOK_AT_TARGET -> lookAtTarget(node, debug);
                    case NpcBehaviorNodeType.MOVE_TO_POSITION -> moveToPosition(node);
                    case NpcBehaviorNodeType.MELEE_ATTACK_TARGET -> meleeAttackTarget(node, debug);
                    default -> {
                        debug.fail("unknown_node_type:" + node.getType());
                        yield Status.FAILURE;
                    }
                };
            }
            return status;
        } finally {
            debug.record(node, status, visitIndex, System.nanoTime() - start);
            if (status != Status.RUNNING) {
                breakpointBypassActive.remove(node.getUid());
            }
        }
    }

    private Status evaluateRoot(NpcBehaviorProgramNode node, int depth, Set<UUID> visited, DebugRecorder debug) {
        NpcBehaviorProgramNode child = firstChild(node);
        if (child == null) {
            debug.fail("root_missing_child");
            return Status.FAILURE;
        }
        return evaluate(child, depth + 1, visited, debug);
    }

    private Status evaluateSequence(NpcBehaviorProgramNode node, int depth, Set<UUID> visited, DebugRecorder debug) {
        List<NpcBehaviorProgramNode> children = children(node);
        int startIndex = 0;
        UUID activeChildId = activeChildByComposite.get(node.getUid());
        if (activeChildId != null && !allowsLowerPriorityAbort(node)) {
            int activeIndex = indexOfChild(children, activeChildId);
            if (activeIndex >= 0) {
                Status result = evaluate(children.get(activeIndex), depth + 1, visited, debug);
                if (result == Status.RUNNING) {
                    activeChildByComposite.put(node.getUid(), activeChildId);
                    return Status.RUNNING;
                }
                activeChildByComposite.remove(node.getUid());
                if (result == Status.FAILURE) {
                    return Status.FAILURE;
                }
                startIndex = activeIndex + 1;
            } else {
                activeChildByComposite.remove(node.getUid());
            }
        }
        boolean executed = false;
        for (int i = startIndex; i < children.size(); i++) {
            NpcBehaviorProgramNode child = children.get(i);
            Status result = evaluate(child, depth + 1, visited, debug);
            if (result == Status.SKIPPED) {
                continue;
            }
            executed = true;
            if (result == Status.RUNNING) {
                activeChildByComposite.put(node.getUid(), child.getUid());
                return Status.RUNNING;
            }
            if (result != Status.SUCCESS) {
                activeChildByComposite.remove(node.getUid());
                return result;
            }
        }
        activeChildByComposite.remove(node.getUid());
        return executed ? Status.SUCCESS : Status.SKIPPED;
    }

    private Status evaluateSelector(NpcBehaviorProgramNode node, int depth, Set<UUID> visited, DebugRecorder debug) {
        List<NpcBehaviorProgramNode> children = children(node);
        if (children.isEmpty()) {
            debug.fail("selector_missing_children");
            return Status.FAILURE;
        }
        int startIndex = 0;
        UUID activeChildId = activeChildByComposite.get(node.getUid());
        if (activeChildId != null && !allowsLowerPriorityAbort(node)) {
            int activeIndex = indexOfChild(children, activeChildId);
            if (activeIndex >= 0) {
                Status result = evaluate(children.get(activeIndex), depth + 1, visited, debug);
                if (result == Status.RUNNING) {
                    activeChildByComposite.put(node.getUid(), activeChildId);
                    return Status.RUNNING;
                }
                activeChildByComposite.remove(node.getUid());
                if (result != Status.FAILURE && result != Status.SKIPPED) {
                    return result;
                }
                startIndex = activeIndex + 1;
            } else {
                activeChildByComposite.remove(node.getUid());
            }
        }
        boolean executed = false;
        for (int i = startIndex; i < children.size(); i++) {
            NpcBehaviorProgramNode child = children.get(i);
            Status result = evaluate(child, depth + 1, visited, debug);
            if (result == Status.SKIPPED) {
                continue;
            }
            executed = true;
            if (result == Status.RUNNING) {
                activeChildByComposite.put(node.getUid(), child.getUid());
                return Status.RUNNING;
            }
            if (result != Status.FAILURE) {
                activeChildByComposite.remove(node.getUid());
                return result;
            }
        }
        activeChildByComposite.remove(node.getUid());
        if (!executed) {
            return Status.SKIPPED;
        }
        debug.fail("selector_all_children_failed");
        return Status.FAILURE;
    }

    private Status evaluateInverter(NpcBehaviorProgramNode node, int depth, Set<UUID> visited, DebugRecorder debug) {
        NpcBehaviorProgramNode child = firstChild(node);
        if (child == null) {
            debug.fail("inverter_missing_child");
            return Status.FAILURE;
        }
        Status result = evaluate(child, depth + 1, visited, debug);
        return switch (result) {
            case SUCCESS -> {
                debug.fail("inverter_child_success");
                yield Status.FAILURE;
            }
            case FAILURE -> Status.SUCCESS;
            case RUNNING -> Status.RUNNING;
            case SKIPPED -> Status.SKIPPED;
        };
    }

    private NpcBehaviorProgramNode firstChild(NpcBehaviorProgramNode node) {
        List<NpcBehaviorProgramNode> children = children(node);
        return children.isEmpty() ? null : children.getFirst();
    }

    private List<NpcBehaviorProgramNode> children(NpcBehaviorProgramNode node) {
        List<NpcBehaviorProgramNode> children = new ArrayList<>();
        if (node.getChildren() != null) {
            for (UUID childId : node.getChildren()) {
                NpcBehaviorProgramNode child = programNodes.get(childId);
                if (child != null) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    private int indexOfChild(List<NpcBehaviorProgramNode> children, UUID childId) {
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).getUid().equals(childId)) {
                return i;
            }
        }
        return -1;
    }

    private boolean allowsLowerPriorityAbort(NpcBehaviorProgramNode node) {
        String abortType = node.getAbortType();
        return NpcBehaviorAbortType.LOWER_PRIORITY.getSerializedName().equals(abortType)
                || NpcBehaviorAbortType.BOTH.getSerializedName().equals(abortType);
    }

    private boolean shouldPauseAtBreakpoint(NpcBehaviorProgramNode node) {
        return node.isBreakpoint() && !breakpointBypassActive.contains(node.getUid());
    }

    private Status idle(NpcBehaviorProgramNode node) {
        UUID uid = node.getUid();
        int duration = Math.max(0, optionInt(node, IdleNode.OPTION_DURATION, 20));
        if (duration <= 0) {
            runningTicks.remove(uid);
            return Status.SUCCESS;
        }
        int remaining = runningTicks.computeIfAbsent(uid, ignored -> duration) - tickRate;
        if (remaining <= 0) {
            runningTicks.remove(uid);
            return Status.SUCCESS;
        }
        runningTicks.put(uid, remaining);
        return Status.RUNNING;
    }

    private Status moveToPosition(NpcBehaviorProgramNode node) {
        double x = optionFloat(node, MoveToPositionNode.OPTION_X, 0.0f);
        double y = optionFloat(node, MoveToPositionNode.OPTION_Y, 0.0f);
        double z = optionFloat(node, MoveToPositionNode.OPTION_Z, 0.0f);
        double speed = Math.max(0.0f, optionFloat(node, MoveToPositionNode.OPTION_SPEED, 1.0f));
        double stoppingDistance = Math.max(0.1f, optionFloat(node, MoveToPositionNode.OPTION_STOPPING_DISTANCE, 1.0f));
        if (npc.distanceToSqr(x, y, z) <= stoppingDistance * stoppingDistance) {
            npc.getNavigation().stop();
            return Status.SUCCESS;
        }
        if (npc.getNavigation().isDone() || npc.tickCount % 20 == 0) {
            npc.getNavigation().moveTo(x, y, z, speed);
        }
        return Status.RUNNING;
    }

    private Status findNearestPlayer(NpcBehaviorProgramNode node, DebugRecorder debug) {
        double range = Math.max(0.0f, optionFloat(node, FindNearestPlayerNode.OPTION_RANGE, 16.0f));
        if (editorDebugTarget != null) {
            if (isValidTarget(editorDebugTarget)
                    && !isEditorDebugIgnoredPlayer(editorDebugTarget)
                    && npc.distanceToSqr(editorDebugTarget) <= range * range) {
                npc.setTarget(editorDebugTarget);
                return Status.SUCCESS;
            }
            npc.setTarget(null);
            debug.fail("debug_target_out_of_range");
            return Status.FAILURE;
        }
        boolean ignoreCreative = optionBool(node, FindNearestPlayerNode.OPTION_IGNORE_CREATIVE, true);
        AABB searchBox = npc.getBoundingBox().inflate(range);
        Player nearest = null;
        double nearestDistance = range * range;
        for (Player player : npc.level().getEntitiesOfClass(Player.class, searchBox, player -> isValidPlayerTarget(player, ignoreCreative))) {
            double distance = npc.distanceToSqr(player);
            if (distance <= nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }
        if (nearest == null) {
            debug.fail("no_player_in_range");
            return Status.FAILURE;
        }
        npc.setTarget(nearest);
        return Status.SUCCESS;
    }

    private Status isTargetInRange(NpcBehaviorProgramNode node, DebugRecorder debug) {
        LivingEntity target = npc.getTarget();
        if (!isValidTarget(target)) {
            debug.fail("target_invalid");
            return Status.FAILURE;
        }
        double range = Math.max(0.0f, optionFloat(node, IsTargetInRangeNode.OPTION_RANGE, 4.0f));
        if (npc.distanceToSqr(target) <= range * range) {
            return Status.SUCCESS;
        }
        debug.fail("target_out_of_range");
        return Status.FAILURE;
    }

    private Status followTarget(NpcBehaviorProgramNode node, DebugRecorder debug) {
        LivingEntity target = npc.getTarget();
        if (!isValidTarget(target)) {
            npc.getNavigation().stop();
            debug.fail("target_invalid");
            return Status.FAILURE;
        }
        double speed = Math.max(0.0f, optionFloat(node, FollowTargetNode.OPTION_SPEED, 1.0f));
        double stoppingDistance = Math.max(0.1f, optionFloat(node, FollowTargetNode.OPTION_STOPPING_DISTANCE, 2.0f));
        if (npc.distanceToSqr(target) <= stoppingDistance * stoppingDistance) {
            npc.getNavigation().stop();
            return Status.SUCCESS;
        }
        if (npc.getNavigation().isDone() || npc.tickCount % 10 == 0) {
            npc.getNavigation().moveTo(target, speed);
        }
        return Status.RUNNING;
    }

    private Status lookAtTarget(NpcBehaviorProgramNode node, DebugRecorder debug) {
        LivingEntity target = npc.getTarget();
        if (!isValidTarget(target)) {
            debug.fail("target_invalid");
            return Status.FAILURE;
        }
        float yawSpeed = Math.max(0.0f, optionFloat(node, LookAtTargetNode.OPTION_YAW_SPEED, 30.0f));
        float pitchSpeed = Math.max(0.0f, optionFloat(node, LookAtTargetNode.OPTION_PITCH_SPEED, 30.0f));
        npc.getLookControl().setLookAt(target, yawSpeed, pitchSpeed);
        return Status.SUCCESS;
    }

    private Status meleeAttackTarget(NpcBehaviorProgramNode node, DebugRecorder debug) {
        LivingEntity target = npc.getTarget();
        if (!isValidTarget(target)) {
            debug.fail("target_invalid");
            return Status.FAILURE;
        }
        double range = Math.max(0.0f, optionFloat(node, MeleeAttackTargetNode.OPTION_RANGE, 2.0f));
        if (npc.distanceToSqr(target) > range * range) {
            debug.fail("target_out_of_attack_range");
            return Status.FAILURE;
        }
        UUID uid = node.getUid();
        if (cooldownTicks.getOrDefault(uid, 0) > 0) {
            return Status.RUNNING;
        }
        npc.getLookControl().setLookAt(target, 30.0F, 30.0F);
        npc.swing(InteractionHand.MAIN_HAND);
        boolean hit = npc.doHurtTarget(target);
        if (hit) {
            cooldownTicks.put(uid, Math.max(1, optionInt(node, MeleeAttackTargetNode.OPTION_COOLDOWN, 20)));
            return Status.SUCCESS;
        }
        debug.fail("attack_failed");
        return Status.FAILURE;
    }

    private Status hasTarget(DebugRecorder debug) {
        if (isValidTarget(npc.getTarget())) {
            return Status.SUCCESS;
        }
        debug.fail("target_missing");
        return Status.FAILURE;
    }

    private boolean isValidTarget(LivingEntity target) {
        return target != null && target.isAlive() && !target.isRemoved();
    }

    private boolean isValidPlayerTarget(Player player, boolean ignoreCreative) {
        return player != null && player.isAlive() && !player.isRemoved() && !player.isSpectator()
                && !isEditorDebugIgnoredPlayer(player)
                && (!ignoreCreative || !player.isCreative());
    }

    private boolean isEditorDebugIgnoredPlayer(@Nullable LivingEntity entity) {
        return entity instanceof Player player
                && editorDebugIgnoredPlayer != null
                && editorDebugIgnoredPlayer.equals(player.getUUID());
    }

    private int optionInt(NpcBehaviorProgramNode node, String id, int fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getInt(id) : fallback;
    }

    private float optionFloat(NpcBehaviorProgramNode node, String id, float fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getFloat(id) : fallback;
    }

    private boolean optionBool(NpcBehaviorProgramNode node, String id, boolean fallback) {
        CompoundTag options = node.getOptions();
        return options.contains(id) ? options.getBoolean(id) : fallback;
    }

    private void tickCooldowns() {
        cooldownTicks.replaceAll((id, value) -> Math.max(0, value - tickRate));
        cooldownTicks.entrySet().removeIf(entry -> entry.getValue() <= 0);
    }

    private void abortRunningNode(UUID nodeId, DebugRecorder debug) {
        NpcBehaviorProgramNode node = programNodes.get(nodeId);
        if (node == null) {
            return;
        }
        runningTicks.remove(nodeId);
        clearActiveChildState(node);
        if (NpcBehaviorNodeType.MOVE_TO_POSITION.equals(node.getType()) || NpcBehaviorNodeType.FOLLOW_TARGET.equals(node.getType())) {
            npc.getNavigation().stop();
        }
        debug.abort(node);
    }

    private void clearActiveChildState(NpcBehaviorProgramNode node) {
        activeChildByComposite.remove(node.getUid());
        for (NpcBehaviorProgramNode child : children(node)) {
            clearActiveChildState(child);
        }
    }

    private double targetDistance() {
        LivingEntity target = npc.getTarget();
        return isValidTarget(target) ? npc.distanceTo(target) : -1.0D;
    }

    private NpcBehaviorDebugSnapshot createIdleSnapshot() {
        DebugRecorder debug = new DebugRecorder();
        return debug.finish();
    }

    private void addDebugLog(String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        debugLogLines.addLast(line);
        while (debugLogLines.size() > MAX_DEBUG_LOG_LINES) {
            debugLogLines.removeFirst();
        }
    }

    private String gameObjectName() {
        String name = npc.getName().getString();
        if (name == null || name.isBlank()) {
            name = npc.getType().getDescription().getString();
        }
        return name + "[" + shortUuid(npc.getUUID()) + "]";
    }

    private String behaviorName() {
        String type = npc.getNpcType();
        return type == null || type.isBlank() ? "viscript_npc:behavior_tree" : type;
    }

    private static String shortUuid(UUID uuid) {
        if (uuid == null) {
            return "0000";
        }
        String value = uuid.toString();
        return value.length() <= 4 ? value : value.substring(0, 4);
    }

    public enum Status {
        SUCCESS,
        FAILURE,
        RUNNING,
        SKIPPED
    }

    private class DebugRecorder {
        private final NpcBehaviorDebugSnapshot snapshot = new NpcBehaviorDebugSnapshot();
        private final Map<UUID, NpcBehaviorDebugNodeSnapshot> nodeSnapshots = new LinkedHashMap<>();
        private final Set<UUID> currentRunningNodes = new HashSet<>();
        private String lastFailureReason = "";
        private String currentRunningNode = "";

        private DebugRecorder() {
            snapshot.setNpcUuid(npc.getUUID());
            snapshot.setGameObjectName(gameObjectName());
            snapshot.setBehaviorName(behaviorName());
            snapshot.setGameTime(npc.level().getGameTime());
            snapshot.setBreakpointPaused(breakpointPaused);
            snapshot.setBreakpointNode(breakpointNode == null ? "" : breakpointNode.toString());
            updateWatchVariables(snapshot);
            if (program != null && program.getNodes() != null) {
                for (NpcBehaviorProgramNode node : program.getNodes()) {
                    NpcBehaviorDebugNodeSnapshot nodeSnapshot = createNodeSnapshot(node);
                    nodeSnapshots.put(node.getUid(), nodeSnapshot);
                }
            }
        }

        private int visit(NpcBehaviorProgramNode node) {
            String nodeId = node.getUid().toString();
            snapshot.getVisitOrder().add(nodeId);
            logTaskChange(node, "PUSH", null);
            return snapshot.getVisitOrder().size() - 1;
        }

        private void fail(String reason) {
            lastFailureReason = reason == null ? "" : reason;
        }

        private void hitBreakpoint(NpcBehaviorProgramNode node) {
            NpcBehaviorDebugNodeSnapshot nodeSnapshot = nodeSnapshots.computeIfAbsent(node.getUid(), ignored -> createNodeSnapshot(node));
            nodeSnapshot.setBreakpointHit(true);
        }

        private void abort(NpcBehaviorProgramNode node) {
            NpcBehaviorDebugNodeSnapshot nodeSnapshot = nodeSnapshots.computeIfAbsent(node.getUid(), ignored -> createNodeSnapshot(node));
            nodeSnapshot.setStatus("ABORTED");
            nodeSnapshot.setLastTick(npc.tickCount);
            nodeSnapshot.setNavigationDone(npc.getNavigation().isDone());
            nodeSnapshot.setCooldownRemaining(cooldownTicks.getOrDefault(node.getUid(), 0));
            nodeSnapshot.setOptions(node.getOptions());
            logTaskChange(node, "ABORT", null);
        }

        private void record(NpcBehaviorProgramNode node, Status status, int visitIndex, long durationNanos) {
            NpcBehaviorDebugNodeSnapshot nodeSnapshot = nodeSnapshots.computeIfAbsent(node.getUid(), ignored -> createNodeSnapshot(node));
            nodeSnapshot.setStatus(status.name());
            nodeSnapshot.setVisitIndex(visitIndex);
            nodeSnapshot.setBreakpointHit(breakpointNode != null && breakpointNode.equals(node.getUid()));
            nodeSnapshot.setLastTick(npc.tickCount);
            nodeSnapshot.setDurationNanos(durationNanos);
            nodeSnapshot.setTargetDistance(targetDistance());
            nodeSnapshot.setNavigationDone(npc.getNavigation().isDone());
            nodeSnapshot.setCooldownRemaining(cooldownTicks.getOrDefault(node.getUid(), 0));
            nodeSnapshot.setOptions(node.getOptions());
            if (status == Status.FAILURE) {
                nodeSnapshot.setFailureReason(lastFailureReason);
            }
            if (status == Status.RUNNING && currentRunningNode.isEmpty()) {
                currentRunningNode = node.getUid().toString();
            }
            if (status == Status.RUNNING) {
                currentRunningNodes.add(node.getUid());
            }
            if (status == Status.SUCCESS || status == Status.FAILURE) {
                logTaskChange(node, "POP", status);
            }
        }

        private Set<UUID> currentRunningNodes() {
            return currentRunningNodes;
        }

        private NpcBehaviorDebugSnapshot finish() {
            snapshot.setCurrentNode(currentRunningNode);
            snapshot.setBreakpointPaused(breakpointPaused);
            snapshot.setBreakpointNode(breakpointNode == null ? "" : breakpointNode.toString());
            snapshot.setLastFailureReason(lastFailureReason);
            snapshot.setLogs(new ArrayList<>(debugLogLines));
            snapshot.setNodes(new ArrayList<>(nodeSnapshots.values()));
            updateWatchVariables(snapshot);
            return snapshot;
        }

        private NpcBehaviorDebugNodeSnapshot createNodeSnapshot(NpcBehaviorProgramNode node) {
            NpcBehaviorDebugNodeSnapshot snapshot = new NpcBehaviorDebugNodeSnapshot();
            snapshot.setNodeId(node.getUid().toString());
            snapshot.setType(node.getType());
            snapshot.setDisplayName(node.getDisplayName());
            snapshot.setTaskIndex(node.getTaskIndex());
            snapshot.setDisabled(node.isDisabled());
            snapshot.setBreakpoint(node.isBreakpoint());
            snapshot.setBreakpointHit(breakpointNode != null && breakpointNode.equals(node.getUid()));
            snapshot.setAbortType(node.getAbortType());
            snapshot.setOptions(node.getOptions());
            return snapshot;
        }

        private void updateWatchVariables(NpcBehaviorDebugSnapshot snapshot) {
            snapshot.setBlackboardLines(new ArrayList<>());
            LivingEntity target = npc.getTarget();
            if (isValidTarget(target)) {
                snapshot.setTargetUuid(target.getUUID());
                snapshot.setTargetName(target.getName().getString());
                snapshot.getBlackboardLines().add("CurrentTarget = " + target.getName().getString() + "[" + shortUuid(target.getUUID()) + "]");
                snapshot.getBlackboardLines().add("CurrentTargetUuid = " + target.getUUID());
            } else {
                snapshot.setTargetUuid(NpcBehaviorProgram.EMPTY_UUID);
                snapshot.setTargetName("");
                snapshot.getBlackboardLines().add("CurrentTarget = <none>");
            }
            snapshot.setTargetDistance(targetDistance());
            snapshot.setHealth(npc.getHealth());
            snapshot.setNavigationDone(npc.getNavigation().isDone());
            snapshot.getBlackboardLines().add("TargetDistance = " + String.format(Locale.ROOT, "%.2f", snapshot.getTargetDistance()));
            snapshot.getBlackboardLines().add("Health = " + String.format(Locale.ROOT, "%.1f / %.1f", npc.getHealth(), npc.getMaxHealth()));
            snapshot.getBlackboardLines().add("NavigationDone = " + npc.getNavigation().isDone());
            snapshot.getBlackboardLines().add("NpcPos = " + String.format(Locale.ROOT, "%.1f, %.1f, %.1f", npc.getX(), npc.getY(), npc.getZ()));
        }

        private void logTaskChange(NpcBehaviorProgramNode node, String change, @Nullable Status status) {
            StringBuilder line = new StringBuilder();
            line.append(gameObjectName())
                    .append(' ')
                    .append(behaviorName())
                    .append(' ')
                    .append(change)
                    .append(' ')
                    .append(node.getType())
                    .append(" task=")
                    .append(node.getTaskIndex())
                    .append(" stack=0");
            if ("POP".equals(change) && (status == Status.SUCCESS || status == Status.FAILURE)) {
                line.append(" [").append(status.name()).append(']');
            }
            addDebugLog(line.toString());
        }
    }
}
