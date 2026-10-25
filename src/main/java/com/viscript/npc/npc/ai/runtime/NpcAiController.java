package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.data.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将 NPC 配置与独立行为定义绑定到单个服务器执行实例。 */
public final class NpcAiController {
    private final CustomNpc npc;
    private final Map<String, AiValue> blackboard = new LinkedHashMap<>();
    private AiExecution execution;
    private AiProgram configured;
    private AiStatus status = AiStatus.SUCCESS;
    private boolean manual;
    public final AiDebugger debugger = new AiDebugger();
    private int stepRequested;
    private boolean tickInProgress;
    private boolean navigationStep;
    public boolean isDebugPaused() { return debugger.enabled && debugger.paused; }
    public boolean canTickNavigation() { return !isDebugPaused() || navigationStep; }
    private long generation;
    private int tickingHits;
    private record HitTask(AiExecution execution, java.util.function.BooleanSupplier valid, long expires) {}
    private final java.util.List<HitTask> hits = new java.util.ArrayList<>();
    private final java.util.List<HitTask> tickHits = new java.util.ArrayList<>();
    private int hitCursor;
    public long generation() { return generation; }
    public void enqueueHit(AiExecution branch, java.util.function.BooleanSupplier valid) {
        if (hits.size() + tickingHits < 64) hits.add(new HitTask(branch, valid, npc.level().getGameTime() + 72000));
        else branch.cancel();
    }
    private String reason = "";

    public NpcAiController(CustomNpc npc) { this.npc = npc; }

    public void tick() {
        navigationStep = false;
        if (isDebugPaused() && stepRequested == 0) return;
        int stepping = stepRequested; stepRequested = 0;
        if (!tickInProgress) {
            prepareExecution();
            if (debugger.enabled) debugger.beginTick();
            tickHits.addAll(hits); hits.clear(); hitCursor = 0;
            tickingHits = tickHits.size();
            tickInProgress = true;
        }
        if (stepping != 0) debugger.step(stepping == 1);
        boolean completed = false;
        try { completed = tickExecutions(); }
        finally {
            if (completed) { tickInProgress = false; tickHits.clear(); }
            navigationStep = stepping != 0 && completed && !debugger.paused;
            if (stepping != 0) debugger.pause();
        }
    }

    private boolean tickExecutions() {
        // 命中任务和主树共用本轮游标；暂停后不会重复推进已经执行过的命中分支。
        while (hitCursor < tickHits.size()) {
            var task = tickHits.get(hitCursor);
            if (!npc.isAlive() || !task.valid.getAsBoolean() || npc.level().getGameTime() > task.expires) task.execution.cancel();
            else {
                var result = task.execution.tick();
                if (debugger.paused) return false;
                if (result == AiStatus.RUNNING) hits.add(task);
            }
            hitCursor++; tickingHits--;
        }
        if (debugger.paused) return false;
        if (manual || !npc.getNpcAI().isEnabled() || !npc.isAlive()) return true;
        if (execution != null) {
            status = execution.tick();
            reason = execution.reason;
        }
        return !debugger.paused;
    }

    private void prepareExecution() {
        if (manual || !npc.getNpcAI().isEnabled() || !npc.isAlive()) return;
        AiProgram program = npc.getNpcAI().getProgram();
        if (configured != program && (execution == null || !execution.isRunning())) {
            cancel();
            configured = program;
            blackboard.clear();
            program.variables.forEach(v -> blackboard.put(v.id, v.initial.copy()));
            var issues = AiValidation.validate(program, npc.getNpcAI()::resolveAction);
            if (!issues.isEmpty()) { reason = issues.getFirst().key(); return; }
            execution = new AiExecution(npc, program, blackboard, Map.of(), npc.getNpcAI()::resolveAction);
            execution.attachDebugger(debugger);
        }
    }

    public void preview(AiProgram program, Map<String, AiProgram> dependencies) {
        apply(program, dependencies, true);
        debugger.enabled = true;
    }

