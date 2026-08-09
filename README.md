# sec_bug_tags

Replication package for **"A Hybrid Framework for Automated Detection of Security
Tags in Software Systems"** — tagging free-form software bug reports with
cybersecurity concepts using unsupervised topic modelling.

The framework runs in two stages:

| | stage | model | input | output |
|---|---|---|---|---|
| **Experiment 1** | extract cybersecurity concepts | **Sentence-LDA** | CVE summaries (NVD) | 40 concepts; 35 manually aligned to 17 CWEs, 5 "Not Assignable" |
| **Experiment 2** | tag bug reports | **Seeded-LDA** | 15 Apache Java projects' bug reports (RDF), seeded with the Experiment-1 concepts | each report tagged with a cybersecurity concept, or left untagged |

Experiment 1's manually-verified concept labels become the **seed sets** for
Experiment 2. The reported result is how often each extracted concept is found
in the bug reports.

---

## Attribution

This package contains third-party model implementations. Neither is our work,
and both are used under their original terms:

- **`experiment_1/SLDA_code/`** — Sentence-LDA (SLDA), by **Yohan Jo**.
  Cite: Y. Jo and A. Oh, *Aspect and Sentiment Unification Model for Online
  Review Analysis*, ACM WSDM, 2011.
- **`experiment_2/SeededLDA_code/`** — Seeded-LDA, by **Jagadeesh Jagarlamudi,
  Hal Daumé III and Raghavendra Udupa**. Cite: *Incorporating Lexical Priors
  into Topic Models*, EACL 2012.

Our contribution is the surrounding pipeline: the CVE→concept extraction, the
manual concept→CWE alignment, the use of Experiment-1 concepts as Experiment-2
seeds, and the evaluation and baseline harness in `baselines/`.

---

## Layout

```
baselines/                     comparison harness (this work)
  lib.py                       Experiment-1 preprocessing + cluster->CWE evaluation
  build_cve_summaries.py       NVD 2.0 feeds -> CVEsSummaries.csv
  parse_nvd.py                 NVD XML / JSON 1.1 / JSON 2.0 (+zip) -> flat CSV
  run_baselines_exp1.py        Experiment-1 baselines (labelled: CWE ground truth)
  run_baselines_exp2.py        Experiment-2 baselines (label-free: no ground truth)
experiment_1/                  Sentence-LDA code (Jo) + published results
experiment_2/                  Seeded-LDA code (Jagarlamudi et al.) + inputs/outputs
results_baselines/exp1/        comparison.csv/.tex, per_cwe_f1.csv, slda_manual_mapping.csv
results_baselines/exp2/        per_project.csv, summary.csv/.tex
cves/                          NVD JSON 2.0 feeds (not in git — see below)
```

`experiment_1/` and `experiment_2/` are treated as read-only source data.
Nothing in `baselines/` writes to them.

## Install

```bash
python -m venv .venv && source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
```

`scikit-learn pandas numpy nltk jinja2` covers everything except the HDP and
SBERT baselines; add `gensim` and `sentence-transformers` for those.

## Reproduce

```bash
# 0. Fetch the NVD JSON 2.0 yearly feeds into cves/  (nvdcve-2.0-YYYY.json.zip,
#    1999-2026; the 2002 feed carries the pre-2002 CVEs).
#    https://nvd.nist.gov/vuln/data-feeds

# 1. Build the CVE corpus  ->  CVEsSummaries.csv  (373,570 rows, ~180 MB)
python baselines/build_cve_summaries.py

# 2. Experiment-1 baselines  (~25 min; SBERT encoding dominates)
python baselines/run_baselines_exp1.py --methods tfidf lsa hdp sbert

# 3. Experiment-2 baselines  (~10 min)
python baselines/run_baselines_exp2.py
```

`run_baselines_exp1.py --sample 4000 --methods tfidf` is a ~10 s smoke test.

`CVEsSummaries.csv` is a **superset** of what Experiment 1's own
`CVEsExtractor.java` reads — its `Vulnerability` column is the SecOnt URI that
class strips and `Summary` is the description — so the original Java pipeline
runs against it unmodified.

---

## Experiment 1 — how the baselines are made comparable

Every row of `results_baselines/exp1/comparison.csv`, **including Sentence-LDA**,
is produced under one identical rule:

| dimension | shared by all rows |
|---|---|
| corpus | the 75,450 CVEs in `experiment_1/results/documentsBestTopics.csv` |
| ground truth | the `CWE` column of `CVEsSummaries.csv` |
| preprocessing | `lib.preprocess_exp1()` — Experiment 1's own pipeline |
| clusters | 40 (`--k`), matching Sentence-LDA's topic count |
| scoring | `lib.evaluate()` — majority-vote cluster→CWE, then P/R/F1, ARI, NMI |

Sentence-LDA enters as its 40 **hard topic assignments** (argmax θ), *not* as the
manual concept→CWE mapping, so it is scored by exactly the rule the baselines
get. The manual mapping is written separately to `slda_manual_mapping.csv`; it
is the citable detailed breakdown but is **not** comparable to the automatic
rows, because a human resolved ambiguous topics the automatic rule must guess.

### `lib.preprocess_exp1()` — reconstructed from the Java

