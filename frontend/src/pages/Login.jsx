import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import api from "@/lib/api";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";

export default function Login() {
  const [searchParams] = useSearchParams();
  const [csrfToken, setCsrfToken] = useState("");
  const [csrfParameter, setCsrfParameter] = useState("_csrf");

  const hasError = searchParams.has("error");
  const hasLogout = searchParams.has("logout");

  useEffect(() => {
    api
      .get("/auth/csrf")
      .then((res) => {
        setCsrfToken(res.data.token);
        setCsrfParameter(res.data.parameter || "_csrf");
      })
      .catch(() => {
        setCsrfToken("");
      });
  }, []);

  return (
    <div
      className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-slate-900 to-slate-800 px-4 py-10"
      data-testid="login-page"
    >
      <img
        src={`${process.env.PUBLIC_URL}/edo-dodo-logo.png`}
        alt="EDO Dodo"
        className="mb-6 h-auto w-44 max-w-[min(100%,280px)] object-contain sm:w-52"
        data-testid="login-logo"
      />

      <div className="w-full max-w-md rounded-xl bg-white p-8 text-slate-900 shadow-2xl">
        <h1 className="mb-1 text-2xl font-bold tracking-tight" style={{ fontFamily: "Manrope, sans-serif" }}>
          EDO Dodo
        </h1>

        <p className="mb-6 text-sm text-slate-500">Войдите в систему</p>

        {hasError && (
          <div
            className="mb-4 rounded-lg border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700"
            data-testid="login-error"
          >
            Неверный логин или пароль.
          </div>
        )}
        {hasLogout && (
          <div
            className="mb-4 rounded-lg border border-green-200 bg-green-50 px-3 py-2 text-sm text-green-800"
            data-testid="login-logout-message"
          >
            Вы вышли из системы.
          </div>
        )}

        <form action="/login" method="post" className="space-y-4">
          <input type="hidden" name={csrfParameter} value={csrfToken} />
          <div className="space-y-2">
            <Label htmlFor="username">Логин</Label>
            <Input
              id="username"
              name="username"
              type="text"
              autoComplete="username"
              required
              autoFocus
              className="focus-visible:ring-orange-500"
              data-testid="login-username"
            />
          </div>
          <div className="space-y-2">
            <Label htmlFor="password">Пароль</Label>
            <Input
              id="password"
              name="password"
              type="password"
              autoComplete="current-password"
              required
              className="focus-visible:ring-orange-500"
              data-testid="login-password"
            />
          </div>
          <Button
            type="submit"
            disabled={!csrfToken}
            className="h-11 w-full bg-orange-600 text-base font-semibold hover:bg-orange-700"
            data-testid="login-submit"
          >
            Войти
          </Button>
        </form>
      </div>
    </div>
  );
}
