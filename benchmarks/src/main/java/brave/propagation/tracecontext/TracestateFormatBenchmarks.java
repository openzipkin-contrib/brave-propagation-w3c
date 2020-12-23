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

import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * This mainly shows the impact of much slower approaches, such as regular expressions. However,
 * this is also used to help us evaluate efficiencies beyond that.
 */
@Measurement(iterations = 5, time = 1)
@Warmup(iterations = 10, time = 1)
@Fork(3)
@BenchmarkMode(Mode.Throughput) // simpler to interpret vs sample time
@OutputTimeUnit(TimeUnit.MICROSECONDS)
public class TracestateFormatBenchmarks {
  static final TracestateFormat tracestate = new TracestateFormat("b3", true);
  static final Pattern KEY_PATTERN = Pattern.compile("^[a-z0-9_\\-*/]{1,256}$");
  static final String VAL_CHAR =
    "[!\"#$%&'()*+\\-./0123456789:;<>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ\\[\\\\\\]^_`abcdefghijklmnopqrstuvwxyz{|}~]";
  static final Pattern VALUE_PATTERN =
    Pattern.compile("^( {0,255}" + VAL_CHAR + "|" + VAL_CHAR + VAL_CHAR + " {0,255})$");

  static final String TRACESTATE_KEY_RANGE = "*-/0123456789@_abcdefghijklmnopqrstuvwxyz";
  static final String TRACESTATE_VALUE_RANGE =
    " !\"#$%&'()*+-./0123456789:;<>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~";

  @Benchmark public boolean validateKey_range_brave() {
    return tracestate.validateKey(TRACESTATE_KEY_RANGE, 0, TRACESTATE_KEY_RANGE.length());
  }

  @Benchmark public boolean validateKey_range() {
    return KEY_PATTERN.matcher(TRACESTATE_KEY_RANGE).matches();
  }

  @Benchmark public boolean validateValue_range_brave() {
    return tracestate.validateValue(TRACESTATE_VALUE_RANGE, 0, TRACESTATE_VALUE_RANGE.length());
  }

  @Benchmark public boolean validateValue_range() {
    return VALUE_PATTERN.matcher(TRACESTATE_VALUE_RANGE).matches();
  }

  // Convenience main entry-point
  public static void main(String[] args) throws RunnerException {
    Options opt = new OptionsBuilder()
      .include(".*" + TracestateFormatBenchmarks.class.getSimpleName())
      .build();

    new Runner(opt).run();
  }
}
