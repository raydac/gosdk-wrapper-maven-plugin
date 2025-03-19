package com.igormaznitsa.mvngolang;

import com.igormaznitsa.mvngolang.utils.FileUtils;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.model.FileSet;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.codehaus.plexus.util.DirectoryScanner;

/**
 * Try to grant all permissions to the files defined through file sets.
 * This mojo traverses all files and attempts to provide all permissions to work with them, removing any flags that could make the files non-accessible.
 *
 * @since 1.0.2
 */
@Mojo(name = "give-all-permissions", defaultPhase = LifecyclePhase.PRE_CLEAN, threadSafe = true)
public class GiveAllPermissionsMojo extends AbstractFileProcessingMojo {

  /**
   * List of file sets defining the processed files.
   *
   * @since 1.0.2
   */
  @Parameter(name = "fileSets")
  private List<FileSet> fileSets;

  public static List<File> getFilesFromFileSet(final FileSet fileSet) {
    if (fileSet == null || fileSet.getDirectory() == null) {
      return List.of();
    }

    File directory = new File(fileSet.getDirectory());
    if (!directory.exists() || !directory.isDirectory()) {
      return List.of();
    }

    final DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(directory);

    final List<String> includes = fileSet.getIncludes();
    final List<String> excludes = fileSet.getExcludes();

    if (includes != null && !includes.isEmpty()) {
      scanner.setIncludes(includes.toArray(new String[0]));
    }
    if (excludes != null && !excludes.isEmpty()) {
      scanner.setExcludes(excludes.toArray(new String[0]));
    }

    scanner.scan();
    final String[] includedFiles = scanner.getIncludedFiles();

    final List<File> files = new ArrayList<>();
    for (final String relativePath : includedFiles) {
      files.add(new File(directory, relativePath));
    }

    return files;
  }

  @Override
  protected boolean isSkip() {
    return false;
  }

  @Override
  public void doExecute() throws MojoExecutionException, MojoFailureException {
    if (this.isSkip()) {
      this.logInfo("Skipped");
    } else {
      if (this.fileSets == null || this.fileSets.isEmpty()) {
        this.logWarn("No defined file sets");
      } else {
        for (final FileSet fs : this.fileSets) {
          for (final File file : getFilesFromFileSet(fs)) {
            if (!file.isFile()) {
              this.logWarn("Can't find file, skipping it: " + file);
              continue;
            }

            final Path path = file.toPath();
            this.logTrace("Processing file: " + path);
            if (this.isProjectBound()) {
              try {
                this.assertProjectBound(path);
              } catch (IllegalStateException ex) {
                if (this.isFailOnError()) {
                  throw new MojoFailureException("File is not in the project file tree: " + path);
                } else {
                  this.logWarn("File is not in the project file tree: " + path);
                }
              }
            }
            try {
              FileUtils.makeWritable(path, this.isFollowSymLinks());
            } catch (Exception ex) {
              if (this.isFailOnError()) {
                throw new MojoFailureException("Can't make the file writable: " + path, ex);
              } else {
                this.logWarn(
                    "Can't make the file writable: " + path + " (" + ex.getMessage() + ')');
              }
            }
          }
        }
      }
    }
  }
}
