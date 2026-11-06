package com.mogaidiaolin.witherchallenge.registry;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** 创造模式标签页：新建「凋亡回响」，本 mod 的 6 个物品全部放进去（按流程顺序排列）。 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, WitherChallenge.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.witherchallenge"))
                    .icon(() -> new ItemStack(ModItems.INFECTION_CORE.get()))
                    .displayItems((params, output) -> {
                        // 流程顺序：核心 → 箭 → 书 → 激活书 → 刀 → 斧
                        output.accept(ModItems.INFECTION_CORE.get());
                        output.accept(ModItems.INFECTION_ARROW.get());
                        output.accept(ModItems.WITHER_END_BOOK.get());
                        output.accept(ModItems.ACTIVATED_WITHER_END_BOOK.get());
                        output.accept(ModItems.WITHER_END_KNIFE.get());
                        output.accept(ModItems.WITHER_END_WAR_AXE.get());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus bus) {
        TABS.register(bus);
    }
}
