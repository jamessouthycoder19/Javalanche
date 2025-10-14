import Button from "../components/Button";
import ListGroup from "../components/ClientList";
import RedTeamLogo from "../assets/RITSECRedTeamLogo.png";
import { DarkJavalancheLogo, LightJavalancheLogo } from "../components/JavalancheLogo";
import { useState, useEffect } from "react";
import '../styling/App.css';
import { getToken, clearToken } from "../lib/auth";
import { Link, useNavigate } from "react-router";

function App() {
  const [darkMode, setDarkMode] = useState(false);

  const navigate = useNavigate();

  const handleThemeToggle = () => setDarkMode(prev => !prev);

  const [clients, setClients] = useState<{ ip: string; active: boolean }[]>([]);
  const handleUpdateClients = async () => {
    try {
      const token = getToken();
      const res = await fetch("https://api.javalanche.net:8000/status", {
        method: "GET",
        headers: token ? { "Authorization": `Bearer ${token}`, "Accept": "application/json" } : { "Accept": "application/json" }
      });
      if (!res.ok) {
        console.error("Failed to fetch /status:", res.status, res.statusText);
        if (res.status === 403 || res.status === 401) {
          clearToken();
          navigate("/login", { replace: true, state: { from: { pathname: "/" } } });
        }
        return;
      }
      const body = await res.json().catch(() => null);
      if (!body) {
        console.error("Empty or non-JSON /status response");
        return;
      }

      // Normalize response into { ip, active }[]
      let newClients: { ip: string; active: boolean }[] = [];

      // Case: top-level map of ip -> bool e.g. { "192.168.1.1": true }
      if (body && typeof body === "object" && !Array.isArray(body)) {
        const entries = Object.entries(body);
        const ipRegex = /^\d{1,3}(\.\d{1,3}){3}$/;
        const ipMapEntries = entries.filter(([k, _]) => ipRegex.test(k));
        if (ipMapEntries.length) {
          newClients = ipMapEntries.map(([k, v]) => ({ ip: k, active: Boolean(v) }));
        }
      }

      // Fallbacks for other shapes
      if (!newClients.length) {
        if (Array.isArray(body)) {
          newClients = body.map((it: any) => ({
            ip: String(it.ip ?? it.address ?? it.host ?? it),
            active: Boolean(it.active ?? it.online ?? false)
          }));
        } else if (Array.isArray(body.clients)) {
          newClients = body.clients.map((it: any) => ({
            ip: String(it.ip ?? it.address ?? it.host ?? it),
            active: Boolean(it.active ?? it.online ?? false)
          }));
        } else if (body.ip) {
          newClients = [{ ip: String(body.ip), active: Boolean(body.active ?? body.online ?? true) }];
        }
      }

      if (newClients.length) {
        setClients(newClients);
      } else {
        console.warn("No clients found in /status response", body);
      }
    } catch (err) {
      console.error("Error fetching clients from /status:", err);
    }
  };

  // Update clients immediately on page load
  // eslint-disable-next-line react-hooks/exhaustive-deps
  useEffect(() => { handleUpdateClients(); }, []);

  return (
    <div className={`app-root${darkMode ? " dark" : ""}`} style={{ display: "flex", height: "100vh" }}>
      <aside className="sidebar" style={{
        width: "272px",
        padding: "1rem 0.5rem",
        display: "flex",
        flexDirection: "column"
      }}>
        <div className="listGroup" style={{ flex: 1, overflowY: "auto" }}>
          <ListGroup items={clients} heading="Clients" onSelectItem={() => console.log("hit")} />
        </div>
        <Button color="primary" onClick={handleUpdateClients}>
          Refresh Clients
        </Button>
      </aside>
      <main style={{ flex: 1, padding: "2rem" }}>
        <div style={{ display: "flex", alignItems: "flex-start", gap: "2rem" }}>
          {darkMode ? <LightJavalancheLogo /> : <DarkJavalancheLogo />}
          <div style={{ display: "flex", flexDirection: "column", alignItems: "center" }}>
            <label style={{ marginBottom: "1rem", display: "flex", alignItems: "center", gap: "0.5rem" }}>
              <div className="form-check form-switch">
                <input className="form-check-input" type="checkbox" role="switch" id="switchCheckDefault" checked={darkMode} onChange={handleThemeToggle} />
              </div>
              {darkMode ? "🌙 Dark Mode" : "☀️ Light Mode"}
            </label>
            <img
              src={RedTeamLogo}
              alt="RITSEC Red Team Logo"
              style={{ width: "220px", height: "auto" }}
            />
          </div>
          
        </div>
        <div style={{
          width: "100%",
          marginTop: "2rem",
          padding: "1rem",
          display: "flex",
          gap: "1rem",
          justifyContent: "center",
          flexWrap: "wrap"
        }}>
          <div className="card" style={{ width: "15rem", height: "15rem" }}>
            <div className="card-body">
              <h5 className="card-title">Enter Client Shell</h5>
              <p className="card-text">Enter a specific client shell. Receive instant command feedback. Useful for testing breaks before bulk deployment.</p>
              <Link to="/clientshell" className="btn btn-primary">Enter Client Shell</Link>
            </div>
          </div>
          <div className="card" style={{ width: "15rem", height: "15rem" }}>
            <div className="card-body">
              <h5 className="card-title">Run Commands</h5>
              <p className="card-text">Run Commands on a range of clients by their IP addresses. Useful for deploying breaks on all teams at once.</p>
              <Link to="clientcommand" className="btn btn-primary">Run Commands</Link>
            </div>
          </div>
          <div className="card" style={{ width: "15rem", height: "15rem" }}>
            <div className="card-body">
              <h5 className="card-title">Get Command History</h5>
              <p className="card-text">See the complete history of all commands run on clients</p>
              <Link to="clienthistory" className="btn btn-primary">Get Command History</Link>
            </div>
          </div>
        </div>
      </main>
    </div>
  );
}

export default App;