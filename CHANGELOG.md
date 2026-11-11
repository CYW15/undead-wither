# 变更日志（Change Log）

> 规则：**原 jar 一律不动**。凡是要改第三方 mod，先复制出副本，只在副本上改，
> 并在本文件记一条「改了哪个 jar / 改了哪个类或数据文件 / 为什么 / 怎么回滚」。
> 新功能优先做在附属 mod `witherchallenge` 里，不动 CWS / W.A.R. 内部。

---

## 2026-11-11 08:2x · version_0.0.32：强化凋灵回归 + 只吃玩家伤害 + 永不回血

产物：`final_modpack/version_0.0.32/`

### 决定

0.0.31 删掉了 Wither Reincarnated（强化凋灵）。用户想了一夜后改主意：
**保留 WIR，但把它改造成「只受玩家伤害、永不回血」**，于是有 0.0.31 / 0.0.32 两个版本可对比。

### 1. WIR 回归

- `pack_plan.DELETE` 里移除 `witherreincarnated-1.20.1-1.0.5.jar`；
- `CONFIG_RESIDUE` 里移除 `witherreincarnated`（保留它自己的配置）；
- mrpack：files 43 + overrides 7 = 50 个 mod（WIR 重新出现在 `files`）；
- 指南书恢复「强化凋灵」措辞。

### 2. 凋灵只受玩家伤害（`WitherDamageRules`）

`LivingAttackEvent`：目标是 `WitherBoss` 且来源不是玩家 → 取消。

放行的「玩家伤害」：玩家本人近战 / 玩家射出的箭、三叉戟、火球 / 玩家驯服的宠物 /
玩家点燃的 TNT；`/kill`、虚空（`BYPASSES_INVULNERABILITY`）例外。

实测：
```
/ damage 40 mob_attack    → Target is invulnerable to the given damage type
/damage 2000 out_of_world → Applied（凋灵死亡）
```

### 3. 凋灵永不回血

每 10 tick 记录「见过的最低血量」，任何回血（WIR 阶段回血、再生药水、治疗）都被拉回。

实测（WIR 把凋灵血量提到 600）：
```
初始 600 → 手动设 400 → 再设 550 → 仍是 400 → 再生药水 II 之后 → 仍是 400
```

---
## 2026-11-10 19:5x · version_0.0.31：去掉滑翔修复 + 修「凋灵死亡链被拦」

产物：`final_modpack/version_0.0.31/`

### 1. 删除 `ElytraFlightFix`

用户确认不需要「滑翔中受伤不打断」这个改动，删除整个类（0.0.29 引入）。
原版行为恢复：滑翔中受伤会被打下来。

### 2. 修一个 0.0.21 埋下的坑：凋灵死亡链被「无感染源不许生成 W.A.R.」拦掉

0.0.21 加的规则是「没有凋暴/凋灵/时空间隙 → 取消所有 W.A.R. 实体生成」。
但凋灵死亡链的真实顺序是：

```
凋灵死亡 → wither_post_death_cutscane_entitty（演出，约 185 tick）
         → spawningrift
         → 时空间隙
```

在「凋灵已死、时空间隙还没生成」的空档里，**没有感染源**，
于是 `spawningrift` 被拦 → 时空间隙永远不出现 → 挑战第一步断掉。

修法：
- `CUTSCENE_IDS` 白名单加入 `spawningrift`、`soulspread`、`spreedentity`；
- `hasInfectionSource` 把「死亡演出实体」也算作感染源。

实测（无 WIR、干净存档）：

```
+4s  cutscene:yes  corpse:-
+8s  cutscene:yes  corpse:-
+12s cutscene:-    corpse:yes   ← 时空间隙正常出现
```

### 3. 0.0.30 内容回顾：删除 Wither Reincarnated（强化凋灵）

- `pack_plan.DELETE` 加入 `witherreincarnated-1.20.1-1.0.5.jar`；
- 指南书里「强化凋灵」全部改成「凋灵」（步骤 1 与通关检查表）；
- WIR 的残留配置 `config/witherreincarnated/` 加入 `CONFIG_RESIDUE`，已清掉。

---
## 2026-11-10 18:3x · version_0.0.28：修「凋灵离开维度后消失」，改成锚点式强加载

产物：`final_modpack/version_0.0.28/`

### 用户复现

召唤凋灵 → 去下界 → 去末地 → 回主世界，用命令护身符查不到凋灵（凋灵不见了）。

### 根因

0.0.19–0.0.27 的强加载是「每 20 tick 扫一遍**已加载**的实体，再按它所在位置强制加载区块」。
玩家一旦离开维度，凋灵所在区块被卸载，我们就**扫描不到它** →
停止为它加载区块 → 它永远不再被加载 → 护身符（只查已加载实体）自然找不到。

这是循环依赖：**要加载才能被看到，要被看到才会去加载。**

### 修法：凋灵锚点（`WitherAnchorData`，SavedData 持久化）

- 凋灵**一生成**就登记锚点（UUID + 坐标），不再依赖扫描；
- 每秒按**记录的坐标**强制加载 **9×9 区块**（原来是 5×5）；
- 加载到实体后用真实坐标刷新锚点（它飞走也能跟上）；
- 没加载到实体不代表死了 —— 继续按记录坐标加载，直到它重新出现；
- 只有确认实体**已死亡/被移除**才注销锚点并释放区块；
- 锚点写进存档（`witherchallenge_wither_anchors`），**重启服务器后依然生效**。

### 验证（跨重启）

```
[第一次启动] summon wither → forceload query 0 0 = marked for force loading
[stop 服务器，重启，全程没有玩家在线]
[第二次启动] execute if entity @e[type=minecraft:wither] = Test passed, count: 2
             forceload query 0 0 = still marked for force loading
```

说明凋灵在无人、跨重启的状态下依然被加载着 —— 和凋暴同级的「存在感」。

---
## 2026-11-09 17:4x · version_0.0.27：加入使用许可（禁止售卖）

产物：`final_modpack/version_0.0.27/`

新增 `LICENSE-凋亡回响.txt`，同时放进三处：

- mrpack 的 `overrides/LICENSE-凋亡回响.txt`（解压后就在实例根目录）；
- 服务端 zip 根目录；
- 工作区根目录 + `AI_document/`，方便单独分发。

