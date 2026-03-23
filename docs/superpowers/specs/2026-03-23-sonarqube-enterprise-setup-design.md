# SonarQube Enterprise Setup — Design Spec

**Date:** 2026-03-23
**Status:** Approved

---

## Purpose

Build a self-hosted SonarQube code quality platform that demonstrates enterprise-grade static analysis across multiple languages and projects. Primary goals: learning, skills showcase for interviews, and practical enforcement of quality standards on real personal projects.

---

## Architecture Overview

Two repositories are involved:

- **`mati-lab`** — owns all infrastructure: VM provisioning on Proxmox, Caddy reverse proxy config, and Cloudflare Tunnel config
- **`sonarqube-sandbox`** — owns the demo app, CI workflow templates, quality gate definitions, and all documentation

Personal project repos (`dietly-scraper`, `smart-resume`, `resto-rate`) each receive one GitHub Actions workflow file that calls reusable workflows defined in `sonarqube-sandbox`.

GitHub Actions uses **GitHub-hosted runners**. No self-hosted runner is deployed — the host has insufficient RAM headroom once all VMs are accounted for (see resource table below). Runners reach SonarQube via its public Cloudflare Tunnel URL.

---

## Infrastructure (mati-lab)

### Proxmox Host Resource Budget

Host: 192.168.1.184 — 16 CPUs, 31GB RAM

| VM | ID | RAM | CPUs | IP | Status |
|----|-----|-----|------|----|--------|
| ollama-gpu | 101 | 12GB | 4 | — | existing |
| smart-resume | 102 | 2GB | 2 | 192.168.1.200 | existing |
| **sonarqube** | **103** | **4GB** | **4** | **192.168.1.201** | new |
| **openclaw** | **104** | **6GB** | **2** | **192.168.1.202** | new |
| Host OS overhead | — | ~3GB | ~2 | — | — |
| **Total** | | **~27GB** | **~14** | | **~4GB / ~2 CPUs remaining** |

> OpenClaw VM (104) is provisioned under `mati-lab/compute/openclaw_vm/` following the same Ansible pattern. It is out of scope for this spec but included above for resource tracking.

### SonarQube VM (103)

Provisioned under `mati-lab/compute/sonarqube_vm/`, following the exact pattern of `smart_resume_vm`.

| Resource | Value | Rationale |
|----------|-------|-----------|
| VM ID | 103 | Next available after 102 |
| RAM | 4096 MB | Elasticsearch (embedded) needs ≥2GB; PostgreSQL + SonarQube fit in 4GB |
| CPUs | 4 cores | Analysis workload benefits from parallelism |
| Disk | 30 GB | SonarQube data + PostgreSQL analysis history |
| IP | 192.168.1.201 | Static, follows .200 pattern |
| OS template | 9000 (debian12-cloud) | Same cloud-init template as other VMs |

**Ansible playbook structure:**

```
mati-lab/compute/sonarqube_vm/
├── ansible.cfg
├── Makefile                         ← provision / configure / deploy targets
├── inventory/hosts.yml              ← host: sonarqube, ansible_host: 192.168.1.201
├── group_vars/all/
│   ├── vars.yml                     ← VM spec, IPs, paths, SonarQube + plugin versions
│   └── vault.yml                    ← DB password, SonarQube admin password
└── playbooks/
    ├── site.yml                     ← imports: create → configure → deploy
    ├── create_vm.yml                ← clone template 9000, cloud-init, resize, start
    ├── configure_vm.yml             ← apt upgrade, Docker, UFW, fail2ban
    └── deploy_app.yml               ← rsync compose file, download + verify plugin JAR, start stack
templates/
    └── docker-compose.yml.j2
```

### SonarQube Version Pinning

**Both the SonarQube image version and the community-branch-plugin version must be pinned together.** The plugin has a strict compatibility matrix — a version mismatch causes SonarQube to fail at startup.

