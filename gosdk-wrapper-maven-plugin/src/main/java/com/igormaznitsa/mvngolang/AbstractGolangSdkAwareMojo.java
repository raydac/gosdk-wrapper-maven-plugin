package com.igormaznitsa.mvngolang;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.out;
import static java.nio.file.Files.isRegularFile;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

import com.igormaznitsa.mvngolang.utils.ApacheHttpClient5Loader;
import com.igormaznitsa.mvngolang.utils.ArchiveUnpacker;
import com.igormaznitsa.mvngolang.utils.GoRecordExtractor;
import com.igormaznitsa.mvngolang.utils.ProxySettings;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@SuppressWarnings({"ReassignedVariable", "CanBeFinal", "SameParameterValue"})
public abstract class AbstractGolangSdkAwareMojo extends AbstractCommonMojo {

  public static final String SDK_NAME_PATTERN = "go%s.%s-%s%s";
  private static final List<String> SDK_ARCHIVE_MIMES =
      List.of(
          "application/octet-stream",
          "application/zip",
          "application/x-tar",
          "application/x-gzip"
      );
  private static final long LOCK_FILE_SLEEP_MS = 100;
  private static final Duration DELAY_LOCK_FILE_NOTIFICATION = Duration.ofSeconds(15);
  /**
   * Section describing proxy settings.
   * <pre>{@code
   * <proxy>
   *     <scheme>http</scheme>
   *     <host>127.0.0.1</host>
   *     <port>8085</port>
   *     <username>johndow</username>
   *     <password>123456</password>
   *     <nonProxyHosts>google.com|youtube.com</nonProxyHosts>
   * </proxy>
   * }</pre>
   *
   * @since 1.0.0
   */
  @Parameter(name = "proxy")
  private ProxySettings proxy;
  /**
   * Disable SSL certificate check during HTTP requests.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.disable.ssl.check", name = "disableSslCheck", defaultValue = "false")
  private boolean disableSslCheck;
  /**
   * Hide SDK load indicator.
   *
   * @since 1.0.1
   */
  @Parameter(property = "mvn.golang.hide.load.indicator", name = "hideLoadIndicator", defaultValue = "false")
  private boolean hideLoadIndicator;
  /**
   * The site for GoSDK archives.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.site", name = "sdkSite", defaultValue = "https://storage.googleapis.com/golang/")
  private String sdkSite;
  /**
   * Allows to define base SDK archive base name. If not defined then base name will be synthesized automatically.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.archive.base.name", name = "sdkArchiveBaseName")
  private String sdkArchiveBaseName;
  /**
   * Folder to download SDK archives. If not defined then storeFolder in use.
   *
   * @see #storeFolder
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.download.archive.folder", name = "downloadArchiveFolder")
  private String downloadArchiveFolder;
  /**
   * MD5 to check downloaded archive file.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.expected.archive.md5", name = "expectedArchiveMd5")
  private String expectedArchiveMd5;
  /**
   * If true then downloaded GoSDK archive will not be removed after processing.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.keep.downloaded.archive", name = "keepDownloadedArchive", defaultValue = "false")
  private boolean keepDownloadedArchive;
  /**
   * Force use of existing pre-installed Go folder.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.preinstalled.sdk.folder", name = "preinstalledSdkFolder")
  private String preinstalledSdkFolder;
  /**
   * Force direct URL to load GoSDK archive.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.download.url", name = "sdkDownloadUrl")
  private String sdkDownloadUrl;

  /**
   * Maven artifact id to load GoSDK archive as an artifact from current Maven repository. It has the highest priority during SDK download.
   * Expected format groupId:artifactId:version[:type[:classifier]]
   * If the type is not defined, the archive extension will be automatically determined based on the host OS.
   *
   * <p>Example usage:</p>
   * <pre>{@code
   *   <sdkArtifactId>com.sdk.go:go-sdk:1.24.1</sdkArtifactId>
   * }</pre>
   *
   * @since 1.0.4
   */
  @Parameter(property = "mvn.golang.sdk.artifact.id", name = "sdkArtifactId")
  private String sdkArtifactId;
  /**
   * GoSDK version.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.go.version", name = "goVersion", defaultValue = "1.24.4")
  private String goVersion;
  /**
   * GoSDK OS.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.os", name = "os")
  private String os;
  /**
   * GoSDK architecture.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.arch", name = "arch")
  private String arch;
  /**
   * Optional suffix for OSX.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.osx.version", name = "osxVersion")
  private String osxVersion;
  /**
   * Force GoSDK archive file name.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.archive.file.name", name = "sdkArchiveFileName")
  private String sdkArchiveFileName;
  /**
   * Connection timeout for HTTP client operations.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.connection.timeout.ms", name = "connectionTimeout", defaultValue = "60000")
  private long connectionTimeout = 60_000L;
  /**
   * Force use maven proxy settings.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.use.maven.proxy", name = "useMavenProxy", defaultValue = "true")
  private boolean useMavenProxy;
  /**
   * Guess GoSDK archive file extension based on host os.
   *
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.archive.file.auto.extension", name = "sdkArchiveFileAutoExtension", defaultValue = "true")
  private boolean sdkArchiveFileAutoExtension;

  private static String makeBaseSdkName(
      final String sdkVersion,
      final String os,
      final String arch,
      final String osxVersion) {
    return String.format(SDK_NAME_PATTERN, sdkVersion, os, arch,
        (osxVersion == null || osxVersion.isBlank()) ? "" : "-" + osxVersion);
  }

  private static String ensureSafeFileName(final String fileName) {
    final String baseName;
    final String extension;
    final int dotIndex = fileName.indexOf('.');
    if (dotIndex < 0) {
      baseName = fileName;
      extension = "";
    } else {
      baseName = fileName.substring(0, dotIndex);
      extension = fileName.substring(dotIndex + 1);
    }
    final String processedBaseName = baseName.replaceAll("[^a-zA-Z0-9-_]", "_");
    return extension.isEmpty() ? processedBaseName : processedBaseName + "." + extension;
  }

  @SuppressWarnings("UnusedReturnValue")
  protected static int printCliProgressBar(
      final String prefix,
      final String postfix,
      final long value,
      final long maxValue,
      final int progressBarWidth
  ) {
    final StringBuilder builder = new StringBuilder();
    builder.append("\r\u001B[?25l");
    builder.append(prefix == null ? "" : prefix);
    builder.append("[");

    final int progress = max(0, min(progressBarWidth,
        (int) Math.round(progressBarWidth * ((double) value / (double) maxValue))));

    builder.append("▒".repeat(progress));
    builder.append("-".repeat(Math.max(0, progressBarWidth - progress)));
    builder.append(']');
    if (postfix != null) {
      builder.append(postfix);
    }
    builder.append("\u001B[?25h");
    out.print(builder);
    out.flush();
    return progress;
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

  private String findSdkBaseName() {
    if (isNullOrEmpty(this.sdkArchiveBaseName)) {
      this.logOptional("Making base sdk name on provided parameters");
      return makeBaseSdkName(this.goVersion, this.findOs(), this.findArch(), this.osxVersion);
    } else {
      this.logOptional("Forced direct sdk base name: " + this.sdkArchiveBaseName);
      return ensureSafeFileName(this.sdkArchiveBaseName.trim());
    }
  }

  @Override
  public final void doExecute() throws MojoExecutionException, MojoFailureException {
    final long startTime = System.currentTimeMillis();
    final Path goSdkFolder;
    if (isNullOrEmpty(this.preinstalledSdkFolder)) {
      final String sdkBaseName = this.findSdkBaseName();
      if (sdkBaseName.isBlank()) {
        throw new MojoExecutionException(
            "Detected blank GoSDK base name, may be wrong config properties");
      }
      this.logOptional("Found sdkBaseName: " + sdkBaseName);
      goSdkFolder = this.ensureCachedGoSdk(sdkBaseName);
    } else {
      this.logInfo("Provided pre-installed GoSDK folder: " + this.preinstalledSdkFolder);
      goSdkFolder = new File(this.preinstalledSdkFolder).toPath();
      if (!Files.isDirectory(goSdkFolder)) {
        throw new MojoExecutionException(
            "Can't find defined pre-installed GoSDK folder: " + this.preinstalledSdkFolder);
      }
    }
    try {
      this.onMojoExecute(goSdkFolder);
    } catch (IOException ex) {
      throw new MojoExecutionException("IOException during onMojoExecute", ex);
    } finally {
      this.logOptional("Elapsed time " + (System.currentTimeMillis() - startTime) + " ms");
    }
  }

  private Path ensureCachedGoSdk(final String sdkBaseName)
      throws MojoFailureException, MojoExecutionException {
    final Path cacheFolder;
    try {
      this.logOptional("Finding or creating store folder: " + this.storeFolder);
      cacheFolder = Files.createDirectories(this.storeFolder.toPath());
    } catch (IOException ex) {
      throw new MojoFailureException(
          "Can't create cache folder or it is not a folder exists: " + this.storeFolder);
    }

    try {
      final File lockFile = this.lockSdkFolder(cacheFolder.toFile(), sdkBaseName);
      try {
        Path preparedSdkFolder = cacheFolder.resolve(sdkBaseName);
        if (!Files.isDirectory(preparedSdkFolder)) {
          this.logOptional("There is no cached GoSDK: " + preparedSdkFolder);
          if (this.session.isOffline()) {
            throw new MojoFailureException(
                "There is no cached GoSDK, the session is offline one: " + preparedSdkFolder);
          } else {
            final Path tempSdkFolder = preparedSdkFolder.resolveSibling(
                ".unpack" + preparedSdkFolder.getFileName().toString());
            long start = System.currentTimeMillis();
            try {
              this.loadAndUnpackGoSdk(sdkBaseName, tempSdkFolder);
            } finally {
              this.logDebug(
                  "Elapsed time for loadAndUnpackGoSdk: " + (System.currentTimeMillis() - start) +
                      " ms");
            }
            start = System.currentTimeMillis();
            try {
              final Path goFolder = tempSdkFolder.resolve("go");
              final Path sourcePath;
              if (Files.isDirectory(goFolder)) {
                sourcePath = goFolder;
              } else {
                sourcePath = tempSdkFolder;
              }
              this.logOptional("Moving unpacked folder " + sourcePath + " to " + preparedSdkFolder);
              FileUtils.moveDirectory(sourcePath.toFile(), preparedSdkFolder.toFile());
            } finally {
              if (Files.exists(tempSdkFolder)) {
                this.logOptional("Deleting temp sdk folder: " + tempSdkFolder);
                FileUtils.deleteDirectory(tempSdkFolder.toFile());
              }
              this.logDebug("Elapsed time for all GoSDK unpacking operations: " +
                  (System.currentTimeMillis() - start) + " ms");
            }
          }
        }
        return preparedSdkFolder;
      } finally {
        this.unlockSdkFolder(lockFile);
      }
    } catch (IOException ex) {
      throw new MojoExecutionException("Detected error during execution", ex);
    }
  }

  private Path findDownloadArchiveFolder() throws IOException {
    final Path result;
    if (isNullOrEmpty(this.downloadArchiveFolder)) {
      result = this.storeFolder.toPath();
    } else {
      result = new File(this.downloadArchiveFolder.trim()).toPath();
    }
    this.logDebug("Creating load folder: " + result);
    return Files.createDirectories(result);
  }

  private void loadAndUnpackGoSdk(final String sdkBaseName, final Path destinationFolder)
      throws IOException, MojoFailureException {
    this.logInfo("Loading GoSDK for base name: " + sdkBaseName);
    if (Files.isDirectory(destinationFolder)) {
      this.logWarn("Detected existing folder for GoSDK, deleting it: " + destinationFolder);
      FileUtils.deleteDirectory(destinationFolder.toFile());
    }

    final boolean loadAsArtifactId = !isNullOrEmpty(this.sdkArtifactId);

    Map<GoRecordChecksum, String> expectedChecksum =
        isNullOrEmpty(this.expectedArchiveMd5) ? Map.of() :
            Map.of(GoRecordChecksum.MD5, this.expectedArchiveMd5.trim());
    final String sdkArchiveUrl;
    final String fileName;
    if (isNullOrEmpty(this.sdkDownloadUrl) && !loadAsArtifactId) {
      final String recordVersion = GoRecordExtractor.extractSdkVersion(sdkBaseName).orElse(null);
      if (recordVersion == null) {
        throw new MojoFailureException("Can't extract expected go sdk version from " + sdkBaseName);
      }

      final GoRecord.GoFile record;
      if (isNullOrEmpty(this.sdkArchiveFileName)) {
        this.logDebug("Looking for GoSDK archive name");
        final List<GoRecord> records = this.loadSdkListAsString(this.sdkSite.trim(),
            URLEncoder.encode(sdkBaseName, StandardCharsets.UTF_8));

        this.logDebug("Loaded GoSDK records: " + records.stream()
            .map(x -> x.getName() + '(' +
                x.getFiles().stream().map(GoRecord.GoFile::getFileName).collect(
                    joining(",")) + ')').collect(
                joining(" ")));

        if (records.isEmpty()) {
          this.logError(
              "Cound not get any GoSDK record during request, " + sdkBaseName + " might not exist");
          throw new MojoFailureException(
              "Empty GoSDK record list returned by request");
        }

        final Set<String> allFileNames =
            Stream.of("tar.gz", "zip").map(x -> sdkBaseName + '.' + x).collect(toSet());

        final GoRecord sdkRecord = records.stream()
            .filter(x -> x.getName().equalsIgnoreCase(recordVersion))
            .findFirst()
            .orElse(null);

        if (sdkRecord == null) {
          this.logError(
              String.format("Can't find %s among loaded records: %s", sdkBaseName,
                  records.stream()
                      .map(GoRecord::getName).collect(joining(","))));
          throw new MojoFailureException(
              "Can't find " + sdkBaseName + " sdk record among " + records.size() +
                  " record(s) in loaded list");
        }

        this.logDebug("Search any file among record files: " + allFileNames);

        record = sdkRecord.getFiles().stream()
            .filter(x -> allFileNames.contains(x.getFileName()))
            .findFirst()
            .orElse(null);

        if (record == null) {
          if (this.sdkArchiveFileAutoExtension) {
            final String supposedSdkName =
                sdkBaseName + '.' + findArchiveExtensionForOs();
            this.logWarn("Force supposed GoSDK file name: " + supposedSdkName);
            fileName = supposedSdkName;
            sdkArchiveUrl = GoRecordExtractor.concatUrl(this.sdkSite.trim(), supposedSdkName);
          } else {
            this.logError(
                "Can't find expected file (" + allFileNames + ") among files: " +
                    sdkRecord.getFiles().stream().map(
                        GoRecord.GoFile::getFileName).collect(
                        joining(",")));
            throw new MojoFailureException(
                "Can't find any supported archive among file list for " + sdkBaseName);
          }
        } else {
          fileName = record.getFileName();
          sdkArchiveUrl = record.getLink();
          if (!record.getChecksum().isEmpty()) {
            final Map<GoRecordChecksum, String> mergedChecksumMap =
                new HashMap<>(record.getChecksum());
            mergedChecksumMap.putAll(expectedChecksum);
            expectedChecksum = mergedChecksumMap;
          }
        }

      } else {
        this.logInfo("Detected directly provided GoSDK archive name: " + this.sdkArchiveFileName);
        fileName = this.sdkArchiveFileName;
        sdkArchiveUrl = GoRecordExtractor.concatUrl(sdkSite.trim(), fileName);
      }
      this.logInfo("Loading from: " + sdkArchiveUrl);
    } else {
      if (loadAsArtifactId) {
        this.logDebug("Loading from Maven repository");
        sdkArchiveUrl = null;
        fileName = isNullOrEmpty(this.sdkArchiveFileName) ? "mavenRepositoryArtifact" :
            this.sdkArchiveFileName.trim();
      } else {
        this.logWarn("Detected directly provided download link: " + this.sdkDownloadUrl);
        fileName = isNullOrEmpty(this.sdkArchiveFileName) ? "directLinkGoSdk" :
            this.sdkArchiveFileName.trim();
        sdkArchiveUrl = this.sdkDownloadUrl.trim();
      }
    }

    final Path loadFolder = this.findDownloadArchiveFolder();

    final Path tempArchivePath = loadFolder.resolve(
        ".tmp_" + Long.toString(System.currentTimeMillis(), 25).toUpperCase(Locale.ENGLISH) + '_' +
            ensureSafeFileName(fileName));
    try {
      Path sdkPath;
      if (loadAsArtifactId) {
        final String trimmedSdkArtifactId = this.sdkArtifactId.trim();
        this.logWarn("Retrieving artifact from the Maven repository: " + trimmedSdkArtifactId);
        sdkPath = this.downloadFromArtifactId(trimmedSdkArtifactId);
        this.logOptional("SDK artifact archive location: " + sdkPath);
      } else {
        this.logInfo("Retrieving GoSDK from URL: " + sdkArchiveUrl);
        this.downloadFromUrl(sdkArchiveUrl, tempArchivePath, expectedChecksum);
        sdkPath = tempArchivePath;
      }
      this.extractArchiveToDestination(sdkPath, destinationFolder);
      this.logInfo("Updating file attributes in folder: " + destinationFolder);
      this.makeExecutableFilesInFolder(destinationFolder);
    } finally {
      if (!this.keepDownloadedArchive && Files.exists(tempArchivePath)) {
        this.logInfo("Deleting temporary archive file:" + tempArchivePath);
        if (Files.deleteIfExists(tempArchivePath)) {
          this.logDebug("Deleted successfully");
        } else {
          this.logWarn("Can't delete temporary archive file: " + tempArchivePath);
          tempArchivePath.toFile().deleteOnExit();
        }
      } else {
        if (Files.exists(tempArchivePath)) {
          this.logWarn("Downloaded archive not removed for direct request: " + tempArchivePath);
        }
      }
    }
  }

  private void downloadFromUrl(final String sdkArchiveUrl, final Path tempArchivePath,
                               final Map<GoRecordChecksum, String> checksum)
      throws IOException, MojoFailureException {
    if (sdkArchiveUrl.toLowerCase(Locale.ROOT).startsWith("http:") ||
        sdkArchiveUrl.toLowerCase(Locale.ROOT).startsWith("https:")) {
      final AtomicInteger lastProgress = new AtomicInteger(-1);
      final Header[] headers = ApacheHttpClient5Loader.loadResource("GET",
          makeHttpClient(),
          sdkArchiveUrl,
          (loaded, size, progress) -> {
            if (progress >= 0 && lastProgress.get() != progress) {
              lastProgress.set(progress);
              if (!this.session.isParallel() || this.hideLoadIndicator) {
                final String sizeText = (size / 1024L) + "Mb";
                final String loadedText = (loaded / 1024L) + "Mb";
                printCliProgressBar("Loading GoSDK:", ' ' + loadedText + '/' + sizeText,
                    progress, 100, 5);
                if (progress == 100) {
                  System.out.println();
                }
              }
            }
          },
          h -> {
            this.logDebug("Opening output stream for archive file:" + tempArchivePath);
            try {
              return Files.newOutputStream(tempArchivePath);
            } catch (IOException ex) {
              throw new IllegalStateException(
                  "IOError during open stream for temporary file: " + tempArchivePath, ex);
            }
          },
          x -> 16 * 1024 * 1024,
          SDK_ARCHIVE_MIMES
      );
      this.logDebug("Headers: " + Arrays.toString(headers));
      this.logInfo("Successfully downloaded archive file: " + tempArchivePath);

      if (checksum.isEmpty()) {
        final ApacheHttpClient5Loader.XGoogHashHeader xgoogHeader =
            new ApacheHttpClient5Loader.XGoogHashHeader(headers);
        if (xgoogHeader.isValid()) {
          this.logInfo("Detected XGoogHashHeader, validating checksum for downloaded archive");
          try (final InputStream fileInputStream = Files.newInputStream(tempArchivePath)) {
            if (xgoogHeader.isDataOk(fileInputStream)) {
              this.logInfo("Checksum is ok");
            } else {
              this.logError("Checksum is wrong");
              throw new MojoFailureException("Downloaded archive has wrong checksum");
            }
          }
        } else {
          this.logWarn(
              "There is no XGoogHashHeader in response and no provided checksum to check the downloaded archive");
        }
      } else {
        this.checkChecksums(tempArchivePath, checksum);
      }
    } else {
      this.logInfo("Copying local file archive: " + sdkArchiveUrl);
      final File archive = sdkArchiveUrl.toLowerCase(Locale.ROOT).startsWith("file:") ?
          new File(URI.create(sdkArchiveUrl)) : new File(sdkArchiveUrl);
      if (archive.isFile()) {
        final Path filePath = archive.toPath();
        this.checkChecksums(filePath, checksum);
        Files.copy(filePath, tempArchivePath);
      } else {
        throw new MojoFailureException("Can't find archive file: " + archive.getAbsolutePath());
      }
    }
  }

  private void checkChecksums(final Path file, final Map<GoRecordChecksum, String> checksum)
      throws IOException, MojoFailureException {
    if (checksum.isEmpty()) {
      this.logWarn("There is not provided checksum info");
    } else {
      for (final Map.Entry<GoRecordChecksum, String> c : checksum.entrySet()) {
        this.logInfo("Validating checksum of downloaded archive: " + c.getKey().name());
        final String hex;
        try (final InputStream inputStream = Files.newInputStream(file)) {
          hex = c.getKey().makeHex(inputStream);
        }
        if (!c.getValue().equalsIgnoreCase(hex)) {
          this.logError(c.getKey() + " : expected " + c.getValue() + " but detected " + hex);
          throw new MojoFailureException("Wrong " + hex + " signature, expected " + c.getValue());
        }
      }
    }
  }

  private void extractArchiveToDestination(Path tempArchivePath, Path destinationFolder)
      throws IOException {
    try {
      this.logInfo("Unpacking archive into: " + destinationFolder);
      final AtomicInteger counter = new AtomicInteger();
      ArchiveUnpacker.INSTANCE.unpackArchive(tempArchivePath.toFile(), destinationFolder.toFile(),
          new ArchiveUnpacker.UnpackListener() {
            @Override
            public void onArchiveType(ArchiveUnpacker source,
                                      ArchiveUnpacker.ArchiveType archiveType) {
              logDebug("Archive type: " + archiveType);
            }

            @Override
            public void onArchiveEntry(ArchiveUnpacker source, ArchiveEntry archiveEntry) {
              logTrace("Archive entry: " + archiveEntry.getName() + " (" +
                  (archiveEntry.isDirectory() ? "" : archiveEntry.getSize()) + ')');
              counter.incrementAndGet();
            }

            @Override
            public void onCompleted(ArchiveUnpacker source) {
              logDebug("Decompression has been completed: " + counter.get() + " item(s)");
            }
          });
      this.logInfo(
          String.format("Archive successfully unpacked, detected %d items: %s", counter.get(),
              destinationFolder));
    } catch (ArchiveException ex) {
      throw new IOException("Can't unpack archive for error", ex);
    }
  }

  private Path downloadFromArtifactId(String artifactId) throws IOException {
    return this.resolveBinaryArtifact(this.createDependencyArtifact(artifactId));
  }

  /**
   * Creates a dependency artifact from a specification in
   * {@code groupId:artifactId:version[:type[:classifier]]} format.
   *
   * @param artifactSpec artifact specification.
   * @return artifact object instance.
   */
  private Artifact createDependencyArtifact(final String artifactSpec) throws IOException {
    final String[] parts = artifactSpec.split(":");
    if (parts.length < 3 || parts.length > 5) {
      throw new IOException(
          "Invalid artifact specification format"
              + ", expected: groupId:artifactId:version[:type[:classifier]]"
              + ", actual: " + artifactSpec);
    }
    final String type = parts.length >= 4 ? parts[3] : findArchiveExtensionForOs();
    final String classifier = parts.length == 5 ? parts[4] : null;
    return this.createDependencyArtifact(parts[0], parts[1], parts[2], type, classifier);
  }

