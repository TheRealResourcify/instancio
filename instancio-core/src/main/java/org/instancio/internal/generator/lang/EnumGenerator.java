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
package org.instancio.internal.generator.lang;

import org.instancio.Random;
import org.instancio.generator.AfterGenerate;
import org.instancio.generator.GeneratorContext;
import org.instancio.generator.Hints;
import org.instancio.generator.specs.EnumAsGeneratorSpec;
import org.instancio.generator.specs.EnumSpec;
import org.instancio.internal.ApiValidator;
import org.instancio.internal.generator.AbstractGenerator;
import org.instancio.internal.generator.InternalGeneratorHint;
import org.instancio.support.Global;

import java.util.Arrays;
import java.util.EnumSet;

public class EnumGenerator<E extends Enum<E>> extends AbstractGenerator<E>
        implements EnumSpec<E>, EnumAsGeneratorSpec<E> {

    private final Class<E> enumClass;
    private final EnumSet<E> values;
    private EnumSet<E> valuesWithExclusions;

    public EnumGenerator(final Class<E> enumClass) {
        this(Global.generatorContext(), enumClass);
    }

    public EnumGenerator(final GeneratorContext context, final Class<E> enumClass) {
        super(context);
        this.enumClass = ApiValidator.notNull(enumClass, "Enum class must not be null");
        this.values = EnumSet.allOf(enumClass);
        this.valuesWithExclusions = EnumSet.noneOf(enumClass);
    }

    @Override
    public String apiMethod() {
        return "enumOf()";
    }

    @Override
    @SafeVarargs
    public final EnumGenerator<E> excluding(final E... values) {
        ApiValidator.notNull(values, "Excluded values must not be null: excluding()");

        // Allow passing empty array, meaning 'no exclusions'
        if (values.length > 0) {
            final EnumSet<E> exclusions = EnumSet.copyOf(Arrays.asList(values));
            valuesWithExclusions = EnumSet.complementOf(exclusions);
        }
        return this;
    }

    @Override
    public EnumGenerator<E> nullable() {
        super.nullable();
        return this;
    }

    @Override
    protected E tryGenerateNonNull(final Random random) {
        if (values.isEmpty()) {
            return null;
        }
        return valuesWithExclusions.isEmpty()
                ? random.oneOf(values)
                : random.oneOf(valuesWithExclusions);
    }

    @Override
    public Hints hints() {
        return Hints.builder()
                .afterGenerate(AfterGenerate.APPLY_SELECTORS)
                .with(InternalGeneratorHint.builder()
                        .targetClass(enumClass)
                        .build())
                .build();
    }
}
