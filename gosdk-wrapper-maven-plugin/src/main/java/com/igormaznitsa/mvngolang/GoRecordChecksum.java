package com.igormaznitsa.mvngolang;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import org.apache.commons.codec.digest.DigestUtils;

public enum GoRecordChecksum {
  SHA256(DigestUtils::sha256Hex),
  MD5(DigestUtils::md5Hex);

  private static final List<GoRecordChecksum> VALUES = List.of(GoRecordChecksum.values());
  private final Function<byte[], String> checksumProvider;

  GoRecordChecksum(final Function<byte[], String> checksumProvider) {
    this.checksumProvider = checksumProvider;
    this.checksumProvider.apply(new byte[] {1, 2, 3});
  }

  public static Optional<GoRecordChecksum> find(final String value) {
    if (value == null) {
      return Optional.empty();
    } else {
      final String upperCased = value.toUpperCase(Locale.ROOT).trim();
      return VALUES.stream().filter(x -> x.name().startsWith(upperCased)).findFirst();
    }
  }

  public String makeHex(final InputStream inputStream) throws IOException {
    switch (this) {
      case MD5:
        return DigestUtils.md5Hex(inputStream);
      case SHA256:
        return DigestUtils.sha256Hex(inputStream);
      default:
        throw new Error("Unexpected error: " + this);
    }
  }

  public String calculate(final byte[] data) {
    return this.checksumProvider.apply(data);
  }
}
