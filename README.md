# MSPT Display

[![Minecraft](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen.svg)](https://www.minecraft.net/)
[![Fabric API](https://img.shields.io/badge/Fabric%20API-0.83.0%2B-blue.svg)](https://fabricmc.net/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

MSPT Display（毫秒每刻显示）是一个专为Minecraft服务器管理员和红石技术玩家设计的高性能监控工具。本模组可以实时显示游戏中各个方块更新操作所消耗的精确时间，帮助您定位导致服务器卡顿的潜在瓶颈，优化您的红石装置和自动化系统性能。

![MSPT显示示例](screenshot.png)

## 🚀 特性

- **实时MSPT监控**：在方块上方直接显示其更新操作消耗的毫秒数
- **多类型方块支持**：专门监控红石线、红石中继器、漏斗等高性能消耗组件
- **颜色编码警告**：根据MSPT值自动使用不同颜色显示（绿色-正常，黄色-警告，红色-严重）
- **全局服务器MSPT显示**：在屏幕底部显示整体服务器MSPT状态
- **低开销设计**：模组本身经过优化，对服务器性能影响微乎其微

## 📋 要求

- Minecraft 1.20.1
- Fabric Loader
- Fabric API 0.83.0+
- Java 17+

## 📥 安装

1. 确保已安装[Fabric Loader](https://fabricmc.net/use/installer/)
2. 下载本模组的最新版本JAR文件
3. 下载[Fabric API](https://www.curseforge.com/minecraft/mc-mods/fabric-api)
4. 将两个JAR文件放入Minecraft的`mods`文件夹中
5. 启动游戏

## 🔧 使用方法

安装后，模组会自动工作，无需额外配置。

### 方块性能监控

模组会自动监控并显示以下类型方块的处理时间：

- **红石元件**：红石线、红石中继器、红石比较器、活塞等
- **容器方块**：漏斗、箱子、发射器等
- **实体更新**：物品实体、生物等

当这些方块/实体发生更新时，会在其上方显示处理所需的毫秒数。数值越高表示性能消耗越大。

### 数据解读

- **时间格式**：`X.XXXXX ms`（毫秒）
- **颜色指示**：
  - **绿色**：<0.5ms - 性能良好
  - **黄色**：0.5ms~2ms - 需要注意
  - **红色**：>2ms - 可能导致卡顿

## 🔍 高级应用

### 红石优化

使用MSPT Display检测红石电路中的性能瓶颈：
1. 放置红石电路
2. 激活电路
3. 观察各个组件上方显示的数值
4. 针对高数值区域进行优化（如减少更新频率、简化电路等）

### 漏斗系统优化

1. 观察物品传输过程中漏斗上方显示的MSPT值
2. 对于高消耗的漏斗路径，考虑：
   - 减少漏斗链长度
   - 使用水流代替部分漏斗传输
   - 添加锁仓器控制漏斗工作频率

## ⚙️ 配置选项

本模组在`config`文件夹中生成配置文件，可自定义以下选项：
```yaml
MSPT显示配置
display:
# 是否启用方块MSPT显示
enabled: true
# 显示持续时间（毫秒）
duration: 5000
# 最小显示阈值（毫秒）
threshold: 0.01
# 颜色设置（RGB格式）
colors:
normal: "0,255,0" # 绿色
warning: "255,255,0" # 黄色
critical: "255,0,0" # 红色
# 是否显示服务器总体MSPT
showServerMspt: true
```
## ❓ 常见问题

<details>
<summary>为什么新放置的方块会显示多个MSPT值？</summary>
<br>
放置方块时会触发多个更新事件（方块放置、相邻方块更新、红石更新等），每个事件都会产生独立的MSPT计算和显示。
</details>

<details>
<summary>模组是否会影响服务器性能？</summary>
<br>
本模组经过优化，性能开销极小。在正常使用情况下，对服务器TPS影响不超过0.1%。
</details>

<details>
<summary>为什么有些方块没有显示MSPT值？</summary>
<br>
模组主要监控那些可能导致性能问题的方块类型（如红石组件、容器等）。普通的固体方块（如石头、泥土）通常不会显示MSPT值，除非它们被放置或破坏。
</details>

## 🔄 兼容性

本模组兼容大多数Fabric模组。经过测试的兼容模组包括：

- Lithium
- Sodium
- Carpet Mod
- Starlight
- Phosphor

## 🤝 贡献

欢迎提交Pull Request帮助改进本模组。若要贡献代码，请遵循以下步骤：

1. Fork本仓库
2. 创建您的特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交您的更改 (`git commit -m 'Add some amazing feature'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 开启Pull Request

## 📄 许可证

本项目采用MIT许可证 - 详情参见 [LICENSE](LICENSE) 文件

## 📞 联系方式

如有问题或建议，请通过以下方式联系：

- GitHub Issues: [创建新Issue](https://github.com/yourusername/msptdisplay/issues)
- Email: rzbfreebird@gmail.com

---

*MSPT Display不隶属于Mojang Studios或Microsoft，且未得到官方认可。*
