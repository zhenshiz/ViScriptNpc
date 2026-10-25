package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** 使用真实实体和投射物验证实体引用、作用对象和异步命中上下文。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiEntityTests {
    private static CustomNpc npc(GameTestHelper t) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 8; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(2, 1, 2));
        npc.getAiController().manualControl(); return npc;
    }
    private static AiProgram program() { var p = new AiProgram(); p.kind = "action"; return p; }
    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var n = new AiNodeData(); n.type = type; for (var child : children) n.children.add(child.id); p.nodes.add(n); return n;
    }
    private static void root(AiProgram p, AiNodeData n) { p.root = node(p, "root", n).id; }
    private static AiVariable variable(AiProgram p, String name, AiValue initial) {
        var v = new AiVariable(); v.name = name; v.initial = initial; p.variables.add(v); return v;
    }
    private static AiBinding binding(String scope, String id, AiValue fallback) {
        var b = new AiBinding(); b.scope = scope; b.variableId = id; b.value = fallback; return b;
    }
    private static AiNodeData recipient(AiNodeData node, String context) {
        node.inputs.put("recipient", binding("context", context, AiValue.entity(new AiEntityRef()))); return node;
    }
    private static AiExecution execution(CustomNpc npc, AiProgram p) {
        var issues = AiValidation.validate(p, ignored -> null);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.toString());
        return new AiExecution(npc, p, new HashMap<>(), Map.of(), ignored -> null);
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void entityReferencePersistsAndRejectsStaleEntities(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 2));
        var value = AiValue.entity(cow); var restored = new AiValue(); restored.deserializeNBT(t.getLevel().registryAccess(), value.serializeNBT(t.getLevel().registryAccess()));
        t.assertTrue(restored.entity.resolve(t.getLevel()) == cow, "reference round-trips through LDLib2");
        var copy = value.copy(); copy.entity.uuid = "";
        t.assertTrue(!value.entity.uuid.isEmpty(), "copies do not share mutable reference data");
        restored.entity.dimension = "minecraft:the_nether";
        t.assertTrue(restored.entity.resolve(t.getLevel()) == null, "foreign dimensions cannot resolve");
        cow.discard(); t.assertTrue(value.entity.resolve(t.getLevel()) == null, "removed entity cannot resolve"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void findEntityStoresReferenceWithoutChangingCurrentTarget(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 2));
        var other = t.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(7, 1, 2)); npc.setTarget(other);
        var p = program(); var entity = variable(p, "found", AiValue.entity(new AiEntityRef()));
        var health = variable(p, "health", AiValue.number(0));
        var find = node(p, "find_target").input("range", AiValue.number(3)).input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living"))).input("entity_type", AiValue.mobType("minecraft:cow"))
                .input("set_current_target", AiValue.bool(false)).input("output", AiValue.text(entity.id));
        var read = node(p, "entity_info").input("output", AiValue.text(health.id));
        read.inputs.put("recipient", binding("local", entity.id, entity.initial));
        root(p, node(p, "sequence", find, read)); npc.tickCount = Math.floorMod(-npc.getId(), 5);
        var e = execution(npc, p); t.assertTrue(e.tick() == AiStatus.SUCCESS, "query flow completes");
        t.assertTrue(npc.getTarget() == other && e.local.get(entity.id).entity.resolve(t.getLevel()) == cow, "selection and stored entity are independent");
        t.assertTrue(e.local.get(health.id).number == cow.getHealth(), "entity info uses the stored reference");
        cow.discard(); npc.tickCount = Math.floorMod(-npc.getId(), 5);
        t.assertTrue(e.tick() == AiStatus.FAILURE && e.local.get(entity.id).entity.uuid.isEmpty(), "failed searches clear stale output"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void entityRecipientsAndTypedOutputsAreValidated(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 2)); npc.setTarget(cow);
        var p = program(); var effect = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:speed")), "self"); root(p, effect);
        t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS && npc.hasEffect(MobEffects.MOVEMENT_SPEED) && !cow.hasEffect(MobEffects.MOVEMENT_SPEED), "beneficial effect can target the NPC itself");
        var q = program(); var pos = variable(q, "position", AiValue.position(net.minecraft.world.phys.Vec3.ZERO));
        var info = recipient(node(q, "entity_info").input("property", AiValue.choice("position", java.util.List.of("health", "max_health", "health_ratio", "alive", "position", "entity_type"))).input("output", AiValue.text(pos.id)), "target"); root(q, info);
        var e = execution(npc, q); e.tick(); t.assertTrue(e.local.get(pos.id).position.equals(cow.position()), "position output uses a position variable");
        info.input("property", AiValue.choice("health", java.util.List.of("health", "max_health", "health_ratio", "alive", "position", "entity_type")));
        t.assertTrue(AiValidation.validate(q, ignored -> null).stream().anyMatch(i -> i.key().equals("binding")), "wrong output type blocks publication");
        effect.onHit = effect.id;
        t.assertTrue(!AiValidation.validate(p, ignored -> null).isEmpty(), "non-attack and cyclic hit branches are rejected"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void rangedHitUsesActualVictimAfterTargetChanges(GameTestHelper t) {
        var npc = npc(t); var victim = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 2));
        var other = t.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(7, 1, 6)); npc.setTarget(victim);
        var p = program(); var seen = variable(p, "hit", AiValue.entity(new AiEntityRef()));
        var mark = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:glowing")).input("seconds", AiValue.number(3)), "hit");
        var record = recipient(node(p, "get_entity").input("output", AiValue.text(seen.id)), "hit");
        // 黑板可观测命中分支的结果，局部变量仍然按每次命中隔离。
        var shared = new AiVariable(); shared.name = "hit"; shared.initial = seen.initial.copy(); p.npcVariables.add(shared);
        var store = node(p, "set_variable").input("scope", AiValue.choice("blackboard", java.util.List.of("local", "blackboard"))).input("variable", AiValue.text(shared.id));
        store.inputs.put("value", binding("local", seen.id, seen.initial));
        var shoot = node(p, "ranged").input("damage", AiValue.number(1)).input("speed", AiValue.number(.7));
        shoot.onHit = node(p, "sequence", node(p, "wait").input("seconds", AiValue.number(.1)), mark, record, store).id;
        root(p, shoot); Map<String, AiValue> blackboard = new HashMap<>(); blackboard.put(shared.id, shared.initial.copy());
        t.assertTrue(AiValidation.validate(p, ignored -> null).isEmpty(), "hit branch is part of validation");
        var e = new AiExecution(npc, p, blackboard, Map.of(), ignored -> null);
        t.assertTrue(e.tick() == AiStatus.SUCCESS && !victim.hasEffect(MobEffects.GLOWING), "launch success precedes impact and does not block the main flow");
        npc.setTarget(other);
        t.succeedWhen(() -> {
            t.assertTrue(victim.hasEffect(MobEffects.GLOWING), "actual projectile victim receives effect");
            t.assertTrue(!other.hasEffect(MobEffects.GLOWING), "new NPC target receives no effect");
            t.assertTrue(blackboard.get(shared.id).entity.resolve(t.getLevel()) == victim, "hit branch can save the actual entity for later operations");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void simultaneousProjectilesKeepTheirCastLocalValues(GameTestHelper t) {
        var npc = npc(t); var a = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 2));
        var b = t.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 5));
        var p = program(); var duration = variable(p, "duration", AiValue.number(1));
        var effect = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:glowing")), "hit");
        effect.inputs.put("seconds", binding("local", duration.id, duration.initial));
        var shoot = node(p, "ranged").input("damage", AiValue.number(1)).input("speed", AiValue.number(.8)); shoot.onHit = effect.id; root(p, shoot);
        var e = execution(npc, p); npc.setTarget(a); e.tick();
        e.local.put(duration.id, AiValue.number(3)); npc.setTarget(b); e.tick();
        e.local.put(duration.id, AiValue.number(9)); npc.setTarget(null);
        t.succeedWhen(() -> {
            t.assertTrue(a.hasEffect(MobEffects.GLOWING) && b.hasEffect(MobEffects.GLOWING), "both actual projectiles hit their own victims");
            int first = a.getEffect(MobEffects.GLOWING).getDuration(), second = b.getEffect(MobEffects.GLOWING).getDuration();
            t.assertTrue(first <= 20 && second <= 60 && second - first >= 30, "each callback reads its own launch-time local snapshot");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void rejectedDamageDoesNotRunHitEffects(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 2)); cow.setInvulnerable(true); npc.setTarget(cow);
        var p = program(); var effect = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:glowing")), "hit");
        var shoot = node(p, "ranged").input("speed", AiValue.number(.8)); shoot.onHit = effect.id; root(p, shoot);
        t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS, "launching is independent of damage acceptance");
        t.runAfterDelay(30, () -> { t.assertTrue(!cow.hasEffect(MobEffects.GLOWING), "rejected damage does not count as a successful hit"); t.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 80)
    public static void cancelledCastCannotStartItsDelayedHitBranch(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(5, 1, 2)); npc.setTarget(cow);
        var p = program(); var effect = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:glowing")), "hit");
        var shoot = node(p, "ranged").input("speed", AiValue.number(.7)); shoot.onHit = effect.id; root(p, shoot);
        var e = execution(npc, p); e.tick(); e.cancel();
        t.runAfterDelay(35, () -> { t.assertTrue(!cow.hasEffect(MobEffects.GLOWING), "cancelled invocation does not deliver its callback"); t.succeed(); });
    }

    @GameTest(template = "empty", timeoutTicks = 60)
    public static void areaHitsUseIndependentContexts(GameTestHelper t) {
        var npc = npc(t); var a = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(3, 1, 2));
        var b = t.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(2, 1, 4));
        var p = program(); var effect = recipient(node(p, "effect").input("effect", AiValue.text("minecraft:glowing")), "hit");
        var hit = node(p, "area_damage").input("damage", AiValue.number(1)); hit.onHit = node(p, "sequence", node(p, "wait").input("seconds", AiValue.number(.1)), effect).id;
        root(p, hit); t.assertTrue(execution(npc, p).tick() == AiStatus.SUCCESS, "area attack completes immediately");
        npc.setTarget(null);
        t.succeedWhen(() -> { t.assertTrue(a.hasEffect(MobEffects.GLOWING) && b.hasEffect(MobEffects.GLOWING), "each delayed callback keeps its own hit entity"); });
    }
}