  private Artifact createDependencyArtifact(
      final String groupId,
      final String artifactId,
      final String version,
      final String type,
      final String classifier
  ) {
    final Dependency dependency = new Dependency();

    dependency.setArtifactId(artifactId);
    dependency.setGroupId(groupId);
    dependency.setVersion(version);
    dependency.setType(type);
    dependency.setClassifier(classifier);
    dependency.setScope(Artifact.SCOPE_PROVIDED);

    return repositorySystem.createDependencyArtifact(dependency);
  }

  private Path resolveBinaryArtifact(final Artifact artifact) throws IOException {
    final ArtifactResolutionRequest request = new ArtifactResolutionRequest()
        .setArtifact(this.project.getArtifact())
        .setResolveRoot(false)
        .setResolveTransitively(false)
        .setArtifactDependencies(singleton(artifact))
        .setManagedVersionMap(emptyMap())
        .setLocalRepository(this.localRepository)
        .setRemoteRepositories(this.remoteRepositories)
        .setOffline(this.session.isOffline())
        .setForceUpdate(this.session.getRequest().isUpdateSnapshots())
        .setServers(this.session.getRequest().getServers())
        .setMirrors(this.session.getRequest().getMirrors())
        .setProxies(this.session.getRequest().getProxies());

    final ArtifactResolutionResult result = this.repositorySystem.resolve(request);
    try {
      this.resolutionErrorHandler.throwErrors(request, result);
    } catch (final ArtifactResolutionException e) {
      throw new IOException("Unable to resolve artifact: " + e.getMessage(), e);
    }

    final Set<Artifact> artifacts = result.getArtifacts();
    this.logDebug("All resolved artifacts: " + artifacts);
    if (artifacts == null || artifacts.isEmpty()) {
      throw new IOException("Unable to resolve artifact: " + artifact);
    }

    if (artifacts.size() > 1) {
      this.logWarn("Multiple artifacts detected: " + artifacts);
    }
    final Artifact resolvedBinaryArtifact = artifacts.iterator().next();
    this.logDebug("Resolved artifact: " + resolvedBinaryArtifact);

    return resolvedBinaryArtifact.getFile().toPath();
  }

