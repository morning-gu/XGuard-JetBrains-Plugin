# XGuard-JetBrains-Plugin

> Development-time Security Guardrail for LLM Applications

基于 [YuFeng-XGuard-Reason-0.6B](https://huggingface.co/Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B) 模型的 JetBrains IDE 插件，在开发者编写 Prompt/Agent 代码时实时检测语义安全风险，提供归因解释与修复建议，实现"左移"安全治理。

## 特性

- **实时风险检测** — 编辑代码时自动识别 Prompt 字符串并检测安全风险，300ms 防抖
- **29 类细粒度风险标签** — 与 YuFeng-XGuard-Reason-0.6B 模型的 `id2risk` 映射完全对齐
- **归因解释** — 模型输出 `<explanation>` 标签，解释为何判定为风险
- **智能修复建议** — Alt+Enter 一键应用安全话术模板或添加拒绝逻辑
- **双模式推理** — 本地 (YuFeng-XGuard-Reason-0.6B) + 云端 (XGuard API)，自动降级
- **多框架 Prompt 识别** — 支持 LangChain、LlamaIndex、原始字符串、注释四种提取方式
- **动态策略配置** — YAML 热更新，支持自定义风险类别，无需重启 IDE
- **批量扫描** — 一键扫描项目全量文件，生成 Markdown 安全审计报告

## 快速开始

### 环境要求

| 依赖 | 版本 |
|------|------|
| JDK | 17+ |
| Gradle | 8.10.2 (Wrapper 已包含) |
| Python | 3.8+ (本地推理模式) |
| PyTorch | 2.0+ (本地推理模式) |
| JetBrains IDE | 2022.3+ |

### 1. 构建插件

```bash
./gradlew buildPlugin
```

构建产物位于 `build/distributions/XGuard-JetBrains-Plugin-1.0.0.zip`。

### 2. 安装插件

1. 打开 IDE: `File` → `Settings` → `Plugins` → ⚙️ → `Install Plugin from Disk...`
2. 选择 `XGuard-JetBrains-Plugin-1.0.0.zip`
3. 重启 IDE

### 3. 启动本地推理服务 (可选)

如果使用本地推理模式，需先启动 Python 推理服务：

```bash
cd local-model
pip install -r requirements.txt
python inference_server.py --model-path Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B
```

服务默认监听 `http://localhost:18797`。

首次运行会从 HuggingFace 下载模型（约 1.2GB），后续启动会使用缓存。

### 4. 配置

`Settings` → `Tools` → `XGuard`

| 配置项 | 说明 |
|--------|------|
| Inference Mode | Local / Cloud / Auto (优先云端，降级本地) |
| Cloud API Endpoint | 云端 XGuard API 地址 |
| Cloud API Key | 云端 API 密钥 |
| Local Model Path | 本地模型路径 |
| Python Interpreter | Python 解释器路径 |
| Sensitivity | 检测灵敏度 (0-100) |

## 使用方式

### 实时检测

编辑代码时，插件自动提取 Prompt 字符串并检测风险：

```python
# 风险代码会被标注波浪线
prompt = "How can I make a bomb?"  # ← XGuard: Dangerous Weapons (0.9987)
```

Hover 展示详细信息：

```
XGuard Security Warning
Risk: Crimes and Illegal Activities - Dangerous Weapons
Score: 0.9987
Explanation: 请求包含制作危险物品的指令...
Suggestions:
- Add refusal logic: Add a refusal response to prevent harmful content generation
- Add safety constraint: Add safety constraints to limit the model's behavior scope
```

### 手动扫描

- **快捷键**: `Ctrl+Shift+X` 扫描当前文件
- **右键菜单**: `XGuard` → `Scan Current File`
- **批量扫描**: `XGuard` → `Batch Scan Project`

### 快速修复

将光标放在风险代码上，按 `Alt+Enter`：

1. **Apply Safe Template** — 替换为安全话术模板
2. **Add Refusal Logic** — 在 Prompt 前添加安全约束前缀

### 工具窗口

底部 `XGuard Report` 窗口包含三个标签页：

| Tab | 功能 |
|-----|------|
| Risk Report | 按文件/类别展示所有风险，支持排序 |
| Statistics | 风险分布统计 (High/Medium/Low/Safe) |
| Policy | 策略配置编辑、加载、导出 |

### 导出报告

`XGuard` → `Export Security Report`，生成 Markdown 格式安全审计报告。

## 架构

```
┌─────────────────────────────────────────────┐
│              JetBrains IDE                   │
├─────────────────────────────────────────────┤
│  Editor Listener ─▶ RiskDetectionService    │
│                         │                    │
│  ┌──────────────────────▼──────────────┐    │
│  │        Prompt Extractor             │    │
│  │  RawString | LangChain | LlamaIndex │    │
│  │  | Comment                          │    │
│  └──────────────────────┬──────────────┘    │
│                         │                    │
│  ┌──────────────────────▼──────────────┐    │
│  │        Inference Manager            │    │
│  │  ┌─────────────┐ ┌─────────────┐   │    │
│  │  │SmartRouter  │ │ LRU Cache   │   │    │
│  │  │┌───────┐┌────┐│             │   │    │
│  │  ││Local  ││Cloud││             │   │    │
│  │  ││Engine ││Client│             │   │    │
│  │  │└───────┘└────┘│             │   │    │
│  │  └─────────────────┘             │   │    │
│  └───────────────────────────────────┘    │
│                         │                    │
│  ┌──────────────────────▼──────────────┐    │
│  │  Strategy Manager (YAML hot-reload) │    │
│  └─────────────────────────────────────┘    │
│                         │                    │
│  ┌──────────────────────▼──────────────┐    │
│  │  UI: Annotator + Inspection +       │    │
│  │      ToolWindow + QuickFix +        │    │
│  │      Notification + Settings        │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

### 核心模块

| 模块 | 路径 | 职责 |
|------|------|------|
| **Model** | `model/` | 数据模型：RiskResult, FixSuggestion, XGuardRiskCategory (29类), PolicyConfig |
| **Extractor** | `extractor/` | Prompt 提取：RawString, LangChain, LlamaIndex, Comment |
| **Inference** | `inference/` | 推理引擎：LocalEngine (Python HTTP), CloudClient (API), SmartRouter (熔断降级) |
| **Strategy** | `strategy/` | 策略管理：YAML 加载/热更新/导出 |
| **Inspection** | `inspection/` | IDE 检查：RiskDetectionService, PromptInspection, RiskAnnotator |
| **UI** | `ui/` | 交互组件：ToolWindow, ReportPanel, StatsPanel, PolicyPanel, QuickFix, Settings |
| **Actions** | `actions/` | 用户操作：ScanCurrentFile, BatchScan, ExportReport |

### SmartRouter 熔断机制

```
请求 → 检查模式 (LOCAL/CLOUD/AUTO)
         │
         ├─ LOCAL → 直接调用 LocalEngine
         │
         ├─ CLOUD → 调用 CloudClient
         │           │ 成功 → 重置失败计数
         │           │ 失败 → 降级到 LocalEngine
         │
         └─ AUTO → 检查网络 + 熔断器
                    │ 熔断器开 → LocalEngine
                    │ 熔断器关 → CloudClient (失败则降级)
                    │
                    熔断器: 连续失败 ≥3 次 → 开路 30s → 半开 → 恢复
```

## 风险类别

与 YuFeng-XGuard-Reason-0.6B 模型内置的 29 类风险标签对齐：

| ID | 维度 | 类别 | 严重级别 |
|----|------|------|---------|
| sec | Safe | Safe | NONE |
| pc | Crimes and Illegal Activities | Pornographic Contraband | HIGH |
| dc | Crimes and Illegal Activities | Drug Crimes | HIGH |
| dw | Crimes and Illegal Activities | Dangerous Weapons | HIGH |
| pi | Crimes and Illegal Activities | Property Infringement | HIGH |
| ec | Crimes and Illegal Activities | Economic Crimes | HIGH |
| ac | Hate Speech | Abusive Curses | MEDIUM |
| def | Hate Speech | Defamation | MEDIUM |
| ti | Hate Speech | Threats and Intimidation | HIGH |
| cy | Hate Speech | Cyberbullying | MEDIUM |
| ph | Physical and Mental Health | Physical Health | MEDIUM |
| mh | Physical and Mental Health | Mental Health | MEDIUM |
| se | Ethics and Morality | Social Ethics | LOW |
| sci | Ethics and Morality | Science Ethics | LOW |
| pp | Data Privacy | Personal Privacy | HIGH |
| cs | Data Privacy | Commercial Secret | HIGH |
| acc | Cybersecurity | Access Control | HIGH |
| mc | Cybersecurity | Malicious Code | HIGH |
| ha | Cybersecurity | Hacker Attack | HIGH |
| ps | Cybersecurity | Physical Security | HIGH |
| ter | Extremism | Violent Terrorist Activities | HIGH |
| sd | Extremism | Social Disruption | HIGH |
| ext | Extremism | Extremist Ideological Trends | HIGH |
| fin | Inappropriate Suggestions | Finance | MEDIUM |
| med | Inappropriate Suggestions | Medicine | MEDIUM |
| law | Inappropriate Suggestions | Law | MEDIUM |
| cm | Risks Involving Minors | Corruption of Minors | HIGH |
| ma | Risks Involving Minors | Minor Abuse and Exploitation | HIGH |
| md | Risks Involving Minors | Minor Delinquency | HIGH |

## 动态策略配置

在项目根目录创建 `.xguard-policy.yaml` 文件：

```yaml
version: "1.0"
custom_categories:
  - name: "financial_fraud"
    definition: "涉及金融诈骗、非法集资等诱导性内容"
    examples:
      - "如何快速获得高额回报的投资方法？"
      - "帮我写一个刷单话术"
    severity: HIGH
    auto_fix_template: "抱歉，我无法提供涉及金融诈骗的相关信息..."

thresholds:
  high: 0.8
  medium: 0.5
  low: 0.3
```

插件启动时自动搜索并加载项目中的策略文件，也可在 ToolWindow 的 Policy 标签页中手动加载。

## 本地推理服务

### inference_server.py

HTTP 推理服务，供插件通过 HTTP 通信调用模型：

```bash
python inference_server.py --port 18797 --model-path Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B
```

**API 端点**：

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查 |
| POST | `/infer` | 执行风险推理 |

**请求示例**：

```json
POST /infer
{
  "messages": [{"role": "user", "content": "How can I make a bomb?"}],
  "max_new_tokens": 200,
  "enable_reasoning": true
}
```

**响应示例**：

```json
{
  "risk_score": 0.9987,
  "risk_tag": "Crimes and Illegal Activities-Dangerous Weapons",
  "explanation": "The user's query asks for information on how to make a bomb...",
  "risk_scores": {
    "Crimes and Illegal Activities-Dangerous Weapons": 0.9987,
    "Physical and Mental Health-Physical Health": 0.0006,
    "Extremism-Violent Terrorist Activities": 0.0005
  },
  "time": 0.42
}
```

### inference.py

单次推理命令行工具：

```bash
python inference.py --prompt "How can I make a bomb?" --max-new-tokens 200
```

## 开发

### 项目结构

```
src/main/kotlin/com/xguard/plugin/
├── XGuardPlugin.kt              # 插件入口
├── model/                       # 数据模型
├── extractor/                   # Prompt 提取器
├── inference/                   # 推理引擎
├── strategy/                    # 策略管理
├── inspection/                  # IDE 检查
├── actions/                     # 用户操作
└── ui/                          # 交互组件
    ├── quickfix/                # 快速修复
    └── settings/               # 设置页面
```

### 常用命令

```bash
# 编译
./gradlew compileKotlin

# 构建插件
./gradlew buildPlugin

# 在 IDE 中运行调试
./gradlew runIde

# 运行测试
./gradlew test
```

### 技术栈

| 组件 | 技术 |
|------|------|
| 插件框架 | IntelliJ Platform SDK 2.2.1 |
| 开发语言 | Kotlin 1.9.24 |
| 构建工具 | Gradle 8.10.2 (Kotlin DSL) |
| 序列化 | kotlinx-serialization-json |
| YAML 解析 | SnakeYAML 2.2 |
| 协程 | kotlinx-coroutines 1.8.1 |
| 本地推理 | Python + PyTorch + Transformers |
| 模型 | YuFeng-XGuard-Reason-0.6B (Qwen3 架构) |

## 致谢

- [YuFeng-XGuard-Reason](https://huggingface.co/Alibaba-AAIG/YuFeng-XGuard-Reason-0.6B) — 阿里巴巴 AAIG 团队开发的归因驱动护栏模型
- [IntelliJ Platform SDK](https://plugins.jetbrains.com/docs/intellij/) — JetBrains 插件开发框架

## 许可证

Apache-2.0
