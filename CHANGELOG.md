# CHANGELOG

《凋亡回响》整合包（Minecraft 1.20.1 + Forge 47.4.28）开发记录。
（仓库初始化时由脚本按版本重建；更早的细节以各版本构建产物为准。）

## 2026-11-07 14:5x · version_0.0.9：凋零腐败改成走 CWS 自己的逻辑

产物：`final_modpack/version_0.0.9/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.2 MB）

### 问题

0.0.8 里「感染力量之箭」给玩家挂的是我们自己 `addEffect(wither_sickness)` 的临时做法，
和 CWS 原生的凋暴感染不是同一套，于是：
效果阶段、治愈延迟、免疫期、死亡保留全都不一致，金苹果炖菜的治疗也和原版对不上。

### 改法：完全交给 CWS 的 tracker

`SicknessControl` 重写：

- **感染**：箭命中玩家 → `WitherSicknessTracker.infect()`（就是凋暴感染用的那个 capability）
  + 1 小时凋零 II。效果、阶段、治愈延迟、免疫期全部由 CWS 自己算；
- **治疗**：金苹果炖菜走 CWS 原生的 `GoldenAppleStewItem → beginCure()`，我们不再插手；
- **治愈后的处理**（每 20 tick 检查一次 `tracker.isInfected()`）：

| 情况 | 结果 |
| --- | --- |
| 世界里**没有凋暴** | 永久治愈（不管设置成什么） |
| 有凋暴 + `curemode permanent` | 永久治愈（和原版 CWS 一样） |
| 有凋暴 + `curemode timed` | 先按 CWS 原生流程解除腐败，然后给 `凋零残响`（`witherchallenge:wither_echo`，无害）效果 N 分钟；时间到 → 重新感染 |

- **死亡永远不治愈**：`PlayerEvent.Clone` 里把感染标记与 tracker 重新写回；
- 新增状态效果 `witherchallenge:wither_echo`（凋零残响，NEUTRAL，无任何数值作用），
  图标复用 CWS 的 `wither_sickness.png`。

### 指令

```
/witherchallenge curemode permanent      # 永久治愈
/witherchallenge curemode timed          # 限时治愈（默认）
/witherchallenge curetime <分钟>          # 限时时长（默认 10 分钟；sickness 是它的别名）
```

### 实测（服务端）

```
witherchallenge curemode permanent → 治愈模式 = 永久
witherchallenge curemode timed     → 治愈模式 = 限时（10 分钟后再次感染）
witherchallenge curetime 3         → 时长已设为 3 分钟
witherchallenge curemode timed     → 治愈模式 = 限时（3 分钟后再次感染）
```

（真机上「吃炖菜 → 延迟 → 解除 → 残响 → 再感染」这条链路需要玩家操作，服务端跑不了，留给你验收。）

---

---

## 2026-11-07 14:1x · version_0.0.8：时空间隙只吃终结武器的伤害

产物：`final_modpack/version_0.0.8/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 7.9 MB）

### 问题

用户反馈「凋暴会误伤 corpse」：W.A.R. 自带的免疫只写了玩家近战、箭、药水、火、摔、仙人掌、
溺水、雷、爆炸、三叉戟、铁砧、龙息、凋零、凋零之首这些来源；
凋暴的**火焰头颅**（`witherstormmod:flaming_wither_skull`，伤害类型是 `mob_attack`）
以及其它生物的近战不在名单里，照样能扣时空间隙的血。

### 改法

`WeaponCombatEvents` 加了一道总闸 `LivingAttackEvent`：

- 目标是时空间隙时，**默认全部取消**；
- 仅两个例外放行：
  1. 我们自己打出去的终结武器伤害（用 `INSIDE_OUR_HIT` 标记包住那一次 `hurt`）；
  2. 带 `BYPASSES_INVULNERABILITY` 的来源（`/kill`、虚空、`out_of_world`），
     保证 T9 里「/kill 也认」的约定不丢。

### 实测（服务端 /damage）

