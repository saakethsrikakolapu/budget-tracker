/**
 * Spendr's voice: plain first, with a light touch in low-stakes moments. Never in errors, warnings
 * about money, or anything about deleting data. Each line is short and still useful on its own.
 */

/** A small nudge after "Groceries is $69 higher than usual." One per category. */
const NUDGES: Record<string, string> = {
  'food & dining': 'Takeout season?',
  groceries: 'Big shop, or a lot of snacks?',
  shopping: 'Treat yourself, just maybe not twice.',
  entertainment: 'Hopefully it was worth it.',
  transportation: 'Lots of rides lately.',
  travel: 'Hope the trip was good.',
  subscriptions: 'Did something new sneak in?',
  'bills & utilities': 'Might be worth checking the bill.',
  'health & personal care': 'Taking care of yourself counts.',
  education: 'Textbooks, probably.',
}

export function nudge(categoryName: string): string {
  return NUDGES[categoryName.toLowerCase()] ?? 'Worth a look.'
}
