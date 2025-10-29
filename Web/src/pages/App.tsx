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

  const [clients, setClients] = useState<{ ip: string; active: boolean; status: string }[]>([]);

  const handleUpdateClients = async () => {
    try {
      const token = getToken();

      // Get HTTPS clients
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

      // Get DNS clients 
      const res2 = await fetch("https://api.javalanche.net:8000/dnsstatus", {
        method: "GET",
        headers: token ? { "Authorization": `Bearer ${token}`, "Accept": "application/json" } : { "Accept": "application/json" }
      });
      const body2 = await res2.json().catch(() => ({}));

      if (!body2) {
        console.error("Empty or non-JSON /status response");
        if (res2.status === 403 || res2.status === 401) {
          clearToken();
          navigate("/login", { replace: true, state: { from: { pathname: "/" } } });
        }
        return;

      }

      // Normalize response into { ip, active, status }[]
      let newClients: { ip: string; active: boolean; status: string }[] = [];
      if ((typeof body === "object" && !Array.isArray(body)) || (typeof body2 === "object" && !Array.isArray(body2))) {
        const entries = Object.entries(body);
        const entries2 = Object.entries(body2);
        const ipRegex = /^\d{1,3}(\.\d{1,3}){3}$/;
        const ipMapEntries = entries.filter(([k]) => ipRegex.test(k));
        const ipMapEntries2 = entries2.filter(([k]) => ipRegex.test(k));
        if (ipMapEntries.length || ipMapEntries2.length) {
          const allIps = new Set([...ipMapEntries.map(([k]) => k), ...ipMapEntries2.map(([k]) => k)]);
          newClients = Array.from(allIps).map((ip) => {
            const httpsActive = Boolean(body[ip]);
            const dnsActive = Boolean(body2[ip]);
            let status = "disconnected";
            if (httpsActive && dnsActive) status = "https, dns";
            else if (httpsActive) status = "https";
            else if (dnsActive) status = "dns";
            return { ip, active: httpsActive || dnsActive, status };
          });
        }
      }

      if (newClients.length) {
        setClients(newClients);
        console.log("Clients updated:", newClients);
      } else {
        console.warn("No clients found in /status response", body);
      }

    } catch (err) {
      console.error("Error fetching clients:", err);
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
          { localStorage.getItem("username") === "root" && (
            <div className="card" style={{ width: "15rem", height: "15rem" }}>
              <div className="card-body">
                <h5 className="card-title">Manage Users</h5>
                <p className="card-text">Add, delete, or set passwords of Red Team Operator users</p>
                <Link to="manageusers" className="btn btn-primary">Manage Users</Link>
              </div>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

export default App;