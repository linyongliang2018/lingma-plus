# Lingma-Plus

增强通义灵码在 IntelliJ IDEA 中的使用体验。

## 功能

### LingmaHelper（批量解释）

- 批量解释类中的所有方法
- 自动选择方法并触发灵码解释功能（TriggerCosyExplainCodeGenerationAction）
- 支持自定义时间间隔配置

### JavaClassScanner（类扫描与批量优化）

- **开始扫描**：扫描项目中方法数超过 20 的 Java 类
- **扫描最近 Git 修改**：按配置的天数、作者获取 Git 修改过的 Java 类
- **开始优化**：多选类后批量调用灵码优化（TriggerCosyOptimizeCodeGenerationAction）
  - `@Data` 类：整类发送
  - 普通类：按方法逐个优化
- **自定义提示词提问**：对选中类/方法批量发起同一条普通问答（纯文本发送）
- **配置 / 设置**：快速打开配置或 IDE 设置

## 使用方法

### 批量解释

1. 在编辑器中打开 Java 类文件
2. 通过 Tools 菜单或右键菜单选择「LingmaHelper」
3. 插件会自动选择方法并向灵码提问

### 批量优化

1. 打开 View -> Tool Windows -> JavaClassScanner
2. 点击「扫描最近git修改」或「开始扫描」获取类列表
3. 多选需要优化的类
4. 点击「开始优化」或右键「对选中项开始优化」

## 配置

通过 Tools ->「LingmaHelper 配置」或 JavaClassScanner 中的「配置」按钮可设置：

- **时间间隔**：每次提问/优化之间的最小、最大秒数
- **Git 回溯天数**：1-1000 天
- **Git 作者过滤**：留空为当前用户
- **零码命令后缀**：为 `/optimize` 与 `/comment` 追加的自定义后缀
- **提问提示词**：用于「自定义提示词提问」的固定问句（批量发送同一条普通问答）

配置持久化到 `~/.lingma-plus/lingma-config.json`，重启后自动加载。

## 开发

基于 IntelliJ Platform 开发。

