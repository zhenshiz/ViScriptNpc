package com.viscript.npc.npc.ai.test;

import com.lowdragmc.lowdraglib2.registry.RegistrationEnvironment;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.lowdragmc.lowdraglib2.uitest.mp.*;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import java.util.Map;

/** 两个真实客户端同时观察服务端跳劈，验证同步位置与单次伤害。 */
@LDLRegister(name = "npc_ai_replication", group = ViScriptNpc.MOD_ID,
        registry = MPScenario.REGISTRY, environment = RegistrationEnvironment.DEV_ONLY)
public final class NpcAiReplicationScenario implements MPScenario {
    @Override public void configure(MPScenarioOptions options) {
        options.clients("A", "B").tags("ai").scenarioTimeoutMs(120_000);
    }

    @Override public void define(MPScenarioBuilder s) {
        s.server("Create a shared jump-slash fixture", c -> {
            var level = c.level();
            for (int x = 0; x <= 10; x++) for (int z = 0; z <= 10; z++) {
                level.setBlockAndUpdate(new BlockPos(x, 100, z), Blocks.STONE.defaultBlockState());
                for (int y = 101; y <= 105; y++) level.setBlockAndUpdate(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
            }
            for (int i = 0; i < c.players().size(); i++)
                c.players().get(i).teleportTo(level, 3 + i * 4, 101, 8, java.util.Set.of(), 180, 15);
            CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(level);
            npc.moveTo(2.5, 101, 2.5); npc.getAiController().manualControl(); level.addFreshEntity(npc);
            var target = EntityType.COW.create(level); target.moveTo(5.5, 101, 2.5); target.setNoAi(true);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); target.setHealth(100); level.addFreshEntity(target);
            npc.setTarget(target); c.put("npc", npc); c.put("target", target);
        }).fetch("npcId", "Share NPC entity ID", Integer.class, c -> ((CustomNpc) c.get("npc")).getId())
                .fetch("targetId", "Share target entity ID", Integer.class, c -> ((LivingEntity) c.get("target")).getId())
                .syncAll("Both clients receive the target before execution", c -> 100f,
                        c -> c.mc().level.getEntity((Integer) c.get("targetId")) instanceof LivingEntity target ? target.getHealth() : -1f)
                .serverSettle(5)
                .server("Execute one jump slash", c -> {
                    CustomNpc npc = c.get("npc");
                    var action = AiExamples.action("jump_slash");
                    action.nodes.stream().filter(n -> n.type.equals("wait")).reduce((a, b) -> b).orElseThrow().input("seconds", AiValue.number(30));
                    var tree = new AiProgram();
                    var call = new AiNodeData(); call.type = "call"; call.input("file", AiValue.text("test/slash"));
                    var root = new AiNodeData(); root.type = "root"; root.children.add(call.id);
                    tree.root = root.id; tree.nodes.add(root); tree.nodes.add(call);
                    npc.getAiController().preview(tree, Map.of("test/slash", action));
                })
                .serverWaitUntil("Physical takeoff", c -> !((CustomNpc) c.get("npc")).onGround())
                .serverWaitUntil("Landing damage", c -> ((CustomNpc) c.get("npc")).onGround() && ((LivingEntity) c.get("target")).getHealth() == 94)
                .server("Stop only the tested NPC", c -> ((CustomNpc) c.get("npc")).getAiController().manualControl())
                .serverSettle(10)
                .syncAll("Both clients observe exactly six damage", c -> ((LivingEntity) c.get("target")).getHealth(),
                        c -> c.mc().level.getEntity((Integer) c.get("targetId")) instanceof LivingEntity target ? target.getHealth() : -1f)
                .fetch("landedX", "Authoritative landing X", Double.class, c -> ((CustomNpc) c.get("npc")).getX())
                .fetch("landedY", "Authoritative landing Y", Double.class, c -> ((CustomNpc) c.get("npc")).getY())
                .fetch("landedZ", "Authoritative landing Z", Double.class, c -> ((CustomNpc) c.get("npc")).getZ())
                .allClients("Check synchronized landing", b -> b.ticks(5).check("Position agrees with server", c -> {
                    var npc = c.mc().level.getEntity((Integer) c.get("npcId"));
                    return npc != null && npc.position().distanceTo(new net.minecraft.world.phys.Vec3(
                            (Double) c.get("landedX"), (Double) c.get("landedY"), (Double) c.get("landedZ"))) < .1;
                }).screenshot("shared_jump_slash"))
                .serverSettle(30)
                .syncAll("No delayed extra damage after stopping", c -> 94f,
                        c -> ((LivingEntity) c.mc().level.getEntity((Integer) c.get("targetId"))).getHealth())
                .teardownServer("Remove fixture entities", c -> {
                    CustomNpc npc = c.get("npc"); if (npc != null) npc.discard();
                    LivingEntity target = c.get("target"); if (target != null) target.discard();
                });
    }
}