  protected abstract void onMojoExecute(final Path goSdkFolder)
      throws IOException, MojoExecutionException, MojoFailureException;

  public String findArch() {
    if (isNullOrEmpty(this.arch)) {
      final String arch = SystemUtils.OS_ARCH.toLowerCase(Locale.ENGLISH);
      if (arch.contains("ppc64le")) {
        return "ppc64le";
      } else if (arch.contains("armv6l")) {
        return "armv6l";
      } else if (arch.contains("arm64") || arch.contains("aarch64")) {
        return "arm64";
      } else if (arch.contains("s390")) {
        return "s390x";
      }
      if (arch.matches("^(x8632|x86|i[3-6]86|ia32|x32)$")) {
        return "386";
      } else if (arch.contains("em64t")
          || arch.contains("x8664")
          || arch.contains("ia32e")
          || arch.contains("x64")
          || arch.contains("amd64")
          || arch.contains("x86_64")) {
        return "amd64";
      } else {
        return "amd64";
      }
    } else {
      return this.arch;
    }
  }

  private String findOs() {
    if (isNullOrEmpty(this.os)) {
      if (SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX) {
        return "darwin";
      } else if (SystemUtils.IS_OS_WINDOWS) {
        return "windows";
      } else if (SystemUtils.IS_OS_AIX) {
        return "aix";
      } else if (SystemUtils.IS_OS_FREE_BSD) {
        return "freebsd";
      } else if (SystemUtils.IS_OS_NET_BSD) {
        return "netbsd";
      } else if (SystemUtils.IS_OS_OPEN_BSD) {
        return "openbsd";
      } else if (SystemUtils.IS_OS_SOLARIS) {
        return "solaris";
      } else {
        return "linux";
      }
    } else {
      return this.os;
    }
  }

