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

import brave.internal.Nullable;
import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import brave.propagation.tracecontext.TraceContextPropagation.LoggerHolder;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.internal.codec.HexCodec.lowerHexToUnsignedLong;
import static brave.propagation.tracecontext.TraceContextPropagation.logOrThrow;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TraceContextPropagationTest {
  Map<String, String> request = new LinkedHashMap<>();
  Propagation.Factory propagation = TraceContextPropagation.newFactoryBuilder().build();
  Injector<Map<String, String>> injector = propagation.get().injector(Map::put);
  Extractor<Map<String, String>> extractor = propagation.get().extractor(Map::get);

  TraceContext sampledContext = TraceContext.newBuilder()
    .traceIdHigh(lowerHexToUnsignedLong("67891233abcdef01"))
    .traceId(lowerHexToUnsignedLong("2345678912345678"))
    .spanId(lowerHexToUnsignedLong("463ac35c9f6413ad"))
    .sampled(true)
    .build();
  String validTraceparent = "00-67891233abcdef012345678912345678-463ac35c9f6413ad-01";
  String validB3Single = "67891233abcdef012345678912345678-463ac35c9f6413ad-1";
  String otherState = "congo=t61rcWkgMzE";

  @Mock Logger logger;

  @Test void injects_b3_when_no_other_tracestate() {
    injector.inject(sampledContext, request);

    assertThat(request)
      .containsEntry("traceparent", validTraceparent)
      .containsEntry("tracestate", "b3=" + validB3Single);
  }

  @Test void injects_b3_before_other_tracestate() {
    TraceContext withTracestate =
      sampledContext.toBuilder().addExtra(new Tracestate(otherState)).build();

    injector.inject(withTracestate, request);

    assertThat(request)
      .containsEntry("traceparent", validTraceparent)
      .containsEntry("tracestate", "b3=" + validB3Single + "," + otherState);
  }

  @Test void extracts_b3_when_no_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single);

    assertExtracted(extractor.extract(request).context(), null);
  }

  @Test void extracts_b3_before_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single + "," + otherState);

    assertExtracted(extractor.extract(request).context(), otherState);
  }

  @Test void extracts_b3_after_other_tracestate() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", otherState + ",b3=" + validB3Single);

    assertExtracted(extractor.extract(request).context(), otherState);
  }

  @Test void extracts_b3_between_other_tracestate_withSpaces() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "app_id=1, b3=" + validB3Single + ", " + otherState);

    assertExtracted(extractor.extract(request).context(), "app_id=1," + otherState);
  }

  @Test void extracted_toString() {
    request.put("traceparent", validTraceparent);
    request.put("tracestate", "b3=" + validB3Single + "," + otherState);

    assertThat(extractor.extract(request)).hasToString(
      "Extracted{"
        + "traceContext=" + sampledContext + ", "
        + "samplingFlags=SAMPLED_REMOTE, "
        + "extra=[Tracestate{" + otherState + "}]"
        + "}");
  }

  @Test void logOrThrow_logs() {
    when(logger.isLoggable(Level.FINE)).thenReturn(true);

    try (MockedStatic<LoggerHolder> mb = mockStatic(LoggerHolder.class)) {
      mb.when(LoggerHolder::logger).thenReturn(logger);

      assertThat(logOrThrow("hello", false))
        .isFalse(); // instead of raising exception
    }

    verify(logger).log(Level.FINE, "hello");
  }

  @Test void logOrThrow_skips_log() {
    when(logger.isLoggable(Level.FINE)).thenReturn(false);

    try (MockedStatic<LoggerHolder> mb = mockStatic(LoggerHolder.class)) {
      mb.when(LoggerHolder::logger).thenReturn(logger);

      assertThat(logOrThrow("hello", false))
        .isFalse(); // instead of raising exception
    }

    verify(logger).isLoggable(Level.FINE);
    verifyNoMoreInteractions(logger);
  }

  @Test void logOrThrow_logs_param() {
    when(logger.isLoggable(Level.FINE)).thenReturn(true);

    try (MockedStatic<LoggerHolder> mb = mockStatic(LoggerHolder.class)) {
      mb.when(LoggerHolder::logger).thenReturn(logger);

      assertThat(logOrThrow("hello {0}", "world", false))
        .isFalse(); // instead of raising exception
    }

    verify(logger).log(any(LogRecord.class));
  }

  @Test void logOrThrow_skips_log_param() {
    when(logger.isLoggable(Level.FINE)).thenReturn(false);

    try (MockedStatic<LoggerHolder> mb = mockStatic(LoggerHolder.class)) {
      mb.when(LoggerHolder::logger).thenReturn(logger);

      assertThat(logOrThrow("hello {0}", "world", false))
        .isFalse(); // instead of raising exception
    }

    verify(logger).isLoggable(Level.FINE);
    verifyNoMoreInteractions(logger);
  }

  void assertExtracted(TraceContext extracted, @Nullable String otherState) {
    assertThat(extracted)
      .usingRecursiveComparison().ignoringFields("extraList")
      .isEqualTo(sampledContext);
    if (otherState == null) {
      assertThat(extracted.findExtra(Tracestate.class).otherState).isNull();
    } else {
      assertThat(extracted.findExtra(Tracestate.class).otherState)
        .hasToString(otherState);
    }
  }
}
