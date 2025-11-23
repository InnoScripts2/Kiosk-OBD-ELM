import { useState } from 'react'
import { useNavigate } from 'react-router-dom'

/**
 * Экран 2 — Приветствие и согласие
 * 
 * Краткое введение в сервис (2-3 предложения).
 * Чекбокс согласия с пользовательским соглашением.
 * Кнопка Продолжить активна только при согласии.
 */
export default function WelcomeScreen() {
  const navigate = useNavigate()
  const [agreed, setAgreed] = useState(false)

  const handleContinue = () => {
    if (agreed) {
      navigate('/services')
    }
  }

  return (
    <div className="min-h-screen bg-kiosk-bg p-8 flex items-center justify-center">
      <div className="kiosk-card max-w-4xl w-full space-y-8">
        <h1 className="text-kiosk-title text-center">Добро пожаловать!</h1>
        
        <div className="text-kiosk-body text-gray-700 space-y-4">
          <p>
            Наш терминал самообслуживания позволяет провести профессиональную диагностику
            вашего автомобиля и проверить состояние лакокрасочного покрытия.
          </p>
          <p>
            Весь процесс займёт от 3 до 5 минут. Результаты вы получите сразу после
            завершения, а подробный отчёт отправим на вашу почту или телефон.
          </p>
          <p>
            Никаких очередей, никаких звонков — только быстрая и точная диагностика.
          </p>
        </div>

        <div className="border-t-2 border-gray-200 pt-6">
          <div className="bg-gray-50 p-6 rounded-lg space-y-4">
            <h2 className="text-kiosk-heading">Пользовательское соглашение</h2>
            <div className="text-base text-gray-600 max-h-32 overflow-y-auto">
              <p>
                Используя данный терминал, вы соглашаетесь с условиями обработки персональных
                данных и условиями предоставления услуг. Мы собираем только минимально
                необходимую информацию (контактные данные) и храним её не более 30 дней.
              </p>
              <p className="mt-2">
                Полный текст соглашения доступен по адресу{' '}
                <a href="#" className="text-kiosk-primary underline">
                  example.com/terms
                </a>
              </p>
            </div>
          </div>
        </div>

        <div className="flex items-center space-x-4 bg-blue-50 p-6 rounded-lg">
          <input
            type="checkbox"
            id="agreement"
            checked={agreed}
            onChange={e => setAgreed(e.target.checked)}
            className="w-8 h-8 cursor-pointer"
          />
          <label htmlFor="agreement" className="text-kiosk-body cursor-pointer">
            Я прочитал и согласен с условиями пользовательского соглашения
          </label>
        </div>

        <div className="flex justify-center pt-4">
          <button
            onClick={handleContinue}
            disabled={!agreed}
            className={`
              kiosk-button-primary
              ${!agreed ? 'opacity-50 cursor-not-allowed' : ''}
            `}
          >
            Продолжить
          </button>
        </div>
      </div>
    </div>
  )
}
