package com.igormaznitsa.mvngolang;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractGolangToolExecuteMojo extends AbstractGolangSdkAwareMojo {

  /**
   * Work directory.
   *
   * @since 1.0.0
   */
  @Parameter(name = "workDir", defaultValue = "${project.basedir}${file.separator}src")
  private String workDir;

  /**
   * List of environment variable to be removed.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envRemove")
  private List<String> envRemove;

  /**
   * List of environment variable values to be added or replaced.
   *
   * @since 1.0.0
   */
  @Parameter(name = "env")
  private Map<String, String> env;

  /**
   * List of environment variable values to be added as first one to existing environment variables or set environment variable value if not exist.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envFirst")
  private Map<String, String> envFirst;

  /**
   * List of environment variable values to be added as last one to existing environment variables or set environment variable value if not exist.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envLast")
  private Map<String, String> envLast;

  /**
   * Timeout for started process. If 0 then no timeout.
   *
   * @since 1.0.0
   */
  @Parameter(name = "processTimeout", defaultValue = "0")
  private long processTimeout;

  /**
   * List of execute command line arguments.
   *
   * @since 1.0.0
   */
  @Parameter(name = "args")
  private List<String> args;

  /**
   * File to log all standard output from started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "logFileStd")
  private String logFileStd;

  /**
   * File to log all error output from started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "logFileErr")
  private String logFileErr;

  /**
   * Expected exit status for started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "expectedExitCode", defaultValue = "0")
  private int expectedExitCode;

  /**
   * Hide process output in maven log. File logs continue work.
   *
   * @since 1.0.0
   */
  @Parameter(name = "hideProcessOutput", defaultValue = "false")
  private boolean hideProcessOutput;

  protected void ensureParentFolderExists(@Nonnull final File file) throws MojoExecutionException {
    final File parent = file.getParentFile();
    if (parent == null) {
      throw new MojoExecutionException("Can't find parent folder for file: " + file);
    }
    if (parent.isDirectory()) {
      return;
    }
    this.logDebug("Making folder: " + parent);
    if (!parent.mkdirs()) {
      throw new MojoExecutionException("Can't create folder: " + parent);
    }
  }

  @Override
  protected void onMojoExecute(final Path goSdkFolder)
      throws IOException, MojoExecutionException, MojoFailureException {

    final List<String> cliListd = new ArrayList<>();

    final Path executable = this.findCommand(goSdkFolder, Path.of(System.getProperty("java.home")));

    if (executable == null) {
      throw new MojoExecutionException("Executable is null");
    } else {
      this.logDebug("Provided command file path: " + executable);
    }

    if (!Files.isExecutable(executable)) {
      throw new MojoExecutionException("Provided file is not executable: " + executable);
    }

    cliListd.add(executable.toString());
    if (args != null) {
      cliListd.addAll(args);
    }

    this.logInfo("Prepared command line arguments: " + cliListd);

    final ProcessBuilder processBuilder = new ProcessBuilder(cliListd);

    if (this.envRemove != null) {
      this.envRemove.forEach(x -> processBuilder.environment().remove(x));
    }
    if (this.env != null) {
      this.env.forEach((key, value) -> processBuilder.environment().put(key, value));
    }

    if (this.envFirst != null) {
      this.envFirst.forEach((key, value) -> {
        String newValue = processBuilder.environment().get(key);
        if (newValue == null) {
          newValue = value;
        } else {
          newValue = value + newValue;
        }
        processBuilder.environment().put(key, newValue);
      });
    }

    if (this.envLast != null) {
      this.envLast.forEach((key, value) -> {
        String newValue = processBuilder.environment().get(key);
        if (newValue == null) {
          newValue = value;
        } else {
          newValue = newValue + value;
        }
        processBuilder.environment().put(key, newValue);
      });
    }

    processBuilder.environment().forEach((k, v) -> this.logDebug("Environment: " + k + "=" + v));

    final File workDirectory = new File(this.workDir);
    if (!workDirectory.isDirectory()) {
      throw new MojoFailureException("Can't find work directory: " + workDirectory);
    }
    processBuilder.directory(workDirectory);
    this.logOptional("Work directory: " + workDir);

    processBuilder.redirectErrorStream(false);

    final File targetOutputFile;
    final File targetErrorFile;

    if (isNullOrEmpty(this.logFileErr)) {
      targetErrorFile = null;
    } else {
      targetErrorFile = new File(this.logFileErr.trim());
      this.ensureParentFolderExists(targetErrorFile);
      this.logInfo("Redirect process error output to: " + targetErrorFile);
    }

    if (isNullOrEmpty(this.logFileStd)) {
      targetOutputFile = null;
    } else {
      targetOutputFile = new File(this.logFileStd.trim());
      this.ensureParentFolderExists(targetOutputFile);
      this.logInfo("Redirect process standard output to: " + targetOutputFile);
    }

    final Process process;
    try {
      this.logInfo("Starting command");
      process = processBuilder.start();
      Thread.yield();

      Long pid = null;
      try {
        pid = process.pid();
      } catch (Exception ex) {
        this.logOptional("Can't get process PID: " + ex.getMessage());
      }

      ProcessHandle.Info processInfo = null;
      try {
        processInfo = process.info();
      } catch (Exception ex) {
        this.logOptional("Can't get process info: " + ex.getMessage());
      }

      this.logInfo(
          String.format("Process started: PID=%s, user=%s",
              (pid == null ? "<null>" : pid.toString()),
              (processInfo == null ? "<null>" : processInfo.user().orElse("<not available>"))
          )
      );
    } catch (Exception ex) {
      throw new MojoFailureException("Can't start process for exception", ex);
    }

    if (this.hideProcessOutput) {
      this.logInfo("Hide process output");
    }

    this.catchStream("thread-process-stderr", process.getErrorStream(), line -> {
      if (!this.hideProcessOutput) {
        this.logInfo(">stderr: " + line);
      }
      if (targetOutputFile != null) {
        try {
          FileUtils.write(targetOutputFile, line + System.lineSeparator(),
              Charset.defaultCharset(), true);
        } catch (IOException ex) {
          this.logError("Can't append record to log output file: " + ex.getMessage());
        }
      }
    });
    this.catchStream("thread-process-stdout", process.getInputStream(), line -> {
      if (!this.hideProcessOutput) {
        this.logError(">stdout: " + line);
      }
      if (targetErrorFile != null) {
        try {
          FileUtils.write(targetErrorFile, line + System.lineSeparator(),
              Charset.defaultCharset(), true);
        } catch (IOException ex) {
          this.logError("Can't append record to log err file: " + ex.getMessage());
        }
      }
    });

    final int exitCode;
    try {
      if (this.processTimeout == 0L) {
        exitCode = process.waitFor();
      } else {
        final boolean result = process.waitFor(this.processTimeout, TimeUnit.MILLISECONDS);
        if (result) {
          exitCode = process.exitValue();
        } else {
          this.logWarn("Destroying started process for timeout");
          process.destroy();
          throw new MojoFailureException("Detected process timeout");
        }
      }
    } catch (InterruptedException ex) {
      this.logWarn("Process interrupted");
      Thread.currentThread().interrupt();
      return;
    }

    if (exitCode != this.expectedExitCode) {
      throw new MojoFailureException("Process exit code: " + exitCode);
    }
  }

  private void catchStream(
      final String threadId,
      final InputStream inputStream,
      final Consumer<String> lineConsumer
  ) {
    // It is important to do this async as on some occasions processes might block until the input is read
    final Thread thread = new Thread(() -> {
      this.logDebug("Start catchStream thread " + threadId);
      try (final BufferedReader reader = new BufferedReader(
          new InputStreamReader(inputStream,
              Charset.defaultCharset()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          lineConsumer.accept(line);
        }
      } catch (IOException ex) {
        this.logError(
            '(' + threadId + ") IOException during input stream read: " + ex.getMessage());
      } finally {
        this.logDebug("Completed catchStream thread " + threadId);
      }
    }, threadId);
    thread.start();
  }

  @Nullable
  protected abstract Path findCommand(@Nonnull final Path goSdkFolder,
                                      @Nonnull final Path jdkFolder)
      throws IOException;

}
