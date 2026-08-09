#!/usr/bin/env python3
"""
run_baselines_exp2.py  --  Baseline comparison for Experiment 2
                           (tagging bug reports with cybersecurity concepts).

Experiment 2 has NO ground-truth security labels: no CWE, no security/non-
security flag. Only 23 CVE identifiers appear anywhere in the 15 projects'
RDF dumps (12 unique), against 11,892 tagged bug reports -- far too few to
score against. So this script deliberately does NOT invent labels. It runs a
label-free comparison, which is what the available data can support:

  * topic coherence (NPMI) -- the standard label-free quality measure for
    topic models; higher is better
  * partition agreement (ARI / NMI) against Seeded-LDA -- shows whether
    seeding actually changes the clustering relative to plain alternatives
  * seed recovery -- how much of each topic's top-N mass is the seed terms
    it was seeded with, which is what the paper's cosine-similarity
    "distinctness" measure is really testing

Corpus: reconstructed EXACTLY from the Seeded-LDA inputs
(<proj>_corpus.txt = per-token document index, <proj>_tokens.txt = per-token
vocabulary index, <proj>_vocabulary.txt), so every method sees byte-identical
input to what Seeded-LDA consumed. experiment_2/ is read-only.

Usage:
    python run_baselines_exp2.py
    python run_baselines_exp2.py --projects hadoop-common cxf --methods lda tfidf
"""
import argparse, csv, math, os, sys, time
from collections import Counter, defaultdict

import numpy as np
import pandas as pd
from sklearn.cluster import KMeans
from sklearn.decomposition import LatentDirichletAllocation, NMF, TruncatedSVD
from sklearn.feature_extraction.text import TfidfVectorizer, CountVectorizer
from sklearn.metrics import adjusted_rand_score, normalized_mutual_info_score
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import Normalizer

SEED = 42
K = 6                       # Seeded-LDA used one topic per seeded concept

PROJECTS = ['activemq', 'ambari', 'camel', 'cxf', 'felix', 'hadoop-common',
            'hbase', 'hive', 'jackrabbit-oak', 'karaf', 'pdfbox', 'sling',
            'spark', 'stanbol', 'tika']

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
IN = os.path.join(ROOT, 'experiment_2', 'SeededLDA_inputdata')
OUT_P = os.path.join(ROOT, 'experiment_2', 'SeededLDA_outputdata_processed')
OUT_R = os.path.join(ROOT, 'experiment_2', 'SeededLDA_outputdata_notprocessed')

LABELS = {'slda': 'Seeded-LDA (this work)', 'lda': 'LDA (unseeded)',
          'nmf': 'NMF', 'tfidf': 'TF-IDF + k-means', 'lsa': 'LSA + k-means',
          'sbert': 'SBERT + k-means'}

# Table 2 of the manuscript: the six concepts and their representative tokens,
# which are the seed sets fed to Seeded-LDA. Topic index i corresponds to the
# i-th seed set, confirmed against conceptsVStopics.xlsx (whose argmax diagonal
# is identical across all 15 projects) and against the manuscript's own
# statement that T4 <-> C16 with tokens "file, user, directory, local,
# authentication, change, access".
SEED_SETS = [
    ('C10', 'Resource Management',
     'service cause denial crash server crafted packet cisco function vulnerability'),
    ('C14', 'Cross-Site Scripting',
     'web remote site script scripting cross xss vectors index'),
    ('C11', 'Buffer Overflow',
     'random execute overlap code buffer overflow memory stack string allows'),
    ('C16', 'Access Privileges',
     'obtain information password access users computer local file authentication user'),
    ('C03', 'Authentication Abuse',
     'ssl middle spoof verify certificates sensitive application arbitrary subject name'),
    ('C08', 'SQL Injection',
     'sql inject database oracle injection query select update write parameter'),
]


# --------------------------------------------------------------------------
# corpus reconstruction
# --------------------------------------------------------------------------
def load_project(proj):
    """Rebuild the exact documents Seeded-LDA saw, plus its own assignments."""
    d = os.path.join(IN, proj)
    rd = lambda f: [ln.strip() for ln in
                    open(os.path.join(d, f), encoding='utf-8', errors='replace')
                    if ln.strip()]
    doc_idx = [int(x) for x in rd(f'{proj}_corpus.txt')]
    tok_idx = [int(x) for x in rd(f'{proj}_tokens.txt')]
    vocab = rd(f'{proj}_vocabulary.txt')
    names = rd(f'{proj}_documentsListNames.txt')

    docs = defaultdict(list)
    for di, ti in zip(doc_idx, tok_idx):
        docs[di].append(vocab[ti - 1])
    texts = [' '.join(docs[i + 1]) for i in range(len(names))]

    slda = []
    with open(os.path.join(OUT_P, f'{proj}_results', 'docBestTopic.csv'),
              encoding='utf-8') as fh:
        rows = {r[1]: int(r[0]) for r in csv.reader(fh) if len(r) >= 2}
    slda = np.array([rows.get(n, -1) for n in names])
    return texts, names, vocab, slda


