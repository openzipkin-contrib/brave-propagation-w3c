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

import brave.propagation.Propagation.Getter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContextOrSamplingFlags;
import java.util.Arrays;

import static brave.propagation.B3SingleFormat.parseB3SingleFormat;
import static brave.propagation.tracecontext.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.tracecontext.TraceContextPropagation.TRACESTATE;
import static brave.propagation.tracecontext.internal.CharSequences.withoutSubSequence;

final class TraceContextExtractor<R> implements Extractor<R> {
  final Getter<R, String> getter;
  final TraceparentFormat traceparentFormat;
  final TracestateFormat tracestateFormat;
  final String tracestateKey;

  TraceContextExtractor(TraceContextPropagation propagation, Getter<R, String> getter) {
    this.getter = getter;
    this.traceparentFormat = propagation.traceparentFormat;
    this.tracestateFormat = propagation.tracestateFormat;
    this.tracestateKey = propagation.tracestateKey;
  }

  @Override public TraceContextOrSamplingFlags extract(R request) {
    if (request == null) throw new NullPointerException("request == null");

    // Below implies both headers must be present or all is invalid
    //
    // MUST propagate the traceparent and tracestate headers
    // https://www.w3.org/TR/trace-context/#design-overview
    // If a tracestate header is received without an accompanying traceparent header, it is invalid and MUST be discarded.
    // https://www.w3.org/TR/trace-context/#no-traceparent-received
    String traceparentString = getter.get(request, TRACEPARENT);
    if (traceparentString == null) return TraceContextOrSamplingFlags.EMPTY;
    String tracestateString = getter.get(request, TRACESTATE);
    if (tracestateString == null) return TraceContextOrSamplingFlags.EMPTY;

    // Below implies traceparent must be valid or all is invalid.
    //
    // If the vendor failed to parse traceparent, it MUST NOT attempt to parse tracestate.
    // Note that the opposite is not true: failure to parse tracestate MUST NOT affect the parsing of traceparent.
    // https://www.w3.org/TR/trace-context/#tracestate-header
    TraceContext maybeUpstream = traceparentFormat.parse(traceparentString);
    if (maybeUpstream == null) return TraceContextOrSamplingFlags.EMPTY;

    // The spec is vague about tracestate handling. We are allowed to parse, ignore or toss it.
    // This implementation chooses to toss a malformed tracestate header.
    //
    // The vendor MAY validate the tracestate header.
    // If the tracestate header cannot be parsed the vendor MAY discard the entire header.
    // Invalid tracestate entries MAY also be discarded.
    // https://www.w3.org/TR/trace-context/#a-traceparent-is-received
    // failure to parse tracestate MUST NOT affect the parsing of traceparent.
    // https://www.w3.org/TR/trace-context/#tracestate-header
    int[] indices = new int[6];
    Arrays.fill(indices, -1);
    if (!tracestateFormat.parseInto(tracestateString, indices)) {
      return TraceContextOrSamplingFlags.EMPTY; // malformed per tracestate spec
    }

    // At this point, we know that traceparent and tracestate are valid. We need to choose what, if
    // anything to read. The priority is tracestate for the primary trace context, if it includes
    // our entry. Otherwise, we will try the same trace ID from traceparent.

    // First check if our entry is inside tracestate. If so, we ignore traceparent when well-formed.
    if (indices[1] != -1) {
      TraceContextOrSamplingFlags fromB3Entry =
        parseB3SingleFormat(tracestateString, indices[3], indices[4]);
      if (fromB3Entry == null) return TraceContextOrSamplingFlags.EMPTY; // malformed per B3 spec
      Tracestate tracestate = Tracestate.create(withoutB3(tracestateString, indices));
      return fromB3Entry.toBuilder().addExtra(tracestate).build();
    }

    // Finally, we have a valid traceparent and a possibly empty tracestate lacking our entry.
    // We trust the traceparent as a part of our system and carry forward tracestate we received.
    return TraceContextOrSamplingFlags.newBuilder(maybeUpstream)
      .addExtra(Tracestate.create(tracestateString))
      .build();
  }

  static CharSequence withoutB3(String tracestateString, int[] indices) {
    if (indices[0] == -1 && indices[5] == -1) return "";

    int firstIndexToSkip = indices[0] != -1 ? tracestateString.indexOf(',', indices[0]) : 0;
    if (indices[4] != tracestateString.length() && firstIndexToSkip != 0) firstIndexToSkip++;
    return withoutSubSequence(tracestateString, firstIndexToSkip,
      indices[5] != -1 ? indices[5] : indices[4]);
  }
}
