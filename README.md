# 凋亡回响（Undead Wither）

Minecraft **1.20.1 + Forge 47.4.28** 的高难度凋灵主题整合包。

把 **Cracker's Wither Storm（CWS）**、**Withering Away: Reborn（W.A.R.）**、**Wither Reincarnated（WIR）** 三套内容
组合成一条完整的挑战链：

> 凋灵死亡 → 生成「时空间隙」→ 感染箭射中 → 召唤凋暴 → 多阶段 Boss 战 → 终结武器与成就

**这是私人挑战包，不是公开发行版。** 目前只在小范围测试游玩。

---

## 这是怎么来的

一个玩过 CWS、看过 W.A.R. 与 WIR 宣传片的玩家，判断这三套内容的玩法可以互补，决定把它们组合起来。
工程实现由 AI 编程助手承担。

分工（完整版见 [`docs/鸣谢-模组作者名单.txt`](docs/鸣谢-模组作者名单.txt)）：

| 角色 | 承担 |
| --- | --- |
| **cyw12** | 需求设计、玩法设想、测试与验收 |
| **DeepSeek Harness**（AI 编程助手） | 附属 mod `witherchallenge`、全部配置、文档、打包 |

设计阶段先产出了 106 个待确认问题（`task/需求确认清单.docx`），其中 37 项是会影响实现结果的决策点，
由需求方逐条拍板，其余采用已核对的默认值。关键取舍整理成
[设计决策记录（13 条）](docs/设计决策记录_ADR.md)，每条都记录「问题 → 决策 → 理由 → 代价」。

---

## 交付物

每次发布三件套：

| 文件 | 说明 |
| --- | --- |
| `凋亡回响-<版本>.mrpack` | 客户端，Modrinth 格式（约 309 MB） |
| `服务端-凋亡回响.zip` | 专用服务端，已剔除客户端专用 mod（约 470 MB） |
| `起始存档-凋亡回响.zip` | 预置存档 |

版本清单与最新版 sha256 见 [`RELEASES.md`](RELEASES.md)。

> 本仓库只包含工程与文档，**不含整合包成品与第三方 jar**（见 `.gitignore`）。
> 这是合规要求：第三方模组的再分发受各自许可约束。

---

## 技术要点

- **51 个第三方模组**、242 个配置文件，双端交付（客户端 / 专用服务端）。
- **附属 mod `witherchallenge`**：40 个 Java 源文件，承载全部新增玩法——物品、自定义箭实体、
  感染转化表、凋灵四阶段技能组、凋暴节奏接管、成就触发器、网络包。
- **20 个 Python 自动化脚本**：mrpack 打包、服务端冒烟测试（自动启服 → RCON 校验实体与配方 → 关服）、
  崩溃模组二分定位、第三方 jar 副本生成。
- **396 份损坏数据修复**：某个第三方内容包自带的战利品表与模型副本存在缺陷，通过副本方式修复，
  原件不动。
- **变更可追溯**：约 3 万字的 `CHANGELOG.md`，记录每个改动、每个 jar 副本、每个运行期问题的定位过程。

---

## 设计原则

1. **原 jar 一律不动。** 要改第三方，先复制出副本，只在副本上改，并在 `CHANGELOG.md` 记录回滚方式。
2. **新增功能优先做在附属 mod 里**，通过事件、反射、Mixin 扩展原模组行为。
3. **改动必须可追溯。** 每个版本、每个 jar 副本、每个运行期问题都要有一条记录。
4. **影响强度的参数外置到配置**，让单人挑战与多人联机可以共用同一份包。

---

## 目录结构

| 路径 | 说明 |
| --- | --- |
| `final_modpack/version_<x.y.z>/` | 交付产物（`.mrpack`、服务端包、起始存档），不进仓库 |
| `addon/` | 附属 mod 的 Gradle 工程（ForgeGradle 6，Java 17） |
| `pack/` | mrpack 源目录：`modrinth.index.json` + `overrides/`，不进仓库 |
| `patched_mods/` | 改过的 jar 副本（原件不动），文件名带 `-wcN` 后缀 |
| `tools/` | 打包、构建、测试脚本 |
| `docs/` | 设计决策记录、鸣谢名单 |
| `CHANGELOG.md` | 变更日志 |
| `RELEASES.md` | 版本与产物清单 |

---

## 常用命令

在仓库根目录执行：

```powershell
# 1) 构建附属 mod（需要 JDK 17）
$env:JAVA_HOME = "<JDK17>"
gradle -p addon build          # 产物：addon/build/libs/witherchallenge-1.0.0.jar

# 2) 生成 mrpack 源目录与服务端 mods，并打进 final_modpack/
python tools/build_pack.py

# 3) 专用服务端冒烟测试（自动启服、RCON 发指令、验证实体与配方、关服）
python tools/server_smoke.py

# 4) 生成改过的 jar 副本
python tools/patch_wwst.py

# 5) 二分定位「客户端专用 mod 让专用服务端崩」的问题
python tools/bisect_driver.py 40
```

打包的「装什么、删什么、为什么」全部集中在 `tools/pack_plan.py`——它是打包的唯一事实来源，
`build_pack.py` 只负责按它执行。

---

## 已知问题

1. **ImmediatelyFast 已移除**：其 1.20.1 最后构建（1.2.4）只认旧 Iris 包名 `net.coderbot.iris.*`，
   遇到 Oculus 1.8.0 会在 `IrisCompat.init` 里直接 `System.exit(-1)`，游戏秒退且日志无异常栈。
   详见 `CHANGELOG.md`。
2. **客户端专用 mod 会让专用服务端崩**（`invalid dist DEDICATED_SERVER`）：
   `entity_texture_features`、`fullbright`、`torohealth`、`oculus`。
   客户端包保留，服务端包按 `tools/pack_plan.py` 的 `SERVER_EXCLUDE` 剔除。
3. **某内容包自带坏数据**：原 jar 内 395 份原版战利品表副本与 1 份配方存在缺陷，
   会刷 100+ 条 `Couldn't parse element loot_tables`，门 / 潜影盒 / 旗帜破坏后无掉落。
   已通过副本修复。
4. **CWS 资源缺失**：`witherstormmod:tainted_hanging_sign` 缺 blockstate/model（放下是紫黑格），
   属 CWS 自身问题，未修。

---

## 许可

见 [`LICENSE`](LICENSE)。整合包免费，**禁止任何形式的售卖**；
第三方模组版权归各自作者所有，完整名单见 [`docs/鸣谢-模组作者名单.txt`](docs/鸣谢-模组作者名单.txt)。