  private HttpClient makeHttpClient() {
    final ProxySettings proxySettings = this.findProxySettings();
    this.logDebug("Proxy settings: " + proxySettings);
    this.logDebug("Disable SSL check: " + this.disableSslCheck);
    this.logDebug("Connection timeout: " + this.connectionTimeout);

    return ApacheHttpClient5Loader.INSTANCE.createHttpClient(
        this.findProxySettings(),
        this.disableSslCheck,
        Duration.ofMillis(this.connectionTimeout)
    );
  }

  private void makeExecutableFilesInFolder(final Path folder) throws IOException {
    try (Stream<Path> walker = Files.walk(folder)) {
      walker.filter(
          path -> isRegularFile(path)
              && !Files.isSymbolicLink(path)
              && !Files.isExecutable(path)
      ).forEach(path -> {
        try {
          Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
          if (!permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
            this.logTrace("Set executable flag for file: " + path);
            permissions = new HashSet<>(permissions);
            permissions.add(PosixFilePermission.OWNER_EXECUTE);
          }
          Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ex) {
          final File targetFile = path.toFile();
          if (targetFile.setExecutable(true, true)) {
            this.logTrace("Set executable flag for file through setExecutable: " + targetFile);
          } else {
            this.logTrace(
                "(!)Cant set executable flag for file through setExecutable: " + targetFile);
          }
        } catch (IOException ex) {
          logError("Can't set execute permission for unpacked file: " + path);
        }
      });
    }
  }

