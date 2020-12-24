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

import brave.propagation.TraceContext;
import org.junit.jupiter.api.Test;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TraceparentFormatTest {
  String traceIdHigh = "1234567890123459";
  String traceId = "1234567890123451";
  String parentId = "1234567890123452";
  String spanId = "1234567890123453";

  TraceparentFormat traceparentFormat = new TraceparentFormat(true);

  /** unsampled isn't the same as not-yet-sampled, but we have no better choice */
  @Test void write_notYetSampled_128() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16)).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  @Test void write_unsampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-00")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  @Test void write_sampled() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  /** debug isn't the same as sampled, but we have no better choice */
  @Test void write_debug() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  /**
   * There is no field for the parent ID in "traceparent" format. What it calls "parent ID" is
   * actually the span ID.
   */
  @Test void write_parent() {
    TraceContext context = TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-0000000000000000" + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  @Test void write_largest() {
    TraceContext context = TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .parentId(Long.parseUnsignedLong(parentId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .debug(true).build();

    assertThat(traceparentFormat.write(context))
      .isEqualTo("00-" + traceIdHigh + traceId + "-" + spanId + "-01")
      .isEqualTo(new String(traceparentFormat.writeAsBytes(context), UTF_8));
  }

  @Test void parse_sampled() {
    assertThat(traceparentFormat.parse("00-" + traceIdHigh + traceId + "-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parse_unsampled() {
    assertThat(traceparentFormat.parse("00-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parse_padded() {
    assertThat(
      traceparentFormat.parse("00-0000000000000000" + traceId + "-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parse_padded_right() {
    assertThat(
      traceparentFormat.parse("00-" + traceIdHigh + "0000000000000000-" + spanId + "-01"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parse_newer_version() {
    assertThat(traceparentFormat.parse("10-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parse_newer_version_ignores_extra_fields() {
    assertThat(
      traceparentFormat.parse("10-" + traceIdHigh + traceId + "-" + spanId + "-00-fobaly"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(false).build()
    );
  }

  @Test void parse_newer_version_ignores_extra_flags() {
    assertThat(traceparentFormat.parse("10-" + traceIdHigh + traceId + "-" + spanId + "-ff"))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  /** for example, parsing inside tracestate */
  @Test void parse_middleOfString() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-01,";
    assertThat(traceparentFormat.parse(input, 3, input.length() - 1))
      .usingRecursiveComparison().isEqualTo(TraceContext.newBuilder()
      .traceIdHigh(Long.parseUnsignedLong(traceIdHigh, 16))
      .traceId(Long.parseUnsignedLong(traceId, 16))
      .spanId(Long.parseUnsignedLong(spanId, 16))
      .sampled(true).build()
    );
  }

  @Test void parse_middleOfString_incorrectIndex() {
    String input = "tc=00-" + traceIdHigh + traceId + "-" + spanId + "-00,";

    assertThatThrownBy(() -> traceparentFormat.parse(input, 0, 12))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: only valid characters are lower-hex for version");
  }

  /** This tests that the being index is inclusive and the end index is exclusive */
  @Test void parse_ignoresBeforeAndAfter() {
    String encoded = "00-" + traceIdHigh + traceId + "-" + spanId + "-01";
    String sequence = "??" + encoded + "??";
    assertThat(traceparentFormat.parse(sequence, 2, 2 + encoded.length()))
      .usingRecursiveComparison().isEqualTo(traceparentFormat.parse(encoded));
  }

  @Test void parse_malformed() {
    assertThatThrownBy(() -> traceparentFormat.parse("not-a-tumor"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: only valid characters are lower-hex for version");
  }

  @Test void parse_malformed_notAscii() {
    assertThatThrownBy(() -> traceparentFormat.parse(
      "00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "💩-1"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: only valid characters are lower-hex for parent ID");
  }

  @Test void parse_malformed_uuid() {
    assertThatThrownBy(() -> traceparentFormat.parse("b970dafd-0d95-40aa-95d8-1d8725aebe40"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: version is too long");
  }

  @Test void parse_short_traceId() {
    String parent = "00-" + traceId + "-" + spanId + "-01";
    assertThatThrownBy(() -> traceparentFormat.parse(parent))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace ID is too short");
  }

  @Test void parse_zero_traceId() {
    String parent = "00-00000000000000000000000000000000-" + spanId + "-01";
    assertThatThrownBy(() -> traceparentFormat.parse(parent))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: read all zeros trace ID");
  }

  @Test void parse_fails_on_extra_flags() {
    String parent = "00-" + traceIdHigh + traceId + "-" + spanId + "-ff";
    assertThatThrownBy(() -> traceparentFormat.parse(parent))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: only choices are 00 or 01 trace flags");
  }

  @Test void parse_fails_on_extra_fields() {
    String parent = "00-" + traceIdHigh + traceId + "-" + spanId + "-0-";
    assertThatThrownBy(() -> traceparentFormat.parse(parent))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace flags is too short");
  }

  @Test void parse_fails_on_version_ff() {
    String input = "ff-" + traceIdHigh + traceId + "-" + spanId + "-01";
    assertThatThrownBy(() -> traceparentFormat.parse(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: ff version");
  }

  @Test void parse_zero_spanId() {
    String parent = "00-" + traceIdHigh + traceId + "-0000000000000000-01";
    assertThatThrownBy(() -> traceparentFormat.parse(parent))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: read all zeros parent ID");
  }

  @Test void parse_empty() {
    assertThatThrownBy(() -> traceparentFormat.parse(""))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: empty");
  }

  @Test void parse_empty_version() {
    assertThatThrownBy(
      () -> traceparentFormat.parse("-" + traceIdHigh + traceId + "-" + spanId + "-00"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: empty version");
  }

  @Test void parse_empty_traceId() {
    assertThatThrownBy(() -> traceparentFormat.parse("00--" + spanId + "-00"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: empty trace ID");
  }

  @Test void parse_empty_spanId() {
    assertThatThrownBy(() -> traceparentFormat.parse("00-" + traceIdHigh + traceId + "--01"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: empty parent ID");
  }

  @Test void parse_empty_flags() {
    assertThatThrownBy(
      () -> traceparentFormat.parse("00-" + traceIdHigh + traceId + "-" + spanId + "-"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: empty trace flags");
  }

  @Test void parse_truncated_traceId() {
    assertThatThrownBy(() -> traceparentFormat.parse("00-1-" + spanId + "-01"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace ID is too short");
  }

  @Test void parse_truncated_traceId128() {
    assertThatThrownBy(() -> traceparentFormat.parse("00-1" + traceId + "-" + spanId + "-01"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace ID is too short");
  }

  @Test void parse_truncated_spanId() {
    assertThatThrownBy(() -> traceparentFormat.parse(
      "00-" + traceIdHigh + traceId + "-" + spanId.substring(0, 15) + "-00"))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: parent ID is too short");
  }

  @Test void parse_truncated_flags() {
    String input = "00-" + traceIdHigh + traceId + "-" + spanId + "-0";
    assertThatThrownBy(() -> traceparentFormat.parse(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace flags is too short", "trace flags");
  }

  @Test void parse_traceIdTooLong() {
    String input = "00-" + traceIdHigh + traceId + "a" + "-" + spanId + "-0";
    assertThatThrownBy(() -> traceparentFormat.parse(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: trace ID is too long");
  }

  @Test void parse_spanIdTooLong() {
    String input = "00-" + traceIdHigh + traceId + "-" + spanId + "a-0";
    assertThatThrownBy(() -> traceparentFormat.parse(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: parent ID is too long");
  }

  @Test void parse_flagsTooLong() {
    String input = "00-" + traceIdHigh + traceId + "-" + spanId + "-001";
    assertThatThrownBy(() -> traceparentFormat.parse(input))
      .isInstanceOf(IllegalArgumentException.class)
      .hasMessage("Invalid input: too long");
  }
}
