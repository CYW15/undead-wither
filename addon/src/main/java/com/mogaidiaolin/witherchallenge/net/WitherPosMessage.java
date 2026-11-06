package com.mogaidiaolin.witherchallenge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 服务端 → 客户端：最近的凋灵位置（供护身符 tooltip 第 6 行使用）。
 */
public record WitherPosMessage(boolean present, double x, double y, double z,
                               int distance, String direction, String dimension) {

    public static void encode(WitherPosMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.present);
        if (msg.present) {
            buf.writeDouble(msg.x);
            buf.writeDouble(msg.y);
            buf.writeDouble(msg.z);
            buf.writeVarInt(msg.distance);
            buf.writeUtf(msg.direction);
            buf.writeUtf(msg.dimension);
        }
    }

    public static WitherPosMessage decode(FriendlyByteBuf buf) {
        boolean present = buf.readBoolean();
        if (!present) {
            return new WitherPosMessage(false, 0, 0, 0, 0, "", "");
        }
        return new WitherPosMessage(true, buf.readDouble(), buf.readDouble(), buf.readDouble(),
                buf.readVarInt(), buf.readUtf(), buf.readUtf());
    }

    public static void handle(WitherPosMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.mogaidiaolin.witherchallenge.client.WitherPosClient.update(msg)));
        context.setPacketHandled(true);
    }
}
