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
package org.instancio.internal;

import org.instancio.assignment.AssignmentType;
import org.instancio.exception.InstancioException;
import org.instancio.generator.AfterGenerate;
import org.instancio.generator.Generator;
import org.instancio.generator.Hints;
import org.instancio.generator.hints.ArrayHint;
import org.instancio.generator.hints.CollectionHint;
import org.instancio.generator.hints.MapHint;
import org.instancio.internal.assigners.Assigner;
import org.instancio.internal.assigners.FieldAssigner;
import org.instancio.internal.assigners.MethodAssigner;
import org.instancio.internal.context.ModelContext;
import org.instancio.internal.generator.ContainerAddFunction;
import org.instancio.internal.generator.GeneratorResult;
import org.instancio.internal.generator.InternalContainerHint;
import org.instancio.internal.nodes.InternalNode;
import org.instancio.internal.nodes.NodeKind;
import org.instancio.internal.util.ArrayUtils;
import org.instancio.internal.util.CollectionUtils;
import org.instancio.internal.util.Constants;
import org.instancio.internal.util.Format;
import org.instancio.internal.util.ObjectUtils;
import org.instancio.internal.util.RecordUtils;
import org.instancio.internal.util.ReflectionUtils;
import org.instancio.internal.util.SystemProperties;
import org.instancio.settings.Keys;
import org.instancio.settings.Settings;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.instancio.internal.util.ExceptionHandler.conditionalFailOnError;
import static org.instancio.internal.util.ObjectUtils.defaultIfNull;

/**
 * Entry point for generating an object.
 * <p>
 * A new instance of this class should be created for each object generated via {@link #createRootObject()}.
 */
@SuppressWarnings({"PMD.GodClass", "PMD.CyclomaticComplexity", "PMD.ExcessiveImports"})
class InstancioEngine {
    private static final Logger LOG = LoggerFactory.getLogger(InstancioEngine.class);

    private final GeneratorFacade generatorFacade;
    private final ModelContext<?> context;
    private final InternalNode rootNode;
    private final CallbackHandler callbackHandler;
    private final List<GenerationListener> listeners;
    private final AfterGenerate defaultAfterGenerate;
    private final boolean overwriteExistingValues;
    private final Assigner assigner;

    InstancioEngine(InternalModel<?> model) {
        context = model.getModelContext();
        rootNode = model.getRootNode();
        callbackHandler = new CallbackHandler(context);
        generatorFacade = new GeneratorFacade(context);
        defaultAfterGenerate = context.getSettings().get(Keys.AFTER_GENERATE_HINT);
        overwriteExistingValues = context.getSettings().get(Keys.OVERWRITE_EXISTING_VALUES);
        listeners = Arrays.asList(callbackHandler, new GeneratedNullValueListener(context));
        assigner = getAssigner();
    }

    private Assigner getAssigner() {
        final Settings settings = context.getSettings();
        final AssignmentType defaultAssignment = settings.get(Keys.ASSIGNMENT_TYPE);

        // The system property is used for running the feature test suite using both assignment types
        final AssignmentType assignment = defaultIfNull(SystemProperties.getAssignmentType(), defaultAssignment);

        if (assignment == AssignmentType.FIELD) {
            return new FieldAssigner(settings);
        } else if (assignment == AssignmentType.METHOD) {
            return new MethodAssigner(settings);
        }
        throw new InstancioException("Invalid assignment type: " + assignment); // unreachable
    }

    @SuppressWarnings("unchecked")
    <T> T createRootObject() {
        return conditionalFailOnError(() -> {
            final GeneratorResult result = createObject(rootNode);
            final T rootResult = (T) result.getValue();
            callbackHandler.invokeCallbacks();
            context.reportWarnings();
            return rootResult;
        }).orElse(null);
    }