  private String extractSdkFileName(
      final Document document,
      final String sdkBaseName,
      final List<String> allowedExtensions) throws IOException {

    this.logDebug(String.format(
        "Extracting GoSDK file name with base %s from document for allowed extensions %s",
        sdkBaseName,
        allowedExtensions)
    );

    final Set<String> variantsWithExtensions =
        allowedExtensions.stream().map(x -> sdkBaseName + '.' + x).collect(
            toSet());

    final List<String> listOfFoundSdk = new ArrayList<>();

    final Element root = document.getDocumentElement();
    if ("ListBucketResult".equals(root.getTagName())) {
      final NodeList list = root.getElementsByTagName("Contents");
      for (int i = 0; i < list.getLength(); i++) {
        final Element element = (Element) list.item(i);
        final NodeList keys = element.getElementsByTagName("Key");
        if (keys.getLength() > 0) {
          final String text = keys.item(0).getTextContent();
          if (variantsWithExtensions.contains(text)) {
            return text;
          } else {
            listOfFoundSdk.add(text);
          }
        }
      }

      this.logWarn(String.format("Can't find any expected distributive %s among provided files:%s",
          variantsWithExtensions, listOfFoundSdk.stream().collect(
              joining("\n", "\n", "\n"))));

      if (this.sdkArchiveFileAutoExtension) {
        final String supposedSdkName =
            sdkBaseName + '.' + findArchiveExtensionForOs();
        this.logWarn("Force supposed GoSDK file name: " + supposedSdkName);
        return supposedSdkName;
      }
      throw new IOException("Can't find SDK : " + sdkBaseName);
    } else {
      throw new IOException("It is not a ListBucket file [" + root.getTagName() + ']');
    }
  }

