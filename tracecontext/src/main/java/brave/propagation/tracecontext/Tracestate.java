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
