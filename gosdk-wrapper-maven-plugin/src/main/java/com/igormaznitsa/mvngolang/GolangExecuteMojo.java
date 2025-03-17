package com.igormaznitsa.mvngolang;

import static java.util.stream.Collectors.joining;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
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
   * By default, the mojo will be looking for the command in GoSDK folder and its bin subfolder, but it is possible to provide custom path for search through <strong>path</strong> parameter.
   * It can be just a name without extension and in the case all executable extensions for the platform will be used to search. If it is some relative or absolute path then the path will be used.
   *
   * @since 1.0.0
   */
  @Parameter(name = "command", defaultValue = "go")
  private String command;

  /**
   * List of folders to find the command executable file. <strong>It is only to find executable file defined as command and doesn't affect environment variables!</strong>
   * In the case the GoSDK folder will be excluded from search and only the listed existing path folders will be processed for search.
   *
   * @since 1.0.3
   */
  @Parameter(name = "path")
  private List<File> path;

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
  @Nullable
  protected Path findCommand(@Nonnull final Path goSdkFolder, @Nonnull final Path jdkFolder)
      throws IOException {
    this.logOptional("GoSDK path: " + goSdkFolder);
    this.logDebug("Find command: " + this.command);
    if (isNullOrEmpty(this.command)) {
      throw new IllegalArgumentException("Command must be provided");
    }
    final List<Path> foundExecutables;
    if (this.path == null || this.path.isEmpty()) {
      this.logDebug("Path is not defined, use GoSDK folder: " + goSdkFolder);
      foundExecutables = findExecutable(this.command.trim(), List.of(goSdkFolder), true, true);
    } else {
      this.logOptional("Find in path: " +
          this.path.stream().map(File::toString).collect(joining(File.pathSeparator)));
      foundExecutables = findExecutable(this.command.trim(), this.path.stream().filter(
          Objects::nonNull).map(File::toPath).collect(Collectors.toList()), false, false);
    }
    if (foundExecutables.isEmpty()) {
      this.logError("Can't find command '" + this.command + "' in path: "
          + this.path.stream().map(File::toString).collect(joining(File.pathSeparator)));
      return null;
    } else if (foundExecutables.size() == 1) {
      final Path found = foundExecutables.get(0);
      this.logOptional("Found command executable file: " + found);
      return found;
    } else {
      this.logError(
          "Unexpectedly found several executable files to be recognized as the command: " +
              foundExecutables);
      return null;
    }
  }
}