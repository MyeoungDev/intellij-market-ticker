package com.github.myeoungdev.marketticker.domain.model

/**
 * 알림 발송 동작 모드입니다.
 */
enum class AlertMode {
    ONCE,
    REPEATING;

    companion object {
        /**
         * 문자열 값을 [AlertMode]로 변환합니다.
         */
        fun of(value: String): AlertMode {
            return values().find { it.name == value } ?: REPEATING
        }
    }
}
