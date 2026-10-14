package com.github.myeoungdev.marketticker.fixtures.naver

object NaverThemeFixtures {
    fun item(code: String, rate: String, updatedAt: String = "2026-10-06T16:00:00+09:00") =
        """{"code":"$code","name":"Theme $code","changeRate":"$rate","risingCount":"3","fallingCount":"2","unchangedCount":"1","updatedAt":"$updatedAt"}"""

    fun page(items: List<String> = emptyList(), hasNext: Boolean = false, cursor: String? = null) =
        """{"hasNext":$hasNext,"items":[${items.joinToString(",")}],"cursor":${cursor?.let { "\"$it\"" } ?: "null"}}"""

    val members = """[
        {"itemcode":"005930","itemname":"Samsung","sosok":"0","nowPrice":"1,200","prevChangeRate":"-2.5%","tradeVolume":"9,000"},
        {"itemcode":"042510","itemname":"Raon","sosok":"1","prevChangeRate":"+3.0"},
        {"itemcode":"000003","itemname":"Unknown","sosok":"9","nowPrice":null},
        {"itemcode":"000004","itemname":"Absent"}
    ]"""
}
