package com.igormaznitsa.mvngolang;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
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
public class GolangDeleteFoldersMojo extends AbstractCommonMojo {

  private static final LinkOption[] LINK_OPTIONS_NO_FOLLOW_LINKS =
      new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
  private static final LinkOption[] LINK_OPTIONS_EMPTY = new LinkOption[0];
  private static final Set<PosixFilePermission> POSIX_ALL_PERMISSIONS = Set.of(
      PosixFilePermission.OTHERS_EXECUTE,
      PosixFilePermission.OTHERS_WRITE,
      PosixFilePermission.OTHERS_READ,
      PosixFilePermission.OWNER_EXECUTE,
      PosixFilePermission.OWNER_READ,
      PosixFilePermission.OWNER_WRITE,
      PosixFilePermission.GROUP_EXECUTE,
      PosixFilePermission.GROUP_WRITE,
      PosixFilePermission.GROUP_READ
  );
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
  /**
   * Follow symbol links.
   *
   * @since 1.0.0
   */
  @Parameter(name = "followSymLinks", defaultValue = "false")
  private boolean followSymLinks;
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

  private static Path getCanonicalPath(final Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return getCanonicalPath(path.getParent()).resolve(path.getFileName());
    }
  }

  private static void quietSetAllPosixPermission(final Path path) {
    if (path != null) {
      try {
        Files.setPosixFilePermissions(path, POSIX_ALL_PERMISSIONS);
      } catch (Exception ignored) {
      }
    }
  }

  private static void tryResetAllAttributes(final Path path, final LinkOption[] linkOptions)
      throws IOException {
    PathUtils.setReadOnly(path, false, linkOptions);
    final DosFileAttributeView dosView =
        PathUtils.getDosFileAttributeView(path, linkOptions);
    if (dosView != null) {
      dosView.setReadOnly(false);
      dosView.setArchive(false);
      dosView.setSystem(false);
      dosView.setHidden(false);
    }
    quietSetAllPosixPermission(path);
  }

  private static void makeWritable(final Path path, final LinkOption[] linkOptions)
      throws IOException {
    if (path == null) {
      return;
    }
    tryResetAllAttributes(path, linkOptions);
    if (Files.isDirectory(path, linkOptions)) {
      try (Stream<Path> stream = Files.list(path)) {
        stream.forEach(x -> {
              try {
                makeWritable(x, linkOptions);
              } catch (IOException ex) {
                throw new UncheckedIOException(ex.getMessage(), ex);
              }
            }
        );
      } catch (UncheckedIOException ex) {
        throw ex.getCause();
      }
    }
  }

  @Override
  protected boolean isSkip() {
    return this.skip;
  }

  private void assertProjectBound(final Path path) {
    final Path projectPath = this.baseDir.toPath().toAbsolutePath().normalize();
    final Path inputPath = getCanonicalPath(path).toAbsolutePath().normalize();
    if (!inputPath.startsWith(projectPath)) {
      throw new IllegalStateException(
          String.format("%s must be within the project directory.", path));
    }
  }

  @Override
  public void execute() throws MojoFailureException {
    final String message;
    if (this.forceDelete) {
      message = "Deleting the folder (force mode): ";
    } else {
      message = "Deleting the folder: ";
    }

    if (this.isSkip()) {
      this.logInfo("Delete folders is skipped");
    } else {
      if (this.folders != null) {
        try {
          this.folders.forEach(file -> {
            final Path path = file.toPath();
            if (this.projectBound) {
              this.assertProjectBound(path);
            }

            this.logOptional(message + path);
            if (Files.exists(path)) {
              if (Files.isDirectory(path)) {
                try {
                  if (this.forceDelete) {
                    this.logOptional("Making folder content writable: " + path);
                    try {
                      makeWritable(path,
                          this.followSymLinks ? LINK_OPTIONS_EMPTY : LINK_OPTIONS_NO_FOLLOW_LINKS);
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
                  if (this.failOnError) {
                    throw new UncheckedIOException("Can't delete folder: " + path, ex);
                  } else {
                    this.logWarn("Can't delete folder " + path + " (" + ex.getMessage() + ')');
                    this.getLog().debug("IOException during delete " + path, ex);
                  }
                }
              } else {
                if (this.failOnError) {
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

}