    private GeneratorResult createObject(final InternalNode node) {
        LOG.trace("Processing: {}", node);

        if (node.getChildren().isEmpty()) { // leaf - generate a value
            return generateValue(node);
        } else if (node.is(NodeKind.ARRAY)) {
            return generateArray(node);
        } else if (node.is(NodeKind.COLLECTION)) {
            return generateCollection(node);
        } else if (node.is(NodeKind.MAP)) {
            return generateMap(node);
        } else if (node.is(NodeKind.RECORD)) {
            return generateRecord(node);
        } else if (node.is(NodeKind.CONTAINER)) {
            return generateContainer(node);
        } else if (node.is(NodeKind.DEFAULT)) {
            return generatePojo(node);
        }
        // unreachable
        throw new InstancioException(String.format("Unhandled node kind '%s' for %s", node.getNodeKind(), node));
    }

    private GeneratorResult generateContainer(final InternalNode node) {
        GeneratorResult generatorResult = generateValue(node);

        if (generatorResult.isEmpty() || generatorResult.isIgnored()) {
            return generatorResult;
        }

        final InternalContainerHint hint = defaultIfNull(
                generatorResult.getHints().get(InternalContainerHint.class),
                InternalContainerHint.empty());

        final List<InternalNode> children = node.getChildren();

        // Creation delegated to the engine
        if (generatorResult.containsNull() && hint.createFunction() != null) {
            final Object[] args = new Object[children.size()];
            for (int j = 0; j < children.size(); j++) {
                final GeneratorResult childResult = createObject(children.get(j));
                args[j] = childResult.getValue();
            }
            final Object result = hint.createFunction().create(args);

            generatorResult = GeneratorResult.create(result, generatorResult.getHints());
        }

        final ContainerAddFunction<Object> addFunction = hint.addFunction();

        if (addFunction != null) {
            for (int i = 0; i < hint.generateEntries(); i++) {
                final Object[] args = new Object[children.size()];

                for (int j = 0; j < children.size(); j++) {
                    final GeneratorResult childResult = createObject(children.get(j));
                    args[j] = childResult.getValue();
                }

                addFunction.addTo(generatorResult.getValue(), args);
            }
        }

        if (hint.buildFunction() != null) {
            final Object builtContainer = hint.buildFunction().build(generatorResult.getValue());
            return GeneratorResult.create(builtContainer, generatorResult.getHints());
        }


        final Optional<GeneratorResult> spiResult = substituteResult(node, generatorResult);
        return spiResult.orElse(generatorResult);
    }

    /**
     * Replaces the original result with another type. For example, converts
     * a Map to ImmutableMap using {@code ImmutableMap.copyOf(Map)}).
     */
    private Optional<GeneratorResult> substituteResult(
            final InternalNode node,
            final GeneratorResult generatorResult) {

        return context.getContainerFactories()
                .stream()
                .map(it -> it.createFromOtherFunction(
                        node.getTargetClass(),
                        node.getChildren()
                                .stream()
                                .map(InternalNode::getTargetClass)
                                .collect(Collectors.toList())))
                .filter(Objects::nonNull)
                .findFirst()
                .map(fn -> fn.apply(generatorResult.getValue()))
                .map(replacedValue -> GeneratorResult.create(replacedValue, generatorResult.getHints()));
    }

