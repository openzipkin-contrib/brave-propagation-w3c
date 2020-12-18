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

import brave.internal.Platform;
import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static brave.propagation.tracecontext.TraceparentFormat.parseTraceparentFormat;
import static brave.propagation.tracecontext.TraceparentFormat.writeTraceparentFormat;
import static brave.propagation.tracecontext.TraceparentFormat.writeTraceparentFormatAsBytes;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TraceparentFormatTest {
  String traceIdHigh = "1234567890123459";
  String traceId = "1234567890123451";
  String parentId = "1234567890123452";
  String spanId = "1234567890123453";

  @Mock Platform platform;

  /** unsampled isn't the same as not-yet-sampled, but we have no better choice */
  @Test void writeTraceparentFormat_notYetSampled_128() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test void writeTraceparentFormat_unsampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test void writeTraceparentFormat_sampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  /** debug isn't the same as sampled, but we have no better choice */
  @Test void writeTraceparentFormat_debug() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  /**
   * There is no field for the parent ID in "traceparent" format. What it calls "parent ID" is
   * actually the span ID.
   */
  @Test void writeTraceparentFormat_parent() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test void writeTraceparentFormat_largest() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(writeTraceparentFormat(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(writeTraceparentFormatAsBytes(context), UTF_8));
  }

  @Test void parseTraceparentFormat_sampled() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseTraceparentFormat_unsampled() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parseTraceparentFormat_padded() {
    assertThat(parseTraceparentFormat("00-0000000000000000" + traceId + "-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseTraceparentFormat_padded_right() {
    assertThat(parseTraceparentFormat("00-" + traceIdHigh + "0000000000000000-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseTraceparentFormat_newer_version() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parseTraceparentFormat_newer_version_ignores_extra_fields() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-00-fobaly"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parseTraceparentFormat_newer_version_ignores_extra_flags() {
    assertThat(parseTraceparentFormat("10-" + traceIdHigh + traceId + "-" + spanId + "-ff"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  /** for example, parsing inside tracestate */
  @Test void parseTraceparentFormat_middleOfString() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-01,";
    assertThat(parseTraceparentFormat(input, 3, input.length() - 1))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parseTraceparentFormat_middleOfString_incorrectIndex() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-00,";
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(parseTraceparentFormat(input, 0, 12))
        .isNull(); // instead of raising exception
    }

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "version", null);
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test void parseTraceparentFormat_ignoresBeforeAndAfter() {
    String encoded = "00-" + traceIdHigh + traceId + "-" + spanId + "-01";
    String sequence = "??" + encoded + "??";
    assertThat(parseTraceparentFormat(sequence, 2, 2 + encoded.length()))
      .usingRecursiveComparison().isEqualTo(parseTraceparentFormat(encoded));
  }

  @Test void parseTraceparentFormat_malformed() {
    assertParseTraceparentFormatIsNull("not-a-tumor");

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "version", null);
  }

  @Test void parseTraceparentFormat_malformed_notAscii() {
    assertParseTraceparentFormatIsNull(
      "00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "💩-1");

    verify(platform)
      .log("Invalid input: only valid characters are lower-hex for {0}", "parent ID", null);
  }

  @Test void parseTraceparentFormat_malformed_uuid() {
    assertParseTraceparentFormatIsNull("b970dafd-0d95-40aa-95d8-1d8725aebe40");

    verify(platform).log("Invalid input: {0} is too long", "version", null);
  }

  @Test void parseTraceparentFormat_short_traceId() {
    assertParseTraceparentFormatIsNull("00-" + traceId + "-" + spanId + "-01");

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test void parseTraceparentFormat_zero_traceId() {
    assertParseTraceparentFormatIsNull("00-00000000000000000000000000000000-" + spanId + "-01");

    verify(platform).log("Invalid input: read all zeros {0}", "trace ID", null);
  }

  @Test void parseTraceparentFormat_fails_on_extra_flags() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "-ff");

    verify(platform).log("Invalid input: only choices are 00 or 01 {0}", "trace flags", null);
  }

  @Test void parseTraceparentFormat_fails_on_extra_fields() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "-0-");

    verify(platform).log("Invalid input: {0} is too short", "trace flags", null);
  }

  @Test void parseTraceparentFormat_fails_on_version_ff() {
    assertParseTraceparentFormatIsNull("ff-" + traceIdHigh + traceId + "-" + spanId + "-01");

    verify(platform).log("Invalid input: ff {0}", "version", null);
  }

  @Test void parseTraceparentFormat_zero_spanId() {
    String parent = "00-" + traceIdHigh + traceId + "-0000000000000000-01";
    assertParseTraceparentFormatIsNull(parent);

    verify(platform).log("Invalid input: read all zeros {0}", "parent ID", null);
  }

  @Test void parseTraceparentFormat_empty() {
    assertParseTraceparentFormatIsNull("");

    verify(platform).log("Invalid input: empty", null);
  }

  @Test void parseTraceparentFormat_empty_version() {
    assertParseTraceparentFormatIsNull("-" + traceIdHigh + traceId + "-" + spanId + "-00");

    verify(platform).log("Invalid input: empty {0}", "version", null);
  }

  @Test void parseTraceparentFormat_empty_traceId() {
    assertParseTraceparentFormatIsNull("00--" + spanId + "-00");

    verify(platform).log("Invalid input: empty {0}", "trace ID", null);
  }

  @Test void parseTraceparentFormat_empty_spanId() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "--01");

    verify(platform).log("Invalid input: empty {0}", "parent ID", null);
  }

  @Test void parseTraceparentFormat_empty_flags() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "-");

    verify(platform).log("Invalid input: empty {0}", "trace flags", null);
  }

  @Test void parseTraceparentFormat_truncated_traceId() {
    assertParseTraceparentFormatIsNull("00-1-" + spanId + "-01");

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test void parseTraceparentFormat_truncated_traceId128() {
    assertParseTraceparentFormatIsNull("00-1" + traceId + "-" + spanId + "-01");

    verify(platform).log("Invalid input: {0} is too short", "trace ID", null);
  }

  @Test void parseTraceparentFormat_truncated_spanId() {
    assertParseTraceparentFormatIsNull(
      "00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "-00");

    verify(platform).log("Invalid input: {0} is too short", "parent ID", null);
  }

  @Test void parseTraceparentFormat_truncated_flags() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "-0");

    verify(platform).log("Invalid input: {0} is too short", "trace flags", null);
  }

  @Test void parseTraceparentFormat_traceIdTooLong() {
    assertParseTraceparentFormatIsNull(
      "00-" + traceIdHigh + traceId + "a" + "-" + spanId + "-0");

    verify(platform).log("Invalid input: {0} is too long", "trace ID", null);
  }

  @Test void parseTraceparentFormat_spanIdTooLong() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "a-0");

    verify(platform).log("Invalid input: {0} is too long", "parent ID", null);
  }

  @Test void parseTraceparentFormat_flagsTooLong() {
    assertParseTraceparentFormatIsNull("00-" + traceIdHigh + traceId + "-" + spanId + "-001");

    verify(platform).log("Invalid input: too long", null);
  }

  /**
   * This calls {@link TraceparentFormat#parseTraceparentFormat(CharSequence)}, after setting {@link
   * Platform#get()} to return {@link #platform} for log assertions.
   */
  void assertParseTraceparentFormatIsNull(String encoded) {
    try (MockedStatic<Platform> mb = mockStatic(Platform.class)) {
      mb.when(Platform::get).thenReturn(platform);

      assertThat(TraceparentFormat.parseTraceparentFormat(encoded))
        .isNull(); // instead of raising exception
    }
  }
}
