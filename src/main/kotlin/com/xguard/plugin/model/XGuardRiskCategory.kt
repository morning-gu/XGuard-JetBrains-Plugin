package com.xguard.plugin.model

/**
 * XGuard 内置 29 类细粒度风险标签
 * 与 YuFeng-XGuard-Reason-0.6B 模型的 id2risk 映射对齐
 */
enum class XGuardRiskCategory(
    val id: String,
    val dimension: String,
    val category: String,
    val severity: RiskSeverity = RiskSeverity.HIGH
) {
    // Safe
    SAFE("sec", "Safe", "Safe", RiskSeverity.NONE),

    // Crimes and Illegal Activities
    PORNOGRAPHIC_CONTRABAND("pc", "Crimes and Illegal Activities", "Pornographic Contraband"),
    DRUG_CRIMES("dc", "Crimes and Illegal Activities", "Drug Crimes"),
    DANGEROUS_WEAPONS("dw", "Crimes and Illegal Activities", "Dangerous Weapons"),
    PROPERTY_INFRINGEMENT("pi", "Crimes and Illegal Activities", "Property Infringement"),
    ECONOMIC_CRIMES("ec", "Crimes and Illegal Activities", "Economic Crimes"),

    // Hate Speech
    ABUSIVE_CURSES("ac", "Hate Speech", "Abusive Curses", RiskSeverity.MEDIUM),
    DEFAMATION("def", "Hate Speech", "Defamation", RiskSeverity.MEDIUM),
    THREATS_AND_INTIMIDATION("ti", "Hate Speech", "Threats and Intimidation"),
    CYBERBULLYING("cy", "Hate Speech", "Cyberbullying", RiskSeverity.MEDIUM),

    // Physical and Mental Health
    PHYSICAL_HEALTH("ph", "Physical and Mental Health", "Physical Health", RiskSeverity.MEDIUM),
    MENTAL_HEALTH("mh", "Physical and Mental Health", "Mental Health", RiskSeverity.MEDIUM),

    // Ethics and Morality
    SOCIAL_ETHICS("se", "Ethics and Morality", "Social Ethics", RiskSeverity.LOW),
    SCIENCE_ETHICS("sci", "Ethics and Morality", "Science Ethics", RiskSeverity.LOW),

    // Data Privacy
    PERSONAL_PRIVACY("pp", "Data Privacy", "Personal Privacy"),
    COMMERCIAL_SECRET("cs", "Data Privacy", "Commercial Secret"),

    // Cybersecurity
    ACCESS_CONTROL("acc", "Cybersecurity", "Access Control"),
    MALICIOUS_CODE("mc", "Cybersecurity", "Malicious Code"),
    HACKER_ATTACK("ha", "Cybersecurity", "Hacker Attack"),
    PHYSICAL_SECURITY("ps", "Cybersecurity", "Physical Security"),

    // Extremism
    VIOLENT_TERRORIST_ACTIVITIES("ter", "Extremism", "Violent Terrorist Activities"),
    SOCIAL_DISRUPTION("sd", "Extremism", "Social Disruption"),
    EXTREMIST_IDEOLOGICAL_TRENDS("ext", "Extremism", "Extremist Ideological Trends"),

    // Inappropriate Suggestions
    FINANCE("fin", "Inappropriate Suggestions", "Finance", RiskSeverity.MEDIUM),
    MEDICINE("med", "Inappropriate Suggestions", "Medicine", RiskSeverity.MEDIUM),
    LAW("law", "Inappropriate Suggestions", "Law", RiskSeverity.MEDIUM),

    // Risks Involving Minors
    CORRUPTION_OF_MINORS("cm", "Risks Involving Minors", "Corruption of Minors"),
    MINOR_ABUSE_AND_EXPLOITATION("ma", "Risks Involving Minors", "Minor Abuse and Exploitation"),
    MINOR_DELINQUENCY("md", "Risks Involving Minors", "Minor Delinquency");

    /** 完整显示名称: "Dimension - Category" */
    val displayName: String get() = "$dimension - $category"

    companion object {
        private val idMap = entries.associateBy { it.id }

        /** 根据模型输出的 ID 查找对应的风险类别 */
        fun fromId(id: String): XGuardRiskCategory = idMap[id] ?: SAFE

        /** 根据模型输出的 "Dimension-Category" 格式查找 */
        fun fromDisplayName(name: String): XGuardRiskCategory {
            // 服务端返回格式: "Dimension-Category"（短横线无空格）
            // 枚举 displayName 格式: "Dimension - Category"（短横线带空格）
            // 统一格式后再匹配
            val normalized = name.replace(" - ", "-").replace("- ", "-").replace(" -", "-")
            return entries.find { it.displayName.replace(" - ", "-") == normalized } ?: SAFE
        }
    }
}

enum class RiskSeverity {
    NONE, LOW, MEDIUM, HIGH
}