- Check the compatibility table at: https://github.com/mc1arke/sonarqube-community-branch-plugin#compatibility
- Pin both in `group_vars/all/vars.yml`:
  ```yaml
  sonarqube_version: "10.8-community"          # Docker image tag
  branch_plugin_version: "1.22.0"              # Must match the above
  branch_plugin_sha256: "<sha256 from release>" # Verified at deploy time
  ```
- The plugin JAR is downloaded from GitHub Releases:
  `https://github.com/mc1arke/sonarqube-community-branch-plugin/releases/download/{{ branch_plugin_version }}/sonarqube-community-branch-plugin-{{ branch_plugin_version }}.jar`
- The Ansible `deploy_app.yml` task verifies the SHA256 checksum after download (`get_url` with `checksum: sha256:{{ branch_plugin_sha256 }}`). Deployment fails if checksum does not match.
- The JAR is volume-mounted into `/opt/sonarqube/extensions/plugins/` — no custom Docker image required.

### Docker Compose Stack (on VM)

Services:
- `sonarqube` — Community Edition, port 9000 (internal only, not exposed directly)
- `postgresql` — SonarQube database, not exposed outside the VM

SonarQube environment hardening (set in Docker Compose):
- `SONAR_FORCEAUTHENTICATION=true` — disables anonymous access entirely
- `SONAR_WEB_CONTEXT=/` — standard root context

### Network Access — Cloudflare Tunnel