```
summon corpse                                  → Health 100.0
damage 40 mob_attack      → Target is invulnerable to the given damage type   health 100.0
damage 40 player_attack   → Target is invulnerable                            health 100.0
damage 40 wither_skull    → Target is invulnerable                            health 100.0
damage 40 out_of_world    → Applied 40.0 damage                                health 60.0
```

（`/kill` 走的就是 `out_of_world` 这一类，所以仍可用；凋暴头颅、其它生物近战全部无效。）

---

---

## 2026-11-07 13:4x · version_0.0.7：换成 entityculling 1.11.3，设置界面回来了

产物：`final_modpack/version_0.0.7/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 7.7 MB）

### 结论

0.0.6 里为了绕开 TRender 竞态崩溃，把 entityculling 内嵌的 TRender/TRansition 删了，
代价是它的设置界面打不开。0.0.7 换成 **entityculling-forge-1.11.3-mc1.20.1**，
**设置界面恢复，崩溃也不会再出现**。

### 依据（不是试出来的，是看代码）

- 1.11.3 内嵌的是 `TRansition-1.0.26` + `TRender-1.0.17`；
- 反编译 `TRansition-1.0.26` 的 `dev.tr7zw.transition.manager.ConfigScreenManager`：

```java
private final List<Function<Screen, Screen>> configScreens = new ArrayList<>();
public void registerConfigScreen(Function<Screen, Screen> configScreen) {
    this.configScreens.add(configScreen);      // 普通 add，没有下标
}
```

  而崩在 1.0.11 的是 `add(1, ...)`（空列表上用下标插入 → IndexOutOfBounds）。
  新版连“按下标插入”的写法都没有了，所以那条崩溃路径**在结构上不存在**。

- 实测：用 0.0.7 的 51 个 mod 启动客户端 3 次，`[TRender] Initializing Client...` 都正常打印，
  没有任何 `ArrayIndexOutOfBounds`。其中一次在 13:35 完整走到主菜单（`Game took 37.257 seconds to start`）；
  另外两次是我这边远程会话锁屏后窗口被系统关掉，属于测试环境问题，与 mod 无关。

### 变化

| 项 | 0.0.6 | 0.0.7 |
| --- | --- | --- |
| entityculling | 1.9.5（删掉内嵌 GUI 库的副本，`overrides/mods`） | **1.11.3 原版**（`files` + Modrinth 下载链接） |
| 设置界面 | 打不开 | 正常 |
| `patched_mods/entityculling-...-wc1.jar` | 使用中 | 已弃用（文件还在，可删） |

其余内容与 0.0.6 完全一致（阵营统一、亡灵接触转化、炖菜材料、判改最后一只 corpse、
出生点小屋命令方块换灵魂沙、W.A.R. 中文化、Boss 血量缩放、音效等）。

---

---

## 2026-11-07 00:0x · version_0.0.6：凋灵阵营统一 + 亡灵接触转化 + 炖菜材料

产物：`final_modpack/version_0.0.6/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 7.1 MB）

### 1. 「凋灵阵营」内部互不敌对

`FactionRules` 从「CWS ↔ W.A.R. 不敌对」扩成四类同阵营：

- CWS 生物（`witherstormmod:*`）
- W.A.R. 生物（`withering_away_reborn:*`）
- 凋灵（`minecraft:wither`）
- 原版亡灵：zombie / husk / drowned / zombie_villager / skeleton / stray /
  wither_skeleton / phantom / zombie_horse / skeleton_horse / zoglin

阵营内互相设置攻击目标时取消（`LivingChangeTargetEvent`）；对玩家、对非亡灵生物不受影响。

### 2. 有时空间隙时，亡灵会走过来并被转化

`UndeadConversion`（每 20 tick 一次）：

- 同维度存在活着的时空间隙才生效；
- 原版亡灵在 32 格内锁定最近的 W.A.R. 生物，每 20 tick 重新下达寻路（避免被原版 AI 覆盖）；
- 碰撞箱相交（膨胀 0.35 格）→ 走转化表变成对应的 W.A.R. 生物 + 灵魂粒子；
- 转化表里没有的亡灵（骷髅、凋灵骷髅等）改为挂凋零 II，不会无限重复触发。


