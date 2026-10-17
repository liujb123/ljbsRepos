/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{vue,js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        brand: {
          50: '#eef7f2',
          100: '#d6ebe0',
          200: '#aed7c2',
          300: '#7dbd9e',
          400: '#4f9f7a',
          500: '#2f835e',
          600: '#22684a',
          700: '#1c533c',
          800: '#184231',
          900: '#14372a'
        }
      }
    }
  },
  plugins: []
}
