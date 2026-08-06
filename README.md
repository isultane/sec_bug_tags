# sec_bug_tags

Replication package for tagging security bugs with concepts and aligning those
concepts to CWEs. The repository holds the two original experiments plus a
baseline-comparison toolkit.

## Repository layout

```
parse_nvd.py, lib.py, run_baselines.py   baseline comparison toolkit (below)
experiment_1/                            Sentence-LDA (SLDA) code and results
experiment_2/                            Seeded-LDA code, input and output data
```

---

## Baseline comparison for the CVE→CWE concept task

This toolkit adds a baseline comparison **using
the data you already have** — no new dataset, no new evaluation harness. It runs
four off-the-shelf unsupervised methods over your CVE corpus and scores each one
with the same concept→CWE F-measure you used in Experiment 1, so the result is a
few extra rows next to your Sentence-LDA numbers.

Methods: **TF-IDF + k-means**, **LSA + k-means**, **HDP**, **SBERT + k-means**.

---

## What you need

Your raw NVD feeds (XML — the legacy `nvdcve-*.xml` dumps — or JSON). Nothing else.

## Install

```bash
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
python -c "import nltk"          # PorterStemmer needs no downloads
```

If you only want the two fast methods first, `scikit-learn pandas numpy nltk`
is enough; add `gensim` for HDP and `sentence-transformers` for SBERT later.

## Run (three commands)

```bash
# 1. Parse NVD feeds -> data/cve.csv  (cve_id, cwe, text). Only CWE-labelled rows.
python parse_nvd.py --input /path/to/nvd_xml_dir --out data/cve.csv

# 2. Quick sanity pass on a 20k subsample with the two fast methods (~1 min)
python run_baselines.py --input data/cve.csv --sample 20000 --methods tfidf lsa

# 3. Full run, all four methods
python run_baselines.py --input data/cve.csv --methods tfidf lsa hdp sbert
```

Outputs land in `results/`:
- `comparison.csv` — every metric per method
- `comparison.tex` — a table ready to paste (fill in the Sentence-LDA row)

### Rough time budget (≈75k CVEs, CPU)
| step | time |
|---|---|
| parse | seconds |
| TF-IDF + k-means | < 1 min |
| LSA + k-means | 1–2 min |
| HDP | 5–20 min |
| SBERT encode + k-means | 10–20 min CPU (much faster on GPU) |

So the whole thing is an afternoon, most of it unattended.

---

## Keeping the comparison fair

The per-CWE breakdown in Experiment 1 uses a **manual** concept→CWE mapping;
these baselines use an **automatic** majority-vote mapping
(`lib.map_clusters_to_labels`). The two mappings are not directly comparable —
a manual mapping resolves ambiguous concepts that the automatic rule must guess,
so differences between them reflect the mapping, not the model. Score the
Sentence-LDA output with the **same** automatic rule so every row of the table
uses one method:

1. Export your Sentence-LDA hard assignments as a CSV with columns
   `cve_id, cluster` (cluster = argmax `p(c|d)`).
2. Join to `data/cve.csv` on `cve_id` and call:

```python
import pandas as pd, lib
d = pd.read_csv('data/cve.csv').merge(pd.read_csv('selflda_assignments.csv'), on='cve_id')
d = d[d.cwe.str.startswith('CWE-')]
print(lib.evaluate(d.cwe.values, d.cluster.values))
```

Put that row in the table alongside the baselines. The comparison is then
like-for-like, and the manual-mapping results remain citable as the detailed
per-CWE breakdown.

Reporting note: lead with **ARI / NMI** (mapping-free, robust) and show macro-F1
next to them. Macro-F1 under majority-vote mapping rewards over-clustering — a
method that splits one true class into ten clusters still maps each fragment to
the right label — so a method free to choose its own topic count (HDP) can post a
high F1 alongside a much lower ARI. Reporting both makes that visible.

---

## Scope

- **Baselines** — TF-IDF, LSA, HDP and an embedding model (SBERT), scored against
  the same concept→CWE task as the original experiments.
- **Replication** — documented, runnable scripts with a pinned
  `requirements.txt`. The Sentence-LDA and Seeded-LDA code retained from the
  original experiments lives in clearly-named, attributed folders alongside.

## Files
```
parse_nvd.py       NVD XML/JSON -> data/cve.csv
lib.py             preprocessing + cluster->CWE evaluation (P/R/F1, ARI, NMI)
run_baselines.py   runs the four methods, writes results/comparison.{csv,tex}
requirements.txt
```
