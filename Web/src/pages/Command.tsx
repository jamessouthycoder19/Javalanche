import { useState, type FormEvent } from "react";
import { getToken, clearToken } from "../lib/auth";
import { useNavigate } from "react-router-dom";

const ipRegex = /^(?:\d{1,3}|x)(?:\.(?:\d{1,3}|x)){3}$/i;

function Command() {
  const navigate = useNavigate();
  const [ip, setIp] = useState("");
  const [cmd, setCmd] = useState("");
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setResult(null);

    if (!ip.trim()) {
      setError("IP address is required.");
      return;
    }
    if (!ipRegex.test(ip.trim())) {
      setError("IP must be IPv4 or use 'x' as a wildcard (e.g. 192.168.x.1).");
      return;
    }
    if (!cmd.trim()) {
      setError("Command is required.");
      return;
    }

    setLoading(true);
    try {
      const token = getToken();
      const res = await fetch("https://api.javalanche.net:8000/command", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify({ scope: ip.trim(), command: cmd })
      });

      if (!res.ok) {
        const text = await res.text().catch(() => `<unreadable body>`);
        if(res.status === 403 || res.status === 401) {
          clearToken();
          navigate("/login", { replace: true, state: { from: { pathname: "/command" } } });
        }
        throw new Error(`HTTP ${res.status} ${res.statusText}\n${text}`);
      }

      const ct = res.headers.get("content-type") ?? "";
      if (ct.includes("application/json")) {
        const json = await res.json();
        setResult(JSON.stringify(json, null, 2));
      } else {
        const text = await res.text();
        setResult(text);
      }
    } catch (err: any) {
      setError(String(err?.message ?? err));
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ padding: 20, maxWidth: 720 }}>
      <h2>Run Command</h2>
      <form onSubmit={handleSubmit} style={{ display: "grid", gap: 12 }}>
        <label>
          IP Address
          <input
            value={ip}
            onChange={e => setIp(e.target.value)}
            placeholder="192.168.1.1 or 192.168.x.1"
            style={{ width: "100%", padding: "8px 10px", marginTop: 6, borderRadius: 6, border: "1px solid #ccc" }}
            autoComplete="off"
          />
        </label>

        <label>
          Command
          <input
            value={cmd}
            onChange={e => setCmd(e.target.value)}
            placeholder="whoami"
            style={{ width: "100%", padding: "8px 10px", marginTop: 6, borderRadius: 6, border: "1px solid #ccc", fontFamily: "monospace" }}
            autoComplete="off"
          />
        </label>

        <div style={{ display: "flex", gap: 8 }}>
          <button type="submit" disabled={loading} style={{ background: "#c92a2a", color: "#fff", border: "none", padding: "8px 12px", borderRadius: 6 }}>
            {loading ? "Sending…" : "Send"}
          </button>
          <button
            type="button"
            onClick={() => { setIp(""); setCmd(""); setResult(null); setError(null); }}
            style={{ background: "transparent", border: "1px solid #ccc", padding: "8px 12px", borderRadius: 6 }}
          >
            Reset
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
        {result && <pre style={{ background: "#f6f8fa", padding: 12, borderRadius: 6, whiteSpace: "pre-wrap" }}>{result}</pre>}
      </form>
    </div>
  );
}

export default Command;