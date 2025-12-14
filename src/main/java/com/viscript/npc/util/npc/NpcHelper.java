package com.viscript.npc.util.npc;

import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.npc.NPC;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashMap;
import java.util.Map;

@ParametersAreNonnullByDefault
public class NpcHelper {
    private final static Map<ResourceLocation, NPC> CACHE = new HashMap<>();
    public static final String NPC_PATH = "npc/";
    //缓存的NPC项目文件
    public static NPCProject cacheNpcProject;

    public static int clearCache() {
        var count = CACHE.size();
        CACHE.clear();
        return count;
    }

    @Nullable
    public static NPC getNPC(ResourceLocation npcLocation) {
        return getNPC(npcLocation, true);
    }


    @Nullable
    public static NPC getNPC(ResourceLocation npcLocation, boolean useCache) {
        return useCache ? CACHE.computeIfAbsent(npcLocation, location -> loadNPC(npcLocation)) : loadNPC(npcLocation);
    }

    @Nullable
    private static NPC loadNPC(ResourceLocation npcLocation) {
        ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(npcLocation.getNamespace(), NPC_PATH + npcLocation.getPath() + NPC.SUFFIX);
        try (var inputStream = Minecraft.getInstance().getResourceManager().open(resourceLocation)) {
            var tag = NbtIo.readCompressed(inputStream, NbtAccounter.unlimitedHeap());
            var npc = new NPC();
            npc.setNpcLocation(npcLocation);
            npc.deserializeNBT(Platform.getFrozenRegistry(), tag);
            return npc;
        } catch (Exception ignored) {
            return null;
        }
    }
}
