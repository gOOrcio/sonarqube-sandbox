# Adding SonarQube CI to a New Project

This repo provides four reusable GitHub Actions workflows. Pick the one matching your language.

## Prerequisites

1. SonarQube is running at `https://sonarqube.mati-lab.online`
2. A SonarQube project has been created (Auto-created on first scan, or created manually via Administration > Projects)
3. A `SONAR_TOKEN` secret is added to the target GitHub repo (Settings > Secrets and variables > Actions)

## Java / Gradle

No `sonar-project.properties` needed — configure in `build.gradle`:

```groovy
sonar {
    properties {
        property 'sonar.projectKey', 'your-project-key'
        property 'sonar.host.url', 'https://sonarqube.mati-lab.online'
        property 'sonar.coverage.jacoco.xmlReportPaths', 'build/reports/jacoco/test/jacocoTestReport.xml'
    }
}
```

Workflow (`.github/workflows/sonar.yml`):

```yaml
name: SonarQube Analysis
on:
  push:
    branches: [main]
  pull_request:
    branches: [main]
jobs:
  sonarqube:
    uses: gOOrcio/sonarqube-sandbox/.github/workflows/reusable-sonar-java.yml@main
    with:
      project_key: your-project-key
    secrets:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

## Python

Add `sonar-project.properties` to the repo root:

```properties
sonar.projectKey=your-project-key
sonar.projectName=Your Project Name
sonar.sources=.
sonar.exclusions=**/__pycache__/**,tests/**,venv/**
sonar.tests=tests
sonar.python.coverage.reportPaths=coverage.xml
sonar.python.version=3
```

Workflow:

```yaml
jobs:
  sonarqube:
    uses: gOOrcio/sonarqube-sandbox/.github/workflows/reusable-sonar-python.yml@main
    with:
      project_key: your-project-key
    secrets:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```

## Go

Add `sonar-project.properties`:

```properties
sonar.projectKey=your-project-key
sonar.sources=.
sonar.exclusions=vendor/**
sonar.go.coverage.reportPaths=coverage.out
```

Workflow uses `reusable-sonar-go.yml`.

## TypeScript / Node

Add `sonar-project.properties`:

```properties
sonar.projectKey=your-project-key
sonar.sources=src
sonar.exclusions=node_modules/**,dist/**
sonar.javascript.lcov.reportPaths=coverage/lcov.info
```

Ensure Jest is configured to output lcov: `--coverageReporters=lcov`.

Workflow uses `reusable-sonar-typescript.yml`.

## Monorepo

For monorepos with multiple languages (e.g. `api/` Go + `web/` TS), put `sonar-project.properties` in each sub-directory and call the reusable workflows with `working_directory`:

```yaml
jobs:
  api:
    uses: gOOrcio/sonarqube-sandbox/.github/workflows/reusable-sonar-go.yml@main
    with:
      project_key: my-api
      working_directory: api
    secrets:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
  web:
    uses: gOOrcio/sonarqube-sandbox/.github/workflows/reusable-sonar-typescript.yml@main
    with:
      project_key: my-web
      working_directory: web
    secrets:
      SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
```
