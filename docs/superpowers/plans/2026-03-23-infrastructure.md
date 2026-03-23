# SonarQube Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Provision a SonarQube CE VM on Proxmox, deploy SonarQube + PostgreSQL via Docker Compose with the community-branch-plugin, expose it via Cloudflare Tunnel, and complete initial configuration.

**Architecture:** Ansible playbooks in `mati-lab/compute/sonarqube_vm/` mirror the existing `smart_resume_vm` pattern exactly (create → configure → deploy). SonarQube runs in Docker Compose alongside PostgreSQL. Traffic reaches the VM via Caddy reverse proxy (consistent with all other homelab services) through the existing Cloudflare Tunnel.

**Tech Stack:** Ansible, Proxmox API (`community.general.proxmox_kvm`), Docker Compose v2, SonarQube LTS Community, PostgreSQL 15, community-branch-plugin, Caddy 2, Cloudflare Tunnel

> **Spec deviation note:** The approved spec said "no Caddyfile entry needed." In practice, the existing Cloudflare Tunnel routes all `*.mati-lab.online` traffic to Caddy, and Caddy routes to each VM by hostname — as seen with every other service. Adding a Caddyfile entry is therefore the correct approach, consistent with the rest of the homelab. A separate cloudflared per-VM would duplicate infrastructure unnecessarily.

---

## File Map

### mati-lab repo (`/home/gooral/Projects/mati-lab/`)

| Action | Path | Responsibility |
|--------|------|----------------|
| Create | `compute/sonarqube_vm/ansible.cfg` | Ansible config (vault file path, inventory path) |
| Create | `compute/sonarqube_vm/Makefile` | `provision` / `configure` / `deploy` targets (run from inside `sonarqube_vm/`) |
| Create | `compute/sonarqube_vm/requirements.yml` | Ansible collection dependencies |
| Create | `compute/sonarqube_vm/inventory/hosts.yml` | VM host entry at 192.168.1.201 |
| Create | `compute/sonarqube_vm/group_vars/all/vars.yml` | VM spec + SonarQube/plugin versions |
| Create | `compute/sonarqube_vm/group_vars/all/vault.yml` | Encrypted secrets (DB password, admin password, plugin SHA256) |
| Create | `compute/sonarqube_vm/playbooks/site.yml` | Imports: create → configure → deploy |
| Create | `compute/sonarqube_vm/playbooks/create_vm.yml` | Clone template 9000, cloud-init, resize disk, start |
| Create | `compute/sonarqube_vm/playbooks/configure_vm.yml` | apt upgrade, Docker, UFW (allow 22 + 9000 from LAN), fail2ban |
| Create | `compute/sonarqube_vm/playbooks/deploy_app.yml` | Download + verify plugin JAR, rsync compose file, start stack |
| Create | `compute/sonarqube_vm/templates/docker-compose.yml.j2` | SonarQube + PostgreSQL services |
| Modify | `network/caddy/Caddyfile` | Add `sonarqube.mati-lab.online` → `192.168.1.201:9000` block |

---

## Task 1: Create Ansible directory structure

**Files:** `compute/sonarqube_vm/` (skeleton)

- [ ] In `mati-lab/compute/`, create the directory tree:

```bash
mkdir -p compute/sonarqube_vm/{inventory,group_vars/all,playbooks,templates}
```

- [ ] Commit skeleton:

```bash
git add compute/sonarqube_vm/
git commit -m "chore: scaffold sonarqube_vm ansible structure"
```

---

## Task 2: Write ansible.cfg, Makefile, and requirements.yml

**Files:**
- Create: `compute/sonarqube_vm/ansible.cfg`
- Create: `compute/sonarqube_vm/Makefile`
- Create: `compute/sonarqube_vm/requirements.yml`

- [ ] Write `ansible.cfg` (mirrors `smart_resume_vm` exactly):

```ini
[defaults]
inventory          = inventory/hosts.yml
roles_path         = roles
vault_password_file = ~/.vault_pass
host_key_checking  = False
stdout_callback    = yaml
```

- [ ] Write `Makefile` — targets run from **inside** `sonarqube_vm/` (same as `smart_resume_vm`):

