/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.internal.Nullable;

final class Tracestate {
  static final Tracestate EMPTY = new Tracestate(null);

  @Nullable final CharSequence otherState;

  Tracestate(CharSequence otherState) {
    this.otherState = otherState;
  }

  static Tracestate create(CharSequence otherState) {
    return otherState != null && otherState.length() > 0
      ? new Tracestate(otherState)
      : Tracestate.EMPTY;
  }

  String stateString(String thisKey, String thisValue) {
    int length = thisKey.length() + 1 + thisValue.length();
    if (otherState != null) length += 1 + otherState.length();

    // TODO: SHOULD on 512 char limit https://tracecontext.github.io/trace-context/#tracestate-limits
    StringBuilder result = new StringBuilder(length);
    result.append(thisKey).append('=').append(thisValue);
    if (otherState != null) result.append(',').append(otherState);
    return result.toString();
  }

  @Override public String toString() {
    if (otherState == null) return "Tracestate{}";
    return "Tracestate{" + otherState + "}";
  }
}
