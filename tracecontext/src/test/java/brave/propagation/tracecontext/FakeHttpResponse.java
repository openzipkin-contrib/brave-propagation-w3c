/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.http.HttpClientResponse;
import brave.http.HttpServerResponse;

final class FakeHttpResponse {
  static final class Client extends HttpClientResponse {
    @Override public Object unwrap() {
      return this;
    }

    @Override public int statusCode() {
      return 200;
    }
  }

  static final class Server extends HttpServerResponse {
    @Override public Object unwrap() {
      return this;
    }

    @Override public int statusCode() {
      return 200;
    }
  }

  private FakeHttpResponse() {
  }
}
