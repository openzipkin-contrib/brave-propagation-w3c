/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.http.HttpClientRequest;
import brave.http.HttpServerRequest;
import java.util.LinkedHashMap;
import java.util.Map;

final class FakeHttpRequest {
  static final class Client extends HttpClientRequest {
    final String path;
    final Map<String, String> headers = new LinkedHashMap<>();

    Client(String path) {
      this.path = path;
    }

    @Override public Object unwrap() {
      return this;
    }

    @Override public String method() {
      return "GET";
    }

    @Override public String path() {
      return path;
    }

    @Override public String url() {
      return null;
    }

    @Override public void header(String name, String value) {
      headers.put(name, value);
    }

    @Override public String header(String name) {
      return headers.get(name);
    }
  }

  static final class Server extends HttpServerRequest {
    final String path;
    final Map<String, String> headers;

    Server(Client incoming) {
      this.path = incoming.path;
      this.headers = new LinkedHashMap<>(incoming.headers);
    }

    @Override public Object unwrap() {
      return this;
    }

    @Override public String method() {
      return "GET";
    }

    @Override public String path() {
      return path;
    }

    @Override public String url() {
      return null;
    }

    public void header(String name, String value) {
      headers.put(name, value);
    }

    @Override public String header(String name) {
      return headers.get(name);
    }
  }

  private FakeHttpRequest() {
  }
}
