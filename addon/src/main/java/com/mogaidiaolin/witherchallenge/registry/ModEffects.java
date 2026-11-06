package com.mogaidiaolin.witherchallenge.registry;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 自定义状态效果。
 *
 * <p>{@code wither_echo}（凋零残响）：限时治愈模式下的「缓冲期」标记效果，
 * 本身没有任何数值作用，只是告诉玩家「过一会儿还会再感染」。</p>
 */
public final class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, WitherChallenge.MODID);

    public static final RegistryObject<MobEffect> WITHER_ECHO = EFFECTS.register("wither_echo",
            () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x6A2C8A) {
            });

    private ModEffects() {
    }

    public static void register(IEventBus bus) {
        EFFECTS.register(bus);
    }
}
