"""
Text cleanup shared by training (here) and prediction (Java: CategoryModel.normalize). The two
MUST stay identical, or the model sees different text in the app than it was trained on; the
parity test in the backend checks that.
"""

import re

_NOT_ALNUM = re.compile(r"[^A-Z0-9]+")


def normalize(description: str) -> str:
    """
    "PURCHASE AUTHORIZED ON 10/21 Domino's Pizza #123" -> "PURCHASE AUTHORIZED ON DOMINO S PIZZA".
    Uppercase, anything that isn't a letter or digit becomes a space, and words containing digits
    (dates, store numbers, card digits, reference codes) are dropped: they change every time and
    would only teach the model noise.
    """
    words = _NOT_ALNUM.sub(" ", description.upper()).split()
    return " ".join(w for w in words if not any(ch.isdigit() for ch in w))
