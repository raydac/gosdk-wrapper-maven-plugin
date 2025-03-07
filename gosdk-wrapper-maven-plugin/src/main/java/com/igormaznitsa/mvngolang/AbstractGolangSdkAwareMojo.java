package com.igormaznitsa.mvngolang;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.out;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.SystemUtils;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.Header;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.Proxy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

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
  /**
   * Section describing proxy settings.
   * @since 1.0.0
   */
  @Parameter(name = "proxy")
  private ProxySettings proxy;
  /**
   * Disable SSL certificate check during HTTP requests.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.disable.ssl.check", name = "disableSslCheck", defaultValue = "false")
  private boolean disableSslCheck;
  /**
   * Hide SDK loading indicator.
   * @since 1.0.1
   */
  @Parameter(property = "mvn.golang.hide.load.indicator", name = "hideLoadIndicator", defaultValue = "false")
  private boolean hideLoadIndicator;
  /**
   * The site contains GoSDK archives.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.site", name = "sdkSite", defaultValue = "https://storage.googleapis.com/golang/")
  private String sdkSite;
  /**
   * Allows to define base SDK archive base name.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.archive.base.name", name = "sdkArchiveBaseName")
  private String sdkArchiveBaseName;
  /**
   * Folder caching downloaded and unpacked GoSDKs.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.store.folder", defaultValue = "${user.home}${file.separator}.mvnGoLang", name = "storeFolder")
  private String storeFolder;
  /**
   * Folder to download SDK archives.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.download.archive.folder", name = "downloadArchiveFolder")
  private String downloadArchiveFolder;
  /**
   * MD5 to check downloaded archive file.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.expected.archive.md5", name = "expectedArchiveMd5")
  private String expectedArchiveMd5;
  /**
   * If true then downloaded GoSDK archive will not be removed after processing.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.keep.downloaded.archive", name = "keepDownloadedArchive", defaultValue = "false")
  private boolean keepDownloadedArchive;
  /**
   * Force use of existing pre-installed Go folder.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.preinstalled.sdk.folder", name = "preinstalledSdkFolder")
  private String preinstalledSdkFolder;
  /**
   * Force direct URL to load GoSDK archive.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.download.url", name = "sdkDownloadUrl")
  private String sdkDownloadUrl;

  /**
   * GoSDK version.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.go.version", name = "goVersion", defaultValue = "1.24.0")
  private String goVersion;
  /**
   * GoSDK OS.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.os", name = "os")
  private String os;
  /**
   * GoSDK architecture.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.arch", name = "arch")
  private String arch;
  /**
   * Optional suffix for OSX.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.osx.version", name = "osxVersion")
  private String osxVersion;

  /**
   * Force GoSDK archive file name.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.sdk.archive.file.name", name = "sdkArchiveFileName")
  private String sdkArchiveFileName;
  /**
   * Connection timeout for HTTP connections.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.connection.timeout.ms", name = "connectionTimeout", defaultValue = "60000")
  private int connectionTimeout = 60000;
  /**
   * Force use maven proxy settings.
   * @since 1.0.0
   */
  @Parameter(property = "mvn.golang.use.maven.proxy", name = "useMavenProxy", defaultValue = "true")
  private boolean useMavenProxy;
  /**
   * Guess GoSDK archive file extension based on host os.
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
  public final void execute() throws MojoExecutionException, MojoFailureException {
    if (this.isSkip()) {
      this.logInfo("Skip execution");
      return;
    }

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
      this.logOptional("Finding or creating store folder: " + this.storeFolder.trim());
      cacheFolder = Files.createDirectories(Path.of(this.storeFolder.trim()));
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
            this.loadAndUnpackGoSdk(sdkBaseName, tempSdkFolder);
            try {
              final Path goFolder = tempSdkFolder.resolve("go");
              final Path sourcePath;
              if (Files.isDirectory(goFolder)) {
                sourcePath = goFolder;
              } else {
                sourcePath = tempSdkFolder;
              }
              this.logOptional("Moving unpacked folder " + sourcePath + " to " + preparedSdkFolder);
              preparedSdkFolder =
                  Files.move(sourcePath, preparedSdkFolder, StandardCopyOption.ATOMIC_MOVE);
            } finally {
              if (Files.exists(tempSdkFolder)) {
                this.logOptional("Deleting temp sdk folder: " + tempSdkFolder);
                FileUtils.deleteDirectory(tempSdkFolder.toFile());
              }
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

  private String normalizedSdkSite() {
    final String link = this.sdkSite.trim();
    if (link.endsWith("/")) {
      return link;
    } else {
      return link + '/';
    }
  }

  private Path findDownloadArchiveFolder() throws IOException {
    final Path result;
    if (isNullOrEmpty(this.downloadArchiveFolder)) {
      result = new File(this.storeFolder.trim()).toPath();
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

    final String sdkArchiveUrl;
    final String fileName;
    if (isNullOrEmpty(this.sdkDownloadUrl)) {
      if (isNullOrEmpty(this.sdkArchiveFileName)) {
        this.logDebug("Looking for GoSDK archive name");
        final Document document =
            this.loadGolangSdkList(URLEncoder.encode(sdkBaseName, StandardCharsets.UTF_8));
        fileName =
            this.extractSdkFileName(document, sdkBaseName, List.of("tar.gz", "zip"));
      } else {
        this.logInfo("Detected directly provided GoSDK archive name: " + this.sdkArchiveFileName);
        fileName = this.sdkArchiveFileName;
      }
      this.logInfo("Found listed archive: " + fileName);
      sdkArchiveUrl = this.normalizedSdkSite() + fileName;
      this.logInfo("Loading from: " + sdkArchiveUrl);
    } else {
      this.logWarn("Detected directly provided download link: " + this.sdkDownloadUrl);
      fileName = isNullOrEmpty(this.sdkArchiveFileName) ? "directLinkGoSdk" :
          this.sdkArchiveFileName.trim();
      sdkArchiveUrl = this.sdkDownloadUrl.trim();
    }

    final Path loadFolder = this.findDownloadArchiveFolder();

    final Path tempArchivePath = loadFolder.resolve(
        ".tmp_" + Long.toString(System.currentTimeMillis(), 25).toUpperCase(Locale.ENGLISH) + '_' +
            ensureSafeFileName(fileName));
    try {
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

      if (isNullOrEmpty(this.expectedArchiveMd5)) {
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
        }
      } else {
        this.logInfo("Check archive MD5 for provided value: " + this.expectedArchiveMd5);
        try (final InputStream fileInputStream = Files.newInputStream(tempArchivePath)) {
          if (ApacheHttpClient5Loader.XGoogHashHeader.checkMd5(fileInputStream,
              this.expectedArchiveMd5)) {
            this.logInfo("MD5 is ok");
          } else {
            this.logError("MD5 is wrong");
            throw new MojoFailureException("Downloaded archive has wrong MD5 checksum");
          }
        }
      }

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
              logDebug("Archive entry: " + archiveEntry.getName() + " (" +
                  (archiveEntry.isDirectory() ? "" : archiveEntry.getSize()) + ')');
              counter.incrementAndGet();
            }

            @Override
            public void onCompleted(ArchiveUnpacker source) {
              logDebug("Completed decompressing");
            }
          });
      this.logInfo(
          String.format("Archive successfully unpacked, detected %d items: %s", counter.get(),
              destinationFolder));
      this.logInfo("Updating file attributes in folder: " + destinationFolder);
      this.makeExecutableFilesInFolder(destinationFolder);
    } catch (CompressorException | ArchiveException ex) {
      throw new IOException("Can't unpack archive for error", ex);
    } finally {
      if (!this.keepDownloadedArchive) {
        this.logInfo("Deleting temporary archive file:" + tempArchivePath);
        Files.deleteIfExists(tempArchivePath);
      } else {
        this.logWarn("Downloaded archive not removed for direct request: " + tempArchivePath);
      }
    }
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
    return ApacheHttpClient5Loader.INSTANCE.createHttpClient(
        this.findProxySettings(),
        this.disableSslCheck,
        Duration.ofMillis(this.connectionTimeout)
    );
  }

  protected static Optional<Path> findExecutable(final String fileName, final Path folder)
      throws IOException {
    if (fileName == null) {
      throw new NullPointerException("File name is null");
    }
    if (folder == null) {
      throw new NullPointerException("Folder is null");
    }
    if (!Files.isDirectory(folder)) {
      throw new IllegalArgumentException("Is not a folder: " + folder);
    }

    if (SystemUtils.IS_OS_WINDOWS && fileName.contains(":")) {
      throw new IllegalArgumentException("Illegal file name: " + fileName);
    }

    if (fileName.contains("/") || fileName.contains("\\")) {
      final Path path = folder.resolve(fileName);
      return Files.isRegularFile(path) ? Optional.of(path) : Optional.empty();
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
          .collect(Collectors.toSet());
    }

    final Path folderBin = folder.resolve("bin");
    if (Files.isDirectory(folderBin)) {
      try (Stream<Path> walker = Files.walk(folderBin)) {
        final Optional<Path> found = walker.filter(
                x -> Files.isRegularFile(x) && variants.contains(x.getFileName().toString()))
            .findFirst();
        if (found.isPresent()) {
          return found;
        }
      }
    }

    try (Stream<Path> walker = Files.walk(folder)) {
      return walker.filter(
              x -> Files.isRegularFile(x) && variants.contains(x.getFileName().toString()))
          .findFirst();
    }
  }

  private void makeExecutableFilesInFolder(final Path folder) throws IOException {
    try (Stream<Path> walker = Files.walk(folder)) {
      walker.filter(x -> Files.isRegularFile(x) && !Files.isSymbolicLink(x))
          .forEach(x -> {
            try {
              Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(x);
              if (!permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
                this.logDebug("Setting executable flag for file: " + x);
                permissions = new HashSet<>(permissions);
                permissions.add(PosixFilePermission.OWNER_EXECUTE);
              }
              Files.setPosixFilePermissions(x, permissions);
            } catch (IOException ex) {
              logError("Can't set execute permission for unpacked file: " + x);
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
            Collectors.toSet());

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
              Collectors.joining("\n", "\n", "\n"))));

      if (this.sdkArchiveFileAutoExtension) {
        final String supposedSdkName =
            sdkBaseName + '.' + (SystemUtils.IS_OS_WINDOWS ? "zip" : "tar.gz");
        this.logWarn("Force supposed GoSDK file name: " + supposedSdkName);
        return supposedSdkName;
      }
      throw new IOException("Can't find SDK : " + sdkBaseName);
    } else {
      throw new IOException("It is not a ListBucket file [" + root.getTagName() + ']');
    }
  }

  private Document loadGolangSdkList(final String keyPrefix) throws IOException {
    final String listOfFilesUrl =
        this.sdkSite.trim() + (keyPrefix == null ? "" : "?prefix=" + keyPrefix);
    this.logDebug("Loading URL list document from GoSDK site: " + listOfFilesUrl);
    final String text =
        ApacheHttpClient5Loader.loadResourceAsString("GET", this.makeHttpClient(), listOfFilesUrl,
            List.of("application/xml"));
    this.logDebug("Loaded URL list: " + text);
    if (text == null) {
      throw new IOException("Empty result as GoSDK list");
    }
    try {
      final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      final DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(text)));
    } catch (ParserConfigurationException ex) {
      getLog().error("Can't configure XML parser", ex);
      throw new IOException("Can't configure XML parser", ex);
    } catch (SAXException ex) {
      getLog().error("Can't parse document", ex);
      throw new IOException("Can't parse document", ex);
    } catch (IOException ex) {
      getLog().error("Unexpected IOException", ex);
      throw new IOException("Unexpected IOException", ex);
    }
  }

  private void unlockSdkFolder(final File lockFile) throws IOException {
    while (lockFile.exists() && !lockFile.delete()) {
      try {
        Thread.sleep(LOCK_FILE_SLEEP_MS);
      } catch (InterruptedException ex) {
        Thread.currentThread().interrupt();
        throw new IOException("Unlock folder operation is interrupted", ex);
      }
    }
  }

  private File lockSdkFolder(final File sdkCacheFolder, final String baseSdkName)
      throws IOException {
    final File lockFile = new File(sdkCacheFolder, ".lock." + baseSdkName);
    lockFile.deleteOnExit();
    if (!lockFile.createNewFile()) {
      while (lockFile.exists()) {
        try {
          Thread.sleep(LOCK_FILE_SLEEP_MS);
        } catch (InterruptedException ex) {
          Thread.currentThread().interrupt();
          throw new IOException("Lock folder operation is interrupted", ex);
        }
      }
    }
    return lockFile;
  }

  private ProxySettings findProxySettings() {
    final ProxySettings result;
    if (this.useMavenProxy) {
      final Proxy activeMavenProxy = this.settings == null ? null : this.settings.getActiveProxy();
      if (activeMavenProxy == null) {
        result = null;
      } else {
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
