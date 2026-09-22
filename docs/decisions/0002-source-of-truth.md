# 0002 Which artifact wins

## Status

Accepted.

## Decision

`src/main/resources` is the config source. `target/` is generated and must not be edited.

Player wiki pages explain play. `docs/systems` explains implementation. They can both be updated when a player-visible rule changes. The wiki is not a reason to skip the system doc.

When code, tests, YAML, and docs disagree, the change is blocked until a person states the intended contract and the artifacts match it. An old sentence in the crop and husbandry specs said "if they disagree, change the code." That blanket rule is retired. Locked formulas in those docs are still the contract until an explicit decision replaces them.

`docs/manifest.json` decides which system doc must move when a path changes. `docs/impact-waiver.md` is the only no-impact escape, and only when that file itself changes in the diff.

## Consequences

- Do not copy nutrition or food numbers into a second hand-maintained table when the YAML file is the number. Docs state the rule and the committed value when agents must not guess.
- Known mismatches (`plugin.yml` version versus `pom.xml`, unloaded `cookware.yml`) stay documented until a change is dedicated to them.
