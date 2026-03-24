# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

`sonarqube-sandbox` is a learning and portfolio project demonstrating a SonarQube enterprise-style setup:
- Self-hosted SonarQube CE on Proxmox homelab with community-branch-plugin for PR decoration
- Multi-language analysis: Java (Spring Boot), Python, Go, TypeScript
- GitHub Actions reusable workflows for CI integration
- Quality gates and profiles defined as code in `sonar-config/`

The companion infrastructure repo is `~/Projects/mati-lab` (Ansible + Docker Compose for the SonarQube VM).

## Repository Structure

```
sonarqube-sandbox/
├── .github/workflows/
│   ├── reusable-sonar-java.yml      # Reusable: Gradle + JaCoCo + sonar scan
│   ├── reusable-sonar-python.yml    # Reusable: pytest + coverage.xml + sonar scan
│   ├── reusable-sonar-go.yml        # Reusable: go test + sonar scan
│   ├── reusable-sonar-typescript.yml # Reusable: jest + sonar scan
│   └── demo-app-ci.yml              # Triggers on spring-demo/ changes
├── spring-demo/                      # Spring Boot Book Library demo app (Java 21, Gradle)
│   └── src/main/java/dev/matilab/library/
│       ├── book/                     # BookService (intentional complexity), BookController
│       └── review/                   # ReviewService (intentional duplication)
├── sonar-config/
│   ├── quality-gates/               # default-gate.json, strict-gate.json
│   ├── quality-profiles/            # java/python/go/ts XML profiles
│   └── provision.sh                 # Idempotent REST API script — apply gates + profiles
└── docs/
    ├── ide-integration.md           # IntelliJ + VS Code SonarLint connected mode
    ├── ci-integration.md            # How to add SonarQube to a new project
    ├── quality-gates.md             # Gate thresholds and how to read results
    └── gitea-alternative.md         # Gitea as GitHub alternative (not deployed)
```

## Running the demo app

```bash
cd spring-demo
./gradlew test                  # run tests + generate JaCoCo coverage
./gradlew jacocoTestReport      # XML report at build/reports/jacoco/test/jacocoTestReport.xml
./gradlew sonar                 # scan against SonarQube (requires SONAR_TOKEN env var)
```

## Provisioning quality gates and profiles

```bash
export SONAR_TOKEN=<admin-token>
export SONAR_HOST_URL=https://sonarqube.mati-lab.online
bash sonar-config/provision.sh
```

## Plans

| Plan | File | Status |
|------|------|--------|
| Plan 1: SonarQube Infrastructure | `docs/superpowers/plans/2026-03-23-infrastructure.md` | Execute in mati-lab session |
| Plan 2: Demo App + Quality Gates | `docs/superpowers/plans/2026-03-23-demo-app-quality-gates.md` | Execute after Plan 1 |
| Plan 3: CI Pipelines + Docs | `docs/superpowers/plans/2026-03-23-ci-pipelines-docs.md` | Execute after Plan 2 |

Plans are in `docs/superpowers/` (gitignored — not committed to GitHub).
