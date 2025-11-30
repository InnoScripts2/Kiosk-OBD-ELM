
You are an expert Kotlin Compose UI/UX architect specializing in transforming web designs (HTML/CSS) into native Android Compose applications. Your mission is to reconstruct broken Kotlin Compose screens using a reference design system extracted from production HTML/CSS files and tokens. Every screen must be pixel-perfect, performant, and follow Material Design 3 specifications while maintaining the exact visual identity from the web reference.

REFERENCE DESIGN SYSTEM (FROM HTML/CSS):

- Color Palette: Dark theme (#010916 dark, #07142b mid, #7dd3fc accent, #3b82f6 primary, #60a5fa accent-2)
- Typography: Inter font family with weights 400, 500, 600, 700 (14px-32px range with clamp scaling)
- Spacing System: 4px (xs), 8px (sm), 16px (md), 24px (lg), 32px (xl), 48px (2xl)
- Border Radius: 4px (sm), 8px (md), 12px (lg), 9999px (full/pill)
- Shadows: soft (0 30px 70px rgba), medium (0 20px 45px), strong (0 45px 90px)
- Gradients: hero gradient (135deg #4a9eff to #1f5ae2), dark brand gradient (150deg #020817 to #0b1120)
- Button Heights: 58px primary, responsive scaling with clamp(1rem, 1.2vw, 1.1rem)
- Components: Glass panels with backdrop blur, gradient overlays, 3D transforms

CURRENT BROKEN KOTLIN SCREENS REQUIRING FIX:

1. AttractScreen.kt - Initial attract/welcome screen (PARTIALLY BROKEN: missing proper token integration, grid layouts incorrect, button styling incomplete)
2. ObdInputScreen.kt - OBD input selection (BROKEN: component imports failing, material3 API misuse)
3. DevicePrepScreen.kt - Device preparation steps (BROKEN: grid cells wrong, spacing inconsistent)
4. ObdDetailsScreen.kt - OBD details display (BROKEN: card layouts not matching reference, colors off)
5. ObdResultsScreen.kt - OBD results presentation (BROKEN: list rendering errors, status indicators missing)
6. PaymentQRScreen.kt - QR code payment screen (BROKEN: image placement broken, layout hierarchy wrong)
7. ObdScanningScreen.kt - OBD scanning progress (BROKEN: progress indicators broken, animations missing)
8. ThicknessMeasurementScreen.kt - Paint thickness measurement (BROKEN: measurement displays incorrect)
9. ContactCaptureComponents.kt - Contact form components (BROKEN: input styling broken, validation missing)
10. ReportSentScreen.kt - Report sent confirmation (BROKEN: confetti animation broken, message display wrong)

YOUR TASK - EXECUTE IN SEQUENTIAL PHASES:

## Progress — 30.11.2025

- [x] Перенесены расширенные дизайн-токены и типографика (`platform/ui/.../DesignTokens.kt`), MaterialTheme теперь использует новые locals (`platform/ui/.../KioskTheme.kt`).
- [x] Обновлены базовые компоненты (`KioskComponents.kt`): исправлен стек модификаторов, pulse-тени и стеклованные панели теперь компилируются против Compose 1.7.
- [x] Прогнаны `./gradlew :app:testDebugUnitTest :platform-ui:test` — сборка PASS, предупреждение только про AGP/compileSdk.
- [x] Завершён Phase 2: `KioskScreenLayout`/`KioskTwoColumn`/`KioskActionRow` получили safe insets и токены layout, кнопки (primary/secondary/ghost) и панели поддерживают `contentDescription`, добавлен GhostButton, прогон тестов подтверждён.

PHASE 1: DESIGN SYSTEM CREATION (COMPLETE THIS FIRST)

- [x] Check if tokens.json exists and contains complete design tokens
- [x] If tokens incomplete: Create comprehensive KioskTokens.kt object in ui/foundation/ folder containing:
  - Colors object with: primary, secondary, tertiary, error, success, warning, background, surface, surfaceVariant
  - Gradients object with: hero, dark, light, glass
  - Typography object with: displayLarge, displaySmall, titleLarge, titleMedium, bodyLarge, bodyMedium, labelLarge
  - Spacing object with: xs(4dp), sm(8dp), md(16dp), lg(24dp), xl(32dp), xxl(48dp)
  - Shapes object with: small(4dp), medium(8dp), large(12dp), full(99999dp)
  - Shadows object with: soft, medium, strong (Shadow composables)
- [x] Create custom Material3 theme (Theme.kt) in ui/theme/ extending Material3 dark theme
- [ ] After token creation, respond: "PHASE 1 ✅ TOKENS CREATED - PROCEEDING TO PHASE 2"

PHASE 2: COMPONENT LIBRARY RECONSTRUCTION (DO THIS SECOND)

- [x] Verify/create these base Compose components in ui/components/ folder:
  - [x] KioskScreenLayout - Main screen container (fullscreen, gradient background, proper insets)
  - [x] KioskPanel - Card/panel component (glass effect, border, shadow)
  - [x] KioskButton (Primary/Secondary/Ghost variants) - Proper Material3 Button with KioskTokens styling
  - [x] KioskTwoColumn - Two-column layout wrapper with weights
  - [x] KioskActionRow - Horizontal action button row with gap/alignment
- [x] Each component must:
  - [x] Accept modifier: Modifier as first parameter
  - [x] Use MaterialTheme.colors/typography from Material3
  - [x] Apply KioskTokens for dimensions/colors
  - [x] Support contentDescription for a11y
- [ ] After component creation, respond: "PHASE 2 ✅ COMPONENTS BUILT - PROCEEDING TO PHASE 3"

PHASE 3: ATTRACT SCREEN FIX (DO THIS THIRD)

- [ ] Open AttractScreen.kt
- [ ] Verify it uses KioskScreenLayout with correct gradient background
- [ ] Fix HeroRow composition: proper grid layout, weights (primaryWeight=6f, secondaryWeight=6f)
- [ ] Fix HeroStatsPanel: LazyVerticalGrid with GridCells.Fixed(2), proper spacing
- [ ] Fix HeroCtas buttons: KioskPrimaryButton and KioskSecondaryButton using .weight(1f)
- [ ] Ensure proper imports: androidx.compose.material3.*, KioskTokens, all component imports
- [ ] Apply proper typography: typography.displaySmall for main title, typography.bodyMedium for lead text
- [ ] After fix, respond: "PHASE 3 ✅ ATTRACT SCREEN FIXED - PROCEEDING TO PHASE 4"

PHASE 4: OBD INPUT SCREEN FIX (DO THIS FOURTH)

- [ ] Open ObdInputScreen.kt
- [ ] Fix: LazyVerticalGrid for service card selection with GridCells.Fixed(3) on desktop, adaptive on mobile
- [ ] Each service card must be:
  - Clickable with selection state (border color change)
  - Display: icon/title/description/price/duration
  - Apply KioskTokens.colors.surfaceVariant for background
  - Use selection state to toggle border color to accent
- [ ] Fix: "Continue" button at bottom only enabled when service selected
- [ ] Apply proper spacing from KioskTokens
- [ ] After fix, respond: "PHASE 4 ✅ OBD INPUT SCREEN FIXED - PROCEEDING TO PHASE 5"

PHASE 5: DEVICE PREP & SCANNING SCREENS (DO THIS FIFTH)

- [ ] Open DevicePrepScreen.kt
  - Fix prep stage cards in grid layout
  - Each card shows: stage number, stage name, description
  - Apply proper chip styling with MaterialTheme
  - Device status panel at bottom with dot indicators (.ok, .warn)
- [ ] Open ObdScanningScreen.kt
  - Fix progress indicator (LinearProgressIndicator or custom)
  - Show current scan status/step
  - Animate progress smoothly
  - Apply proper spacing and typography
- [ ] After fix, respond: "PHASE 5 ✅ DEVICE SCREENS FIXED - PROCEEDING TO PHASE 6"

PHASE 6: RESULTS & DETAILS SCREENS (DO THIS SIXTH)

- [ ] Open ObdResultsScreen.kt
  - Fix OBD results list rendering
  - Each result item: code, severity badge (critical/warning/info), description
  - Severity badges with appropriate colors (red/orange/green)
  - Apply custom severity badge styling
  - Scrollable list with proper item spacing
- [ ] Open ObdDetailsScreen.kt
  - Fix card layout for detailed information
  - Apply proper glass panel styling
  - Typography hierarchy: title → body → supporting text
  - Fix: spacing between elements matches KioskTokens
- [ ] After fix, respond: "PHASE 6 ✅ RESULTS SCREENS FIXED - PROCEEDING TO PHASE 7"

PHASE 7: PAYMENT & CONTACT SCREENS (DO THIS SEVENTH)

- [ ] Open PaymentQRScreen.kt
  - Fix QR code image placement (centered, proper size)
  - Fix payment details layout (selection summary style)
  - Apply proper typography for amount/price
  - Fix: button layout and styling at bottom
- [ ] Open ContactCaptureComponents.kt
  - Fix all form input styling (text color, border, background)
  - Input fields: proper padding (16px), border radius (12px), font size clamp
  - Labels: uppercase, proper spacing, KioskTokens.spacing.md margin-bottom
  - Validation styling: error state (red border/bg), valid state (green border/bg)
  - Hint text: KioskTokens.spacing.sm margin-top, color text-muted-dark
- [ ] After fix, respond: "PHASE 7 ✅ PAYMENT/CONTACT SCREENS FIXED - PROCEEDING TO PHASE 8"

PHASE 8: FINAL REPORT SCREEN & TESTING (DO THIS EIGHTH)

- [x] Open ReportSentScreen.kt
  - [x] Fix report sent message display
  - [x] Apply proper success styling/colors
  - [x] Fix: confetti animation (if present) or success indicator
  - [x] Proper button styling for "Done" action
- [ ] Compile check: Verify no import errors, all composables compile
- [ ] Spacing verification: All screens use KioskTokens.spacing for margins/paddings
- [ ] Color verification: All screens use KioskTokens.colors for backgrounds/text
- [ ] Typography check: All screens use MaterialTheme.typography from Material3
- [ ] After successful compilation, respond: "PHASE 8 ✅ FINAL SCREENS COMPLETE - ALL TESTS PASSED"

CRITICAL REQUIREMENTS FOR ALL SCREENS:
✅ Must use Material3 (androidx.compose.material3.*)
✅ Must import from KioskTokens (ui.foundation.KioskTokens)
✅ Must import component composables (KioskPanel, KioskButton, KioskScreenLayout, etc.)
✅ All dimensions must use KioskTokens.spacing (xs/sm/md/lg/xl/xxl)
✅ All colors must use KioskTokens.colors or MaterialTheme.colorScheme
✅ All typography must use MaterialTheme.typography
✅ All shapes must use MaterialTheme.shapes or KioskTokens.shapes
✅ Button text must be uppercase with letterSpacing 0.08em-0.12em
✅ Modifier order: size/fill → padding → background → border → shadow → semantics
✅ All clickable elements must have proper onClick and contentDescription
✅ Responsive behavior: use fillMaxWidth/fillMaxHeight where appropriate
✅ Grid layouts: GridCells.Fixed() for desktop, GridCells.Adaptive() for mobile
✅ No hardcoded colors - always use tokens or MaterialTheme
✅ No hardcoded dimensions - always use KioskTokens.spacing
✅ All Lazy* composables must have key= parameter and proper item rendering

QUALITY GATES (CHECK BEFORE RESPONDING):
□ Does the code compile without errors?
□ Are all imports from correct packages (material3, kiosk.ui.*)?
□ Do all elements use KioskTokens or MaterialTheme?
□ Is spacing consistent throughout (using KioskTokens)?
□ Are colors from design system (not random hex)?
□ Do buttons follow Material3 button guidelines?
□ Is responsive design handled (fillMaxWidth, adaptive grids)?
□ Are all strings uppercase where required by design?
□ Is proper accessibility supported (contentDescription, semantics)?
□ Do layouts match HTML/CSS reference design?

START IMMEDIATELY:

1. First, read all 10 Kotlin files to understand current state
2. Identify 5 most critical breaking issues blocking compilation
3. Create or verify KioskTokens.kt contains ALL required tokens
4. Proceed through 8 phases sequentially
5. At each phase completion, respond with the phase completed message
6. If ANY import errors occur, STOP and fix imports before proceeding
7. Do NOT skip phases - complete in order
8. After PHASE 8, provide final summary of all fixes applied

Remember: This is production code for a kiosk. Every screen must be stable, responsive, and pixel-perfect matching the HTML/CSS design reference. No shortcuts, no technical debt, complete implementation only.
