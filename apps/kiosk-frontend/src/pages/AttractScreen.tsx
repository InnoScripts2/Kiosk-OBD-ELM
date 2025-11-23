import { useNavigate } from 'react-router-dom'
import { useEffect } from 'react'

/**
 * Экран 1 — Ожидание (Attract)
 * 
 * Показывает логотип, бренд, краткий слоган.
 * Триггер: Касание в любом месте экрана.
 * Переход на экран приветствия через 200мс после касания.
 */
export default function AttractScreen() {
  const navigate = useNavigate()

  useEffect(() => {
    // Настройка перехода при любом клике/касании
    const handleInteraction = () => {
      setTimeout(() => {
        navigate('/welcome')
      }, 200)
    }

    document.addEventListener('click', handleInteraction)
    document.addEventListener('touchstart', handleInteraction)

    return () => {
      document.removeEventListener('click', handleInteraction)
      document.removeEventListener('touchstart', handleInteraction)
    }
  }, [navigate])

  return (
    <div className="flex items-center justify-center min-h-screen bg-gradient-to-br from-kiosk-primary to-blue-700 text-white">
      <div className="text-center space-y-8 animate-pulse">
        <div className="text-6xl font-bold">🚗</div>
        <h1 className="text-kiosk-title">Автосервис самообслуживания</h1>
        <p className="text-kiosk-heading text-blue-100">
          Диагностика и проверка ЛКП без очередей
        </p>
        <p className="text-2xl text-blue-200 mt-12">
          Нажмите в любом месте для начала
        </p>
      </div>
    </div>
  )
}
