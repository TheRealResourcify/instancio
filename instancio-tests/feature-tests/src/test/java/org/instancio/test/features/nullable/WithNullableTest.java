/*
 * Copyright 2022-2023 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.instancio.test.features.nullable;

import org.instancio.Instancio;
import org.instancio.test.support.pojo.basic.StringHolder;
import org.instancio.test.support.pojo.person.Person;
import org.instancio.test.support.tags.Feature;
import org.instancio.test.support.tags.FeatureTag;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.all;
import static org.instancio.Select.allStrings;
import static org.instancio.Select.field;

@FeatureTag({Feature.NULLABILITY, Feature.WITH_NULLABLE})
class WithNullableTest {

    private static final int SAMPLE_SIZE = 1000;

    @Test
    void withField() {
        final Stream<String> results = Stream.generate(() ->
                        Instancio.of(StringHolder.class)
                                .withNullable(allStrings())
                                .create())
                .limit(SAMPLE_SIZE)
                .map(StringHolder::getValue);

        assertThat(results).containsNull();
    }

    @Test
    void rootObject() {
        final Stream<String> results = Stream.generate(() ->
                        Instancio.of(String.class)
                                .withNullable(allStrings())
                                .create())
                .limit(SAMPLE_SIZE);

        assertThat(results).containsNull();
    }

    @Test
    void rootObjectViaStream() {
        final Stream<String> results = Instancio.of(String.class)
                .withNullable(allStrings())
                .stream()
                .limit(SAMPLE_SIZE);

        assertThat(results).containsNull();
    }

    @Test
    void arrayElements() {
        final String[] results = Instancio.of(String[].class)
                .withNullable(allStrings())
                .generate(all(String[].class), gen -> gen.array().length(SAMPLE_SIZE))
                .create();

        assertThat(results)
                .hasSize(SAMPLE_SIZE)
                .doesNotContainNull();
    }

    @Test
    void collectionElements() {
        final Set<String> results = Instancio.ofSet(String.class)
                .size(SAMPLE_SIZE)
                .withNullable(allStrings())
                .create();

        assertThat(results)
                .hasSize(SAMPLE_SIZE)
                .doesNotContainNull();
    }

    @Test
    void mapKeys() {
        final Map<String, UUID> results = Instancio.ofMap(String.class, UUID.class)
                .size(SAMPLE_SIZE)
                .withNullable(allStrings())
                .create();

        assertThat(results.keySet())
                .hasSize(SAMPLE_SIZE)
                .doesNotContainNull();
    }

    @Test
    void usingSelectorGroup() {
        final List<Person> results = Instancio.of(Person.class)
                .withNullable(all(
                        all(LocalDateTime.class),
                        field(Person::getName)))
                .stream()
                .limit(SAMPLE_SIZE)
                .collect(toList());

        assertThat(results.stream().map(Person::getName).collect(toSet())).containsNull();
        assertThat(results.stream().map(Person::getLastModified).collect(toSet())).containsNull();
    }
}
