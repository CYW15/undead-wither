package com.mogaidiaolin.witherchallenge.net;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 极简网络通道：只用来把「最近凋灵的位置」同步给客户端，供护身符 tooltip 使用。
 */
public final class WCPackets {

    private static final String VERSION = "1";
    private static int nextId = 0;

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(WitherChallenge.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    public static void register() {
        CHANNEL.messageBuilder(WitherPosMessage.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(WitherPosMessage::encode)
                .decoder(WitherPosMessage::decode)
                .consumerMainThread(WitherPosMessage::handle)
                .add();
    }

    private WCPackets() {
    }
}
