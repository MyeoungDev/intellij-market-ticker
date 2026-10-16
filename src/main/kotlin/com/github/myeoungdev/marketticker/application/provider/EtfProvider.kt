package com.github.myeoungdev.marketticker.application.provider

import com.github.myeoungdev.marketticker.domain.model.etf.EtfDetail
import com.github.myeoungdev.marketticker.domain.model.etf.EtfSummary

interface EtfProvider {
    fun getDomesticEtfs(page: Int, pageSize: Int): List<EtfSummary>
    fun getDomesticEtfDetail(code: String): EtfDetail?
}
