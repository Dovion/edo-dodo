import { useEffect, useState } from "react";
import { Outlet } from "react-router-dom";
import api, { redirectToLogin } from "@/lib/api";

export default function RequireAuth() {
  const [status, setStatus] = useState("loading");

  useEffect(() => {
    let cancelled = false;

    api
      .get("/auth/session")
      .then(() => {
        if (!cancelled) {
          setStatus("authenticated");
        }
      })
      .catch(() => {
        if (!cancelled) {
          redirectToLogin();
        }
      });

    return () => {
      cancelled = true;
    };
  }, []);

  if (status !== "authenticated") {
    return (
      <div className="flex h-screen items-center justify-center bg-slate-100 text-slate-600">
        Проверка авторизации…
      </div>
    );
  }

  return <Outlet />;
}
