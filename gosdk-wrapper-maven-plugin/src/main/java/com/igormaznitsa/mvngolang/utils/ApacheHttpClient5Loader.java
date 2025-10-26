package com.igormaznitsa.mvngolang.utils;

import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.LongFunction;
import java.util.regex.Pattern;
import java.util.zip.CRC32C;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.ManagedHttpClientConnectionFactory;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultProxyRoutePlanner;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.DefaultClientTlsStrategy;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HeaderElement;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicClassicHttpRequest;
import org.apache.hc.core5.http.message.BasicHeaderValueParser;
import org.apache.hc.core5.http.message.ParserCursor;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.ssl.SSLContexts;

public class ApacheHttpClient5Loader {
  static {
    // disable mass logging from http client if debug mode
    System.setProperty("org.slf4j.simpleLogger.log.org.apache.hc.client5.http.wire", "off");
  }

  public static final ApacheHttpClient5Loader INSTANCE = new ApacheHttpClient5Loader();

  private final PoolingHttpClientConnectionManager connectionManagerNoSslCheck;
  private final PoolingHttpClientConnectionManager connectionManagerDefault;

  private ApacheHttpClient5Loader() {
    try {
      this.connectionManagerDefault = PoolingHttpClientConnectionManagerBuilder.create().build();
      this.connectionManagerNoSslCheck = PoolingHttpClientConnectionManagerBuilder.create()
          .setConnectionFactory(ManagedHttpClientConnectionFactory.INSTANCE)
          .setTlsSocketStrategy(
              new DefaultClientTlsStrategy(
                  SSLContexts.custom()
                      .loadTrustMaterial(
                          null,
                          (chain, authType) -> true
                      )
                      .build(),
                  (host, session) -> true
              )
          )
          .build();
    } catch (Exception ex) {
      throw new IllegalStateException(ex);
    }
  }

  private static String extractComputerName() {
    String result = System.getenv("COMPUTERNAME");
    if (result == null) {
      result = System.getenv("HOSTNAME");
    }
    if (result == null) {
      try {
        result = InetAddress.getLocalHost().getHostName();
      } catch (UnknownHostException ex) {
        // do nothing, let be null
      }
    }
    return requireNonNullElse(result, "<Unknown computer>");
  }

  private static String extractDomainName() {
    final String result = System.getenv("USERDOMAIN");
    return requireNonNullElse(result, "");
  }

  public static String loadResourceAsString(
      final String httpMethod,
      final HttpClient httpClient,
      final String resourceUri,
      final List<String> acceptedMimes
  ) throws IOException {
    final HttpHost target;
    try {
      target = HttpHost.create(URI.create(resourceUri));
    } catch (Exception e) {
      throw new IllegalArgumentException("Can't decode URI: " + resourceUri, e);
    }
    final ClassicHttpRequest request = new BasicClassicHttpRequest(httpMethod, resourceUri);
    request.setHeader("Accept", acceptedMimes == null ? "*/*" : String.join(",", acceptedMimes));
    final HttpContext context = HttpClientContext.create();
    try (final ClassicHttpResponse response = httpClient.executeOpen(target, request, context)) {
      if (response.getCode() == HTTP_OK) {
        HttpEntity entity = response.getEntity();
        if (entity != null) {
          try {
            return EntityUtils.toString(entity);
          } catch (ParseException ex) {
            throw new IOException("Can't parse entity", ex);
          }
        } else {
          return null;
        }
      } else {
        throw new HttpsNotOkStatusException(response.getReasonPhrase(), response.getCode());
      }
    }
  }

  @FunctionalInterface
  public interface ProgressConsumer {
    void apply(long downloaded, long total, int progress);
  }

