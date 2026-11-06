package com.mogaidiaolin.witherchallenge.combat;

import com.mogaidiaolin.witherchallenge.WitherChallenge;
import com.mogaidiaolin.witherchallenge.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 铁砧升级：W.A.R. 的凋灵刀 / 凋灵战斧 + 激活的凋灵终结书 → 凋灵终结刀 / 凋灵终结战斧。
 *
 * <p>规则（需求 Q46）：消耗激活书、保留武器全部附魔与自定义名、不保留书的附魔。
 * 拿错东西（未激活书 / 两本书 / 书+书）时原版铁砧照常拒绝，不额外消耗。</p>
 */
@Mod.EventBusSubscriber(modid = WitherChallenge.MODID)
public final class AnvilEvents {

    private static final ResourceLocation WAR_KNIFE = new ResourceLocation("withering_away_reborn", "wither_knife");
    private static final ResourceLocation WAR_AXE =
            new ResourceLocation("withering_away_reborn", "wither_forced_iron_war_axe");

    @SubscribeEvent
    public static void onAnvilUpdate(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        ItemStack right = event.getRight();
        if (left.isEmpty() || right.isEmpty()) {
            return;
        }
        if (right.getItem() != ModItems.ACTIVATED_WITHER_END_BOOK.get()) {
            return;
        }
        ResourceLocation leftId = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(left.getItem());
        boolean knife;
        if (WAR_KNIFE.equals(leftId)) {
            knife = true;
        } else if (WAR_AXE.equals(leftId)) {
            knife = false;
        } else {
            return;
        }

        ItemStack out = new ItemStack(knife ? ModItems.WITHER_END_KNIFE.get() : ModItems.WITHER_END_WAR_AXE.get());
        // 保留原武器的附魔、名称与 NBT（书的附魔不带过去）
        if (left.hasTag()) {
            out.setTag(left.getTag().copy());
        }
        out.getOrCreateTag().putBoolean("Unbreakable", true);
        // 保险：清掉可能带过来的耐久损伤
        out.setDamageValue(0);

        event.setOutput(out);
        event.setCost(1);
        event.setMaterialCost(1);
    }

    private AnvilEvents() {
    }
}
