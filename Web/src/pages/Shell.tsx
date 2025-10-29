// filepath: c:\Users\James\RedTeam\Javalanche\Web\src\pages\Shell.tsx
import React, { useEffect, useRef, useState, type FormEvent } from "react";
import { useSearchParams, useNavigate } from "react-router-dom";
import { clearToken, getToken } from "../lib/auth";

const ipRegex = /^\d{1,3}(\.\d{1,3}){3}$/;

function sortIps(a: string, b: string) {
  const aa = a.split(".").map(n => Number(n));
  const bb = b.split(".").map(n => Number(n));
  for (let i = 0; i < 4; i++) {
    if ((aa[i] ?? 0) < (bb[i] ?? 0)) return -1;
    if ((aa[i] ?? 0) > (bb[i] ?? 0)) return 1;
  }
  return 0;
}

export default function Shell() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const client = searchParams.get("client");

  // list view state
  const [clients, setClients] = useState<{ ip: string; active: boolean }[]>([]);
  const [loadingClients, setLoadingClients] = useState(false);
  const [clientsError, setClientsError] = useState<string | null>(null);

  // shell state
  const [historyLines, setHistoryLines] = useState<string[]>([]);
  const [cmd, setCmd] = useState("");
  const [sending, setSending] = useState(false);
  const outputRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (client) return;
    (async () => {
      setLoadingClients(true);
      setClientsError(null);
      try {
        const token = getToken();
        const res = await fetch("https://api.javalanche.net:8000/status", {
          method: "GET",
          headers: token ? { Authorization: `Bearer ${token}`, Accept: "application/json" } : { Accept: "application/json" }
        });
        console.log("Fetched /status, response:", res);
        if (!res.ok) {
          setClientsError(`Failed to fetch /status: ${res.status} ${res.statusText}`);
          if(res.status === 403 || res.status === 401) {
            clearToken();
            navigate("/login", { replace: true, state: { from: { pathname: "/shell" } } });
          }
          setLoadingClients(false);
          return;
        }
        const body = await res.json().catch(() => null);
        if (!body) {
          setClientsError("Empty or non-JSON /status response");
          setLoadingClients(false);
          return;
        }

        let newClients: { ip: string; active: boolean }[] = [];

        // shape: { "192.168.1.1": true }
        if (typeof body === "object" && !Array.isArray(body)) {
          const entries = Object.entries(body);
          const ipEntries = entries.filter(([k]) => ipRegex.test(k));
          if (ipEntries.length) {
            newClients = ipEntries.map(([k, v]) => ({ ip: k, active: Boolean(v) }));
          }
        }

        // fallback: array of items
        if (!newClients.length && Array.isArray(body)) {
          newClients = body.map((it: any) => ({
            ip: String(it.ip ?? it.address ?? it.host ?? it),
            active: Boolean(it.active ?? it.online ?? false)
          }));
        }

        if (newClients.length) {
          // sort by IP for predictable ordering
          newClients.sort((x, y) => sortIps(x.ip, y.ip));
          setClients(newClients);
        } else {
          setClientsError("No clients found in /status response");
        }
      } catch (err: any) {
        setClientsError(String(err?.message ?? err));
      } finally {
        setLoadingClients(false);
      }
    })();
  }, [client]);

  useEffect(() => {
    if (!outputRef.current) return;
    outputRef.current.scrollTop = outputRef.current.scrollHeight;
  }, [historyLines]);

  async function sendCommand(commandToSend: string) {
    if (!client) return;
    setSending(true);
    setHistoryLines(prev => [...prev, `> ${commandToSend}`]);

    try {
      const token = getToken();
      const url = "https://api.javalanche.net:8000/shell"; // proxy / relative path; dev proxy or backend should handle TLS/CORS
      const payload = { scope: client, command: commandToSend };

      const res = await fetch(url, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          ...(token ? { Authorization: `Bearer ${token}` } : {})
        },
        body: JSON.stringify(payload)
      });

      if (!res.ok) {
        const text = await res.text().catch(() => "<unable to read body>");
        setHistoryLines(prev => [...prev, `<error ${res.status} ${res.statusText}>`, text]);
        if(res.status === 403 || res.status === 401) {
            clearToken();
            navigate("/login", { replace: true, state: { from: { pathname: "/shell" } } });
          }
        return;
      }


      const body = JSON.parse(await res.text());
      // If backend returns {"output":"..."} prefer the raw output.
      const out = body?.output ?? body?.result ?? body;
      // normalize to string
      const outStr = typeof out === "string" ? out : JSON.stringify(out);
      setHistoryLines(prev => [...prev, outStr]);

    } catch (err: any) {
      setHistoryLines(prev => [...prev, `<network error> ${String(err?.message ?? err)}`]);
    } finally {
      setSending(false);
    }
  }

   // Add ref for the input element
  const inputRef = useRef<HTMLInputElement | null>(null);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!cmd.trim()) return;
    const toSend = cmd;
    setCmd("");
    await sendCommand(toSend);
    // Focus the input after sending
    inputRef.current?.focus();
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLInputElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      void handleSubmit(e as unknown as FormEvent);
    }
  }

  if (client) {
    return (
      <div style={{ padding: 16, height: "100vh", display: "flex", flexDirection: "column" }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>Shell - {client}</h3>
          <div>
            <button
              onClick={() => setHistoryLines([])}
              style={{
                marginRight: 8,
                background: "transparent",
                border: "1px solid #ccc",
                padding: "8px 12px",
                borderRadius: 6,
                cursor: "pointer",
                color: "#333"
              }}
            >
              Clear
            </button>
            <button
              onClick={() => navigate("/")}
              style={{
                background: "#c92a2a",
                color: "#fff",
                border: "none",
                padding: "8px 12px",
                borderRadius: 6,
                cursor: "pointer"
              }}
            >
              Home
            </button>
          </div>
        </div>

        <div
          ref={outputRef}
          style={{
            flex: 1,
            background: "#0b1220",
            color: "#e6edf3",
            padding: 12,
            borderRadius: 6,
            fontFamily: "monospace",
            fontSize: 13,
            overflow: "auto",
            whiteSpace: "pre-wrap"
          }}
        >
          {historyLines.length === 0 ? (
            <div style={{ color: "#8892a6" }}>Connected. Type commands below.</div>
          ) : (
            historyLines.map((l, i) => (
              <div key={i} style={{ whiteSpace: "pre-wrap", marginBottom: 6 }}>
                {l}
              </div>
            )))
          }
        </div>

        <form onSubmit={handleSubmit} style={{ marginTop: 12, display: "flex", gap: 8 }}>
          <input
            ref={inputRef}
            value={cmd}
            onChange={e => setCmd(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder={sending ? "Sending…" : "Enter command and press Enter"}
            style={{
              flex: 1,
              padding: "10px 12px",
              borderRadius: 6,
              border: "1px solid #ccc",
              fontFamily: "monospace"
            }}
            disabled={sending}
            autoFocus
          />
          <button type="submit" disabled={sending || !cmd.trim()} style={{ padding: "10px 14px", borderRadius: 6, background: "#c92a2a", color: "#fff", border: "none" }}>
            {sending ? "…" : "Send"}
          </button>
        </form>
      </div>
    );
  }

  // list view -> cleaned up tiles
  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h3 style={{ margin: 0 }}>Available Clients</h3>
        <div>
          <button
            onClick={() => navigate(location.pathname)}
            style={{
              background: "#c92a2a",
              color: "#fff",
              border: "none",
              padding: "8px 12px",
              borderRadius: 6,
              cursor: "pointer"
            }}
          >
            Refresh
          </button>
          <button
            onClick={() => navigate("/")}
            style={{
              marginLeft: 8,
              background: "transparent",
              border: "1px solid #ccc",
              padding: "8px 12px",
              borderRadius: 6,
              cursor: "pointer",
              color: "#333"
            }}
          >
            Home
          </button>
        </div>
      </div>

      {loadingClients && <div>Loading...</div>}
      {clientsError && <div style={{ color: "red" }}>{clientsError}</div>}
      {!loadingClients && !clients.length && !clientsError && <div>No clients found.</div>}

      {clients.length > 0 && (
        <div
          style={{
            display: "grid",
            gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))",
            gap: 12,
            alignItems: "start"
          }}
        >
          {clients.map(c => (
            <div
              key={c.ip}
              onClick={() => navigate(`?client=${encodeURIComponent(c.ip)}`)}
              style={{
                aspectRatio: "1 / 1",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexDirection: "column",
                borderRadius: 8,
                cursor: "pointer",
                background: c.active ? "#66bb6a" : "#c62828",
                color: "#ffffff",
                padding: 12,
                boxShadow: "0 4px 8px rgba(0,0,0,0.08)",
                userSelect: "none"
              }}
            >
              <div style={{ fontWeight: 700, fontFamily: "monospace", fontSize: 16 }}>{c.ip}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}