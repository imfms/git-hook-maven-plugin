package ms.imf.maven.plugin.git.hook;

import static java.util.Optional.ofNullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import ms.imf.maven.plugin.git.hook.executable.Executable;
import ms.imf.maven.plugin.git.hook.executable.ExecutableManager;
import ms.imf.maven.plugin.git.hook.maven.MavenEnvironment;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Installs git hooks on each initialization. Hooks are always overridden in case of changes in:
 *
 * <ul>
 *   <li>maven installation
 *   <li>plugin structure
 * </ul>
 */
@Mojo(name = "install-hooks", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class InstallHooksMojo extends AbstractMavenGitHookMojo {

  enum HookType {
    PRE_COMMIT(
        "pre-commit",
        BASE_PLUGIN_PRE_COMMIT_HOOK,
        PRE_COMMIT_HOOK_BASE_SCRIPT,
        installHooksMojo -> installHooksMojo.preCommitHookContent,
        mojo -> mojo.preCommitCommandMavenPrefix,
        mojo -> mojo.preCommitEnvVarToPropagate,
        mojo -> mojo.preCommitPropertiesToPropagate),
    PRE_PUSH(
        "pre-push",
        BASE_PLUGIN_PRE_PUSH_HOOK,
        PRE_PUSH_HOOK_BASE_SCRIPT,
        mojo -> mojo.prePushHookContent,
        mojo -> mojo.prePushCommandMavenPrefix,
        mojo -> mojo.prePushEnvVarToPropagate,
        mojo -> mojo.prePushPropertiesToPropagate),
    POST_COMMIT(
        "post-commit",
        BASE_PLUGIN_POST_COMMIT_HOOK,
        POST_COMMIT_HOOK_BASE_SCRIPT,
        mojo -> mojo.postCommitHookContent,
        mojo -> mojo.postCommitCommandMavenPrefix,
        mojo -> mojo.postCommitEnvVarToPropagate,
        mojo -> mojo.postCommitPropertiesToPropagate);

    private final String name;
    private final String pluginHookFile;
    private final String baseScript;
    private final Function<InstallHooksMojo, String> hookContentGetter;
    private final Function<InstallHooksMojo, Boolean> commandMavenPrefixGetter;
    private final Function<InstallHooksMojo, String[]> envVarToPropagateGetter;
    private final Function<InstallHooksMojo, String[]> propertiesToPropagateGetter;

    HookType(
        String name,
        String pluginHookFile,
        String baseScript,
        Function<InstallHooksMojo, String> hookContentGetter,
        Function<InstallHooksMojo, Boolean> commandMavenPrefixGetter,
        Function<InstallHooksMojo, String[]> envVarToPropagateGetter,
        Function<InstallHooksMojo, String[]> propertiesToPropagateGetter) {
      this.name = name;
      this.pluginHookFile = pluginHookFile;
      this.baseScript = baseScript;
      this.hookContentGetter = hookContentGetter;
      this.commandMavenPrefixGetter = commandMavenPrefixGetter;
      this.envVarToPropagateGetter = envVarToPropagateGetter;
      this.propertiesToPropagateGetter = propertiesToPropagateGetter;
    }

    public String getName() {
      return name;
    }

    public String getPluginHookFile() {
      return pluginHookFile;
    }

    public String getBaseScript() {
      return baseScript;
    }
  }

  private static final String BASE_PLUGIN_PRE_COMMIT_HOOK = "git-hook.pre-commit.sh";
  private static final String PRE_COMMIT_HOOK_BASE_SCRIPT = "pre-commit";
  private static final String BASE_PLUGIN_PRE_PUSH_HOOK = "git-hook.pre-push.sh";
  private static final String PRE_PUSH_HOOK_BASE_SCRIPT = "pre-push";
  private static final String BASE_PLUGIN_POST_COMMIT_HOOK = "git-hook.post-commit.sh";
  private static final String POST_COMMIT_HOOK_BASE_SCRIPT = "post-commit";

  private final ExecutableManager executableManager = new ExecutableManager(this::getLog);
  private final MavenEnvironment mavenEnvironment = new MavenEnvironment(this::getLog);

  /** Skip execution of this goal */
  @Parameter(property = "ghmp.skip", defaultValue = "false")
  private boolean skip;

  /**
   * True to truncate hooks base scripts before each install. <br>
   * Do not use this option if any other system or human manipulate the hooks
   */
  @Parameter(property = "ghmp.truncateHooksBaseScripts", defaultValue = "false")
  private boolean truncateHooksBaseScripts;

  /** The list of properties to propagate to the pre-push hooks */
  @Parameter(property = "ghmp.prePushPropertiesToPropagate")
  private String[] prePushPropertiesToPropagate;

  @Parameter(property = "ghmp.debug", defaultValue = "false")
  private boolean debug;

  /**
   * Whether to use Maven command prefix for pre-commit hooks. When true, the hook will execute
   * maven commands. When false, the hook will execute the hookPipeline directly.
   */
  @Parameter(property = "ghmp.preCommitCommandMavenPrefix", defaultValue = "false")
  private boolean preCommitCommandMavenPrefix;

  /**
   * Content to execute for the pre-commit hook. If this is empty, no pre-commit hook will be
   * installed. If preCommitCommandMavenPrefix is true, this will be executed as Maven command. If
   * preCommitCommandMavenPrefix is false, this will be executed as direct command.
   */
  @Parameter(property = "ghmp.preCommitHookContent", defaultValue = "")
  private String preCommitHookContent;

  /** The list of environment variables to propagate to the pre-commit hooks */
  @Parameter(property = "ghmp.preCommitEnvVarToPropagate")
  private String[] preCommitEnvVarToPropagate;

  /**
   * Whether to use Maven command prefix for pre-push hooks. When true, the hook will execute maven
   * commands. When false, the hook will execute the hookPipeline directly.
   */
  @Parameter(property = "ghmp.prePushCommandMavenPrefix", defaultValue = "false")
  private boolean prePushCommandMavenPrefix;

  /** The list of properties to propagate to the pre-commit hooks */
  @Parameter(property = "ghmp.preCommitPropertiesToPropagate")
  private String[] preCommitPropertiesToPropagate;

  /**
   * Content to execute for the pre-push hook. If this is empty, no pre-push hook will be installed.
   * If prePushCommandMavenPrefix is true, this will be executed as Maven command. If
   * prePushCommandMavenPrefix is false, this will be executed as direct command.
   */
  @Parameter(property = "ghmp.prePushHookContent", defaultValue = "")
  private String prePushHookContent;

  /** The list of environment variables to propagate to the pre-push hooks */
  @Parameter(property = "ghmp.prePushEnvVarToPropagate")
  private String[] prePushEnvVarToPropagate;

  /**
   * Whether to use Maven command prefix for post-commit hooks. When true, the hook will execute
   * maven commands. When false, the hook will execute the hookPipeline directly.
   */
  @Parameter(property = "ghmp.postCommitCommandMavenPrefix", defaultValue = "false")
  private boolean postCommitCommandMavenPrefix;

  /**
   * Content to execute for the post-commit hook. If this is empty, no post-commit hook will be
   * installed. If postCommitCommandMavenPrefix is true, this will be executed as Maven command. If
   * postCommitCommandMavenPrefix is false, this will be executed as direct command.
   */
  @Parameter(property = "ghmp.postCommitHookContent", defaultValue = "")
  private String postCommitHookContent;

  /** The list of environment variables to propagate to the post-commit hooks */
  @Parameter(property = "ghmp.postCommitEnvVarToPropagate")
  private String[] postCommitEnvVarToPropagate;

  /** The list of properties to propagate to the post-commit hooks */
  @Parameter(property = "ghmp.postCommitPropertiesToPropagate")
  private String[] postCommitPropertiesToPropagate;

  public void execute() throws MojoExecutionException {
    if (!isExecutionRoot()) {
      getLog().debug("Not in execution root. Do not execute.");
      return;
    }
    if (skip) {
      Log log = getLog();
      if (log.isInfoEnabled()) {
        log.info("Skipped install git hooks");
      }
      return;
    }

    try {
      getLog().info("Installing git hooks");
      doExecute();
      getLog().info("Installed git hooks");
    } catch (Exception e) {
      throw new MojoExecutionException(e.getMessage(), e);
    }
  }

  private void doExecute() throws IOException {
    Path hooksDirectory = prepareHooksDirectory();

    // Install pre-commit hook if content is provided
    if (preCommitHookContent != null && !preCommitHookContent.trim().isEmpty()) {
      writePluginHook(hooksDirectory, HookType.PRE_COMMIT);
      configureHookBaseScript(hooksDirectory, HookType.PRE_COMMIT);
    }

    // Install pre-push hook if content is provided
    if (prePushHookContent != null && !prePushHookContent.trim().isEmpty()) {
      writePluginHook(hooksDirectory, HookType.PRE_PUSH);
      configureHookBaseScript(hooksDirectory, HookType.PRE_PUSH);
    }

    // Install post-commit hook if content is provided
    if (postCommitHookContent != null && !postCommitHookContent.trim().isEmpty()) {
      writePluginHook(hooksDirectory, HookType.POST_COMMIT);
      configureHookBaseScript(hooksDirectory, HookType.POST_COMMIT);
    }
  }

  private void writePluginHook(Path hooksDirectory, HookType hookType) throws IOException {
    getLog().debug("Writing plugin " + hookType.getName() + " hook file");
    String hookContent = generateHookContent(hookType);

    Path hookFile = hooksDirectory.resolve(pluginHookFileName(hookType));
    executableManager.getOrCreateExecutableScript(hookFile).truncate().write(hookContent);
    getLog().debug("Written plugin " + hookType.getName() + " hook file");
  }

  private void configureHookBaseScript(Path hooksDirectory, HookType hookType) throws IOException {
    Executable baseHook =
        executableManager.getOrCreateExecutableScript(
            hooksDirectory.resolve(hookType.getBaseScript()));
    getLog().debug("Configuring '" + baseHook + "' for " + hookType.getName());
    if (truncateHooksBaseScripts) {
      baseHook.truncate();
    } else {
      baseHook.removeCommandCall(hookBaseScriptCall(hookType));
    }
    baseHook.appendCommandCall(hookBaseScriptCall(hookType));
  }

  private String buildAdditionalMavenArguments(String[] propertiesToPropagate) {
    Stream<String> propagatedProperties =
        ofNullable(propertiesToPropagate)
            .map(Arrays::asList)
            .orElse(Collections.emptyList())
            .stream()
            .filter(prop -> System.getProperty(prop) != null)
            .map(prop -> "-D" + prop + "=" + System.getProperty(prop));

    return propagatedProperties.collect(Collectors.joining(" "));
  }

  private String generateHookContent(HookType hookType) {
    StringBuilder content = new StringBuilder();
    content.append("#!/bin/bash\n");
    content.append("set -e\n");
    content.append("\n");

    // Export environment variables
    addEnvironmentVariables(content, hookType);
    content.append("\n");

    // Get hook-specific configuration
    String hookContent = hookType.hookContentGetter.apply(this);
    boolean commandMavenPrefix = hookType.commandMavenPrefixGetter.apply(this);

    // Only generate commands if hookContent is not empty
    if (hookContent != null && !hookContent.isEmpty()) {
      if (commandMavenPrefix) {
        // Maven mode
        String mavenExecutable =
            unixifyPath(mavenEnvironment.getMavenExecutable(debug).toAbsolutePath());
        content.append(mavenExecutable);
        content.append(" -f ");
        content.append(unixifyPath(pomFile().toAbsolutePath()));

        // Get hook-specific properties to propagate
        String[] propertiesToPropagate = hookType.propertiesToPropagateGetter.apply(this);
        String additionalArgs = buildAdditionalMavenArguments(propertiesToPropagate);
        if (!additionalArgs.isEmpty()) {
          content.append(" ").append(additionalArgs);
        }

        content.append(" ").append(hookContent);
      } else {
        // Direct command mode
        content.append(hookContent);
      }
      content.append("\n");
    }
    // If hookContent is empty, create empty script (just bash header and env vars)

    return content.toString();
  }

  private String unixifyPath(Path path) {
    String result = path.toAbsolutePath().toString();
    return "\"" + result.replace("\\", "/") + "\"";
  }

  private void addEnvironmentVariables(StringBuilder content, HookType hookType) {
    // Export specified environment variables if available
    String[] envVarsToPropagate = hookType.envVarToPropagateGetter.apply(this);
    if (envVarsToPropagate != null) {
      for (String envVar : envVarsToPropagate) {
        if (envVar != null && !envVar.trim().isEmpty()) {
          String envValue = System.getenv(envVar.trim());
          if (envValue != null && !envValue.isEmpty()) {
            content
                .append("export ")
                .append(envVar.trim())
                .append("=\"")
                .append(envValue)
                .append("\"\n");
          }
        }
      }
    }
  }

  private Path prepareHooksDirectory() {
    getLog().debug("Preparing git hook directory");
    Path hooksDirectory = getOrCreateHooksDirectory();
    getLog().debug("Prepared git hook directory");
    return hooksDirectory;
  }

  private String hookBaseScriptCall(HookType hookType) {
    return "$(git rev-parse --git-dir)/" + HOOKS_DIR + "/" + pluginHookFileName(hookType);
  }

  private String pluginHookFileName(HookType hookType) {
    return artifactId() + "." + hookType.getPluginHookFile();
  }

  private String pluginPreCommitHookFileName() {
    return pluginHookFileName(HookType.PRE_COMMIT);
  }
}
