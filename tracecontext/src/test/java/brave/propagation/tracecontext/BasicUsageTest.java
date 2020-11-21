/*
 * Copyright 2020 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package brave.propagation.tracecontext;

import brave.ScopedSpan;
import brave.Span;
import brave.Tracing;
import brave.http.HttpClientHandler;
import brave.http.HttpServerHandler;
import brave.http.HttpTracing;
import brave.test.TestSpanHandler;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

// Do not bring any dependencies into this test without looking at src/it/pom.xml as this is used
// to verify we don't depend on internals.
public class BasicUsageTest {
  TestSpanHandler spans = new TestSpanHandler();

  @Test public void basicUsage() {
    try (Tracing tracing = Tracing.newBuilder()
      .propagationFactory(TraceContextPropagation.create())
      .addSpanHandler(spans)
      .build()) {

      ScopedSpan parent = tracing.tracer().startScopedSpan("parent");

      assertThat(parent.context().sampled()).isTrue(); // sanity check

      try (HttpTracing httpTracing = HttpTracing.create(tracing)) {
        HttpClientHandler clientHandler = HttpClientHandler.create(httpTracing);
        HttpServerHandler serverHandler = HttpServerHandler.create(httpTracing);

        FakeHttpRequest.Client request = new FakeHttpRequest.Client("/");
        Span client = clientHandler.handleSend(request);
        assertThat(client.context().parentIdString())
          .isEqualTo(parent.context().spanIdString()); // sanity check

        assertThat(request.headers)
          // Doesn't dual-propagate b3 at the moment
          .containsOnlyKeys("traceparent", "tracestate");

        Span server = serverHandler.handleReceive(new FakeHttpRequest.Server(request));
        assertThat(server.context().parentIdString())
          .isEqualTo(client.context().spanIdString()); // trace continued

        server.finish();
        client.finish();
      } finally {
        parent.finish();
      }
    }
  }
}
