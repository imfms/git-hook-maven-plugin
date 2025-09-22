package ms.imf.maven.plugin.git.hook.maven;

import static org.assertj.core.api.Assertions.assertThat;

import io.takari.maven.testing.executor.MavenExecution;
import io.takari.maven.testing.executor.MavenRuntime;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

/**
 * @author Réda Housni Alaoui
 */
public abstract class AbstractMavenModuleTest extends AbstractTest {

  private final String mavenModuleDirectory;

  public AbstractMavenModuleTest(
      MavenRuntime.MavenRuntimeBuilder mavenBuilder,
      String projectRootDirectoryName,
      String mavenModuleDirectory)
      throws Exception {
    super(mavenBuilder, projectRootDirectoryName);
    this.mavenModuleDirectory = mavenModuleDirectory;
  }

  @Test
  public void GIVEN_project_WHEN_install_hooks_THEN_git_hooks_should_be_installed()
      throws Exception {
    // Execute maven initialize to install hooks with both hooks content provided
    mavenExecution()
        .withCliOptions("-Dghmp.preCommitHookContent=validate", "-Dghmp.prePushHookContent=test")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify git hooks directory exists
    Path hooksDir = projectRoot().resolve(".git/hooks");
    assertThat(Files.exists(hooksDir)).isTrue();

    // Verify pre-commit hook script is created
    String artifactId = getProjectArtifactId();
    Path preCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-commit.sh");
    assertThat(Files.exists(preCommitPluginHook)).isTrue();
    assertThat(Files.isExecutable(preCommitPluginHook)).isTrue();

    // Verify pre-push hook script is created
    Path prePushPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-push.sh");
    assertThat(Files.exists(prePushPluginHook)).isTrue();
    assertThat(Files.isExecutable(prePushPluginHook)).isTrue();

    // Verify base git hooks reference the plugin hooks
    Path preCommitHook = hooksDir.resolve("pre-commit");
    assertThat(Files.exists(preCommitHook)).isTrue();
    String preCommitContent = readFileContent(preCommitHook);
    assertThat(preCommitContent).contains(artifactId + ".git-hook.pre-commit.sh");

    Path prePushHook = hooksDir.resolve("pre-push");
    assertThat(Files.exists(prePushHook)).isTrue();
    String prePushContent = readFileContent(prePushHook);
    assertThat(prePushContent).contains(artifactId + ".git-hook.pre-push.sh");
  }

  @Test
  public void
      GIVEN_custom_pre_commit_pipeline_WHEN_install_hooks_THEN_hook_should_contain_pipeline()
          throws Exception {
    // Execute maven initialize with custom pre-commit content in Maven mode
    mavenExecution()
        .withCliOptions(
            "-Dghmp.preCommitCommandMavenPrefix=true", "-Dghmp.preCommitHookContent=validate")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify hook contains the custom pipeline
    Path hooksDir = projectRoot().resolve(".git/hooks");
    String artifactId = getProjectArtifactId();
    Path preCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-commit.sh");

    String hookContent = readFileContent(preCommitPluginHook);
    assertThat(hookContent).contains("validate");
    assertThat(hookContent).contains("mvn");
  }

  @Test
  public void GIVEN_direct_command_mode_WHEN_install_hooks_THEN_hook_should_execute_direct_command()
      throws Exception {
    // Execute maven initialize with direct command mode
    mavenExecution()
        .withCliOptions(
            "-Dghmp.preCommitCommandMavenPrefix=false",
            "-Dghmp.preCommitHookContent=echo 'Direct command test'")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify hook contains direct command
    Path hooksDir = projectRoot().resolve(".git/hooks");
    String artifactId = getProjectArtifactId();
    Path preCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-commit.sh");

    String hookContent = readFileContent(preCommitPluginHook);
    assertThat(hookContent).contains("echo 'Direct command test'");
    assertThat(hookContent).doesNotContain("mvn");
  }

