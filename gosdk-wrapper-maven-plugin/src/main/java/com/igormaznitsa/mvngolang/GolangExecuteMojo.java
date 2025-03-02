package com.igormaznitsa.mvngolang;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Mojo loads and caching GoSDK and executing its selected tool.
 *
 * @since 1.0.0
 */
@Mojo(name = "execute", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GolangExecuteMojo extends AbstractGolangToolExecuteMojo {

  /**
   * Command to be executed.
   *
   * @since 1.0.0
   */
  @Parameter(name = "command", defaultValue = "go")
  private String command;

  /**
   * Skip execution of the mojo.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.execute.skip", name = "skip", defaultValue = "false")
  private boolean skip;

  @Override
  protected boolean isSkip() {
    return this.skip;
  }

  @Override
  protected Path findCommand(final Path goSdkFolder, final Path jdkFolder) throws IOException {
    this.logOptional("GoSDK path: " + goSdkFolder);
    this.logDebug("Find command: " + this.command);
    if (isNullOrEmpty(this.command)) {
      throw new IllegalArgumentException("Command must be provided");
    }
    final Optional<Path> path = findExecutable(this.command.trim(), goSdkFolder);
    if (path.isPresent()) {
      this.logOptional("Found file for command: " + path.get());
      return path.get();
    } else {
      this.logError("Can't find any executable file as command: " + this.command);
      return null;
    }
  }
}