lowercase → split on `[^a-zA-Z0-9]+` (**digits kept**) → drop Experiment 1's 463
stopwords → keep only tokens in `Dictionary.txt | ISdictionary.txt` (the
**vocabulary gate**) → Porter stem, original Martin algorithm (matching the Java
stemmer: `array`→`arrai`). No minimum token length.

Validated two ways: stemming the gate reproduces **502 of the 505** distinct
terms in `SLDA-T40-…-ProbWords.csv` (99.4%), and the paper's worked example
`CVE-2001-1365` → `"vulner"` reproduces exactly. The three stragglers — `sql`,
`exe`, `database` — are in the published model vocabulary but absent from the
shipped dictionaries, so they are recovered from `ProbWords.csv`. Without that,
`sql` would be stripped from the corpus the baselines see even though
Experiment 1's own run had it.

The generic `lib.preprocess()` is **not** used; it mismatches Experiment 1 on
tokeniser, stopword list, digits, minimum length and the vocabulary gate. It is
kept only for back-compatibility.

### Choices worth knowing

- **`--outside na`** (default): the 4,556 CVEs whose current NVD CWE falls
  outside Experiment 1's 18 classes are folded into `NA`, which
  `tags_trends.xlsx` names *"Not Assignable"*. This keeps the denominator at
  exactly 75,450. `--outside drop|keep` test the sensitivity.
- **SBERT sees raw, unstemmed text**; every other method gets the gated,
  stemmed corpus. Deliberately generous to the baseline.
- **HDP is tuned, not stock** (`T=40, K=5, chunksize=4096`). With gensim's
  defaults it collapses: 91% of documents in one cluster and negative ARI.
  Even tuned it stays degenerate (85.7% in one cluster) and should be reported
  as a failed baseline on this corpus, not as a fair characterisation of HDP.

### Label vintage

`CVEsSummaries.csv` is built from a **2026** NVD snapshot; the published numbers
used 2016-era labels. Drift over the 75,450 documents is small (NA −870,
CWE-399 −378, CWE-119 −277, everything else within ±70), and the manual-mapping
reproduction below confirms it is immaterial — but the resulting table
**supersedes** `topicsEvaluation.csv` rather than extending it.

---

## Experiment 2 — why the evaluation is label-free

There is **no security ground truth** for these bug reports: no CWE, no
security/non-security flag. Across all 15 projects' RDF dumps only **23 CVE
identifiers appear (12 unique)**, against 11,892 tagged reports — far too few to
score against. `run_baselines_exp2.py` therefore does not invent labels. It
reports what the data can support:

- **NPMI topic coherence** — the standard label-free quality measure
- **ARI / NMI against Seeded-LDA** — does seeding change the partition?
- **seed recovery** — how much of each topic's top-20 mass is its own seed terms

The corpus is reconstructed exactly from the Seeded-LDA inputs
(`<proj>_corpus.txt` = per-token document index, `<proj>_tokens.txt` =
per-token vocabulary index, `<proj>_vocabulary.txt`), so every method sees
byte-identical input to what Seeded-LDA consumed.

---

## Errata found while replicating

These are discrepancies between the manuscript and the shipped data, found by
re-running the pipeline. They are recorded here so the package and the paper
can be reconciled.

1. **`topicsEvaluation.csv` (paper Table 3) has `P[%]` and `R[%]` transposed.**
   For all 18 rows `TP+FN` equals the number of documents *predicted* into that
   class, and `Σ(TP+FN)` = 75,450 = the corpus size. So the column labelled `P`
   is recall and the one labelled `R` is precision. The paper's own definition
   in *Measurements* is the standard one; the implementation swapped them. F1 is
   symmetric and unaffected — only the two component columns need relabelling.
   Independently confirmed: re-scoring with the manual mapping reproduces every
   row to a mean absolute error of **0.37 percentage points**, but only when
   P and R are swapped.

2. **Experiment-2 run parameters differ from those reported.** All 15 run logs
   record `N = 100` iterations and `ALPHA = 1.0`; the manuscript states 1,000
   iterations and α = 50/K (≈ 8.33). β = 0.01 matches.

3. **Document counts.** Experiment 2 processed 12,593 reports and tagged
   11,892; the manuscript reports 12,663.

4. **Tag-frequency ranking.** Counting `docBestTopic.csv` across all 15 projects
   — cross-checked against `TopicsUsages.csv`, which agrees exactly — gives:
   XSS 34.0%, Buffer Overflow 29.3%, Access Privileges 9.7%, SQL Injection
   9.3%, Authentication Abuse 9.1%, Resource Management 8.6%. The manuscript
   states SQL injection, access control and authentication abuse are the most
   frequent; they rank 3rd, 4th and 5th, within 0.6 points of the last.

5. **The cosine-similarity "distinctness" measure is confounded.** Across all 15
   projects the argmax concept→topic mapping is identical with zero variation
   (C10→T1, C14→T2, C11→T3, C16→T4, C03→T5, C08→T6). Seeded-LDA seeds topic *k*
   with seed set *k*, so this diagonal is a property of the algorithm, not a
   finding. Seed recovery (below) measures the same thing without the
   confound.

6. **CVE corpus size.** The manuscript reports 74,945 unique vulnerabilities;
   the shipped results cover 75,450 documents.