SonarQube is accessible externally via Cloudflare Tunnel. **No port forwarding on the router. No Cloudflare Access policy** (SonarQube's own authentication is the gate).

Security posture:
- Anonymous access disabled (`SONAR_FORCEAUTHENTICATION=true`)
- All API access (including GitHub Actions scanner) requires a SonarQube token
- Cloudflare Tunnel hides the origin IP and provides DDoS protection
- SonarQube must be kept updated as the primary CVE mitigation

Tunnel config added to `mati-lab` (under the existing `cloudflared` setup):
```yaml
# In Cloudflare dashboard tunnel config or cloudflared config.yml
- hostname: sonarqube.mati-lab.online
  service: http://192.168.1.201:9000
```

### Caddy Reverse Proxy

Caddy is **not** used for the SonarQube VM — traffic goes directly via Cloudflare Tunnel to the VM's port 9000. No new Caddyfile entry is needed.

> Rationale: adding a Caddy hop adds latency and complexity for no benefit when the tunnel terminates at the VM directly.

### PR Decoration — GitHub DevOps Integration

The community-branch-plugin posts PR status checks back to GitHub. This requires a GitHub credential configured in SonarQube's Administration → DevOps Platform Integrations:

- Create a **GitHub Personal Access Token** (or GitHub App) with `repo` scope on the account that owns the analysed repos
- Configure in SonarQube UI: Administration → DevOps Platform Integrations → GitHub → add PAT
- This is a one-time manual step post-deploy; the token is stored in SonarQube's database (not in any config file)
- Document the required scopes and setup steps in `docs/pr-decoration.md`

---

## Spring Boot Demo App (sonarqube-sandbox)

**Path:** `sonarqube-sandbox/spring-demo/`

**Stack:** Spring Boot 3.x, Java 21, Gradle, Spring Data JPA, H2 (tests), JUnit 5 + Mockito, JaCoCo

**Domain:** Book Library CRUD — `Book` and `Review` entities with full REST API.

**Structure:**

```
spring-demo/
├── build.gradle          ← JaCoCo plugin + sonarqube plugin + project properties (canonical config)
├── settings.gradle
└── src/
    ├── main/java/dev/matilab/library/
    │   ├── book/          ← BookController, BookService, BookRepository, Book
    │   └── review/        ← ReviewController, ReviewService, ReviewRepository, Review
    └── test/java/dev/matilab/library/
        ├── book/           ← unit + integration tests (well covered)
        └── review/         ← intentionally sparse coverage
```

> `sonar-project.properties` is **not used**. All SonarQube configuration lives in `build.gradle` via the `sonarqube` Gradle plugin (`org.sonarqube`). The two mechanisms conflict — Gradle plugin properties take precedence and `sonar-project.properties` would be silently ignored.

**Intentional issues seeded for demo purposes:**

| Issue | Type | Effect |
|-------|------|--------|
| Method with cyclomatic complexity > 10 | Code smell | Maintainability rating drop |
| Duplicate logic block in Book + Review services | Duplication | Duplicated lines threshold breach |
| `review/` package <50% test coverage | Coverage | Quality gate failure (see note below) |
| String concatenation in a query method | Security hotspot | Security rating warning |
| Unused variables / dead code | Code smell | Minor maintainability issues |

> **Coverage demo loop note:** SonarQube's "New coverage" metric applies only to lines changed in the PR diff, not to the overall project. The gate failure fires reliably when: (a) first analysis (all code is "new"), or (b) a PR modifies code in `review/`. For repeated demos, the PR should always touch a file in `review/` to guarantee the gate fires. Document this in `docs/quality-gates.md`.

---

## CI/CD Pipelines (GitHub Actions)

### Runner

GitHub-hosted runners (`ubuntu-latest`). No self-hosted runner deployed.

`SONAR_HOST_URL` = `https://sonarqube.mati-lab.online` (Cloudflare Tunnel URL, publicly reachable).

### Reusable workflow templates (sonarqube-sandbox)

```
sonarqube-sandbox/.github/workflows/
├── sonar-spring-demo.yml      ← direct workflow for spring-demo (PR + push to main)
├── sonar-python-template.yml  ← reusable (on: workflow_call)
├── sonar-go-template.yml      ← reusable (on: workflow_call)
└── sonar-ts-template.yml      ← reusable (on: workflow_call)
```

**Repository visibility requirement:** `sonarqube-sandbox` must be a **public** GitHub repository for other repos to call its reusable workflows via `uses: <org>/sonarqube-sandbox/.github/workflows/sonar-python-template.yml@main`. If kept private, all calling repos must be in the same GitHub organisation with Actions access granted. The `@main` ref pins to the main branch; consider a tag (`@v1`) for stability once workflows are stable.

### Personal project integrations

| Repo | Language | Calls template |
|------|----------|---------------|
| `dietly-scraper` | Python | `sonar-python-template.yml` |
| `smart-resume` (api) | Python | `sonar-python-template.yml` |
| `resto-rate` (api) | Go | `sonar-go-template.yml` |
| `resto-rate` (web) | TypeScript | `sonar-ts-template.yml` |

Each personal repo adds one `.github/workflows/sonar.yml` (~10 lines) that calls the reusable workflow, passing `projectKey`, `sources`, and `language`.

### Trigger strategy

| Event | Behaviour |
|-------|-----------|
| PR opened / updated | Analysis runs, results posted as PR check, merge blocked if gate fails |
| Push to `main` | Full analysis, updates project baseline in SonarQube |
| `workflow_dispatch` | Manual trigger for setup / debugging |

### Required secrets (set per repo in GitHub Settings → Secrets)

- `SONAR_TOKEN` — project-scoped analysis token generated in SonarQube UI (Administration → Security → Users → Tokens)
- `SONAR_HOST_URL` — `https://sonarqube.mati-lab.online`

---

## Quality Gates & Profiles

**Path:** `sonarqube-sandbox/sonar-config/`

```
sonar-config/
├── quality-gates/
│   ├── default-gate.json    ← Python, Go, TypeScript projects
│   └── strict-gate.json     ← Java / spring-demo
├── quality-profiles/
│   ├── java-profile.xml
│   ├── python-profile.xml
│   ├── go-profile.xml
│   └── ts-profile.xml
└── provision.sh             ← idempotent: creates gates + profiles via SonarQube REST API
```

**`provision.sh` execution context:**
- Runs from the **developer's machine** (not from inside the VM)
- Authenticates via a SonarQube **admin token** (not username/password) passed as `SONAR_TOKEN` env var
- Targets `https://sonarqube.mati-lab.online` (public Cloudflare Tunnel URL)
- Example: `SONAR_TOKEN=<admin-token> ./provision.sh`
- The script is idempotent: checks for existing gates/profiles by name before creating

**Default gate** (Python, Go, TypeScript):

| Metric | Threshold |
|--------|-----------|
| New coverage | ≥ 80% |
| New duplicated lines | ≤ 3% |
| New reliability rating | A |
| New security rating | A |

**Strict gate** (Java / spring-demo):

| Metric | Threshold |
|--------|-----------|
| New coverage | ≥ 85% |
| New duplicated lines | ≤ 2% |
| New reliability rating | A |
| New security rating | A |
| New maintainability rating | A |

---

## IDE Integration Documentation

**Path:** `sonarqube-sandbox/docs/ide-integration.md`

Covers both IDEs with connected mode (syncs custom quality profiles from `sonarqube.mati-lab.online`) and standalone mode.

**IntelliJ IDEA:**
- SonarQube for IDE plugin (JetBrains Marketplace)
- Connected mode: bind project to SonarQube instance, sync rules
- Real-time highlighting, manual file/module analysis
- Connected vs standalone comparison

**VS Code:**
- SonarQube for IDE extension (VS Code Marketplace)
- `sonarlint.connectedMode` block in `settings.json`
- Per-workspace vs global connection
- Language coverage nuances (Go support differences)

**Both sections include:** token scope requirements, Cloudflare Tunnel connectivity notes, firewall considerations, and the relationship between IDE warnings and CI gate failures.

---

## Documentation Structure

```
sonarqube-sandbox/docs/
├── architecture.md          ← this system, how the pieces connect
├── vm-setup.md              ← how to provision/destroy the SonarQube VM
├── quality-gates.md         ← gate definitions, how to modify, the demo loop (incl. coverage caveat)
├── ide-integration.md       ← IntelliJ + VS Code setup guides
├── pr-decoration.md         ← how PR checks work, GitHub PAT setup, community-branch-plugin explanation
├── gitea-alternative.md     ← fully self-hosted SCM alternative (documentation only)
└── adding-new-project.md    ← how to onboard a new language/project to Sonar
```

---

## Gitea Alternative (Documentation Only)

`docs/gitea-alternative.md` documents how to replicate the entire stack using Gitea (self-hosted Git + Gitea Actions) instead of GitHub. Gitea Actions uses identical workflow YAML syntax, making the CI config nearly copy-paste. This shows awareness of fully air-gapped enterprise setups without the overhead of actually running Gitea.

---

## Verification Plan

1. **VM provisioning:** `make provision` in `mati-lab/compute/sonarqube_vm/` → VM 103 appears in Proxmox, SSH accessible at 192.168.1.201
2. **SonarQube running:** `https://sonarqube.mati-lab.online` loads the login page (not a dashboard — confirms anonymous access is disabled)
3. **Plugin active:** Log in as admin → Administration → System → Installed Plugins → confirm `sonarqube-community-branch-plugin` is listed and active
4. **Quality gates applied:** `SONAR_TOKEN=<admin> ./sonar-config/provision.sh` exits 0 → gates and profiles visible in SonarQube UI
5. **PR decoration configured:** Administration → DevOps Platform Integrations → GitHub shows the PAT connection as green
6. **Spring demo — gate failure:** Open a PR on `sonarqube-sandbox` that touches `review/` code with sparse coverage → GitHub Actions check fails → PR blocked
7. **Spring demo — gate pass:** Fix seeded issues in the same PR → check passes → PR mergeable
8. **Personal project analysis:** Push to a personal repo → workflow runs → project appears in SonarQube with findings
9. **IDE connected mode:** SonarLint in IntelliJ connects to `sonarqube.mati-lab.online`, custom rules sync, editor highlights issues before commit
