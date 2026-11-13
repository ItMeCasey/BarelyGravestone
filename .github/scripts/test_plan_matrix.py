#!/usr/bin/env python3
"""Tests for plan_matrix.py. Run from the repository root:

  python -I -m unittest discover -s .github/scripts -p "test_plan_matrix.py"
  python -I .github/scripts/test_plan_matrix.py
"""
import os
import subprocess
import sys
import tempfile
import unittest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, HERE)
import plan_matrix as pm  # noqa: E402

EVERYTHING = set(pm.LAYERS)
PAIR_263 = {"forge-26.3", "neoforge-26.3"}


def layers(files):
    return {layer for layer, _ in pm.pick_for_files(files)}


def commit_exists(sha):
    return subprocess.run(["git", "cat-file", "-e", sha + "^{commit}"], cwd=ROOT, capture_output=True).returncode == 0


def layers_of_commit(sha):
    out = subprocess.run(["git", "diff", "--name-only", sha + "^", sha], cwd=ROOT, capture_output=True, text=True)
    return layers([line for line in out.stdout.splitlines() if line])


class SyntheticFileLists(unittest.TestCase):
    def test_docs_and_status_select_nothing(self):
        self.assertEqual(layers(["PROJECT_STATUS.md", "CHANGELOG.md", "README.md", "docs/frames/a.png", "wiki/index.html",
                                 "branding/icon.png", "curseforge/LISTING.md", "LICENSE", ".gitignore", ".gitattributes"]), set())

    def test_empty_diff_selects_nothing(self):
        self.assertEqual(layers([]), set())

    def test_workflow_and_ci_scripts_select_nothing(self):
        self.assertEqual(layers([".github/workflows/build.yml", ".github/workflows/release.yml", ".github/scripts/plan_matrix.py",
                                 ".github/scripts/publish_curseforge.py", ".github/ISSUE_TEMPLATE/bug.yml"]), set())

    def test_dev_scripts_select_nothing(self):
        self.assertEqual(layers(["scripts/make_assets.py"]), set())

    def test_common_selects_every_layer(self):
        self.assertEqual(layers(["common/src/main/java/A.java"]), EVERYTHING)
        self.assertEqual(layers(["common/src/test/java/ATest.java"]), EVERYTHING)
        self.assertEqual(layers(["common/src/dev/java/Tour.java"]), EVERYTHING)

    def test_version_folder_selects_only_its_two_layers(self):
        self.assertEqual(layers(["version-26.2/src/main/java/A.java"]), {"forge-26.2", "neoforge-26.2"})
        self.assertEqual(layers(["version-1.20.1/src/main/resources/x.json"]), {"forge-1.20.1", "neoforge-1.20.1"})
        self.assertEqual(layers(["version-26.1.2/src/dev/java/A.java"]), {"forge-26.1.2", "neoforge-26.1.2"})

    def test_single_layer_change_selects_exactly_that_layer(self):
        self.assertEqual(layers(["forge-26.2/build.gradle"]), {"forge-26.2"})
        self.assertEqual(layers(["neoforge-26.3/gradle.properties"]), {"neoforge-26.3"})
        self.assertEqual(layers(["neoforge-1.20.1/build.gradle"]), {"neoforge-1.20.1"})
        self.assertEqual(layers(["forge-1.21.1/src/main/java/A.java"]), {"forge-1.21.1"})

    def test_forge_1_20_1_sources_are_also_compiled_into_neoforge_1_20_1(self):
        self.assertEqual(layers(["forge-1.20.1/src/main/java/A.java"]), {"forge-1.20.1", "neoforge-1.20.1"})
        self.assertEqual(layers(["forge-1.20.1/src/main/templates/META-INF/mods.toml"]), {"forge-1.20.1", "neoforge-1.20.1"})
        self.assertEqual(layers(["forge-1.20.1/gradle.properties"]), {"forge-1.20.1"})

    def test_mixed_changes_are_the_union(self):
        self.assertEqual(layers(["PROJECT_STATUS.md", "forge-26.2/build.gradle", "version-26.3/src/main/java/A.java"]),
                         {"forge-26.2", "forge-26.3", "neoforge-26.3"})

    def test_unknown_paths_select_everything(self):
        self.assertEqual(layers(["something-new/file.txt"]), EVERYTHING)
        self.assertEqual(layers(["build.gradle"]), EVERYTHING)
        self.assertEqual(layers(["forge-9.9/src/A.java"]), EVERYTHING)
        self.assertEqual(layers(["docs/a.md", "settings.gradle"]), EVERYTHING)

    def test_windows_separators_and_dot_prefix(self):
        self.assertEqual(layers(["version-26.2\\src\\main\\java\\A.java"]), {"forge-26.2", "neoforge-26.2"})
        self.assertEqual(layers(["./docs/a.md"]), set())

    def test_failed_diff_selects_everything(self):
        self.assertIsNone(pm.changed_files("0123456789abcdef0123456789abcdef01234567", "HEAD"))
        self.assertEqual(pm.pick_for_files(None), pm.ALL)

    def test_matrix_entries_are_the_old_build_matrix(self):
        self.assertEqual(len(pm.ALL), 10)
        self.assertEqual(dict(pm.ALL)["neoforge-1.20.1"], 17)
        self.assertEqual(dict(pm.ALL)["forge-1.21.1"], 21)
        self.assertEqual(dict(pm.ALL)["forge-26.3"], 25)
        self.assertEqual(pm.matrix_json(pm.ALL[:1]), {"include": [{"layer": "neoforge-1.20.1", "java": 17}]})


