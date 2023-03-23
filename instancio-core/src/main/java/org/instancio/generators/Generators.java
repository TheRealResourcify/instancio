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
package org.instancio.generators;

import org.instancio.documentation.ExperimentalApi;
import org.instancio.generator.GeneratorContext;
import org.instancio.generator.specs.ArrayGeneratorSpec;
import org.instancio.generator.specs.BooleanAsGeneratorSpec;
import org.instancio.generator.specs.CharacterAsGeneratorSpec;
import org.instancio.generator.specs.CollectionGeneratorSpec;
import org.instancio.generator.specs.EmitGeneratorSpec;
import org.instancio.generator.specs.EnumAsGeneratorSpec;
import org.instancio.generator.specs.EnumSetGeneratorSpec;
import org.instancio.generator.specs.HashAsGeneratorSpec;
import org.instancio.generator.specs.MapGeneratorSpec;
import org.instancio.generator.specs.NumberAsGeneratorSpec;
import org.instancio.generator.specs.OneOfArrayGeneratorSpec;
import org.instancio.generator.specs.OneOfCollectionGeneratorSpec;
import org.instancio.generator.specs.StringGeneratorSpec;
import org.instancio.internal.generator.array.ArrayGenerator;
import org.instancio.internal.generator.array.OneOfArrayGenerator;
import org.instancio.internal.generator.domain.hash.HashGenerator;
import org.instancio.internal.generator.lang.BooleanGenerator;
import org.instancio.internal.generator.lang.ByteGenerator;
import org.instancio.internal.generator.lang.CharacterGenerator;
import org.instancio.internal.generator.lang.DoubleGenerator;
import org.instancio.internal.generator.lang.EnumGenerator;
import org.instancio.internal.generator.lang.FloatGenerator;
import org.instancio.internal.generator.lang.IntegerGenerator;
import org.instancio.internal.generator.lang.LongGenerator;
import org.instancio.internal.generator.lang.ShortGenerator;
import org.instancio.internal.generator.lang.StringGenerator;
import org.instancio.internal.generator.misc.EmitGenerator;
import org.instancio.internal.generator.util.CollectionGeneratorSpecImpl;
import org.instancio.internal.generator.util.EnumSetGenerator;
import org.instancio.internal.generator.util.MapGeneratorSpecImpl;
import org.instancio.internal.generator.util.OneOfCollectionGenerator;

import java.util.Collection;

/**
 * This class provides access to built-in generators.
 * <p>
 * It can be used to customise random values generated by built-in generators.
 * This includes numeric, collection sizes, string lengths, etc.
 *
 * @since 1.0.1
 */
@SuppressWarnings({"PMD.CouplingBetweenObjects", "PMD.ExcessiveImports"})
public class Generators {

    private final GeneratorContext context;

    public Generators(final GeneratorContext context) {
        this.context = context;
    }

    /**
     * Customises generated {@link String} values.
     *
     * @return customised generator
     */
    public StringGeneratorSpec string() {
        return new StringGenerator(context);
    }

    /**
     * Customises generated {@link boolean} values.
     *
     * @return customised generator
     * @since 2.0.0
     */
    public BooleanAsGeneratorSpec booleans() {
        return new BooleanGenerator(context);
    }

    /**
     * Customises generated {@link char} values.
     *
     * @return customised generator
     * @since 2.0.0
     */
    public CharacterAsGeneratorSpec chars() {
        return new CharacterGenerator(context);
    }

    /**
     * Customises generated {@link Byte} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Byte> bytes() {
        return new ByteGenerator(context);
    }

    /**
     * Customises generated {@link Short} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Short> shorts() {
        return new ShortGenerator(context);
    }

    /**
     * Customises generated {@link Integer} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Integer> ints() {
        return new IntegerGenerator(context);
    }

    /**
     * Customises generated {@link Long} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Long> longs() {
        return new LongGenerator(context);
    }

    /**
     * Customises generated {@link Float} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Float> floats() {
        return new FloatGenerator(context);
    }

    /**
     * Customises generated {@link Double} values.
     *
     * @return customised generator
     */
    public NumberAsGeneratorSpec<Double> doubles() {
        return new DoubleGenerator(context);
    }

    /**
     * Customises generated enum values.
     *
     * @param enumClass type of enum to generate
     * @param <E>       enum type
     * @return customised generator
     */
    public <E extends Enum<E>> EnumAsGeneratorSpec<E> enumOf(final Class<E> enumClass) {
        return new EnumGenerator<>(context, enumClass);
    }

    /**
     * Provides generators for {@code java.math} classes.
     *
     * @return built-in generators for {@code java.math} classes.
     */
    public MathGenerators math() {
        return new MathGenerators(context);
    }

    /**
     * Provides generators for {@code java.net} classes.
     *
     * @return built-in generators for {@code java.net} classes.
     * @since 2.3.0
     */
    public NetGenerators net() {
        return new NetGenerators(context);
    }

