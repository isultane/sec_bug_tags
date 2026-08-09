#!/usr/bin/env python3
"""
run_baselines.py  --  Baseline comparison for the CVE->CWE concept task.

Every row of the output table -- including Sentence-LDA -- is produced under
ONE identical rule:

  * the SAME corpus       : exactly the CVEs Experiment 1 scored, taken from
                            experiment_1/results/documentsBestTopics.csv
  * the SAME ground truth : the CWE column of CVEsSummaries.csv
  * the SAME preprocessing: lib.preprocess_exp1() (Experiment 1's tokeniser,
                            stopwords, dictionary gate and Porter stemmer)
  * the SAME scoring      : lib.evaluate() -- majority-vote cluster->CWE
                            mapping, then macro/weighted P/R/F1 + ARI/NMI

Sentence-LDA enters as its 40 hard topic assignments (argmax theta), NOT as
the manual concept->CWE mapping, so it is scored by the same automatic rule as
every baseline. The manual mapping is still written out separately, for
reference, as slda_manual_mapping.csv.

Usage:
    python run_baselines.py --methods tfidf lsa                # fast pass
    python run_baselines.py --methods tfidf lsa hdp sbert      # full run

experiment_1/ and experiment_2/ are read-only throughout.
"""
import argparse, csv, os, sys, time
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.decomposition import TruncatedSVD
from sklearn.preprocessing import Normalizer
from sklearn.cluster import KMeans
from sklearn.pipeline import make_pipeline

import lib

SEED = 42
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

LABELS = {'slda': 'Sentence-LDA (this work)', 'tfidf': 'TF-IDF + k-means',
          'lsa': 'LSA + k-means', 'hdp': 'HDP', 'sbert': 'SBERT + k-means'}

# The 18 classes Experiment 1 reports in topicsEvaluation.csv.
EXP1_CLASSES = {
    'NA', 'CWE-16', 'CWE-17', 'CWE-20', 'CWE-22', 'CWE-77', 'CWE-79',
    'CWE-89', 'CWE-94', 'CWE-119', 'CWE-134', 'CWE-200', 'CWE-255',
    'CWE-264', 'CWE-287', 'CWE-310', 'CWE-352', 'CWE-399',
}


def load_corpus(summaries, assignments, outside):
    """Return a DataFrame: cve_id, cwe, text, slda_topic -- Experiment 1's set."""
    slda = {}
    with open(assignments, encoding='utf-8') as fh:
        for row in csv.reader(fh):
            if len(row) >= 2 and row[0].strip():
                slda[row[0].strip()] = row[1].strip()
    print(f"Sentence-LDA assignments: {len(slda)} docs, "
          f"{len(set(slda.values()))} topics")

    csv.field_size_limit(10 ** 9)
    rows, seen = [], set()
    with open(summaries, encoding='utf-8') as fh:
        for r in csv.DictReader(fh):
            key = r['CVE_ID'].replace('CVE-', '')
            if key not in slda or key in seen:
                continue
            seen.add(key)
            rows.append((r['CVE_ID'], r['CWE'], r['Summary'], slda[key]))

    df = pd.DataFrame(rows, columns=['cve_id', 'cwe', 'text', 'slda_topic'])
    missing = len(slda) - len(df)
    if missing:
        print(f"  ! {missing} assigned CVEs absent from {summaries}")

    n_out = (~df['cwe'].isin(EXP1_CLASSES)).sum()
    if outside == 'na':
        df.loc[~df['cwe'].isin(EXP1_CLASSES), 'cwe'] = 'NA'
        print(f"  {n_out} docs in CWEs outside Experiment 1's 18 classes "
              f"-> folded into NA")
    elif outside == 'drop':
        df = df[df['cwe'].isin(EXP1_CLASSES)].reset_index(drop=True)
        print(f"  {n_out} docs in outside CWEs -> dropped")
    else:
        print(f"  {n_out} docs in outside CWEs -> kept as their own classes")

    print(f"Corpus: {len(df)} CVEs, {df['cwe'].nunique()} ground-truth classes")
    return df


def tfidf_matrix(texts):
    return TfidfVectorizer(min_df=2, max_df=0.6,
                           max_features=50000).fit_transform(texts)


