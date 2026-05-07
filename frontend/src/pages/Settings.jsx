import { useState, useEffect } from "react";
import axios from "axios";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Label } from "@/components/ui/label";
import { Switch } from "@/components/ui/switch";
import { Separator } from "@/components/ui/separator";
import { Settings as SettingsIcon, CheckCircle2, Loader2, LogIn, KeyRound } from "lucide-react";
import { toast } from "sonner";

const API = `${process.env.REACT_APP_BACKEND_URL}/api`;

export default function Settings() {
  const [settings, setSettings] = useState(null);
  const [form, setForm] = useState({ mode: "mock", api_url: "", api_token: "", org_inn: "" });
  const [saving, setSaving] = useState(false);
  const [authForm, setAuthForm] = useState({ login: "", password: "", account_number: "" });
  const [authenticating, setAuthenticating] = useState(false);

  const fetchSettings = async () => {
    try {
      const res = await axios.get(`${API}/settings/saby`);
      setSettings(res.data);
      setForm({
        mode: res.data.mode || "mock",
        api_url: res.data.api_url || "",
        api_token: res.data.api_token || "",
        org_inn: res.data.org_inn || "",
      });
    } catch (e) {
      console.error(e);
    }
  };

  useEffect(() => { fetchSettings(); }, []);

  const handleSave = async () => {
    setSaving(true);
    try {
      const payload = { ...form };
      // Don't send masked token back
      if (payload.api_token.startsWith("••")) {
        delete payload.api_token;
      }
      const res = await axios.put(`${API}/settings/saby`, payload);
      setSettings(res.data);
      setForm({
        mode: res.data.mode,
        api_url: res.data.api_url,
        api_token: res.data.api_token,
        org_inn: res.data.org_inn,
      });
      toast.success("Настройки сохранены");
    } catch (e) {
      toast.error("Ошибка сохранения");
    } finally {
      setSaving(false);
    }
  };

  const handleAuth = async () => {
    if (!authForm.login || !authForm.password) {
      toast.error("Введите логин и пароль");
      return;
    }
    setAuthenticating(true);
    try {
      const res = await axios.post(`${API}/settings/saby/auth`, authForm);
      if (res.data.success) {
        toast.success(res.data.message);
        setAuthForm({ login: "", password: "", account_number: "" });
        await fetchSettings();
      } else {
        toast.error(res.data.message);
      }
    } catch (e) {
      toast.error(e.response?.data?.detail || "Ошибка авторизации");
    } finally {
      setAuthenticating(false);
    }
  };

  const isProduction = form.mode === "production";

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="settings-page">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
        Настройки
      </h1>

      {/* СБИС Integration Settings */}
      <Card className="p-6 space-y-5">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <SettingsIcon size={18} className="text-slate-600" />
            <h2 className="text-lg font-semibold text-slate-800">Интеграция с Saby/СБИС</h2>
            <Badge
              variant="outline"
              className={isProduction ? "bg-emerald-50 text-emerald-700 border-emerald-200 ml-2" : "bg-amber-50 text-amber-700 border-amber-200 ml-2"}
              data-testid="mode-badge"
            >
              {isProduction ? "Продуктовый режим" : "Mock-режим"}
            </Badge>
          </div>
        </div>

        {/* Mode Toggle */}
        <div className="flex items-center justify-between p-4 bg-slate-50 rounded-lg border border-slate-200" data-testid="mode-toggle-section">
          <div>
            <p className="text-sm font-medium text-slate-800">Режим работы</p>
            <p className="text-xs text-slate-500 mt-0.5">
              {isProduction
                ? "Отправка документов через реальный API СБИС"
                : "Имитация вызовов API — документы не отправляются реально"}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <span className={`text-xs font-medium ${!isProduction ? "text-amber-600" : "text-slate-400"}`}>Mock</span>
            <Switch
              checked={isProduction}
              onCheckedChange={(checked) => setForm(f => ({ ...f, mode: checked ? "production" : "mock" }))}
              data-testid="mode-switch"
            />
            <span className={`text-xs font-medium ${isProduction ? "text-emerald-600" : "text-slate-400"}`}>Production</span>
          </div>
        </div>

        {/* Connection fields */}
        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">API URL</Label>
            <Input
              value={form.api_url}
              onChange={(e) => setForm(f => ({ ...f, api_url: e.target.value }))}
              placeholder="https://online.sbis.ru/service"
              className={isProduction ? "bg-white" : "bg-slate-50"}
              data-testid="saby-api-url"
            />
          </div>
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">API Токен (Session ID)</Label>
            <Input
              type="password"
              value={form.api_token}
              onChange={(e) => setForm(f => ({ ...f, api_token: e.target.value }))}
              placeholder={isProduction ? "Введите токен СБИС..." : "Не требуется в mock-режиме"}
              className={isProduction ? "bg-white" : "bg-slate-50"}
              data-testid="saby-api-token"
            />
            {settings?.api_token_set && (
              <p className="text-xs text-emerald-600">Токен установлен</p>
            )}
          </div>
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">ИНН организации</Label>
            <Input
              value={form.org_inn}
              onChange={(e) => setForm(f => ({ ...f, org_inn: e.target.value }))}
              placeholder="7700123456"
              className={isProduction ? "bg-white" : "bg-slate-50"}
              data-testid="saby-org-inn"
            />
          </div>
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">Текущий режим</Label>
            <Input
              value={isProduction ? "Production (боевой)" : "Mock (тестовый)"}
              disabled
              className="bg-slate-50"
              data-testid="saby-current-mode"
            />
          </div>
        </div>

        {/* Warning for production */}
        {isProduction && !settings?.api_token_set && (
          <div className="p-3 bg-amber-50 border border-amber-200 rounded-lg text-sm text-amber-800" data-testid="production-warning">
            Для работы в продуктовом режиме необходимо указать API-токен СБИС.
            Без токена отправка документов будет возвращать ошибку.
          </div>
        )}

        <div className="flex items-center gap-3 pt-2">
          <Button onClick={handleSave} disabled={saving} data-testid="save-settings-button">
            {saving && <Loader2 size={14} className="mr-2 animate-spin" />}
            Сохранить настройки
          </Button>
          {settings && (
            <span className="text-xs text-slate-400">
              Последний режим: {settings.mode === "production" ? "Production" : "Mock"}
            </span>
          )}
        </div>
      </Card>

      {/* СБИС Authentication */}
      <Card className="p-6 space-y-5" data-testid="saby-auth-card">
        <div className="flex items-center gap-2">
          <LogIn size={18} className="text-blue-600" />
          <h2 className="text-lg font-semibold text-slate-800">Авторизация в СБИС</h2>
        </div>
        <p className="text-sm text-slate-500">
          Введите логин и пароль от вашего аккаунта СБИС. После успешной авторизации SessionID
          автоматически подставится в поле API-токена, а режим переключится на Production.
        </p>

        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">Логин СБИС</Label>
            <Input
              value={authForm.login}
              onChange={(e) => setAuthForm(f => ({ ...f, login: e.target.value }))}
              placeholder="user@company.ru"
              data-testid="saby-auth-login"
            />
          </div>
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">Пароль</Label>
            <Input
              type="password"
              value={authForm.password}
              onChange={(e) => setAuthForm(f => ({ ...f, password: e.target.value }))}
              placeholder="Пароль от СБИС"
              data-testid="saby-auth-password"
              onKeyDown={(e) => e.key === "Enter" && handleAuth()}
            />
          </div>
          <div className="space-y-2">
            <Label className="text-sm text-slate-600">Номер аккаунта <span className="text-slate-400">(опционально)</span></Label>
            <Input
              value={authForm.account_number}
              onChange={(e) => setAuthForm(f => ({ ...f, account_number: e.target.value }))}
              placeholder="Если несколько организаций"
              data-testid="saby-auth-account"
            />
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Button onClick={handleAuth} disabled={authenticating} className="bg-blue-600 hover:bg-blue-700" data-testid="saby-auth-button">
            {authenticating ? <Loader2 size={14} className="mr-2 animate-spin" /> : <KeyRound size={14} className="mr-2" />}
            Авторизоваться и получить токен
          </Button>
          {settings?.auth_login && (
            <span className="text-xs text-slate-400">
              Последняя авторизация: {settings.auth_login}
              {settings.auth_at && ` (${new Date(settings.auth_at).toLocaleString("ru-RU")})`}
            </span>
          )}
        </div>

        <div className="p-3 bg-slate-50 border border-slate-200 rounded-lg text-xs text-slate-500 space-y-1">
          <p><span className="font-medium text-slate-700">Как это работает:</span></p>
          <p>1. Система отправляет запрос <code className="bg-slate-200 px-1 rounded">СБИС.Аутентифицировать</code> на сервер СБИС</p>
          <p>2. При успехе полученный <code className="bg-slate-200 px-1 rounded">SessionID</code> сохраняется как API-токен</p>
          <p>3. Режим автоматически переключается на Production</p>
          <p>4. Сессия действительна 24 часа (или 7 дней с момента авторизации)</p>
        </div>
      </Card>

      {/* Status Model */}
      <Card className="p-6 space-y-4">
        <h2 className="text-lg font-semibold text-slate-800">Статусная модель</h2>
        <p className="text-sm text-slate-500">Жизненный цикл акта сверки в системе:</p>
        <div className="flex flex-wrap gap-2 items-center">
          {[
            { name: "Готов к отправке", color: "bg-blue-100 text-blue-700" },
            { name: "Отправлен", color: "bg-amber-100 text-amber-700" },
            { name: "Подписан", color: "bg-emerald-100 text-emerald-700" },
            { name: "Нет ответа", color: "bg-red-100 text-red-700" },
            { name: "Корректировки", color: "bg-orange-100 text-orange-700" },
            { name: "В работе бухгалтерии", color: "bg-purple-100 text-purple-700" },
            { name: "Закрыт", color: "bg-slate-100 text-slate-700" },
          ].map((s, i) => (
            <div key={i} className="flex items-center gap-1">
              <Badge variant="outline" className={`${s.color} border-transparent`}>{s.name}</Badge>
              {i < 6 && <span className="text-slate-300 mx-1">→</span>}
            </div>
          ))}
        </div>
        <div className="text-sm text-slate-600 space-y-1 mt-3">
          <p className="flex items-start gap-2"><CheckCircle2 size={14} className="text-emerald-500 mt-0.5 flex-shrink-0" /> Успешный кейс: Готов к отправке → Отправлен → Подписан → Закрыт</p>
          <p className="flex items-start gap-2"><CheckCircle2 size={14} className="text-amber-500 mt-0.5 flex-shrink-0" /> Нет ответа: Отправлен → Нет ответа → (эскалация в бухгалтерию или повторная отправка)</p>
          <p className="flex items-start gap-2"><CheckCircle2 size={14} className="text-orange-500 mt-0.5 flex-shrink-0" /> Корректировки: Отправлен → Корректировки → В работе бухгалтерии → Готов к отправке (повтор)</p>
        </div>
      </Card>
    </div>
  );
}
