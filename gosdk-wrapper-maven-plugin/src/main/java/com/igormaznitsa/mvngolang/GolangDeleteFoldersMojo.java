package com.igormaznitsa.mvngolang;

import static com.igormaznitsa.mvngolang.utils.FileUtils.LINK_OPTIONS_EMPTY;
import static com.igormaznitsa.mvngolang.utils.FileUtils.LINK_OPTIONS_NO_FOLLOW_LINKS;
import static com.igormaznitsa.mvngolang.utils.FileUtils.makeWritable;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.apache.commons.io.file.PathUtils;
import org.apache.commons.io.file.StandardDeleteOption;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * Delete listed folders, it provides way to force folder delete even if it contains read only content.
 *
 * @since 1.0.0
 */
@Mojo(name = "delete-folders", defaultPhase = LifecyclePhase.CLEAN, threadSafe = true)
public class GolangDeleteFoldersMojo extends AbstractFileProcessingMojo {

  /**
   * Skip execution of the mojo.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.delete.skip", name = "skip", defaultValue = "false")
  private boolean skip;
  /**
   * List of folder to be deleted,
   *
   * @since 1.0.0
   */
  @Parameter(name = "folders")
  private List<File> folders;
  /**
   * Ensure folder content deletable.
   *
   * @since 1.0.0
   */
  @Parameter(name = "forceDelete", defaultValue = "false")
  private boolean forceDelete;

  @Override
  protected boolean isSkip() {
    return this.skip;
  }

  @Override
  public void execute() throws MojoFailureException {
    if (this.isSkip()) {
      this.logInfo("Delete folders is skipped");
      return;
    }

    final String message;
    if (this.forceDelete) {
      message = "Deleting the folder (force mode): ";
    } else {
      message = "Deleting the folder: ";
    }

    if (this.folders != null) {
      try {
        this.folders.forEach(file -> {
          final Path path = file.toPath();
          if (this.isProjectBound()) {
            this.assertProjectBound(path);
          }

          this.logOptional(message + path);
          if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
              try {
                if (this.forceDelete) {
                  this.logOptional("Making folder content writable: " + path);
                  try {
                    makeWritable(path, this.followSymLinks);
                  } catch (IOException ex) {
                    throw new UncheckedIOException(ex.getMessage(), ex);
                  }
                  PathUtils.deleteDirectory(path,
                      this.followSymLinks ? LINK_OPTIONS_EMPTY : LINK_OPTIONS_NO_FOLLOW_LINKS,
                      StandardDeleteOption.OVERRIDE_READ_ONLY);
                } else {
                  PathUtils.deleteDirectory(path,
                      this.followSymLinks ? LINK_OPTIONS_EMPTY :
                          LINK_OPTIONS_NO_FOLLOW_LINKS);
                }
                this.logInfo("Successfully deleted folder: " + path);
              } catch (IOException ex) {
                if (this.isFailOnError()) {
                  throw new UncheckedIOException("Can't delete folder: " + path, ex);
                } else {
                  this.logWarn("Can't delete folder " + path + " (" + ex.getMessage() + ')');
                  this.getLog().debug("IOException during delete " + path, ex);
                }
              }
            } else {
              if (this.isFailOnError()) {
                throw new UncheckedIOException("It is not a folder: " + path,
                    new IOException("It is not a folder: " + path));
              } else {
                this.logWarn("It is not a folder: " + path);
              }
            }
          } else {
            this.logInfo("There is no folder: " + path);
          }
        });
      } catch (UncheckedIOException ex) {
        throw new MojoFailureException(ex.getMessage(), ex.getCause());
      }
    }
  }

}