    /**
     * 取消正在运行的旧树、命中分支及导航，提交独立快照并从根节点重新执行。
     * 调用方须先校验完整树与依赖。
     *
     * @param program 已校验的行为树
     * @param dependencies 树引用的完整行为定义快照
     * @param enabled 提交后的 AI 开关
     */
    public void apply(AiProgram program, Map<String, AiProgram> dependencies, boolean enabled) {
        var definitions = new LinkedHashMap<String, AiProgram>();
        dependencies.forEach((id, action) -> definitions.put(id, action.copy(npc.registryAccess())));
        var tree = program.copy(npc.registryAccess());
        cancel();
        npc.getNpcAI().setActionDefinitions(definitions);
        npc.getNpcAI().setProgram(tree);
        npc.getNpcAI().setEnabled(enabled);
        manual = false;
        stepRequested = 0;
        navigationStep = false;
        debugger.reset();
    }

    public void manualControl() { cancel(); manual = true; }
    public void resume() { manual = false; stepRequested = 0; debugger.resume(); }
    public void pause() { debugger.enabled = true; stepRequested = 0; debugger.pause(); }
    /** 请求只进入一个新的节点执行位置；同一轮中的父节点与 AI 时钟保持原进度。 */
    public void step() { requestStep(1); }
    /** 请求完成当前 AI 轮次；当前没有未完成轮次时推进一轮。 */
    public void stepTick() { requestStep(2); }
    private void requestStep(int mode) {
        debugger.enabled = true; stepRequested = mode; manual = false;
        npc.getNpcAI().setEnabled(true);
    }
    public void cancel() {
        generation++;
        hits.forEach(task -> task.execution.cancel()); hits.clear();
        for (int i = hitCursor; i < tickHits.size(); i++) tickHits.get(i).execution.cancel();
        tickHits.clear(); hitCursor = 0; tickingHits = 0; tickInProgress = false;
        stepRequested = 0; navigationStep = false; debugger.clearPosition();
        if (execution != null) execution.cancel();
        execution = null;
        configured = null;
        reason = "";
        npc.getNpcNavigation().cancel();
    }
    public AiDebugSnapshot snapshot() {
        AiDebugSnapshot data = new AiDebugSnapshot();
        data.entityId = npc.getId();
        if (execution != null) {
            data.activeNodes.addAll(execution.activeNodes()); data.phase = execution.phase;
            data.perceptions.addAll(execution.perceptions());
            execution.resolvedInputs.forEach((key, value) -> data.inputs.put(key, value.copy()));
        }
        hits.forEach(task -> {
            data.activeNodes.addAll(task.execution.activeNodes());
            task.execution.resolvedInputs.forEach((key, value) -> data.inputs.put(key, value.copy()));
            data.perceptions.addAll(task.execution.perceptions());
        });
        for (int i = hitCursor; i < tickHits.size(); i++) {
            var pending = tickHits.get(i).execution;
            data.activeNodes.addAll(pending.activeNodes());
            pending.resolvedInputs.forEach((key, value) -> data.inputs.put(key, value.copy()));
            data.perceptions.addAll(pending.perceptions());
        }
        data.paused = manual || isDebugPaused();
        data.enabled = npc.getNpcAI().isEnabled();
        data.status = data.paused ? "paused" : status.name().toLowerCase(java.util.Locale.ROOT);
        data.logging = debugger.logging;
        data.tick = debugger.tick;
        data.breakpoint = debugger.breakpoint;
        data.tickInProgress = tickInProgress;
        data.currentNode = debugger.currentNode; data.currentOperation = debugger.currentOperation;
        data.currentAction = debugger.currentAction; data.currentDepth = debugger.currentDepth;
        data.lastNode = debugger.lastNode; data.lastOperation = debugger.lastOperation;
        data.lastAction = debugger.lastAction; data.lastResult = debugger.lastResult;
        data.breakpoints.addAll(debugger.breakpoints);
        data.nodeStates.putAll(debugger.states);
        data.values.putAll(debugger.values);
        data.events.addAll(debugger.events);
        data.lines.addAll(debugger.lines);
        data.reason = reason;
        data.navigationStatus = npc.getNpcNavigation().status().name();
        data.targetId = npc.getTarget() == null ? -1 : npc.getTarget().getId();
        blackboard.forEach((key, value) -> data.variables.put(key, value.copy()));
        npc.getNpcNavigation().path().forEach(step -> data.path.add(step.position()));
        data.navigationQueueMs = npc.getNpcNavigation().queueNanos() / 1_000_000.0;
        data.navigationSearchMs = npc.getNpcNavigation().searchNanos() / 1_000_000.0;
        return data;
    }
}
