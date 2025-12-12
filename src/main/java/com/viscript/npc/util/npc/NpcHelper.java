package com.viscript.npc.util.npc;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.Platform;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.command.NpcCommand;
import com.viscript.npc.gui.edit.NPCProject;
import com.viscript.npc.gui.edit.npc.NPC;
import com.viscript.npc.util.common.FileScanner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.File;
import java.io.FileInputStream;
import java.util.Set;

@ParametersAreNonnullByDefault
public class NpcHelper {
    public static final String NPC_PATH = "%s/npc/".formatted(ViScriptNpc.MOD_ID);
    //缓存的NPC项目文件
    public static NPCProject cacheNpcProject;

    public static Set<String> scanNpcFiles() {
        NpcCommand.npcFilesPath.clear();
        return FileScanner.scanFilesWithSuffix(new File(LDLib2.getAssetsDir(), NPC_PATH), NPC.SUFFIX);
    }

    public static NPC loadShop(String path) {
        File file = new File(LDLib2.getAssetsDir(), NPC_PATH + path + NPC.SUFFIX);
        if (!file.exists()) {
            ViScriptNpc.LOGGER.error("npc file {} not found", path);
            return null;
        }
        try {
            CompoundTag tag = NbtIo.readCompressed(new FileInputStream(file), NbtAccounter.unlimitedHeap());
            var npc = new NPC();
            npc.setPath(path);
            npc.deserializeNBT(Platform.getFrozenRegistry(), tag);
            return npc;
        } catch (Exception ignored) {
            return null;
        }
    }

}