  @SuppressWarnings("resource")
  public static Header[] loadResource(
      final String httpMethod,
      final HttpClient httpClient,
      final String resourceUri,
      final ProgressConsumer onProgressConsumer,
      final Function<Header[], OutputStream> outputStreamProvider,
      final LongFunction<Integer> internalBufferSizeProvider,
      final List<String> acceptedMimes
  ) throws IOException {
    final HttpHost target;
    try {
      target = HttpHost.create(URI.create(resourceUri));
    } catch (Exception e) {
      throw new IllegalArgumentException("Can't decode URI: " + resourceUri, e);
    }
    final ClassicHttpRequest request = new BasicClassicHttpRequest(httpMethod, resourceUri);
    request.setHeader("Accept", acceptedMimes == null ? "*/*" : String.join(",", acceptedMimes));

    final HttpContext context = HttpClientContext.create();
    try (final ClassicHttpResponse response = httpClient.executeOpen(target, request, context)) {
      if (response.getCode() != HTTP_OK) {
        throw new HttpsNotOkStatusException(response.getReasonPhrase(), response.getCode());
      }

      HttpEntity entity = response.getEntity();
      if (entity != null) {
        try {
          final OutputStream outputStream = outputStreamProvider.apply(response.getHeaders());
          if (outputStream != null) {
            final long contentLength = entity.getContentLength();
            try (final InputStream inputStream = entity.getContent()) {
              final byte[] internalBuffer = new byte[internalBufferSizeProvider == null ? 16384 :
                  internalBufferSizeProvider.apply(contentLength)];
              long totalRead = 0;
              int bytesRead;
              while ((bytesRead = inputStream.read(internalBuffer, 0, internalBuffer.length)) !=
                  -1) {
                outputStream.write(internalBuffer, 0, bytesRead);
                totalRead += bytesRead;
                if (contentLength > 0) {
                  int progress = (int) ((totalRead * 100) / contentLength);
                  onProgressConsumer.apply(totalRead, contentLength, progress );
                } else {
                  onProgressConsumer.apply(totalRead, -1, -1);
                }
              }
              onProgressConsumer.apply(totalRead, contentLength,100);
            }
          }
        } finally {
          EntityUtils.consume(entity);
        }
      }
      return response.getHeaders();
    }
  }

  public HttpClient createHttpClient(
      final ProxySettings proxySettings,
      final boolean disableSslCheck,
      final Duration connectionTimeout
  ) {
    final HttpClientBuilder builder = HttpClients.custom();

    if (connectionTimeout != null) {
      final RequestConfig.Builder requestBuilder = RequestConfig.custom();
      requestBuilder.setConnectionRequestTimeout(connectionTimeout.toMillis(),
          TimeUnit.MILLISECONDS);
      builder.setDefaultRequestConfig(requestBuilder.build());
    }

    if (proxySettings != null) {
      if (proxySettings.hasCredentials()) {
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(new AuthScope(proxySettings.host, proxySettings.port),
            new NTCredentials(requireNonNullElse(proxySettings.username, ""),
                proxySettings.password == null ? new char[0] :
                    proxySettings.password.toCharArray(),
                extractComputerName(), extractDomainName()));
        builder.setDefaultCredentialsProvider(credentialsProvider);
      }

      final String[] ignoreForAddresses =
          proxySettings.nonProxyHosts == null ? new String[0] :
              proxySettings.nonProxyHosts.split("\\|");

      final WildCardMatcher[] matchers;

      if (ignoreForAddresses.length > 0) {
        matchers = new WildCardMatcher[ignoreForAddresses.length];
        for (int i = 0; i < ignoreForAddresses.length; i++) {
          matchers[i] = new WildCardMatcher(ignoreForAddresses[i]);
        }
      } else {
        matchers = new WildCardMatcher[0];
      }

      final HttpRoutePlanner routePlanner =
          new DefaultProxyRoutePlanner(proxySettings.asHttpHost()) {
            @Override
            protected HttpHost determineProxy(HttpHost target, HttpContext context) {
              HttpHost proxyHost = proxySettings.asHttpHost();
              final String hostName = target.getHostName();
              for (final WildCardMatcher m : matchers) {
                if (m.match(hostName)) {
                  proxyHost = null;
                  break;
                }
              }
              return proxyHost;
            }
          };
      builder.setRoutePlanner(routePlanner);
    }

    if (disableSslCheck) {
      builder.setConnectionManager(this.connectionManagerNoSslCheck);
    } else {
      builder.setConnectionManager(this.connectionManagerDefault);
    }

    return builder
        .setUserAgent("gosdk-wrapper-maven-plugin-agent/1.0.6")
        .disableCookieManagement()
        .build();
  }

  private static final class WildCardMatcher {

