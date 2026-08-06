#!/usr/bin/env python3
"""
parse_nvd.py  --  Turn raw NVD feeds into a flat CSV for the baseline experiment.

Input : a file OR directory of NVD feeds. Supports the legacy NVD XML schema
        (vuln:/ nvd namespaces) and the NVD JSON feeds (1.1). Namespaces are
        matched by *local name*, so minor schema differences are tolerated.
Output: CSV with columns  cve_id, cwe, text
        (only entries that carry a CWE are written by default -- that is the
         labelled set your F-measure is computed against.)

Usage:
    python parse_nvd.py --input path/to/nvd_xml_dir --out data/cve.csv
    python parse_nvd.py --input nvdcve-2.0-2015.xml --out data/cve.csv
    python parse_nvd.py --input nvd_json_dir --out data/cve.csv --keep-unlabelled
"""
import argparse, csv, json, os, sys, glob, xml.etree.ElementTree as ET


def _local(tag):
    return tag.rsplit('}', 1)[-1] if '}' in tag else tag


def parse_xml(path):
    """Yield (cve_id, cwe, summary) from one legacy NVD XML file."""
    try:
        tree = ET.parse(path)
    except ET.ParseError as e:
        print(f"  ! XML parse error in {os.path.basename(path)}: {e}", file=sys.stderr)
        return
    root = tree.getroot()
    for entry in root.iter():
        if _local(entry.tag) != 'entry':
            continue
        cve_id, cwe, summary = None, None, None
        # id may be an attribute on <entry> or a child <cve-id>
        cve_id = entry.get('id')
        for child in entry.iter():
            ln = _local(child.tag)
            if ln == 'cve-id' and child.text:
                cve_id = child.text.strip()
            elif ln == 'cwe':
                cwe = child.get('id') or (child.text.strip() if child.text else None)
            elif ln == 'summary' and child.text:
                summary = child.text.strip()
        if cve_id and summary:
            yield cve_id, cwe, summary


def parse_json(path):
    """Yield (cve_id, cwe, description) from an NVD JSON 1.1 feed."""
    with open(path, encoding='utf-8') as fh:
        data = json.load(fh)
    for item in data.get('CVE_Items', []):
        cve = item.get('cve', {})
        cve_id = cve.get('CVE_data_meta', {}).get('ID')
        # first CWE found
        cwe = None
        for pt in cve.get('problemtype', {}).get('problemtype_data', []):
            for d in pt.get('description', []):
                if d.get('value', '').startswith('CWE-'):
                    cwe = d['value']; break
            if cwe: break
        # english description
        desc = None
        for d in cve.get('description', {}).get('description_data', []):
            if d.get('lang') == 'en':
                desc = d.get('value'); break
        if cve_id and desc:
            yield cve_id, cwe, desc


def iter_files(inp):
    if os.path.isfile(inp):
        return [inp]
    files = sorted(glob.glob(os.path.join(inp, '*.xml')) +
                   glob.glob(os.path.join(inp, '*.json')))
    if not files:
        sys.exit(f"No .xml/.json files found under {inp}")
    return files


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--input', required=True, help='NVD file or directory')
    ap.add_argument('--out', default='data/cve.csv')
    ap.add_argument('--keep-unlabelled', action='store_true',
                    help='also write CVEs that have no CWE (default: skip)')
    args = ap.parse_args()

    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    n_total = n_written = n_labelled = 0
    seen = set()
    with open(args.out, 'w', newline='', encoding='utf-8') as out:
        w = csv.writer(out)
        w.writerow(['cve_id', 'cwe', 'text'])
        for path in iter_files(args.input):
            parser = parse_json if path.endswith('.json') else parse_xml
            for cve_id, cwe, text in parser(path):
                n_total += 1
                if cve_id in seen:
                    continue
                seen.add(cve_id)
                if cwe:
                    n_labelled += 1
                if cwe or args.keep_unlabelled:
                    w.writerow([cve_id, cwe or '', ' '.join(text.split())])
                    n_written += 1
    print(f"Parsed {n_total} entries | {n_labelled} with a CWE | wrote {n_written} rows -> {args.out}")


if __name__ == '__main__':
    main()
