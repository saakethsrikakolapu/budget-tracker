# Category model (Stage 6)

Suggests a category for transactions the rules can't place: a TF-IDF model over character
n-grams (3 to 5 letters) with multinomial logistic regression, trained in Python with
scikit-learn and **run inside the Java backend** (no separate service, so no extra cold start on
free hosting). The model is exported as plain JSON (`backend/src/main/resources/ml/category-model.json`);
`CategoryModel.java` does the same math, and a parity test checks it against Python's outputs.

```bash
cd ml
pip install -r requirements.txt
python -m budget_ml.train     # generate data, train, evaluate vs. the rules, export the model
python -m pytest              # tests
```

| File | What it does |
|---|---|
| `budget_ml/data.py` | Synthetic labeled descriptions in real banks' formats, plus the messy cases rules miss: glued words (`DOMINOSPIZZA`), text cut at ~22 characters, dropped vowels (`WALGRNS`). Also turns the app's merchant dictionary into training examples. |
| `budget_ml/text.py` | Text cleanup, identical to the Java side. |
| `budget_ml/model.py` | The scikit-learn pipeline, the JSON export, and `predict_exported` (the exact algorithm Java runs). |
| `budget_ml/rules.py` | The baseline: the app's dictionary, read from migration V11 and matched like `MerchantKeywords.java`. |
| `budget_ml/train.py` | Trains, evaluates two scenarios, picks the confidence cutoff, writes the model, Java parity cases, and `results.json`. |

**Two evaluations**, because they answer different questions:

- **Known merchants, new descriptions:** merchants seen in training, written in fresh, messy ways.
  This is the everyday case: the same coffee shop shows up glued, truncated, or in another
  bank's format.
- **Unseen merchants:** held out of training entirely. A bare brand name carries no hint of its
  category, so this stays hard; the model helps only when there's a telling word in the text.

The data is synthetic, so these numbers show the model handles the patterns it was built for, not
how it does on any real statement. Real users' corrections are the natural next training data.
