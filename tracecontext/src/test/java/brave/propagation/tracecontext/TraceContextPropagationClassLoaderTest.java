/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static brave.test.util.ClassLoaders.assertRunIsUnloadable;

class TraceContextPropagationClassLoaderTest {
  @Test void unloadable_afterBasicUsage() {
    assertRunIsUnloadable(BasicUsage.class, getClass().getClassLoader());
  }

  static class BasicUsage implements Runnable {
    @Override public void run() {
      Propagation<String> propagation = TraceContextPropagation.get();
      Injector<Map<String, String>> injector = propagation.injector(Map::put);
      Extractor<Map<String, String>> extractor = propagation.extractor(Map::get);

      TraceContext context = TraceContext.newBuilder().traceId(1L).spanId(2L).build();

      Map<String, String> headers = new LinkedHashMap<>();
      injector.inject(context, headers);

      String traceparent = headers.get("traceparent");
      if (!"00-00000000000000000000000000000001-0000000000000002-00".equals(traceparent)) {
        throw new AssertionError(traceparent);
      }

      if (!context.equals(extractor.extract(headers).context())) {
        throw new AssertionError();
      }
    }
  }
}
