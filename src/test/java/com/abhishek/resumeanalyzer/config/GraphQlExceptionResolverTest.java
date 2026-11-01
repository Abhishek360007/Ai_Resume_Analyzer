package com.abhishek.resumeanalyzer.config;

import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import graphql.GraphQLError;
import graphql.execution.ExecutionStepInfo;
import graphql.execution.ResultPath;
import graphql.language.Field;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingEnvironmentImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.graphql.execution.ErrorType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GraphQlExceptionResolver}: business errors keep their message,
 * framework errors stay hidden behind the default handling.
 */
class GraphQlExceptionResolverTest {

    private final GraphQlExceptionResolver resolver = new GraphQlExceptionResolver();

    @Test
    @DisplayName("Validation RuntimeException keeps its message as BAD_REQUEST")
    void exposesValidationMessages() {
        GraphQLError error = resolve(new RuntimeException("Skill already exists: Java"));

        assertThat(error.getMessage()).isEqualTo("Skill already exists: Java");
        assertThat(error.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
    }

    @Test
    @DisplayName("Access-denied messages are classified as FORBIDDEN")
    void classifiesAccessDenied() {
        GraphQLError error = resolve(new RuntimeException("Access denied: Can only delete your own feedback"));

        assertThat(error.getErrorType()).isEqualTo(ErrorType.FORBIDDEN);
    }

    @Test
    @DisplayName("IllegalArgumentException and AI errors keep their message")
    void exposesArgumentAndAiErrors() {
        assertThat(resolve(new IllegalArgumentException("Job requirement not found: 42")).getMessage())
                .isEqualTo("Job requirement not found: 42");
        assertThat(resolve(new LlmServiceException("AI matching is unavailable right now")).getMessage())
                .isEqualTo("AI matching is unavailable right now");
    }

    @Test
    @DisplayName("Framework exceptions are left to the default handler so internals stay hidden")
    void hidesFrameworkExceptions() {
        assertThat(resolve(new DataIntegrityViolationException("duplicate key value violates unique constraint"))).isNull();
        assertThat(resolve(new NullPointerException("boom"))).isNull();
    }

    private GraphQLError resolve(Throwable ex) {
        DataFetchingEnvironment env = DataFetchingEnvironmentImpl.newDataFetchingEnvironment()
                .mergedField(graphql.execution.MergedField.newMergedField(new Field("createSkill")).build())
                .executionStepInfo(ExecutionStepInfo.newExecutionStepInfo()
                        .type(graphql.Scalars.GraphQLString)
                        .path(ResultPath.rootPath())
                        .build())
                .build();
        return resolver.resolveToSingleError(ex, env);
    }
}
