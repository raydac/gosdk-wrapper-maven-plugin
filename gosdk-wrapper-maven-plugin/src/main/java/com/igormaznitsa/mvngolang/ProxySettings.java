package com.igormaznitsa.mvngolang;

import static java.util.Objects.requireNonNull;

import org.apache.hc.core5.http.HttpHost;

/**
 * Container keeps Proxy parameters.
 *
 * @since 1.0.0
 */
public class ProxySettings {
  /**
   * Proxy host address, for instance 127.0.0.1
   */
  public String host = "127.0.0.1";
  /**
   * Proxy server scheme, for instance http
   */
  public String scheme = "http";
  /**
   * Proxy server port, for instance 80
   */
  public int port = 80;
  /**
   * Optional username.
   */
  public String username;
  /**
   * Optional password
   */
  public String password = "";
  /**
   * Optional list of non-proxy hosts separated by vertical bar.
   */
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
  public String toString() {
    return "ProxySettings{" +
        "host='" + host + '\'' +
        ", scheme='" + scheme + '\'' +
        ", port=" + port +
        ", username='" + username + '\'' +
        ", password='" + password + '\'' +
        ", nonProxyHosts='" + nonProxyHosts + '\'' +
        '}';
  }
}
