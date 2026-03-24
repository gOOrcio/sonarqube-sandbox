# Gitea as a Self-Hosted GitHub Alternative

Gitea is a lightweight, self-hosted Git service. It is **not deployed** in this setup — GitHub is used for all repositories. This document captures what a Gitea-based setup would look like if you ever want to move off GitHub.

## Why consider Gitea

- Full control over code hosting (no GitHub dependency)
- Free private repos, no plan limits
- Runs on a Proxmox VM with ~512MB RAM
- SonarQube community-branch-plugin supports Gitea PR decoration natively

## What would change

| Concern | GitHub (current) | Gitea |
|---------|-----------------|-------|
| CI runners | GitHub-hosted (free) | Gitea Act runners (self-hosted on the same VM or a separate one) |
| PR decoration | GitHub App / PAT in SonarQube DevOps integrations | Gitea token in SonarQube DevOps integrations |
| Reachability from CI | Cloudflare Tunnel (public URL) | Internal network (runner on same LAN) |
| Secrets management | GitHub repo secrets | Gitea repo secrets (same concept) |

## SonarQube DevOps Platform Integration for Gitea

1. Administration > DevOps Platform Integrations > Gitea
2. Provide Gitea API URL (e.g. `https://gitea.mati-lab.online`)
3. Provide a Gitea access token with `repo` and `issues` scopes

The scanner then passes:
```
sonar.pullrequest.provider=gitea
sonar.pullrequest.gitea.instanceUrl=https://gitea.mati-lab.online
sonar.pullrequest.key=<PR number>
sonar.pullrequest.branch=<head branch>
sonar.pullrequest.base=<base branch>
```

## Act runners (Gitea CI)

Gitea Actions is API-compatible with GitHub Actions. The reusable workflow files in `.github/workflows/` would work with minimal changes — mostly replacing `uses: actions/checkout@v4` with Gitea-compatible equivalents or pointing to a self-hosted action mirror.

## Why not deployed

- GitHub provides free hosted runners, removing the need for a self-hosted runner VM
- All personal projects are already on GitHub
- The additional complexity does not provide learning value beyond what GitHub demonstrates
