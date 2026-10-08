import { Navigate, Route, Routes } from 'react-router'
import { RequireAuth } from './auth/RequireAuth'
import { AppLayout } from './components/AppLayout'
import { BudgetsPage } from './pages/BudgetsPage'
import { CategoriesPage } from './pages/CategoriesPage'
import { ImportPage } from './pages/ImportPage'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { TransactionsPage } from './pages/TransactionsPage'

/** Which page shows for which URL. */
function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />

      {/* Logged-in pages: checked once by RequireAuth, wrapped in the shared header/footer. */}
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route path="/" element={<TransactionsPage />} />
        <Route path="/import" element={<ImportPage />} />
        <Route path="/categories" element={<CategoriesPage />} />
        <Route path="/budgets" element={<BudgetsPage />} />
      </Route>

      {/* Unknown URL: go home (which redirects to /login if logged out). */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