  private List<GoRecord> loadSdkListAsString(final String sdkSite, final String keyPrefix)
      throws IOException {
    final String text;
    if (sdkSite.toLowerCase(Locale.ROOT).startsWith("http:") ||
        sdkSite.toLowerCase(Locale.ROOT).startsWith("https:")) {
      final String listOfFilesUrl =
          sdkSite.trim() + (keyPrefix == null ? "" : "?prefix=" + keyPrefix);
      this.logWarn("Loading GoSDK link from URI: " + listOfFilesUrl);
      text =
          ApacheHttpClient5Loader.loadResourceAsString("GET", this.makeHttpClient(), listOfFilesUrl,
              List.of("application/xml", "application/json", "text/plain", "text/html"));
    } else {
      this.logWarn("Loading GoSDK link list as a local file: " + sdkSite);
      final File file = new File(sdkSite);
      if (!file.isFile()) {
        throw new IOException("Can't find sdk list file: " + file.getAbsolutePath());
      }
      text = FileUtils.readFileToString(file, StandardCharsets.UTF_8);
    }

    final List<GoRecord> goRecordList =
        GoRecordExtractor.getInstance().findRecords(sdkSite, text).orElse(null);
    if (goRecordList == null) {
      throw new IOException("Can't extract GoSDK items from loaded text: " + sdkSite);
    }

    return goRecordList;
  }

