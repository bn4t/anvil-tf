# Large Map Edit Eval Prompts

This file defines prompt-ready edit tasks for testing whether an LLM can edit
the large-map plan format ergonomically. The model should work in a fresh copy
of `examples/large-map-validation`, then the evaluator should score the result
with `plan-edit-manifest`.

The candidate format passes a task only when the edited source:

- changes the expected group file,
- stays within the source-line threshold,
- produces at least the expected expanded-ATF impact,
- has zero `plan-diagnose` warnings,
- has zero expanded-core diagnostics,
- keeps the edit inside prepared routes/reservations/landmark zones,
- does not add map-theme directives to the core language.

## Evaluation Flow

For a model output directory at `target/validation-trials/MODEL-RUN`, add one
row to `edit-manifest.tsv`:

```text
task-id<TAB>../../target/validation-trials/MODEL-RUN/world.atf<TAB>max_source_lines<TAB>min_expanded_lines<TAB>expected_changed_file
```

Then run:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-edit-manifest \
  examples/large-map-validation/world.atf \
  examples/large-map-validation/edit-manifest.tsv
```

## Task 1: Kikoshi Harbor Mooring Detail

Prompt:

```text
You are editing the ATF large-map validation source. Add a small Kikoshi-style
harbor mooring detail inside the existing harbor pad. Do not move the route,
reservation, or landmark. Do not add raw block commands. Use the existing plan
cluster/component/style vocabulary and keep diagnostics clean.
```

Context commands:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench \
  examples/large-map-validation/world.atf \
  --group "kikoshi 512 validation skeleton" \
  --kind cluster \
  --limit 12 \
  --catalog-limit 30

java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --name "harbor frontage" \
  --kind cluster \
  --context 2
```

Scoring row:

```text
kikoshi-harbor-mooring<TAB>../../target/validation-trials/MODEL-RUN/world.atf<TAB>4<TAB>8<TAB>groups/kikoshi-512-plan.atf
```

## Task 2: Riverton Keep Parapet Accents

Prompt:

```text
You are editing the ATF large-map validation source. Add a compact
Riverton/castle-town keep parapet accent inside the existing east keep island.
Do not move the keep, routes, or reservation. Do not add city/castle-specific
core directives. Use existing generic cluster/component/style rows and keep
diagnostics clean.
```

Context commands:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench \
  examples/large-map-validation/world.atf \
  --group "canal 448 validation skeleton" \
  --kind cluster \
  --limit 14 \
  --catalog-limit 30

java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --name "east keep retaining trim" \
  --kind cluster \
  --context 2
```

Scoring row:

```text
riverton-keep-parapet<TAB>../../target/validation-trials/MODEL-RUN/world.atf<TAB>4<TAB>8<TAB>groups/canal-448-plan.atf
```

## Task 3: Polish Plaza Recipe Rhythm

Prompt:

```text
You are editing the ATF large-map validation source. Use the existing
component/style catalog to add one small recipe-assisted arch rhythm inside the
central polished plaza. Start from `plan-recipe`, then adjust the span so
`plan-diagnose` reports no reservation or spacing warnings.
```

Context commands:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench \
  examples/large-map-validation/world.atf \
  --group "polish 384 density benchmark" \
  --kind cluster \
  --limit 12 \
  --catalog-limit 30

java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-recipe \
  examples/large-map-validation/world.atf \
  --style polish.arch_piece \
  --group "polish 384 density benchmark" \
  --count 6
```

Scoring row:

```text
polish-recipe-arch-rhythm<TAB>../../target/validation-trials/MODEL-RUN/world.atf<TAB>4<TAB>6<TAB>groups/polish-384-plan.atf
```

## Task 4: Dense Overlook Additive Detail

Prompt:

```text
You are editing the ATF large-map validation source. Add a richer but still
compact detail cluster to the east overlook reservation in the dense polish
benchmark. The edit should add repeated local detail using existing style keys,
not raw block coordinates. Keep it inside the reservation and preserve route
readability.
```

Context commands:

```text
java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-workbench \
  examples/large-map-validation/world.atf \
  --group "polish 384 density benchmark" \
  --kind cluster \
  --limit 12 \
  --catalog-limit 40

java -jar target/anvil-tf-0.1.0-SNAPSHOT.jar plan-snippet \
  examples/large-map-validation/world.atf \
  --name "east overlook ribs" \
  --kind cluster \
  --context 2
```

Scoring row:

```text
dense-overlook-additive<TAB>../../target/validation-trials/MODEL-RUN/world.atf<TAB>8<TAB>80<TAB>groups/polish-384-plan.atf
```

