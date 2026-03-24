# Quality Gates Reference

A Quality Gate is a set of conditions that must pass for new code to be considered acceptable. "New code" = lines changed in the current PR or since the last analysis of the branch.

## Gates in this setup

Two gates are defined in `sonar-config/quality-gates/` and applied by `sonar-config/provision.sh`.

### Default Gate (all projects)

| Metric | Threshold | Meaning |
|--------|-----------|---------|
| New Code Coverage | ≥ 80% | At least 80% of new lines must be covered by tests |
| New Duplicated Lines Density | ≤ 3% | Max 3% copy-paste in new code |
| New Reliability Rating | ≥ A | No new Bugs |
| New Security Rating | ≥ A | No new Vulnerabilities |
| New Maintainability Rating | ≥ A | No new Code Smells rated D or E |

### Strict Gate (Java projects)

Same as Default but coverage threshold raised to 85%.

## Reading gate results

After a scan on a PR:
1. GitHub check shows **SonarQube Quality Gate passed/failed**
2. SonarQube posts a PR decoration comment (requires DevOps Platform Integration — see `docs/ide-integration.md`)
3. Full detail at `https://sonarqube.mati-lab.online/dashboard?id=<project-key>&pullRequest=<pr-number>`

## Why "new code" matters

Sonar evaluates only changed lines — not the entire legacy codebase. This means:
- A project with 40% overall coverage can still pass the gate if the PR's new lines hit 80%
- The demo app's `review/` package intentionally has low coverage on existing code; the gate only catches new uncovered additions
- To see the demo gate fail: open a PR that adds untested code to `ReviewService`

## Common gate failures

| Failure | Likely cause | Fix |
|--------|-------------|-----|
| Coverage < 80% | New code lacks unit tests | Add tests covering the new lines |
| Reliability Rating B+ | New Bug introduced | Fix the issue flagged in SonarQube |
| Duplicated lines > 3% | Copy-pasted code | Extract to shared method |
| Security hotspot | Native query / raw SQL / user input | Review and mark safe, or fix |
