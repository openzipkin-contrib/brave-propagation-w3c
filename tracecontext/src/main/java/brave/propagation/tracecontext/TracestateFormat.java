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

import brave.internal.codec.EntrySplitter;

import static brave.internal.codec.CharSequences.regionMatches;
import static brave.propagation.tracecontext.TraceContextPropagation.logOrThrow;

/**
 * Implements https://tracecontext.github.io/trace-context/#tracestate-header
 *
 * <p>In the above specification, a tracestate entry is sometimes called member. The key of the
 * entry is most often called vendor name, but it is more about a tracing system vs something vendor
 * specific. We choose to not use the term vendor as this is open source code. Instead, we use term
 * entry (key/value).
 */
final class TracestateFormat implements EntrySplitter.Handler<int[]> {
  static final TracestateFormat INSTANCE = new TracestateFormat("b3", false);

  static TracestateFormat get() {
    return INSTANCE;
  }

  final String thisKey;
  final boolean shouldThrow;
  final EntrySplitter entrySplitter;

  TracestateFormat(String thisKey, boolean shouldThrow) {
    this.thisKey = thisKey;
    this.shouldThrow = shouldThrow;
    entrySplitter = EntrySplitter.newBuilder()
      .maxEntries(32) // https://www.w3.org/TR/trace-context/#list
      .entrySeparator(',')
      .trimOWSAroundEntrySeparator(true) // https://www.w3.org/TR/trace-context/#list
      .keyValueSeparator('=')
      .trimOWSAroundKeyValueSeparator(false) // https://github.com/w3c/trace-context/pull/411
      .shouldThrow(shouldThrow)
      .build();
  }

  // Simplify parsing rules by allowing value-based lookup on an ASCII value.
  //
  // This approach is similar to io.netty.util.internal.StringUtil.HEX2B as it uses an array to
  // cache values. Unlike HEX2B, this requires a bounds check when using the character's integer
  // value as a key.
  //
  // The performance cost of a bounds check is still better than using BitSet, and avoids allocating
  // an array of 64 thousand booleans: that could be problematic in old JREs or Android.
  static int LAST_VALID_KEY_CHAR = 'z';
  static boolean[] VALID_KEY_CHARS = new boolean[LAST_VALID_KEY_CHAR + 1];
  static int LAST_VALID_VALUE_CHAR = '~';
  static boolean[] VALID_VALUE_CHARS = new boolean[LAST_VALID_VALUE_CHAR + 1];

  static {
    for (char c = 0; c < VALID_KEY_CHARS.length; c++) {
      VALID_KEY_CHARS[c] = isValidTracestateKeyChar(c);
    }
    for (char c = 0; c < VALID_VALUE_CHARS.length; c++) {
      VALID_VALUE_CHARS[c] = isValidTracestateValueChar(c);
    }
  }

  static boolean isValidTracestateKeyChar(char c) {
    return isLetterOrNumber(c) || c == '@' || c == '_' || c == '-' || c == '*' || c == '/';
  }

  static boolean isLetterOrNumber(char c) {
    return (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9');
  }

  static boolean isValidTracestateValueChar(char c) {
    return c >= ' ' && c <= '~' && c != ',' && c != '=';
  }

  @Override public boolean onEntry(int[] target,
    CharSequence buffer, int beginKey, int endKey, int beginValue, int endValue) {
    if (!validateKey(buffer, beginKey, endKey) || !validateValue(buffer, beginValue, endValue)) {
      return false;
    }

    // If we receive upstream data for our key, mark the offsets so we can parse them later.
    if (regionMatches(thisKey, buffer, beginKey, endKey)) {
      target[1] = beginKey;
      target[2] = endKey;
      target[3] = beginValue;
      target[4] = endValue;
    } else if (target[1] != -1 && target[5] == -1) {
      target[5] = beginKey;
    } else if (target[0] == -1) {
      target[0] = endValue;
    }

    return true;
  }

  boolean parseInto(String tracestateString, int[] indices) {
    return entrySplitter.parse(this, indices, tracestateString);
  }

  /**
   * Performs validation according to the ABNF of the {@code tracestate} key.
   *
   * <p>See https://www.w3.org/TR/trace-context-1/#key
   */
  // Logic to narrow error messages is intentionally deferred.
  // Performance matters as this could be called up to 32 times per header.
  boolean validateKey(CharSequence buffer, int beginKey, int endKey) {
    int length = endKey - beginKey;
    if (length == 0) return logOrThrow("Invalid key: empty", shouldThrow);
    if (length > 256) return logOrThrow("Invalid key: too large", shouldThrow);
    char first = buffer.charAt(beginKey);
    if (!isLetterOrNumber(first)) {
      return logOrThrow("Invalid key: must start with a-z 0-9", shouldThrow);
    }

    for (int i = beginKey + 1; i < endKey; i++) {
      char c = buffer.charAt(i);

      if (c > LAST_VALID_KEY_CHAR || !VALID_KEY_CHARS[c]) {
        return logOrThrow("Invalid key: valid characters are: a-z 0-9 _ - * / @", shouldThrow);
      }
    }
    return true;
  }

  /**
   * https://www.w3.org/TR/trace-context-1 has some ambiguity about how to treat the value when
   * considering whitespace. https://github.com/w3c/trace-context/pull/411 clarifies initial spaces
   * are a part of the value. However, the value must end in at least one character in the range ' '
   * to '~', except ',' and '='. This implementation is based on the updated interpretation.
   *
   * <p>For example, pull 411 clarifies "" is not valid, and "   a" is a different value than " a".
   */
  boolean validateValue(CharSequence buffer, int beginValue, int endValue) {
    int length = endValue - beginValue;
    if (length == 0) return logOrThrow("Invalid value: empty", shouldThrow);
    if (length > 256) return logOrThrow("Invalid value: too large", shouldThrow);
    if (buffer.charAt(endValue - 1) == ' ') {
      return logOrThrow("Invalid value: must end in a non-space character", shouldThrow);
    }

    for (int i = beginValue; i < endValue; i++) {
      char c = buffer.charAt(i);

      if (c > LAST_VALID_VALUE_CHAR || !VALID_VALUE_CHARS[c]) {
        return logOrThrow("Invalid value: valid characters are: ' ' to '~', except ',' and '='",
          shouldThrow);
      }
    }
    return true;
  }
}
