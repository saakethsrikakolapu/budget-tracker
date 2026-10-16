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
  /** False until the user clicks the link in the verification email (or resets their password). */
  emailVerified: boolean
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

/** Mirrors DashboardService.Dashboard: everything the dashboard shows for one month. */
export type Dashboard = {
  month: string
  totals: Totals
  /** Biggest first; categoryId null = Uncategorized. Net spending (purchases minus refunds). */
  spendingByCategory: { categoryId: number | null; categoryName: string; amount: number }[]
  /** Six months ending with the selected one, oldest first. */
  monthlyTrend: { month: string; netSpending: number }[]
}

/** Mirrors ImportService.ImportResult. importBatchId is null when every row was already imported. */
export type ImportResult = {
  importBatchId: number | null
  accountId: number
  fileName: string
  importedCount: number
  skippedCount: number
  /** New transactions nothing could categorize automatically. */
  uncategorizedCount: number
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
  /** How the category was set: from the bank's label, one of your rules, the built-in merchant list, or by you. */
  /** SUGGESTED = guessed by the machine-learning model; shown with a "Suggested" badge. */
  categorySource: 'BANK' | 'RULE' | 'KEYWORD' | 'SUGGESTED' | 'MANUAL' | null
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

/** Emails a reset link if the address has an account. Same response either way, by design. */
export function forgotPassword(email: string): Promise<void> {
  return apiFetch<void>('/api/auth/forgot-password', { method: 'POST', body: { email } })
}

/** Sets a new password from an emailed link; afterwards every device is logged out. */
export function resetPassword(token: string, password: string): Promise<void> {
  return apiFetch<void>('/api/auth/reset-password', { method: 'POST', body: { token, password } })
}

export function verifyEmail(token: string): Promise<void> {
  return apiFetch<void>('/api/auth/verify-email', { method: 'POST', body: { token } })
}

export function resendVerification(): Promise<void> {
  return apiFetch<void>('/api/auth/resend-verification', { method: 'POST' })
}

/** Permanently deletes your account and all your data. Asks for the password again to confirm. */
export function deleteAccount(password: string): Promise<void> {
  return apiFetch<void>('/api/auth/account', { method: 'DELETE', body: { password } })
}

export function fetchAccounts(): Promise<Account[]> {
  return apiFetch<Account[]>('/api/accounts')
}

export function createAccount(name: string): Promise<Account> {
  return apiFetch<Account>('/api/accounts', { method: 'POST', body: { name } })
}

/**
 * Which column holds what (0-based positions). Mirrors the backend's ColumnMapping.
 * SIGNED: one amount column. DEBIT_CREDIT: separate money-out and money-in columns.
 * UNSIGNED_WITH_TYPE: positive amounts plus a column saying "Debit"/"Credit".
 */
export type ColumnMapping = {
  dateColumn: number
  postedDateColumn: number | null
  descriptionColumn: number
  categoryColumn: number | null
  amountStyle: 'SIGNED' | 'DEBIT_CREDIT' | 'UNSIGNED_WITH_TYPE'
  amountColumn: number | null
  debitColumn: number | null
  creditColumn: number | null
  typeColumn: number | null
  /** SIGNED only: purchases appear as positive numbers in this file. */
  positiveIsSpending: boolean
  /** One of DATE_FORMATS' keys, e.g. "M/d/uuuu". */
  dateFormat: string
}

/** Mirrors ImportService.Preview: how the file would be read, before anything is saved. */
export type ImportPreview = {
  /** CUSTOM = your column choices, SAVED = a format you saved, DETECTED = worked out automatically. */
  source: 'CUSTOM' | 'SAVED' | 'DETECTED' | null
  /** null when the columns couldn't be worked out. */
  mapping: ColumnMapping | null
  columns: { index: number; name: string; samples: string[] }[]
  /** The first few transactions, as they'd be imported (amount: negative = spent). */
  transactions: {
    transactionDate: string
    postedDate: string | null
    description: string
    amount: number
    bankCategory: string | null
  }[]
  transactionCount: number
  skippedRows: number
  warnings: string[]
  errors: string[]
  /** Date range of the file's transactions (null if nothing could be read). */
  firstDate: string | null
  lastDate: string | null
  /** Rows already in the chosen account, which an import would skip (null if no account was given). */
  alreadyImported: number | null
}

/** Read a file without importing it. Pass a mapping to try your own column choices. */
export function previewStatement(file: File, mapping?: ColumnMapping, accountId?: number): Promise<ImportPreview> {
  const form = new FormData()
  form.append('file', file)
  if (mapping) form.append('mapping', JSON.stringify(mapping))
  if (accountId !== undefined) form.append('accountId', String(accountId))
  return apiFetch<ImportPreview>('/api/imports/preview', { method: 'POST', body: form })
}

export function uploadStatement(
  accountId: number,
  file: File,
  options: { mapping?: ColumnMapping; rememberFormat?: boolean } = {},
): Promise<ImportResult> {
  const form = new FormData()
  form.append('accountId', String(accountId))
  form.append('file', file)
  if (options.mapping) form.append('mapping', JSON.stringify(options.mapping))
  form.append('rememberFormat', String(options.rememberFormat ?? false))
  return apiFetch<ImportResult>('/api/imports', { method: 'POST', body: form })
}

export function fetchImports(): Promise<ImportSummary[]> {
  return apiFetch<ImportSummary[]>('/api/imports')
}

export function fetchCategories(): Promise<Category[]> {
  return apiFetch<Category[]>('/api/categories')
}

/** Most specific first, which is the order they're applied in. */
export function fetchRules(): Promise<Rule[]> {
  return apiFetch<Rule[]>('/api/rules')
}

/** Creates the rule and applies it to your existing (non-manual) transactions. */
export function createRule(pattern: string, categoryId: number): Promise<RuleChange> {
  return apiFetch<RuleChange>('/api/rules', { method: 'POST', body: { pattern, categoryId } })
}

/** The merchant text to offer for "always put this merchant in ...", and how many others it matches. */
export function fetchRuleSuggestion(transactionId: number): Promise<{ pattern: string; otherMatches: number }> {
  return apiFetch(`/api/rules/suggestion?transactionId=${transactionId}`)
}

/** Points an existing rule at a different category (and re-sorts its transactions). */
export function updateRule(id: number, pattern: string, categoryId: number): Promise<RuleChange> {
  return apiFetch<RuleChange>(`/api/rules/${id}`, { method: 'PUT', body: { pattern, categoryId } })
}

/** Deletes the rule; its transactions fall back to the next matching rule or the bank's label. */
export function deleteRule(id: number): Promise<{ recategorizedCount: number }> {
  return apiFetch<{ recategorizedCount: number }>(`/api/rules/${id}`, { method: 'DELETE' })
}

export function fetchDashboard(month: string): Promise<Dashboard> {
  return apiFetch<Dashboard>(`/api/dashboard?month=${encodeURIComponent(month)}`)
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

export type Pace = 'HIGHER' | 'NORMAL' | 'LOWER' | 'UNKNOWN'

/** One category in the suggested budget (mirrors BudgetPlanService.PlanLine). */
export type PlanLine = {
  /** null = Uncategorized: part of the picture, but it can't hold a budget. */
  categoryId: number | null
  categoryName: string
  /** Median monthly spending over the last few full months. */
  usual: number
  /** The part of it that's regular bills and subscriptions. */
  fixed: number
  /** Suggested monthly budget, whole dollars. */
  suggested: number
  /** This month so far (through throughDay). */
  spent: number
  /** What had usually been spent by that day of the month. */
  usualSoFar: number
  pace: Pace
  /** Already has a budget the person set; using the plan won't change it. */
  hasBudget: boolean
}

/** A budget suggested from the person's own history (mirrors BudgetPlanService.BudgetPlan). */
export type BudgetPlan = {
  month: string
  /** Full months "usual" is based on; 0 = upload more statements first. */
  fullMonths: number
  /** Latest day of this month with data; 0 = none yet. */
  throughDay: number
  /** Usual money in per month; null when the statements show none (e.g. a credit card only). */
  monthlyIncome: number | null
  target: number | null
  basis: 'INCOME' | 'USUAL' | 'CUSTOM' | null
  suggestedTotal: number
  usualTotal: number
  spent: number
  usualSoFar: number
  pace: Pace
  lines: PlanLine[]
}

/** The suggestion for a month; pass target to build it around your own monthly total. Saves nothing. */
export function fetchBudgetPlan(month: string, target?: string): Promise<BudgetPlan> {
  const params = new URLSearchParams({ month })
  if (target) params.set('target', target)
  return apiFetch<BudgetPlan>(`/api/budgets/plan?${params}`)
}

/** "Use this budget": saves the suggestion (budgets you already set stay as they are). */
export function applyBudgetPlan(month: string, target?: string): Promise<BudgetOverview> {
  return apiFetch<BudgetOverview>('/api/budgets/plan', { method: 'POST', body: target ? { month, target } : { month } })
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

export type Cadence = 'WEEKLY' | 'BIWEEKLY' | 'MONTHLY' | 'QUARTERLY' | 'YEARLY'

/** Mirrors RecurringService.RecurringCharge: a subscription or bill found in your transactions. */
export type RecurringCharge = {
  /** Identifies the merchant, e.g. "NETFLIX COM"; used to hide it. */
  merchantKey: string
  /** Friendlier name, e.g. "Netflix". */
  name: string
  /** The latest charge exactly as the bank wrote it. */
  description: string
  cadence: Cadence
  /** The latest charge (current price), positive. */
  amount: number
  monthlyCost: number
  chargeCount: number
  firstDate: string
  lastDate: string
  nextExpected: string
  /** false = an expected charge never came: probably cancelled. */
  active: boolean
  categoryId: number | null
  categoryName: string | null
}

export type RecurringOverview = {
  charges: RecurringCharge[]
  /** Ones you marked "not a subscription". */
  hidden: RecurringCharge[]
  activeMonthlyTotal: number
  /** Your latest transaction date (null with no transactions); "cancelled" is judged from here. */
  asOf: string | null
}

export function fetchRecurring(): Promise<RecurringOverview> {
  return apiFetch<RecurringOverview>('/api/recurring')
}

/** "Not a subscription": leave this merchant off the list. */
export function hideRecurring(merchantKey: string): Promise<void> {
  return apiFetch<void>(`/api/recurring/hidden/${encodeURIComponent(merchantKey)}`, { method: 'PUT' })
}

export function unhideRecurring(merchantKey: string): Promise<void> {
  return apiFetch<void>(`/api/recurring/hidden/${encodeURIComponent(merchantKey)}`, { method: 'DELETE' })
}
