#!/usr/bin/env python3
"""
Build CVEsSummaries.csv from the zipped NVD 2.0 feeds in cves/.

Output schema is a superset of what experiment_1/SLDA_code CVEsExtractor.java
reads, so that Java class runs unmodified:
  - "Vulnerability" : SecOnt URI, exactly the form CVEsExtractor strips
  - "Summary"       : English description

plus the ground-truth / provenance columns the baseline comparison needs.
Nothing in experiment_1/ or experiment_2/ is touched.
"""
import zipfile, json, glob, csv, re, sys, os, collections

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SECONT = "http://encs.concordia.ca/ontologies/2015/Secont#"
OUT = os.path.join(ROOT, "CVEsSummaries.csv")
FEEDS = os.path.join(ROOT, "cves")

COLS = ["Vulnerability", "CVE_ID", "Year", "Published", "LastModified",
        "VulnStatus", "CWE", "CWE_All", "CWE_Source", "Summary"]

_ws = re.compile(r"\s+")
_cwe = re.compile(r"^CWE-\d+$")


def pick_cwe(weaknesses):
    """Return (label, all_pipe_joined, source).

    label : single ground-truth CWE. Real CWE-<n> preferred, Primary before
            Secondary. NVD's 'NVD-CWE-noinfo' / 'NVD-CWE-Other' placeholders
            collapse to 'NA', matching the NA class used in Experiment 1.
    """
    prim, sec, seen = [], [], []
    for w in weaknesses or []:
        typ = w.get("type")
        for d in w.get("description", []):
            if d.get("lang") != "en":
                continue
            v = (d.get("value") or "").strip()
            if not v:
                continue
            if v not in seen:
                seen.append(v)
            (prim if typ == "Primary" else sec).append(v)

    for bucket, src in ((prim, "Primary"), (sec, "Secondary")):
        for v in bucket:
            if _cwe.match(v):
                return v, "|".join(seen), src
    # only placeholders (NVD-CWE-noinfo / NVD-CWE-Other) or nothing at all
    return "NA", "|".join(seen), ("Placeholder" if seen else "None")


def main():
    files = sorted(glob.glob(os.path.join(FEEDS, "*.json.zip")))
    if not files:
        sys.exit(f"No *.json.zip feeds found under {FEEDS}")

    rows = {}
    for p in files:
        z = zipfile.ZipFile(p)
        data = json.loads(z.read(z.namelist()[0]))
        for item in data.get("vulnerabilities", []):
            c = item.get("cve", {})
            cid = c.get("id")
            if not cid:
                continue
            summary = ""
            for d in c.get("descriptions", []):
                if d.get("lang") == "en":
                    summary = _ws.sub(" ", (d.get("value") or "")).strip()
                    break
            if not summary:
                continue
            cwe, cwe_all, src = pick_cwe(c.get("weaknesses"))
            m = re.match(r"CVE-(\d{4})-", cid)
            rows[cid] = {
                "Vulnerability": SECONT + cid,
                "CVE_ID":        cid,
                "Year":          m.group(1) if m else "",
                "Published":     c.get("published", ""),
                "LastModified":  c.get("lastModified", ""),
                "VulnStatus":    c.get("vulnStatus", ""),
                "CWE":           cwe,
                "CWE_All":       cwe_all,
                "CWE_Source":    src,
                "Summary":       summary,
            }
        print(f"  {p} -> running total {len(rows)}")

    def key(cid):
        m = re.match(r"CVE-(\d{4})-(\d+)", cid)
        return (int(m.group(1)), int(m.group(2))) if m else (9999, 0)

    with open(OUT, "w", newline="", encoding="utf-8") as fh:
        w = csv.DictWriter(fh, fieldnames=COLS, quoting=csv.QUOTE_MINIMAL,
                           lineterminator="\n")
        w.writeheader()
        for cid in sorted(rows, key=key):
            w.writerow(rows[cid])

    print(f"\nWrote {OUT}: {len(rows)} rows, {len(COLS)} columns")
    st = collections.Counter(r["CWE"] for r in rows.values())
    rej = sum(1 for r in rows.values() if r["VulnStatus"] == "Rejected")
    print(f"Rejected-status rows: {rej}")
    print(f"Distinct CWE labels : {len(st)}   (NA = {st['NA']})")
    print("Top 15 CWE:", st.most_common(15))


if __name__ == "__main__":
    main()
