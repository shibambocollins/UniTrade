// One place for every call to the backend.
// Locally VITE_API_URL is unset, so paths like "/api/health" go to the Vite proxy.
const BASE_URL = import.meta.env.VITE_API_URL ?? '';

/** Error thrown for any failed request. `message` is always safe to show to the user. */
export class ApiError extends Error {
  constructor(status, message, fieldErrors = null) {
    super(message);
    this.status = status;
    this.fieldErrors = fieldErrors; // { fieldName: "problem" } for 400 validation errors
  }
}

const FRIENDLY_MESSAGES = {
  400: 'Please check the highlighted fields.',
  401: 'Please log in to continue.',
  403: 'You are not allowed to do that.',
  404: 'We could not find what you were looking for.',
  409: 'That action conflicts with the current state. Please refresh and try again.',
};

export async function apiRequest(path, { method = 'GET', body, token } = {}) {
  const headers = {};
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response;
  try {
    response = await fetch(BASE_URL + path, {
      method,
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });
  } catch {
    // fetch only throws when there is no response at all (server down, no network)
    throw new ApiError(0, 'Cannot reach the UniTrade server. Check your connection and try again.');
  }

  // The API always answers with JSON (or 204 No Content with no body)
  const isJson = (response.headers.get('Content-Type') || '').includes('application/json');
  const data = isJson ? await response.json().catch(() => null) : null;

  // Our API answers every error with JSON. A 5xx that is not JSON comes from something in between
  // (the Vite dev proxy or the host) when the API itself is down or asleep (DEF-11).
  if (!response.ok && !isJson && response.status >= 500) {
    throw new ApiError(0, 'Cannot reach the UniTrade server. Check your connection and try again.');
  }
  if (!response.ok) {
    const message =
      data?.message || FRIENDLY_MESSAGES[response.status] || 'Something went wrong. Please try again.';
    throw new ApiError(response.status, message, data?.fieldErrors ?? null);
  }
  // A "successful" answer that is not JSON did not come from the UniTrade API
  // (e.g. a hosting page when VITE_API_URL is missing or wrong), so treat it as an error.
  if (response.status !== 204 && !isJson) {
    throw new ApiError(0, 'The UniTrade server sent an unexpected response. Please try again later.');
  }
  return data;
}