### 追加（2026-11-07 00:3x，重做 0.0.6）：修启动崩溃 —— entityculling 内嵌的 TRender

用户 00:19 的错误报告：客户端在 `Mod loading error` 阶段崩，报告里写
`MOD trender ... ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0`
栈顶是 `dev.tr7zw.transition.manager.ConfigScreenManager.registerConfigScreen`。

根因：`entityculling-forge-1.9.5` 用 jar-in-jar 内嵌了 `TRender` + `TRansition`，
TRender 在**构造期**就调 `TRansition.ConfigScreenManager.registerConfigScreen`，
而 Forge 是并行构造 mod 的 —— TRansition 自己的列表还没初始化好，就会 `add(1, ...)` 越界。
这是竞态，所以 0.0.1–0.0.5 偶尔能过、这次正好撞上。

修复：`tools/patch_entityculling.py` 生成副本
`patched_mods/entityculling-forge-1.9.5-mc1.20.1-wc1.jar`：删掉内嵌的
`TRender-1.0.10` 与 `TRansition-1.0.11`，并把 `META-INF/jarjar/metadata.json` 清空。
entityculling 的实体剔除功能不依赖它们（只影响它的配置界面）。

验证：把 0.0.6 的 51 个 mod 全部灌进客户端测试副本启动 →
`Game took 40.378 seconds to start`，无 mod loading 崩溃、log 里再无 TRender/TRansition 报错。

代价：entityculling 的配置界面打不开（功能本身正常）。

### 3. W.A.R. 原材料可用于合成金苹果炖菜

CWS 的炖菜配方是 `cure_ingredient 标签 + 迷之炖菜 + 金苹果 + cure_base 标签`。
我们把 W.A.R. 的 3 个原材料物品加进 `witherstormmod:cure_ingredient` 标签：

- 凋灵骨 `witherbone`
- 凋灵骨碎片 `wither_bone_shard`
- 凋灵茎块 `chunk_of_wither_stem`

这样任意一种都能替代原来的「治愈材料」来合成炖菜（炖菜的「暂时压制凋零腐败 10 分钟」逻辑见 0.0.5）。

---

---

## 2026-11-06 22:5x · version_0.0.5：按反馈修判定 + 补齐交付件

产物（三件）：
- 
inal_modpack/version_0.0.5/不死的凋灵-0.0.5.mrpack（309.0 MB）
- 
inal_modpack/version_0.0.5/起始存档-不死的凋灵.zip（7.2 MB，含 48×48 战场平台 @ 512,70,0）
- 
inal_modpack/version_0.0.5/服务端-不死的凋灵.zip（346.4 MB，51 mod + config + 启动脚本）

### 1. 判定改了：最后一只 corpse，而不是击杀计数

- 通关成就 
inal 现在只在「世界里已经没有任何时空间隙」时触发（onCorpseDied 里判断），
  不再看「打死了几只加强版」；
- 也就是说：你同时召 n 只凋灵 → 有 n 个 corpse（1 只加强 + n-1 只未加强），
  只要把世界上所有 corpse 清空，就算通关；
- 挑战计数只留在 /witherchallenge status 里做参考，不参与判定。

### 2. 挑战播报全删

截图里那几条 已击败 1/2、已击败 2/2、挑战完成、最后一只时空间隙已被击败 —— 感染终止了。
全部去掉了，只在服务端日志里留一行。

### 3. 修「击杀凋暴的 corpse 后它复活一次」

根因：凋暴死亡时「死亡事件」和「看门狗」各补了一只 corpse，于是场上总有两只，
打死一只另一只还在 → 看起来像复活。
修复：每只凋暴的 UUID 只允许补一次 corpse（HANDLED_STORMS），死亡事件与看门狗互斥。

### 4. 感染规则回到「只看世界上有没有 corpse」

- 有 corpse → 感染继续（rage 锁、扩散、召唤都恢复）；
- 一只不剩 → 感染终止：清掉场上 W.A.R. 生物、拦掉后续生成；
- 之后再有凋灵死亡（W.A.R. 会给未加强 corpse）或凋暴死亡（我们给加强 corpse），感染自动重新开始。

