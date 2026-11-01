/**
 * Extract a human-readable message from an error thrown by graphql-request or axios.
 *
 * graphql-request builds `error.message` as the server message followed by a JSON dump
 * of the whole request and response, and axios only says "Request failed with status
 * code 401", so neither is fit to show to users. The server's own message lives in
 * `response.errors[0].message` (GraphQL) or `response.data.error` (REST).
 */
export const getErrorMessage = (error: unknown, fallback = 'Something went wrong. Please try again.'): string => {
  const err = error as {
    message?: unknown
    response?: { errors?: { message?: unknown }[]; data?: { error?: unknown; message?: unknown } }
  } | null

  const graphqlMessage = err?.response?.errors?.[0]?.message
  if (typeof graphqlMessage === 'string' && graphqlMessage.trim()) {
    // Spring's generic "INTERNAL_ERROR for <id>" carries no useful information
    return graphqlMessage.startsWith('INTERNAL_ERROR for ') ? fallback : graphqlMessage
  }

  const restMessage = err?.response?.data?.error ?? err?.response?.data?.message
  if (typeof restMessage === 'string' && restMessage.trim()) {
    return restMessage
  }

  if (error instanceof Error && error.message && !error.message.startsWith('Request failed with status code')) {
    return error.message
  }

  return fallback
}
