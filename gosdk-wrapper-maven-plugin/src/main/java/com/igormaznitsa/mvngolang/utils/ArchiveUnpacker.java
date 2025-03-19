package com.igormaznitsa.mvngolang.utils;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;

public final class ArchiveUnpacker {
  public static final ArchiveUnpacker INSTANCE = new ArchiveUnpacker();

  private ArchiveUnpacker() {
  }

  private static ArchiveInputStream<?> createArchiveInputStream(
      final ArchiveType archiveType,
      final InputStream input
  ) throws ArchiveException, IOException {
    switch (archiveType) {
      case TAR:
        return new TarArchiveInputStream(input);
      case TAR_GZ:
        return new TarArchiveInputStream(
            new GZIPInputStream(input));
      case ZIP:
        return new ZipArchiveInputStream(input);
      default:
        throw new ArchiveException("Unsupported archive format: " + archiveType);
    }
  }

  public static ArchiveType detectArchiveType(final File archiveFile) throws IOException {
    if (!archiveFile.isFile()) {
      throw new IOException("Can't find file: " + archiveFile);
    }

    try (final InputStream fi = new FileInputStream(archiveFile);
         BufferedInputStream bi = new BufferedInputStream(fi)) {

      byte[] signature = new byte[8]; // Read first 8 bytes
      bi.mark(8);
      int bytesRead = bi.read(signature, 0, 8);
      bi.reset();

      if (bytesRead < 4) {
        return ArchiveType.UNKNOWN;
      }

      if (signature[0] == 0x50 && signature[1] == 0x4B) {
        return ArchiveType.ZIP;
      } else if (signature[0] == 0x1F && signature[1] == (byte) 0x8B) {
        if (isGz(signature)) {
          return ArchiveType.TAR_GZ;
        }
      } else if (isTar(signature)) {
        return ArchiveType.TAR;
      } else if (is7z(signature)) {
        return ArchiveType.Z7;
      } else if (isRar(signature)) {
        return ArchiveType.RAR;
      }
    }
    return ArchiveType.UNKNOWN;
  }

  private static boolean isTar(byte[] signature) {
    return signature[0] == 0x75 && signature[1] == 0x73 && signature[2] == 0x74 &&
        signature[3] == 0x61;
  }

  private static boolean is7z(byte[] signature) {
    return signature[0] == '7' && signature[1] == 'z' && signature[2] == (byte) 0xBC &&
        signature[3] == (byte) 0xAF;
  }

  private static boolean isRar(byte[] signature) {
    return (signature[0] == 0x52 && signature[1] == 0x61 && signature[2] == 0x72 &&
        signature[3] == 0x21);
  }

  private static boolean isGz(byte[] signature) {
    return signature[0] == 0x1F && signature[1] == (byte) 0x8B;
  }

  public void unpackArchive(final File archiveFile, final File outputDir,
                            final UnpackListener unpackListener)
      throws IOException, ArchiveException {
    if (!outputDir.exists() && !outputDir.mkdirs()) {
      throw new IOException("Failed to create output directory: " + outputDir);
    }

    final ArchiveType archiveType = detectArchiveType(archiveFile);
    if (unpackListener != null) {
      unpackListener.onArchiveType(this, archiveType);
    }
    if (archiveType == ArchiveType.UNKNOWN) {
      throw new ArchiveException("Unknown archive type: " + archiveType);
    }

    final byte[] buffer = new byte[0x2FFFFF];
    try (final ArchiveInputStream<?> ai = createArchiveInputStream(archiveType, new BufferedInputStream(new FileInputStream(archiveFile)))) {
      ArchiveEntry entry;
      while ((entry = ai.getNextEntry()) != null) {
        if (unpackListener != null) {
          unpackListener.onArchiveEntry(this, entry);
        }
        final File outputFile = new File(outputDir, entry.getName());
        if (entry.isDirectory()) {
          if (!outputFile.exists() && !outputFile.mkdirs()) {
            throw new IOException("Failed to create directory: " + outputFile);
          }
        } else {
          Files.createDirectories(outputFile.getParentFile().toPath());
          try (OutputStream os = new BufferedOutputStream(new FileOutputStream(outputFile),
              buffer.length)) {
            int length;
            while ((length = ai.read(buffer)) != -1) {
              os.write(buffer, 0, length);
            }
          }
        }
      }
    }
  }

  public enum ArchiveType {
    TAR,
    TAR_GZ,
    ZIP,
    Z7,
    RAR,
    UNKNOWN
  }

  public interface UnpackListener {
    @SuppressWarnings("unused")
    void onArchiveType(ArchiveUnpacker source, ArchiveType archiveType);

    @SuppressWarnings("unused")
    void onArchiveEntry(ArchiveUnpacker source, ArchiveEntry archiveEntry);

    @SuppressWarnings("unused")
    void onCompleted(ArchiveUnpacker source);
  }
}
