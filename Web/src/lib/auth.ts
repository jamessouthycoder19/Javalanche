export function getToken(): string | null {
    return localStorage.getItem("authToken");
}

export function setToken(token: string, ttlSeconds?: number) {
    localStorage.setItem("authToken", token);
    if (ttlSeconds && ttlSeconds > 0) {
        const expiry = Date.now() + ttlSeconds * 1000;
        localStorage.setItem("authTokenExpiry", String(expiry));
    } else {
        localStorage.removeItem("authTokenExpiry");
    }
}

export function clearToken() {
    localStorage.removeItem("authToken");
    localStorage.removeItem("authTokenExpiry");
}

/**
 * Request a bearer token from the backend using Basic auth.
 * Simple, browser-style fetch similar to other files (e.g. shell.tsx).
 */
export async function requestToken(username: string, password: string): Promise<{ bearer: string; ttl: number }> {
    const credentials = btoa(`${username}:${password}`);
    const url = "https://api.javalanche.net:8000/auth";

    const res = await fetch(url, {
        method: "GET",
        headers: {
            "Authorization": `Basic ${credentials}`,
            "Accept": "application/json"
        }
    });

    // Try to parse JSON if possible
    const body = await res.json().catch(() => ({} as any));

    if (!res.ok) {
        throw new Error(`Auth failed: HTTP ${res.status} ${res.statusText}`);
    }

    if (!body || !body.bearer) {
        throw new Error("Malformed auth response: missing 'bearer' field");
    }

    return { bearer: body.bearer, ttl: Number(body.ttl) || 0 };
}

/**
 * Validate token using local expiry only (keeps calls lightweight and consistent with other pages).
 */
export async function isTokenValid(token?: string | null): Promise<boolean> {
    const t = token ?? getToken();
    if (!t) return false;

    const expiry = localStorage.getItem("authTokenExpiry");
    if (expiry && Date.now() > Number(expiry)) return false;

    return true;
}