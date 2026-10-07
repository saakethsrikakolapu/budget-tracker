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

/**
 * An error response from the backend, parsed from its ProblemDetail JSON, e.g.
 * {"status":400,"detail":"Invalid request","errors":{"password":"Password must be 8 to 72 characters"}}
 */
export class ApiError extends Error {
  readonly status: number
  readonly fieldErrors: Record<string, string>

  constructor(status: number, message: string, fieldErrors: Record<string, string> = {}) {
    super(message)
    this.status = status
    this.fieldErrors = fieldErrors
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
 */
export async function apiFetch<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = {}

  if (options.body !== undefined) {
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
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    const problem = await response.json().catch(() => ({}))
    throw new ApiError(response.status, problem.detail ?? `Request failed (HTTP ${response.status})`, problem.errors)
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