    private GeneratorResult generatePojo(final InternalNode node) {
        final GeneratorResult nodeResult = generateValue(node);
        if (!nodeResult.containsNull()) {
            populateChildren(node.getChildren(), nodeResult);
        }
        return nodeResult;
    }

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
    private GeneratorResult generateMap(final InternalNode node) {
        final GeneratorResult generatorResult = generateValue(node);

        if (generatorResult.containsNull() || node.getChildren().size() < 2) {
            return generatorResult;
        }

        //noinspection unchecked
        final Map<Object, Object> map = (Map<Object, Object>) generatorResult.getValue();
        final InternalNode valueNode = node.getChildren().get(1);
        final InternalNode keyNode = node.getChildren().get(0);
        final Hints hints = generatorResult.getHints();

        // Populated objects that were created/added in the generator itself
        for (Map.Entry<Object, Object> entry : map.entrySet()) {
            final List<InternalNode> keyNodeChildren = keyNode.getChildren();
            final List<InternalNode> valueNodeChildren = valueNode.getChildren();

            populateChildren(keyNodeChildren, GeneratorResult.create(entry.getKey(), hints));
            populateChildren(valueNodeChildren, GeneratorResult.create(entry.getValue(), hints));
        }

        if (keyNode.is(NodeKind.IGNORED) || valueNode.is(NodeKind.IGNORED)) {
            return generatorResult;
        }

        final MapHint hint = defaultIfNull(hints.get(MapHint.class), MapHint.empty());
        final boolean nullableKey = hint.nullableMapKeys();
        final boolean nullableValue = hint.nullableMapValues();
        final Iterator<Object> withKeysIterator = hint.withKeys().iterator();

        int entriesToGenerate = hint.generateEntries();
        int failedAdditions = 0;

        while (entriesToGenerate > 0) {
            final GeneratorResult mapValueResult = createObject(valueNode, nullableValue);
            final Object mapValue = mapValueResult.getValue();

            final Object mapKey = withKeysIterator.hasNext()
                    ? withKeysIterator.next()
                    : createObject(keyNode, nullableKey).getValue();

            // Note: map key does not support emit() null
            if ((mapKey != null || nullableKey)
                    && (mapValue != null || nullableValue || mapValueResult.hasEmitNullHint())) {
                if (!map.containsKey(mapKey)) {
                    map.put(mapKey, mapValue);
                    entriesToGenerate--;
                } else {
                    failedAdditions++;
                }
            } else {
                failedAdditions++;
            }

            if (failedAdditions > Constants.FAILED_ADD_THRESHOLD) {
                conditionalFailOnError(() -> {
                    throw new InstancioException(
                            "Unable to populate " + Format.withoutPackage(node.getType())
                                    + " with requested number of entries: " + hint.generateEntries());
                });
                break;
            }
        }

        map.putAll(hint.withEntries());

        final Optional<GeneratorResult> spiResult = substituteResult(node, generatorResult);
        return spiResult.orElse(generatorResult);
    }

