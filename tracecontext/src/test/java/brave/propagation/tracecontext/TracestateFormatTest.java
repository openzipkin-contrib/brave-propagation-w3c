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

import java.util.Arrays;
import org.assertj.core.api.AbstractBooleanAssert;
import org.assertj.core.api.AbstractThrowableAssert;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TracestateFormatTest {
  static final String FORTY_KEY_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789_-*/";
  static final String TWO_HUNDRED_FORTY_KEY_CHARS =
    FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS
      + FORTY_KEY_CHARS + FORTY_KEY_CHARS + FORTY_KEY_CHARS;

  static final String LONGEST_BASIC_KEY =
    TWO_HUNDRED_FORTY_KEY_CHARS + FORTY_KEY_CHARS.substring(0, 16);
  static final String LONGEST_VALUE = LONGEST_BASIC_KEY;

  static final String LONGEST_TENANT_KEY =
    "1" + TWO_HUNDRED_FORTY_KEY_CHARS + "@" + FORTY_KEY_CHARS.substring(0, 13);

  TracestateFormat tracestateFormat = new TracestateFormat("b3", true);

  // all these need log assertions
  @Test void validateKey_empty() {
    assertThatThrownByValidateKey("")
      .hasMessage("Invalid key: empty");
  }

  @Test void validateKey_tooLong() {
    char[] tooMany = new char[257];
    Arrays.fill(tooMany, 'a');
    assertThatThrownByValidateKey(new String(tooMany))
      .hasMessage("Invalid key: too large");
  }

  @Test void validateKey_specialCharacters() {
    for (char allowedSpecial : Arrays.asList('@', '_', '-', '*', '/')) {
      assertThatThrownByValidateKey(allowedSpecial + "")
        .hasMessage("Invalid key: must start with a-z 0-9");
      assertThatValidateKey("a" + allowedSpecial).isTrue();
      // Any number of special characters are allowed. ex "a*******", "a@@@@@@@"
      // https://github.com/tracecontext/trace-context/pull/386
      assertThatValidateKey("a" + allowedSpecial + allowedSpecial).isTrue();
      assertThatValidateKey("a" + allowedSpecial + "1").isTrue();
    }
  }

  @Test void validateKey_longest_basic() {
    assertThatValidateKey(LONGEST_BASIC_KEY).isTrue();
  }

  @Test void validateKey_longest_tenant() {
    assertThatValidateKey(LONGEST_TENANT_KEY).isTrue();
  }

  @Test void validateKey_shortest() {
    for (char n = '0'; n <= '9'; n++) {
      assertThatValidateKey(String.valueOf(n)).isTrue();
    }
    for (char l = 'a'; l <= 'z'; l++) {
      assertThatValidateKey(String.valueOf(l)).isTrue();
    }
  }

  @Test void validateKey_invalid_unicode() {
    assertThatThrownByValidateKey("a💩")
      .hasMessage("Invalid key: valid characters are: a-z 0-9 _ - * / @");
    assertThatThrownByValidateKey("💩a")
      .hasMessage("Invalid key: must start with a-z 0-9");
  }

  AbstractBooleanAssert<?> assertThatValidateKey(String key) {
    return assertThat(tracestateFormat.validateKey(key, 0, key.length()));
  }

  AbstractThrowableAssert<?, ? extends Throwable> assertThatThrownByValidateKey(String key) {
    return assertThatThrownBy(() -> tracestateFormat.validateKey(key, 0, key.length()))
      .isInstanceOf(IllegalArgumentException.class);
  }

  @Test void validateValue_empty() {
    assertThatThrownByValidateValue("")
      .hasMessage("Invalid value: empty");
  }

  @Test void validateValue_tooLong() {
    char[] tooMany = new char[257];
    Arrays.fill(tooMany, 'a');
    assertThatThrownByValidateValue(new String(tooMany))
      .hasMessage("Invalid value: too large");
  }

  @Test void validateValue_specialCharacters() {
    for (char allowedSpecial : Arrays.asList('@', '_', '~', '*', '/')) {
      assertThatValidateValue(allowedSpecial + "").isTrue();
      assertThatValidateValue("a" + allowedSpecial).isTrue();
      assertThatValidateValue("a" + allowedSpecial + allowedSpecial).isTrue();
      assertThatValidateValue("a" + allowedSpecial + "1").isTrue();
    }
  }

  @Test void validateValue_spaces() {
    assertThatThrownByValidateValue(" ")
      .hasMessage("Invalid value: must end in a non-space character");
    assertThatThrownByValidateValue("a ")
      .hasMessage("Invalid value: must end in a non-space character");
    assertThatValidateValue(" a").isTrue();
    assertThatValidateValue("a a").isTrue();
    assertThatValidateValue(" a a").isTrue();
  }

  @Test void validateValue_longest() {
    assertThatValidateValue(LONGEST_VALUE).isTrue();
  }

  @Test void validateValue_shortest() {
    for (char n = '0'; n <= '9'; n++) {
      assertThatValidateValue(String.valueOf(n)).isTrue();
    }
    for (char l = 'a'; l <= 'z'; l++) {
      assertThatValidateValue(String.valueOf(l)).isTrue();
    }
  }

  @Test void validateValue_invalid_unicode() {
    assertThatThrownByValidateValue("a💩")
      .hasMessage("Invalid value: valid characters are: ' ' to '~', except ',' and '='");
    assertThatThrownByValidateValue("💩a")
      .hasMessage("Invalid value: valid characters are: ' ' to '~', except ',' and '='");
  }

  AbstractBooleanAssert<?> assertThatValidateValue(String value) {
    return assertThat(tracestateFormat.validateValue(value, 0, value.length()));
  }

  AbstractThrowableAssert<?, ? extends Throwable> assertThatThrownByValidateValue(String value) {
    return assertThatThrownBy(() -> tracestateFormat.validateValue(value, 0, value.length()))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
