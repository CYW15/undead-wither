package com.mogaidiaolin.witherchallenge.registry;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.item.InfectionArrowItem;
import com.mogaidiaolin.witherchallenge.item.InfectionCoreItem;
import com.mogaidiaolin.witherchallenge.item.WitherEndBookItem;
import com.mogaidiaolin.witherchallenge.item.WitherEndWeaponItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 物品注册表。ID 一旦发布不再改动（进度、配方、指令都会引用）。 */
public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, WitherChallenge.MODID);

    /** 感染核心：the Mangled 掉落，堆叠 64。 */
    public static final RegistryObject<Item> INFECTION_CORE = ITEMS.register("infection_core",
            () -> new InfectionCoreItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON)));

    /** 感染力量之箭：工作台 8 核心 + 1 箭，堆叠 64。 */
    public static final RegistryObject<Item> INFECTION_ARROW = ITEMS.register("infection_arrow",
            () -> new InfectionArrowItem(new Item.Properties().stacksTo(64).rarity(Rarity.COMMON)));

    /** 凋灵终结书（未激活）：4 核心 + 命令方块书 + 钻石/铁锭/绿宝石/红石粉，堆叠 1。 */
    public static final RegistryObject<Item> WITHER_END_BOOK = ITEMS.register("wither_end_book",
            () -> new WitherEndBookItem(false, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 激活的凋灵终结书：右击 IV 级凋暴信标获得，带附魔光效。 */
    public static final RegistryObject<Item> ACTIVATED_WITHER_END_BOOK = ITEMS.register("activated_wither_end_book",
            () -> new WitherEndBookItem(true, new Item.Properties().stacksTo(1).rarity(Rarity.RARE)));

    /** 凋灵终结刀：5 攻 / 4.5 速 / 附魔能力 15 / 无限耐久。 */
    public static final RegistryObject<Item> WITHER_END_KNIFE = ITEMS.register("wither_end_knife",
            () -> new WitherEndWeaponItem(ModTiers.KNIFE, 4, 0.5F, true,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** 凋灵终结战斧：13 攻 / 1.5 速 / 附魔能力 15 / 无限耐久。 */
    public static final RegistryObject<Item> WITHER_END_WAR_AXE = ITEMS.register("wither_end_war_axe",
            () -> new WitherEndWeaponItem(ModTiers.WAR_AXE, 12, -2.5F, false,
                    new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    private ModItems() {
    }

    public static void register(IEventBus bus) {
        ITEMS.register(bus);
    }
}
