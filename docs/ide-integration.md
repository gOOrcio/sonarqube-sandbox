# IDE Integration (SonarLint Connected Mode)

SonarLint runs analysis locally in real time. In Connected Mode it syncs quality profiles and suppressed issues from your SonarQube instance.

## Prerequisites

- SonarQube running at `https://sonarqube.mati-lab.online`
- A SonarQube user token (see below)

## Generate a SonarQube token

1. Log in to `https://sonarqube.mati-lab.online`
2. Click your avatar > **My Account > Security**
3. Under **Generate Tokens**, enter a name (e.g. `intellij-sonarlint`), type = **User Token**
4. Click **Generate**, copy the token

## IntelliJ IDEA

1. **Preferences > Plugins** — install **SonarLint**
2. **Preferences > Tools > SonarLint > SonarQube / SonarCloud Connections**
3. Click `+`, choose **SonarQube**, URL = `https://sonarqube.mati-lab.online`, paste token
4. **Preferences > Tools > SonarLint > Project Settings** — bind to a project key (e.g. `library`)
5. Connected Mode syncs the Java profile rules defined in `sonar-config/quality-profiles/java-profile.xml`

## VS Code

1. Install extension: **SonarLint** (SonarSource)
2. Open **Settings (JSON)** and add:

```json
"sonarlint.connectedMode.connections.sonarqube": [
  {
    "connectionId": "mati-lab",
    "serverUrl": "https://sonarqube.mati-lab.online",
    "token": "<your-token>"
  }
],
"sonarlint.connectedMode.project": {
  "connectionId": "mati-lab",
  "projectKey": "library"
}
```

Replace `library` with the project key for whichever project you are working on.

## What Connected Mode gives you

- Same rule set as CI — no surprises at PR time
- Suppressed issues (marked Won't Fix / False Positive) are excluded locally
- Security hotspot review synced from server
