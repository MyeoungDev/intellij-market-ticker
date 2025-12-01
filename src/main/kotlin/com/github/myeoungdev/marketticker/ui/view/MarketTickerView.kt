package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchClient
import com.github.myeoungdev.marketticker.infrastructure.naver.NaverSearchItem
import com.github.myeoungdev.marketticker.infrastructure.naver.toTicker
import com.github.myeoungdev.marketticker.ui.rendener.SearchResultRenderer
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.*
import java.awt.BorderLayout
import javax.swing.DefaultListModel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener


/**
 * Some Descirption...
 *
 * @author  : 강명관
 * @since   : 2025-12-01
 */
class MarketTickerView() {

    val panel = JPanel(BorderLayout())
    private val searchField = JBTextField()
    private val listModel = DefaultListModel<NaverSearchItem>() // 리스트 데이터 모델
    private val resultList = JBList(listModel) // 실제 리스트 컴포넌트

    // 서비스 및 코루틴 스코프
    private val searchClient = NaverSearchClient()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var searchJob: Job? = null

    init {
        setupUI()
        setupListeners()
    }

    private fun setupUI() {
        // 검색창 설정
        searchField.emptyText.text = "종목명 또는 코드 검색 (예: 삼성전자, NVDA)"
        searchField.margin = JBUI.insets(5)

        // 리스트 설정
        resultList.cellRenderer = SearchResultRenderer() // (아래에서 정의)

        // 레이아웃 배치
        panel.add(searchField, BorderLayout.NORTH)
        panel.add(JBScrollPane(resultList), BorderLayout.CENTER)
    }

    private fun setupListeners() {
        // 검색창 입력 리스너 (디바운스 적용)
        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onQueryChanged()
            override fun removeUpdate(e: DocumentEvent) = onQueryChanged()
            override fun changedUpdate(e: DocumentEvent) = onQueryChanged()
        })

        // 리스트 더블 클릭 이벤트 (나중에 관심 종목 추가용)
        resultList.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    val selected = resultList.selectedValue
                    val ticker = selected.toTicker()
                    println("관심 종목 추가됨: ${ticker.name}")
                }
            }
        })
    }

    private fun onQueryChanged() {
        val query = searchField.text.trim()

        // 이전 검색 취소
        searchJob?.cancel()

        // 검색어 없거나 짧으면 리스트 클리어
        if (query.length < 2) {
            listModel.clear()
            return
        }

        // 새 검색 시작
        searchJob = scope.launch {
            delay(300) // 300ms 디바운스 (입력 대기)

            val results = try {
                searchClient.searchStocks(query)
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }

            // UI 업데이트는 반드시 EDT(Main Thread)에서!
            withContext(Dispatchers.Main) { // IntelliJ에서는 Swing Thread
                listModel.clear()
                results.forEach { listModel.addElement(it) }
            }
        }
    }
}