export async function requestJson(baseUrl, path, options = {}) {
    const headers = {
        Accept: "application/json",
        ...(options.body !== undefined ? { "Content-Type": "application/json" } : {}),
        ...(options.headers ?? {})
    };

    const response = await fetch(`${baseUrl}${path}`, {
        ...options,
        headers,
        body: options.body !== undefined && typeof options.body !== "string"
            ? JSON.stringify(options.body)
            : options.body
    });

    const text = await response.text();
    let body = null;

    if (text) {
        try {
            body = JSON.parse(text);
        } catch {
            body = text;
        }
    }

    return { response, body };
}

export function bearer(token) {
    return { Authorization: `Bearer ${token}` };
}