    private final Pattern pattern;
    private final String addressPattern;

    public WildCardMatcher(@Nonnull final String txt) {
      this.addressPattern = txt.trim();
      final StringBuilder builder = new StringBuilder();
      for (final char c : this.addressPattern.toCharArray()) {
        switch (c) {
          case '*':
            builder.append(".*");
            break;
          case '?':
            builder.append('.');
            break;
          default: {
            final String code = Integer.toHexString(c).toUpperCase(Locale.ENGLISH);
            builder.append("\\u").append("0000", 0, 4 - code.length()).append(code);
          }
          break;
        }
      }
      this.pattern = Pattern.compile(builder.toString());
    }

    public boolean match(@Nonnull final String txt) {
      return this.pattern.matcher(txt).matches();
    }

    @Nonnull
    @Override
    public String toString() {
      return this.addressPattern;
    }
  }

  public static class XGoogHashHeader {

    private final boolean valid;
    private final String crc32c;
    private final String md5;
    private final String unknownType;
    private final String unknownValue;

    public XGoogHashHeader(final Header[] headers) {
      String md5value = null;
      String crc32value = null;
      String unknownTypeValue = null;
      String unknownValueValue = null;

      boolean valid = true;

      BasicHeaderValueParser basicHeaderValueParser = new BasicHeaderValueParser();

      for (final Header header : headers) {
        HeaderElement[] elements = basicHeaderValueParser.parseElements(header.getValue(),
            new ParserCursor(0, header.getValue().length()));
        for (final HeaderElement e : elements) {
          final String name = e.getName();
          if (name.equalsIgnoreCase("md5")) {
            md5value = Hex.encodeHexString(Base64.decodeBase64(e.getValue()));
          } else if (name.equalsIgnoreCase("crc32c")) {
            crc32value = Hex.encodeHexString(Base64.decodeBase64(e.getValue()));
          } else {
            unknownTypeValue = name;
            unknownValueValue = e.getValue();
          }
        }
      }

      this.md5 = md5value;
      this.crc32c = crc32value;
      this.unknownType = unknownTypeValue;
      this.unknownValue = unknownValueValue;
      this.valid = valid;
    }

    @SuppressWarnings("unused")
    public boolean hasData() {
      return this.hasMd5() || this.hasCrc32c();
    }

    public boolean hasMd5() {
      return this.md5 != null;
    }

    @Nullable
    public String getMd5() {
      return this.md5;
    }

    public boolean hasCrc32c() {
      return this.crc32c != null;
    }

    @Nullable
    public String getCrc32c() {
      return this.crc32c;
    }

    public static boolean checkMd5(final InputStream in, final String expectedMd5)
        throws IOException {
      return requireNonNull(expectedMd5).trim().equalsIgnoreCase(DigestUtils.md5Hex(in));
    }

    public boolean isDataOk(final InputStream in) throws IOException {
      if (this.isValid()) {
        if (this.hasMd5()) {
          return checkMd5(in, this.getMd5());
        } else if (this.hasCrc32c()) {
          final CRC32C crc = new CRC32C();
          final byte[] buffer = new byte[0x1FFFF];
          int bytesRead;
          while ((bytesRead = in.read(buffer, 0, buffer.length)) !=
              -1) {
            if (bytesRead > 0) {
              crc.update(buffer, 0, bytesRead);
            }
          }
          final String thisCrc32 = this.getCrc32c();
          return thisCrc32 != null && thisCrc32.equalsIgnoreCase(Long.toHexString(crc.getValue()));
        }
        throw new IOException("XGoogHashHeader has neither MD5 nor CRC32 data records");
      } else {
        throw new IOException("XGoogHashHeader is invalid");
      }
    }

    @Nonnull
    @Override
    public String toString() {
      final StringBuilder result = new StringBuilder();
      result.append("XGoogHashHeader(valid=").append(this.valid)
          .append(",md5=").append(this.md5)
          .append(",crc32c=").append(this.crc32c);
      if (this.unknownType != null) {
        result.append(',').append(this.unknownType).append('=').append(this.unknownValue);
      }
      result.append(')');
      return result.toString();
    }

    public boolean isValid() {
      return this.valid;
    }
  }

}
