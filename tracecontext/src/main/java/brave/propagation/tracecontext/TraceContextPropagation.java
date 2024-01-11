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

import brave.propagation.Propagation;
import brave.propagation.TraceContext;
import brave.propagation.TraceContext.Extractor;
import brave.propagation.TraceContext.Injector;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static java.util.Arrays.asList;

public final class TraceContextPropagation implements Propagation<String> {
  static final String TRACEPARENT = "traceparent", TRACESTATE = "tracestate";
  public static final Propagation.Factory FACTORY = new Factory(newFactoryBuilder());
  static final Propagation<String> INSTANCE = FACTORY.get();

  public static Propagation<String> get() {
    return INSTANCE;
  }

  public static FactoryBuilder newFactoryBuilder() {
    return new FactoryBuilder();
  }

  public static final class FactoryBuilder {
    static final TracestateFormat THROWING_VALIDATOR = new TracestateFormat("b3", true);
    String tracestateKey = "b3";

    FactoryBuilder() {
    }

    /**
     * The key to use inside the {@code tracestate} value. Defaults to "b3".
     *
     * @throws IllegalArgumentException if the key doesn't conform to ABNF rules defined by the
     *                                  <href="<a href="https://www.w3.org/TR/trace-context-1/#key">...</a>">trace-context
     *                                  specification</href>.
     */
    public FactoryBuilder tracestateKey(String key) {
      if (key == null) throw new NullPointerException("key == null");
      THROWING_VALIDATOR.validateKey(key, 0, key.length());
      this.tracestateKey = key;
      return this;
    }

    public Propagation.Factory build() {
      Factory result = new Factory(this);
      if (result.equals(FACTORY)) return FACTORY;
      return result;
    }
  }

  static final class Factory extends Propagation.Factory {
    final String tracestateKey;

    Factory(FactoryBuilder builder) {
      this.tracestateKey = builder.tracestateKey;
    }

    @Override public Propagation<String> get() {
      return new TraceContextPropagation(this);
    }

    @Override public boolean supportsJoin() {
      return true; // B3 (in tracestate) allows join
    }

    @Override public boolean requires128BitTraceId() {
      return false; // B3  (in tracestate) doesn't require 128-bit
    }

    @Override public TraceContext decorate(TraceContext context) {
      // we don't allow state changes yet, so nothing to decorate
      return context;
    }

    @Override public boolean equals(Object o) {
      if (o == this) return true;
      if (!(o instanceof Factory)) return false;

      Factory that = (Factory) o;
      return tracestateKey.equals(that.tracestateKey);
    }
  }

  final String tracestateKey;
  final List<String> keys = Collections.unmodifiableList(asList(TRACEPARENT, TRACESTATE));
  final TraceparentFormat traceparentFormat = TraceparentFormat.get();
  final TracestateFormat tracestateFormat = TracestateFormat.get();

  TraceContextPropagation(Factory factory) {
    this.tracestateKey = factory.tracestateKey;
  }

  @Override public List<String> keys() {
    return keys;
  }

  @Override public <R> Injector<R> injector(Setter<R, String> setter) {
    if (setter == null) throw new NullPointerException("setter == null");
    return new TraceContextInjector<>(this, setter);
  }

  @Override public <R> Extractor<R> extractor(Getter<R, String> getter) {
    if (getter == null) throw new NullPointerException("getter == null");
    return new TraceContextExtractor<>(this, getter);
  }

  static boolean logOrThrow(String msg, boolean shouldThrow) {
    if (shouldThrow) throw new IllegalArgumentException(msg);
    Logger logger = LoggerHolder.logger();
    if (!logger.isLoggable(Level.FINE)) return false; // fine level to not fill logs
    logger.log(Level.FINE, msg);
    return false;
  }

  static boolean logOrThrow(String msg, String param1, boolean shouldThrow) {
    if (shouldThrow) throw new IllegalArgumentException(msg.replace("{0}", param1));
    Logger logger = LoggerHolder.logger();
    if (!logger.isLoggable(Level.FINE)) return false; // fine level to not fill logs
    LogRecord lr = new LogRecord(Level.FINE, msg);
    Object[] params = {param1};
    lr.setParameters(params);
    logger.log(lr);
    return false;
  }

  // Use nested class to ensure logger isn't initialized unless it is accessed once.
  static final class LoggerHolder { // visible for testing
    static final Logger LOG = Logger.getLogger(TraceContextPropagation.class.getName());

    static Logger logger() {
      return LOG;
    }
  }
}
