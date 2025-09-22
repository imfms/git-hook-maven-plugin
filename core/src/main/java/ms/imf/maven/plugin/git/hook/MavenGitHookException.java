package ms.imf.maven.plugin.git.hook;

/**
 * @author Réda Housni Alaoui
 */
public class MavenGitHookException extends RuntimeException {

  public MavenGitHookException(Throwable cause) {
    super(cause);
  }

  public MavenGitHookException(String message, Throwable cause) {
    super(message, cause);
  }

  public MavenGitHookException(String message) {
    super(message);
  }
}