### 5. CWS 与 W.A.R. 生物互不敌对

FactionRules 在 LivingChangeTargetEvent 上拦截：跨 mod 的目标直接取消。
对玩家、对同 mod 生物的仇恨不受影响。

### 6. rage mode：只要场上有加强 corpse 就锁

CorpseRules 每秒检查一次，有加强版 corpse 就把 W.A.R. 的世界变量 
age 置 true 并同步。

### 7. 凋零腐败：可以「暂时治」，时长可指令配置

- 吃 witherstormmod:golden_apple_stew（金苹果炖菜）→ 压制凋零腐败 **10 分钟**（默认），
  到点自动回来；
- /witherchallenge sickness <分钟> 改这个时长（存档持久化）；
- 命令行外的实现：SicknessControl 每秒给已感染且不在压制期的玩家续 3 秒的腐败效果，
  所以压制一结束就会立刻回来。

### 8. 指南书补上指令页

成书从 23 页变成 25 页，新增：/witherchallenge status|tags|recipes|entities|sickness 的说明，
以及「感染只看世界上有没有时空间隙」这条规则。

### 9. 音效（不新增任何音频文件）

FxAndScaling：合成（铁砧放置 1.6）、铁砧升级（铁砧使用 1.2）、重击时空间隙（暴击音 0.6）、
通关（成就提示音）；激活书（信标激活）与凋暴召唤（雷声）原本就有。

### 10. Boss 血量缩放接上了

凋暴 / 共生体加入世界时按在线人数 ×(1+0.5(n-1))（上限取配置 oss_health_multiplier），
原血量存在实体 NBT 里，避免重复叠加。

### 11. W.A.R. 中文化

新增 ssets/withering_away_reborn/lang/zh_cn.json，翻译 127 个玩家可见的实体 / 物品 / 方块名
（脚本 	ools/gen_war_lang.py，可继续加词条）。

### 12. 出生点「凋暴小屋」的命令方块 → 灵魂沙（用户要求）

CWS 会在出生点附近生成 *_storm_spawn_platform 结构（9 个生物群系变体），里面各有 2 个命令方块。
处理：生成 CWS 的修改副本 patched_mods/witherstormmod-1.20.1-4.2.1-all-wc1.jar，
把 9 个结构 NBT 里共 **18 个命令方块** 的 palette 名称换成 minecraft:soul_sand。

- 做法：解 gzip → 字节级替换 minecraft:command_block（NBT 字符串自带长度前缀、复合标签无长度字段，
  改长度安全）→ 重新 gzip；原件不动；
- 脚本：	ools/patch_cws.py（可重复执行，幂等）；
- 校验：替换后这 9 个结构里 command_block 出现次数 = 0，soul_sand = 27；
  服务端实测 place template witherstormmod:storm_spawn_platform 正常加载（结构没损坏）。

### 12. 关于「原版成就看不到」

实测服务端加载进度：{minecraft=1271, witherstormmod=82, witherchallenge=9, withering_away_reborn=11, command_tool_expansion=13}，
原版 1271 条都在。我们的 9 条是一个**独立的根标签页**，进度界面里点上面的标签切换即可；
如果确实只剩一个标签，请把进度界面截图发我。
（新增了 /witherchallenge advancements 可以现场打印各命名空间的数量。）

---

---

## 2026-11-06 21:5x · version_0.0.4：修 0.0.3 的两个问题 + 剩余功能一次补齐

产物：`final_modpack/version_0.0.4/不死的凋灵-0.0.4.mrpack`（309.0 MB，51 mod）

### A. 修 0.0.3 的两个问题（用户反馈）

**A1. 凋暴死亡不释放加强版 corpse**

原因：我以为这事 W.A.R. 会做。反编译确认 W.A.R. 的 `WhenWitherDiesProcedure` 只监听
`minecraft:wither`（普通凋灵），**凋暴死亡根本不归它管**——所以这条从来没人实现。

