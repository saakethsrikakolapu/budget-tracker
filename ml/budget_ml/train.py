"""
Train, evaluate against the rules, and export.

    cd ml && python -m budget_ml.train

Two evaluations, because they answer different questions:
  known merchants   merchants the model was trained on, in new, messy descriptions (glued words,
                    cut-off text, dropped vowels, other banks' formats): the everyday case.
  unseen merchants  merchants held out of training entirely: the hard case. A bare brand name
                    ("BOJANGLES") carries no hint of its category, so no text model can be expected
                    to do well; it only helps when the description contains a telling word.

The suggestion cutoff is the lowest confidence at which suggestions are right at least 95% of the
time for known merchants AND 80% for unseen ones (they're labeled "Suggested" and one click to
change, so 4 in 5 right on brand-new merchants is worth showing). The shipped model is then trained
on all merchants.

Writes:
  backend/src/main/resources/ml/category-model.json   the model the app loads
  backend/src/test/resources/ml/parity-cases.json     inputs + expected outputs Java must reproduce
  ml/results.json                                     the measured numbers (quoted in the README)
"""

from __future__ import annotations

import json
from pathlib import Path

from sklearn.metrics import f1_score

from . import model as m
from .data import CATEGORIES, MERCHANTS, descriptions, keyword_examples, split_merchants
from .rules import KeywordRules
from .text import normalize

ROOT = Path(__file__).resolve().parents[2]
MODEL_OUT = ROOT / "backend/src/main/resources/ml/category-model.json"
PARITY_OUT = ROOT / "backend/src/test/resources/ml/parity-cases.json"
RESULTS_OUT = ROOT / "ml/results.json"

THRESHOLDS = [round(0.30 + 0.05 * i, 2) for i in range(14)]  # 0.30 .. 0.95
TARGET_KNOWN = 0.95
TARGET_UNSEEN = 0.80
PER_MERCHANT = 25


def evaluate(pipeline, test, rules: KeywordRules) -> dict:
    """Rules alone, the model alone, and rules first with the model filling gaps, at each cutoff."""
    labels = [e.category for e in test]
    n = len(test)
    rule_guess = [rules.categorize(e.description) for e in test]
    probabilities = pipeline.predict_proba([e.description for e in test])
    classes = list(pipeline.classes_)
    ml_guess = [classes[row.argmax()] for row in probabilities]
    ml_conf = [float(row.max()) for row in probabilities]
    missed = [i for i, g in enumerate(rule_guess) if g is None]

    def at(threshold: float) -> dict:
        # The model is only asked about what rules leave uncategorized (that's how the app uses it).
        asked = [i for i in missed if ml_conf[i] >= threshold]
        right = sum(ml_guess[i] == labels[i] for i in asked)
        combined = [g if g is not None else (mg if c >= threshold else None)
                    for g, mg, c in zip(rule_guess, ml_guess, ml_conf)]
        return {
            "threshold": threshold,
            "suggestions": len(asked),
            "suggestionPrecision": right / len(asked) if asked else None,
            "combinedCoverage": sum(g is not None for g in combined) / n,
            "combinedAccuracy": sum(g == y for g, y in zip(combined, labels)) / n,
            "combinedWrong": sum(g is not None and g != y for g, y in zip(combined, labels)) / n,
        }

    covered = n - len(missed)
    return {
        "examples": n,
        "merchants": len({e.merchant for e in test}),
        "rules": {
            "coverage": covered / n,
            "precision": sum(g == y for g, y in zip(rule_guess, labels) if g is not None) / max(1, covered),
            "accuracy": sum(g == y for g, y in zip(rule_guess, labels)) / n,
        },
        "model": {
            "accuracy": sum(g == y for g, y in zip(ml_guess, labels)) / n,
            "macroF1": f1_score(labels, ml_guess, average="macro"),
            "accuracyWhereRulesMiss": sum(ml_guess[i] == labels[i] for i in missed) / len(missed) if missed else None,
        },
        "sweep": [at(t) for t in THRESHOLDS],
    }