def seeded_topic_words(proj, topn=10):
    """Top-N words per Seeded-LDA topic, parsed from the run log."""
    path = os.path.join(OUT_R, f'{proj}_results', f'{proj}.txt')
    if not os.path.exists(path):
        return None
    topics, cur = [], None
    for ln in open(path, encoding='utf-8', errors='replace'):
        ln = ln.rstrip('\n')
        if ln.startswith('Topic:'):
            cur = []
            topics.append(cur)
        elif cur is not None and '-->' in ln:
            if len(cur) < topn:
                cur.append(ln.split('-->')[0].strip())
    return topics or None


# --------------------------------------------------------------------------
# metrics
# --------------------------------------------------------------------------
def npmi_coherence(topics, texts, eps=1e-12):
    """Mean pairwise NPMI over each topic's top words. Range [-1, 1]."""
    docsets = [set(t.split()) for t in texts]
    N = len(docsets)
    df = Counter()
    for s in docsets:
        df.update(s)
    scores = []
    for words in topics:
        w = [x for x in words if df[x] > 0]
        pairs, tot = 0, 0.0
        for i in range(len(w)):
            for j in range(i + 1, len(w)):
                a, b = w[i], w[j]
                co = sum(1 for s in docsets if a in s and b in s)
                pa, pb = df[a] / N, df[b] / N
                pab = co / N
                if pab <= 0:
                    tot += -1.0
                else:
                    tot += math.log(pab / (pa * pb) + eps) / -math.log(pab + eps)
                pairs += 1
        if pairs:
            scores.append(tot / pairs)
    return float(np.mean(scores)) if scores else float('nan')


def cluster_top_words(labels, texts, vocab_matrix, feature_names, topn=10):
    """Top-N terms per cluster by mean TF-IDF -- 'topic words' for k-means."""
    out = []
    for c in sorted(set(labels)):
        rows = np.where(labels == c)[0]
        if len(rows) == 0:
            out.append([])
            continue
        mean = np.asarray(vocab_matrix[rows].mean(axis=0)).ravel()
        idx = mean.argsort()[::-1][:topn]
        out.append([feature_names[i] for i in idx])
    return out


