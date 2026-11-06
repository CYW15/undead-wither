# CHANGELOG

《凋亡回响》整合包（Minecraft 1.20.1 + Forge 47.4.28）开发记录。
（仓库初始化时由脚本按版本重建；更早的细节以各版本构建产物为准。）

## 2026-11-06 14:0x · version_0.0.2：物品属性 / 交互全部接线

产物：`final_modpack/version_0.0.2/不死的凋灵-0.0.2.mrpack`（308.9 MB，51 mod）

### 1. 感染力量之箭现在能被弓 / 弩发射

**原因**：弓和弩找弹药时只认 `minecraft:arrows` 这个 **物品标签**（`ProjectileWeaponItem` 的
`getSupportedHeldProjectiles` / `getAllSupportedProjectiles`），我们的箭不在标签里，
所以拉弓会「没有箭」、弩也装不上——和 `createArrow` 的代码无关。

**修复**：新增 `data/minecraft/tags/items/arrows.json`，把 `witherchallenge:infection_arrow` 加进去。

**验证**：`/witherchallenge tags` →
`感染箭∈minecraft:arrows=true`。

### 2. 终结武器对凋暴体内命令方块的伤害（不用写代码）

反编译 CWS 的 `CommandBlockEntity.hurt()` 发现它自己就实现了「每次有效命中扣 25% 最大生命，
打 4 次进下一阶段」，判定条件是：

```java
stack.is(WitherStormModItemTags.COMMAND_BLOCK_TOOLS)   // 物品标签
    || stack.canPerformAction(WitherStormModToolActions.COMMAND_BLOCK_DAMAGE)
```

**修复**：新增 `data/witherstormmod/tags/items/command_block_tools.json`，
把 `wither_end_knife` / `wither_end_war_axe` 加进去。
**验证**：`/witherchallenge tags` → 刀/斧 = true。

### 3. 终结武器对时空间隙造成伤害

**原因**：W.A.R. 的 `TheCorpseOfTheWitherEntity.hurt()` 里硬编码
`if (source.getDirectEntity() instanceof Player) return false;` —— 玩家近战打它连事件都不触发。

**核对**：反编译确认它拒收的伤害类型只有
`IN_FIRE / FALL / CACTUS / DROWN / LIGHTNING_BOLT / EXPLOSION / TRIDENT / FALLING_ANVIL /
DRAGON_BREATH / WITHER / WITHER_SKULL`（外加箭、药水、玩家来源），**`generic` 不在其中**。

**修复**：`combat/WeaponCombatEvents` 监听 `AttackEntityEvent`：
- 目标是时空间隙 → 取消原版这一刀；
- 手里是终结武器 且 攻击冷却 ≥90%（重击）→ 用 `damageSources().generic()` 打 1 点（刀）/ 2 点（斧）；
- 轻击 → 只显示「你需要用更大的力气才能伤到它」；
- 手里不是终结武器 → 显示「它已经死透了」。
- 因为伤害源不带实体，原版击杀归属会丢，`ChallengeTracker` 自己记最后攻击者（留给后面的通关判定）。

### 4. 铁砧升级

**修复**：`combat/AnvilEvents` 监听 `AnvilUpdateEvent`：
`withering_away_reborn:wither_knife` + 激活书 → 凋灵终结刀；
`withering_away_reborn:wither_forced_iron_war_axe` + 激活书 → 凋灵终结战斧。
保留原武器附魔 / 自定义名 / NBT，不保留书的附魔，产物强制带 `Unbreakable`。

### 5. 凋灵终结书的激活（拦掉 CWS 的召唤仪式）

**原因**：CWS 的凋暴信标右击有自己的一套行为（会触发召唤仪式的配方流程），
我们的书右击被它抢走了。

**修复**：`combat/BookActivationEvents` 监听 `PlayerInteractEvent.RightClickBlock`：
主手持未激活书 + 目标方块是 `witherstormmod:super_beacon` → **先取消事件**（`setCanceled` +
`useBlock/useItem = DENY`），再读 CWS 的 `AbstractSuperBeaconBlockEntity.beaconLevel`：
- `beaconLevel >= 4` → 换成激活书、播音效 + 末地烛粒子 + 提示；
- 否则 → 提示「凋暴信标的能量不足以激活凋灵终结书」，不消耗。
（CWS 的 `beaconLevel >= 1` 就会把 `isActive` 置 true，所以等级 4 必然已激活，符合 Q40。）

### 6. 感染核心掉率 10% → 25%

`combat/LootEvents`：the Mangled 被玩家 / 玩家射出的箭 / 已驯服的狼击杀时，
基础 **25%**，抢夺每级 **+5%**（即 25/30/35/40%）。

### 7. 新增调试命令

`/witherchallenge tags`：打印「感染箭是否在 arrows 标签、刀/斧是否在 command_block_tools 标签」。

### 8. 服务端实测

```
[34s] Done
/witherchallenge status  -> 配方 1361 条｜凋暴 0｜时空间隙 1｜the Mangled 1
/witherchallenge tags    -> 感染箭∈minecraft:arrows=true｜刀∈command_block_tools=true｜斧∈command_block_tools=true
/witherchallenge recipes -> 1361 条，含 infection_arrow / wither_end_book
```

### 9. 这一版**还没做**的

- **感染箭的命中效果**：射中时空间隙召唤凋暴、射中生物按转化表替换、没射中退回背包 —— 未写
- 9 个成就与 3 个自定义触发器
- 凋暴强化（8 连发 / Head 机炮 / 光束感染 / 召唤 W.A.R. 生物）、共生体锁血、corpse 加强
- 指南书、起始存档、配置面板、`server.zip`

---
