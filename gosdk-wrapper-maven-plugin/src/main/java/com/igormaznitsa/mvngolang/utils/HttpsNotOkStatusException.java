package com.igormaznitsa.mvngolang.utils;

import java.io.IOException;

public class HttpsNotOkStatusException extends IOException {
  private final int status;
  private final String reason;

  public HttpsNotOkStatusException(final String reason, final int status) {
    this.reason = reason;
    this.status = status;
  }

  public int getStatus() {
    return this.status;
  }

  public String getReason() {
    return this.reason;
  }

  @Override
  public String toString() {
    return "HttpsNotOkStatusException{" +
        "status=" + status +
        ", reason='" + reason + '\'' +
        '}';
  }
}
