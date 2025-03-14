package com.igormaznitsa.mvngolang.utils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.DosFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;
import java.util.stream.Stream;
import org.apache.commons.io.file.PathUtils;

public final class FileUtils {
  public static final LinkOption[] LINK_OPTIONS_NO_FOLLOW_LINKS =
      new LinkOption[] {LinkOption.NOFOLLOW_LINKS};
  public static final LinkOption[] LINK_OPTIONS_EMPTY = new LinkOption[0];
  public static final Set<PosixFilePermission> POSIX_ALL_PERMISSIONS = Set.of(
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

  private FileUtils() {
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
    }
    quietSetAllPosixPermission(path);
  }

  private static void quietSetAllPosixPermission(final Path path) {
    if (path != null) {
      try {
        Files.setPosixFilePermissions(path, POSIX_ALL_PERMISSIONS);
      } catch (Exception ignored) {
      }
    }
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


  public static Path getCanonicalPath(final Path path) {
    try {
      return path.toRealPath();
    } catch (IOException e) {
      return getCanonicalPath(path.getParent()).resolve(path.getFileName());
    }
  }


  public static void makeWritable(final Path path, final boolean followSymlinks)
      throws IOException {
    makeWritable(path, followSymlinks ? LINK_OPTIONS_EMPTY : LINK_OPTIONS_NO_FOLLOW_LINKS);
  }
}
