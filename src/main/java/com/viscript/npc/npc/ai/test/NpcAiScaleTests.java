package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.*;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.ai.navigation.*;
import com.viscript.npc.npc.ai.runtime.AiExamples;
import com.viscript.npc.util.NpcActionFiles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.*;

import java.nio.file.*;
import java.util.*;

/** 连续 50 秒采样 50 个真实 NPC 的服务端追击、决策和后台队列。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public final class NpcAiScaleTests {
    private static List<Double> samples;
    private static long tickStart, sampleStarted, nextTickAt;
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void before(ServerTickEvent.Pre event) { if (samples != null) tickStart = System.nanoTime(); }
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void after(ServerTickEvent.Post event) {
        if (samples != null && tickStart != 0) {
            long elapsed = System.nanoTime() - tickStart;
            samples.add(elapsed / 1_000_000d);
            // GameTestServer deliberately omits normal server tick pacing. Pace only this
            // benchmark after measuring work, so asynchronous workers see real 20 TPS timing.
            nextTickAt += 50_000_000L;
            long remaining = nextTickAt - System.nanoTime();
            if (remaining > 0) java.util.concurrent.locks.LockSupport.parkNanos(remaining);
        }
        tickStart = 0;
    }

    @GameTest(template = "ai_scale", batch = "npc_ai_scale", timeoutTicks = 1600)
    public static void fiftyNpcs(GameTestHelper test) {
        for (int x = 0; x < 62; x++) for (int z = 0; z < 60; z++) test.setBlock(x, 0, z, Blocks.STONE);
        List<CustomNpc> npcs = new ArrayList<>(); List<ArmorStand> targets = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            int x = 3 + (i % 5) * 10, z = 3 + (i / 5) * 5;
            CustomNpc npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(x, 1, z));
            ArmorStand target = test.spawn(EntityType.ARMOR_STAND, new BlockPos(x + 7, 1, z));
            target.setInvulnerable(true); target.setNoGravity(true);
            npc.setTarget(target);
            var program = AiExamples.tree("melee");
            var dependencies = Map.of("examples/melee", NpcActionFiles.load("examples/melee"),
                    "examples/pursue_melee", NpcActionFiles.load("examples/pursue_melee"));
            com.viscript.npc.npc.ai.runtime.AiValidation.requireValid(program, dependencies::get);
            npc.getAiController().preview(program, dependencies);
            npcs.add(npc); targets.add(target);
        }
        int[] tick = {0}, peak = {0}, failures = {0}, activeSamples = {0};
        double[] queue = {0}, search = {0};
        Runnable[] finish = new Runnable[1];
        boolean[] completed = {false};
        test.onEachTick(() -> {
            if (completed[0]) return;
            tick[0]++;
            if (tick[0] == 100) { samples = new ArrayList<>(); sampleStarted = nextTickAt = System.nanoTime(); tickStart = 0; }
            if (tick[0] >= 100) {
                peak[0] = Math.max(peak[0], NpcNavigation.pendingCount());
                for (CustomNpc npc : npcs) {
                    var navigation = npc.getNpcNavigation();
                    queue[0] = Math.max(queue[0], navigation.queueNanos() / 1_000_000d);
                    search[0] = Math.max(search[0], navigation.searchNanos() / 1_000_000d);
                    if (navigation.status() == NavigationStatus.FAILED) failures[0]++;
                    activeSamples[0]++;
                }
            }
            if (tick[0] > 1100 && System.nanoTime() - sampleStarted >= 50_000_000_000L) {
                completed[0] = true; finish[0].run(); return;
            }
            if (tick[0] % 100 == 0) for (int i = 0; i < targets.size(); i++) {
                int x = 3 + (i % 5) * 10 + ((tick[0] / 100) % 2 == 0 ? 7 : 0), z = 3 + (i / 5) * 5;
                var goal = net.minecraft.world.phys.Vec3.atBottomCenterOf(test.absolutePos(new BlockPos(x, 1, z)));
                targets.get(i).moveTo(goal);
            }
        });
        finish[0] = () -> {
            List<Double> measured = samples; samples = null;
            int consecutive = 0, longest = 0;
            for (double ms : measured) { consecutive = ms > 50 ? consecutive + 1 : 0; longest = Math.max(longest, consecutive); }
            var sorted = measured.stream().sorted().toList();
            double average = measured.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double p95 = sorted.get((int) (sorted.size() * .95));
            long requests = npcs.stream().mapToLong(npc -> npc.getNpcNavigation().requests()).sum();
            long requestFailures = npcs.stream().mapToLong(npc -> npc.getNpcNavigation().failedRequests()).sum();
            String report = String.format(Locale.ROOT,
                    "NPCs=50; pacing=20TPS; wallSeconds=%.2f; samples=%d; meanMs=%.3f; p95Ms=%.3f; maxMs=%.3f; consecutiveOver50=%d; queuePeak=%d; queueMaxMs=%.1f; searchMaxMs=%.1f; requests=%d; requestFailures=%d; requestFailureRate=%.5f; failedSampleRate=%.5f; Java=%s; OS=%s %s; processors=%d%n",
                    (System.nanoTime() - sampleStarted) / 1_000_000_000d, measured.size(), average, p95, sorted.getLast(), longest, peak[0], queue[0], search[0],
                    requests, requestFailures, requestFailures / (double) Math.max(1, requests), (double) failures[0] / activeSamples[0],
                    System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"), Runtime.getRuntime().availableProcessors());
            ViScriptNpc.LOGGER.info("NPC AI SCALE: {}", report);
            try { Files.writeString(Path.of("npc-ai-scale-report.txt"), report); } catch (java.io.IOException ex) { throw new RuntimeException(ex); }
            npcs.forEach(npc -> npc.getAiController().manualControl());
            test.assertTrue(average < 50 && longest < 5, "20 TPS budget without sustained tick overruns: " + report);
            test.assertTrue(peak[0] <= 128, "bounded navigation queue");
            test.assertTrue(requests >= npcs.size(), "each NPC participates in the navigation workload");
            test.succeed();
        };

    }
}
