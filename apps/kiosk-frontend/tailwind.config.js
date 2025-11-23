/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'kiosk-primary': '#0066CC',
        'kiosk-secondary': '#00AA66',
        'kiosk-error': '#CC0000',
        'kiosk-warning': '#FF9900',
        'kiosk-success': '#00AA00',
        'kiosk-bg': '#F5F5F5',
        'kiosk-surface': '#FFFFFF',
      },
      fontFamily: {
        'sans': ['Inter', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        'kiosk-title': ['3rem', { lineHeight: '1.2', fontWeight: '700' }],
        'kiosk-heading': ['2rem', { lineHeight: '1.3', fontWeight: '600' }],
        'kiosk-body': ['1.25rem', { lineHeight: '1.5', fontWeight: '400' }],
        'kiosk-button': ['1.5rem', { lineHeight: '1.4', fontWeight: '600' }],
      },
      spacing: {
        'kiosk-xs': '0.5rem',
        'kiosk-sm': '1rem',
        'kiosk-md': '1.5rem',
        'kiosk-lg': '2rem',
        'kiosk-xl': '3rem',
      },
    },
  },
  plugins: [],
}
