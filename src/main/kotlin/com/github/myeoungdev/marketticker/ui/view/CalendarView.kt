package com.github.myeoungdev.marketticker.ui.view

import com.github.myeoungdev.marketticker.application.service.CalendarService
import com.github.myeoungdev.marketticker.application.service.LocalizationService
import com.github.myeoungdev.marketticker.domain.model.calendar.CalendarType
import com.github.myeoungdev.marketticker.domain.model.calendar.MarketCalendarEvent
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.DefaultComboBoxModel
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.JTextArea
import javax.swing.ListSelectionModel
import javax.swing.SwingConstants

class CalendarView : JPanel(BorderLayout()), Disposable {

    private val localizationService = service<LocalizationService>()
    private val calendarService = service<CalendarService>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val typeCombo = JComboBox(DefaultComboBoxModel(CalendarType.values()))
    private val refreshButton = JButton()
    private val statusLabel = JLabel()
    private val model = DefaultListModel<MarketCalendarEvent>()
    private val list = JBList(model)
    private val detailTitleLabel = JLabel()
    private val detailMetaLabel = JLabel()
    private val detailBodyArea = JTextArea()
    private var currentEvents: List<MarketCalendarEvent> = emptyList()

    init {
        border = JBUI.Borders.empty(10)
        add(buildToolbar(), BorderLayout.NORTH)
        add(buildContent(), BorderLayout.CENTER)
        add(statusLabel, BorderLayout.SOUTH)
        setupUi()
        renderDetail(null)
        loadCalendar(forceRefresh = false)
    }

    override fun dispose() {
        scope.cancel()
    }

    private fun buildToolbar(): JPanel {
        return JPanel(BorderLayout(8, 0)).apply {
            add(typeCombo, BorderLayout.CENTER)
            add(refreshButton, BorderLayout.EAST)
        }.also {
            typeCombo.addActionListener { loadCalendar(forceRefresh = false) }
            refreshButton.addActionListener { loadCalendar(forceRefresh = true) }
        }
    }

    private fun buildContent(): JPanel {
        val detailPanel = JPanel(BorderLayout(0, 6)).apply {
            border = titledPanelBorder(localizationService.text("선택 이벤트", "Selected Event"))
            add(
                JPanel().apply {
                    layout = javax.swing.BoxLayout(this, javax.swing.BoxLayout.Y_AXIS)
                    isOpaque = false
                    add(detailTitleLabel)
                    add(detailMetaLabel)
                },
                BorderLayout.NORTH
            )
            add(JBScrollPane(detailBodyArea), BorderLayout.CENTER)
            preferredSize = Dimension(0, 180)
        }

        return JPanel(BorderLayout(0, 10)).apply {
            add(
                wrapSection(
                    localizationService.text("이벤트 목록", "Event List"),
                    JBScrollPane(list)
                ),
                BorderLayout.CENTER
            )
            add(detailPanel, BorderLayout.SOUTH)
        }
    }

