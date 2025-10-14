import { useEffect, useState } from "react";
import { useSearchParams, useNavigate, useLocation } from "react-router-dom";
import { getToken } from "../lib/auth";

const ipRegex = /^(?:\d{1,3}|x)(?:\.(?:\d{1,3}|x)){3}$/;

/** sort IPv4 strings numerically */
function sortIps(a: string, b: string) {
  const aa = a.split(".").map(n => Number(n));
  const bb = b.split(".").map(n => Number(n));
  for (let i = 0; i < 4; i++) {
    if ((aa[i] ?? 0) < (bb[i] ?? 0)) return -1;
    if ((aa[i] ?? 0) > (bb[i] ?? 0)) return 1;
  }
  return 0;
}

export default function History() {
  const [searchParams] = useSearchParams();
  const location = useLocation();
  const navigate = useNavigate();
  const client = searchParams.get("client");

  const [clients, setClients] = useState<{ ip: string; active: boolean }[]>([]);
  const [loadingClients, setLoadingClients] = useState(false);
  const [clientsError, setClientsError] = useState<string | null>(null);

  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [historyLines, setHistoryLines] = useState<string[]>([]);

  // fetch /status when in list view
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
        if (!res.ok) {
          setClientsError(`Failed to fetch /status: ${res.status} ${res.statusText}`);
          if(res.status === 403 || res.status === 401) {
            navigate("/login", { replace: true, state: { from: { pathname: "/clienthistory" } } });
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
        if (typeof body === "object" && !Array.isArray(body)) {
          const entries = Object.entries(body);
          const ipEntries = entries.filter(([k]) => ipRegex.test(k));
          if (ipEntries.length) newClients = ipEntries.map(([k, v]) => ({ ip: k, active: Boolean(v) }));
        }
        if (!newClients.length && Array.isArray(body)) {
          newClients = body.map((it: any) => ({
            ip: String(it.ip ?? it.address ?? it.host ?? it),
            active: Boolean(it.active ?? it.online ?? false)
          }));
        }

        if (newClients.length) {
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

  // fetch history for a specific client
  useEffect(() => {
    if (!client) return;
    (async () => {
      setHistoryLoading(true);
      setHistoryError(null);
      setHistoryLines([]);
      try {
        const token = getToken();
        const res = await fetch("https://api.javalanche.net:8000/responses", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
            ...(token ? { Authorization: `Bearer ${token}` } : {})
          },
          body: JSON.stringify({ scope: client })
        });
        if (!res.ok) {
          const text = await res.text().catch(() => "<unreadable body>");
          if(res.status === 403 || res.status === 401) {
            navigate("/login", { replace: true, state: { from: { pathname: "/clienthistory" } } });
          }
          setHistoryError(`Failed to fetch /responses: ${res.status} ${res.statusText}\n${text}`);
          setHistoryLoading(false);
          return;
        }
        const body = await res.json().catch(() => null);
        if (!body) {
          setHistoryError("Empty or non-JSON /responses response");
          setHistoryLoading(false);
          return;
        }

        // body may be a map of ip -> [responses]
        const lines: string[] = [];
        if (typeof body === "object" && !Array.isArray(body)) {
          // If body contains multiple IPs, pick the requested client's array if present,
          // otherwise merge all results into the list.
          if (body[client] && Array.isArray(body[client])) {
            for (const r of body[client]) lines.push(String(r));
          } else {
            // collect everything
            const keys = Object.keys(body).sort((a, b) => sortIps(a, b));
            for (const k of keys) {
              const arr = Array.isArray(body[k]) ? body[k] : [body[k]];
              for (const r of arr) lines.push(`${k}: ${String(r)}`);
            }
          }
        } else if (Array.isArray(body)) {
          for (const item of body) lines.push(String(item));
        } else {
          lines.push(String(body));
        }

        setHistoryLines(lines.length ? lines : [`No responses for ${client}`]);
      } catch (err: any) {
        setHistoryError(String(err?.message ?? err));
      } finally {
        setHistoryLoading(false);
      }
    })();
  }, [client]);

  // UI
  if (client) {
    return (
      <div style={{ padding: 20 }}>
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
          <h3 style={{ margin: 0 }}>Command History — {client}</h3>
          <div>
            <button
              onClick={() => navigate(location.pathname)}
              style={{ background: "#c92a2a", color: "#fff", border: "none", padding: "8px 12px", borderRadius: 6, cursor: "pointer", marginRight: 8 }}
            >
              Refresh
            </button>
            <button
              onClick={() => navigate("/clienthistory")}
              style={{ background: "transparent", border: "1px solid #ccc", padding: "8px 12px", borderRadius: 6, cursor: "pointer", color: "#333", marginRight: 8 }}
            >
              Back
            </button>
            <button
              onClick={() => navigate("/")}
              style={{ background: "#c92a2a", color: "#fff", border: "none", padding: "8px 12px", borderRadius: 6, cursor: "pointer" }}
            >
              Home
            </button>
          </div>
        </div>

        {historyLoading && <div>Loading history…</div>}
        {historyError && <pre style={{ color: "crimson", whiteSpace: "pre-wrap" }}>{historyError}</pre>}

        {!historyLoading && !historyError && (
          <div style={{ marginTop: 12 }}>
            {historyLines.length === 0 ? (
              <div>No history found.</div>
            ) : (
              <div style={{ background: "#f6f8fa", padding: 12, borderRadius: 6, whiteSpace: "pre-wrap", fontFamily: "monospace" }}>
                {historyLines.map((l, i) => <div key={i}>{l}</div>)}
              </div>
            )}
          </div>
        )}
      </div>
    );
  }

  // list view
  return (
    <div style={{ padding: 20 }}>
      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 12 }}>
        <h3 style={{ margin: 0 }}>Command History — Select Client</h3>
        <div>
          <button
            onClick={() => navigate(location.pathname)}
            style={{ background: "#c92a2a", color: "#fff", border: "none", padding: "8px 12px", borderRadius: 6, cursor: "pointer", marginRight: 8 }}
          >
            Refresh
          </button>
          <button
            onClick={() => navigate("/")}
            style={{ background: "transparent", border: "1px solid #ccc", padding: "8px 12px", borderRadius: 6, cursor: "pointer", color: "#333" }}
          >
            Home
          </button>
        </div>
      </div>

      {loadingClients && <div>Loading clients…</div>}
      {clientsError && <pre style={{ color: "crimson", whiteSpace: "pre-wrap" }}>{clientsError}</pre>}

      {!loadingClients && !clients.length && !clientsError && <div>No clients found.</div>}

      {clients.length > 0 && (
        <div style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(140px, 1fr))",
          gap: 12,
          alignItems: "start"
        }}>
          {clients.map(c => (
            <div
              key={c.ip}
              onClick={() => navigate(`/clienthistory?client=${encodeURIComponent(c.ip)}`)}
              style={{
                aspectRatio: "1 / 1",
                display: "flex",
                alignItems: "center",
                justifyContent: "center",
                flexDirection: "column",
                borderRadius: 8,
                cursor: "pointer",
                background: "#ffffffff",
                color: "#000000ff",
                padding: 12,
                boxShadow: "0 4px 8px rgba(0,0,0,0.08)",
                userSelect: "none",
                fontFamily: "monospace",
                fontWeight: 700
              }}
            >
              <div style={{ fontSize: 16 }}>{c.ip}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}