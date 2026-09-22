"""Manifest and documentation-impact tests. Run: python scripts/test_doc_manifest.py"""

import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from doclib import glob_to_re, impact_errors, matches, repo_root, validate_manifest


class ManifestTests(unittest.TestCase):
    def test_manifest_matches_repo(self) -> None:
        errors = validate_manifest(repo_root())
        self.assertEqual(errors, [], "\n".join(errors))

    def test_glob_does_not_match_target_by_pattern_intent(self) -> None:
        self.assertTrue(matches("src/main/resources/types.yml", "src/main/resources/types.yml"))
        self.assertFalse(matches("src/main/java/net/tfminecraft/cooking/item/**", "target/classes/types.yml"))
        self.assertTrue(
            matches(
                "src/main/java/net/tfminecraft/cooking/carve/**",
                "src/main/java/net/tfminecraft/cooking/carve/CarveHandler.java",
            )
        )
        self.assertIsNotNone(glob_to_re("target/**").match("target/classes/config.yml"))

    def test_config_change_requires_an_owner_doc(self) -> None:
        errors = impact_errors(
            repo_root(),
            {"src/main/resources/husbandry.yml"},
        )
        self.assertTrue(any("husbandry.md" in error for error in errors))

    def test_owner_doc_in_the_same_change_passes(self) -> None:
        errors = impact_errors(
            repo_root(),
            {
                "src/main/resources/husbandry.yml",
                "docs/systems/husbandry.md",
            },
        )
        self.assertEqual(errors, [])

    def test_unmapped_comment_file_passes(self) -> None:
        errors = impact_errors(repo_root(), {"README.md"})
        self.assertEqual(errors, [])

    def test_waiver_must_change_and_explain(self) -> None:
        root = repo_root()
        waiver = root / "docs" / "impact-waiver.md"
        original = waiver.read_text(encoding="utf-8")
        try:
            self.assertNotEqual(
                impact_errors(root, {"src/main/resources/crops.yml"}),
                [],
            )
            waiver.write_text(
                "docs-impact: none\nreason: comment only in a crop loader\n",
                encoding="utf-8",
            )
            self.assertEqual(
                impact_errors(
                    root,
                    {"src/main/resources/crops.yml", "docs/impact-waiver.md"},
                ),
                [],
            )
            waiver.write_text("docs-impact: none\nreason: none\n", encoding="utf-8")
            self.assertNotEqual(
                impact_errors(
                    root,
                    {"src/main/resources/crops.yml", "docs/impact-waiver.md"},
                ),
                [],
            )
        finally:
            waiver.write_text(original, encoding="utf-8")

    def test_generated_path_is_rejected(self) -> None:
        errors = impact_errors(repo_root(), {"target/classes/config.yml"})
        self.assertTrue(any("generated" in error for error in errors))


class IsolatedTests(unittest.TestCase):
    def test_missing_pattern_is_reported(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            docs = root / "docs"
            docs.mkdir()
            (docs / "only.md").write_text("ok", encoding="utf-8")
            resources = root / "src" / "main" / "resources"
            resources.mkdir(parents=True)
            (resources / "types.yml").write_text("a: 1\n", encoding="utf-8")
            (resources / "cookware.yml").write_text("old: true\n", encoding="utf-8")
            (docs / "manifest.json").write_text(
                """
                {
                  "generated_excludes": ["target/**"],
                  "known_unloaded": ["src/main/resources/cookware.yml"],
                  "satisfy": "any-owner",
                  "documents": [
                    {
                      "id": "missing",
                      "document": "docs/only.md",
                      "paths": ["src/main/java/no/such/**"]
                    }
                  ]
                }
                """,
                encoding="utf-8",
            )
            errors = validate_manifest(root)
            self.assertTrue(any("matches nothing" in error for error in errors))
            self.assertTrue(any("types.yml" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