    private fun setupUi() {
        refreshButton.text = localizationService.text("새로고침", "Refresh")
        statusLabel.text = localizationService.text("캘린더를 불러오는 중...", "Loading calendar...")
        typeCombo.renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                val type = value as? CalendarType
                label.text = type?.let { localizationService.text(it.labelKo, it.labelEn) } ?: ""
                return label
            }
        }

        detailTitleLabel.font = detailTitleLabel.font.deriveFont(Font.BOLD, detailTitleLabel.font.size2D + 1f)
        detailMetaLabel.foreground = JBColor.GRAY
        detailBodyArea.isEditable = false
        detailBodyArea.lineWrap = true
        detailBodyArea.wrapStyleWord = true
        detailBodyArea.background = JBColor.PanelBackground
        detailBodyArea.foreground = JBColor.foreground()
        detailBodyArea.margin = java.awt.Insets(8, 8, 8, 8)

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.visibleRowCount = 8
        list.cellRenderer = EventRenderer()
        list.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                renderDetail(list.selectedValue)
            }
        }
    }

    private fun loadCalendar(forceRefresh: Boolean) {
        val type = typeCombo.selectedItem as? CalendarType ?: CalendarType.EARNINGS
        statusLabel.text = localizationService.text("캘린더를 불러오는 중...", "Loading calendar...")

        scope.launch {
            val events = calendarService.loadEvents(type, limit = 50, forceRefresh = forceRefresh)
            withContext(Dispatchers.Main) {
                currentEvents = events
                model.removeAllElements()
                events.forEach(model::addElement)
                if (events.isNotEmpty()) {
                    list.selectedIndex = 0
                } else {
                    renderDetail(null)
                }
                statusLabel.text = localizationService.text(
                    "${localizationService.text(type.labelKo, type.labelEn)} ${events.size}건",
                    "${localizationService.text(type.labelKo, type.labelEn)} ${events.size} items"
                )
            }
        }
    }

    private fun renderDetail(event: MarketCalendarEvent?) {
        if (event == null) {
            detailTitleLabel.text = localizationService.text("이벤트를 선택하세요", "Select an event")
            detailMetaLabel.text = localizationService.text(
                "좁은 공간에서 읽기 쉽도록 목록과 상세 정보를 분리했습니다.",
                "The list and detail are separated for better readability in a narrow tool window."
            )
            detailBodyArea.text = localizationService.text(
                "실적 또는 경제지표 이벤트를 선택하면 실제값, 예상치, 이전값을 한 번에 보여줍니다.",
                "Select an earnings or economic event to see actual, forecast, and previous values together."
            )
            return
        }

        detailTitleLabel.text = event.title
        detailMetaLabel.text = listOfNotNull(
            event.type.let { localizationService.text(it.labelKo, it.labelEn) },
            event.ticker?.takeIf { it.isNotBlank() },
            event.dateTime.replace('T', ' ')
        ).joinToString("  |  ")
        detailBodyArea.text = buildString {
            if (event.subtitle.isNotBlank()) {
                append(localizationService.text("분류", "Category"))
                append(": ")
                append(event.subtitle)
                append("\n\n")
            }
            append(localizationService.text("실제값", "Actual"))
            append(": ")
            append(event.actual.ifBlank { "-" })
            append("\n")
            append(localizationService.text("예상치", "Forecast"))
            append(": ")
            append(event.forecast.ifBlank { "-" })
            append("\n")
            append(localizationService.text("이전값", "Previous"))
            append(": ")
            append(event.previous.ifBlank { "-" })
            append("\n")
            append(localizationService.text("영향도", "Impact"))
            append(": ")
            append(event.impact.toImpactText())
        }
        detailBodyArea.caretPosition = 0
    }

    private fun titledPanelBorder(title: String) = javax.swing.BorderFactory.createCompoundBorder(
        javax.swing.BorderFactory.createCompoundBorder(
            javax.swing.BorderFactory.createLineBorder(JBColor(0x3A404A, 0x3A404A)),
            javax.swing.BorderFactory.createTitledBorder(title)
        ),
        JBUI.Borders.empty(8)
    )

    private fun wrapSection(title: String, component: Component): JPanel {
        return JPanel(BorderLayout()).apply {
            border = titledPanelBorder(title)
            background = JBColor.PanelBackground
            add(component, BorderLayout.CENTER)
        }
    }

    private fun Int.toImpactText(): String {
        return when (this) {
            3 -> localizationService.text("상", "High")
            2 -> localizationService.text("중", "Medium")
            1 -> localizationService.text("하", "Low")
            else -> "-"
        }
    }

    private inner class EventRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(
            list: JList<*>?,
            value: Any?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
            val event = value as? MarketCalendarEvent ?: return label
            val metaColor = if (isSelected) "#DCE8FF" else "#89909A"
            label.verticalAlignment = SwingConstants.TOP
            label.border = JBUI.Borders.empty(10, 8)
            label.text = """
                <html>
                  <div style='width:420px;'>
                    <div style='line-height:1.38; font-weight:700;'>${escapeHtml(event.title)}</div>
                    <div style='margin-top:5px; color:$metaColor;'>${escapeHtml(event.dateTime.replace('T', ' '))} · ${escapeHtml(event.ticker ?: localizationService.text(event.type.labelKo, event.type.labelEn))}</div>
                    <div style='margin-top:6px; color:$metaColor; line-height:1.35;'>${escapeHtml(buildInlineSummary(event))}</div>
                  </div>
                </html>
            """.trimIndent()
            return label
        }
    }

    private fun buildInlineSummary(event: MarketCalendarEvent): String {
        return listOf(
            "${localizationService.text("실제", "Actual")} ${event.actual.ifBlank { "-" }}",
            "${localizationService.text("예상", "Forecast")} ${event.forecast.ifBlank { "-" }}",
            "${localizationService.text("이전", "Previous")} ${event.previous.ifBlank { "-" }}"
        ).joinToString(" · ")
    }

    private fun escapeHtml(text: String): String {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
