# CHANGELOG

《凋亡回响》整合包（Minecraft 1.20.1 + Forge 47.4.28）开发记录。
（仓库初始化时由脚本按版本重建；更早的细节以各版本构建产物为准。）

## 2026-11-06 20:4x · version_0.0.3：感染可结束 + 箭的命中逻辑 + 加强时空间隙

产物：`final_modpack/version_0.0.3/不死的凋灵-0.0.3.mrpack`（308.9 MB，51 mod）

### 1. 「所有时空间隙死亡 → 直接结束感染」（用户 2026-11-06 新需求）

**做法**：`world/InfectionControl`（SavedData，存在主世界的 `witherchallenge_infection`）：

- 时空间隙一出现就记 `hadCorpse`；
- 任意一只时空间隙死亡时统计**所有维度**还活着的 `the_corpse_of_the_wither`，
  一只不剩 → `ended = true`，广播「最后一只时空间隙已被击败 —— 感染终止了。」；
- `ended` 之后在 `EntityJoinLevelEvent` 里**拦截一切 `withering_away_reborn:*` 实体的生成**。
  因为 W.A.R. 的方块扩散本身也是靠「扩散实体」做的（`soulspread_entity` / `glich_blocks_spread` /
  `obamaspreadentity` / `spreedentity` …），所以这一条同时干掉：
  - 方块继续感染；
  - W.A.R. 生物击杀普通生物后的转化（转化也是「生成新实体」）；
  - 刷怪笼刷 W.A.R. 生物；
- 结束瞬间还会把场上剩余的 W.A.R. 生物清掉（corpse 保留成雕像）。
- 状态写进存档，重进世界仍然有效（`/witherchallenge status` 会显示「感染结束=true」）。

**踩坑记录**：第一版 `endInfection` 边遍历 `level.getAllEntities()` 边 `discard()`，
导致 `kill` 指令报 “An unexpected error occurred”，日志里是
`NullPointerException: Cannot invoke Entity.getType() because "e" is null`。
已改成「先收集成 List 再删」。

**实测（全新世界）**：

```
status               -> 感染结束=false｜挑战 0/2
summon ...the_corpse_of_the_wither -> Summoned
kill @e[...corpse]   -> Killed The Corpse Of The Wither
status               -> 感染结束=true｜挑战 0/2
summon ...the_mangled -> Summoned（消息照出）
execute if entity @e[type=...the_mangled] -> Test failed   ← 被拦掉，没生成
summon minecraft:zombie -> Summoned / Test passed          ← 普通生物不受影响
```

### 2. 感染力量之箭的命中逻辑（`entity/InfectionArrowEntity`）

| 命中对象 | 效果 |
| --- | --- |
| 未加强的时空间隙 | 转成加强版 + 在该位置召唤凋暴（phase 0）+ 锁 rage + 雷声/粒子 |
| 已加强的时空间隙 | 无效果（提示「它已经被感染过了」） |
| 玩家 | 凋零 II 1 小时 + CWS 的凋零腐败（`witherstormmod:wither_sickness`） |
| 普通生物 | 按 `combat/ConversionTable` 替换成 W.A.R. 生物；表里没有 → 凋零 II 1 小时 |
| 凋灵 / 末影龙 / 监守者 / 凋暴 / 命令方块 / 任何 W.A.R. 生物 | 无效果 |
| 命中方块 / 落空 | 箭**退回射手背包**（背包满则掉在脚下），不消耗 |

转化表（19 条）：僵尸→new_withering_zombie、苦力怕→new_withering_creeper、蜘蛛/洞穴蜘蛛→withering_spider、
末影人→withering_enderman、牛→new_withering_cow、猪→withering_pig、鸡→withering_chiken、
海龟→withering_turtle、村民→withering_v_illager、卫道士→withering_vindicator、
掠夺者→withering_pillager、唤魔者→withering_evoker、劫掠兽→withering_ravager、
幻翼→withering_phantom、疣猪兽→witheringhoglin、猪灵→withering_piglin、
铁傀儡→withering_golem、烈焰人→wither_blaze。

### 3. 加强版时空间隙（`combat/CorpseRules`）

- 血量 150（`MAX_HEALTH` 基础值直接改，AttributeFix 在包里，上限够）；
- 顶点 `wc_enhanced` 标签 + 登记为挑战目标（最多两只）；
- 锁 rage：`WitheringAwayRebornModVariables.WorldVariables.rage = true` 并 `syncData`；
- 方块感染加速：每 20 tick 在周围半径 6 采样 24 个方块，按 `world/BlockInfection` 的表（就是
  `task/凋暴光束感染对应表.docx` 那张）转化；
- W.A.R. 生物生成加速：每 100 tick 在 16 格内补 1 只（半径 48 内超过 40 只就停）；
- 免疫一切状态效果（`MobEffectEvent.Applicable` → DENY）；
- 不能被船 / 矿车 / 坐骑搬运（`EntityMountEvent` → 取消）；
- 永久保留（`setPersistenceRequired`）；
- 凋暴死亡后 W.A.R. 补的那只 corpse，会在 400 tick 内被自动转成加强版（需求 Q62）。

### 4. 挑战判定（需求 Q63 / T9 / T14）

- 只认**前两只**加强时空间隙（`challengeCorpses` 存 UUID）；
- 击杀必须由玩家 / 玩家射出的箭 / 已驯服的狼完成（终结武器走的是不带实体的伤害源，
  所以用 `ChallengeTracker` 记录最后攻击者来补这个归属）；
- 1/2 → 广播进度；2/2 → 广播「挑战完成」并把 `challengeCompleted` 写进存档；
- 非合法击杀 → 广播「挑战判定失败（可重来）」。

### 5. 调试命令扩展

`/witherchallenge status` 现在还会输出：`感染结束=true/false｜挑战 n/2（已完成/失败）`。

---

---

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