# --------------------------------------------------------------------------
# methods
# --------------------------------------------------------------------------
def run_methods(texts, methods, k=K):
    cv = CountVectorizer(min_df=2)
    X_cnt = cv.fit_transform(texts)
    tv = TfidfVectorizer(min_df=2)
    X_tfidf = tv.fit_transform(texts)
    res = {}

    if 'lda' in methods:
        m = LatentDirichletAllocation(n_components=k, random_state=SEED,
                                      learning_method='batch', max_iter=100)
        res['lda'] = (m.fit_transform(X_cnt).argmax(1), X_cnt, cv)
    if 'nmf' in methods:
        m = NMF(n_components=k, random_state=SEED, init='nndsvda', max_iter=400)
        res['nmf'] = (m.fit_transform(X_tfidf).argmax(1), X_tfidf, tv)
    if 'tfidf' in methods:
        res['tfidf'] = (KMeans(k, n_init=10, random_state=SEED)
                        .fit_predict(X_tfidf), X_tfidf, tv)
    if 'lsa' in methods:
        Z = make_pipeline(
            TruncatedSVD(min(100, X_tfidf.shape[1] - 1), random_state=SEED),
            Normalizer(copy=False)).fit_transform(X_tfidf)
        res['lsa'] = (KMeans(k, n_init=10, random_state=SEED)
                      .fit_predict(Z), X_tfidf, tv)
    if 'sbert' in methods:
        from sentence_transformers import SentenceTransformer
        emb = SentenceTransformer('all-MiniLM-L6-v2').encode(
            list(texts), batch_size=64, normalize_embeddings=True,
            show_progress_bar=False)
        res['sbert'] = (KMeans(k, n_init=10, random_state=SEED)
                        .fit_predict(emb), X_tfidf, tv)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--projects', nargs='+', default=PROJECTS)
    ap.add_argument('--methods', nargs='+',
                    default=['lda', 'nmf', 'tfidf', 'lsa', 'sbert'],
                    choices=['lda', 'nmf', 'tfidf', 'lsa', 'sbert'])
    ap.add_argument('--outdir',
                    default=os.path.join(ROOT, 'results_baselines', 'exp2'))
    args = ap.parse_args()

    rows = []
    for proj in args.projects:
        t0 = time.time()
        texts, names, vocab, slda = load_project(proj)
        print(f"\n=== {proj}  ({len(texts)} docs, {len(vocab)} vocab) ===")

        st = seeded_topic_words(proj)
        rows.append({'project': proj, 'method': LABELS['slda'],
                     'n_docs': len(texts), 'n_clusters': len(set(slda)),
                     'NPMI': round(npmi_coherence(st, texts), 4) if st else np.nan,
                     'ARI_vs_SLDA': 1.0, 'NMI_vs_SLDA': 1.0})

        out = run_methods(texts, args.methods)
        for m, (lab, mat, vec) in out.items():
            tw = cluster_top_words(lab, texts, mat, vec.get_feature_names_out())
            rows.append({
                'project': proj, 'method': LABELS[m], 'n_docs': len(texts),
                'n_clusters': len(set(lab)),
                'NPMI': round(npmi_coherence(tw, texts), 4),
                'ARI_vs_SLDA': round(adjusted_rand_score(slda, lab), 4),
                'NMI_vs_SLDA': round(normalized_mutual_info_score(slda, lab), 4),
            })
        print(f"  {time.time() - t0:.0f}s")

    df = pd.DataFrame(rows)
    os.makedirs(args.outdir, exist_ok=True)
    df.to_csv(os.path.join(args.outdir, 'per_project.csv'), index=False)

    # Seed recovery + tag frequency: does each topic actually contain the seed
    # terms it was seeded with, and how often is it assigned?
    sr, freq = [], Counter()
    ndocs = 0
    for proj in args.projects:
        tw = seeded_topic_words(proj, topn=20)
        if not tw:
            continue
        _, _, _, slda = load_project(proj)
        c = Counter(int(x) for x in slda if x >= 0)
        ndocs += sum(c.values())
        freq.update(c)
        for i, (cid, name, seeds) in enumerate(SEED_SETS):
            if i >= len(tw):
                continue
            hits = sorted(set(tw[i]) & set(seeds.split()))
            sr.append({'project': proj, 'concept': f'{cid} {name}',
                       'seed_terms_in_top20': len(hits),
                       'matched': ' '.join(hits),
                       'docs_assigned': c.get(i, 0)})
    if sr:
        d = pd.DataFrame(sr)
        d.to_csv(os.path.join(args.outdir, 'seed_recovery.csv'), index=False)
        agg2 = (d.groupby('concept')
                 .agg(mean_seed_terms_in_top20=('seed_terms_in_top20', 'mean'),
                      docs_assigned=('docs_assigned', 'sum'))
                 .sort_values('docs_assigned', ascending=False))
        agg2['share_%'] = (100 * agg2.docs_assigned / ndocs).round(1)
        agg2.round(2).to_csv(os.path.join(args.outdir, 'tag_frequency.csv'))
        print("\n--- tag frequency and seed recovery ---")
        print(agg2.round(2).to_string())

    order = [LABELS[m] for m in ('slda', 'lda', 'nmf', 'tfidf', 'lsa', 'sbert')]
    agg = (df.groupby('method')[['NPMI', 'ARI_vs_SLDA', 'NMI_vs_SLDA']]
             .mean().round(4).reindex([o for o in order if o in set(df.method)]))
    agg.insert(0, 'n_projects', df.groupby('method').size())
    agg.to_csv(os.path.join(args.outdir, 'summary.csv'))

    tex = agg.reset_index()[['method', 'NPMI', 'ARI_vs_SLDA']].copy()
    tex.columns = ['Method', 'NPMI coherence', 'ARI vs Seeded-LDA']
    with open(os.path.join(args.outdir, 'summary.tex'), 'w') as fh:
        fh.write(tex.to_latex(
            index=False, escape=True, float_format='%.3f',
            caption='Experiment 2: label-free comparison on the 15 Apache bug-report '
                    'corpora (mean over projects, $K=6$). No security ground truth '
                    'exists for these reports, so coherence and partition agreement '
                    'are reported instead of precision/recall.',
            label='tab:exp2baselines'))

    print("\n" + agg.to_string())
    print(f"\nWrote {args.outdir}/per_project.csv, summary.csv, summary.tex")


if __name__ == '__main__':
    main()
