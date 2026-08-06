"""
lib.py  --  preprocessing + evaluation shared by run_baselines.py

Evaluation mirrors Experiment 1 in the paper: each unsupervised method produces
a hard cluster per CVE; every cluster is mapped to the CWE that is most frequent
among its members (the automatic analogue of the manual concept->CWE mapping);
predictions are then scored against the true CWE with precision / recall / F1.
We also report mapping-free agreement scores (ARI, NMI, V-measure) because they
do not depend on the cluster->label mapping, so they are unaffected by how
clusters happen to be assigned to labels.
"""
import re
from collections import Counter
import numpy as np
from sklearn.metrics import (precision_recall_fscore_support,
                             adjusted_rand_score, normalized_mutual_info_score,
                             v_measure_score)
from sklearn.feature_extraction.text import ENGLISH_STOP_WORDS

_WORD = re.compile(r"[a-z]+")

# Porter stemmer if nltk is present; otherwise fall back to no stemming.
try:
    from nltk.stem import PorterStemmer
    _stem = PorterStemmer().stem
except Exception:                                    # pragma: no cover
    _stem = lambda w: w
    print("[lib] nltk not found -> running WITHOUT stemming "
          "(pip install nltk to match the paper's pipeline).")


def preprocess(text, stem=True):
    """lowercase -> tokenize alpha -> drop stopwords/len<3 -> Porter stem."""
    toks = _WORD.findall(text.lower())
    toks = [t for t in toks if t not in ENGLISH_STOP_WORDS and len(t) > 2]
    if stem:
        toks = [_stem(t) for t in toks]
    return " ".join(toks)


def map_clusters_to_labels(clusters, y_true):
    """Majority-vote mapping: cluster id -> most frequent true label in it."""
    mapping = {}
    for c in np.unique(clusters):
        members = y_true[clusters == c]
        mapping[c] = Counter(members).most_common(1)[0][0]
    return np.array([mapping[c] for c in clusters])


def evaluate(y_true, clusters):
    """Return a dict of comparable metrics for one method."""
    y_true = np.asarray(y_true)
    clusters = np.asarray(clusters)
    y_pred = map_clusters_to_labels(clusters, y_true)

    p_macro, r_macro, f_macro, _ = precision_recall_fscore_support(
        y_true, y_pred, average='macro', zero_division=0)
    p_w, r_w, f_w, _ = precision_recall_fscore_support(
        y_true, y_pred, average='weighted', zero_division=0)

    return {
        'n_docs': len(y_true),
        'n_clusters': len(np.unique(clusters)),
        'P_macro': round(p_macro, 4), 'R_macro': round(r_macro, 4),
        'F1_macro': round(f_macro, 4),
        'F1_weighted': round(f_w, 4),
        'ARI': round(adjusted_rand_score(y_true, clusters), 4),
        'NMI': round(normalized_mutual_info_score(y_true, clusters), 4),
        'Vmeasure': round(v_measure_score(y_true, clusters), 4),
    }


def per_class_f1(y_true, clusters):
    """Per-CWE P/R/F1 table (mirrors your Table 4), as a list of dict rows."""
    y_true = np.asarray(y_true)
    y_pred = map_clusters_to_labels(clusters, y_true)
    labels = sorted(set(y_true))
    p, r, f, s = precision_recall_fscore_support(
        y_true, y_pred, labels=labels, zero_division=0)
    return [{'cwe': lab, 'P': round(p[i], 4), 'R': round(r[i], 4),
             'F1': round(f[i], 4), 'support': int(s[i])}
            for i, lab in enumerate(labels)]