  @Test
  public void
      GIVEN_environment_variables_enabled_WHEN_install_hooks_THEN_hook_should_export_variables()
          throws Exception {
    // Execute maven initialize with environment variables enabled
    mavenExecution()
        .withCliOptions(
            "-Dghmp.preCommitHookContent=validate",
            "-Dghmp.preCommitEnvVarToPropagate=JAVA_HOME,HOME")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify hook exports environment variables
    Path hooksDir = projectRoot().resolve(".git/hooks");
    String artifactId = getProjectArtifactId();
    Path preCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-commit.sh");

    String hookContent = readFileContent(preCommitPluginHook);
    // Hook should contain export statements for JAVA_HOME and HOME (if they exist in environment)
    assertThat(hookContent).contains("#!/bin/bash");
    assertThat(hookContent).contains("set -e");
    // Check if JAVA_HOME is exported (it might be available in test environment)
    if (System.getenv("JAVA_HOME") != null) {
      assertThat(hookContent).contains("export JAVA_HOME=");
    }
    // Check if HOME is exported (should always be available)
    if (System.getenv("HOME") != null) {
      assertThat(hookContent).contains("export HOME=");
    }
  }

  private MavenExecution mavenExecution() {
    return buildMavenExecution(projectRoot().resolve(mavenModuleDirectory));
  }

  private String getProjectArtifactId() throws IOException {
    // Read artifact ID from the test project's pom.xml
    Path pomFile = projectRoot().resolve(mavenModuleDirectory).resolve("pom.xml");
    if (Files.exists(pomFile)) {
      String pomContent = readFileContent(pomFile);
      // Simple parsing to extract artifactId - for test purposes
      if (pomContent.contains("<artifactId>")) {
        int start = pomContent.indexOf("<artifactId>") + "<artifactId>".length();
        int end = pomContent.indexOf("</artifactId>", start);
        return pomContent.substring(start, end).trim();
      }
    }
    // Default fallback
    return "test-project";
  }

  @Test
  public void
      GIVEN_multiple_properties_to_propagate_WHEN_install_hooks_THEN_hook_should_contain_properties()
          throws Exception {
    // Execute maven initialize with multiple properties to propagate
    // Pass the properties as system properties to the Maven execution
    mavenExecution()
        .withCliOptions(
            "-Dghmp.preCommitCommandMavenPrefix=true",
            "-Dghmp.preCommitHookContent=validate",
            "-Dghmp.preCommitPropertiesToPropagate=maven.test.skip,skipTests",
            "-Dmaven.test.skip=true",
            "-DskipTests=true")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify hook contains the propagated properties
    Path hooksDir = projectRoot().resolve(".git/hooks");
    String artifactId = getProjectArtifactId();
    Path preCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.pre-commit.sh");

    String hookContent = readFileContent(preCommitPluginHook);
    assertThat(hookContent).contains("mvn");
    assertThat(hookContent).contains("-Dmaven.test.skip=true");
    assertThat(hookContent).contains("-DskipTests=true");
  }

  @Test
  public void
      GIVEN_post_commit_configuration_WHEN_install_hooks_THEN_post_commit_hook_should_be_installed()
          throws Exception {
    // Execute maven initialize with post-commit content in Maven mode
    mavenExecution()
        .withCliOptions(
            "-Dghmp.postCommitCommandMavenPrefix=true", "-Dghmp.postCommitHookContent=deploy")
        .execute("initialize")
        .assertErrorFreeLog();

    // Verify post-commit hook script is created
    Path hooksDir = projectRoot().resolve(".git/hooks");
    String artifactId = getProjectArtifactId();
    Path postCommitPluginHook = hooksDir.resolve(artifactId + ".git-hook.post-commit.sh");
    assertThat(Files.exists(postCommitPluginHook)).isTrue();
    assertThat(Files.isExecutable(postCommitPluginHook)).isTrue();

    // Verify base post-commit hook references the plugin hook
    Path postCommitHook = hooksDir.resolve("post-commit");
    assertThat(Files.exists(postCommitHook)).isTrue();
    String postCommitContent = readFileContent(postCommitHook);
    assertThat(postCommitContent).contains(artifactId + ".git-hook.post-commit.sh");

    // Verify hook contains the custom pipeline
    String hookContent = readFileContent(postCommitPluginHook);
    assertThat(hookContent).contains("deploy");
    assertThat(hookContent).contains("mvn");
  }

  private String readFileContent(Path filePath) throws IOException {
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      return IOUtils.toString(inputStream, StandardCharsets.UTF_8);
    }
  }
}
