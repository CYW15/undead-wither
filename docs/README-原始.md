# 凋亡回响 · 整合包工程

Minecraft **1.20.1 + Forge 47.4.28** 的私人整合包（A 方案：jar 全带，开箱即用）。
新增玩法由附属 mod **`witherchallenge`**（包名 `com.mogaidiaolin.witherchallenge`）实现，
不改动 CWS / W.A.R. 的原 jar。

需求以 `task/需求确认清单.docx`（含 T1–T14 与项目交接摘要）为准，
最终 mod 清单以 `task/mod冲突检查报告.docx` 第五节为准，落地配置在 `tools/pack_plan.py`。

> 本目录（`AI_document/`）是**所有产出物**的根；`凋暴/`、`classcial_mods/`、`libraries/`、
> `1.20.1-Forge_47.4.28/`、`reference_textures/`、`task/` 在工作区根，是只读材料。

## 目录结构（相对 AI_document）

| 路径 | 说明 |
| --- | --- |
| `final_modpack/version_0.0.1~0.0.4/` | **交付产物**：`凋亡回响-<版本>.mrpack`（最新 0.0.4） |
| `addon/` | 附属 mod 的 Gradle 工程（ForgeGradle 6，Java 17） |
| `pack/` | mrpack 源目录：`modrinth.index.json` + `overrides/`（mods、config） |
| `patched_mods/` | **改过的 jar 副本**（原件不动），文件名带 `-wcN` 后缀 |
| `tools/` | 打包、构建、测试脚本（见下） |
| `CHANGELOG.md` | 每个改动 / 每个 jar 副本 / 每个运行期问题的记录 |
| `README.md` | 本文件 |

工作区根（`..`）下另有：`server-test/`（专用服务端测试实例）、`_build/`（JDK17、Gradle、
沙箱补丁、日志等构建临时物），两者都可以随时删掉重建。

## 常用命令（在 AI_document 目录下执行）

```powershell
# 1) 构建附属 mod（需要 JDK 17）
$env:JAVA_HOME = "<JDK17>"
gradle -p addon build                 # 产物：addon/build/libs/witherchallenge-1.0.0.jar

# 2) 生成/刷新 mrpack 源目录 + 服务端 mods，并打进 final_modpack/version_<OUT_VERSION>/
python tools/build_pack.py
python tools/build_pack.py --no-zip   # 只要目录，不打 zip

# 3) 专用服务端冒烟测试（自动启服、RCON 发指令、验证实体/时空间隙、关服）
python tools/server_smoke.py

# 4) 生成改过的 jar 副本
python tools/patch_wwst.py            # wwst 的战利品表 / 配方 / 坏模型修复

# 5) 定位「客户端专用 mod 让专用服务端崩」的问题
python tools/bisect_driver.py 40      # 二分；结论写进 tools/pack_plan.py 的 SERVER_EXCLUDE
```

## 打包规则（mrpack）

- `files[]`：能在 Modrinth 按 sha1 查到的公开 mod，带真实下载链接与 sha1/sha512。
- `overrides/mods/`：查不到哈希的（CurseForge 独占、自制改版构建、改过的副本、附属 mod）。
- `overrides/config/`：从凋暴包复制，并剔除「mod 已删、配置残留」的 30+ 项。
- `dependencies`：`minecraft: 1.20.1`、`forge: 47.4.28`。

## 已知问题

1. **ImmediatelyFast 已从包里删除**：它的 1.20.1 最后构建（1.2.4）只认旧 Iris 包名
   `net.coderbot.iris.*`，遇到 Oculus 1.8.0 会在 `IrisCompat.init` 里直接 `System.exit(-1)`，
   游戏秒退且日志没有异常栈。详见 `CHANGELOG.md`。
2. **客户端专用 mod 会让专用服务端崩**（`invalid dist DEDICATED_SERVER`）：
   `entity_texture_features`、`fullbright`、`torohealth`、`oculus`。
   客户端包保留；服务端包按 `tools/pack_plan.py` 的 `SERVER_EXCLUDE` 剔除。LAN 直连不受影响。
3. **wwst 0.5.5 自带坏数据**（原 jar 里就是坏的）：396 份原版战利品表/模型副本、1 份配方。
   已用 `patched_mods/wwst-...-wc1.jar` 修复并记进 `CHANGELOG.md`。
4. CWS 的 `witherstormmod:tainted_hanging_sign` 缺 blockstate/model（放下是紫黑格），属 CWS 自身资源缺失，未修。
5. 本工作区（DSH 沙箱）里 `Files.isWritable()` 恒为 false，导致 JDK zipfs 只读、
   ForgeGradle 的 AccessTransformer 步骤失败。本机构建用打补丁的 `jdk.zipfs`
   （`_build/jdk-patch/`，通过 `JDK_JAVA_OPTIONS=--patch-module jdk.zipfs=...` 注入）。
   **这个补丁只属于本机构建环境，不进任何交付物**；用户自己账号下直接 `gradlew build` 即可。

## 版本

- 整合包：0.0.1 → 0.0.2 → 0.0.3 → 0.0.4 → **0.0.5（当前）**；之后只修 bug
- 附属 mod：`witherchallenge` 1.0.0
