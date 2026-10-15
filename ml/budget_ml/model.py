"""
The category model: TF-IDF over character n-grams, then multinomial logistic regression.

Why character n-grams (3 to 5 letters, within words): they match pieces of words, so "DOMINOSPIZZA"
still shares "PIZ", "IZZ", "ZZA" with "PIZZA", and "WALGRNS" shares "WAL", "GRN" with "WALGREENS".
Whole-word rules can't do that.

The trained model is exported to plain JSON (vocabulary, weights) so the Java backend can run it
without Python: see export() and predict_exported(), which is the exact math Java implements.
"""

from __future__ import annotations

import json
import math

import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.pipeline import Pipeline

from .text import normalize

NGRAMS = (3, 5)
DECIMALS = 6  # exported weights are rounded; Java and predict_exported both use the rounded values


def build() -> Pipeline:
    return Pipeline([
        ("tfidf", TfidfVectorizer(
            analyzer="char_wb", ngram_range=NGRAMS, preprocessor=normalize, lowercase=False,
            sublinear_tf=True, min_df=2, max_features=6000)),
        ("clf", LogisticRegression(C=10.0, max_iter=3000)),
    ])


def export(pipeline: Pipeline, threshold: float) -> dict:
    """Everything Java needs to reproduce predict_proba, as plain numbers and strings."""
    tfidf: TfidfVectorizer = pipeline.named_steps["tfidf"]
    clf: LogisticRegression = pipeline.named_steps["clf"]
    vocabulary = {term: int(index) for term, index in sorted(tfidf.vocabulary_.items(), key=lambda kv: kv[1])}
    return {
        "format": "budget-tracker-category-model/1",
        "description": "TF-IDF (char_wb n-grams, sublinear tf, l2) + multinomial logistic regression",
        "ngramMin": NGRAMS[0],
        "ngramMax": NGRAMS[1],
        "suggestThreshold": threshold,
        "classes": [str(c) for c in clf.classes_],
        "vocabulary": vocabulary,
        "idf": [round(float(v), DECIMALS) for v in tfidf.idf_],
        "coef": [[round(float(v), DECIMALS) for v in row] for row in clf.coef_],
        "intercept": [round(float(v), DECIMALS) for v in clf.intercept_],
    }


def char_wb_ngrams(text: str, n_min: int, n_max: int) -> list[str]:
    """Same n-grams sklearn's analyzer="char_wb" makes: each word padded with spaces, then sliced."""
    grams: list[str] = []
    for word in text.split():
        padded = f" {word} "
        for n in range(n_min, n_max + 1):
            offset = 0
            grams.append(padded[offset:offset + n])
            while offset + n < len(padded):
                offset += 1
                grams.append(padded[offset:offset + n])
            if offset == 0:  # word shorter than n: longer n-grams would repeat the whole word
                break
    return grams


def predict_exported(model: dict, description: str) -> list[float]:
    """Class probabilities from the exported JSON. This is the algorithm CategoryModel.java runs."""
    counts: dict[int, int] = {}
    for gram in char_wb_ngrams(normalize(description), model["ngramMin"], model["ngramMax"]):
        index = model["vocabulary"].get(gram)
        if index is not None:
            counts[index] = counts.get(index, 0) + 1
    # tf-idf with sublinear tf (1 + ln count), then scale the vector to length 1
    weights = {i: (1.0 + math.log(c)) * model["idf"][i] for i, c in counts.items()}
    norm = math.sqrt(sum(w * w for w in weights.values()))
    if norm > 0:
        weights = {i: w / norm for i, w in weights.items()}
    scores = [b + sum(row[i] * w for i, w in weights.items()) for row, b in zip(model["coef"], model["intercept"])]
    top = max(scores)
    exps = [math.exp(s - top) for s in scores]  # softmax, shifted for numerical safety
    total = sum(exps)
    return [e / total for e in exps]


def save(model: dict, path) -> None:
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(model, f, separators=(",", ":"))


def top(model: dict, probabilities: list[float]) -> tuple[str, float]:
    best = int(np.argmax(probabilities))
    return model["classes"][best], probabilities[best]
