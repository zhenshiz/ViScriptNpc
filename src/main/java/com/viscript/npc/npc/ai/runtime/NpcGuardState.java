package com.viscript.npc.npc.ai.runtime;

import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.ai.data.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** 单个 NPC 的驻守点和战斗记忆；仅驻守点随实体保存，计时使用 AI 轮次。 */
public final class NpcGuardState {
    private final CustomNpc npc;
    private Vec3 home;
    private String homeDimension = "";
    private long clock, seenAt, scannedAt = -5;
    private UUID remembered;
    private Vec3 lastKnown;
    private boolean active, returning;
    private String state = "idle";
    private record Threat(AiEntityRef entity, Vec3 position, double damage, long at) {}
    private final Map<UUID, Threat> threats = new LinkedHashMap<>();

    public NpcGuardState(CustomNpc npc) { this.npc = npc; }
    public boolean hasHome() { return home != null && homeDimension.equals(npc.level().dimension().location().toString()); }
    public Vec3 home() { if (!hasHome()) setHome(npc.position()); return home; }
    public boolean active() { return active; }
    public boolean returning() { return returning; }
    public String state() { return state; }
    public Vec3 pursuitPosition(LivingEntity target) {
        return active && state.equals("searching") && target.getUUID().equals(remembered) && lastKnown != null
                ? lastKnown : target.position();
    }
    public void setHome(Vec3 position) {
        if (!Double.isFinite(position.lengthSqr())) throw new IllegalArgumentException("Invalid home position");
        home = position; homeDimension = npc.level().dimension().location().toString();
        reset();
    }
    public void reset() {
        active = returning = false; remembered = null; lastKnown = null;
        threats.clear(); clock = seenAt = 0; scannedAt = -5; state = "idle";
    }
    public void beginReturn() {
        returning = true; remembered = null; lastKnown = null; threats.clear();
        npc.setTarget(null); state = "returning";
    }
    public void arrived() { returning = false; state = "patrolling"; }

    /** 只由实际伤害结算事件调用；不会为被取消、免疫或零伤害建立仇恨。 */
    public void damagedBy(LivingEntity attacker, double damage) {
        if (!active || returning || damage <= 0 || !Double.isFinite(damage) || !AiPerception.canTarget(npc, attacker)) return;
        if (!threats.containsKey(attacker.getUUID()) && threats.size() >= 64) threats.remove(threats.keySet().iterator().next());
        var old = threats.get(attacker.getUUID());
        threats.put(attacker.getUUID(), new Threat(AiEntityRef.of(attacker), attacker.position(),
                Math.min(1_000_000, damage + (old == null ? 0 : old.damage)), clock));
        scannedAt = -5;
    }

    public void update(AiExecution e, AiNodeData node) {
        double leash = e.number(node, "leash_range"), arrival = e.number(node, "home_range");
        if (arrival > leash) throw new AiExecution.InputException("distance_range");
        int memory = (int) Math.ceil(e.number(node, "memory_seconds") * 20);
        home(); active = true; clock = e.time();
        var current = npc.getTarget();
        if (npc.position().distanceToSqr(home) > leash * leash
                || current != null && (!withinLeash(current, leash))) beginReturn();
        if (returning) {
            npc.setTarget(null);
            if (npc.position().distanceTo(home) <= arrival) arrived();
            else return;
        }
        threats.entrySet().removeIf(entry -> clock - entry.getValue().at > Math.max(1, memory)
                || !withinLeash(entry.getValue().entity.resolve((ServerLevel) npc.level()), leash));
        var volume = AiPerception.volume(e, node); e.recordPerception(node.id, volume);
        if (clock - scannedAt >= 5 || current == null || !AiPerception.canTarget(npc, current)) {
            scannedAt = clock;
            LivingEntity found = null;
            if (e.flag(node, "retaliate")) {
                var threat = threats.values().stream().max(Comparator.comparingDouble(Threat::damage)).orElse(null);
                if (threat != null && threat.entity.resolve((ServerLevel) npc.level()) instanceof LivingEntity attacker) {
                    found = attacker; remembered = attacker.getUUID(); lastKnown = threat.position; seenAt = threat.at;
                }
            }
            if (found == null) found = AiPerception.find(e, node, volume, true, entity -> withinLeash(entity, leash));
            if (found != null) {
                boolean visible = !volume.requireSight || npc.hasLineOfSight(found);
                if (visible) { remembered = found.getUUID(); lastKnown = found.position(); seenAt = clock; }
                npc.setTarget(found); current = npc.getTarget();
            }
        }
        current = npc.getTarget();
        if (current != null && AiPerception.canTarget(npc, current) && withinLeash(current, leash)) {
            if (volume.contains(current.position()) && (!volume.requireSight || npc.hasLineOfSight(current))) {
                remembered = current.getUUID(); lastKnown = current.position(); seenAt = clock; state = "combat";
            } else if (current.getUUID().equals(remembered) && clock - seenAt <= memory) state = "searching";
            else beginReturn();
        } else if (remembered != null) beginReturn();
        else { npc.setTarget(null); state = "patrolling"; }
    }

    private boolean withinLeash(net.minecraft.world.entity.Entity entity, double leash) {
        return entity instanceof LivingEntity living && AiPerception.canTarget(npc, living)
                && entity.position().distanceToSqr(home) <= leash * leash;
    }
    public CompoundTag save() {
        var tag = new CompoundTag();
        if (home != null) {
            tag.putString("dimension", homeDimension); tag.putDouble("x", home.x); tag.putDouble("y", home.y); tag.putDouble("z", home.z);
        }
        return tag;
    }
    public void load(CompoundTag tag) {
        home = null; homeDimension = ""; reset();
        if (tag.contains("dimension")) {
            var position = new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z"));
            if (Double.isFinite(position.lengthSqr())) { home = position; homeDimension = tag.getString("dimension"); }
        }
    }
}
