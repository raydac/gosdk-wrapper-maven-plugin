package com.igormaznitsa.mvngolang;

import static java.lang.Long.toHexString;
import static java.lang.System.lineSeparator;
import static java.util.Objects.requireNonNullElse;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;

public abstract class AbstractGolangToolExecuteMojo extends AbstractGolangSdkAwareMojo {

  /**
   * Working directory. This directory will be used as the working directory for the executed process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "workDir", defaultValue = "${project.basedir}${file.separator}src")
  private String workDir;

  /**
   * Delay in milliseconds after process exit detection to collect internal buffers before processing threads interruptions.
   *
   * @since 1.0.5
   */
  @Parameter(name = "processExitLingerMs", defaultValue = "100")
  private long processExitLingerMs;

  /**
   * Try to make the found command file executable.
   *
   * @since 1.0.3
   */
  @Parameter(name = "makeExecutable", defaultValue = "false")
  private boolean makeExecutable;

  /**
   * Automatically add the internal GOPATH to the environment if none is already provided.
   * The GOPATH will be [storeFolder]/.go_path
   *
   * @see #storeFolder
   * @since 1.0.3
   */
  @Parameter(property = "mvn.golang.may.add.internal.gopath", name = "mayAddInternalGOPATH", defaultValue = "true")
  private boolean mayAddInternalGOPATH;

  /**
   * List of environment variables to be removed. All listed variables will be removed from the executing process's environment.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envRemove")
  private List<String> envRemove;

  /**
   * List of environment variable values to be added or replaced. All values will be added or will replace existing ones in the environment of the started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "env")
  private Map<String, String> env;

  /**
   * List of environment variable values to be added as the first entry to existing environment variables or set if they do not exist.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envFirst")
  private Map<String, String> envFirst;

  /**
   * List of environment variable values to be added as the last entry to existing environment variables or set if they do not exist.
   *
   * @since 1.0.0
   */
  @Parameter(name = "envLast")
  private Map<String, String> envLast;

  /**
   * Timeout for the started process in milliseconds. If 0 or negative, no timeout is applied.
   *
   * @since 1.0.0
   */
  @Parameter(name = "processTimeout", defaultValue = "0")
  private long processTimeout;

  /**
   * List of command-line arguments for executing the command.
   *
   * @since 1.0.0
   */
  @Parameter(name = "args")
  private List<String> args;

  /**
   * File to log all standard output from the started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "logFileStd")
  private String logFileStd;

  /**
   * File to log all error output from the started process.
   *
   * @since 1.0.0
   */
  @Parameter(name = "logFileErr")
  private String logFileErr;

  /**
   * Expected exit status for the started process. If the exit status is unexpected, the execution is considered failed.
   *
   * @since 1.0.0
   */
  @Parameter(name = "expectedExitCode", defaultValue = "0")
  private int expectedExitCode;

  /**
   * Hide process output in the Maven log. The file log will continue to work, but the output will be disabled in the Maven log.
   *
   * @since 1.0.0
   */
  @Parameter(name = "hideProcessOutput", defaultValue = "false")
  private boolean hideProcessOutput;

