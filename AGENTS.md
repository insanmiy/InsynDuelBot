# Agent Notes For InsynDuelBot

## Git Rules

- NEVER add co-author trailers, "Generated with" lines, or any tool/agent attribution to commit messages. Plain commit messages only.
- NEVER change git config (user.name, user.email). Commits must use the repo owner's identity.
- Only commit and push when the user explicitly asks.
- Git is NOT on the PATH in this environment. Use the full path: `"C:\Program Files\Git\cmd\git.exe"`.
- The shell is cmd.exe, not bash. Do not use bash heredocs (`$(cat <<'EOF' ...)`) in commit commands; use repeated `-m` flags instead.
- Repo remote: `https://github.com/insanmiy/InsynDuelBot.git` (old InsynPracticeBot URL redirects here).

## Build And Version

- Build: `.\gradlew.bat build`
- Version lives in `gradle.properties` (`version=X.Y.Z`). Bump it when releasing.
- Plugin version is injected into `core/src/main/resources/plugin.yml` via `${version}`.
- NMS code is shared across versions from `nms/shared/`; per-version modules live under `nms/v1_20_R4`, `nms/v1_21_R1`, `nms/v1_21_R3`.

## Conventions

- Java 21, Gradle Kotlin DSL.
- Match existing code style. Run the full build before considering work done.
- Use sentence case for messages, menu text, logs, and documentation. Preserve proper names, acronyms, commands, and placeholders.
- Keep all plugin-owned player and console text editable in `messages.yml`, including menu titles, labels, lore, HUD text, errors, and logs. Resolve labels when they are displayed so reload applies to new output.

## Backward compatibility

- Backward compatibility is not required unless the user explicitly requests it for the task.
- Prefer the current configuration and data formats. Do not add migration files, legacy fallbacks, or compatibility shims solely to preserve old behavior.
