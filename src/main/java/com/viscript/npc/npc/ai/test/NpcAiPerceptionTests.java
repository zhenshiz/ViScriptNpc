package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.*;
import com.viscript.npc.util.NpcActionFiles;
import com.viscript.npc.util.NpcAiPublication;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;

import java.util.*;

/** 验证方向几何、遮挡、持续感知抢占和巡逻中心在真实实体上的行为。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiPerceptionTests {
    private static CustomNpc npc(GameTestHelper t) {
        for (int x = 0; x < 10; x++) for (int z = 0; z < 8; z++) t.setBlock(x, 0, z, Blocks.STONE);
        var npc = t.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(4, 1, 3));
        npc.getAiController().manualControl(); npc.setYHeadRot(0); return npc;
    }
    private static AiNodeData node(AiProgram p, String type, AiNodeData... children) {
        var n = new AiNodeData(); n.type = type; for (var child : children) n.children.add(child.id); p.nodes.add(n); return n;
    }
    private static void root(AiProgram p, AiNodeData n) { p.root = node(p, "root", n).id; }
    private static AiExecution execution(CustomNpc npc, AiProgram p) {
        var issues = AiValidation.validate(p, NpcActionFiles::load);
        if (!issues.isEmpty()) throw new IllegalArgumentException(issues.toString());
        return new AiExecution(npc, p, new HashMap<>(), Map.of(), NpcActionFiles::load);
    }
    private static void scan(CustomNpc npc, AiExecution e) { npc.tickCount = Math.floorMod(-npc.getId(), 5); e.tick(); }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void cylinderAndRotatedSectorShareExactBoundaries(GameTestHelper t) {
        var v = new AiPerceptionVolume(); v.mode = "combined"; v.origin = new Vec3(31, 64, -19);
        v.range = 3; v.viewRadius = 12; v.viewAngle = 90; v.height = 6;
        t.assertTrue(v.contains(v.origin.add(0, 0, -2.9)) && !v.contains(v.origin.add(0, 0, -3.1)), "behind NPC only proximity detects");
        t.assertTrue(v.contains(v.origin.add(5, 0, 5)) && !v.contains(v.origin.add(5.01, 0, 5)), "90 degree opening includes 45 degree boundary only");
        t.assertTrue(v.contains(v.viewPoint(45, 3)) && !v.contains(v.viewPoint(45.01, 0)), "drawn arc and side boundary match detection");
        t.assertTrue(!v.contains(v.origin.add(0, 0, 12.01)) && !v.contains(v.origin.add(0, 3.01, 2)), "radius and vertical height apply");
        v.mode = "range"; t.assertTrue(!v.contains(v.origin.add(2.2, 0, 2.2)), "radius uses a circle rather than the query AABB");
        v.mode = "view"; v.forward = new Vec3(-1, 0, 0);
        t.assertTrue(v.contains(v.origin.add(-10, 0, 3.9)) && !v.contains(v.origin.add(1, 0, 0)), "sector rotates with heading");
        var copy = new AiPerceptionVolume(); copy.deserializeNBT(t.getLevel().registryAccess(), v.serializeNBT(t.getLevel().registryAccess()));
        t.assertTrue(copy.contains(v.viewPoint(-45, 3)) && copy.viewAngle == 90 && copy.viewRadius == 12
                && copy.origin.equals(v.origin) && copy.forward.equals(v.forward), "debug packet geometry round trips through LDLib2");
        v.forward = new Vec3(0, 0, 1); v.viewAngle = 180;
        t.assertTrue(v.contains(v.origin.add(10, 0, 0)) && !v.contains(v.origin.add(10, 0, -.01)), "180 degrees sees the front half plane");
        v.viewAngle = 270;
        t.assertTrue(v.contains(v.viewPoint(135, 0)) && !v.contains(v.viewPoint(135.01, 0)), "wide sectors include side rear positions");
        v.viewAngle = 360; t.assertTrue(v.contains(v.origin.add(0, 0, -12)), "360 degrees covers the rear boundary");
        v.viewAngle = 0; t.assertTrue(!v.contains(v.origin) && !v.contains(v.origin.add(0, 0, 1)), "zero opening disables view");
        v.viewAngle = 90; v.viewRadius = 0; t.assertTrue(!v.contains(v.origin), "zero radius disables view");
        t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void targetQuerySupportsBehindCloseViewOcclusionAndStoredEntity(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 5));
        var p = new AiProgram(); p.kind = "action";
        var output = new AiVariable(); output.initial = AiValue.entity(new AiEntityRef()); p.variables.add(output);
        var find = node(p, "find_target").input("perception", AiValue.choice("combined", java.util.List.of("range", "view", "combined")))
                .input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living"))).input("entity_type", AiValue.mobType("minecraft:cow"))
                .input("range", AiValue.number(1.5)).input("view_radius", AiValue.number(4)).input("view_angle", AiValue.number(45))
                .input("require_sight", AiValue.bool(true)).input("output", AiValue.text(output.id)); root(p, find);
        var e = execution(npc, p); scan(npc, e);
        t.assertTrue(npc.getTarget() == cow && e.local.get(output.id).entity.resolve(t.getLevel()) == cow, "forward sector discovers and stores entity");
        cow.setPos(npc.position().add(0, 0, -2)); scan(npc, e);
        t.assertTrue(npc.getTarget() == null && e.local.get(output.id).entity.uuid.isEmpty(), "distant rear target hides and old output clears");
        cow.setPos(npc.position().add(0, 0, -1)); scan(npc, e); t.assertTrue(npc.getTarget() == cow, "near rear target is discovered");
        cow.setPos(npc.position().add(0, 0, 3));
        for (int y = 1; y <= 3; y++) t.setBlock(4, y, 4, Blocks.STONE);
        scan(npc, e); t.assertTrue(npc.getTarget() == null, "wall blocks sector vision");
        find.input("require_sight", AiValue.bool(false)); e = execution(npc, p); scan(npc, e);
        t.assertTrue(npc.getTarget() == cow, "occlusion can be disabled explicitly");
        cow.setPos(npc.position().add(2, 0, 3)); scan(npc, e); t.assertTrue(npc.getTarget() == null, "side target outside 45 degree opening stays hidden");
        find.input("view_angle", AiValue.number(90)); e = execution(npc, p); scan(npc, e);
        t.assertTrue(npc.getTarget() == cow, "wider configured opening detects the same side target");
        cow.setPos(npc.position().add(0, 0, 4.01)); scan(npc, e); t.assertTrue(npc.getTarget() == null, "radius limits actual entity detection"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void continuousSensingInterruptsLongPatrolAndReturnsAfterLoss(GameTestHelper t) {
        var npc = npc(t); var cow = t.spawnWithNoFreeWill(EntityType.COW, new BlockPos(4, 1, 5)); cow.setPos(npc.position().add(0, 0, -3));
        var p = new AiProgram(); p.kind = "tree";
        var combat = node(p, "reactive_sequence", node(p, "target_exists"),
                node(p, "call").input("file", AiValue.text("test/wait")));
        var patrol = node(p, "call").input("file", AiValue.text("examples/patrol"));
        var sense = node(p, "sense_targets", node(p, "reactive_selector", combat, patrol)).input("filter", AiValue.choice("living", java.util.List.of("hostile", "player", "living")))
                .input("entity_type", AiValue.mobType("minecraft:cow")).input("range", AiValue.number(1.5))
                .input("view_radius", AiValue.number(4)).input("view_angle", AiValue.number(45)); root(p, sense);
        var wait = NpcAiFixtures.action("wait");
        java.util.function.Function<String, AiProgram> actions = id -> id.equals("test/wait") ? wait : NpcActionFiles.load(id);
        AiValidation.requireValid(p, actions);
        var e = new AiExecution(npc, p, new HashMap<>(), Map.of(), actions);
        scan(npc, e); Vec3 origin = e.patrolOrigin();
        t.assertTrue(npc.getTarget() == null && e.activeNodes().contains(patrol.id), "patrol runs while distant target is behind");
        cow.setPos(npc.position().add(0, 0, 3)); scan(npc, e);
        t.assertTrue(npc.getTarget() == cow && e.activeNodes().contains(combat.id) && !e.activeNodes().contains(patrol.id), "continuous sensing preempts an already running movement");
        t.assertTrue(npc.getNpcNavigation().status() == com.viscript.npc.npc.ai.navigation.NavigationStatus.CANCELLED, "preempted patrol relinquishes navigation");
        cow.setPos(npc.position().add(0, 0, -3)); scan(npc, e);
        t.assertTrue(npc.getTarget() == null && e.activeNodes().contains(patrol.id) && e.patrolOrigin().equals(origin), "lost target resumes original patrol area");
        var snapshot = new AiDebugSnapshot(); snapshot.perceptions.addAll(e.perceptions());
        var restored = new AiDebugSnapshot(); restored.deserializeNBT(t.getLevel().registryAccess(), snapshot.serializeNBT(t.getLevel().registryAccess()));
        t.assertTrue(restored.perceptions.size() == 1 && restored.perceptions.getFirst().contains(npc.position()), "debug snapshots carry actual combined volume");
        e.cancel(); t.assertTrue(e.perceptions().isEmpty(), "cancel clears obsolete debug volumes"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void completeTreeTemplatesPublishSeparateReusableActions(GameTestHelper t) {
        for (String name : AiExamples.TREE_NAMES) {
            var tree = AiExamples.tree(name);
            var dependencies = NpcAiPublication.readDependencies(NpcAiPublication.bundle(tree, NpcActionFiles::load));
            t.assertTrue(AiValidation.validate(tree, dependencies::get).isEmpty(), "complete template validates: " + name);
            t.assertTrue(tree.nodes.stream().noneMatch(n -> List.of("patrol", "approach", "melee", "ranged").contains(n.type)), "tree delegates action details to reusable behaviors: " + name);
        }
        var npc = npc(t); var p = new AiProgram(); p.kind = "tree";
        root(p, node(p, "sense_targets", node(p, "success")).input("view_radius", AiValue.number(-1)));
        t.assertTrue(!AiValidation.validate(p, NpcActionFiles::load).isEmpty(), "negative perception geometry is rejected"); t.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void sectorParametersValidateAndBind(GameTestHelper t) {
        var radiusField = AiOperations.get("find_target").fields().stream().filter(f -> f.id().equals("view_radius")).findFirst().orElseThrow();
        var angleField = AiOperations.get("find_target").fields().stream().filter(f -> f.id().equals("view_angle")).findFirst().orElseThrow();
        for (double angle : new double[]{-1, 360.01, Double.NaN})
            t.assertTrue(AiValidation.valueIssue(angleField, AiValue.number(angle)) != null, "invalid full opening rejected: " + angle);
        for (double angle : new double[]{0, 90, 180, 270, 360})
            t.assertTrue(AiValidation.valueIssue(angleField, AiValue.number(angle)) == null, "valid full opening accepted: " + angle);
        t.assertTrue(AiValidation.valueIssue(radiusField, AiValue.number(64)) == null
                && AiValidation.valueIssue(radiusField, AiValue.number(64.01)) != null, "radius bound is enforced");
        var p = new AiProgram(); p.kind = "action";
        var radius = new AiVariable(); radius.initial = AiValue.number(9); p.parameters.add(radius);
        var angle = new AiVariable(); angle.initial = AiValue.number(120); p.parameters.add(angle);
        var find = node(p, "find_target").input("perception", AiValue.choice("view", java.util.List.of("range", "view", "combined"))); root(p, find);
        for (var field : Map.of("view_radius", radius, "view_angle", angle).entrySet()) {
            var binding = new AiBinding(); binding.scope = "parameter"; binding.variableId = field.getValue().id;
            find.inputs.put(field.getKey(), binding);
        }
        var npc = npc(t); var volume = AiPerception.volume(execution(npc, p), find);
        t.assertTrue(volume.viewRadius == 9 && volume.viewAngle == 120 && volume.contains(volume.viewPoint(60, 0))
                && !volume.contains(volume.viewPoint(60.01, 0)), "parameter bindings control actual server geometry");
        t.succeed();
    }

}
