package com.github.myeoungdev.marketticker.domain.model.etf

data class EtfSummary(
    val code: String,
    val name: String,
    val price: Double? = null,
    val changeRate: Double? = null,
    val returnRate1m: Double? = null,
    val returnRate3m: Double? = null,
    val returnRate6m: Double? = null,
    val returnRate1y: Double? = null,
    val returnRate3y: Double? = null,
    val returnRate5y: Double? = null,
    val returnRate10y: Double? = null,
    val tradingValue: Double? = null,
    val netAssets: Double? = null,
    val totalFee: Double? = null,
    val distributionYield: Double? = null,
    val category: String? = null,
    val manager: String? = null,
    val indexName: String? = null,
    val hedgeType: String? = null,
    val leverageType: String? = null,
    val referenceDate: String? = null
) {
    val id: String get() = code
}

data class EtfComponent(
    val code: String?,
    val name: String,
    val quantity: Double? = null,
    val evaluationAmount: Double? = null,
    val weight: Double? = null,
    val referenceDate: String? = null
)

data class EtfDetail(
    val summary: EtfSummary,
    val components: List<EtfComponent> = emptyList()
)

enum class EtfSort(val labelKo: String, val labelEn: String) {
    TRADING_VALUE("거래대금순", "Trading Value"),
    RETURN_RATE("수익률순", "Return"),
    CHANGE_RATE("등락률순", "Change %"),
    ASSETS("순자산순", "Net Assets"),
    NAME("이름순", "Name")
}

enum class EtfExposureFilter(val labelKo: String, val labelEn: String) {
    ALL("전체 상품", "All products"),
    REGULAR("일반", "Regular"),
    LEVERAGED("레버리지", "Leveraged"),
    INVERSE("인버스", "Inverse")
}

enum class EtfPerformancePeriod(val labelKo: String, val labelEn: String) {
    MONTH_1("1개월", "1 month"),
    MONTH_3("3개월", "3 months"),
    MONTH_6("6개월", "6 months"),
    YEAR_1("1년", "1 year"),
    YEAR_3("3년", "3 years"),
    YEAR_5("5년", "5 years"),
    YEAR_10("10년", "10 years");

    fun rate(etf: EtfSummary): Double? = when (this) {
        MONTH_1 -> etf.returnRate1m
        MONTH_3 -> etf.returnRate3m
        MONTH_6 -> etf.returnRate6m
        YEAR_1 -> etf.returnRate1y
        YEAR_3 -> etf.returnRate3y
        YEAR_5 -> etf.returnRate5y
        YEAR_10 -> etf.returnRate10y
    }
}
