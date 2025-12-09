package com.igormaznitsa.mvngolang.utils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

public final class OsUtils {
  private OsUtils() {

  }

  public static Optional<Path> findGoSdkFolderInPath(final String path) {
    final List<String> expectedGoNames = List.of("go", "go.exe");
    final String pathSeparator = File.pathSeparator;
    final String[] folders = path.split(Pattern.quote(pathSeparator));
    for (final String p : folders) {
      if (p == null || p.isBlank()) {
        continue;
      }
      final Path folder = new File(p).toPath();
      if (Files.exists(folder) && Files.isDirectory(folder)) {
        final Path binFolder = folder.resolve("bin");
        if (Files.exists(binFolder) && Files.isDirectory(binFolder)) {
          for (final String exe : expectedGoNames) {
            final Path exeFile = binFolder.resolve(exe);
            if (Files.exists(exeFile) && Files.isExecutable(exeFile)) {
              return Optional.of(folder);
            }
          }
        }
      }
    }
    return Optional.empty();
  }

  public static Optional<String> findEnvPath() {
    final Map<String, String> envVars = System.getenv();
    String path = envVars.get("PATH");
    if (path != null) {
      return Optional.of(path);
    } else {
      return envVars.entrySet().stream()
          .filter(e -> "path".equals(e.getKey().toLowerCase(Locale.ROOT)))
          .filter(e -> e.getValue() != null && !e.getValue().isBlank())
          .map(Map.Entry::getValue)
          .findFirst();
    }
  }
}