    @SuppressWarnings({
            "PMD.CognitiveComplexity",
            "PMD.NPathComplexity",
            "PMD.ForLoopVariableCount",
            "PMD.AvoidReassigningLoopVariables"})
    private GeneratorResult generateArray(final InternalNode node) {
        final GeneratorResult generatorResult = generateValue(node);

        if (generatorResult.containsNull() || node.getChildren().isEmpty()) {
            return generatorResult;
        }

        final Object arrayObj = generatorResult.getValue();
        final Hints hints = generatorResult.getHints();
        final ArrayHint hint = defaultIfNull(hints.get(ArrayHint.class), ArrayHint.empty());

        final List<?> withElements = hint.withElements();
        final int arrayLength = Array.getLength(arrayObj);
        final InternalNode elementNode = node.getOnlyChild();
        int lastIndex = 0;

        // Fill-in withElements first (if any)
        for (int i = 0, j = 0; i < arrayLength && j < withElements.size(); i++) {
            final Object elementValue = Array.get(arrayObj, i);

            // Populate objects created by user within the generator
            if (elementValue != null) {
                final List<InternalNode> elementNodeChildren = node.getOnlyChild().getChildren();
                populateChildren(elementNodeChildren, GeneratorResult.create(elementValue, hints));
            }

            // Current element may have been set by a custom generator.
            // withElements will always override null values in object arrays
            // and default values in primitive arrays.
            if (!ReflectionUtils.neitherNullNorPrimitiveWithDefaultValue(elementNode.getRawType(), elementValue)) {
                Array.set(arrayObj, i, withElements.get(j));
                j++;
            }

            lastIndex = i + 1;
        }

        final AfterGenerate action = hints.afterGenerate();
        final NodePopulationFilter filter = new ArrayElementNodePopulationFilter(context);
        final boolean isPrimitiveArray = elementNode.getRawType().isPrimitive();

        // If array elements fail to generate for any reason and null is returned,
        // terminate the loop once we reach the threshold to avoid an infinite loop.
        int failedAdditions = 0;

        for (int i = lastIndex; i < arrayLength; i++) {

            // Current value at index may have been set by a custom generator
            final Object currentValue = Array.get(arrayObj, i);

            // Populate objects created by user within the generator
            if (currentValue != null) {
                final List<InternalNode> elementNodeChildren = node.getOnlyChild().getChildren();
                populateChildren(elementNodeChildren, GeneratorResult.create(currentValue, hints));
            }

            if (filter.shouldSkip(elementNode, action, currentValue)) {
                continue;
            }

            final GeneratorResult elementResult = createObject(elementNode, hint.nullableElements());
            Object elementValue = elementResult.getValue();

            // If elements are not nullable, keep generating until a non-null
            while (elementValue == null
                    && !hint.nullableElements()
                    && !elementResult.hasEmitNullHint()
                    && !context.isIgnored(elementNode)
                    && failedAdditions < Constants.FAILED_ADD_THRESHOLD) {

                failedAdditions++;
                elementValue = createObject(elementNode, false).getValue();
            }

            // can't assign null values to primitive arrays
            if (!isPrimitiveArray || elementValue != null) {
                Array.set(arrayObj, i, elementValue);
            }
        }

        if (hint.shuffle()) {
            ArrayUtils.shuffle(arrayObj, context.getRandom());
        }
        return generatorResult;
    }

    @SuppressWarnings({"PMD.CognitiveComplexity", "PMD.NPathComplexity"})
    private GeneratorResult generateCollection(final InternalNode node) {
        final GeneratorResult generatorResult = generateValue(node);

        if (generatorResult.containsNull() || node.getChildren().isEmpty()) {
            return generatorResult;
        }

        //noinspection unchecked
        final Collection<Object> collection = (Collection<Object>) generatorResult.getValue();
        final InternalNode elementNode = node.getOnlyChild();
        final Hints hints = generatorResult.getHints();

        // Populated objects that were created/added in the generator itself
        for (Object element : collection) {
            final List<InternalNode> elementNodeChildren = elementNode.getChildren();
            populateChildren(elementNodeChildren, GeneratorResult.create(element, hints));
        }

        if (elementNode.is(NodeKind.IGNORED)) {
            return generatorResult;
        }

        final CollectionHint hint = defaultIfNull(hints.get(CollectionHint.class), CollectionHint.empty());
        final boolean nullableElements = hint.nullableElements();
        final boolean requireUnique = hint.unique();
        final Set<Object> generated = new HashSet<>();

        int elementsToGenerate = hint.generateElements();
        int failedAdditions = 0;

        while (elementsToGenerate > 0) {
            final GeneratorResult elementResult = createObject(elementNode, nullableElements);
            final Object elementValue = elementResult.getValue();

            if (elementValue != null || nullableElements || elementResult.hasEmitNullHint()) {

                boolean canAdd = !requireUnique || !generated.contains(elementValue);

                if (requireUnique) {
                    generated.add(elementValue);
                }

                if (canAdd && collection.add(elementValue)) {
                    elementsToGenerate--;

                } else {
                    // Special case for hash based collections.
                    // If requested size is impossible (e.g. a Set<Boolean> of size 5)
                    // then abandon populating it after the threshold is reached
                    failedAdditions++;
                }
            } else {
                // Avoid infinite loop (e.g. if value couldn't be generated)
                failedAdditions++;
            }

            if (failedAdditions > Constants.FAILED_ADD_THRESHOLD) {
                conditionalFailOnError(() -> {
                    throw new InstancioException(
                            "Unable to populate " + Format.withoutPackage(node.getType())
                                    + " with requested number of elements: " + hint.generateElements());
                });
                break;
            }
        }

        if (!hint.withElements().isEmpty()) {
            collection.addAll(hint.withElements());
        }
        if (hint.shuffle()) {
            CollectionUtils.shuffle(collection, context.getRandom());
        }

        final Optional<GeneratorResult> spiResult = substituteResult(node, generatorResult);
        return spiResult.orElse(generatorResult);
    }

