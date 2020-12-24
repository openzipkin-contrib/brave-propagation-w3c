# brave-propagation-w3c

[![Gitter chat](http://img.shields.io/badge/gitter-join%20chat%20%E2%86%92-brightgreen.svg)](https://gitter.im/openzipkin/zipkin)
[![Build Status](https://github.com/openzipkin-contrib/brave-propagation-w3c/workflows/test/badge.svg)](https://github.com/openzipkin-contrib/brave-propagation-w3c/actions?query=workflow%3Atest)

This is not usable, yet, as it doesn't fall-back to B3 on failure. Also, both specs and
implementations are unclear about use of "tracestate".

## Why tracestate is mostly unusable

It is questionable the value of this except treating `traceparent` the same as `b3` considering what
has happened in the tracing ecosystem. The primary custodians of w3c trace-context are also the
primary custodians of OpenTelemetry.

OpenTelemetry switched the primary trace format from `b3` to `traceparent` + `tracestate`. However,
they did so without fully implementing the specification, notably only implementing the `tracestate`
format, but not the processing model of it.

This causes an unresolvable conflict when anyone uses `tracestate` processing model in the same
system with a broken implementation (ex OpenTelemetry). Concretely, OpenTelemetry blindly copies the
`tracestate` while *also* mutating `traceparent`. It has no implementation to participate in the
processing model a library like this would implement.

The impact is that a receiver that implements `tracestate` processing cannot know how to prioritize
in this circumstance without manual coordination. For example, it could know from special deployment
knowledge that upstream is OpenTelemetry and invert the usual priority of `tracestate` over
`traceparent`. Without any special knowledge, following processing norms would actually break traces
as a stale upstream parent would be used as it wouldn't expect `traceparent` mutated + sending to
the same system unless that processor also mutating the corresponding entry in `tracestate`.

Hence, it is questionable if there's any sense at all using `tracestate`. Besides manual
coordination, another way to progress is to conform to the broken practice of treating `traceparent`
the same as the `b3` header and `tracestate` as arbitrary baggage. In other words, we would not
implement the processing model or look for entries. We would forward `tracestate`, but never use it.
That option is an inefficient implementation of `b3`, losing the main merit of `tracestate`:
durability of a reliable last position in a trace.

## Trace Context format
See [here](tracecontext/README.md) for instructions on how to use the [Trace Context](https://w3c.github.io/trace-context/) format.

https://github.com/w3c/trace-state-ids-registry/issues/2

## Artifacts
All artifacts publish to the group ID "io.zipkin.contrib.brave-propagation-w3c". We use a common
release version for all components.

### Library Snapshots
Snapshots are uploaded to [Sonatype](https://oss.sonatype.org/content/repositories/snapshots) after
commits to master.
