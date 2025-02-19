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
package org.instancio.internal.util;

import org.instancio.exception.InstancioApiException;
import org.instancio.exception.InstancioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.ClearSystemProperty;
import org.junitpioneer.jupiter.SetSystemProperty;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.internal.util.ExceptionHandler.conditionalFailOnError;

@ClearSystemProperty(key = SystemProperties.FAIL_ON_ERROR)
class ExceptionHandlerTest {

    private static final String[] SUBMIT_BUG_REPORT_MESSAGE = {
            "Instancio encountered an error.",
            "Please submit a bug report including the stacktrace:",
            "https://github.com/instancio/instancio/issues"
    };

    private static final String EXCEPTION_MSG = "Test exception";
    private static final InstancioException INSTANCIO_EXCEPTION = new InstancioException(EXCEPTION_MSG);
    private static final InstancioException INSTANCIO_API_EXCEPTION = new InstancioApiException(EXCEPTION_MSG);
    private static final RuntimeException RUNTIME_EXCEPTION = new RuntimeException(EXCEPTION_MSG);
    private static final Throwable THROWABLE = new Throwable(EXCEPTION_MSG);

    @Nested
    class CausedByTest {
        @Test
        void getCausedIsNull() {
            assertThat(ExceptionHandler.getCausedBy(null)).isEmpty();
        }

        @Test
        void singleCauseWithNoMessage() {
            assertThat(ExceptionHandler.getCausedBy(new Throwable()))
                    .isEqualTo(String.format("%n => caused by: Throwable"));
        }

        @Test
        void multipleCausesWithMessages() {
            final InstancioException ex = new InstancioException("top",
                    new RuntimeException("cause",
                            new NullPointerException("root")));

            assertThat(ExceptionHandler.getCausedBy(ex))
                    .isEqualTo(String.format("%n" +
                            " => caused by: InstancioException: \"top\"%n" +
                            " => caused by: RuntimeException: \"cause\"%n" +
                            " => caused by: NullPointerException: \"root\""));
        }

        @Test
        void multipleCausesWithoutMessages() {
            final InstancioException ex = new InstancioException("top",
                    new RuntimeException(
                            new NullPointerException()));

            assertThat(ExceptionHandler.getCausedBy(ex))
                    .isEqualTo(String.format("%n" +
                            " => caused by: InstancioException: \"top\"%n" +
                            " => caused by: RuntimeException: \"java.lang.NullPointerException\"%n" +
                            " => caused by: NullPointerException"));
        }
    }

    @Test
    @SetSystemProperty(key = SystemProperties.FAIL_ON_ERROR, value = "true")
    void shouldNotThrowErrorIfSupplierReturnsNull() {
        assertThat(conditionalFailOnError(() -> null)).isEmpty();
    }

    @Test
    @DisplayName("Verify errors are suppressed by default when 'fail on error' property is not set")
    void doesNotFailOnErrorWithSupplier() {
        // no return value - just verify no exception is raised
        ExceptionHandler.conditionalFailOnError(functionThrowing(THROWABLE));
        ExceptionHandler.conditionalFailOnError(functionThrowing(INSTANCIO_EXCEPTION));

        assertThat(conditionalFailOnError(supplierThrowing(RUNTIME_EXCEPTION))).isEmpty();
        assertThat(conditionalFailOnError(supplierThrowing(INSTANCIO_EXCEPTION))).isEmpty();
    }

    @Test
    @DisplayName("Verify API exception is propagated even if 'fail on error' is disabled")
    @SetSystemProperty(key = SystemProperties.FAIL_ON_ERROR, value = "false")
    void propagatesApiExceptionWithVoidFunction() {
        final VoidFunction voidFunction = functionThrowing(INSTANCIO_API_EXCEPTION);
        assertThatThrownBy(() -> ExceptionHandler.conditionalFailOnError(voidFunction))
                .isSameAs(INSTANCIO_API_EXCEPTION);

        final Supplier<?> supplier = supplierThrowing(INSTANCIO_API_EXCEPTION);
        assertThatThrownBy(() -> conditionalFailOnError(supplier))
                .isSameAs(INSTANCIO_API_EXCEPTION);
    }

    @Test
    @DisplayName("Verify errors are wrapped in InstancioException and include a request to submit a bug report")
    @SetSystemProperty(key = SystemProperties.FAIL_ON_ERROR, value = "true")
    void failWithSupplier() {
        final Supplier<?> supplier = supplierThrowing(RUNTIME_EXCEPTION);
        assertThatThrownBy(() -> conditionalFailOnError(supplier))
                .isExactlyInstanceOf(InstancioException.class)
                .hasCause(RUNTIME_EXCEPTION)
                .hasMessageContainingAll(SUBMIT_BUG_REPORT_MESSAGE);

        final VoidFunction voidFunction = functionThrowing(THROWABLE);
        assertThatThrownBy(() -> conditionalFailOnError(voidFunction))
                .isExactlyInstanceOf(InstancioException.class)
                .hasCause(THROWABLE)
                .hasMessageContainingAll(SUBMIT_BUG_REPORT_MESSAGE);
    }

    private static VoidFunction functionThrowing(final Throwable t) {
        return () -> {
            throw t;
        };
    }

    private static Supplier<?> supplierThrowing(final RuntimeException ex) {
        return () -> {
            throw ex;
        };
    }
}
