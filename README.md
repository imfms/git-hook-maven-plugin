# Git Hook Maven Plugin

[![Maven Central](https://img.shields.io/maven-central/v/ms.imf/git-hook-maven-plugin.svg)](https://central.sonatype.com/artifact/ms.imf/git-hook-maven-plugin)

[中文](README_zh.md) | English

A maven plugin that installs custom git hooks. Supports both pre-commit and pre-push hooks.

> **Note**: This plugin is based on [Cosium/git-code-format-maven-plugin](https://github.com/Cosium/git-code-format-maven-plugin), Removed the code format functionality and focused only on Git hooks with more useful features added.
> 
> **AI Generated**: Most code is written by AI. Focus on functionality rather than code quality.

## Features

- **Flexible Hook Installation**: Automatically installs git hooks during Maven initialization
- **Pre-Commit, Post-Commit & Pre-Push Support**

## Usage

This example shows all available features:

```xml
<plugin>
  <groupId>ms.imf</groupId>
  <artifactId>git-hook-maven-plugin</artifactId>
  <!-- Check Maven Central for latest version -->
  <version>${ms.imf.git-hook-maven-plugin.version}</version>
  <executions>
    <execution>
      <goals>
        <goal>install-hooks</goal> <!-- Bound to initialize phase by default -->
      </goals>
    </execution>
  </executions>
  <configuration>
    <!-- Pre-commit hook -->
    <preCommitCommandMavenPrefix>true</preCommitCommandMavenPrefix>
    <preCommitEnvVarToPropagate>JAVA_HOME,HOME</preCommitEnvVarToPropagate>
    <preCommitHookContent>compile com.diffplug.spotless:spotless-maven-plugin:apply</preCommitHookContent>
    <preCommitPropertiesToPropagate>maven.test.skip,maven.repo.local</preCommitPropertiesToPropagate>

    <!-- Post-commit hook -->
    <postCommitCommandMavenPrefix>true</postCommitCommandMavenPrefix>
    <postCommitHookContent>deploy -DskipTests</postCommitHookContent>
    <postCommitPropertiesToPropagate>maven.repo.local</postCommitPropertiesToPropagate>

    <!-- Pre-push hook -->
    <prePushHookContent>docker build -t myapp . &amp;&amp; docker run --rm myapp npm test</prePushHookContent>
  </configuration>
</plugin>
```

**Generated pre-commit hook** (`pre-commit`):
```bash
#!/bin/bash
set -e

export JAVA_HOME="/path/to/java"                              # preCommitEnvVarToPropagate=JAVA_HOME,HOME
export HOME="/path/to/home"                                   # preCommitEnvVarToPropagate=JAVA_HOME,HOME

/path/to/mvn -f /path/to/pom.xml \                            # preCommitCommandMavenPrefix=true
  -Dmaven.test.skip=true \                                    # preCommitPropertiesToPropagate=maven.test.skip,maven.repo.local
  -Dmaven.repo.local=/path/to/local/repo \                    # preCommitPropertiesToPropagate=maven.test.skip,maven.repo.local
  compile com.diffplug.spotless:spotless-maven-plugin:apply   # preCommitHookContent=compile com...
```

**Generated post-commit hook** (`post-commit`):
```bash
#!/bin/bash
set -e

/path/to/mvn -f /path/to/pom.xml \                            # postCommitCommandMavenPrefix=true
  -Dmaven.repo.local=value \                                  # postCommitPropertiesToPropagate=maven.repo.local
  deploy -DskipTests                                          # postCommitHookContent=deploy -DskipTests
```

**Generated pre-push hook** (`pre-push`):
```bash
#!/bin/bash
set -e

docker build -t myapp . && docker run --rm myapp npm test     # prePushHookContent=docker build -t myapp . &amp;&amp; ...
```

## Configuration Parameters

| Parameter | Property | Default | Description |
|-----------|----------|---------|-------------|
| **Pre-commit Hook Parameters** | | | |
| `preCommitHookContent` | `ghmp.preCommitHookContent` | `""` | Content to execute for pre-commit hook. If empty, no pre-commit hook is installed |
| `preCommitCommandMavenPrefix` | `ghmp.preCommitCommandMavenPrefix` | `false` | Whether to use Maven command prefix for pre-commit hooks |
| `preCommitEnvVarToPropagate` | `ghmp.preCommitEnvVarToPropagate` | | Comma-separated list of environment variables to propagate to pre-commit hooks |
| `preCommitPropertiesToPropagate` | `ghmp.preCommitPropertiesToPropagate` | | Comma-separated list of Maven properties to propagate to pre-commit hooks |
| **Post-commit Hook Parameters** | | | |
| `postCommitHookContent` | `ghmp.postCommitHookContent` | `""` | Content to execute for post-commit hook. If empty, no post-commit hook is installed |
| `postCommitCommandMavenPrefix` | `ghmp.postCommitCommandMavenPrefix` | `false` | Whether to use Maven command prefix for post-commit hooks |
| `postCommitEnvVarToPropagate` | `ghmp.postCommitEnvVarToPropagate` | | Comma-separated list of environment variables to propagate to post-commit hooks |
| `postCommitPropertiesToPropagate` | `ghmp.postCommitPropertiesToPropagate` | | Comma-separated list of Maven properties to propagate to post-commit hooks |
| **Pre-push Hook Parameters** | | | |
| `prePushHookContent` | `ghmp.prePushHookContent` | `""` | Content to execute for pre-push hook. If empty, no pre-push hook is installed |
| `prePushCommandMavenPrefix` | `ghmp.prePushCommandMavenPrefix` | `false` | Whether to use Maven command prefix for pre-push hooks |
| `prePushEnvVarToPropagate` | `ghmp.prePushEnvVarToPropagate` | | Comma-separated list of environment variables to propagate to pre-push hooks |
| `prePushPropertiesToPropagate` | `ghmp.prePushPropertiesToPropagate` | | Comma-separated list of Maven properties to propagate to pre-push hooks |
| **General Parameters** | | | |
| `skip` | `ghmp.skip` | `false` | Skip plugin execution |


## How the Hook Works

On the `initialize` maven phase, `git-hook:install-hooks` installs git hooks that look like this:

**Pre-commit hook:**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.pre-commit.sh"
```

**Post-commit hook:**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.post-commit.sh"
```

**Pre-push hook:**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.pre-push.sh"
```

The actual hook scripts contain the configured commands based on your settings for each hook type.

## Frequently Asked Questions

### Do I need to run mvn initialize or is that a stage that happens automatically?
`initialize` is the first phase of the Maven lifecycle. Any goal that you perform (e.g. `compile` or `test`) will automatically trigger `initialize` and thus trigger the git hook installation.

### I'd like to skip hook installation in a child project
Add a `<skip>true</skip>` configuration in the inheriting project or set the `ghmp.skip` property to true.