```makefile
.PHONY: provision configure deploy install-deps

install-deps:
	ansible-galaxy collection install -r requirements.yml

provision: install-deps
	ansible-playbook playbooks/site.yml

configure:
	ansible-playbook playbooks/configure_vm.yml

deploy:
	ansible-playbook playbooks/deploy_app.yml
```

> Run `make provision` from inside `mati-lab/compute/sonarqube_vm/`, not from the parent directory. This mirrors how `smart_resume_vm` works.

- [ ] Write `requirements.yml` — declares all Ansible collections needed by the playbooks:

```yaml
collections:
  - name: community.general    # proxmox_kvm, ufw
  - name: community.docker     # docker_compose_v2
  - name: ansible.posix        # sysctl, synchronize
```

- [ ] Commit:

```bash
git add compute/sonarqube_vm/ansible.cfg \
        compute/sonarqube_vm/Makefile \
        compute/sonarqube_vm/requirements.yml
git commit -m "chore: add sonarqube_vm ansible.cfg, Makefile, and requirements"
```

---

## Task 3: Write inventory and vars

**Files:**
- Create: `compute/sonarqube_vm/inventory/hosts.yml`
- Create: `compute/sonarqube_vm/group_vars/all/vars.yml`

- [ ] Write `inventory/hosts.yml`:

```yaml
all:
  hosts:
    sonarqube:
      ansible_host: "192.168.1.201"
      ansible_user: debian
      ansible_ssh_private_key_file: ~/.ssh/id_ed25519
      ansible_python_interpreter: /usr/bin/python3
```

- [ ] Write `group_vars/all/vars.yml`:

```yaml
# Proxmox connection
proxmox_host: "192.168.1.184"
proxmox_node: "proxmox"
proxmox_api_user: "root@pam"
proxmox_api_token_id:     "{{ vault_proxmox_api_token_id }}"
proxmox_api_token_secret: "{{ vault_proxmox_api_token_secret }}"

# VM spec
vm_id: 103
vm_name: "sonarqube"
vm_template_id: 9000
vm_cores: 4
vm_memory_mb: 4096
vm_disk_size: "30G"
vm_storage: "local-lvm"

# Network (static)
vm_ip: "192.168.1.201"
vm_gateway: "192.168.1.1"
vm_nameserver: "192.168.1.1"
vm_cidr: 24

# SSH
vm_ssh_user: "debian"
vm_ssh_key_path: "~/.ssh/id_ed25519.pub"

# App paths
app_remote_path: "/opt/sonarqube"

# SonarQube — MUST be pinned together, check compatibility matrix before changing:
# https://github.com/mc1arke/sonarqube-community-branch-plugin#compatibility
sonarqube_version: "10.8-community"      # concrete tag — DO NOT use "lts-community" (floating)
branch_plugin_version: "1.22.0"
# SHA256 of the plugin JAR — get from GitHub Releases page for the version above
# https://github.com/mc1arke/sonarqube-community-branch-plugin/releases
branch_plugin_sha256: "{{ vault_branch_plugin_sha256 }}"

# PostgreSQL
postgres_db: "sonarqube"
postgres_user: "sonarqube"
postgres_password: "{{ vault_postgres_password }}"
```

