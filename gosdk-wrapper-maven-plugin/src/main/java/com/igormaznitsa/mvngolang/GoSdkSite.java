package com.igormaznitsa.mvngolang;

import static java.util.stream.Stream.concat;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum GoSdkSite {
  AUTO(List.of()),
  GOOGLE_APIS(List.of("https://storage.googleapis.com/golang/")),
  GOSDK_SITE(List.of("https://go.dev/dl/"));

  private final List<String> links;

  GoSdkSite(final List<String> links) {
    this.links = links;
  }

  public static Optional<GoSdkSite> find(final String name) {
    if (name == null || name.isBlank()) {
      return Optional.empty();
    }
    final String normalized = name.trim().toUpperCase(Locale.ROOT);
    return Arrays.stream(GoSdkSite.values()).filter(x -> x.name().equals(normalized)).findFirst()
        .map(x -> x ==
            AUTO ? GOOGLE_APIS : x);
  }

  public List<String> getLinks() {
    if (this == AUTO) {
      return concat(GOOGLE_APIS.links.stream(), GOSDK_SITE.links.stream()).collect(
          Collectors.toList());
    } else {
      return this.links;
    }
  }
}