修复：`CorpseRules` 做了两层：
- `LivingDeathEvent` 捕获凋暴死亡 → 20 tick 后在死亡点生成 `the_corpse_of_the_wither` 并立即强化；
- 另加**看门狗**：所有在场的凋暴都登记 UUID + 最后坐标，一旦实体消失（死亡、被 /kill、被移除）
  → 同样补一只加强版 corpse。这样不依赖 CWS 到底走不走死亡事件。

实测（`/summon` 凋暴 → `/kill`）：

```
kill @e[type=witherstormmod:wither_storm] -> Killed Wither Storm
data get ... the_corpse_of_the_wither Health -> 150.0f
Attributes -> [{Base: 150.0d, Name: "generic.max_health"}, ...]
```

**A2. 感染是否继续「只看世界上有没有 corpse」，不看击败次数**

改法：`InfectionControl` 的 `ended` 不再是单向闩锁——
- 任意 corpse 出现（包括凋暴死亡新生成的那只）→ `ended = false`，广播「感染重新开始扩散」；
- 所有 corpse 都不在了 → `ended = true`，广播「感染终止」，并清掉场上 W.A.R. 生物、拦掉后续生成。

同时去掉任何「击杀次数」参与判定的逻辑（挑战进度是独立的，只影响成就/广播）。

### B. 凋暴强化（`combat/StormCombat`）

| 项 | 实现 |
| --- | --- |
| 1–3 阶段 8 连发 | 拦掉 CWS 自己射出的凋零之首，改由我们按「黑黑黑蓝黑黑黑蓝」重发，发间隔 4 tick |
| 4 阶段起本体停火 | 本体射出的头颅一律取消 |
| Head 机炮 | 阶段 ≥3 且全局冷却结束时，每 0.2 秒从头部发射 1 发蓝色头颅，持续 5 秒；之后全局冷却 8 秒 |
| 牵引光束感染 | 每 20 tick 对每道激活的光束做射线检测，在落点半径 3 格按 `BlockInfection` 表感染方块 |
| 召唤 W.A.R. 生物 | 每 5 秒 1–2 只（8–24 格外），带 `wither_storm_spawned` 标签；标签上限 60、全服 W.A.R. 上限 120；超 200 格自动移除 |

### C. 凋灵共生体锁血（`combat/SymbiontLock`）

锁点 3/4、1/2、1/4，状态存实体 NBT；贴着锁点时 `LivingHurtEvent` 直接取消（无音效无击退），
`/kill` 与虚空仍然有效（`BYPASSES_INVULNERABILITY`）；每打掉一个 Wither Storm Head 解锁一档。

### D. 9 个进度 + 3 个自定义触发器

- `data/witherchallenge/advancements/` 下 9 个 JSON：root / kill_wither / first_core / craft_arrow /
  awaken_storm / kill_storm / beacon_4 / craft_weapon / final；
- 根节点背景用 CWS 的 `tainted_stone.png`（复制进 `assets/witherchallenge/textures/gui/advancements/backgrounds/`）；
- 自定义触发器 `witherchallenge:awaken_storm / beacon_4 / final` 在 `FMLCommonSetupEvent` 里
  `CriteriaTriggers.register(...)`（1.20.1 没有可用的 TRIGGER_TYPES 注册表常量，踩过一次）；
- 触发点：箭召出凋暴 → awaken_storm；信标激活书 → beacon_4；两只加强 corpse 都被击败 → final（发给全服）。
- 实测：`Loaded 1386 advancements`，我们这 9 个没有解析错误（唯一的报错是 wwst 自带的 `bwsb:stormlusioner_killed`）。

### E. 挑战指南书（首次进服自动给）

- `item/GuideBook`：把 `task/挑战指南.docx` 的 23 段正文写成成书（书名《不死的凋灵 · 挑战指南》，作者 cyw12）；
- `item/GuideBookEvents`：玩家首次进服放进物品栏第一格（已给过就记在玩家 NBT 上）。

### F. 配置（`WCConfig`，`config/witherchallenge-common.toml`）

7 项：PVP 武器特效、宠物是否可被感染、Boss 血量倍率上限、召唤标签上限、全服 W.A.R. 上限、
光束感染开关、加强 corpse 加速感染开关。按用户要求**没有**给「corpse 免伤」和「凋零腐败」做开关。

---

---

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
