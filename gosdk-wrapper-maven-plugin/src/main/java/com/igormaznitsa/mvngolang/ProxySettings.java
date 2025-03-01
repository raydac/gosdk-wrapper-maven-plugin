package com.igormaznitsa.mvngolang;

import static java.util.Objects.requireNonNull;

import javax.annotation.Nonnull;
import org.apache.hc.core5.http.HttpHost;

public class ProxySettings {

  public String host = "127.0.0.1";
  public String scheme = "http";
  public int port = 80;
  public String username;
  public String password = "";
  public String nonProxyHosts;

  public ProxySettings(
      final String scheme,
      final String host,
      final int port,
      final String username,
      final String password,
      final String nonProxyHosts
  ) {
    this.scheme = requireNonNull(scheme);
    this.host = requireNonNull(host);
    this.port = port;
    this.username = username;
    this.password = password;
    this.nonProxyHosts = nonProxyHosts;
  }

  public HttpHost asHttpHost() {
    return new HttpHost(this.scheme, this.host, this.port);
  }

  public boolean hasCredentials() {
    return this.username != null && this.password != null;
  }

  @Override
  @Nonnull
  public String toString() {
    return this.scheme + "://" + this.host + ":" + this.port;
  }
}