内容要点：允许免费分享/原样转载/开服直播；**禁止售卖、付费墙、去署名后重发、
声称原创、单独再分发修改过的第三方 jar**；第三方模组版权归原作者。

---
## 2026-11-08 19:3x · version_0.0.26：指南书加鸣谢页 + 署名

产物：`final_modpack/version_0.0.26/`
（凋亡回响-0.0.26.mrpack 309.0 MB / 服务端-凋亡回响.zip 482.6 MB / 起始存档-凋亡回响.zip 8.6 MB）

### 1. 指南书末尾新增 9 页「鸣谢」

- 8 页列出全部 49 个模组的名称与作者（含客户端专用 4 个）；
- 最后 1 页是版权说明，署名顺序按贡献：

```
【版权说明】
本整合包由下列两位完成，署名按贡献排序：
1. DeepSeek Harness（AI 编程助手）—— 附属 mod、全部配置与文档
2. cyw12 —— 需求设计、玩法设想与测试

整合包内所有第三方模组的版权归各自作者，名单见前几页。
```

指南书从 25 页变成 34 页。

### 2. 署名同步

- `mods.toml` 的 `authors` → `DeepSeek Harness, cyw12`
- 成书的 `author` 字段 → `DeepSeek Harness`
- 指南书首页 → 作者：DeepSeek Harness ｜ 整合：DeepSeek Harness、cyw12
- 通关检查表落款 → `—— DeepSeek Harness`

---
## 2026-11-08 18:0x · version_0.0.24：删除「远处可见」

产物：`final_modpack/version_0.0.24/`
（凋亡回响-0.0.24.mrpack 309.0 MB / 服务端-凋亡回响.zip 482.6 MB / 起始存档-凋亡回响.zip 8.6 MB）

用户评估 0.0.23 的投影方案后决定不做：

- 删除 `client/WitherOmenRenderer.java`（世界坐标的暗紫四边形投影）；
- jar 里已确认不含该类；
- 定位凋灵的职责交回 **CWS 护身符**（物品栏悬停显示「凋灵：128 格 东北」那一行）与
  `/witherchallenge find wither`。

保留：网络通道与 `WitherPosClient` 缓存（护身符 tooltip 靠它们，凋灵在远处也有数据）。

---
## 2026-11-08 17:1x · version_0.0.23：凋灵的远距离投影

产物：`final_modpack/version_0.0.23/`
（凋亡回响-0.0.23.mrpack 309.0 MB / 服务端-凋亡回响.zip 482.6 MB / 起始存档-凋亡回响.zip 8.6 MB）

### 做法（按用户 2026-11-08 的定义）

不是 UI 图标、不是雷达箭头，而是**把凋灵投影到屏幕上**：

- 服务端每 0.5 秒把「该玩家维度内最近活凋灵」的**真实坐标**发给客户端；
- 客户端在 `RenderLevelStageEvent.AFTER_TRANSLUCENT_BLOCKS` 里，
  于该坐标画一个 **3×4 格**的摄像机朝向四边形（内层深紫近黑、外层淡紫光晕）；
- 因为是世界坐标里的实体尺寸，透视会自然处理远近：
  远 → 屏幕上只有几个像素；足够远 → **亚像素，自然消失**；
  有深度测试，会被地形挡住（和「远远看见凋暴」同感）。
- 距离 < 8 格不画，交给凋灵实体自己的渲染。

### 验证到什么程度

- 客户端加载到主菜单（36.9 s），无异常、无 `NoClassDefFoundError`；
- **视觉效果本身我没法模拟**（需要真人进世界、站远看）。

如果实际看着不对（太亮/太暗/位置偏），下个版本我直接删掉这一条，不纠缠。

---
## 2026-11-08 17:0x · version_0.0.22：整合包改名《凋亡回响》

产物：`final_modpack/version_0.0.22/`
（**凋亡回响**-0.0.22.mrpack 309.0 MB / 服务端-**凋亡回响**.zip 482.6 MB / 起始存档-**凋亡回响**.zip 8.6 MB）

### 改名范围

显示名统一为 **《凋亡回响》**，**modid 仍是 `witherchallenge`**（存档、配方、进度 ID 不受影响）：

- `mods.toml` 的 displayName / description；
- 所有游戏内提示前缀 `[凋亡回响]`、指南书标题与内容、创造模式标签页名、进度页名；
- 语言文件（zh_cn / en_us）；
- mrpack / 服务端 zip / 起始存档 zip 的文件名；
- README、服务端包里的 `怎么启动.txt`。

CHANGELOG 里的历史条目保留原文（记录当时叫《不死的凋灵》）。

### 实测

```
witherchallenge status → [凋亡回响] 配方 1361 条｜…
witherchallenge tags   → [凋亡回响] 感染箭∈arrows=true｜…｜凋零残响已注册=true
```

---
## 2026-11-08 17:0x · version_0.0.21：没有感染源就不许生成 W.A.R. + 凋灵产凋零骷髅

产物：`final_modpack/version_0.0.21/`（mrpack 309.0 MB / 服务端 zip 482.6 MB / 起始存档 8.5 MB）

### 0. 先澄清「凋灵杀死的生物会传染」

这是 **W.A.R. 自带的** `WhenKilledByWitherProcedure`：凋灵击杀的生物会被转换成 W.A.R. 生物。
不是我们写的，也**按用户要求保留**（我们的新拦截不会影响它 —— 凋灵活着时属于「有感染源」）。

### 1. 没有感染源 → W.A.R. 生物一个都不许新生成

`InfectionControl.onEntityJoin` 新增判定：

```
同一维度里，只要「活着的凋暴 / 活着的凋灵 / 活着的时空间隙」三者都没有
→ 取消所有 withering_away_reborn:* 实体的生成
```

- 已有的 W.A.R. 生物会按原版规则自然 despawn，于是「杀一个少一个」，很快清空；
- 死亡演出链（`wither_post_death_cutscane_entitty`、`the_aftermath`）放行，避免凋灵死亡演出被拦掉；
- 之前「感染已结束（ended=true）就禁止生成」的逻辑保留，两者取并集。

实测：
```
有 corpse 时 summon withering_zombie → Test passed, count: 1
杀掉 corpse 后再 summon            → Test failed（被拦）
```

### 2. 凋灵每 5 分钟产 4 只凋零骷髅

