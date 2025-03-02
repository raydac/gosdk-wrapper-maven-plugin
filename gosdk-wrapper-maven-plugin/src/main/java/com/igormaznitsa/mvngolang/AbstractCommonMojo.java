package com.igormaznitsa.mvngolang;

import java.io.File;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecution;
import org.apache.maven.plugins.annotations.Parameter;
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

  /**
   * Make verbose log output.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.verbose", name = "verbose", defaultValue = "false")
  protected boolean verbose;

  protected static boolean isNullOrEmpty(final String text) {
    return text == null || text.isBlank();
  }

  protected abstract boolean isSkip();

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
}
