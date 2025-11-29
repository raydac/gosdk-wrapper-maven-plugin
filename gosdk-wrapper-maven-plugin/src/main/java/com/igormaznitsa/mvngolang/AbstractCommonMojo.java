package com.igormaznitsa.mvngolang;

import static java.nio.file.Files.isRegularFile;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.lang3.SystemUtils;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ResolutionErrorHandler;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.repository.RepositorySystem;
import org.apache.maven.settings.Settings;

public abstract class AbstractCommonMojo extends AbstractMojo {

  protected static final String DEFAULT_GO_PATH_FOLDER = ".go_path";
  @Parameter(defaultValue = "${project.basedir}", readonly = true)
  protected File baseDir;
  @Parameter(defaultValue = "${settings}", readonly = true)
  protected Settings settings;
  @Parameter(defaultValue = "${session}", readonly = true, required = true)
  protected MavenSession session;
  @Parameter(defaultValue = "${mojoExecution}", readonly = true, required = true)
  protected MojoExecution execution;
  @Parameter(readonly = true, defaultValue = "${project}", required = true)
  protected MavenProject project;
  /**
   * Folder caching downloaded and unpacked GoSDKs.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.store.folder", defaultValue = "${user.home}${file.separator}.mvnGoLang", name = "storeFolder")
  protected File storeFolder;

  /**
   * Make verbose log output.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.verbose", name = "verbose", defaultValue = "false")
  protected boolean verbose;

  /**
   * List of messages to be printed into log as INFO.
   *
   * @since 1.0.3
   */
  @Parameter(name = "echo")
  protected List<String> echo;

  /**
   * List of messages to be printed into log as WARN.
   *
   * @since 1.0.3
   */
  @Parameter(name = "echoWarn")
  protected List<String> echoWarn;

  /**
   * Enable tracing messages into debug log ,active only if debug mode activated.
   *
   * @since 1.0.2
   */
  @Parameter(property = "mvn.golang.trace", name = "trace", defaultValue = "false")
  protected boolean trace;

  /**
   * A factory for Maven artifact definitions (former ArtifactFactory).
   *
   * @since 1.0.4
   */
  @Component
  protected RepositorySystem repositorySystem;

  /**
   * This is the path to the local maven {@code repository}.
   *
   * @since @since 1.0.4
   */
  @Parameter(
      required = true,
      readonly = true,
      property = "localRepository"
  )
  protected ArtifactRepository localRepository;

  /**
   * Remote repositories for artifact resolution.
   *
   * @since @since 1.0.4
   */
  @Parameter(
      required = true,
      readonly = true,
      defaultValue = "${project.remoteArtifactRepositories}"
  )
  protected List<ArtifactRepository> remoteRepositories;

  /**
   * A component that handles resolution errors.
   *
   * @since @since 1.0.4
   */
  @Component
  protected ResolutionErrorHandler resolutionErrorHandler;

  protected static boolean isNullOrEmpty(final String text) {
    return text == null || text.isBlank();
  }

  protected static String findArchiveExtensionForOs() {
    return SystemUtils.IS_OS_WINDOWS ? "zip" : "tar.gz";
  }

  protected static List<Path> findExecutable(
      final String fileName,
      final List<Path> folders,
      final boolean findInBinSubfolder,
      final boolean walkSubfolders) throws IOException {
    if (fileName == null) {
      throw new NullPointerException("File name is null");
    }

    if (SystemUtils.IS_OS_WINDOWS && fileName.contains(":")) {
      throw new IllegalArgumentException("Illegal file name: " + fileName);
    }

    if (fileName.contains("/") || fileName.contains("\\")) {
      final List<Path> result = new ArrayList<>();
      for (final Path p : folders) {
        final Path filePath = p.resolve(fileName);
        if (isRegularFile(filePath)) {
          result.add(filePath);
        }
      }
      return result;
    }

    final Set<String> variants;
    if (fileName.contains(".")) {
      variants = Set.of(fileName);
    } else {
      final List<String> possibleExtensions;
      if (SystemUtils.IS_OS_WINDOWS) {
        possibleExtensions = List.of(".exe", ".cmd", ".bat");
      } else {
        possibleExtensions = List.of(".sh", "");
      }
      variants = possibleExtensions.stream()
          .flatMap(x -> Stream.of(x, x.toUpperCase(Locale.ENGLISH)))
          .map(x -> fileName + x)
          .collect(toSet());
    }

    final List<Path> result = new ArrayList<>();
    if (findInBinSubfolder) {
      for (final Path path : folders) {
        final Path folderBin = path.resolve("bin");
        if (Files.isDirectory(folderBin)) {
          try (
              Stream<Path> walker = Files.walk(folderBin, walkSubfolders ? Integer.MAX_VALUE : 1)) {
            walker
                .filter(x -> isRegularFile(x) && variants.contains(x.getFileName().toString()))
                .forEach(result::add);
          }
        }
      }
    } else {
      for (final Path path : folders) {
        try (Stream<Path> walker = Files.walk(path, walkSubfolders ? Integer.MAX_VALUE : 1)) {
          walker.filter(x -> isRegularFile(x) && variants.contains(x.getFileName().toString()))
              .forEach(result::add);
        }
      }
    }
    return result;
  }

  protected abstract boolean isSkip();

  protected File makeDefaultGoPath() {
    return new File(this.storeFolder, DEFAULT_GO_PATH_FOLDER);
  }

  protected void logTrace(final String text) {
    if (text != null && this.trace && this.getLog().isDebugEnabled()) {
      this.getLog().debug(text);
    }
  }

  protected void logDebug(final String text) {
    if (text != null && this.getLog().isDebugEnabled()) {
      this.getLog().debug(text);
    }
  }

  protected void logOptional(final String text) {
    if (text != null && (this.verbose || this.getLog().isDebugEnabled())) {
      this.getLog().info(text);
    }
  }

  protected void logError(final String text) {
    if (text != null && this.getLog().isErrorEnabled()) {
      this.getLog().error(text);
    }
  }

  protected void logInfo(final String text) {
    if (text != null && this.getLog().isInfoEnabled()) {
      this.getLog().info(text);
    }
  }

  protected void logWarn(final String text) {
    if (text != null && this.getLog().isWarnEnabled()) {
      this.getLog().warn(text);
    }
  }

  @Override
  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (this.isSkip()) {
      this.logInfo("Skipping execution");
      return;
    }

    if (this.echo != null && !this.echo.isEmpty()) {
      this.echo.forEach(this::logInfo);
    }

    if (this.echoWarn != null && !this.echoWarn.isEmpty()) {
      this.echoWarn.forEach(this::logWarn);
    }

    this.doExecute();
  }

  protected void sleep(final long milliseconds) {
    try {
      this.logDebug("Do sleep for " + milliseconds + "ms");
      Thread.sleep(milliseconds);
    } catch (InterruptedException ex) {
      this.logWarn("Detected thread interruption");
      Thread.currentThread().interrupt();
    }
  }

  public abstract void doExecute() throws MojoExecutionException, MojoFailureException;
}
