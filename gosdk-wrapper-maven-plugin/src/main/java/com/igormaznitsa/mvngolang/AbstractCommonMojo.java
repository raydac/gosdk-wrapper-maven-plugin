package com.igormaznitsa.mvngolang;

import java.io.File;
import java.util.List;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.settings.Settings;

public abstract class AbstractCommonMojo extends AbstractMojo {

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

  protected static final String DEFAULT_GO_PATH_FOLDER = ".go_path";

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

  protected static boolean isNullOrEmpty(final String text) {
    return text == null || text.isBlank();
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
