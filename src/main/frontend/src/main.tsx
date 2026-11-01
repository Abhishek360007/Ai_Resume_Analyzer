import React from 'react'
import ReactDOM from 'react-dom/client'
import { Provider } from 'react-redux'
import { BrowserRouter } from 'react-router-dom'
import { store } from './store'
import App from './App'
import { isTokenExpired, redirectToLogin } from './utils/tokenUtils'
import './index.css'

const accessToken = localStorage.getItem('accessToken')
if (accessToken && isTokenExpired(accessToken)) {
  redirectToLogin(true)
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <Provider store={store}>
      <BrowserRouter>
        <App />
      </BrowserRouter>
    </Provider>
  </React.StrictMode>,
)
