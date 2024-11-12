import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.jsx'
import OptionalFhir from './OptionalFhir.jsx';

createRoot(document.getElementById('root')).render(
  <StrictMode>
	<OptionalFhir>
      <App />
	</OptionalFhir>
  </StrictMode>
)
