package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.registry.ModItems;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import nonamecrackers2.witherstormmod.common.blockentity.AbstractSuperBeaconBlockEntity;

/**
 * 凋灵终结书的激活：主手持未激活的书，右击 IV 级凋暴信标。
 *
 * <p>必须拦掉 CWS 自己的右击行为（它的信标右击会触发召唤仪式 / 开 GUI），
 * 所以这里取消事件并自行处理。信标不足 IV 级时不消耗物品，只提示。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class BookActivationEvents {

    private static final ResourceLocation SUPER_BEACON_ID =
            new ResourceLocation("witherstormmod", "super_beacon");
    private static final int REQUIRED_LEVEL = 4;

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack held = event.getItemStack();
        if (held.getItem() != ModItems.WITHER_END_BOOK.get()) {
            return;
        }
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (!SUPER_BEACON_ID.equals(blockId)) {
            return;
        }

        // 先拦下来：不让 CWS 的仪式 / GUI 抢这次右击
        event.setCanceled(true);
        event.setUseBlock(Event.Result.DENY);
        event.setUseItem(Event.Result.DENY);

        Player player = event.getEntity();
        if (level.isClientSide) {
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof AbstractSuperBeaconBlockEntity beacon)) {
            return;
        }
        if (beacon.beaconLevel < REQUIRED_LEVEL) {
            player.displayClientMessage(
                    Component.translatable("message.witherchallenge.beacon.too_weak")
                            .withStyle(ChatFormatting.RED),
                    true);
            return;
        }

        ItemStack activated = new ItemStack(ModItems.ACTIVATED_WITHER_END_BOOK.get());
        if (held.hasCustomHoverName()) {
            activated.setHoverName(held.getHoverName());
        }
        player.setItemInHand(event.getHand(), activated);
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            com.mogaidiaolin.witherchallenge.registry.ModCriteria.trigger(
                    com.mogaidiaolin.witherchallenge.registry.ModCriteria.BEACON_4, sp);
        }
        level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.0F, 1.0F);
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D,
                    24, 0.4D, 0.6D, 0.4D, 0.02D);
        }
        player.displayClientMessage(
                Component.translatable("message.witherchallenge.book.activated").withStyle(ChatFormatting.LIGHT_PURPLE),
                true);
    }

    private BookActivationEvents() {
    }
}