def pick_threshold(known: dict, unseen: dict) -> float:
    """Lowest cutoff meeting both precision targets (the strictest cutoff if none does)."""
    def ok(evaluation: dict, i: int, target: float) -> bool:
        s = evaluation["sweep"][i]
        return s["suggestions"] == 0 or s["suggestionPrecision"] >= target
    for i, t in enumerate(THRESHOLDS):
        if ok(known, i, TARGET_KNOWN) and ok(unseen, i, TARGET_UNSEEN):
            return t
    return THRESHOLDS[-1]


def parity_cases(exported: dict, test, limit: int = 150) -> dict:
    """Inputs with the outputs Java must reproduce (n-grams, and probabilities from exported weights)."""
    picked = test[:: max(1, len(test) // limit)][:limit]
    extra = ["", "   ", "1234 5678", "Café Ünicode #12", "DOMINOSPIZZA 4471 CHAPEL HILL NC"]
    return {
        "classes": exported["classes"],
        "cases": [
            {
                "description": d,
                "normalized": normalize(d),
                "ngrams": m.char_wb_ngrams(normalize(d), exported["ngramMin"], exported["ngramMax"]),
                "probabilities": m.predict_exported(exported, d),
            }
            for d in [e.description for e in picked] + extra
        ],
    }


def main() -> None:
    rules = KeywordRules()
    keywords = keyword_examples([(k, c) for k, c, money_in in rules.keywords if not money_in])

    # Known merchants: train on all of them, test on fresh descriptions (exact repeats removed).
    train_all = descriptions(MERCHANTS, seed=1, per_merchant=PER_MERCHANT) + keywords
    seen = {normalize(e.description) for e in train_all}
    test_known = [e for e in descriptions(MERCHANTS, seed=3, per_merchant=10) if normalize(e.description) not in seen]
    final = m.build().fit([e.description for e in train_all], [e.category for e in train_all])
    known = evaluate(final, test_known, rules)

    # Unseen merchants: a separate model that never sees the test merchants.
    train_m, test_m = split_merchants()
    train_unseen = descriptions(train_m, seed=1, per_merchant=PER_MERCHANT) + keywords
    test_unseen = descriptions(test_m, seed=2, per_merchant=PER_MERCHANT)
    held_out = m.build().fit([e.description for e in train_unseen], [e.category for e in train_unseen])
    unseen = evaluate(held_out, test_unseen, rules)

    threshold = pick_threshold(known, unseen)
    exported = m.export(final, threshold=threshold)
    MODEL_OUT.parent.mkdir(parents=True, exist_ok=True)
    PARITY_OUT.parent.mkdir(parents=True, exist_ok=True)
    m.save(exported, MODEL_OUT)
    with open(PARITY_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(parity_cases(exported, test_known), f, indent=1, ensure_ascii=False)

    results = {
        "categories": len(CATEGORIES),
        "trainExamples": len(train_all),
        "keywordExamples": len(keywords),
        "features": len(exported["vocabulary"]),
        "threshold": threshold,
        "knownMerchants": known,
        "unseenMerchants": unseen,
    }
    with open(RESULTS_OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(results, f, indent=2)

    i = THRESHOLDS.index(threshold)
    print(f"trained on {len(train_all)} examples ({len(keywords)} from the dictionary), "
          f"{results['features']} features; suggestion cutoff {threshold}")
    for name, r in (("known merchants, new descriptions", known), ("unseen merchants", unseen)):
        s = r["sweep"][i]
        print(f"\n{name}: {r['examples']} test descriptions, {r['merchants']} merchants")
        print(f"  rules only:    covers {r['rules']['coverage']:.1%}, right {r['rules']['precision']:.1%} of those, "
              f"overall {r['rules']['accuracy']:.1%}")
        print(f"  model only:    {r['model']['accuracy']:.1%} (macro-F1 {r['model']['macroF1']:.3f}); "
              f"on what rules miss {r['model']['accuracyWhereRulesMiss']:.1%}")
        print(f"  rules + model: covers {s['combinedCoverage']:.1%}, overall {s['combinedAccuracy']:.1%}, "
              f"wrong {s['combinedWrong']:.1%}; {s['suggestions']} suggestions, right "
              f"{(s['suggestionPrecision'] or 0):.1%}")
    print("\nwrote", MODEL_OUT.relative_to(ROOT), f"({MODEL_OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()
