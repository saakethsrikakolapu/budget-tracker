// Every call to the backend goes through this file.

/** Mirrors HealthController.HealthResponse in the backend. */
export type HealthResponse = {
  status: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
}

/** Mirrors AuthDtos.UserResponse in the backend. */
export type User = {
  id: number
  email: string
}

/** Mirrors AccountController.AccountResponse. */
export type Account = {
  id: number
  name: string
}

/** Mirrors CategoryController.CategoryResponse. */
export type Category = {
  id: number
  name: string
  /** False for e.g. "Payments & Transfers", which budgets and spending totals leave out. */
  countsAsSpending: boolean
  transactionCount: number
}

/** Mirrors CategoryRuleService.RuleView: "description contains pattern -> category". */
export type Rule = {
  id: number
  /** Stored uppercase with single spaces, e.g. "POSHMARK". */
  pattern: string
  categoryId: number
  categoryName: string
  /** How many of your transactions contain the pattern. */
  matchCount: number
}

/** Result of creating or editing a rule: how many transactions moved to a different category. */
export type RuleChange = {
  rule: Rule
  recategorizedCount: number
}

/** One budgeted category for one month (mirrors BudgetService.BudgetLine). */
export type BudgetLine = {
  categoryId: number
  categoryName: string
  limit: number
  /** Net spending: purchases minus refunds. */
  spent: number
  /** Negative when over budget. */
  remaining: number
  percentUsed: number
}

export type BudgetOverview = {
  month: string // "2026-09"
  budgets: BudgetLine[]
  totalBudgeted: number
  totalSpent: number
  /** Spending in categories without a budget, plus Uncategorized. */
  unbudgetedSpending: number
}

/** Mirrors ImportService.ImportResult. importBatchId is null when every row was already imported. */
export type ImportResult = {
  importBatchId: number | null
  accountId: number
  fileName: string
  importedCount: number
  skippedCount: number
}

/** Mirrors ImportService.ImportSummary: one row of import history. */
export type ImportSummary = {
  id: number
  accountId: number
  accountName: string
  fileName: string
  importedCount: number
  skippedCount: number
  /** ISO timestamp, e.g. "2026-10-07T15:09:52.123Z" */
  createdAt: string
}

/** Mirrors TransactionQueryService.TransactionItem. amount: negative = spent, positive = money in. */
export type Transaction = {
  id: number
  accountId: number
  accountName: string
  transactionDate: string // "2026-09-28"
  postedDate: string | null
  description: string
  amount: number
  bankCategory: string | null
  /** null = Uncategorized */
  categoryId: number | null
  categoryName: string | null
  /** How the category was set: from the bank's label, one of your rules, or by you. */
  categorySource: 'BANK' | 'RULE' | 'MANUAL' | null
}

/**
 * Totals over every matching transaction (not just one page), computed by the backend.
 * Spending figures leave out categories that don't count as spending (e.g. card payments).
 */
export type Totals = {
  spent: number
  refunds: number
  netSpending: number
  count: number
  /** Matching transactions left out of the spending figures. */
  excludedCount: number
}

export type TransactionPage = {
  items: Transaction[]
  page: number // 0-based
  size: number
  totalItems: number
  totalPages: number
  totals: Totals
}

export type TransactionFilter = {
  accountId?: number
  from?: string
  to?: string
  q?: string
  /** a category id, or "uncategorized" */
  category?: string
  page?: number
}

/**
 * An error response from the backend, parsed from its ProblemDetail JSON, e.g.
 * {"status":400,"detail":"Invalid request","errors":{"password":"Password must be 8 to 72 characters"}}
 */
export class ApiError extends Error {
  readonly status: number
  /** Per-field messages for forms, e.g. { password: "..." }. */
  readonly fieldErrors: Record<string, string>
  /** Per-row messages for statement imports, e.g. ["Line 7: ..."]. */
  readonly rowErrors: string[]

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}, rowErrors: string[] = []) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
    this.rowErrors = rowErrors
  }
}

const UNSAFE_METHODS = ['POST', 'PUT', 'PATCH', 'DELETE']

function readCsrfToken(): string | undefined {
  return document.cookie
    .split('; ')
    .find((cookie) => cookie.startsWith('XSRF-TOKEN='))
    ?.split('=')[1]
}

/**
 * The backend sets the XSRF-TOKEN cookie on responses. If we don't have one yet (first visit,
 * or right after logout, which clears it), make a harmless GET so the backend sends one.
 */
async function ensureCsrfToken(): Promise<string | undefined> {
  if (!readCsrfToken()) {
    await fetch('/api/auth/me')
  }
  return readCsrfToken()
}

/**
 * fetch() plus: JSON request bodies, the CSRF header on unsafe methods, and errors thrown as ApiError.
 * A FormData body (file upload) is sent as multipart/form-data instead of JSON.
 */
