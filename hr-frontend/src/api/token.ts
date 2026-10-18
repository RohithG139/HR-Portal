/**
 * Module-level Keycloak token accessor.
 *
 * The AuthProvider (AuthContext.tsx) registers a token getter after Keycloak
 * initialises. The API client reads it on every request to send the Bearer token.
 * This avoids prop-drilling or circular imports between the auth layer and the
 * fetch client.
 */

/** Token getter function registered by the auth layer. Default returns undefined (unauthenticated). */
let _getToken: () => string | undefined = () => undefined

/**
 * Called once by `AuthProvider` after Keycloak initialises and on every token refresh.
 *
 * @param fn  Returns the current access token string, or `undefined` if not authenticated.
 */
export function setTokenGetter(fn: () => string | undefined): void {
  _getToken = fn
}

/**
 * Returns the current Keycloak access token, or `undefined` when not authenticated.
 * Called by the API client on every outbound request.
 */
export function getToken(): string | undefined {
  return _getToken()
}
