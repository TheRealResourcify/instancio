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
package org.instancio.generator.specs;

/**
 * Spec for generating {@link Short} values.
 *
 * @since 2.6.0
 */
public interface ShortSpec extends NumberSpec<Short> {

    @Override
    ShortSpec min(Short min);

    @Override
    ShortSpec max(Short max);

    @Override
    ShortSpec range(Short min, Short max);

    @Override
    ShortSpec nullable();
}