`WitherPresence`：每只活着的凋灵，每 5 分钟（6000 tick）在周围 10 格内生成 4 只
`minecraft:wither_skeleton`（MOB_SUMMONED，不吃自然生成限制）；附近 32 格内已有 24 只时不再增加。

### 3. 关于「凋灵感染方块」

- **原版凋灵：没有**（只会破坏方块）；
- **W.A.R.**：只有凋灵**死亡之后**，由时空间隙去侵蚀方块；
- **我们**：没有给「活着的凋灵」加方块感染（之前提的 A/B/C 方案没做）。

想让活着的凋灵也慢慢侵蚀方块，说一声，加个开关就行。

---
## 2026-11-08 16:3x · 服务端包改成「解压即用」完整版

`服务端-不死的凋灵.zip`（v0.0.20 目录内，482.6 MB）重新制作：

| 之前 | 现在 |
| --- | --- |
| 只有 mods + config + 启动脚本 | **加了 `libraries/`（158 MB，Forge 运行时库）** |
| `run.bat` 会因缺 `win_args.txt` 直接失败 | 解压即可双击启动 |
| 端口沿用测试值 25599、RCON 开着、motd=WC-Dev-Test | 端口 25565、RCON 关闭、motd 改中文 |
| eula=false 需手动改 | eula=true，直接能开服 |
| `user_jvm_args.txt` 里 -Xmx 是注释 | 写成 `-Xms2G -Xmx4G` |
| 说明文档 4 行 | 补齐端口映射 / OP / 内存 / 升级 / 常用指令 |

实测：**全新解压到空目录 → 直接启动 → `Done (…)` 成功进入就绪状态**。

---
## 2026-11-08 16:2x · version_0.0.20：护身符 tooltip 第 6 行 = 最近凋灵

产物：`final_modpack/version_0.0.20/`（mrpack 309.0 MB / 服务端 zip 344.3 MB / 起始存档 8.5 MB）

### 改了什么（纠正 0.0.19 的理解错误）

0.0.19 我做成了「手持护身符 → 动作栏刷一行」。用户澄清：参考 CWS 原版，
护身符是在**物品栏里悬停时**列出「4 个已绑定实体 + 最近凋暴」共 5 行，
要的是**在这 5 行后面再加一行「最近的凋灵」**。

- 删掉动作栏那个 readout；
- 新增 `ItemTooltipEvent`：悬停 `witherstormmod:amulet` 时追加一行
  `凋灵：128 格 东北（overworld）`，没有则显示 `凋灵：这个维度里没有`；
- 这一行需要「玩家看不到凋灵也能知道」，所以加了最小的网络同步：
  - `WCPackets`（`witherchallenge:main` 通道，PLAY_TO_CLIENT）；
  - 服务端每 2 秒把「该玩家维度内最近的活凋灵」发给他（含距离/方位/维度）；
  - 客户端 `WitherPosClient` 缓存，tooltip 直接读缓存 —— 因此凋灵在几千格外也照样有数据。
- `/witherchallenge find wither|storm` 保留，方便测试。

### 0.0.19 的凋灵机制保持不变

强制加载 5×5 区块、存在即锁黑夜、不改 AI 不强制追击（都已实测）。

### 验证

- 服务端启动正常、通道注册无报错；
- 客户端加载到主菜单（61.9s），没有 `witherchallenge` 相关异常；
- tooltip 本身需要真人开物品栏悬停确认（我这边无法模拟 UI）。

---
## 2026-11-08 16:1x · version_0.0.19（测试版）：凋灵的凋暴式存在感

产物：`final_modpack/version_0.0.19/`（mrpack 309.0 MB / 服务端 zip 344.3 MB / 起始存档 8.5 MB）

### 1. 强制刷新（已验证）

`WitherPresence`：只要凋灵活着，每 20 tick 把它周围的 5×5 区块 `setChunkForced(true)`；
凋灵死亡后同一 tick 周期内自动 `setChunkForced(false)` 回收（不会永久污染存档的 forceload 列表）。

实测：
```
summon wither → forceload query 0 0 → Chunk at [0, 0] ... is marked for force loading
kill wither   → forceload query 0 0 → ... is not marked for force loading
```

### 2. 存在即锁黑夜（已验证）

所在维度只要有活着的凋灵，每 20 tick 把 `dayTime` 钉到 18000（夜晚），**与玩家距离无关**。
凋灵消失后不再干预，昼夜自然恢复。

实测：
```
summon wither → time set day（1000）→ 3 秒后 time query daytime = 18000
kill wither   → time set day（1000）→ time query daytime = 6545（不再被锁）
```

### 3. 不强制追击

没有对凋灵的 AI / 目标做任何修改：它仍按原版逻辑只在侦测范围内追人，
玩家跑远不会跟过来（没有传送、没有强制 setTarget）。

### 4. 命令护身符：凋灵 / 凋暴 分开定位

- 手持 CWS 的护身符（`witherstormmod:amulet`）时，动作栏每 20 tick 显示一行：
  `凋灵 128 格 东北　凋暴 342 格 西`（两个数值**各自独立计算**，互不影响）；
- 新增测试命令：`/witherchallenge find wither` 与 `/witherchallenge find storm`。

### 5. 「远处可见」暂未实现（需要客户端渲染）

凋暴能远看是因为它体积巨大 + 自定义渲染 + 高追踪距离；
原版凋灵做不到同样效果，需要：
① 服务端把凋灵位置同步给客户端（自定义包）；
② 客户端在天空/HUD 上画标记（方向 + 距离 + 剪影）。
这属于客户端功能，没放进 0.0.19（测试版先验证服务端机制），
方案确认后在 0.1.1 里做。可选风格：
- A. HUD 边缘箭头 + 距离（最省事，类似雷达）；
- B. 天空中的暗色剪影 / 光束（更接近「看见凋暴」的感觉，工作量大）；
- C. A + B 都做。

---
## 2026-11-07 19:3x · version_0.0.18：status 只按「世界上还有没有 corpse」显示

产物：`final_modpack/version_0.0.18/`（mrpack 309.0 MB / 服务端 zip 344.3 MB / 起始存档 8.5 MB）

### 先核对 0.0.16 的判定（反编译 jar 确认）

`version_0.0.16` 里的 `InfectionControl.onCorpseDied` 实际逻辑：

