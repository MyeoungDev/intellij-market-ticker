package com.github.myeoungdev.marketticker.common.extenion

import java.text.DecimalFormat

/**
 * "11,234,000.50" -> 11234000.5
 * 콤마를 제거하고 Double로 변환. 실패 시 0.0 반환.
 */
fun String?.parseCommaToDouble(): Double {
    if (this.isNullOrBlank()) {
        return 0.0
    }

    val normalized = this.replace(",", "")
        .replace("%", "")
        .replace("−", "-")
        .trim()

    return normalized.toDoubleOrNull()
        ?: Regex("[-+]?\\d*\\.?\\d+").find(normalized)?.value?.toDoubleOrNull()
        ?: 0.0
}

/**
 * "11,234,000" -> 11234000L
 * 콤마를 제거하고 Long으로 변환. 실패 시 0L 반환.
 */
fun String?.parseCommaToLong(): Long {
    if (this.isNullOrBlank()) {
        return 0L
    }

    return this.replace(",", "")
        .substringBefore(".")
        .toLongOrNull() ?: 0L
}

/**
 * 11234000L -> "11,234,000"
 */
fun Long?.toCommaString(): String {
    if (this == null) {
        return "0"
    }

    return DecimalFormat("#,###").format(this)
}

/**
 * 11234000.5 -> "11,234,000.5" (소수점 있으면 표시)
 * 11234000.0 -> "11,234,000" (소수점 없으면 정수처럼)
 */
fun Double?.toCommaString(): String {
    if (this == null){
        return "0"
    }

    return DecimalFormat("#,###.##").format(this)
}
