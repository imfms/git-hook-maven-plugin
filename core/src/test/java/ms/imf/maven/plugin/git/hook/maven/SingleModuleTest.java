package ms.imf.maven.plugin.git.hook.maven;

import io.takari.maven.testing.executor.MavenRuntime;

public class SingleModuleTest extends AbstractMavenModuleTest {

  public SingleModuleTest(MavenRuntime.MavenRuntimeBuilder mavenBuilder) throws Exception {
    super(mavenBuilder, "single-module", "");
  }
}
