# Part of the measurement content hash: changing this file is a measurement change.
# The nodesource setup script is the one non-pinned input.
FROM eclipse-temurin:25-jdk@sha256:12e44624adee6808a36d962717e1656e0afeeeff5a100f9cb00e0136513558f0

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

# Non-root: Claude Code refuses --dangerously-skip-permissions under root.
# /sandbox holds empty per-container config dirs; /opt/m2-cache is the read-only
# host cache mount, read through via maven.repo.local.tail to the writable overlay.
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