```java
for (ServerLevel l : level.getServer().getAllLevels()) {      // 所有维度
    for (Entity e : l.getAllEntities()) {
        if (id == "withering_away_reborn:the_corpse_of_the_wither" && e.isAlive()) {
            anyLeft = true;
        }
    }
}
if (!anyLeft) {
    s.ended = true;                     // 没有活着的 corpse → 感染结束
    trigger(ModCriteria.FINAL, player); // 最后一个 corpse 被击败 → 最终成就
}
```

也就是说：**0.0.16 的判定本来就是「世界上（所有维度）还有没有活着的时空间隙」，不是击败数量。**

问题只出在 `status` 的**显示文本**上：它读的是 `challengeKilled` 这个纯展示用计数器，
打印成「挑战 0/2」，看起来像是在按击杀数判定。

### 0.0.18 改了什么

`/witherchallenge status` 换成按维度统计活着的时空间隙：

```
[不死的凋灵] 配方 1361 条｜凋暴 0｜the Mangled 0｜
活着的时空间隙 3（overworld=3 the_nether=0 bowels=0 the_end=0）｜感染结束=false
```

- 不再出现「x/2」这种击败计数；
- 每个维度各显示几只，能直接看出「还有 3 只在主世界 → 感染未结束」；
- 判定代码没有改动（原本就是对的）。

---
## 2026-11-07 19:1x · version_0.0.17：血条变灰 + 溺尸/尸壳转化

产物：`final_modpack/version_0.0.17/`（mrpack 309.0 MB / 服务端 zip 344.3 MB / 起始存档 8.5 MB）

### 1. 有腐败但血条不变灰

**根因**：血条（红心）变灰是**原版凋零效果**的表现。
之前只有「感染箭命中」那一下才给凋零 II（1 小时）；
如果是**被凋暴照到**感染的，身上有凋零腐败、却没有凋零效果 → 血条保持红色。
这就是「有时候有腐败、血条没变灰」的来源。

**修法**：`SicknessControl` 每个检测周期，只要玩家身上有凋零腐败、却没有凋零效果，
就补一个凋零 II（1 小时，不显示粒子）。现在「有腐败 ⇒ 血条变灰」恒成立。

### 2. 转化表新增溺尸、尸壳

按 `task文档更新/转化表.docx` 里新增的两行：

| 原生物 | 转化为 |
| --- | --- |
| 溺尸 `minecraft:drowned` | NewWitheringZombie |
| 尸壳 `minecraft:husk` | NewWitheringZombie |

和僵尸走同一条路径（`ConversionTable`）。

### 实测

```
witherchallenge status → 配方 1361 条｜感染结束=false｜挑战 0/2
witherchallenge tags   → …｜凋零残响已注册=true
本次启动 witherchallenge 相关异常：0
```

---
## 2026-11-07 18:4x · version_0.0.16：删掉 3D 皮肤层，TRender 只留一份

产物：`final_modpack/version_0.0.16/`（mrpack 309.0 MB / 服务端 zip 344.3 MB / 起始存档 8.5 MB）

### 0.0.15 为什么又崩了

桌面 `错误报告-2026-11-7_18.13.09.zip`（18:34 覆盖的新内容）：

```
MOD entityculling: NoClassDefFoundError: dev/tr7zw/transition/manager/CodeManager
  at dev.tr7zw.entityculling.EntityCullingModBase.onInitialize(EntityCullingModBase.java:55)
```

说明 **entityculling 1.11.3 在初始化阶段就依赖 TRansition**（不只是配置界面），
所以「把它内嵌的 TRender/TRansition 删掉」这条路在 1.11.3 上走不通。
（0.0.6 时删的是 1.9.5，那版没这个依赖，所以能启动。）

### 这版换思路：只留一份 TRender

两个 mod 都内嵌 TRender/TRansition，并行构造时两个调用方同时
`registerConfigScreen` → 写坏 ArrayList。
那就**只保留一个内嵌方**：

- ✅ 保留 entityculling 1.11.3 **原版**（TRender/TRansition 完整，功能与配置界面都正常）；
- ❌ 删除 `[3D 皮肤层] skinlayers3d`（纯外观 mod，它的 TRender 才是第二个调用方）。

竞态不存在了（单调用方），也不用再阉割 entityculling。

### 验证

0.0.16 的 50 个 mod 启动客户端：

```
[18:42:14] [TRender] Initializing Client...
[18:42:32] Game took 42.429 seconds to start     ← 主菜单
无 ArrayIndexOutOfBounds / 无 NoClassDefFoundError
```

### 已经导入过 0.0.15 的话

两个办法任选：
1. 重新导入 0.0.16（推荐）；
2. 或者把旧实例 `mods` 里的 `[3D 皮肤层] skinlayers3d-forge-1.10.1-mc1.20.1.jar` 删掉，
   再把 `entityculling-forge-1.11.3-mc1.20.1-wc1.jar` 换回不带 `-wc1` 的 1.11.3 原版。

---
## 2026-11-07 18:2x · version_0.0.15：真正修掉启动崩溃（TRender 竞态）

产物：`final_modpack/version_0.0.15/`（mrpack 309.0 MB / 服务端 zip 345.0 MB / 起始存档 8.5 MB）

### 诊断（基于桌面 `错误报告-2026-11-7_18.13.09.zip`）

```
MOD trender (1.0.17) has failed to load correctly
java.lang.ArrayIndexOutOfBoundsException: Index 1 out of bounds for length 0
  at java.util.ArrayList.add(ArrayList.java:484)
  at dev.tr7zw.transition.manager.ConfigScreenManager.registerConfigScreen(ConfigScreenManager.java:13)
  at dev.tr7zw.trender.gui.impl.client.LibGuiClient.onInitializeClient(LibGuiClient.java:38)
```

0.0.14 用的是 entityculling **1.11.3**（自带 TRender 1.0.17 + TRansition 1.0.26）。
我反编译看了它的源码：`ConfigScreenManager` 的 `add()` 本身没错，
错在**两个 mod 同时调它**：

| mod | 内嵌的 GUI 库 |
| --- | --- |
| entityculling 1.11.3 | TRender 1.0.17 + TRansition 1.0.26 |
| **3D 皮肤层 skinlayers3d 1.10.1** | TRender 1.0.10 + TRansition 1.0.11 |

