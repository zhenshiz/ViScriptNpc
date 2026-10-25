package com.viscript.npc.npc.ai.test;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import com.viscript.npc.npc.ai.data.*;
import com.viscript.npc.npc.data.ai.NpcAI;
import com.viscript.npc.util.NpcAiPublication;
import com.viscript.npc.util.NpcAiUpdates;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;

import java.util.Map;
import java.util.UUID;

/** 验证按标记提交、运行中替换、依赖隔离和世界存档往返。 */
@GameTestHolder(ViScriptNpc.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NpcAiSaveTests {
    private static final String ACTION = "__ldlib2_tests__/saved_navigation";

    private static CustomNpc npc(GameTestHelper test, String marker, int z) {
        for (int x = 0; x < 10; x++) test.setBlock(x, 0, z, Blocks.STONE);
        var npc = test.spawn(NpcRegister.CUSTOM_NPC.get(), new BlockPos(1, 1, z));
        npc.getNpcBasicsSetting().setNpcId(marker);
        return npc;
    }

    private static AiProgram tree() {
        var program = new AiProgram();
        var call = new AiNodeData(); call.type = "call"; call.input("file", AiValue.text(ACTION));
        var root = new AiNodeData(); root.type = "root"; root.children.add(call.id);
        program.root = root.id; program.nodes.add(root); program.nodes.add(call);
        return program;
    }

    private static AiProgram action(CustomNpc npc, double speed) {
        var action = NpcAiFixtures.action("navigate");
        action.nodes.getLast().input("speed", AiValue.number(speed))
                .input("position", AiValue.position(npc.position().add(5, 0, 0)));
        return action;
    }

    private static CompoundTag snapshot(GameTestHelper test, AiProgram tree, AiProgram action) {
        var ai = new NpcAI(); ai.setProgram(tree);
        var tag = ai.serializeNBT(test.getLevel().registryAccess());
        tag.put(NpcAiPublication.DEPENDENCIES, NpcAiPublication.bundle(tree, id -> ACTION.equals(id) ? action : null));
        return tag;
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void savingRestartsOnlyMatchingNpcs(GameTestHelper test) {
        String marker = UUID.randomUUID().toString();
        var first = npc(test, marker, 2);
        var second = npc(test, marker, 4);
        var other = npc(test, marker + "_other", 6);
        var tree = tree();
        for (var npc : java.util.List.of(first, second, other)) {
            npc.getAiController().preview(tree, Map.of(ACTION, action(npc, .8)));
            npc.getAiController().tick();
        }
        long generation = first.getAiController().generation(), untouched = other.getAiController().generation();
        var position = first.position(); float health = first.getHealth();
        int count = NpcAiUpdates.apply(test.getLevel().getServer(), marker, snapshot(test, tree, action(first, .3)));
        test.assertTrue(count == 2 && first.getAiController().generation() > generation,
                "all exact marker matches cancel the running tree immediately");
        test.assertTrue(other.getAiController().generation() == untouched
                        && other.getAttributeValue(Attributes.MOVEMENT_SPEED) == .8,
                "other markers keep their executing tree and speed");
        first.getAiController().tick(); second.getAiController().tick();
        test.assertTrue(first.getAttributeValue(Attributes.MOVEMENT_SPEED) == .3
                        && second.getAttributeValue(Attributes.MOVEMENT_SPEED) == .3,
                "both NPCs execute the newly saved action instead of the old running snapshot");
        test.assertTrue(first.position().equals(position) && first.getHealth() == health,
                "saving AI preserves position and health");
        test.assertTrue(first.getNpcAI().getProgram() != second.getNpcAI().getProgram()
                        && first.getNpcAI().resolveAction(ACTION) != second.getNpcAI().resolveAction(ACTION),
                "each NPC owns independent tree and behavior snapshots");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void invalidSaveLeavesRunningAiIntact(GameTestHelper test) {
        String marker = UUID.randomUUID().toString();
        var npc = npc(test, marker, 2); var tree = tree();
        npc.getAiController().preview(tree, Map.of(ACTION, action(npc, .5)));
        npc.getAiController().tick();
        long generation = npc.getAiController().generation();
        var before = npc.getNpcAI().getProgram();
        var broken = snapshot(test, tree, action(npc, .3));
        broken.put(NpcAiPublication.DEPENDENCIES, new CompoundTag());
        boolean rejected = false;
        try { NpcAiUpdates.apply(test.getLevel().getServer(), marker, broken); }
        catch (IllegalArgumentException expected) { rejected = true; }
        test.assertTrue(rejected && npc.getAiController().generation() == generation && npc.getNpcAI().getProgram() == before,
                "missing dependency is rejected before any existing execution is cancelled");
        rejected = false;
        try { NpcAiUpdates.apply(test.getLevel().getServer(), " ", snapshot(test, tree, action(npc, .3))); }
        catch (IllegalArgumentException expected) { rejected = true; }
        test.assertTrue(rejected && npc.getAiController().generation() == generation,
                "blank marker never acts as a wildcard");
        test.assertTrue(NpcAiUpdates.apply(test.getLevel().getServer(), marker + "_absent",
                snapshot(test, tree, action(npc, .3))) == 0, "unloaded or absent matches report zero");
        test.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40)
    public static void savedDependenciesSurviveNpcReload(GameTestHelper test) {
        String marker = UUID.randomUUID().toString();
        var original = npc(test, marker, 2); var tree = tree();
        NpcAiUpdates.apply(test.getLevel().getServer(), marker, snapshot(test, tree, action(original, .3)));
        var tag = new CompoundTag(); original.saveWithoutId(tag);
        var restored = NpcRegister.CUSTOM_NPC.get().create(test.getLevel());
        if (restored == null) throw new IllegalStateException("NPC creation failed");
        restored.load(tag);
        test.assertTrue(restored.getNpcType().equals(marker) && restored.getNpcAI().resolveAction(ACTION) != null,
                "entity save reload restores marker, tree and unpublished behavior dependency");
        restored.getAiController().tick();
        test.assertTrue(restored.getAttributeValue(Attributes.MOVEMENT_SPEED) == .3,
                "restored NPC executes the committed dependency without a shared behavior file");
        test.succeed();
    }
}
