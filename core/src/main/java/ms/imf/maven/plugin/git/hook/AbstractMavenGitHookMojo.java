package ms.imf.maven.plugin.git.hook;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * @author Réda Housni Alaoui
 */
public abstract class AbstractMavenGitHookMojo extends AbstractMojo {

  protected static final String HOOKS_DIR = "hooks";

  @Parameter(readonly = true, defaultValue = "${project}")
  private MavenProject currentProject;

  protected final boolean isExecutionRoot() {
    return currentProject.isExecutionRoot();
  }

  protected final String artifactId() {
    return currentProject.getArtifactId();
  }

  protected final Path pomFile() {
    return currentProject.getFile().toPath();
  }

  protected final Path gitBaseDir() {
    return currentProject.getBasedir().toPath();
  }

  protected final Path getOrCreateHooksDirectory() {
    Path gitDir = findGitDirectory();
    Path hooksDir = gitDir.resolve(HOOKS_DIR);
    try {
      Files.createDirectories(hooksDir);
    } catch (IOException e) {
      throw new MavenGitHookException("Failed to create hooks directory: " + hooksDir, e);
    }
    return hooksDir;
  }

  private Path findGitDirectory() {
    Path currentDir = gitBaseDir();
    while (currentDir != null) {
      Path gitDir = currentDir.resolve(".git");
      if (Files.exists(gitDir)) {
        if (Files.isDirectory(gitDir)) {
          return gitDir;
        } else {
          // Handle git worktree case - .git file contains path to actual git dir
          try {
            byte[] bytes = Files.readAllBytes(gitDir);
            String content = new String(bytes, StandardCharsets.UTF_8).trim();
            if (content.startsWith("gitdir: ")) {
              String gitDirPath = content.substring(8);
              Path actualGitDir = Paths.get(gitDirPath);
              if (!actualGitDir.isAbsolute()) {
                actualGitDir = currentDir.resolve(actualGitDir);
              }
              return actualGitDir;
            }
          } catch (IOException e) {
            throw new MavenGitHookException("Failed to read .git file: " + gitDir, e);
          }
        }
      }
      currentDir = currentDir.getParent();
    }
    throw new MavenGitHookException("Could not find .git directory");
  }
}