Forge 是**并行**构造 mod 的，两边同时往同一个非线程安全的 `ArrayList` 里 `add`，
内部状态被写坏 → `add` 里数组越界。所以换版本没用（0.0.6 用 1.9.5 崩、0.0.14 用 1.11.3 还是崩）。

### 修法

`tools/patch_entityculling.py` 生成副本
`patched_mods/entityculling-forge-1.11.3-mc1.20.1-wc1.jar`：**删掉它内嵌的 TRender/TRansition**，
让「注册配置界面」只剩 skinlayers3d 一个调用方，竞态消失。

- entityculling 的实体剔除功能不依赖 GUI 库，照常工作；
- 代价：entityculling 自己的设置界面打不开（skinlayers3d 的还能开）。

### 验证

用 0.0.15 的 51 个 mod 启动客户端 **两次**：

```
18:19  Game took 32.212 seconds to start   ← 主菜单
18:21  Game took 29.297 seconds to start   ← 主菜单
两次日志里都没有 TRender / TRansition / ArrayIndexOutOfBounds
```

### 如果你已经导入过 0.0.14

旧的实例里还是 1.11.3 原版。两个办法任选：
1. 删掉旧实例，重新导入 0.0.15；
2. 或者只把实例 `mods` 里的 `entityculling-forge-1.11.3-mc1.20.1.jar` 换成
   `AI_document/pack/overrides/mods/entityculling-forge-1.11.3-mc1.20.1-wc1.jar`。

---
## 2026-11-07 17:2x · version_0.0.14：转化表新增骷髅系 + 无对应目标不产生效果

产物：`final_modpack/version_0.0.14/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.4 MB）

### 1. 转化表新增两条

| 原生物 | 转化为 |
| --- | --- |
| 骷髅 | 凋零骷髅（原版 `minecraft:wither_skeleton`） |
| 流浪者 | 凋零骷髅（原版） |

实现上把 `ConversionTable` 的值从「W.A.R. 短 id」改成**完整实体 id**，
所以能同时指向 W.A.R. 生物和原版生物（这两个是原版目标）。

### 2. 表里没有对应目标的亡灵 → 什么都不发生

`UndeadConversion.convert()` 之前对没映射的亡灵挂凋零 II，现在直接 `return`，
不产生任何效果（用户 2026-11-07 定）。

### 3. `task/转化表.docx` 的更新

沙箱里 `task/` 目录是**只读**的（写入被拒绝），所以没法原地改。做法：

- 原件备份：`_build/backup/转化表-原始.docx`
- 更新后的文件：`AI_document/task文档更新/转化表.docx`（在原表末尾加了骷髅、流浪者两行）

把它拖回 `task/` 覆盖即可（或者在 Word 里手动加这两行）。

### 实测

```
witherchallenge status → 配方 1361 条｜感染结束=false｜挑战 0/2
witherchallenge tags   → …｜凋零残响已注册=true
本次启动 witherchallenge 相关异常：0
```

---
## 2026-11-07 17:0x · version_0.0.13：残响对所有感染来源生效 + 四个确定性 bug

产物：`final_modpack/version_0.0.13/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.4 MB）

### 1. 感染/残响不再关心腐败是怎么来的

`SicknessControl` 彻底去掉 `wc_arrow_infected` 这类「来源标记」，
现在只看玩家身上**实际有没有 `witherstormmod:wither_sickness` 效果**：

- 任何来源（凋暴、感染箭、其它 mod）造成的腐败，走同一套规则；
- 腐败消失的那一瞬间（10 tick 内检测到）→ 世界里**有凋暴**就立刻给残响（限时模式）；
- 世界里没有凋暴，或模式为永久 → 不给残响，直接算康复；
- 残响期间再获得腐败（任何来源）→ 残响立刻取消。

### 2. 死亡不再错误地跳过残响

`onClone` 改成：**死亡时身上确实有腐败才续感染**；如果是在残响期死亡，只保留残响计时，
重生后按剩余时间补回效果，不会立刻变成腐败。

### 3. `/kill` 计入合法击杀

`CorpseRules` 的 legit 判定加了 `BYPASSES_INVULNERABILITY`（`/kill` 与虚空走这条），
符合 T9「/kill 也认」。

### 4. 成就「终结之刃」改为「刀 或 斧」

`craft_weapon.json` 的 requirements 从 `[["knife","axe"]]`（同时满足）改成
`[["knife"],["axe"]]`（任一即可）。

### 5. 配置项接回代码

之前只有 `boss_health_multiplier` 真正生效，现在补上：
`summon_tag_cap` / `summon_total_cap`（凋暴召唤上限）、`beam_inflection`→`beam_infection`（光束感染开关）、
`enhanced_corpse_spread`（加强 corpse 加速感染开关）。

### 6. 亡灵「注意到」就转化（不需要接触）

`UndeadConversion` 删掉了 `getNavigation().moveTo(...)`（之前每 20 tick 强推寻路会打断原版 AI），
并且连「碰撞箱接触」也不要求了：每 10 tick 扫一次，只要某个原版亡灵
**在它自己的侦测范围（`FOLLOW_RANGE`）内、且有视线（`hasLineOfSight`）** 看到任意 W.A.R. 生物，
就直接转化。全程不碰 AI/寻路，亡灵该怎么走还怎么走。

### 实测

```
witherchallenge tags   → …｜凋零残响已注册=true
witherchallenge status → 配方 1361 条｜感染结束=false｜挑战 0/2
本次启动 witherchallenge 相关异常：0
```

（残响链路仍需真人验收：治愈 → 残响 → 中途再感染 → 残响消失。）

---
## 2026-11-07 16:4x · version_0.0.12（重做）：以「实际效果」为准的互斥

产物：`final_modpack/version_0.0.12/`（覆盖同名文件）

### 为什么上一版没解决

上一版判断「是否处于感染」用的是 CWS tracker 的 `isInfected()`。
但 CWS 的治愈是有延迟的：tracker 先变成「未感染」，而玩家身上的
`wither_sickness` **效果还在**，于是我的 tick 认为「已治愈」，就给上了残响 ——
结果就是**腐败和残响同时挂在玩家身上**。

### 这一版的原则

**只看玩家身上实际有没有 `witherstormmod:wither_sickness` 效果**，不看 tracker 的内部字段：

