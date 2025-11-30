package com.selfservice.feature.reports

/**
 * Дизайн-система отчётов.
 * Единая цветовая палитра, типографика и отступы для симметричных отчётов.
 * 
 * Требования:
 * - WCAG AA контраст минимум 4.5:1 для обычного текста
 * - 12-колоночная сетка
 * - Тёмная тема (#0B0D17 фон)
 * - Акценты: #00C4B4 (толщиномер), #FFC857 (OBD-II)
 */
object DesignTokens {
    
    /**
     * Цветовая палитра.
     */
    object Colors {
        /** Фоновые цвета */
        object Background {
            const val PRIMARY = "#0B0D17"      // Основной тёмный фон
            const val SECONDARY = "#1A1D2E"    // Вторичный фон для карточек
            const val TERTIARY = "#262A3F"     // Третичный для hover состояний
        }
        
        /** Текстовые цвета */
        object Text {
            const val PRIMARY = "#F7F7F8"      // Основной текст (яркий белый)
            const val SECONDARY = "#B8B9BF"    // Вторичный текст
            const val MUTED = "#6B6C75"        // Приглушённый текст
            const val INVERSE = "#0B0D17"      // Инверсный (для светлого фона)
        }
        
        /** Акцентные цвета */
        object Accent {
            const val TEAL = "#00C4B4"         // Толщиномер (голубой/бирюзовый)
            const val AMBER = "#FFC857"        // OBD-II (янтарный)
            const val TEAL_DARK = "#009B8E"    // Тёмный оттенок для градиентов
            const val AMBER_DARK = "#E6A844"   // Тёмный янтарный
        }
        
        /** Статусные цвета */
        object Status {
            const val OK = "#10B981"           // Зелёный - всё в порядке
            const val WARNING = "#F59E0B"      // Оранжевый - предупреждение
            const val CRITICAL = "#EF4444"     // Красный - критично
            const val INFO = "#3B82F6"         // Синий - информация
            const val EMPTY = "#4B5563"        // Серый - нет данных
        }
        
        /** Границы и разделители */
        object Border {
            const val DEFAULT = "#2D3142"
            const val LIGHT = "#3D4254"
            const val ACCENT = "#00C4B4"
        }
        
        /** Градиенты */
        object Gradients {
            const val TEAL = "linear-gradient(135deg, #00C4B4 0%, #009B8E 100%)"
            const val AMBER = "linear-gradient(135deg, #FFC857 0%, #E6A844 100%)"
            const val DARK = "linear-gradient(180deg, #1A1D2E 0%, #0B0D17 100%)"
        }
    }
    
    /**
     * Типографика.
     */
    object Typography {
        /** Шрифты */
        object Fonts {
            const val PRIMARY = "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Roboto', sans-serif"
            const val MONO = "'Roboto Mono', 'Courier New', monospace"
        }
        
        /** Размеры шрифтов */
        object Sizes {
            // Заголовки
            const val H1 = "2.5rem"            // 40px
            const val H2 = "2rem"              // 32px
            const val H3 = "1.5rem"            // 24px
            const val H4 = "1.25rem"           // 20px
            const val H5 = "1.125rem"          // 18px
            
            // Тело
            const val BODY = "1rem"            // 16px
            const val BODY_LARGE = "1.125rem"  // 18px
            const val BODY_SMALL = "0.875rem"  // 14px
            
            // Специальные
            const val CAPTION = "0.75rem"      // 12px
            const val LABEL = "0.875rem"       // 14px
        }
        
        /** Вес шрифта */
        object Weights {
            const val LIGHT = 300
            const val REGULAR = 400
            const val MEDIUM = 500
            const val SEMIBOLD = 600
            const val BOLD = 700
        }
        
        /** Высота строки */
        object LineHeights {
            const val TIGHT = 1.2f
            const val NORMAL = 1.5f
            const val RELAXED = 1.75f
        }
    }
    
    /**
     * Отступы и размеры.
     */
    object Spacing {
        // Базовая единица: 8px (0.5rem)
        const val XS = "0.5rem"                // 8px
        const val SM = "1rem"                  // 16px
        const val MD = "1.5rem"                // 24px
        const val LG = "2rem"                  // 32px
        const val XL = "2.5rem"                // 40px
        const val XXL = "3rem"                 // 48px
        
        /** Сетка */
        object Grid {
            const val COLUMNS = 12
            const val GUTTER = "1.5rem"        // 24px между колонками
            
            object Margin {
                const val HORIZONTAL = "2.5rem" // 40px по бокам
                const val VERTICAL = "2rem"     // 32px сверху/снизу
            }
        }
    }
    
    /**
     * Тени.
     */
    object Shadows {
        const val SM = "0 1px 2px 0 rgba(0, 0, 0, 0.3)"
        const val MD = "0 4px 6px -1px rgba(0, 0, 0, 0.4)"
        const val LG = "0 10px 15px -3px rgba(0, 0, 0, 0.5)"
        const val XL = "0 20px 25px -5px rgba(0, 0, 0, 0.6)"
    }
    
    /**
     * Скругления углов.
     */
    object BorderRadius {
        const val NONE = "0"
        const val SM = "0.25rem"               // 4px
        const val MD = "0.5rem"                // 8px
        const val LG = "0.75rem"               // 12px
        const val XL = "1rem"                  // 16px
        const val FULL = "9999px"
    }
    
    /**
     * Размеры иконок.
     */
    object IconSizes {
        const val SM = 16                      // 16px
        const val MD = 24                      // 24px
        const val LG = 32                      // 32px
        const val XL = 48                      // 48px
    }
    
    /**
     * Доступность (WCAG AA минимум).
     */
    object Accessibility {
        const val MIN_CONTRAST_RATIO = 4.5     // WCAG AA для обычного текста
        const val MIN_CONTRAST_RATIO_LARGE = 3.0 // WCAG AA для крупного текста
        const val FOCUS_OUTLINE_WIDTH = "2px"
        const val FOCUS_OUTLINE_OFFSET = "2px"
    }
    
    /**
     * Анимации.
     */
    object Animations {
        object Durations {
            const val FAST = "150ms"
            const val NORMAL = "300ms"
            const val SLOW = "500ms"
        }
        
        object Easings {
            const val DEFAULT = "cubic-bezier(0.4, 0, 0.2, 1)"
            const val IN = "cubic-bezier(0.4, 0, 1, 1)"
            const val OUT = "cubic-bezier(0, 0, 0.2, 1)"
            const val IN_OUT = "cubic-bezier(0.4, 0, 0.2, 1)"
        }
    }
}
