import React, { useEffect, useState } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { isTokenValid } from "../lib/auth";

export default function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const location = useLocation();
  const [checked, setChecked] = useState(false);
  const [allowed, setAllowed] = useState(false);

  useEffect(() => {
    let mounted = true;
    (async () => {
      const ok = await isTokenValid();
      if (!mounted) return;
      setAllowed(ok);
      setChecked(true);
    })();
    return () => { mounted = false; };
  }, []);

  if (!checked) return <div>Checking authentication…</div>;
  if (!allowed) return <Navigate to="/login" state={{ from: location }} replace />;
  return children as React.ReactElement;
}