  protected void ensureParentFolderExists(@Nonnull final File file) throws MojoExecutionException {
    final File parent = file.getParentFile();
    if (parent == null) {
      throw new MojoExecutionException("Unable to find the parent folder for the file: " + file);
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

    final List<String> cliList = new ArrayList<>();

    final Path executable = this.findCommand(goSdkFolder, Path.of(System.getProperty("java.home")));

    if (executable == null) {
      throw new MojoFailureException(
          "Executable command file not found. Check the log for details.");
    } else {
      this.logDebug("Command file path provided: " + executable);
    }

    if (!Files.isExecutable(executable)) {
      if (this.makeExecutable) {
        this.logOptional("Attempting to set the command file as executable: " + executable);
        if (executable.toFile().setExecutable(true)) {
          this.logWarn("The command file has been set as executable: " + executable);
        } else {
          throw new MojoFailureException(
              "Unable to set the command file as executable: " + executable);
        }
      } else {
        throw new MojoExecutionException("The command file is not executable: " + executable);
      }
    }

    cliList.add(executable.toString());
    if (args != null) {
      cliList.addAll(args);
    }

    this.logInfo("Command line arguments prepared: " + cliList);

    final ProcessBuilder processBuilder = new ProcessBuilder(cliList);

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

    if (!processBuilder.environment().containsKey("GOPATH") && this.mayAddInternalGOPATH) {
      final File defaultGoPath = goSdkFolder.toFile();
      if (processBuilder.environment().put("GOPATH", defaultGoPath.getAbsolutePath()) == null) {
        this.logInfo("GOPATH not found; defaulting to internal folder: " + defaultGoPath);
      }
    }

    final String environmentInfo = "Process builder environment is set up" +
        lineSeparator() + "--------------------------" + lineSeparator() +
        processBuilder.environment().entrySet().stream()
            .map(x -> String.format("\t%s=%s", x.getKey(), x.getValue()))
            .collect(Collectors.joining(lineSeparator())) +
        lineSeparator() + "--------------------------";
    if (this.verbose) {
      this.logInfo(environmentInfo);
    } else {
      this.logDebug(environmentInfo);
    }

    final File workDirectory = new File(this.workDir);
    if (!workDirectory.isDirectory()) {
      throw new MojoFailureException("Unable to find the work directory: " + workDirectory);
    }
    processBuilder.directory(workDirectory);
    this.logOptional("Work directory: " + workDir);

    processBuilder.redirectErrorStream(true);

    final File targetOutputFile;
    final File targetErrorFile;

    if (isNullOrEmpty(this.logFileErr)) {
      targetErrorFile = null;
    } else {
      targetErrorFile = new File(this.logFileErr.trim());
      this.ensureParentFolderExists(targetErrorFile);
      this.logInfo("Redirecting process error output to: " + targetErrorFile);
    }

    if (isNullOrEmpty(this.logFileStd)) {
      targetOutputFile = null;
    } else {
      targetOutputFile = new File(this.logFileStd.trim());
      this.ensureParentFolderExists(targetOutputFile);
      this.logInfo("Redirecting process standard output to: " + targetOutputFile);
    }

    final String localId =
        requireNonNullElse(this.execution.getExecutionId(), "undefined") + '-' +
            toHexString(System.nanoTime()).toUpperCase(Locale.ENGLISH);

    final Thread threadStdErr;
    final Thread threadStdOut;
    final Process process;

    final Lock stdErrLock = new ReentrantLock(true);
    final Lock stdOutLock = new ReentrantLock(true);
    try {
      this.logInfo("Starting command");
      process = processBuilder.start();
      threadStdErr =
          this.prepareCatchStream("thread-process-stderr-" + localId, process.getErrorStream(),
              (line, eol) -> {
                if (!this.hideProcessOutput) {
                  this.logWarn(">stderr: " + line);
                }
                if (targetErrorFile != null) {
                  stdErrLock.lock();
                  try (FileOutputStream outputFile = new FileOutputStream(targetErrorFile, true)) {
                    outputFile.write(
                        (line + (eol ? lineSeparator() : "")).getBytes(Charset.defaultCharset()));
                    outputFile.flush();
                  } catch (IOException ex) {
                    this.logError("Unable to append record to log stderr file.: " + ex);
                  } finally {
                    stdErrLock.unlock();
                  }
                }
              });
      threadStdOut =
          this.prepareCatchStream("thread-process-stdout-" + localId, process.getInputStream(),
              (line, eol) -> {
                if (!this.hideProcessOutput) {
                  this.logInfo(">stdout: " + line);
                }
                if (targetOutputFile != null) {
                  stdOutLock.lock();
                  try (FileOutputStream outputFile = new FileOutputStream(targetOutputFile, true)) {
                    outputFile.write(
                        (line + (eol ? lineSeparator() : "")).getBytes(Charset.defaultCharset()));
                    outputFile.flush();
                  } catch (IOException ex) {
                    this.logError("Unable to append record to log stdout file: " + ex);
                  } finally {
                    stdOutLock.unlock();
                  }
                }
              });
      threadStdErr.start();
      threadStdOut.start();
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
      throw new MojoFailureException("Unable to start process due to exception.", ex);
    }

    if (this.hideProcessOutput) {
      this.logInfo("Hide process output");
    }

    final int exitCode;
    try {
      if (this.processTimeout <= 0L) {
        this.logDebug("Waiting process, localId=" + localId);
        exitCode = process.waitFor();
        this.logDebug("Exit code: " + exitCode);
      } else {
        final boolean result = process.waitFor(this.processTimeout, TimeUnit.MILLISECONDS);
        if (result) {
          exitCode = process.exitValue();
          this.logDebug("Exit code: " + exitCode);
        } else {
          this.logWarn("Terminating the process due to timeout.");
          process.destroy();
          throw new MojoFailureException("Process timeout detected.");
        }
      }
    } catch (InterruptedException ex) {
      this.logWarn("Process interrupted");
      Thread.currentThread().interrupt();
      return;
    } finally {
      this.sleep(this.processExitLingerMs);
      stdOutLock.lock();
      try {
        threadStdOut.interrupt();
      } finally {
        stdOutLock.unlock();
      }
      stdErrLock.lock();
      try {
        threadStdErr.interrupt();
      } finally {
        stdErrLock.unlock();
      }
      try {
        this.logDebug("Waiting for end of log collecting threads");
        threadStdOut.join(5000L);
        threadStdErr.join(5000L);
      } catch (IllegalThreadStateException ex) {
        this.logWarn("Can't close std reading threads during 5 seconds");
      } catch (InterruptedException ex) {
        this.logError("Detected thread interruption");
        Thread.currentThread().interrupt();
      }
    }

    if (exitCode != this.expectedExitCode) {
      throw new MojoFailureException("Process exit code: " + exitCode);
    }
  }

  private Thread prepareCatchStream(
      final String threadId,
      final InputStream inputStream,
      final BiConsumer<String, Boolean> lineConsumer
  ) {
    final Thread thread = new Thread(() -> {
      this.logDebug("Start catchStream thread " + threadId);
      try (final BufferedReader reader = new BufferedReader(
          new InputStreamReader(inputStream,
              Charset.defaultCharset()))) {
        final StringBuilder buffer = new StringBuilder();
        while (!Thread.currentThread().isInterrupted()) {
          final int nextChar = reader.read();
          if (nextChar < 0) {
            break;
          }
          if (nextChar == '\n') {
            lineConsumer.accept(buffer.toString(), true);
            buffer.setLength(0);
          } else {
            buffer.append((char) nextChar);
          }
        }
        if (buffer.length() > 0) {
          lineConsumer.accept(buffer.toString(), false);
        }
      } catch (IOException ex) {
        this.logError(
            '(' + threadId + ") IOException during input stream read: " + ex.getMessage());
      } finally {
        this.logDebug("Completed catchStream thread " + threadId);
      }
    }, threadId);
    thread.setDaemon(true);
    return thread;
  }

  @SuppressWarnings("unused")
  @Nullable
  protected abstract Path findCommand(@Nonnull final Path goSdkFolder,
                                      @Nonnull final Path jdkFolder)
      throws IOException;

}
