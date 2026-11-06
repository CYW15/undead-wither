package com.mogaidiaolin.witherchallenge;

import com.mogaidiaolin.witherchallenge.net.WCPackets;
import com.mogaidiaolin.witherchallenge.registry.ModCreativeTabs;
import com.mogaidiaolin.witherchallenge.registry.ModEntities;
import com.mogaidiaolin.witherchallenge.registry.ModEffects;
import com.mogaidiaolin.witherchallenge.registry.ModItems;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 《凋亡回响》挑战附属 mod 主类。
 *
 * <p>modid = witherchallenge，所有新增内容（感染核心、感染力量之箭、凋灵终结武器、
 * 凋暴强化、成就触发器、调试命令）都挂在这个 mod 下，不改动 CWS / W.A.R. 的原 jar。</p>
 */
@Mod(WitherChallenge.MODID)
public final class WitherChallenge {
    public static final String MODID = "witherchallenge";
    public static final Logger LOGGER = LoggerFactory.getLogger("WitherChallenge");

    public WitherChallenge() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modBus);
        ModEntities.register(modBus);
        ModCreativeTabs.register(modBus);
        ModEffects.register(modBus);
        WCPackets.register();
        WCConfig.register();
        LOGGER.info("[凋亡回响] 附属 mod 初始化完成");
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}
