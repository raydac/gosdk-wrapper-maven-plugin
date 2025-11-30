package com.igormaznitsa.mvngolang;

import static java.util.stream.Collectors.joining;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;

/**
 * The mojo just loads and caches the GoSDK and allows export paths as named maven project properties to use them in another mojo and calls.
 *
 * @since 1.1.1
 */
@Mojo(name = "cache-sdk", defaultPhase = LifecyclePhase.INITIALIZE, threadSafe = true)
public class GolangCacheSdkCacheMojo extends AbstractGolangSdkAwareMojo {

  /**
   * Skip execution of the mojo.
   */
  @Parameter(property = "mvn.golang.cache.skip", name = "skip", defaultValue = "false")
  private boolean skip;

  /**
   * If it is not empty then it defines a project property name to export cached GoSDK path as an absolute path.
   */
  @Parameter(name = "propertyGoSdkPath", defaultValue = "")
  private String propertyGoSdkPath;

  /**
   * If it is not empty then it defines a project property name to the default go path folder as an absolute path.
   */
  @Parameter(name = "propertyDefaultGoPath", defaultValue = "")
  private String propertyDefaultGoPath;

  /**
   * If it is not empty then it defines a project property name to export file path to the executable go command file found in the target GoSDK.
   */
  @Parameter(name = "propertyGoCommandPath", defaultValue = "")
  private String propertyGoCommandPath;

  private static String getSafeString(final String value) {
    return value == null ? "" : value.trim();
  }

  @Override
  protected void onMojoExecute(final Path goSdkFolder)
      throws IOException, MojoFailureException {

    final String namePropertySdkPath = getSafeString(this.propertyGoSdkPath);
    final String namePropertyDefaultGoPath = getSafeString(this.propertyDefaultGoPath);
    final String namePropertyGoCommandPath = getSafeString(this.propertyGoCommandPath);

    if (!namePropertySdkPath.isEmpty()) {
      final String path = goSdkFolder.toAbsolutePath().toString();
      this.logInfo(
          "Exporting the cached GoSDK path as a project property '" + namePropertySdkPath + "': " +
              path);
      this.project.getProperties()
          .setProperty(namePropertySdkPath, path);
    }

    if (!namePropertyDefaultGoPath.isEmpty()) {
      final String path = this.makeDefaultGoPath().getAbsolutePath();
      this.logInfo("Exporting the default Go path folder as a project property '" +
          namePropertyDefaultGoPath + "': " + path);
      this.project.getProperties().setProperty(namePropertyDefaultGoPath, path);
    }

    if (!namePropertyGoCommandPath.isEmpty()) {
      final List<Path> paths = findExecutable("go", List.of(goSdkFolder), true, true);

      final Path foundFilePath;
      if (paths.isEmpty()) {
        this.logError("Can't find GO command in cached GoSDK folder " + goSdkFolder);
        throw new MojoFailureException(
            "Can't find executable go file in cached GoSDK to export its path as property");
      } else if (paths.size() == 1) {
        foundFilePath = paths.get(0);
      } else {
        this.logError(
            "Found multiple go executable files in cached folder: " + (paths.stream()
                .map(Path::toString).collect(joining(";"))));
        throw new MojoFailureException(
            "Found multiple candidates as go executable file in the cached GoSDK folder");
      }

      this.logInfo("Exporting the executable go file path as a project property '" +
          namePropertyGoCommandPath + "': " + foundFilePath);
      this.project.getProperties()
          .setProperty(namePropertyGoCommandPath, foundFilePath.toString());
    }
  }

  @Override
  protected boolean isSkip() {
    return this.skip;
  }
}