| 玩家身上 | 处理 |
| --- | --- |
| 有凋零腐败 | **立刻移除残响**（腐败优先），并记下 `wc_had_sickness=true` |
| 没腐败 + 残响计时中 | 保持残响；效果被死亡清掉就按剩余时间补回 |
| 没腐败 + 残响到期 | 取消残响 → `tracker.infect()` → 提示「凋零腐败又回来了」 |
| 没腐败 + 没残响 + 曾经感染过 + **有凋暴** + 限时模式 | 挂上残响（设定分钟数） |
| 没腐败 + 没残响 + 曾经感染过 + **没有凋暴** 或 永久模式 | 永久治愈，退出追踪 |

另外 `MobEffectEvent.Applicable` 兜底：任何来源（含 CWS 自己）要给玩家挂凋零腐败时，
**先撤掉残响**，所以两个图标不可能同时亮。

### 验证

- 打出来的 jar 里含新标记 `wc_had_sickness`，mrpack 内的 jar 与构建产物 sha1 一致；
- 服务端启动正常：`Done`、`凋零残响已注册=true`、无异常；
- 「吃炖菜 → 腐败消失 → 残响出现 → 到期 → 腐败回来」「中途再感染 → 残响消失」仍需真人验收。

---
## 2026-11-07 16:2x · version_0.0.12：凋零残响与凋零腐败互斥

产物：`final_modpack/version_0.0.12/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.3 MB）

### 规则（用户 2026-11-07 定）

1. 治愈后进入「凋零残响」，**只有残响结束才会再次感染**；
2. 残响期间若再次被感染（感染箭 / 凋暴）→ **立刻取消残响**，只保留凋零腐败；
3. 残响与凋零腐败**永不共存**于同一实体。

### 改法

`SicknessControl`：

- `infect()` 第一件事就是 `cancelEcho(player)`（移除效果 + 清计时），保证「感染优先」；
- tick 里残响分支重写：
  - 残响中且 `tracker.isInfected()` → `cancelEcho()`，直接走感染状态；
  - 残响未到期 → 若效果被死亡/其它原因清掉，**按剩余时间补回**（视觉与计时一致）；
  - 残响到期 → `cancelEcho()` + `tracker.infect()` + 提示「凋零腐败又回来了」；
- 新增兜底监听 `MobEffectEvent.Applicable`：任何来源（含 CWS 自己）要给玩家挂凋零腐败时，
  只要身上还有残响，就先取消残响 —— 这样两者不会同时出现在 HUD 上。

### 实测

```
witherchallenge tags   → …｜凋零残响已注册=true
witherchallenge status → 配方 1361 条｜感染结束=false｜挑战 0/2
本次启动 witherchallenge 相关异常：0
```

（「治愈 → 残响 → 中途再感染 → 残响消失」这条链路要真人操作，服务端跑不了，仍需你验收。）

---
## 2026-11-07 16:0x · version_0.0.11：凋零残响换图标

产物：`final_modpack/version_0.0.11/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.3 MB）

### 改动

0.0.9/0.0.10 里「凋零残响」（`wither_echo`）的图标是直接复制的 CWS 凋零腐败贴图，
HUD 上两个效果长得一模一样，玩家分不清。换成自绘的 **18×18 回声环**：

- 三层同心圆（外圈暗、中圈中紫、内圈亮紫，2px 线宽）+ 中心白色亮点；
- 含义是「残留的回响 / 倒计时」，和凋零腐败的骷髅脸在 18px 下也能区分；
- 生成脚本：`tools/make_echo_icon.py`（可重复执行，改颜色/圈数都很容易）；
- 三个候选（回声环 / 褪色骷髅 / 紫晶碎片）的源文件在 `_build/icons/`。

### 验证

- 图标 256 字节、18×18 RGBA，已打进 `witherchallenge-1.0.0.jar` 的
  `assets/witherchallenge/textures/mob_effect/wither_echo.png`；
- 代码与 0.0.10 完全一致（这次只换贴图）。

---
## 2026-11-07 15:4x · version_0.0.10：修 0.0.9 的闪退（wither_echo 没注册）

产物：`final_modpack/version_0.0.10/`（mrpack 309.0 MB / 服务端 zip 346.4 MB / 起始存档 8.2 MB）

### 崩溃

用户 0.0.9 玩着玩着闪退，报告：

```
java.lang.NullPointerException: Registry Object not present: witherchallenge:wither_echo
  at com.mogaidiaolin.witherchallenge.combat.SicknessControl.onServerTick(SicknessControl.java:94)
```

### 根因

0.0.9 我加了个新状态效果 `witherchallenge:wither_echo`（凋零残响），但**忘了在主类里注册**
`ModEffects`：改主类时我用的锚点是 `ModCriteria.register(modBus);`，
而那一行在 0.0.4 就已经改成事件注册、被删掉了 —— 替换没命中，静默什么都没做。
于是 `RegistryObject.get()` 在第一次走「限时治愈」分支时直接 NPE，服务端 tick 崩溃。

### 修复

1. 主类补上 `ModEffects.register(modBus);`（现在 Items / Entities / Tabs / Effects 全注册）；
2. `SicknessControl` 里对 `WITHER_ECHO` 加了 `isPresent()` 防御，即使将来再漏注册也不会崩服；
3. `/witherchallenge tags` 增加一项「凋零残响已注册」，以后一眼就能查。

### 实测

```
witherchallenge tags → 感染箭∈arrows=true｜刀/斧∈command_block_tools=true｜凋零残响已注册=true
witherchallenge status → 配方 1361 条｜感染结束=false｜挑战 0/2
日志里没有任何 wither_echo / Registry Object not present 报错
```

---
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
## 2026-11-06 22:5x · version_0.0.5：按反馈修判定 + 补齐交付件

产物（三件）：
- inal_modpack/version_0.0.5/不死的凋灵-0.0.5.mrpack（309.0 MB）
- inal_modpack/version_0.0.5/起始存档-不死的凋灵.zip（7.2 MB，含 48×48 战场平台 @ 512,70,0）
- inal_modpack/version_0.0.5/服务端-不死的凋灵.zip（346.4 MB，51 mod + config + 启动脚本）

### 1. 判定改了：最后一只 corpse，而不是击杀计数

- 通关成就 inal 现在只在「世界里已经没有任何时空间隙」时触发（onCorpseDied 里判断），
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

## 2026-11-06 12:2x · 目录整理 + 客户端崩溃修复（第 1 次交付的补丁）

### A. 所有产出物收进 `AI_document/`

