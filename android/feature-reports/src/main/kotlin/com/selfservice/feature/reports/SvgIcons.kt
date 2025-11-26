package com.selfservice.feature.reports

/**
 * SVG компоненты для отчётов.
 * Векторные иконки и визуальные элементы, следующие дизайн-системе.
 * 
 * Все SVG inline для простоты встраивания в HTML.
 */
object SvgIcons {
    
    /**
     * Логотип киоска (placeholder - должен быть заменён реальным).
     */
    fun logo(width: Int = 120, height: Int = 40): String = """
        <svg width="$width" height="$height" viewBox="0 0 120 40" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect width="120" height="40" rx="8" fill="${DesignTokens.Colors.Accent.TEAL}"/>
          <text x="60" y="25" font-family="Arial, sans-serif" font-size="18" font-weight="bold" 
                fill="${DesignTokens.Colors.Text.PRIMARY}" text-anchor="middle">
            KIOSK
          </text>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка галочки (OK).
     */
    fun checkIcon(size: Int = 24, color: String = DesignTokens.Colors.Status.OK): String = """
        <svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="12" cy="12" r="10" fill="$color"/>
          <path d="M7 12l3 3 7-7" stroke="${DesignTokens.Colors.Text.PRIMARY}" 
                stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка предупреждения.
     */
    fun warningIcon(size: Int = 24, color: String = DesignTokens.Colors.Status.WARNING): String = """
        <svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <path d="M12 2L2 20h20L12 2z" fill="$color"/>
          <path d="M12 9v5M12 16h.01" stroke="${DesignTokens.Colors.Text.INVERSE}" 
                stroke-width="2" stroke-linecap="round"/>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка ошибки.
     */
    fun errorIcon(size: Int = 24, color: String = DesignTokens.Colors.Status.CRITICAL): String = """
        <svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="12" cy="12" r="10" fill="$color"/>
          <path d="M15 9l-6 6M9 9l6 6" stroke="${DesignTokens.Colors.Text.PRIMARY}" 
                stroke-width="2" stroke-linecap="round"/>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка информации.
     */
    fun infoIcon(size: Int = 24, color: String = DesignTokens.Colors.Status.INFO): String = """
        <svg width="$size" height="$size" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
          <circle cx="12" cy="12" r="10" fill="$color"/>
          <path d="M12 8h.01M12 12v4" stroke="${DesignTokens.Colors.Text.PRIMARY}" 
                stroke-width="2" stroke-linecap="round"/>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка толщиномера.
     */
    fun thicknessGauge(size: Int = 48): String = """
        <svg width="$size" height="$size" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="8" y="16" width="32" height="24" rx="4" 
                fill="${DesignTokens.Colors.Background.SECONDARY}" 
                stroke="${DesignTokens.Colors.Accent.TEAL}" stroke-width="2"/>
          <circle cx="24" cy="28" r="6" fill="${DesignTokens.Colors.Accent.TEAL}"/>
          <rect x="18" y="8" width="12" height="10" rx="2" 
                fill="${DesignTokens.Colors.Background.TERTIARY}" 
                stroke="${DesignTokens.Colors.Accent.TEAL}"/>
          <text x="24" y="32" font-family="monospace" font-size="6" 
                fill="${DesignTokens.Colors.Text.PRIMARY}" text-anchor="middle">123</text>
        </svg>
    """.trimIndent()
    
    /**
     * Иконка OBD-II адаптера.
     */
    fun obdAdapter(size: Int = 48): String = """
        <svg width="$size" height="$size" viewBox="0 0 48 48" fill="none" xmlns="http://www.w3.org/2000/svg">
          <rect x="12" y="8" width="24" height="32" rx="4" 
                fill="${DesignTokens.Colors.Background.SECONDARY}" 
                stroke="${DesignTokens.Colors.Accent.AMBER}" stroke-width="2"/>
          <rect x="16" y="12" width="4" height="4" rx="1" fill="${DesignTokens.Colors.Accent.AMBER}"/>
          <rect x="22" y="12" width="4" height="4" rx="1" fill="${DesignTokens.Colors.Accent.AMBER}"/>
          <rect x="28" y="12" width="4" height="4" rx="1" fill="${DesignTokens.Colors.Accent.AMBER}"/>
          <rect x="16" y="18" width="16" height="2" rx="1" fill="${DesignTokens.Colors.Text.MUTED}"/>
          <rect x="16" y="22" width="12" height="2" rx="1" fill="${DesignTokens.Colors.Text.MUTED}"/>
          <rect x="16" y="26" width="14" height="2" rx="1" fill="${DesignTokens.Colors.Text.MUTED}"/>
          <path d="M20 36v4M28 36v4" stroke="${DesignTokens.Colors.Accent.AMBER}" 
                stroke-width="2" stroke-linecap="round"/>
        </svg>
    """.trimIndent()
    
    /**
     * Тепловая карта кузова автомобиля (упрощённая версия).
     * Показывает распределение измерений по зонам.
     */
    fun carBodyHeatmap(measurements: List<Pair<String, Float>>, width: Int = 600, height: Int = 400): String {
        // Упрощённая SVG-диаграмма кузова (вид сверху)
        val zones = measurements.take(12) // Берём первые 12 зон для демонстрации
        val cellWidth = width / 4
        val cellHeight = height / 3
        
        val cells = zones.mapIndexed { index, (zoneName, value) ->
            val row = index / 4
            val col = index % 4
            val x = col * cellWidth
            val y = row * cellHeight
            val sanitizedZoneName = HtmlStyles.escapeHtml(zoneName)
            
            // Определяем цвет по значению (80-200 норма)
            val color = when {
                value < 80 -> DesignTokens.Colors.Status.WARNING
                value > 200 -> DesignTokens.Colors.Status.CRITICAL
                else -> DesignTokens.Colors.Status.OK
            }
            
            """
            <rect x="$x" y="$y" width="$cellWidth" height="$cellHeight" 
                  fill="$color" fill-opacity="0.3" 
                  stroke="${DesignTokens.Colors.Border.LIGHT}" stroke-width="2"/>
            <text x="${x + cellWidth / 2}" y="${y + cellHeight / 2 - 10}" 
                  font-family="${DesignTokens.Typography.Fonts.PRIMARY}" 
                  font-size="12" fill="${DesignTokens.Colors.Text.PRIMARY}" 
                text-anchor="middle">$sanitizedZoneName</text>
            <text x="${x + cellWidth / 2}" y="${y + cellHeight / 2 + 10}" 
                  font-family="${DesignTokens.Typography.Fonts.MONO}" 
                  font-size="14" font-weight="bold" 
                  fill="${DesignTokens.Colors.Text.PRIMARY}" 
                  text-anchor="middle">${String.format("%.1f", value)} µm</text>
            """.trimIndent()
        }.joinToString("\n")
        
        return """
            <svg width="$width" height="$height" viewBox="0 0 $width $height" 
                 xmlns="http://www.w3.org/2000/svg">
              $cells
            </svg>
        """.trimIndent()
    }
    
    /**
     * Круговая диаграмма распределения статусов DTC.
     */
    fun pieChart(
        critical: Int,
        warning: Int,
        info: Int,
        size: Int = 200
    ): String {
        val total = critical + warning + info
        if (total == 0) {
            return """
                <svg width="$size" height="$size" viewBox="0 0 $size $size" 
                     xmlns="http://www.w3.org/2000/svg">
                  <circle cx="${size / 2}" cy="${size / 2}" r="${size / 2 - 10}" 
                          fill="${DesignTokens.Colors.Background.SECONDARY}" 
                          stroke="${DesignTokens.Colors.Border.DEFAULT}" stroke-width="2"/>
                  <text x="${size / 2}" y="${size / 2}" 
                        font-family="${DesignTokens.Typography.Fonts.PRIMARY}" 
                        font-size="16" fill="${DesignTokens.Colors.Text.MUTED}" 
                        text-anchor="middle">Нет данных</text>
                </svg>
            """.trimIndent()
        }
        
        val radius = (size / 2 - 20).toFloat()
        val centerX = size / 2f
        val centerY = size / 2f
        
        // Расчёт углов для секторов
        val criticalAngle = (critical.toFloat() / total) * 360f
        val warningAngle = (warning.toFloat() / total) * 360f
        val infoAngle = (info.toFloat() / total) * 360f
        
        fun polarToCartesian(angle: Float): Pair<Float, Float> {
            val rad = (angle - 90) * Math.PI / 180
            val x = centerX + radius * Math.cos(rad).toFloat()
            val y = centerY + radius * Math.sin(rad).toFloat()
            return Pair(x, y)
        }
        
        fun createArc(startAngle: Float, endAngle: Float, color: String): String {
            val start = polarToCartesian(startAngle)
            val end = polarToCartesian(endAngle)
            val largeArc = if (endAngle - startAngle > 180) 1 else 0
            
            return """
                <path d="M $centerX $centerY L ${start.first} ${start.second} 
                         A $radius $radius 0 $largeArc 1 ${end.first} ${end.second} Z" 
                      fill="$color" stroke="${DesignTokens.Colors.Background.PRIMARY}" stroke-width="2"/>
            """.trimIndent()
        }
        
        var currentAngle = 0f
        val paths = mutableListOf<String>()
        
        if (critical > 0) {
            paths.add(createArc(currentAngle, currentAngle + criticalAngle, DesignTokens.Colors.Status.CRITICAL))
            currentAngle += criticalAngle
        }
        if (warning > 0) {
            paths.add(createArc(currentAngle, currentAngle + warningAngle, DesignTokens.Colors.Status.WARNING))
            currentAngle += warningAngle
        }
        if (info > 0) {
            paths.add(createArc(currentAngle, currentAngle + infoAngle, DesignTokens.Colors.Status.INFO))
        }
        
        return """
            <svg width="$size" height="$size" viewBox="0 0 $size $size" 
                 xmlns="http://www.w3.org/2000/svg">
              ${paths.joinToString("\n")}
            </svg>
        """.trimIndent()
    }
}
