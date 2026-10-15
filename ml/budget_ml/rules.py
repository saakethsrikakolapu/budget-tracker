"""
The baseline to beat: the app's built-in merchant dictionary, read straight from the database
migration that creates it (V11) and matched exactly like MerchantKeywords.java does: whole words
on normalized text, longest keyword first. The model only has to win where this misses.
"""

from __future__ import annotations

import re
from pathlib import Path

MIGRATION = Path(__file__).resolve().parents[2] / "backend/src/main/resources/db/migration/V11__create_merchant_keywords.sql"
_ROW = re.compile(r"\('([A-Z0-9 ]+)',\s*'([^']+)'(?:,\s*(TRUE|FALSE))?\)")
_NOT_ALNUM = re.compile(r"[^A-Z0-9]+")


def _normalize(text: str) -> str:
    """MerchantKeywords.normalize: like text.normalize, but digits stay (keywords like 7 ELEVEN)."""
    return _NOT_ALNUM.sub(" ", text.upper()).strip()


class KeywordRules:
    def __init__(self, migration: Path = MIGRATION):
        rows = _ROW.findall(migration.read_text(encoding="utf-8"))
        # (keyword, category, money_in_only), most specific (longest) first, then alphabetical.
        self.keywords = sorted(((k, c, m == "TRUE") for k, c, m in rows), key=lambda r: (-len(r[0]), r[0]))

    def categorize(self, description: str, money_in: bool = False) -> str | None:
        padded = f" {_normalize(description)} "
        for keyword, category, money_in_only in self.keywords:
            if money_in_only and not money_in:
                continue
            if f" {keyword} " in padded:
                return category
        return None
