import { BrowserRouter as Router, Routes, Route } from 'react-router-dom'
import AttractScreen from './pages/AttractScreen'
import WelcomeScreen from './pages/WelcomeScreen'
import ServiceSelectionScreen from './pages/ServiceSelectionScreen'

function App() {
  return (
    <Router>
      <div className="min-h-screen">
        <Routes>
          <Route path="/" element={<AttractScreen />} />
          <Route path="/welcome" element={<WelcomeScreen />} />
          <Route path="/services" element={<ServiceSelectionScreen />} />
        </Routes>
      </div>
    </Router>
  )
}

export default App
