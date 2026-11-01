package com.abhishek.resumeanalyzer.config;

import com.abhishek.resumeanalyzer.exceptions.LlmServiceException;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.schema.DataFetchingEnvironment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Turns the application's own business exceptions into GraphQL errors that keep
 * their message, so the UI can show "Username already exists" instead of the
 * default opaque "INTERNAL_ERROR for &lt;id&gt;".
 *
 * <p>Only exceptions the code throws deliberately are exposed: plain
 * {@link RuntimeException} (used across the services for validation),
 * {@link IllegalArgumentException}, {@link IllegalStateException} and
 * {@link LlmServiceException}. Framework exceptions such as data-access errors
 * keep the default handling so internal details never reach the client.</p>
 */
@Slf4j
@Component
public class GraphQlExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected GraphQLError resolveToSingleError(Throwable ex, DataFetchingEnvironment env) {
        ErrorType errorType = classify(ex);
        if (errorType == null) {
            return null;
        }
        log.debug("GraphQL {} on {}: {}", errorType, env.getField().getName(), ex.getMessage());
        return GraphqlErrorBuilder.newError(env)
                .errorType(errorType)
                .message(ex.getMessage())
                .build();
    }

    private ErrorType classify(Throwable ex) {
        if (ex instanceof LlmServiceException) {
            return ErrorType.INTERNAL_ERROR;
        }
        if (ex instanceof IllegalArgumentException || ex instanceof IllegalStateException) {
            return ErrorType.BAD_REQUEST;
        }
        if (ex.getClass() == RuntimeException.class && ex.getMessage() != null) {
            return ex.getMessage().startsWith("Access denied") ? ErrorType.FORBIDDEN : ErrorType.BAD_REQUEST;
        }
        return null;
    }
}
