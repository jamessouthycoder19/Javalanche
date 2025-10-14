import { type FormEvent, useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { clearToken, getToken, isTokenValid, requestToken, setToken } from "../lib/auth";
import RedTeamLogo from "../assets/RITSECRedTeamLogo.png";
import { DarkJavalancheLogo, LightJavalancheLogo } from "../components/JavalancheLogo";

function Login() {
  const navigate = useNavigate();
  const location = useLocation() as any;
  const from = location.state?.from?.pathname || "/";
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [useDarkLogo] = useState(() => {
    // pick a logo variant depending on prefers-color-scheme (initial only)
    try {
      return window.matchMedia && window.matchMedia("(prefers-color-scheme: dark)").matches;
    } catch {
      return false;
    }
  });

  useEffect(() => {
    (async () => {
      if (await isTokenValid(getToken())) navigate(from, { replace: true });
    })();
  }, [navigate, from]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const res = await requestToken(username, password);
      setToken(res.bearer, res.ttl);
      navigate(from, { replace: true });
    } catch (err: any) {
      clearToken();
      setError(err?.message || "Login failed");
      setLoading(false);
    }
  }

  return (
    <div style={{
      minHeight: "100vh",
      display: "flex",
      alignItems: "center",
      justifyContent: "center",
      background: "linear-gradient(180deg,#fff,#f3f3f3)",
      padding: 2
    }}>
      <div style={{
        width: 1050,
        maxWidth: "95%",
        display: "flex",
        gap: 32,
        background: "#ffffff",
        borderRadius: 12,
        boxShadow: "0 10px 30px rgba(0,0,0,0.12)",
        padding: 28,
        alignItems: "center"
      }}>
        <div style={{ flex: "0 0 30px", textAlign: "center" }}>
          <div style={{ marginBottom: 12 }}>
            {useDarkLogo ? <LightJavalancheLogo /> : <DarkJavalancheLogo />}
          </div>
          <div
            style={{
            marginBottom: 20,
            color: "#555",
            fontSize: 14,
            display: "flex",
            alignItems: "center",
            justifyContent: "center", // optional: centers the pair horizontally
            gap: 12, // optional: adds spacing between image and text
            }}
        >
              <img src={RedTeamLogo} alt="RITSEC Red Team Logo" style={{ width: 250, opacity: 0.95 }} />
                <div style={{ flex: 1 }}>
          <h2 style={{ margin: "0 0 16px 0" }}>Sign in</h2>
          <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: 12 }}>
            <input
              value={username}
              onChange={e => setUsername(e.target.value)}
              placeholder="Username"
              autoComplete="username"
              style={{
                padding: "10px 12px",
                borderRadius: 8,
                border: "1px solid #d0d7de",
                fontSize: 14
              }}
            />
            <input
              value={password}
              onChange={e => setPassword(e.target.value)}
              type="password"
              placeholder="Password"
              autoComplete="current-password"
              style={{
                padding: "10px 12px",
                borderRadius: 8,
                border: "1px solid #d0d7de",
                fontSize: 14
              }}
            />

            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
              <button
                type="submit"
                disabled={loading}
                style={{
                  background: "#c92a2a",
                  color: "#fff",
                  border: "none",
                  padding: "10px 16px",
                  borderRadius: 8,
                  cursor: loading ? "default" : "pointer"
                }}
              >
                {loading ? "Signing in…" : "Sign in"}
              </button>

              <button
                type="button"
                onClick={() => { setUsername(""); setPassword(""); setError(null); }}
                style={{
                  background: "transparent",
                  border: "none",
                  color: "#666",
                  cursor: "pointer"
                }}
              >
                Reset
              </button>
            </div>

            {error && <div style={{ color: "red", marginTop: 6 }}>{error}</div>}
            <div style={{ marginTop: 10, color: "#8b8f94", fontSize: 13 }}>
              Need help? Contact the red team.
            </div>
          </form>
        </div>
        </div>
        </div>

        
      </div>
    </div>
  );
}

export default Login;