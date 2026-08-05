# Spring Evals benchmark image. Every docker-mode attempt runs its
# agent CLI in a container from this image, and the judge's
# ./mvnw clean test runs in a fresh container from the SAME image, so
# agent and judge always see an identical toolchain. This file is part
# of the measurement content hash; changing it is a measurement change,
# and the image tag is derived from this file's hash so a stale local
# image can never serve a newer Dockerfile.
# Base is digest-pinned; the nodesource setup script is the one
# remaining non-pinned input.
FROM eclipse-temurin:25-jdk@sha256:12e44624adee6808a36d962717e1656e0afeeeff5a100f9cb00e0136513558f0

# CLI versions are pinned for reproducibility. Host versions at pin
# time (2026-08-05): claude 2.1.221, codex-cli 0.146.0,
# gemini-cli 0.1.13, qwen-code 0.21.5, node v24.19.0.
RUN apt-get update \
 && apt-get install -y --no-install-recommends ca-certificates curl git gnupg procps unzip zip \
 && curl -fsSL https://deb.nodesource.com/setup_24.x | bash - \
 && apt-get install -y --no-install-recommends nodejs \
 && rm -rf /var/lib/apt/lists/*

RUN npm install -g \
    @anthropic-ai/claude-code@2.1.221 \
    @openai/codex@0.146.0 \
    @google/gemini-cli@0.1.13 \
    @qwen-code/qwen-code@0.21.5

# Attempts run as a non-root user: Claude Code refuses
# --dangerously-skip-permissions under root, and the agent must not
# own the container as root anyway.
# /sandbox/claude-config: empty per-container Claude config dir, so no
# host CLAUDE.md, skills, plugins, or MCP servers can exist here.
# /sandbox/codex-home: empty; the harness seeds ONLY auth.json into it
# when the provider is codex.
# /opt/m2-cache: host Maven caches, mounted read-only at run time.
# /home/agent/.m2/repository: the container-local writable overlay;
# Maven reads through to the cache via maven.repo.local.tail.
RUN useradd -m -s /bin/bash agent \
 && mkdir -p /sandbox/claude-config /sandbox/codex-home \
    /opt/m2-cache/repository /opt/m2-cache/wrapper \
    /home/agent/.m2/repository /workspace \
 && chown -R agent:agent /sandbox /home/agent/.m2 /workspace

ENV CLAUDE_CONFIG_DIR=/sandbox/claude-config \
    CODEX_HOME=/sandbox/codex-home \
    MAVEN_OPTS="-Dmaven.repo.local.tail=/opt/m2-cache/repository"

USER agent
WORKDIR /workspace