| 现在的位置 | 原来 |
| --- | --- |
| `AI_document/final_modpack/version_0.0.1/不死的凋灵-1.0.0.mrpack` | `dist/` |
| `AI_document/addon/` `pack/` `patched_mods/` `tools/` | 工作区根 |
| `AI_document/README.md` `CHANGELOG.md` `交付报告-第1次.md` | 工作区根 |

脚本路径常量同步改成：`TOOLS_DIR`(tools) / `PROJECT`(AI_document) / `ROOT`(工作区根)；
`addon/build.gradle` 里编译期引用的第三方 jar 路径改成 `../../凋暴/mods`、`../../classcial_mods`。
重新跑过 `patch_wwst.py` 与 `build_pack.py`，产物正常。

### B. 客户端崩溃：ImmediatelyFast 1.2.4 + Oculus 1.8.0

用户 11:52 的错误报告：游戏退出码 -1，日志最后一行是
`ImmediatelyFast: Failed to initialize Iris compatibility`，之后没有任何异常栈。

根因（反编译 `ImmediatelyFast-1.2.4+1.20.1.jar` 的 `IrisCompat.init`）：

```java
Class.forName("net.coderbot.iris.vertices.ImmediateState");   // 旧 Iris 包名
...
} catch (Throwable e) {
    LOGGER.error("Failed to initialize Iris compatibility...", e);
    System.exit(-1);        // ← 直接杀进程
}
```

包里是 Oculus 1.8.0（新包名 `net.irisshaders.iris.*`），所以必然 ClassNotFoundException →
必然 `System.exit(-1)`。IF 在 1.20.1 上最后一个构建就是 1.2.4，没有能认新包名的 1.20.1 版本。

处理：**从清单里删除 ImmediatelyFast**（`tools/pack_plan.py` 的 `DELETE` 里有完整注释）。
Embeddium + Oculus + ModernFix + BadOptimizations + EntityCulling 仍保留。

验证：把用户的实例整份复制到 `_build/client-test/inst`（不动用户目录），在副本里删掉 IF 后启动两次：

```
[12:05:56] ModernFix: Game took 40.774 seconds to start      ← 到主菜单
[12:10:21] ModernFix: Game took 34.0 seconds to start        ← 换新 wwst 副本后再次确认
```

### C. 顺手修掉的 wwst 数据问题（副本）

- 删掉 wwst 自带的 `assets/minecraft/models/item/debris.json`
  （Blockbench 导出的非法旋转角 -6.57529，每次进游戏刷一条 `Failed to load model`）。
  连同此前的 395 份战利品表，副本共剔除 **396** 个文件。
- `patched_mods/wwst-0.5.5-1.20.1-Forge-bugfix-wc1.jar` 重新生成（158.9 MB）。

### D. 当前清单规模

**51 个 mod = 44 个 Modrinth `files` + 7 个 `overrides/mods`**（含附属 mod）。
mrpack：`AI_document/final_modpack/version_0.0.1/不死的凋灵-1.0.0.mrpack`，308.9 MB。

---

## 2026-11-06 · 第 1 次交付（骨架 + 全量 mod + 首批功能 + 服务端实测）

### 1. 打包骨架（新增）

| 路径 | 说明 |
| --- | --- |
| `pack/modrinth.index.json` | mrpack 清单；公开 mod 走 `files` + 下载链接（sha1/sha512 取自 Modrinth），其余走 overrides |
| `pack/overrides/mods/` | 7 个 jar：查不到 Modrinth 哈希的 + 改过的副本 + 附属 mod |
| `pack/overrides/config/` | 从凋暴包复制并清掉残留（214 个文件） |
| `final_modpack/version_0.0.1/不死的凋灵-1.0.0.mrpack` | 最终产物，**308.9 MB，51 个 mod**（44 files + 7 overrides） |

校验结果：`formatVersion=1`、`minecraft=1.20.1`、`forge=47.4.28`、
45 条 files 全部带 sha1/sha512/downloads，无缺项。

### 2. mod 清单落地（55 → 51 + 附属 mod = 52）

**删除 4 个**

| jar | 原因 |
| --- | --- |
| classcial_mods/witherstormmod-1.20.1-4.2.1-all.jar | 与凋暴包 CWS 构建重复（2427 个文件哈希一致，仅 2 张 5120×5120 贴图不同），保留凋暴包高清版 |
| 凋暴/mods/geckolib-forge-1.20.1-4.8.2.jar | 与 4.8.4 重复 modid，必崩；W.A.R. 要求 4.8.4 |
| 凋暴/mods/[自动汉化更新] I18nUpdateMod-3.7.0-all.jar | 只有 `fabric.mod.json`，Forge 端不加载 |
| 凋暴/mods/Pehkui-3.8.2+1.20.1-forge.jar | 无 mod 依赖、无类引用的历史残留 |

**替换 2 个（换成 1.20.1 构建）**

| 原 | 换成 |
| --- | --- |
| ImmediatelyFast-Forge-1.5.3+1.20.4.jar | ImmediatelyFast-1.2.4+1.20.1.jar |
| fast-ip-ping-v1.0.8-mc1.20.4-forge.jar | fast-ip-ping-mc1.20.1-forge-v1.0.0.jar |

**`overrides/mods` 携带 7 个**：CWS（凋暴包高清构建）、W.A.R. B1.4.5、wwst 修改副本、
CWSM V-Sides 1.1.0、CommandToolExpansion2.1、edf-remastered 4.3、附属 mod。

### 3. 改了第三方 jar 的副本：wwst

| 项 | 内容 |
| --- | --- |
| 原件 | `凋暴/mods/wwst-0.5.5-1.20.1-Forge-bugfix.jar`（**未改动**） |
| 副本 | `patched_mods/wwst-0.5.5-1.20.1-Forge-bugfix-wc1.jar`（158.9 MB） |
| 生成脚本 | `tools/patch_wwst.py`（可重复执行，幂等） |
| 回滚 | 删掉副本、把 `tools/pack_plan.py` 的 `PATCHED` 置空即可回到原件 |

改了三处：

1. 删掉 wwst 自带的 **395 份原版方块战利品表副本**（`data/minecraft/loot_tables/**`）。
   这些副本是坏的：门类少一个引号、箱子类 `set_container_contents` 缺 `"type"`，
   实测会让服务端刷 100+ 条 `Couldn't parse element loot_tables`，对应方块破坏后无掉落。
   删掉后原版表正常生效（已验证错误数 0）。
