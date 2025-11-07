package com.viscript.npc.network.s2c;

import com.lowdragmc.lowdraglib2.Platform;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUI;
import com.lowdragmc.lowdraglib2.gui.ui.ModularUIScreen;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.test.TestNpcEditor;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.NotNull;

public record OpenNpcEditor() implements CustomPacketPayload {
    public static final Type<OpenNpcEditor> TYPE = new Type<>(ViScriptNpc.id("open_npc_editor"));
    public static final StreamCodec<FriendlyByteBuf, OpenNpcEditor> CODEC = StreamCodec.ofMember(OpenNpcEditor::write, OpenNpcEditor::new);

    public OpenNpcEditor(FriendlyByteBuf friendlyByteBuf) {
        this();
    }

    private void write(FriendlyByteBuf buf) {
    }

    public static void execute(OpenNpcEditor payload, IPayloadContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        TestNpcEditor testNpcEditor = new TestNpcEditor();
        ModularUI ui = testNpcEditor.createUI(minecraft.player);
        if (!Platform.isDevEnv()) ui.shouldCloseOnEsc(false).shouldCloseOnKeyInventory(false);
        minecraft.setScreen(new ModularUIScreen(ui, Component.empty()));
    }

    @Override
    public @NotNull CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
