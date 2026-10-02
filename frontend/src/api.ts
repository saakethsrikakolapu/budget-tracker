/** Mirrors HealthController.HealthResponse in the backend. */
export type HealthResponse = {
  status: 'UP' | 'DOWN'
  database: 'UP' | 'DOWN'
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
