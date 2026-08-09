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
import csv
import os
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
    """lowercase -> tokenize alpha -> drop stopwords/len<3 -> Porter stem.

    NOTE: this is the toolkit's own generic preprocessing. It does NOT match
    Experiment 1 (different tokeniser, different stopword list, no dictionary
    gate, and it strips digits). Use preprocess_exp1() for a like-for-like
    comparison; this is kept only so older invocations keep working.
    """
    toks = _WORD.findall(text.lower())
    toks = [t for t in toks if t not in ENGLISH_STOP_WORDS and len(t) > 2]
    if stem:
        toks = [_stem(t) for t in toks]
    return " ".join(toks)


# --------------------------------------------------------------------------
# Experiment-1-faithful preprocessing
# --------------------------------------------------------------------------
# Reconstructed from experiment_1/SLDA_code/src/util/{BagOfSentencesGenertor,
# GeneratingWordList}.java. Pipeline, in order:
#
#   1. lowercase
#   2. tokenise by splitting on [^a-zA-Z0-9]+   (digits are KEPT)
#   3. drop tokens in experiment_1's StopWords.txt (463 words, not sklearn's)
#   4. keep only tokens in Dictionary.txt | ISdictionary.txt -- the
#      "vocabulary gate"; anything else is silently dropped
#   5. Porter stem, original Martin algorithm (matches the Java stemmer:
#      array -> arrai, display -> displai)
#
# There is NO minimum token length: the Java code tests only !term.isEmpty().
# Sentence boundaries (tabs in BagOfSentences.txt) are dropped, since every
# baseline is bag-of-words; only SLDA itself consumes sentence structure.
#
# Validation: stemming the gate reproduces 502 of the 505 distinct terms in
# experiment_1/results/SLDA-T40-...-ProbWords.csv (99.4%). The 3 stragglers
# -- sql, exe, database -- are in the published model vocabulary but absent
# from the shipped dictionaries, so they are recovered from ProbWords by
# default. Without that, "sql" would be stripped from the corpus the
# baselines see even though Experiment 1's own run had it.

_ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXP1_DATA = os.path.join(_ROOT, 'experiment_1', 'SLDA_code', 'data')
EXP1_PROBWORDS = os.path.join(_ROOT, 'experiment_1', 'results',
                              'SLDA-T40-A0.01-B0.01-I1000-ProbWords.csv')

_SPLIT = re.compile(r"[^a-zA-Z0-9]+")
_exp1_cache = {}


def _read_words(path):
    with open(path, encoding='utf-8', errors='replace') as fh:
        return {ln.strip().lower() for ln in fh if ln.strip()}


def load_exp1_resources(data_dir=EXP1_DATA, probwords=EXP1_PROBWORDS):
    """Load (stopwords, gate, stem, extra) for Experiment-1 preprocessing.

    Read-only: nothing under experiment_1/ is written. Cached per path pair.
    """
    key = (data_dir, probwords)
    if key in _exp1_cache:
        return _exp1_cache[key]

    stop = _read_words(os.path.join(data_dir, 'StopWords.txt'))
    gate = (_read_words(os.path.join(data_dir, 'Dictionary.txt')) |
            _read_words(os.path.join(data_dir, 'ISdictionary.txt')))

    try:
        from nltk.stem import PorterStemmer
        stem = PorterStemmer(mode=PorterStemmer.ORIGINAL_ALGORITHM).stem
    except Exception:                                # pragma: no cover
        raise RuntimeError("nltk is required for Experiment-1 preprocessing; "
                           "pip install nltk")

    # Terms the original model used that the shipped dictionaries lack.
    extra = set()
    if probwords and os.path.exists(probwords):
        with open(probwords, encoding='utf-8', errors='replace') as fh:
            for i, row in enumerate(csv.reader(fh)):
                if i == 0:
                    continue                          # "Topic 0,Topic 1,..."
                for cell in row:
                    t = cell.split(' ')[0].strip().lower()
                    if t:
                        extra.add(t)
        extra -= {stem(w) for w in gate}

    res = (stop, gate, stem, extra)
    _exp1_cache[key] = res
    return res


def preprocess_exp1(text, resources=None):
    """Replicate Experiment 1's preprocessing. Returns a space-joined string."""
    stop, gate, stem, extra = resources or load_exp1_resources()
    out = []
    for tok in _SPLIT.split(text.lower()):
        if not tok or tok in stop:
            continue
        if tok in gate:
            out.append(stem(tok))
        else:
            s = stem(tok)
            if s in extra:                            # sql, exe, database
                out.append(s)
    return " ".join(out)


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
    return per_class_f1_from_pred(y_true,
                                  map_clusters_to_labels(clusters,
                                                         np.asarray(y_true)))


def per_class_f1_from_pred(y_true, y_pred):
    """Per-CWE P/R/F1 for predictions that are already CWE labels.

    Used for Experiment 1's MANUAL topic->CWE mapping, where the prediction is
    given directly rather than derived by majority vote.

    Note on convention: P is precision = TP/(TP+FP) with the denominator over
    *predicted* members of the class, and R is recall = TP/(TP+FN) over *true*
    members. experiment_1/results/topicsEvaluation.csv labels these two columns
    the other way round; F1 is unaffected.
    """
    y_true = np.asarray(y_true)
    y_pred = np.asarray(y_pred)
    labels = sorted(set(y_true) | set(y_pred))
    p, r, f, s = precision_recall_fscore_support(
        y_true, y_pred, labels=labels, zero_division=0)
    return [{'cwe': lab, 'P': round(p[i], 4), 'R': round(r[i], 4),
             'F1': round(f[i], 4), 'support': int(s[i])}
            for i, lab in enumerate(labels)]
