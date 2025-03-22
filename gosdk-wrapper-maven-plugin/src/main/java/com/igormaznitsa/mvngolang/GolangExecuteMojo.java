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
 * The mojo loads and caches the GoSDK and executes its selected tool or a custom file.
 *
 * @since 1.0.0
 */
@Mojo(name = "execute", defaultPhase = LifecyclePhase.COMPILE, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class GolangExecuteMojo extends AbstractGolangToolExecuteMojo {

  /**
   * Command to be executed.
   * By default, the mojo will look for the command in the GoSDK folder and its bin subfolder. However, it is possible to provide a custom search path through the <strong>path</strong> parameter.
   * It can be just a name without an extension, in which case all executable extensions for the platform will be used to search. If a relative or absolute path is provided, that path will be used.
   *
   * @see #path
   * @since 1.0.0
   */
  @Parameter(name = "command", defaultValue = "go")
  private String command;

  /**
   * List of folders to find the command executable file. <strong>This is only for finding the executable file defined as the command and does not affect environment variables!</strong>
   * In this case, the GoSDK folder will be excluded from the search, and only the listed existing path folders will be processed for the search.   *
   *
   * @since 1.0.3
   */
  @Parameter(name = "path")
  private List<File> path;

  /**
   * Skip the execution of the mojo.
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
    this.logInfo("GoSDK folder: " + goSdkFolder);
    this.logDebug("Find command: " + this.command);
    if (isNullOrEmpty(this.command)) {
      throw new IllegalArgumentException("The command must be provided");
    }

    final List<Path> pathsToFind;
    final List<Path> foundExecutables;
    if (this.path == null || this.path.isEmpty()) {
      pathsToFind = List.of(goSdkFolder);
      this.logDebug("Path is not defined, use GoSDK folder: " + goSdkFolder);
      foundExecutables = findExecutable(this.command.trim(), pathsToFind, true, true);
    } else {
      pathsToFind = this.path.stream().filter(
          Objects::nonNull).map(File::toPath).collect(Collectors.toList());
      this.logOptional("Find in path: " +
          pathsToFind.stream().map(Path::toString).collect(joining(File.pathSeparator)));
      foundExecutables = findExecutable(this.command.trim(), pathsToFind, false, false);
    }

    if (foundExecutables.isEmpty()) {
      this.logError("Can't find command '" + this.command + "' in path: "
          + pathsToFind.stream().map(Path::toString).collect(joining(File.pathSeparator)));
      return null;
    } else if (foundExecutables.size() == 1) {
      final Path found = foundExecutables.get(0);
      this.logOptional("Found command executable file: " + found);
      return found;
    } else {
      this.logError(
          "Several executable files were unexpectedly found and recognized as the command: " +
              foundExecutables);
      return null;
    }
  }
}