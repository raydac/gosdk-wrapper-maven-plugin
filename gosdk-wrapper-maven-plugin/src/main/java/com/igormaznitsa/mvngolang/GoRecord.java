package com.igormaznitsa.mvngolang;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.Map;

public class GoRecord {
  private final String name;
  private final List<GoFile> files;

  public GoRecord(final String sdkName, final List<GoFile> files) {
    this.name = sdkName;
    this.files = List.copyOf(files);
  }

  public String getName() {
    return this.name;
  }

  public List<GoFile> getFiles() {
    return this.files;
  }

  public static class GoFile {
    private final String fileName;
    private final String link;
    private final Map<GoRecordChecksum, String> checksum;

    public GoFile(final String fileName, final String link,
                  final Map<GoRecordChecksum, String> checksum) {
      this.fileName = requireNonNull(fileName);
      this.link = requireNonNull(link);
      this.checksum = Map.copyOf(checksum);
    }

    public String getFileName() {
      return this.fileName;
    }

    public String getLink() {
      return this.link;
    }

    public Map<GoRecordChecksum, String> getChecksum() {
      return this.checksum;
    }
  }
}
