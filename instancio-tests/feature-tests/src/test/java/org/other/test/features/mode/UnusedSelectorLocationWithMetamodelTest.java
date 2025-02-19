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
package org.other.test.features.mode;

import org.instancio.Instancio;
import org.instancio.InstancioApi;
import org.instancio.test.support.pojo.basic.StringHolder;
import org.instancio.test.support.pojo.person.Address_;
import org.instancio.test.support.pojo.person.Person_;
import org.instancio.test.support.pojo.person.Phone_;
import org.instancio.test.support.tags.Feature;
import org.instancio.test.support.tags.FeatureTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.fail;
import static org.instancio.Select.all;
import static org.instancio.test.support.UnusedSelectorsAssert.assertThrowsUnusedSelectorException;
import static org.instancio.test.support.UnusedSelectorsAssert.line;

@FeatureTag({Feature.MODE, Feature.SELECTOR})
class UnusedSelectorLocationWithMetamodelTest {

    @Test
    void unused() {
        final InstancioApi<StringHolder> api = Instancio.of(StringHolder.class)
                .set(Address_.city, "foo")
                .ignore(all(
                        Person_.age,
                        Address_.country))
                .supply(Phone_.number, () -> fail("not called"));

        assertThrowsUnusedSelectorException(api)
                .hasUnusedSelectorCount(4)
                .generatorSelector(Address_.city, line(getClass(), 39))
                .ignoreSelector(Person_.age, line(getClass(), 40))
                .ignoreSelector(Address_.country, line(getClass(), 40))
                .generatorSelector(Phone_.number, line(getClass(), 43));
    }
}