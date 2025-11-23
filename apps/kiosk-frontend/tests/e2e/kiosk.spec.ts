import { test, expect } from '@playwright/test'

test.describe('Kiosk User Flow', () => {
  test('should navigate from attract screen to service selection', async ({ page }) => {
    // Navigate to attract screen
    await page.goto('/')

    // Verify attract screen is displayed
    await expect(page.getByText('Автосервис самообслуживания')).toBeVisible()
    await expect(page.getByText('Нажмите в любом месте для начала')).toBeVisible()

    // Click anywhere to proceed to welcome screen
    await page.click('body')
    
    // Wait for navigation
    await page.waitForURL('/welcome', { timeout: 1000 })

    // Verify welcome screen is displayed
    await expect(page.getByText('Добро пожаловать!')).toBeVisible()
    
    // Accept terms and conditions
    const checkbox = page.locator('input[type="checkbox"]')
    await checkbox.check()
    
    // Continue button should be enabled
    const continueButton = page.getByRole('button', { name: 'Продолжить' })
    await expect(continueButton).toBeEnabled()
    
    // Click continue
    await continueButton.click()
    
    // Should navigate to services screen
    await page.waitForURL('/services')
    await expect(page.getByText('Выберите услугу')).toBeVisible()
    
    // Verify both services are displayed
    await expect(page.getByText('Толщинометрия ЛКП')).toBeVisible()
    await expect(page.getByText('Диагностика OBD-II')).toBeVisible()
  })

  test('should not allow proceeding without accepting terms', async ({ page }) => {
    await page.goto('/welcome')

    // Continue button should be disabled initially
    const continueButton = page.getByRole('button', { name: 'Продолжить' })
    await expect(continueButton).toBeDisabled()
  })

  test('should display service details correctly', async ({ page }) => {
    await page.goto('/services')

    // Check thickness measurement service
    const thicknessCard = page.locator('text=Толщинометрия ЛКП').locator('..')
    await expect(thicknessCard.getByText('от 350₽')).toBeVisible()
    await expect(thicknessCard.getByText('Выявление перекрашенных деталей')).toBeVisible()

    // Check diagnostics service
    const diagnosticsCard = page.locator('text=Диагностика OBD-II').locator('..')
    await expect(diagnosticsCard.getByText('480₽')).toBeVisible()
    await expect(diagnosticsCard.getByText('Чтение кодов ошибок (DTC)')).toBeVisible()
  })
})