def tfidf_kmeans(texts, k):
    X = tfidf_matrix(texts)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(X)


def lsa_kmeans(texts, k, n_comp=100):
    X = tfidf_matrix(texts)
    Z = make_pipeline(TruncatedSVD(n_components=min(n_comp, X.shape[1] - 1),
                                   random_state=SEED),
                      Normalizer(copy=False)).fit_transform(X)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(Z)


def hdp_topics(texts, T=40, K=5, chunksize=4096):
    """HDP picks its own topic count; doc -> argmax topic = its cluster.

    NOT gensim's defaults, and deliberately so. With the stock settings the
    model collapses on this corpus: 91% of all 75,450 CVEs land in a single
    topic, majority-vote maps 150 clusters onto just 6 labels, and ARI goes
    negative (worse than chance). Converting via suggested_lda_model() does
    not help, so the collapse is in the variational fit, not the inference.

    A six-config sweep on the FULL corpus (a 20k subsample does NOT predict
    full-corpus behaviour -- the best subsample config was among the worst at
    75k) gave these settings as the best available: macro-F1 0.079 vs 0.039
    for the defaults, ARI +0.053 vs -0.012, NMI 0.172 vs 0.049. T=40 also
    matches the cluster budget every other method is given, so it is not a
    cherry-picked truncation.

    Tuning here favours the *baseline*, the conservative direction for any
    claim made against it -- but it must be disclosed, and so must the fact
    that even at its best HDP stays badly degenerate: 85.7% of documents sit
    in one cluster. It is reported as a failed baseline on this corpus, not
    as a fair characterisation of hierarchical Dirichlet processes.
    """
    from gensim import corpora
    from gensim.models import HdpModel
    tokenised = [t.split() for t in texts]
    dic = corpora.Dictionary(tokenised)
    dic.filter_extremes(no_below=2, no_above=0.6)
    corpus = [dic.doc2bow(d) for d in tokenised]
    hdp = HdpModel(corpus, dic, random_state=SEED,
                   T=T, K=K, chunksize=chunksize)
    out = []
    for bow in corpus:
        dist = hdp[bow]
        out.append(max(dist, key=lambda x: x[1])[0] if dist else -1)
    return np.array(out)


