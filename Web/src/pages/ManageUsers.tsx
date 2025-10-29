import { useState, type FormEvent } from "react";
import { getToken } from "../lib/auth";
import { useNavigate } from "react-router";

export default function ManageUsers() {
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [loading, setLoading] = useState(false);
  const [info, setInfo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function sendUserRequest(payload: Record<string, any>) {
    setLoading(true);
    setInfo(null);
    setError(null);
    try {
      const token = getToken();
      const res = await fetch("https://api.javalanche.net:8000/user", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(payload)
      });

      const ct = res.headers.get("content-type") ?? "";
      const body = ct.includes("application/json") ? await res.json().catch(() => null) : await res.text().catch(() => null);

      if (!res.ok) {
        setError(typeof body === "string" ? body : JSON.stringify(body ?? { status: res.status, text: res.statusText }));
        return;
      }

      if (body && typeof body === "object" && body.output) setInfo(String(body.output));
      else setInfo("success");
    } catch (err: any) {
      setError(String(err?.message ?? err));
    } finally {
      setLoading(false);
    }
  }

  async function handleAddSet(e: FormEvent) {
    e.preventDefault();
    if (!username.trim()) { setError("username required"); return; }
    if (!password.trim()) { setError("password required for add/set"); return; }
    await sendUserRequest({ type: "add", username: username.trim(), password: password });
  }

  async function handleDisable(e?: FormEvent) {
    if (e) e.preventDefault();
    if (!username.trim()) { setError("username required"); return; }
    await sendUserRequest({ type: "disable", username: username.trim() });
  }

  return (
    <div style={{ padding: 20, maxWidth: 720 }}>
      <h2>Manage Users</h2>

      <form onSubmit={handleAddSet} style={{ display: "grid", gap: 12 }}>
        <label>
          Username
          <input
            value={username}
            onChange={e => setUsername(e.target.value)}
            placeholder="bob"
            style={{ width: "100%", padding: "8px 10px", marginTop: 6, borderRadius: 6, border: "1px solid #ccc" }}
            autoComplete="off"
          />
        </label>

        <label>
          Password (for add / set)
          <input
            value={password}
            onChange={e => setPassword(e.target.value)}
            placeholder="password"
            style={{ width: "100%", padding: "8px 10px", marginTop: 6, borderRadius: 6, border: "1px solid #ccc", fontFamily: "monospace" }}
            autoComplete="off"
            type="password"
          />
        </label>

        <div style={{ display: "flex", gap: 8 }}>
          <button
            type="submit"
            disabled={loading}
            style={{ background: "#c92a2a", color: "#fff", border: "none", padding: "8px 12px", borderRadius: 6 }}
          >
            {loading ? "Working…" : "Add / Set Password"}
          </button>

          <button
            type="button"
            onClick={handleDisable}
            disabled={loading}
            style={{ background: "transparent", border: "1px solid #ccc", padding: "8px 12px", borderRadius: 6 }}
          >
            {loading ? "Working…" : "Disable / Delete User"}
          </button>

          <button
            type="button"
            onClick={() => navigate("/")}
            style={{ background: "transparent", border: "1px solid #ccc", padding: "8px 12px", borderRadius: 6 }}
          >
            Home
          </button>
        </div>

        {error && <pre style={{ color: "crimson", whiteSpace: "pre-wrap" }}>{error}</pre>}
        {info && <pre style={{ background: "#f6f8fa", padding: 12, borderRadius: 6, whiteSpace: "pre-wrap" }}>{info}</pre>}
      </form>
    </div>
  );
}