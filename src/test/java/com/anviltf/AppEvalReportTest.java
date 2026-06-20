package com.anviltf;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppEvalReportTest {
    @TempDir
    Path tempDir;

    @Test
    void writesEvalStatusAndManifestScoreReports() throws Exception {
        Path base = tempDir.resolve("base");
        Path edited = tempDir.resolve("edited");
        writePlanSource(base, false);
        writePlanSource(edited, true);

        Path pack = tempDir.resolve("pack");
        Files.createDirectories(pack);
        Path manifest = pack.resolve("manifest.tsv");
        Files.writeString(manifest, "# name\tedited_world_atf\tmax_source_lines\tmin_expanded_lines\texpected_changed_file\n"
                + "add-loot\t" + edited.resolve("world.atf") + "\t2\t1\tgroups/plan.atf\n");

        Path status = pack.resolve("status.md");
        App.main(new String[]{
                "plan-eval-status-report",
                base.resolve("world.atf").toString(),
                manifest.toString(),
                status.toString()
        });
        String statusText = Files.readString(status);
        assertTrue(statusText.contains("summary: ready=1 missing=0 unedited=0 wrong_file=0 total=1"));
        assertTrue(statusText.contains("| `ready` | `add-loot` |"));
        assertTrue(statusText.contains("`groups/plan.atf`"));

        Path score = pack.resolve("score.md");
        App.main(new String[]{
                "plan-edit-manifest-report",
                base.resolve("world.atf").toString(),
                manifest.toString(),
                score.toString()
        });
        String scoreText = Files.readString(score);
        assertTrue(scoreText.contains("summary: 1/1 edit manifest row(s) passed"));
        assertTrue(scoreText.contains("| `pass` | `add-loot` |"));
        assertTrue(scoreText.contains("plan=0, core=0"));
        assertTrue(scoreText.contains("`groups/plan.atf` pass"));
    }

    @Test
    void evalPackCanPrepareSelfContainedRunDirectory() throws Exception {
        Path base = tempDir.resolve("base");
        writePlanSource(base, false);
        Files.writeString(base.resolve("edit-eval-prompts.md"), "controlled prompt notes\n");
        Files.writeString(base.resolve("edit-manifest.tsv"), "controlled\trows\n");

        Path tasks = tempDir.resolve("tasks.tsv");
        Files.writeString(tasks, "add-loot\tAdd loot\tAdd one loot row"
                + "\tjava -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench "
                + base.resolve("world.atf")
                + "\t2\t1\tgroups/plan.atf\n");

        Path pack = tempDir.resolve("pack");
        Path handoff = pack.resolve("handoffs/add-loot");
        Path handoffSource = handoff.resolve("source");
        App.main(new String[]{
                "plan-eval-pack",
                base.resolve("world.atf").toString(),
                tasks.toString(),
                pack.toString(),
                "--run-prefix", "tiny",
                "--independent",
                "--model-surface", "fixture-model",
                "--operator", "test",
                "--date", "2026-06-13",
                "--independence-notes", "fixture edited after pack generation",
                "--isolated-handoffs",
                "--write-initial-reports",
                "--initial-min-passed", "1"
        });

        String manifest = Files.readString(pack.resolve("manifest.tsv"));
        assertTrue(manifest.contains("add-loot\thandoffs/add-loot/source/world.atf\t2\t1\tgroups/plan.atf"));
        assertTrue(Files.isRegularFile(handoffSource.resolve("world.atf")));
        assertTrue(Files.isRegularFile(handoffSource.resolve("groups/plan.atf")));
        assertTrue(Files.isRegularFile(handoff.resolve("README.md")));
        assertFalse(Files.exists(handoffSource.resolve("edit-eval-prompts.md")));
        assertFalse(Files.exists(handoffSource.resolve("edit-manifest.tsv")));
        String lock = Files.readString(pack.resolve("handoff-lock.tsv"));
        assertTrue(lock.contains("README.md\t"));
        assertTrue(lock.contains("add-loot.md\t"));
        assertTrue(lock.contains("handoffs/add-loot/README.md\t"));
        String baselineLock = Files.readString(pack.resolve("baseline-lock.tsv"));
        assertTrue(baselineLock.contains("add-loot\tworld.atf\t"));
        assertTrue(baselineLock.contains("add-loot\tgroups/plan.atf\t"));

        String readme = Files.readString(pack.resolve("README.md"));
        assertTrue(readme.contains("Isolated handoff directories: `yes`"));
        assertTrue(readme.contains("handoffs/add-loot/source/world.atf"));
        assertTrue(readme.contains("The model should not inspect earlier validation examples"));

        String brief = Files.readString(pack.resolve("add-loot.md"));
        assertTrue(brief.contains("handoff_scope:"));
        assertTrue(brief.contains("use only this brief and the prepared source tree named below"));
        assertTrue(brief.contains("prepared_source_tree: " + handoffSource));
        assertTrue(brief.contains("edit the prepared_source_tree in place; do not recopy the base source"));
        assertTrue(brief.contains("edited_world_atf: " + handoffSource.resolve("world.atf")));
        assertTrue(brief.contains("add-loot\thandoffs/add-loot/source/world.atf\t2\t1\tgroups/plan.atf"));
        String handoffReadme = Files.readString(handoff.resolve("README.md"));
        assertTrue(handoffReadme.contains("prepared_source_tree: " + handoffSource));
        assertTrue(handoffReadme.contains("edited_world_atf: " + handoffSource.resolve("world.atf")));
        String runNotes = Files.readString(pack.resolve("run-notes.md"));
        assertTrue(runNotes.contains("Model or surface: fixture-model"));
        assertTrue(runNotes.contains("Independence notes: fixture edited after pack generation"));
        String initialStatus = Files.readString(pack.resolve("status.md"));
        assertTrue(initialStatus.contains("summary: ready=0 missing=0 unedited=1 wrong_file=0 total=1"));
        String initialAudit = Files.readString(pack.resolve("audit.md"));
        assertTrue(initialAudit.contains("summary: 11/14 eval audit gate(s) passed"));
        assertTrue(initialAudit.contains("| `pass` | `baseline_lock` |"));
        assertTrue(initialAudit.contains("| `pass` | `prepared_source_scope` |"));
        assertTrue(initialAudit.contains("| `fail` | `score_artifact` |"));
        assertTrue(initialAudit.contains("| `fail` | `readiness` |"));

        writePlanSource(handoffSource, true);
        App.main(new String[]{
                "plan-eval-status-report",
                base.resolve("world.atf").toString(),
                pack.resolve("manifest.tsv").toString(),
                pack.resolve("status.md").toString()
        });
        App.main(new String[]{
                "plan-edit-manifest-report",
                base.resolve("world.atf").toString(),
                pack.resolve("manifest.tsv").toString(),
                pack.resolve("score.md").toString()
        });
        App.main(new String[]{
                "plan-eval-audit",
                base.resolve("world.atf").toString(),
                pack.toString(),
                "--min-passed", "1",
                "--out", pack.resolve("audit.md").toString()
        });
        String audit = Files.readString(pack.resolve("audit.md"));
        assertTrue(audit.contains("summary: 14/14 eval audit gate(s) passed"));
        assertTrue(audit.contains("| `pass` | `handoff_lock` |"));
        assertTrue(audit.contains("| `pass` | `baseline_lock` |"));
        assertTrue(audit.contains("| `pass` | `prepared_source_scope` |"));
        assertTrue(audit.contains("| `pass` | `independence_hygiene` |"));
        assertTrue(audit.contains("| `pass` | `score_pass_count` |"));

        Path pack2 = tempDir.resolve("pack-2");
        Path runs2 = pack2.resolve("runs");
        App.main(new String[]{
                "plan-eval-pack",
                base.resolve("world.atf").toString(),
                tasks.toString(),
                pack2.toString(),
                "--run-prefix", "tiny2",
                "--runs-dir", runs2.toString(),
                "--prepare-runs"
        });
        writePlanSource(runs2.resolve("tiny2-add-loot"), true);
        App.main(new String[]{
                "plan-eval-status-report",
                base.resolve("world.atf").toString(),
                pack2.resolve("manifest.tsv").toString(),
                pack2.resolve("status.md").toString()
        });
        App.main(new String[]{
                "plan-edit-manifest-report",
                base.resolve("world.atf").toString(),
                pack2.resolve("manifest.tsv").toString(),
                pack2.resolve("score.md").toString()
        });
        Files.writeString(pack2.resolve("run-notes.md"), """
                # Model Eval Run Notes

                Run prefix: `tiny2`
                Model or surface: fixture-model-2
                Operator: test
                Date: 2026-06-13
                Independence notes: second fixture edited after pack generation

                ## Per-Task Retries

                | Task | Brief | Retry count | Notes |
                | --- | --- | --- | --- |
                | `add-loot` | `add-loot.md` | 0 |  |
                """);

        Path completion = tempDir.resolve("completion.md");
        App.main(new String[]{
                "plan-eval-completion",
                base.resolve("world.atf").toString(),
                pack.toString(),
                pack2.toString(),
                "--min-runs", "2",
                "--min-passed-per-run", "1",
                "--out", completion.toString()
        });
        String completionText = Files.readString(completion);
        assertTrue(completionText.contains("summary: 3/3 eval completion gate(s) passed"));
        assertTrue(completionText.contains("| `pass` | `distinct_model_surfaces` | `2`"));
    }

    private static void writePlanSource(Path root, boolean extraLoot) throws Exception {
        Files.createDirectories(root.resolve("groups"));
        Files.writeString(root.resolve("world.atf"), """
                world "Eval Report Fixture" {
                  min_y 0
                  height 64
                  default_biome minecraft:plains
                  include "groups/plan.atf"
                }
                """);
        Files.writeString(root.resolve("groups/plan.atf"), """
                group "plan" {
                  origin 0 0 0
                  bounds 0 0 0 to 16 48 16

                  plan {
                    area 0 0 16 16
                    mask playable = circle 8 8 radius 7
                    field ground = terrain playable base 10 amplitude 0 scale 8 octaves 1 seed 1
                    terrain playable field ground from 1 top minecraft:grass_block[snowy=false] under minecraft:dirt
                %s    loot playable tier 1 count 1 seed 2 avoid_routes 0 avoid_reservations 0 conform ground
                  }
                }
                """.formatted(extraLoot
                ? "    loot playable tier 2 count 1 seed 3 avoid_routes 0 avoid_reservations 0 conform ground\n"
                : ""));
    }
}