class RealCommits(unittest.TestCase):
    def check(self, sha, expected):
        if not commit_exists(sha):
            self.skipTest(f"commit {sha} is not in this clone")
        self.assertEqual(layers_of_commit(sha), expected)

    def test_status_only_commit_builds_nothing(self):
        self.check("11047bb", set())  # Status: 2026.11.1 released

    def test_workflow_only_commit_builds_nothing(self):
        self.check("189d9bd", set())  # .github/workflows

    def test_ci_script_only_commit_builds_nothing(self):
        self.check("d7bbb9a", set())  # .github/scripts/publish_curseforge.py

    def test_26_3_layer_builds_only_the_26_3_pair(self):
        self.check("78a5afc", PAIR_263)

    def test_1_20_1_port_builds_only_the_1_20_1_pair(self):
        self.check("d9560fd", {"forge-1.20.1", "neoforge-1.20.1"})

    def test_26_1_2_and_26_2_port_builds_only_those_pairs(self):
        self.check("af32a7b", {"forge-26.1.2", "neoforge-26.1.2", "forge-26.2", "neoforge-26.2"})

    def test_self_test_fix_touching_every_version_folder_builds_everything(self):
        self.check("df50686", EVERYTHING)

class ReleaseMode(unittest.TestCase):
    def make_root(self, versions):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        for layer, version in versions.items():
            os.makedirs(os.path.join(tmp.name, layer))
            with open(os.path.join(tmp.name, layer, "gradle.properties"), "w") as fh:
                fh.write(f"minecraft_version=1\nmod_version={version}\nmod_id=x\n")
        return tmp.name

    def test_only_layers_at_the_tag_version_are_picked(self):
        versions = {layer: "2026.11.6" for layer in pm.LAYERS}
        versions["forge-26.3"] = versions["neoforge-26.3"] = "2026.11.7"
        root = self.make_root(versions)
        picked, version = pm.pick_for_release("v2026.11.7", root)
        self.assertEqual({l for l, _ in picked}, PAIR_263)
        self.assertEqual(version, "2026.11.7")
        picked, _ = pm.pick_for_release("v2026.11.6", root)
        self.assertEqual({l for l, _ in picked}, EVERYTHING - PAIR_263)

    def test_all_layers_at_the_tag_version_picks_everything(self):
        root = self.make_root({layer: "2026.11.7" for layer in pm.LAYERS})
        picked, _ = pm.pick_for_release("v2026.11.7", root)
        self.assertEqual(picked, pm.ALL)

    def test_tag_that_matches_nothing_picks_nothing(self):
        root = self.make_root({layer: "2026.11.7" for layer in pm.LAYERS})
        picked, _ = pm.pick_for_release("v2026.12.1", root)
        self.assertEqual(picked, [])

    def test_manual_run_uses_the_newest_version(self):
        versions = {layer: "2026.11.9" for layer in pm.LAYERS}
        versions["forge-26.2"] = "2026.11.10"
        root = self.make_root(versions)
        picked, version = pm.pick_for_release("main", root)
        self.assertEqual(version, "2026.11.10")
        self.assertEqual([l for l, _ in picked], ["forge-26.2"])

    def test_the_repos_real_versions_are_found(self):
        for layer in pm.LAYERS:
            self.assertRegex(pm.module_version(layer, ROOT) or "", r"^\d+\.\d+\.\d+$", layer)


if __name__ == "__main__":
    unittest.main()
