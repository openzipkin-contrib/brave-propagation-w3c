/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpClientRequest;
import brave.http.HttpClientResponse;
import brave.http.HttpServerHandler;
import brave.http.HttpServerRequest;
import brave.http.HttpServerResponse;
import brave.http.HttpTracing;
import brave.test.TestSpanHandler;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Do not bring any dependencies into this test without looking at src/it/pom.xml as this is used
// to verify we don't depend on internals.
class BasicUsageTest {
  TestSpanHandler spans = new TestSpanHandler();

  @Test void basicUsage() {
    try (Tracing tracing = Tracing.newBuilder()
      .propagationFactory(TraceContextPropagation.FACTORY)
      .addSpanHandler(spans)
      .build()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");

      assertThat(parent.context().sampled()).isTrue(); // sanity check

      try (HttpTracing httpTracing = HttpTracing.create(tracing)) {
        HttpClientHandler<HttpClientRequest, HttpClientResponse> clientHandler = HttpClientHandler.create(httpTracing);
        HttpServerHandler<HttpServerRequest, HttpServerResponse> serverHandler = HttpServerHandler.create(httpTracing);

        FakeHttpRequest.Client request = new FakeHttpRequest.Client("/");
        Span client = clientHandler.handleSend(request);
        assertThat(client.context().parentIdString())
          .isEqualTo(parent.context().spanIdString()); // sanity check

        assertThat(request.headers)
          // Doesn't dual-propagate b3 at the moment
          .containsOnlyKeys("traceparent", "tracestate");

        Span server = serverHandler.handleReceive(new FakeHttpRequest.Server(request));
        assertThat(server.context().parentIdString())
          .isEqualTo(client.context().parentIdString()); // trace continued

        server.finish();
        client.finish();
      } finally {
        parent.finish();
      }
    }
  }
}