> **Before starting Task 3:** Open the [compatibility table](https://github.com/mc1arke/sonarqube-community-branch-plugin#compatibility). Find the plugin version that matches the exact `sonarqube_version` tag you chose (e.g. `10.8-community`). If the exact tag is not yet listed, use the closest LTS version that is listed and update `sonarqube_version` to match. Update both `sonarqube_version`, `branch_plugin_version`, and `branch_plugin_sha256` in `vars.yml` accordingly. The two versions **must match** — a mismatch causes SonarQube to refuse to start.

- [ ] Commit:

```bash
git add compute/sonarqube_vm/inventory/ compute/sonarqube_vm/group_vars/
git commit -m "chore: add sonarqube_vm inventory and vars"
```

---

## Task 4: Write vault secrets

**Files:**
- Create: `compute/sonarqube_vm/group_vars/all/vault.yml` (encrypted)

- [ ] Create a plaintext temp file with these keys (do NOT commit unencrypted):

```yaml
vault_proxmox_api_token_id: "root@pam!<token-name>"
vault_proxmox_api_token_secret: "<token-secret>"
vault_postgres_password: "<strong-random-password>"
vault_sonarqube_admin_password: "<strong-random-password>"
vault_branch_plugin_sha256: "<sha256-from-github-releases>"
```

- [ ] Encrypt with ansible-vault:

```bash
ansible-vault encrypt group_vars/all/vault.yml
```

> Uses `~/.vault_pass` as the password file (same as all other VMs in this repo).

- [ ] Verify it's encrypted:

```bash
head -1 compute/sonarqube_vm/group_vars/all/vault.yml
# Expected: $ANSIBLE_VAULT;1.1;AES256
```

- [ ] Add `.gitignore` to prevent accidental plaintext commit:

```
# compute/sonarqube_vm/.gitignore
group_vars/all/vault.yml.plain
```

- [ ] Commit:

```bash
git add compute/sonarqube_vm/group_vars/all/vault.yml compute/sonarqube_vm/.gitignore
git commit -m "chore: add encrypted vault for sonarqube_vm"
```

---

## Task 5: Write create_vm.yml

**Files:**
- Create: `compute/sonarqube_vm/playbooks/create_vm.yml`

A direct copy of `smart_resume_vm/playbooks/create_vm.yml`. All values come from `vars.yml` and `vault.yml` via the explicit `vars_files` block already present in the original file — this is the pattern used across all playbooks in this repo (not auto-loading). No changes needed.

- [ ] Copy and commit:

```bash
cp compute/smart_resume_vm/playbooks/create_vm.yml \
   compute/sonarqube_vm/playbooks/create_vm.yml
git add compute/sonarqube_vm/playbooks/create_vm.yml
git commit -m "chore: add sonarqube_vm create_vm playbook"
```

---

## Task 6: Write configure_vm.yml

**Files:**
- Create: `compute/sonarqube_vm/playbooks/configure_vm.yml`

Based on `smart_resume_vm/playbooks/configure_vm.yml` with one change: open port 9000 for LAN access (Caddy reverse-proxies to it) and remove the HTTP/80 rule (Caddy handles TLS termination, not this VM).

- [ ] Write `configure_vm.yml`:

```yaml
- name: Configure VM
  hosts: sonarqube
  become: true
  vars_files:
    - ../group_vars/all/vars.yml
    - ../group_vars/all/vault.yml
  tasks:
    - name: Upgrade all packages
      ansible.builtin.apt:
        upgrade: dist
        update_cache: true

    - name: Install required packages
      ansible.builtin.apt:
        name:
          - ca-certificates
          - curl
          - gnupg
          - ufw
          - fail2ban
          - rsync
        state: present

    - name: Add Docker GPG key
      ansible.builtin.apt_key:
        url: https://download.docker.com/linux/debian/gpg
        state: present

    - name: Add Docker repository
      ansible.builtin.apt_repository:
        repo: >
          deb [arch=amd64]
          https://download.docker.com/linux/debian
          {{ ansible_distribution_release }} stable

    - name: Install Docker
      ansible.builtin.apt:
        name:
          - docker-ce
          - docker-ce-cli
          - containerd.io
          - docker-compose-plugin
        update_cache: true

    - name: Add deploy user to docker group
      ansible.builtin.user:
        name: "{{ vm_ssh_user }}"
        groups: docker
        append: true

    - name: Reset SSH connection to pick up new group membership
      ansible.builtin.meta: reset_connection

    - name: Configure UFW — allow SSH
      community.general.ufw:
        rule: allow
        port: "22"
        proto: tcp

    - name: Configure UFW — allow SonarQube from LAN only
      community.general.ufw:
        rule: allow
        port: "9000"
        proto: tcp
        src: "192.168.1.0/24"

    - name: Enable UFW
      community.general.ufw:
        state: enabled
        policy: deny

    - name: Enable fail2ban
      ansible.builtin.service:
        name: fail2ban
        enabled: true
        state: started

    - name: Set vm.max_map_count for Elasticsearch
      ansible.posix.sysctl:
        name: vm.max_map_count
        value: "524288"
        state: present
        reload: true

    - name: Set fs.file-max for Elasticsearch
      ansible.posix.sysctl:
        name: fs.file-max
        value: "131072"
        state: present
        reload: true
```

> The two `sysctl` tasks at the end are **required** for SonarQube's embedded Elasticsearch — without them, Elasticsearch refuses to start. This is the most common reason SonarQube fails to launch.

- [ ] Commit:

```bash
git add compute/sonarqube_vm/playbooks/configure_vm.yml
git commit -m "chore: add sonarqube_vm configure_vm playbook"
```

---

## Task 7: Write Docker Compose template

**Files:**
- Create: `compute/sonarqube_vm/templates/docker-compose.yml.j2`

- [ ] Write `templates/docker-compose.yml.j2`:

```yaml
services:
  postgresql:
    image: postgres:15-alpine
    container_name: sonarqube-db
    restart: unless-stopped
    environment:
      POSTGRES_DB: "{{ postgres_db }}"
      POSTGRES_USER: "{{ postgres_user }}"
      POSTGRES_PASSWORD: "{{ postgres_password }}"
    volumes:
      - postgresql_data:/var/lib/postgresql/data
    networks:
      - sonarqube-net
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U {{ postgres_user }}"]
      interval: 10s
      timeout: 5s
      retries: 5

  sonarqube:
    image: sonarqube:{{ sonarqube_version }}
    container_name: sonarqube
    restart: unless-stopped
    depends_on:
      postgresql:
        condition: service_healthy
    ports:
      - "9000:9000"
    environment:
      SONAR_JDBC_URL: "jdbc:postgresql://postgresql:5432/{{ postgres_db }}"
      SONAR_JDBC_USERNAME: "{{ postgres_user }}"
      SONAR_JDBC_PASSWORD: "{{ postgres_password }}"
      SONAR_FORCEAUTHENTICATION: "true"
    volumes:
      - sonarqube_data:/opt/sonarqube/data
      - sonarqube_logs:/opt/sonarqube/logs
      - sonarqube_extensions:/opt/sonarqube/extensions
      - ./plugins:/opt/sonarqube/extensions/plugins:ro
    networks:
      - sonarqube-net
    ulimits:
      nofile:
        soft: 131072
        hard: 131072

volumes:
  postgresql_data:
  sonarqube_data:
  sonarqube_logs:
  sonarqube_extensions:

networks:
  sonarqube-net:
    driver: bridge
```

> The `./plugins` directory (created by `deploy_app.yml`) is mounted read-only into the extensions/plugins path. SonarQube scans this directory on startup to load the community-branch-plugin JAR.

- [ ] Commit:

```bash
git add compute/sonarqube_vm/templates/docker-compose.yml.j2
git commit -m "chore: add sonarqube docker-compose template"
```

---

## Task 8: Write deploy_app.yml

**Files:**
- Create: `compute/sonarqube_vm/playbooks/deploy_app.yml`

- [ ] Write `deploy_app.yml`:

```yaml
- name: Deploy SonarQube
  hosts: sonarqube
  gather_facts: false
  vars_files:
    - ../group_vars/all/vars.yml
    - ../group_vars/all/vault.yml

  tasks:
    - name: Create app directory
      ansible.builtin.file:
        path: "{{ app_remote_path }}"
        state: directory
        owner: "{{ vm_ssh_user }}"
        mode: "0755"
      become: true

    - name: Create plugins directory
      ansible.builtin.file:
        path: "{{ app_remote_path }}/plugins"
        state: directory
        owner: "{{ vm_ssh_user }}"
        mode: "0755"
      become: true

    - name: Write docker-compose.yml
      ansible.builtin.template:
        src: ../templates/docker-compose.yml.j2
        dest: "{{ app_remote_path }}/docker-compose.yml"
        owner: "{{ vm_ssh_user }}"
        mode: "0644"
      become: true

    - name: Download community-branch-plugin JAR
      ansible.builtin.get_url:
        url: "https://github.com/mc1arke/sonarqube-community-branch-plugin/releases/download/{{ branch_plugin_version }}/sonarqube-community-branch-plugin-{{ branch_plugin_version }}.jar"
        dest: "{{ app_remote_path }}/plugins/sonarqube-community-branch-plugin-{{ branch_plugin_version }}.jar"
        checksum: "sha256:{{ vault_branch_plugin_sha256 }}"
        owner: "{{ vm_ssh_user }}"
        mode: "0644"
      become: true

    - name: Start SonarQube stack
      community.docker.docker_compose_v2:
        project_src: "{{ app_remote_path }}"
        state: present
        pull: missing
      become: true

    - name: Wait for SonarQube to be ready
      ansible.builtin.uri:
        url: "http://{{ vm_ip }}:9000/api/system/status"
        method: GET
        status_code: 200
        return_content: true
      register: sonar_status
      until: sonar_status.json.status == "UP"
      retries: 30
      delay: 15
      delegate_to: localhost
```

> SonarQube takes 2-5 minutes to start on first run (database schema creation). The `until` loop retries for up to 7.5 minutes — do not interrupt it.

- [ ] Commit:

```bash
git add compute/sonarqube_vm/playbooks/deploy_app.yml
git commit -m "chore: add sonarqube_vm deploy_app playbook"
```

---

## Task 9: Write site.yml

**Files:**
- Create: `compute/sonarqube_vm/playbooks/site.yml`

- [ ] Write `site.yml`:

```yaml
- import_playbook: create_vm.yml
- import_playbook: configure_vm.yml
- import_playbook: deploy_app.yml
```

- [ ] Commit:

```bash
git add compute/sonarqube_vm/playbooks/site.yml
git commit -m "chore: add sonarqube_vm site.yml"
```

---

## Task 10: Provision the VM

All commands run from **inside** `mati-lab/compute/sonarqube_vm/`:

```bash
cd /home/gooral/Projects/mati-lab/compute/sonarqube_vm
```

- [ ] Verify vault decrypts correctly:

```bash
ansible-vault view group_vars/all/vault.yml
# Expected: prints decrypted YAML — confirms ~/.vault_pass works
```

- [ ] Run full provisioning:

```bash
make provision
# This runs: ansible-galaxy collection install -r requirements.yml, then ansible-playbook playbooks/site.yml
```

Expected output (abridged):
```
PLAY [Create VM from cloud-init template] ...
TASK [Clone template] ... ok
TASK [Configure cloud-init] ... ok
TASK [Resize disk] ... ok
TASK [Start VM] ... ok
TASK [Wait for SSH] ... ok

PLAY [Configure VM] ...
TASK [Upgrade all packages] ... changed
TASK [Install Docker] ... changed
TASK [Set vm.max_map_count] ... changed
...

PLAY [Deploy SonarQube] ...
TASK [Download community-branch-plugin JAR] ... ok
TASK [Start SonarQube stack] ... changed
TASK [Wait for SonarQube to be ready] ... ok  ← takes 2-5 min

PLAY RECAP: sonarqube: ok=N changed=N failed=0
```

- [ ] If `Wait for SonarQube to be ready` fails after all retries, SSH in and check logs:

```bash
ssh debian@192.168.1.201
docker logs sonarqube --tail 50
```

Common failure: `vm.max_map_count too low` — means the sysctl task didn't persist. Run `make configure` again.

---

## Task 11: Add sonarqube.mati-lab.online to Caddy

**Files:**
- Modify: `network/caddy/Caddyfile`

- [ ] Add the following block to `Caddyfile` before the catch-all `handle` block:

```
    @sonarqube host sonarqube.mati-lab.online
    handle @sonarqube {
        reverse_proxy 192.168.1.201:9000 {
            header_up Host {host}
            header_up X-Forwarded-Proto https
            header_up X-Forwarded-Ssl on
        }
    }
```

- [ ] Redeploy Caddy from `mati-lab/network/`:

```bash
cd /home/gooral/Projects/mati-lab/network
make deploy
# or: docker compose -f caddy/docker-compose.yml up -d
```

- [ ] Verify Caddy reloaded without errors:

```bash
docker logs caddy --tail 20
# Expected: no error lines, Caddy reports it's serving the new host
```

- [ ] Commit the Caddyfile change:

```bash
git add network/caddy/Caddyfile
git commit -m "feat: add sonarqube.mati-lab.online to Caddy reverse proxy"
```

---

## Task 12: Add DNS record in Cloudflare

This must happen before Task 13 — without a DNS record, `sonarqube.mati-lab.online` won't resolve.

- [ ] Log in to the Cloudflare dashboard → DNS → Records for `mati-lab.online`.

- [ ] Check what record type the other services use (e.g. `smart-resume`, `proxmox`). It will be one of:
  - A CNAME pointing to a Cloudflare Tunnel `<tunnel-id>.cfargotunnel.com`
  - Or a CNAME pointing to the same hostname as other records

- [ ] Add a new record:
  - **Type:** CNAME (same as other `*.mati-lab.online` records)
  - **Name:** `sonarqube`
  - **Target:** same target as the other records
  - **Proxy status:** Proxied (orange cloud)

- [ ] Wait 30-60 seconds for propagation, then verify:

```bash
nslookup sonarqube.mati-lab.online
# Expected: resolves (does not return NXDOMAIN)
```

---

## Task 13: Verify public access

- [ ] Open `https://sonarqube.mati-lab.online` in a browser.

Expected: SonarQube login page (NOT a dashboard — confirms `SONAR_FORCEAUTHENTICATION=true` is working).

If you see `502 Bad Gateway`: Caddy can't reach 192.168.1.201:9000 — SSH to VM and check `docker ps` to confirm sonarqube container is running.

If you see `ERR_NAME_NOT_RESOLVED`: DNS hasn't propagated yet — wait another 60 seconds and retry.

---

## Task 14: SonarQube initial configuration

All steps in this task are manual via the web UI at `https://sonarqube.mati-lab.online`.

- [ ] Log in with `admin` / `admin`. SonarQube will force a password change — set it to `{{ vault_sonarqube_admin_password }}` from your vault.

- [ ] Verify the community-branch-plugin is active:
  - Navigate to: Administration → System → Installed Plugins
  - Confirm `sonarqube-community-branch-plugin` appears with status "Active"
  - If not listed: check `docker logs sonarqube | grep -i "plugin\|error"` for JAR load errors

- [ ] Disable anonymous access (belt-and-suspenders — env var already sets this, but confirm in UI):
  - Administration → Configuration → General Settings → Security
  - Confirm "Force user authentication" is checked

- [ ] Generate an admin token for `provision.sh`:
  - Administration → Security → Users → Administrator → Tokens
  - Create token named `provision-admin`, type `User Token`, no expiry
  - **Copy the token now** — it won't be shown again. Save to your password manager.

- [ ] Configure GitHub DevOps Platform Integration (for PR decoration):
  - Administration → DevOps Platform Integrations → GitHub → Create configuration
  - GitHub API URL: `https://api.github.com`
  - Personal Access Token: a GitHub PAT with `repo` scope from your GitHub account
  - Click "Check configuration" — should show green

- [ ] Commit nothing (these are runtime config changes stored in PostgreSQL, not in files).

---

## Task 15: Smoke test the full stack

- [ ] From your developer machine, call the SonarQube API to confirm auth works:

```bash
curl -u <admin-token>: https://sonarqube.mati-lab.online/api/system/status
# Expected: {"id":"...","version":"...","status":"UP"}
```

- [ ] Confirm anonymous access is blocked:

```bash
curl https://sonarqube.mati-lab.online/api/system/status
# Expected: HTTP 401 or redirect to login
```

- [ ] Confirm plugin is loaded via API:

```bash
curl -u <admin-token>: \
  "https://sonarqube.mati-lab.online/api/plugins/installed" \
  | python3 -m json.tool | grep -A2 "community-branch"
# Expected: plugin entry with "key": "communityBranchPlugin"
```

- [ ] Commit nothing. Infrastructure is live.

---

## Done

Infrastructure is complete when:
- `https://sonarqube.mati-lab.online` shows the login page
- Admin password changed from default
- community-branch-plugin confirmed active
- GitHub DevOps integration configured
- Admin token saved to password manager (needed for Plan 2's `provision.sh`)

**Next:** Implement [Plan 2: Demo App + Quality Gates](./2026-03-23-demo-app-quality-gates.md)
