package com.igormaznitsa.mvngolang;

import static com.igormaznitsa.mvngolang.utils.FileUtils.getCanonicalPath;

import java.nio.file.Path;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractFileProcessingMojo extends AbstractCommonMojo {
  /**
   * Fail on error.
   *
   * @since 1.0.0
   */
  @Parameter(name = "failOnError", defaultValue = "true")
  private boolean failOnError;
  /**
   * Ensure all listed folders in bounds of project folder tree.
   *
   * @since 1.0.0
   */
  @Parameter(name = "projectBound", defaultValue = "true")
  private boolean projectBound;
  /**
   * Follow symbol links.
   *
   * @since 1.0.0
   */
  @Parameter(name = "followSymLinks", defaultValue = "false")
  private boolean followSymLinks;

  public boolean isFollowSymLinks() {
    return this.followSymLinks;
  }

  public boolean isFailOnError() {
    return this.failOnError;
  }

  public boolean isProjectBound() {
    return this.projectBound;
  }

  /**
   * Check that the path in bounds of the project file tree.
   *
   * @param path path to be checked, must not be null.
   * @throws IllegalStateException if the file is not in bounds
   */
  protected void assertProjectBound(final Path path) {
    final Path projectPath = this.baseDir.toPath().toAbsolutePath().normalize();
    final Path inputPath = getCanonicalPath(path).toAbsolutePath().normalize();
    if (!inputPath.startsWith(projectPath)) {
      throw new IllegalStateException(
          String.format("%s must be within the project directory.", path));
    }
  }

}