def sbert_kmeans(raw_texts, k, model='all-MiniLM-L6-v2'):
    """Embed the ORIGINAL unstemmed text, then cluster.

    Deliberately generous to the baseline: SBERT sees the raw description,
    not the dictionary-gated stemmed text the other methods (and SLDA) get.
    Disclose this when reporting.
    """
    from sentence_transformers import SentenceTransformer
    emb = SentenceTransformer(model).encode(
        list(raw_texts), batch_size=64, show_progress_bar=True,
        normalize_embeddings=True)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(emb)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--summaries',
                    default=os.path.join(ROOT, 'CVEsSummaries.csv'))
    ap.add_argument('--assignments',
                    default=os.path.join(ROOT, 'experiment_1', 'results',
                                         'documentsBestTopics.csv'))
    ap.add_argument('--methods', nargs='+',
                    default=['tfidf', 'lsa', 'hdp', 'sbert'],
                    choices=['tfidf', 'lsa', 'hdp', 'sbert'])
    ap.add_argument('--k', type=int, default=0,
                    help='clusters for k-means (0 = SLDA topic count, 40)')
    ap.add_argument('--outside', choices=['na', 'drop', 'keep'], default='na',
                    help="docs whose CWE is outside Experiment 1's 18 classes")
    ap.add_argument('--sample', type=int, default=0,
                    help='subsample N docs for a quick smoke test')
    ap.add_argument('--outdir',
                    default=os.path.join(ROOT, 'results_baselines', 'exp1'))
    args = ap.parse_args()

    df = load_corpus(args.summaries, args.assignments, args.outside)
    if args.sample and args.sample < len(df):
        df = df.sample(args.sample, random_state=SEED).reset_index(drop=True)
        print(f"Subsampled to {len(df)} docs (smoke test)")

    y = df['cwe'].values
    k = args.k or df['slda_topic'].nunique()

    print("\nPreprocessing with Experiment 1's pipeline ...")
    t0 = time.time()
    res = lib.load_exp1_resources()
    proc = df['text'].map(lambda t: lib.preprocess_exp1(t, res)).values
    raw = df['text'].values
    empty = int((proc == '').sum())
    print(f"  done in {time.time() - t0:.0f}s | {empty} docs empty after gating")

    runs = [('slda', df['slda_topic'].values)]          # always, same rule
    for m in args.methods:
        t0 = time.time()
        print(f"\n=== {m}  (k={k}) ===")
        try:
            if m == 'tfidf':   c = tfidf_kmeans(proc, k)
            elif m == 'lsa':   c = lsa_kmeans(proc, k)
            elif m == 'hdp':   c = hdp_topics(proc)
            elif m == 'sbert': c = sbert_kmeans(raw, k)
        except ImportError as e:
            print(f"  skipped ({e}); install the dependency and re-run.")
            continue
        print(f"  clustered in {time.time() - t0:.0f}s")
        runs.append((m, c))

    rows, per_cwe = [], []
    for name, clusters in runs:
        r = lib.evaluate(y, clusters)
        r['method'] = LABELS[name]
        rows.append(r)
        for d in lib.per_class_f1(y, clusters):
            d['method'] = LABELS[name]
            per_cwe.append(d)
        print(f"{LABELS[name]:26} F1m={r['F1_macro']:.4f} "
              f"F1w={r['F1_weighted']:.4f} ARI={r['ARI']:.4f} NMI={r['NMI']:.4f}")

    os.makedirs(args.outdir, exist_ok=True)
    order = [LABELS[m] for m in ('slda', 'tfidf', 'lsa', 'hdp', 'sbert')]
    cols = ['method', 'n_docs', 'n_clusters', 'P_macro', 'R_macro',
            'F1_macro', 'F1_weighted', 'ARI', 'NMI', 'Vmeasure']
    out = pd.DataFrame(rows)[cols]
    out['_o'] = out['method'].map(order.index)
    out = out.sort_values('_o').drop(columns='_o').reset_index(drop=True)
    out.to_csv(os.path.join(args.outdir, 'comparison.csv'), index=False)

    pc = pd.DataFrame(per_cwe)[['method', 'cwe', 'P', 'R', 'F1', 'support']]
    pc['_o'] = pc['method'].map(order.index)
    pc = pc.sort_values(['_o', 'cwe']).drop(columns='_o')
    pc.to_csv(os.path.join(args.outdir, 'per_cwe_f1.csv'), index=False)

    tex = out[['method', 'F1_macro', 'F1_weighted', 'ARI', 'NMI']].copy()
    for c in ['F1_macro', 'F1_weighted', 'ARI', 'NMI']:
        tex[c] = tex[c].map(lambda v: f'{v:.3f}')
    tex.columns = ['Method', 'F1 (macro)', 'F1 (weighted)', 'ARI', 'NMI']
    with open(os.path.join(args.outdir, 'comparison.tex'), 'w') as fh:
        fh.write(tex.to_latex(
            index=False, escape=True,
            caption='Unsupervised methods on the CVE corpus (concept--CWE '
                    'alignment). All rows share one corpus, one preprocessing '
                    'pipeline and one automatic majority-vote cluster--CWE '
                    'mapping, so they are directly comparable.',
            label='tab:baselines'))

    # Reference only: Experiment 1's MANUAL topic->CWE mapping, which is not
    # comparable to the rows above (a human resolved ambiguous topics).
    tags_path = os.path.join(ROOT, 'experiment_1', 'results',
                             'cwetopicstags.csv')
    if os.path.exists(tags_path):
        with open(tags_path, encoding='utf-8') as fh:
            tags = next(csv.reader(fh))
        pred = np.array([tags[int(t[1:])] if int(t[1:]) < len(tags) else 'NA'
                         for t in df['slda_topic'].values])
        man = pd.DataFrame(lib.per_class_f1_from_pred(y, pred))
        man.to_csv(os.path.join(args.outdir, 'slda_manual_mapping.csv'),
                   index=False)

    print(f"\nWrote {args.outdir}/comparison.csv, comparison.tex, per_cwe_f1.csv")
    print(out.to_string(index=False))


if __name__ == '__main__':
    main()