export async function apiFetch<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {}
  const isFormData = options.body instanceof FormData

  // For FormData, the browser sets Content-Type itself (including the multipart boundary).
  if (options.body !== undefined && !isFormData) {
    headers['Content-Type'] = 'application/json'
  }
  if (UNSAFE_METHODS.includes(method)) {
    const token = await ensureCsrfToken()
    if (token) {
      headers['X-XSRF-TOKEN'] = decodeURIComponent(token)
    }
  }

  const response = await fetch(path, {
    method,
    headers,
    body: isFormData ? (options.body as FormData) : options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(
      response.status,
      problem.detail ?? `Request failed (HTTP ${response.status})`,
      problem.errors,
      problem.rowErrors,
    )
  }
  // Some endpoints (like logout) return no body.
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await fetch('/api/health')
  // The backend answers 503 with a JSON body when the database is down, so read
  // the body for both 200 and 503. Anything else (e.g. backend not running) is an error.
  if (response.status !== 200 && response.status !== 503) {
    throw new Error(`Unexpected response: HTTP ${response.status}`)
  }
  return (await response.json()) as HealthResponse
}

/** The logged-in user, or null if nobody is logged in. */
export async function fetchCurrentUser(): Promise<User | null> {
  try {
    return await apiFetch<User>('/api/auth/me')
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null
    }
    throw error
  }
}

export function login(email: string, password: string): Promise<User> {
  return apiFetch<User>('/api/auth/login', { method: 'POST', body: { email, password } })
}

export function register(email: string, password: string): Promise<User> {
  return apiFetch<User>('/api/auth/register', { method: 'POST', body: { email, password } })
}

export function logout(): Promise<void> {
  return apiFetch<void>('/api/auth/logout', { method: 'POST' })
}

export function fetchAccounts(): Promise<Account[]> {
  return apiFetch<Account[]>('/api/accounts')
}

export function createAccount(name: string): Promise<Account> {
  return apiFetch<Account>('/api/accounts', { method: 'POST', body: { name } })
}

export function uploadStatement(accountId: number, file: File): Promise<ImportResult> {
  const form = new FormData()
  form.append('accountId', String(accountId))
  form.append('file', file)
  return apiFetch<ImportResult>('/api/imports', { method: 'POST', body: form })
}

export function fetchImports(): Promise<ImportSummary[]> {
  return apiFetch<ImportSummary[]>('/api/imports')
}

export function fetchCategories(): Promise<Category[]> {
  return apiFetch<Category[]>('/api/categories')
}

export function createCategory(name: string, countsAsSpending: boolean): Promise<Category> {
  return apiFetch<Category>('/api/categories', { method: 'POST', body: { name, countsAsSpending } })
}

export function updateCategory(id: number, name: string, countsAsSpending: boolean): Promise<Category> {
  return apiFetch<Category>(`/api/categories/${id}`, { method: 'PUT', body: { name, countsAsSpending } })
}

/** Its transactions become Uncategorized; they are not deleted. */
export function deleteCategory(id: number): Promise<void> {
  return apiFetch<void>(`/api/categories/${id}`, { method: 'DELETE' })
}

/** Most specific first, which is the order they're applied in. */
export function fetchRules(): Promise<Rule[]> {
  return apiFetch<Rule[]>('/api/rules')
}

/** Creates the rule and applies it to your existing (non-manual) transactions. */
export function createRule(pattern: string, categoryId: number): Promise<RuleChange> {
  return apiFetch<RuleChange>('/api/rules', { method: 'POST', body: { pattern, categoryId } })
}

/** Deletes the rule; its transactions fall back to the next matching rule or the bank's label. */
export function deleteRule(id: number): Promise<{ recategorizedCount: number }> {
  return apiFetch<{ recategorizedCount: number }>(`/api/rules/${id}`, { method: 'DELETE' })
}

export function fetchBudgets(month: string): Promise<BudgetOverview> {
  return apiFetch<BudgetOverview>(`/api/budgets?month=${encodeURIComponent(month)}`)
}

/**
 * Set or change a category's monthly limit. The amount is sent as the exact text typed
 * (e.g. "200.50"), so it never passes through floating point.
 */
export function setBudget(categoryId: number, monthlyLimit: string): Promise<void> {
  return apiFetch<void>(`/api/budgets/${categoryId}`, { method: 'PUT', body: { monthlyLimit } })
}

export function deleteBudget(categoryId: number): Promise<void> {
  return apiFetch<void>(`/api/budgets/${categoryId}`, { method: 'DELETE' })
}

export function fetchTransactions(filter: TransactionFilter): Promise<TransactionPage> {
  // URLSearchParams encodes values safely (spaces, &, %, ...), so search text can't break the URL.
  const params = new URLSearchParams()
  for (const [key, value] of Object.entries(filter)) {
    if (value !== undefined && value !== '') params.set(key, String(value))
  }
  return apiFetch<TransactionPage>(`/api/transactions?${params}`)
}

/** Change a transaction's category by hand (null = Uncategorized). */
export function setTransactionCategory(id: number, categoryId: number | null): Promise<Transaction> {
  return apiFetch<Transaction>(`/api/transactions/${id}/category`, { method: 'PUT', body: { categoryId } })
}

/** Months that have transactions, newest first, e.g. ["2026-10", "2026-09"]. */
export function fetchTransactionMonths(): Promise<string[]> {
  return apiFetch<string[]>('/api/transactions/months')
}

/** Undo an import: deletes it and the transactions it added. */
export function deleteImport(id: number): Promise<void> {
  return apiFetch<void>(`/api/imports/${id}`, { method: 'DELETE' })
}
