package com.selfservice.feature.reports

/**
 * Генератор базовых HTML стилей для отчётов.
 * Следует дизайн-системе DesignTokens и обеспечивает симметричный внешний вид.
 * 
 * Использует:
 * - 12-колоночную сетку
 * - Тёмную тему
 * - WCAG AA контрастность
 * - Мобильную адаптацию (для предпросмотра на планшетах)
 */
object HtmlStyles {
    
    /**
     * Базовые стили для всех отчётов.
     * Включают: reset, typography, grid, components, utilities.
     */
    fun generateBaseStyles(): String = """
        /* Reset и базовые стили */
        * {
            margin: 0;
            padding: 0;
            box-sizing: border-box;
        }
        
        html {
            font-size: 16px;
            -webkit-font-smoothing: antialiased;
            -moz-osx-font-smoothing: grayscale;
        }
        
        body {
            font-family: ${DesignTokens.Typography.Fonts.PRIMARY};
            font-size: ${DesignTokens.Typography.Sizes.BODY};
            line-height: ${DesignTokens.Typography.LineHeights.NORMAL};
            color: ${DesignTokens.Colors.Text.PRIMARY};
            background: ${DesignTokens.Colors.Background.PRIMARY};
            padding: ${DesignTokens.Spacing.LG};
        }
        
        /* Типографика */
        h1, h2, h3, h4, h5, h6 {
            font-weight: ${DesignTokens.Typography.Weights.BOLD};
            line-height: ${DesignTokens.Typography.LineHeights.TIGHT};
            margin-bottom: ${DesignTokens.Spacing.MD};
        }
        
        h1 {
            font-size: ${DesignTokens.Typography.Sizes.H1};
            color: ${DesignTokens.Colors.Text.PRIMARY};
        }
        
        h2 {
            font-size: ${DesignTokens.Typography.Sizes.H2};
            color: ${DesignTokens.Colors.Text.PRIMARY};
        }
        
        h3 {
            font-size: ${DesignTokens.Typography.Sizes.H3};
            color: ${DesignTokens.Colors.Text.SECONDARY};
        }
        
        p {
            margin-bottom: ${DesignTokens.Spacing.SM};
        }
        
        strong {
            font-weight: ${DesignTokens.Typography.Weights.SEMIBOLD};
        }
        
        /* Монопространенный шрифт */
        .font-mono {
            font-family: ${DesignTokens.Typography.Fonts.MONO};
        }
        
        /* 12-колоночная сетка */
        .container {
            max-width: 1200px;
            margin: 0 auto;
            padding: 0 ${DesignTokens.Spacing.Grid.Margin.HORIZONTAL};
        }
        
        .row {
            display: flex;
            flex-wrap: wrap;
            margin: 0 calc(-${DesignTokens.Spacing.Grid.GUTTER} / 2);
        }
        
        .col-1, .col-2, .col-3, .col-4, .col-5, .col-6,
        .col-7, .col-8, .col-9, .col-10, .col-11, .col-12 {
            padding: 0 calc(${DesignTokens.Spacing.Grid.GUTTER} / 2);
            margin-bottom: ${DesignTokens.Spacing.MD};
        }
        
        .col-1 { flex: 0 0 8.333%; max-width: 8.333%; }
        .col-2 { flex: 0 0 16.666%; max-width: 16.666%; }
        .col-3 { flex: 0 0 25%; max-width: 25%; }
        .col-4 { flex: 0 0 33.333%; max-width: 33.333%; }
        .col-5 { flex: 0 0 41.666%; max-width: 41.666%; }
        .col-6 { flex: 0 0 50%; max-width: 50%; }
        .col-7 { flex: 0 0 58.333%; max-width: 58.333%; }
        .col-8 { flex: 0 0 66.666%; max-width: 66.666%; }
        .col-9 { flex: 0 0 75%; max-width: 75%; }
        .col-10 { flex: 0 0 83.333%; max-width: 83.333%; }
        .col-11 { flex: 0 0 91.666%; max-width: 91.666%; }
        .col-12 { flex: 0 0 100%; max-width: 100%; }
        
        /* Карточки */
        .card {
            background: ${DesignTokens.Colors.Background.SECONDARY};
            border: 1px solid ${DesignTokens.Colors.Border.DEFAULT};
            border-radius: ${DesignTokens.BorderRadius.LG};
            padding: ${DesignTokens.Spacing.MD};
            box-shadow: ${DesignTokens.Shadows.SM};
        }
        
        .card-accent-teal {
            border-color: ${DesignTokens.Colors.Accent.TEAL};
            border-width: 2px;
        }
        
        .card-accent-amber {
            border-color: ${DesignTokens.Colors.Accent.AMBER};
            border-width: 2px;
        }
        
        /* Секции */
        .section {
            margin-bottom: ${DesignTokens.Spacing.XL};
        }
        
        .section:last-child {
            margin-bottom: 0;
        }
        
        /* Таблицы */
        table {
            width: 100%;
            border-collapse: collapse;
            margin-bottom: ${DesignTokens.Spacing.MD};
            background: ${DesignTokens.Colors.Background.SECONDARY};
            border-radius: ${DesignTokens.BorderRadius.MD};
            overflow: hidden;
        }
        
        thead {
            background: ${DesignTokens.Colors.Background.TERTIARY};
        }
        
        th, td {
            padding: ${DesignTokens.Spacing.SM};
            text-align: left;
            border-bottom: 1px solid ${DesignTokens.Colors.Border.DEFAULT};
        }
        
        th {
            font-weight: ${DesignTokens.Typography.Weights.SEMIBOLD};
            color: ${DesignTokens.Colors.Text.PRIMARY};
            text-transform: uppercase;
            font-size: ${DesignTokens.Typography.Sizes.LABEL};
            letter-spacing: 0.05em;
        }
        
        td {
            color: ${DesignTokens.Colors.Text.SECONDARY};
        }
        
        tr:last-child td {
            border-bottom: none;
        }
        
        tbody tr:hover {
            background: ${DesignTokens.Colors.Background.TERTIARY};
        }
        
        /* Статусные бейджи */
        .tag {
            display: inline-block;
            padding: ${DesignTokens.Spacing.XS} ${DesignTokens.Spacing.SM};
            border-radius: ${DesignTokens.BorderRadius.MD};
            font-size: ${DesignTokens.Typography.Sizes.LABEL};
            font-weight: ${DesignTokens.Typography.Weights.MEDIUM};
            line-height: 1;
        }
        
        .tag-ok, .tag-normal {
            background: ${DesignTokens.Colors.Status.OK};
            color: ${DesignTokens.Colors.Text.INVERSE};
        }
        
        .tag-warning {
            background: ${DesignTokens.Colors.Status.WARNING};
            color: ${DesignTokens.Colors.Text.INVERSE};
        }
        
        .tag-critical {
            background: ${DesignTokens.Colors.Status.CRITICAL};
            color: ${DesignTokens.Colors.Text.PRIMARY};
        }
        
        .tag-info {
            background: ${DesignTokens.Colors.Status.INFO};
            color: ${DesignTokens.Colors.Text.PRIMARY};
        }
        
        .tag-empty, .tag-unknown {
            background: ${DesignTokens.Colors.Status.EMPTY};
            color: ${DesignTokens.Colors.Text.SECONDARY};
        }
        
        /* KPI карточки */
        .kpi-card {
            text-align: center;
        }
        
        .kpi-value {
            font-size: ${DesignTokens.Typography.Sizes.H1};
            font-weight: ${DesignTokens.Typography.Weights.BOLD};
            font-family: ${DesignTokens.Typography.Fonts.MONO};
            margin-bottom: ${DesignTokens.Spacing.XS};
        }
        
        .kpi-label {
            font-size: ${DesignTokens.Typography.Sizes.BODY_SMALL};
            color: ${DesignTokens.Colors.Text.SECONDARY};
            text-transform: uppercase;
            letter-spacing: 0.05em;
        }
        
        /* Списки */
        ul, ol {
            margin-bottom: ${DesignTokens.Spacing.MD};
            padding-left: ${DesignTokens.Spacing.MD};
        }
        
        li {
            margin-bottom: ${DesignTokens.Spacing.XS};
            color: ${DesignTokens.Colors.Text.SECONDARY};
        }
        
        .summary {
            list-style: none;
            padding: 0;
        }
        
        .summary li {
            padding: ${DesignTokens.Spacing.XS} 0;
            border-bottom: 1px solid ${DesignTokens.Colors.Border.DEFAULT};
        }
        
        .summary li:last-child {
            border-bottom: none;
        }
        
        /* Рекомендации */
        .recommendations {
            list-style: none;
            padding: 0;
        }
        
        .recommendation {
            background: ${DesignTokens.Colors.Background.SECONDARY};
            border-left: 4px solid ${DesignTokens.Colors.Accent.TEAL};
            padding: ${DesignTokens.Spacing.MD};
            margin-bottom: ${DesignTokens.Spacing.MD};
            border-radius: ${DesignTokens.BorderRadius.MD};
        }
        
        .recommendation-header {
            display: flex;
            align-items: center;
            justify-content: space-between;
            margin-bottom: ${DesignTokens.Spacing.SM};
        }
        
        .recommendation-body {
            color: ${DesignTokens.Colors.Text.SECONDARY};
        }
        
        .priority {
            font-size: ${DesignTokens.Typography.Sizes.BODY_SMALL};
            color: ${DesignTokens.Colors.Text.MUTED};
        }
        
        /* Метаданные */
        .meta {
            font-size: ${DesignTokens.Typography.Sizes.BODY_SMALL};
            color: ${DesignTokens.Colors.Text.MUTED};
            margin-bottom: ${DesignTokens.Spacing.SM};
        }
        
        /* Утилиты */
        .text-center { text-align: center; }
        .text-right { text-align: right; }
        .text-left { text-align: left; }
        
        .mt-xs { margin-top: ${DesignTokens.Spacing.XS}; }
        .mt-sm { margin-top: ${DesignTokens.Spacing.SM}; }
        .mt-md { margin-top: ${DesignTokens.Spacing.MD}; }
        .mt-lg { margin-top: ${DesignTokens.Spacing.LG}; }
        
        .mb-xs { margin-bottom: ${DesignTokens.Spacing.XS}; }
        .mb-sm { margin-bottom: ${DesignTokens.Spacing.SM}; }
        .mb-md { margin-bottom: ${DesignTokens.Spacing.MD}; }
        .mb-lg { margin-bottom: ${DesignTokens.Spacing.LG}; }
        
        /* DEV режим бейдж */
        .dev-mode-badge {
            position: fixed;
            top: ${DesignTokens.Spacing.SM};
            right: ${DesignTokens.Spacing.SM};
            background: ${DesignTokens.Colors.Status.WARNING};
            color: ${DesignTokens.Colors.Text.INVERSE};
            padding: ${DesignTokens.Spacing.XS} ${DesignTokens.Spacing.SM};
            border-radius: ${DesignTokens.BorderRadius.MD};
            font-size: ${DesignTokens.Typography.Sizes.CAPTION};
            font-weight: ${DesignTokens.Typography.Weights.BOLD};
            text-transform: uppercase;
            z-index: 9999;
        }
        
        /* Печать */
        @media print {
            body {
                background: white;
                color: black;
            }
            
            .dev-mode-badge {
                display: none;
            }
            
            .card {
                border: 1px solid #ccc;
                box-shadow: none;
            }
            
            .section {
                page-break-inside: avoid;
            }
        }
        
        /* Адаптивность */
        @media (max-width: 768px) {
            .col-1, .col-2, .col-3, .col-4, .col-5, .col-6,
            .col-7, .col-8, .col-9, .col-10, .col-11, .col-12 {
                flex: 0 0 100%;
                max-width: 100%;
            }
            
            body {
                padding: ${DesignTokens.Spacing.SM};
            }
            
            .container {
                padding: 0 ${DesignTokens.Spacing.SM};
            }
        }
    """.trimIndent()
    
    /**
     * Экранирование HTML для безопасности.
     */
    fun escapeHtml(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}