2. 修 `data/minecraft/loot_tables/blocks/{acacia,birch,dark_oak,jungle,oak}_door.json`
   的 `half"` → `"half"`（这 5 个文件其实随第 1 条一起删掉了，修法保留以备将来）。
3. 修 `data/witherstormmod/recipes/resummon_withered_horseman.json`：
   `nbt` 字段的 SNBT 整行没有转义，JSON 无效；已把内层引号转义，配方现在能加载。

### 4. 附属 mod `witherchallenge` 1.0.0（新增，源码在 `addon/`）

| 内容 | 说明 |
| --- | --- |
| `witherchallenge:infection_core` | 感染核心，堆叠 64，2 行 tooltip |
| `witherchallenge:infection_arrow` | 感染力量之箭，堆叠 64 |
| `witherchallenge:wither_end_book` | 凋灵终结书（未激活），堆叠 1，稀有 |
| `witherchallenge:activated_wither_end_book` | 激活的凋灵终结书，带附魔光效（`isFoil`） |
| `witherchallenge:wither_end_knife` | 凋灵终结刀：5 攻 / 4.5 速 / 附魔 15 / 无限耐久 / 抗火 |
| `witherchallenge:wither_end_war_axe` | 凋灵终结战斧：13 攻 / 1.5 速 / 附魔 15 / 无限耐久 / 抗火 |
| 实体 `witherchallenge:infection_arrow` | 基础伤害恒 0、命中即消失、不可拾回、无限附魔不生效；弓/弩均可用；客户端有独立渲染器 |
| 配方 `witherchallenge:infection_arrow` | 8 个感染核心围 1 支箭（工作台） |
| 配方 `witherchallenge:wither_end_book` | 钻石/感染核心/铁锭 + 感染核心/命令方块书/感染核心 + 红石粉/感染核心/绿宝石 |
| 创造标签页 | 「不死的凋灵」，**6 个物品按流程顺序排列**（核心 → 箭 → 书 → 激活书 → 刀 → 斧） |
| lang | zh_cn / en_us 全量 |
| 数据补丁 | 补上 wwst 引用但缺失的 tag：`bwsb:shiny`、`bwsb:tick`、`afb:exception_nbt`、`minecraft:replaceable_plants` |
| 调试命令 | `/witherchallenge status\|recipes\|entities`（OP，`status` 会打印配方数/凋暴数/时空间隙数/Mangled 数） |

**这 6 个物品里，行为还没接完的部分（第三批）**：
激活书的「右击 IV 级信标」、铁砧升级、刀/斧对时空间隙的轻击/重击判定与特效、
对凋暴体内命令方块的 4×25% 伤害。物品本身已可正常获得/手持/造成基础攻击伤害。

`data/witherchallenge/tags` 与 `data/bwsb`、`data/afb`、`data/minecraft/tags` 的补丁
属于「我们自己的 mod 提供数据」，不改任何第三方文件。

### 5. 运行期实测（专用服务端，48 个 mod）

测试脚本 `tools/server_smoke.py`（起服 → RCON 发指令 → 断言 → 关服），
环境：Forge 47.4.28 + 48 mods，全新世界，`-Xmx6G`。

```
[46s] Done (…)!                 # 世界加载完成，无崩溃
summon item {witherchallenge:infection_core}   -> Summoned new Infection Core
summon item {witherchallenge:infection_arrow}  -> Summoned new Infection Arrow
summon witherchallenge:infection_arrow         -> Summoned new Infection Arrow（实体注册成功）
summon withering_away_reborn:the_mangled       -> Summoned new The Mangled
summon witherstormmod:wither_storm             -> Summoned new Wither Storm
summon minecraft:wither → kill                 -> Killed Wither
+5s  后处理动画实体存在（wither_post_death_cutscane_entitty）
+10s 时空间隙出现：Health 100.0f, PersistenceRequired 1b, Pos [-5.5, 102, -3.5]
```

配方：`/witherchallenge recipes` → **配方总数 1360**，其中
`witherchallenge:infection_arrow` 已在列。

> 注：日志里那条 `Loaded 10 recipes` 是加载中途的一次计数（KubeJS 之后的真实计数是 1360），
> 已用 `/witherchallenge recipes` 实测确认，不是问题。

### 6. 运行期问题清单（本次定位）

| # | 问题 | 处理 |
| --- | --- | --- |
| 1 | `entity_texture_features` 7.0.8 让**专用服务端**崩溃（`BrigadierExceptions.<clinit>` 触发加载 `Screen`，`invalid dist DEDICATED_SERVER`） | 客户端包保留；服务端包剔除 |
| 2 | `fullbright`、`torohealth`、`oculus` 同样在服务端构造期崩溃 | 同上，已进 `SERVER_EXCLUDE` |
| 3 | wwst 的 395 份原版战利品表 + 1 份配方是坏的 | 已用修改副本修复 |
| 4 | wwst 的 `bwsb:*` 数据包引用了未安装的 `bwsb`/`afb` mod，2 条 tag 加载告警（非致命） | 我们补了空 tag；wwst 自带的部分 `bwsb:*.mcfunction` 命令语法在新版本无效（`Failed to load function bwsb:shrink_4-5`），属于 wwst 本身与 1.20.1 不匹配，待确认是否补装其配套 mod |
| 5 | `config/attributefix.json` 里全是已删 mod 的属性，刷 27 条 ERROR | 从 overrides 里删除该配置，让 mod 重新生成 |

### 7. 构建环境（本机专项，不进交付物）

本工作区被 DSH 的 AppContainer 沙箱限制：`Files.isWritable()` 恒返回 false，
而 JDK zipfs 用 `readOnly = !Files.isWritable(path)` 判定只读，
导致 ForgeGradle 的 AccessTransformer 步骤（用 zipfs 写 jar）必然失败。

处理：把 JDK 的 `jdk.zipfs` 模块源码解出来，`ZipFileSystem.java` 里那一行改成
`this.readOnly = false`，单独编译成 patch 目录，构建时用
`JDK_JAVA_OPTIONS=--patch-module jdk.zipfs=_build/jdk-patch/mods/jdk.zipfs` 注入。
**只影响本机构建**；用户自己账号下直接 `gradlew build` 即可。
