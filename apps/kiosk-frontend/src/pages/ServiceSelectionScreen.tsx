/**
 * Экран 3 — Выбор услуги
 * 
 * Две карточки: Толщиномер и Диагностика OBD-II.
 * Каждая карточка содержит название, описание выгоды, цену, кнопку Выбрать.
 */
export default function ServiceSelectionScreen() {
  const services = [
    {
      id: 'thickness',
      title: 'Толщинометрия ЛКП',
      icon: '📏',
      description: 'Проверка толщины лакокрасочного покрытия в 60 точках кузова',
      benefits: [
        'Выявление перекрашенных деталей',
        'Оценка состояния ЛКП',
        'Подробный отчёт с результатами',
      ],
      price: 'от 350₽',
      path: '/thickness/setup',
    },
    {
      id: 'diagnostics',
      title: 'Диагностика OBD-II',
      icon: '🔧',
      description: 'Полная компьютерная диагностика систем автомобиля',
      benefits: [
        'Чтение кодов ошибок (DTC)',
        'Расшифровка ошибок',
        'Возможность сброса ошибок',
      ],
      price: '480₽',
      path: '/diagnostics/setup',
    },
  ]

  return (
    <div className="min-h-screen bg-kiosk-bg p-8">
      <div className="max-w-6xl mx-auto">
        <h1 className="text-kiosk-title text-center mb-12">Выберите услугу</h1>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
          {services.map(service => (
            <div
              key={service.id}
              className="kiosk-card hover:shadow-2xl transition-shadow duration-300 cursor-pointer"
            >
              <div className="text-center mb-6">
                <div className="text-7xl mb-4">{service.icon}</div>
                <h2 className="text-kiosk-heading text-kiosk-primary">{service.title}</h2>
              </div>

              <p className="text-kiosk-body text-gray-700 mb-6 text-center">
                {service.description}
              </p>

              <ul className="space-y-3 mb-8">
                {service.benefits.map((benefit, index) => (
                  <li key={index} className="flex items-start">
                    <span className="text-kiosk-success mr-3 text-2xl">✓</span>
                    <span className="text-lg text-gray-700">{benefit}</span>
                  </li>
                ))}
              </ul>

              <div className="border-t-2 border-gray-200 pt-6">
                <div className="flex items-center justify-between mb-6">
                  <span className="text-2xl font-bold text-kiosk-primary">{service.price}</span>
                  <span className="text-base text-gray-500">≈ 3-5 минут</span>
                </div>

                <button
                  onClick={() => {
                    // В реальном приложении здесь будет навигация
                    console.log(`Выбрана услуга: ${service.id}`)
                  }}
                  className="kiosk-button-primary w-full"
                >
                  Выбрать
                </button>
              </div>
            </div>
          ))}
        </div>

        <div className="mt-12 text-center">
          <button
            onClick={() => window.history.back()}
            className="kiosk-button-secondary"
          >
            Назад
          </button>
        </div>
      </div>
    </div>
  )
}
