# Update docs with code

1. Open `docs/manifest.json` and list every document whose `paths` match your files.
2. Read those documents before editing.
3. Change the doc in the same commit as the behavior.
4. If the change cannot affect behavior (format, comment, rename with no contract change), edit `docs/impact-waiver.md` in that same commit:

```text
docs-impact: none
reason: Reformatted FoodItem imports only.
```

The checker ignores a waiver that was not part of the diff. Replace the reason every time. Do not leave a stale waiver as a permanent bypass.

5. Run:

```text
python scripts/test_doc_manifest.py
python scripts/check-links.py
python scripts/check-doc-impact.py
```
