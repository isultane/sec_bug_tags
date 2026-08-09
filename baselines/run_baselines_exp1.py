#!/usr/bin/env python3
"""
run_baselines.py  --  Baseline comparison for the CVE->CWE concept task.

Runs any subset of {tfidf, lsa, hdp, sbert} over the SAME CVE corpus used in
Experiment 1, evaluates each with the shared cluster->CWE F-measure, and writes
a comparison table you can paste next to your Sentence-LDA row.

Quick first pass (subsample, skip the slow ones):
    python run_baselines.py --input data/cve.csv --sample 20000 --methods tfidf lsa
Full run:
    python run_baselines.py --input data/cve.csv --methods tfidf lsa hdp sbert

Fair-comparison note: the numbers in your paper's Table 4 use a *manual*
concept->CWE mapping, while these baselines use an *automatic* majority mapping.
For a strictly fair table, re-score your Sentence-LDA document assignments with
lib.evaluate() too (see --selflda in the README), so every row uses one rule.
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


def load(path, sample, min_class):
    df = pd.read_csv(path).dropna(subset=['text'])
    df = df[df['cwe'].astype(str).str.startswith('CWE-')]        # labelled only
    if min_class > 0:
        keep = df['cwe'].value_counts()[lambda s: s >= min_class].index
        df = df[df['cwe'].isin(keep)]
    if sample and sample < len(df):
        df = df.sample(sample, random_state=SEED)
    df = df.reset_index(drop=True)
    print(f"Loaded {len(df)} labelled CVEs across {df['cwe'].nunique()} CWEs")
    return df


def tfidf_kmeans(texts, k):
    X = TfidfVectorizer(min_df=2, max_df=0.6, max_features=50000).fit_transform(texts)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(X)


def lsa_kmeans(texts, k, n_comp=100):
    X = TfidfVectorizer(min_df=2, max_df=0.6, max_features=50000).fit_transform(texts)
    Z = make_pipeline(TruncatedSVD(n_components=min(n_comp, X.shape[1] - 1),
                                   random_state=SEED),
                      Normalizer(copy=False)).fit_transform(X)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(Z)


def hdp_topics(texts):
    """HDP decides its own number of topics; doc -> argmax topic = its cluster."""
    from gensim import corpora
    from gensim.models import HdpModel
    tokenised = [t.split() for t in texts]
    dic = corpora.Dictionary(tokenised)
    dic.filter_extremes(no_below=2, no_above=0.6)
    corpus = [dic.doc2bow(d) for d in tokenised]
    hdp = HdpModel(corpus, dic, random_state=SEED)
    labels = []
    for bow in corpus:
        dist = hdp[bow]
        labels.append(max(dist, key=lambda x: x[1])[0] if dist else -1)
    return np.array(labels)


def sbert_kmeans(raw_texts, k, model='all-MiniLM-L6-v2'):
    """Embed the ORIGINAL (unstemmed) text, then cluster."""
    from sentence_transformers import SentenceTransformer
    emb = SentenceTransformer(model).encode(
        list(raw_texts), batch_size=64, show_progress_bar=True,
        normalize_embeddings=True)
    return KMeans(n_clusters=k, n_init=10, random_state=SEED).fit_predict(emb)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', default='data/cve.csv')
    ap.add_argument('--methods', nargs='+',
                    default=['tfidf', 'lsa', 'hdp', 'sbert'],
                    choices=['tfidf', 'lsa', 'hdp', 'sbert'])
    ap.add_argument('--k', type=int, default=0,
                    help='clusters for kmeans (0 = number of distinct CWEs)')
    ap.add_argument('--sample', type=int, default=0, help='subsample N docs for speed')
    ap.add_argument('--min-class', type=int, default=0,
                    help='drop CWEs with fewer than this many CVEs')
    ap.add_argument('--outdir', default='results')
    args = ap.parse_args()

    df = load(args.input, args.sample, args.min_class)
    y = df['cwe'].values
    k = args.k or df['cwe'].nunique()
    proc = df['text'].map(lib.preprocess).values          # stemmed, cleaned
    raw = df['text'].values                                # for sbert

    rows = []
    for m in args.methods:
        t0 = time.time()
        print(f"\n=== {m}  (k={k}) ===")
        try:
            if m == 'tfidf':   clusters = tfidf_kmeans(proc, k)
            elif m == 'lsa':   clusters = lsa_kmeans(proc, k)
            elif m == 'hdp':   clusters = hdp_topics(proc)
            elif m == 'sbert': clusters = sbert_kmeans(raw, k)
        except ImportError as e:
            print(f"  skipped ({e}); install the dependency and re-run.")
            continue
        res = lib.evaluate(y, clusters)
        res['method'] = {'tfidf': 'TF-IDF + k-means', 'lsa': 'LSA + k-means',
                         'hdp': 'HDP', 'sbert': 'SBERT + k-means'}[m]
        res['seconds'] = round(time.time() - t0, 1)
        rows.append(res)
        print("  " + "  ".join(f"{key}={res[key]}" for key in
              ('F1_macro', 'F1_weighted', 'ARI', 'NMI', 'seconds')))

    if not rows:
        sys.exit("No methods ran.")

    os.makedirs(args.outdir, exist_ok=True)
    cols = ['method', 'n_docs', 'n_clusters', 'P_macro', 'R_macro',
            'F1_macro', 'F1_weighted', 'ARI', 'NMI', 'Vmeasure', 'seconds']
    out = pd.DataFrame(rows)[cols]
    # placeholder row to fill from your existing Table 4 (same evaluate() rule!)
    csv_path = os.path.join(args.outdir, 'comparison.csv')
    out.to_csv(csv_path, index=False)

    tex_df = out[['method', 'F1_macro', 'F1_weighted', 'ARI', 'NMI']].copy()
    for c in ['F1_macro', 'F1_weighted', 'ARI', 'NMI']:
        tex_df[c] = tex_df[c].map(lambda v: f'{v:.3f}')
    # placeholder row -- fill from your Table 4 (re-scored with lib.evaluate)
    tex_df.loc[len(tex_df)] = ['Sentence-LDA (this work)', '--', '--', '--', '--']
    tex_df.columns = ['Method', 'F1 (macro)', 'F1 (weighted)', 'ARI', 'NMI']
    tex = tex_df.to_latex(
        index=False, escape=True,
        caption='Comparison of unsupervised methods on the CVE corpus '
                '(concept--CWE alignment, same evaluation as Experiment~1). '
                'Fill the Sentence-LDA row from Table~4, re-scored with the '
                'automatic mapping so every row uses one rule.',
        label='tab:baselines')
    with open(os.path.join(args.outdir, 'comparison.tex'), 'w') as fh:
        fh.write(tex)
    print(f"\nWrote {csv_path} and comparison.tex")
    print(out.to_string(index=False))


if __name__ == '__main__':
    main()
