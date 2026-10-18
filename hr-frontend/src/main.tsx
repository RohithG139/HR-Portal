import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import './index.css'
import App from './App.tsx'

// getElementById can return null; we throw early with a clear message rather
// than allowing a cryptic "Cannot read properties of null" error later.
const rootElement = document.getElementById('root')
if (rootElement === null) {
  throw new Error(
    'Mount target #root not found. Check that index.html contains <div id="root">.',
  )
}

createRoot(rootElement).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