    /**
     * Picks a random value from the given choices.
     *
     * @param choices to choose from
     * @param <T>     element type
     * @return generator for making a selection
     */
    @SafeVarargs
    public final <T> OneOfArrayGeneratorSpec<T> oneOf(T... choices) {
        return new OneOfArrayGenerator<T>(context).oneOf(choices);
    }

    /**
     * Picks a random value from the given choices.
     *
     * @param choices to choose from
     * @param <T>     element type
     * @return generator for making a selection
     */
    public final <T> OneOfCollectionGeneratorSpec<T> oneOf(Collection<T> choices) {
        return new OneOfCollectionGenerator<T>(context).oneOf(choices);
    }

    /**
     * Customises generated arrays.
     *
     * @param <T> array component type
     * @return customised generator
     */
    public <T> ArrayGeneratorSpec<T> array() {
        return new ArrayGenerator<>(context);
    }

    /**
     * Customises generated collections.
     *
     * @param <T> element type
     * @return customised generator
     */
    public <T> CollectionGeneratorSpec<T> collection() {
        return new CollectionGeneratorSpecImpl<>(context);
    }

    /**
     * Customises generated {@link java.util.EnumSet}.
     *
     * @param enumClass contained by the generated enum set
     * @param <E>       enum type
     * @return customised generator
     * @since 2.0.0
     */
    public <E extends Enum<E>> EnumSetGeneratorSpec<E> enumSet(final Class<E> enumClass) {
        return new EnumSetGenerator<>(context, enumClass);
    }

    /**
     * Customises generated maps.
     *
     * @param <K> key type
     * @param <V> value type
     * @return customised generator
     */
    public <K, V> MapGeneratorSpec<K, V> map() {
        return new MapGeneratorSpecImpl<>(context);
    }

    /**
     * Emits provided items. This generator would typically be used
     * to generate collections containing different expected values.
     *
     * <p>The following example generates a list of 7 orders with different
     * statuses: 1 received, 1 shipped, 3 completed, and 2 cancelled.
     *
     * <pre>{@code
     *   List<Order> orders = Instancio.ofList(Order.class)
     *     .size(7)
     *     .generate(field(Order::getStatus), gen -> gen.emit()
     *              .items(OrderStatus.RECEIVED, OrderStatus.SHIPPED)
     *              .item(OrderStatus.COMPLETED, 3)
     *              .item(OrderStatus.CANCELLED, 2))
     *     .create();
     * }</pre>
     *
     * <p>If the selector target is a group, then each selector will
     * have its own copy of items, for example:
     *
     * <pre>{@code
     *   // Given
     *   class FooBar {
     *       String foo;
     *       String bar;
     *   }
     *
     *   TargetSelector fooAndBarFields = Select.all(
     *       field(FooBar::getFoo),
     *       field(FooBar::getBar));
     *
     *   // When
     *   FooBar result = Instancio.of(FooBar.class)
     *       .generate(fooAndBarFields, gen -> gen.emit().items("BAZ"))
     *       .create();
     *
     *   // Then => FooBar[foo=BAZ, bar=BAZ]
     * }</pre>
     *
     * @param <T> the type to emit
     * @return emitting generator
     * @since 2.12.0
     */
    @ExperimentalApi
    public <T> EmitGeneratorSpec<T> emit() {
        return new EmitGenerator<>(context);
    }

    /**
     * Provides access to atomic generators.
     *
     * @return built-in atomic generators
     */
    public AtomicGenerators atomic() {
        return new AtomicGenerators(context);
    }

    /**
     * Provides access to IO generators.
     *
     * @return built-in IO generators
     * @since 2.2.0
     */
    public IoGenerators io() {
        return new IoGenerators(context);
    }

    /**
     * Provides access to NIO generators.
     *
     * @return built-in NIO generators
     * @since 2.1.0
     */
    public NioGenerators nio() {
        return new NioGenerators(context);
    }

    /**
     * Provides access to temporal generators.
     *
     * @return built-in temporal generators
     */
    public TemporalGenerators temporal() {
        return new TemporalGenerators(context);
    }

    /**
     * Provides access to text generators.
     *
     * @return built-in text generators
     */
    public TextGenerators text() {
        return new TextGenerators(context);
    }

    /**
     * Provides access to identifier generators.
     *
     * @return built-in id generators
     * @since 2.11.0
     */
    public IdGenerators id() {
        return new IdGenerators(context);
    }

    /**
     * Generates various types of hashes.
     *
     * @return API builder reference
     * @since 2.11.0
     */
    public HashAsGeneratorSpec hash() {
        return new HashGenerator(context);
    }

    /**
     * Provides access to finance-related generators.
     *
     * @return built-in finance-related generators
     * @since 2.11.0s
     */
    public FinanceGenerators finance() {
        return new FinanceGenerators(context);
    }
}
