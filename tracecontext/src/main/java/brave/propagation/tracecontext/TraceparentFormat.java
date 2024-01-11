/*
 * Copyright 2020-2024 The OpenZipkin Authors
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
import brave.propagation.TraceContext;
import java.nio.ByteBuffer;

import static brave.internal.codec.HexCodec.writeHexLong;

/** Implements <a href="https://tracecontext.github.io/trace-context/#traceparent-header">...</a> */
// TODO: this uses the internal Platform class as it defers access to the logger and makes JUL less
// expensive. We should inline that here to to unhook the internal dep.
final class TraceparentFormat {
  static final TraceparentFormat INSTANCE = new TraceparentFormat(false);

  static TraceparentFormat get() {
    return INSTANCE;
  }

  /** Version '00' is fixed length, though future versions may be longer. */
  static final int FORMAT_LENGTH = 3 + 32 + 1 + 16 + 3; // 00-traceid128-spanid-01
  static final int // instead of enum for smaller bytecode
    FIELD_VERSION = 1,
    FIELD_TRACE_ID = 2,
    FIELD_PARENT_ID = 3,
    FIELD_TRACE_FLAGS = 4;

  final boolean shouldThrow;

  TraceparentFormat(boolean shouldThrow) {
    this.shouldThrow = shouldThrow;
  }

  /** Writes all "traceparent" defined fields in the trace context to a hyphen delimited string. */
  String write(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = write(context, buffer);
    return new String(buffer, 0, length);
  }

  /**
   * Like {@link #write(TraceContext)}, but for requests with byte array or byte buffer values. For
   * example, {@link ByteBuffer#wrap(byte[])} can wrap the result.
   */
  byte[] writeAsBytes(TraceContext context) {
    char[] buffer = getCharBuffer();
    int length = write(context, buffer);
    return asciiToNewByteArray(buffer, length);
  }

  int write(TraceContext context, char[] result) {
    int pos = 0;
    result[pos++] = '0';
    result[pos++] = '0';
    result[pos++] = '-';
    long traceIdHigh = context.traceIdHigh();
    writeHexLong(result, pos, traceIdHigh);
    pos += 16;
    writeHexLong(result, pos, context.traceId());
    pos += 16;
    result[pos++] = '-';
    writeHexLong(result, pos, context.spanId());
    pos += 16;

    result[pos++] = '-';
    result[pos++] = '0';
    result[pos++] = Boolean.TRUE.equals(context.sampled()) ? '1' : '0';

    return pos;
  }

  @Nullable TraceContext parse(CharSequence parent) {
    return parse(parent, 0, parent.length(), shouldThrow);
  }

  /**
   * This reads a trace context a sequence potentially larger than the format. The use-case is
   * reducing garbage, by re-using the input {@code value} across multiple parse operations.
   *
   * @param value      the sequence that contains a {@code traceparent} formatted trace context
   * @param beginIndex the inclusive begin index: {@linkplain CharSequence#charAt(int) index} of the
   *                   first character in {@code traceparent} format.
   * @param endIndex   the exclusive end index: {@linkplain CharSequence#charAt(int) index}
   *                   <em>after</em> the last character in {@code traceparent} format.
   */
  @Nullable TraceContext parse(CharSequence value, int beginIndex, int endIndex) {
    return parse(value, beginIndex, endIndex, shouldThrow);
  }

