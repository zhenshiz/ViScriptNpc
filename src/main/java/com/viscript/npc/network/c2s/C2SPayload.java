package com.viscript.npc.network.c2s;

import com.lowdragmc.lowdraglib2.LDLib2;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacketDistributor;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.network.s2c.S2CPayload;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.NpcRegister;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.io.File;

public class C2SPayload {
    public static final String CREATE_NPC = "createNpc";
    public static final String UPLOAD_NPC_FILE = "uploadNpcFile";

    @RPCPacket(CREATE_NPC)
    public static void createNpc(RPCSender sender, CompoundTag tag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;

            String npcType = tag.getString("npcType");
            boolean hasThisType = false;
            for (Entity entity : ((ServerLevel) player.level()).getEntities().getAll()) {
                if (entity instanceof CustomNpc npc && npc.getNpcType().equals(npcType)) {
                    npc.readAdditionalSaveData(tag);
                    hasThisType = true;
                }
            }
            if (!hasThisType) {
                CustomNpc npc = NpcRegister.CUSTOM_NPC.get().create(player.level());
                if (npc != null) {
                    npc.moveTo(player.position());
                    npc.readAdditionalSaveData(tag);
                    player.level().addFreshEntity(npc);
                }
            }
        }
    }

    @RPCPacket(UPLOAD_NPC_FILE)
    public static void uploadNpcFile(RPCSender sender, CompoundTag tag) {
        if (!sender.isServer()) {
            ServerPlayer player = sender.asPlayer();
            if (player == null) return;

            String fileName = tag.getString("fileName");
            if (fileName.isEmpty()) return;
            tag.remove("fileName");

            File file = new File(LDLib2.getAssetsDir(), "viscript_npc/npc/" + fileName + ".npc");
            boolean exists = file.exists();
            if (!exists) {
                if (file.getParentFile().mkdirs()) {
                    try {
                        if (!file.createNewFile()) {
                            sendEditorDialog(player, "editor.project.upload.failed", "editor.project.upload.failed.3");
                            return;
                        }
                    } catch (Exception e) {
                        sendEditorDialog(player, "editor.project.upload.failed", "editor.project.upload.failed.3");
                        return;
                    }
                }
            }
            try {
                NbtIo.writeCompressed(tag, file.toPath());
                sendEditorDialog(player, "editor.project.upload.success", exists ? "editor.project.upload.success.1" : "editor.project.upload.success.2");
            } catch (Exception e) {
                sendEditorDialog(player, "editor.project.upload.failed", "editor.project.upload.failed.4");
            }
        }
    }

    public static void sendEditorDialog(ServerPlayer player, String title, String content) {
        RPCPacketDistributor.rpcToPlayer(player, S2CPayload.SEND_EDITOR_DIALOG, title, content);
    }
}
