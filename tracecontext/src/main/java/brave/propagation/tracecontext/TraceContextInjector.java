/*
 * Copyright The OpenZipkin Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package brave.propagation.tracecontext;

import brave.propagation.B3SingleFormat;
import brave.propagation.Propagation.Setter;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Injector;

import static brave.propagation.tracecontext.TraceContextPropagation.TRACEPARENT;
import static brave.propagation.tracecontext.TraceContextPropagation.TRACESTATE;

final class TraceContextInjector<R> implements Injector<R> {
  final Setter<R, String> setter;
  final TraceparentFormat traceparentFormat;
  final String tracestateKey;

  TraceContextInjector(TraceContextPropagation propagation, Setter<R, String> setter) {
    this.setter = setter;
    this.traceparentFormat = propagation.traceparentFormat;
    this.tracestateKey = propagation.tracestateKey;
  }

  @Override public void inject(TraceContext context, R request) {
    setter.put(request, TRACEPARENT, traceparentFormat.write(context));
    Tracestate tracestate = context.findExtra(Tracestate.class);

    // TODO: char buffer to reduce allocations in tracestate.stateString
    String b3 = B3SingleFormat.writeB3SingleFormat(context);
    if (tracestate != null) {
      setter.put(request, TRACESTATE, tracestate.stateString(tracestateKey, b3));
    } else {
      setter.put(request, TRACESTATE, tracestateKey + "=" + b3);
    }
  }
}
