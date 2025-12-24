package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.editor.ui.EditorWindow;
import com.lowdragmc.lowdraglib2.gui.holder.ModularUIScreen;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Dialog;
import com.lowdragmc.lowdraglib2.networking.rpc.RPCPacket;
import com.lowdragmc.lowdraglib2.syncdata.rpc.RPCSender;
import com.viscript.npc.gui.edit.NpcEditor;
import com.viscript.npc.util.ViScriptNpcClientUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;

public class S2CPayload {
    public static final String OPEN_NPC_EDITOR = "openNpcEditor";
    public static final String SEND_EDITOR_DIALOG = "sendEditorDialog";

    @RPCPacket(OPEN_NPC_EDITOR)
    public static void openNpcEditor(RPCSender sender, CompoundTag tag) {
        ViScriptNpcClientUtil.openNpcEditor(tag);
    }

    @RPCPacket(SEND_EDITOR_DIALOG)
    public static void sendEditorDialog(RPCSender sender, String title, String content) {
        if (sender.isServer() && Minecraft.getInstance().screen instanceof ModularUIScreen uiScreen) {
            if (uiScreen.modularUI.ui.rootElement instanceof EditorWindow window && window.getCurrentEditor() instanceof NpcEditor editor) {
                Dialog.showNotification(title, content, null).show(editor);
            }
        }
    }
}
