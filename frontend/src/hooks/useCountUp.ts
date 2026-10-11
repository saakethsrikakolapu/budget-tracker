import { useEffect, useState } from 'react'

const prefersReducedMotion = () => window.matchMedia('(prefers-reduced-motion: reduce)').matches

/**
 * Animates a number from 0 up to `target` (about 0.6 s, easing out). Display only: the real value
 * is never changed. People who ask their OS for reduced motion get the final number right away.
 */
export function useCountUp(target: number, durationMs = 600): number {
  const [value, setValue] = useState(0)

  useEffect(() => {
    if (prefersReducedMotion()) return
    let frame = 0
    const start = performance.now()
    const tick = (now: number) => {
      const progress = Math.min((now - start) / durationMs, 1)
      setValue(target * (1 - Math.pow(1 - progress, 3)))
      if (progress < 1) frame = requestAnimationFrame(tick)
    }
    frame = requestAnimationFrame(tick)
    return () => cancelAnimationFrame(frame)
  }, [target, durationMs])

  return prefersReducedMotion() ? target : value
}
