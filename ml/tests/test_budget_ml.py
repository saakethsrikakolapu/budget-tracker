"""Tests for the category model's data, text handling, export, and the rules baseline."""

import random

import pytest
from sklearn.feature_extraction.text import TfidfVectorizer

from budget_ml import model as m
from budget_ml.data import CATEGORIES, MERCHANTS, descriptions, generate, keyword_examples, split_merchants
from budget_ml.rules import KeywordRules
from budget_ml.text import normalize


# --- text ---

@pytest.mark.parametrize("raw, clean", [
    ("PURCHASE AUTHORIZED ON 10/21 Domino's Pizza #123", "PURCHASE AUTHORIZED ON DOMINO S PIZZA"),
    ("SQ *JOES COFFEE 4471", "SQ JOES COFFEE"),
    ("  uber   *trip  ", "UBER TRIP"),
    ("AMAZON MKTPL*2K4LM8AB3", "AMAZON MKTPL"),  # reference codes with digits go
    ("1234 5678", ""),
    ("", ""),
])
def test_normalize(raw, clean):
    assert normalize(raw) == clean


def test_char_ngrams_match_sklearn():
    analyzer = TfidfVectorizer(analyzer="char_wb", ngram_range=m.NGRAMS, lowercase=False).build_analyzer()
    for text in ["DOMINOS PIZZA", "A", "AB CD", "WALGRNS", "", "MCDONALDS F CHAPEL HILL NC"]:
        assert m.char_wb_ngrams(text, *m.NGRAMS) == analyzer(text)


# --- data ---

def test_generation_is_repeatable():
    assert generate(seed=5)[0][:50] == generate(seed=5)[0][:50]
    assert descriptions(MERCHANTS, seed=1, per_merchant=2) == descriptions(MERCHANTS, seed=1, per_merchant=2)


def test_unseen_merchants_never_appear_in_training():
    train, test = split_merchants()
    for category in CATEGORIES:
        assert test[category], category
        assert not set(train[category]) & set(test[category])


def test_keyword_examples_only_use_spending_categories():
    examples = keyword_examples([("PIZZA", "Food & Dining"), ("PAYROLL", "Income")], per_keyword=3)
    assert {e.category for e in examples} == {"Food & Dining"}
    assert len(examples) == 3


# --- rules baseline (mirrors MerchantKeywords.java) ---

def test_rules_match_whole_words_longest_first():
    rules = KeywordRules()
    assert rules.categorize("UBER EATS ORDER 123") == "Food & Dining"   # UBER EATS beats UBER
    assert rules.categorize("DOMINOSPIZZA 4471") is None                  # glued: whole words miss
    assert rules.categorize("SQ *JOES COFFEE") == "Food & Dining"
    assert rules.categorize("PAYROLL ACME") is None                       # money-in only
    assert rules.categorize("PAYROLL ACME", money_in=True) == "Income"


# --- export: what Java will run ---

@pytest.fixture(scope="module")
def trained():
    rng = random.Random(0)
    train = descriptions(MERCHANTS, seed=1, per_merchant=6)
    rng.shuffle(train)
    pipeline = m.build().fit([e.description for e in train], [e.category for e in train])
    return pipeline, m.export(pipeline, threshold=0.8)


def test_exported_model_predicts_like_sklearn(trained):
    pipeline, exported = trained
    samples = [e.description for e in descriptions(MERCHANTS, seed=9, per_merchant=1)][:120]
    expected = pipeline.predict_proba(samples)
    same_top = 0
    for text, row in zip(samples, expected):
        got = m.predict_exported(exported, text)
        # Weights are rounded to 6 decimals in the export, so allow a tiny difference.
        assert max(abs(a - b) for a, b in zip(got, row)) < 1e-3
        same_top += int(max(range(len(got)), key=got.__getitem__) == row.argmax())
    assert same_top == len(samples)


def test_export_is_plain_json_with_the_expected_shape(trained):
    _, exported = trained
    n_classes, n_features = len(exported["classes"]), len(exported["vocabulary"])
    assert exported["classes"] == sorted(CATEGORIES)
    assert len(exported["idf"]) == n_features
    assert len(exported["coef"]) == n_classes and all(len(row) == n_features for row in exported["coef"])
    assert len(exported["intercept"]) == n_classes
    assert sorted(exported["vocabulary"].values()) == list(range(n_features))


def test_text_with_no_known_ngrams_gives_the_prior(trained):
    _, exported = trained
    probabilities = m.predict_exported(exported, "1234 5678")
    assert abs(sum(probabilities) - 1) < 1e-9