  private void unlockSdkFolder(final File lockFile) throws IOException {
    long nextNotificationTime =
        System.currentTimeMillis() + DELAY_LOCK_FILE_NOTIFICATION.toMillis();
    while (lockFile.exists() && !lockFile.delete()) {
      if (System.currentTimeMillis() >= nextNotificationTime) {
        this.logWarn("Still can't unlock folder and remove file: " + lockFile);
        nextNotificationTime = System.currentTimeMillis() + DELAY_LOCK_FILE_NOTIFICATION.toMillis();
      }
      this.sleep(LOCK_FILE_SLEEP_MS);
    }
  }

  private File lockSdkFolder(final File sdkCacheFolder, final String baseSdkName)
      throws IOException {
    final File lockFile = new File(sdkCacheFolder, ".lock." + baseSdkName);
    lockFile.deleteOnExit();
    if (!lockFile.createNewFile()) {
      long nextNotificationTime =
          System.currentTimeMillis() + DELAY_LOCK_FILE_NOTIFICATION.toMillis();
      while (lockFile.exists()) {
        if (System.currentTimeMillis() >= nextNotificationTime) {
          this.logWarn("Waiting for lock file: " + lockFile);
          nextNotificationTime =
              System.currentTimeMillis() + DELAY_LOCK_FILE_NOTIFICATION.toMillis();
        }
        this.sleep(LOCK_FILE_SLEEP_MS);
      }
    }
    return lockFile;
  }

  private ProxySettings findProxySettings() {
    final ProxySettings result;
    if (this.useMavenProxy) {
      final Proxy activeMavenProxy = this.settings == null ? null : this.settings.getActiveProxy();
      if (activeMavenProxy == null) {
        this.logDebug("No maven proxy settings");
        result = null;
      } else {
        this.logDebug("Using maven proxy settings: " + activeMavenProxy);
        result = new ProxySettings(
            activeMavenProxy.getProtocol(),
            activeMavenProxy.getHost(),
            activeMavenProxy.getPort(),
            activeMavenProxy.getUsername(),
            activeMavenProxy.getPassword(),
            activeMavenProxy.getNonProxyHosts()
        );
      }
    } else {
      result = this.proxy;
    }
    return result;
  }

}