  @Nullable
  static TraceContext parse(CharSequence value, int beginIndex, int endIndex, boolean shouldThrow) {
    int length = endIndex - beginIndex;

    if (length == 0) {
      TraceContextPropagation.logOrThrow("Invalid input: empty", shouldThrow);
      return null;
    }

    // Benchmarks show no difference in memory usage re-using with thread local vs newing each time.
    TraceContext.Builder builder = TraceContext.newBuilder();

    int version = 0;
    boolean traceIdHighZero = false;

    int currentField = FIELD_VERSION, currentFieldLength = 0;
    // Used for hex-decoding, performed by bitwise addition
    long buffer = 0L;

    // Instead of pos < endIndex, this uses pos <= endIndex to keep field processing consolidated.
    // Otherwise, we'd have to process again when outside the loop to handle dangling data on EOF.
    LOOP:
    for (int pos = beginIndex; pos <= endIndex; pos++) {
      // treat EOF same as a hyphen for simplicity
      boolean isEof = pos == endIndex;
      char c = isEof ? '-' : value.charAt(pos);

      if (c == '-') {
        if (!validateFieldLength(currentField, currentFieldLength, shouldThrow)) {
          return null;
        }

        switch (currentField) {
          case FIELD_VERSION:
            // 8-bit unsigned 255 is disallowed https://tracecontext.github.io/trace-context/#version
            version = (int) buffer;
            if (version == 0xff) {
              logOrThrow(currentField, "Invalid input: ff {0}", shouldThrow);
              return null;
            } else if (version == 0 && length > FORMAT_LENGTH) {
              TraceContextPropagation.logOrThrow("Invalid input: too long", shouldThrow);
              return null;
            }

            currentField = FIELD_TRACE_ID;
            break;
          case FIELD_TRACE_ID:
            if (traceIdHighZero && buffer == 0L) {
              logReadAllZeros(currentField, shouldThrow);
              return null;
            }

            builder.traceId(buffer);

            currentField = FIELD_PARENT_ID;
            break;
          case FIELD_PARENT_ID:
            if (buffer == 0L) {
              logReadAllZeros(currentField, shouldThrow);
              return null;
            }

            builder.spanId(buffer);

            currentField = FIELD_TRACE_FLAGS;
            break;
          case FIELD_TRACE_FLAGS:
            int traceparentFlags = (int) (buffer & 0xff);
            // Only one flag is defined at version 0: sampled
            // https://tracecontext.github.io/trace-context/#sampled-flag
            builder.sampled(((traceparentFlags & 1) == 1));

            // If the version is greater than zero ignore other flags and fields
            // https://tracecontext.github.io/trace-context/#other-flags
            if (version == 0) {
              if ((traceparentFlags & ~1) != 0) {
                logOrThrow(currentField, "Invalid input: only choices are 00 or 01 {0}",
                  shouldThrow);
                return null;
              }

              if (!isEof) {
                TraceContextPropagation.logOrThrow("Invalid input: more than 3 fields exist",
                  shouldThrow);
                return null;
              }
            }
            break LOOP;
          default:
            throw new AssertionError();
        }

        buffer = 0L;
        currentFieldLength = 0;
        continue;
      }

      // At this point, 'c' is not a hyphen

      // When we get to a non-hyphen at position 16, we have a 128-bit trace ID.
      if (currentField == FIELD_TRACE_ID && currentFieldLength == 16) {
        // traceIdHigh can be zeros when a 64-bit trace ID is encoded in 128-bits.
        traceIdHighZero = buffer == 0L;
        builder.traceIdHigh(buffer);

        // This character is the next hex. If it isn't, the next iteration will throw. Either way,
        // reset so that we can capture the next 16 characters of the trace ID.
        buffer = 0L;
      }

      currentFieldLength++;

      // The rest of this is normal lower-hex decoding
      buffer <<= 4;
      if (c >= '0' && c <= '9') {
        buffer |= c - '0';
      } else if (c >= 'a' && c <= 'f') {
        buffer |= c - 'a' + 10;
      } else {
        logOrThrow(currentField, "Invalid input: only valid characters are lower-hex for {0}",
          shouldThrow);
        return null;
      }
    }

    return builder.build();
  }

  static boolean validateFieldLength(int field, int length, boolean shouldThrow) {
    int expectedLength = (field == FIELD_VERSION || field == FIELD_TRACE_FLAGS)
      ? 2  // There are two fields that are 2 characters long: version and flags
      : field == FIELD_TRACE_ID ? 32 : 16; // trace ID or span ID
    if (length == 0) {
      return logOrThrow(field, "Invalid input: empty {0}", shouldThrow);
    } else if (length < expectedLength) {
      return logOrThrow(field, "Invalid input: {0} is too short", shouldThrow);
    } else if (length > expectedLength) {
      return logOrThrow(field, "Invalid input: {0} is too long", shouldThrow);
    }
    return true;
  }

  static void logReadAllZeros(int currentField, boolean shouldThrow) {
    logOrThrow(currentField, "Invalid input: read all zeros {0}", shouldThrow);
  }

  static boolean logOrThrow(int fieldCode, String msg, boolean shouldThrow) {
    String field;
    switch (fieldCode) {
      case FIELD_VERSION:
        field = "version";
        break;
      case FIELD_TRACE_ID:
        field = "trace ID";
        break;
      // Confusingly, the spec calls the span ID field parentId
      // https://tracecontext.github.io/trace-context/#parent-id
      case FIELD_PARENT_ID:
        field = "parent ID";
        break;
      case FIELD_TRACE_FLAGS:
        field = "trace flags";
        break;
      default:
        throw new AssertionError("field code unmatched: " + fieldCode);
    }
    return TraceContextPropagation.logOrThrow(msg, field, shouldThrow);
  }

  static byte[] asciiToNewByteArray(char[] buffer, int length) {
    byte[] result = new byte[length];
    for (int i = 0; i < length; i++) {
      result[i] = (byte) buffer[i];
    }
    return result;
  }

  static final ThreadLocal<char[]> CHAR_BUFFER = new ThreadLocal<>();

  static char[] getCharBuffer() {
    char[] charBuffer = CHAR_BUFFER.get();
    if (charBuffer == null) {
      charBuffer = new char[FORMAT_LENGTH];
      CHAR_BUFFER.set(charBuffer);
    }
    return charBuffer;
  }
}
