# Git Hook Maven 插件

中文 | [English](README.md)

一个安装自定义 Git hooks 的 Maven 插件。支持 pre-commit、post-commit 和 pre-push hooks。

> **说明**: 本插件基于 [Cosium/git-code-format-maven-plugin](https://github.com/Cosium/git-code-format-maven-plugin)，移除了代码格式化功能，专注于 Git hooks 功能，并添加了更多实用特性。
>
> **AI 生成**: 大部分代码由 AI 编写。请关注功能而非代码质量。

## 功能特性

- **灵活的 Hook 安装**: 在 Maven 初始化期间自动安装 Git hooks
- **支持 Pre-Commit、Post-Commit 和 Pre-Push**

## 使用方法

下面的示例展示了所有可用功能：

```xml
<plugin>
  <groupId>ms.imf</groupId>
  <artifactId>git-hook-maven-plugin</artifactId>
  <executions>
    <execution>
      <goals>
        <goal>install-hooks</goal> <!-- 默认绑定到 initialize 阶段 -->
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

**生成的 pre-commit hook** (`pre-commit`):
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

**生成的 post-commit hook** (`post-commit`):
```bash
#!/bin/bash
set -e

/path/to/mvn -f /path/to/pom.xml \                            # postCommitCommandMavenPrefix=true
  -Dmaven.repo.local=value \                                  # postCommitPropertiesToPropagate=maven.repo.local
  deploy -DskipTests                                          # postCommitHookContent=deploy -DskipTests
```

**生成的 pre-push hook** (`pre-push`):
```bash
#!/bin/bash
set -e

docker build -t myapp . && docker run --rm myapp npm test     # prePushHookContent=docker build -t myapp . &amp;&amp; ...
```

## 配置参数

| 参数 | 属性 | 默认值 | 描述 |
|-----------|----------|---------|-------------|
| **Pre-commit Hook 参数** | | | |
| `preCommitHookContent` | `ghmp.preCommitHookContent` | `""` | pre-commit hook 要执行的内容。如果为空，则不安装 pre-commit hook |
| `preCommitCommandMavenPrefix` | `ghmp.preCommitCommandMavenPrefix` | `false` | 是否在 pre-commit hooks 中使用 Maven 命令前缀 |
| `preCommitEnvVarToPropagate` | `ghmp.preCommitEnvVarToPropagate` | | 要传播到 pre-commit hooks 的环境变量列表（逗号分隔） |
| `preCommitPropertiesToPropagate` | `ghmp.preCommitPropertiesToPropagate` | | 要传播到 pre-commit hooks 的 Maven 属性列表（逗号分隔） |
| **Post-commit Hook 参数** | | | |
| `postCommitHookContent` | `ghmp.postCommitHookContent` | `""` | post-commit hook 要执行的内容。如果为空，则不安装 post-commit hook |
| `postCommitCommandMavenPrefix` | `ghmp.postCommitCommandMavenPrefix` | `false` | 是否在 post-commit hooks 中使用 Maven 命令前缀 |
| `postCommitEnvVarToPropagate` | `ghmp.postCommitEnvVarToPropagate` | | 要传播到 post-commit hooks 的环境变量列表（逗号分隔） |
| `postCommitPropertiesToPropagate` | `ghmp.postCommitPropertiesToPropagate` | | 要传播到 post-commit hooks 的 Maven 属性列表（逗号分隔） |
| **Pre-push Hook 参数** | | | |
| `prePushHookContent` | `ghmp.prePushHookContent` | `""` | pre-push hook 要执行的内容。如果为空，则不安装 pre-push hook |
| `prePushCommandMavenPrefix` | `ghmp.prePushCommandMavenPrefix` | `false` | 是否在 pre-push hooks 中使用 Maven 命令前缀 |
| `prePushEnvVarToPropagate` | `ghmp.prePushEnvVarToPropagate` | | 要传播到 pre-push hooks 的环境变量列表（逗号分隔） |
| `prePushPropertiesToPropagate` | `ghmp.prePushPropertiesToPropagate` | | 要传播到 pre-push hooks 的 Maven 属性列表（逗号分隔） |
| **通用参数** | | | |
| `skip` | `ghmp.skip` | `false` | 跳过插件执行 |


## Hook 工作原理

在 Maven 的 `initialize` 阶段，`git-hook:install-hooks` 会安装如下所示的 Git hooks：

**Pre-commit hook：**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.pre-commit.sh"
```

**Post-commit hook：**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.post-commit.sh"
```

**Pre-push hook：**
```bash
#!/bin/bash
"$(git rev-parse --git-dir)/hooks/${project.artifactId}.git-hook.pre-push.sh"
```

实际的 hook 脚本包含基于每种 hook 类型设置的已配置命令。

## 常见问题

### 我需要运行 mvn initialize 吗，还是会自动执行？
`initialize` 是 Maven 生命周期的第一个阶段。你执行的任何目标（例如 `compile` 或 `test`）都会自动触发 `initialize`，从而触发 Git hook 安装。

### 我想在子项目中跳过 hook 安装
在继承项目中添加 `<skip>true</skip>` 配置，或将 `ghmp.skip` 属性设置为 true。