    private GeneratorResult generateRecord(final InternalNode node) {
        // Handle the case where user supplies a generator for creating a record.
        Optional<Generator<?>> generator = context.getGenerator(node);
        if (!generator.isPresent()) {
            generator = generatorFacade.getGenerator(node);
        }

        if (generator.isPresent()) {
            return generateValue(node);
        }

        final List<InternalNode> children = node.getChildren();
        final Object[] args = new Object[children.size()];
        final Class<?>[] ctorArgs = RecordUtils.getComponentTypes(node.getTargetClass());

        // There may have been a cyclic node structure that was terminated with a null node.
        // If we don't have enough arguments for the record's constructor, simply return null.
        if (ctorArgs.length != args.length) {
            LOG.debug("Record {} has {} constructor arguments, but got {}",
                    node.getTargetClass(), ctorArgs.length, args.length);

            return GeneratorResult.nullResult();
        }

        for (int i = 0; i < args.length; i++) {
            final InternalNode child = children.get(i);
            final GeneratorResult result = createObject(child);

            args[i] = result.containsNull()
                    ? ObjectUtils.defaultValue(ctorArgs[i])
                    : result.getValue();
        }

        try {
            final Object obj = RecordUtils.instantiate(node.getTargetClass(), args);
            final GeneratorResult generatorResult = GeneratorResult.create(
                    obj, Hints.afterGenerate(defaultAfterGenerate));

            notifyListeners(node, generatorResult);
            return generatorResult;
        } catch (Exception ex) {
            conditionalFailOnError(() -> {
                throw new InstancioException("Failed creating a record for: " + node, ex);
            });
        }
        return GeneratorResult.emptyResult();
    }

    private void populateChildren(final List<InternalNode> children, final GeneratorResult generatorResult) {
        if (generatorResult.containsNull()) {
            return;
        }

        final Object value = generatorResult.getValue();
        final AfterGenerate action = generatorResult.getHints().afterGenerate();
        final NodePopulationFilter filter = new FieldNodePopulationFilter(context);

        for (final InternalNode child : children) {
            if (filter.shouldSkip(child, action, value)) {
                continue;
            }

            final GeneratorResult result = createObject(child);
            if (!result.isEmpty() && !result.isIgnored()) {
                final Object arg = result.getValue();
                final Field field = child.getField();

                if (overwriteExistingValues || !ReflectionUtils.hasNonNullOrNonDefaultPrimitiveValue(field, value)) {
                    assigner.assign(child, value, arg);
                }
            }
        }
    }

    @NotNull
    private GeneratorResult createObject(final InternalNode node, final boolean isNullable) {
        if (context.getRandom().diceRoll(isNullable)) {
            final GeneratorResult nullResult = GeneratorResult.nullResult();
            notifyListeners(node, nullResult);
            return nullResult;
        }
        return createObject(node);
    }

    private GeneratorResult generateValue(final InternalNode node) {
        final GeneratorResult generatorResult = generatorFacade.generateNodeValue(node);
        notifyListeners(node, generatorResult);
        return generatorResult;
    }

    private void notifyListeners(final InternalNode node, final GeneratorResult result) {
        for (GenerationListener listener : listeners) {
            listener.objectCreated(node, result);
        }
    }
}
