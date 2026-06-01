import { useState, useEffect, useCallback } from "react";
import api from "@/lib/api";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Upload, Download, TrendingUp, TrendingDown, AlertCircle, BarChart3, FileWarning } from "lucide-react";
import { toast } from "sonner";

export default function Dashboard() {
  const [stats, setStats] = useState(null);
  const [attention, setAttention] = useState(null);
  const [stages, setStages] = useState([]);
  const [periods, setPeriods] = useState([]);
  const [legalEntities, setLegalEntities] = useState([]);
  const [selectedPeriod, setSelectedPeriod] = useState("all");
  const [selectedEntity, setSelectedEntity] = useState("all");
  const [search, setSearch] = useState("");

  const fetchData = useCallback(async () => {
    try {
      const params = {};
      if (selectedPeriod !== "all") params.period = selectedPeriod;
      if (selectedEntity !== "all") params.legal_entity = selectedEntity;
      if (search.trim()) params.search = search.trim();

      const [statsRes, attentionRes, stagesRes, periodsRes, entitiesRes] = await Promise.all([
        api.get(`/dashboard/stats`, { params }),
        api.get(`/dashboard/attention`, { params }),
        api.get(`/dashboard/stages`, { params }),
        api.get(`/periods`),
        api.get(`/legal-entities`),
      ]);
      setStats(statsRes.data);
      setAttention(attentionRes.data);
      setStages(stagesRes.data);
      setPeriods(periodsRes.data);
      setLegalEntities(entitiesRes.data);
    } catch (e) {
      console.error("Error fetching dashboard data:", e);
    }
  }, [selectedPeriod, selectedEntity, search]);

  useEffect(() => {
    const debounce = setTimeout(() => { fetchData(); }, search ? 400 : 0);
    return () => clearTimeout(debounce);
  }, [fetchData]);

  const handleSeed = async () => {
    try {
      await api.post(`/seed`);
      toast.success("Тестовые данные загружены");
      fetchData();
    } catch (e) {
      toast.error("Ошибка загрузки данных");
    }
  };

  const handleFileUpload = async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    const formData = new FormData();
    formData.append("file", file);
    try {
      const res = await api.post(`/acts/upload`, formData);
      toast.success(res.data.message);
      fetchData();
    } catch (err) {
      toast.error(err.response?.data?.detail || "Ошибка загрузки");
    }
    e.target.value = "";
  };

  const handleExport = async () => {
    try {
      const res = await api.get(`/acts/export/json`);
      const blob = new Blob([JSON.stringify(res.data, null, 2)], { type: "application/json" });
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = `export_acts_${new Date().toISOString().slice(0,10)}.json`;
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Экспорт завершён");
    } catch (e) {
      toast.error("Ошибка экспорта");
    }
  };

  const handleDownloadSamples = async () => {
    try {
      const res = await api.get(`/acts/samples`, { responseType: "blob" });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = "sample_acts.zip";
      a.click();
      URL.revokeObjectURL(url);
      toast.success("Шаблоны скачаны: JSON, CSV, Excel");
    } catch (e) {
      toast.error("Ошибка скачивания шаблонов");
    }
  };

  if (!stats) {
    return (
      <div className="p-8 flex items-center justify-center h-full">
        <div className="text-slate-400 text-sm">Загрузка...</div>
      </div>
    );
  }

  const maxStage = Math.max(...stages.map(s => s.count), 1);

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="dashboard-page">
      {/* Header */}
      <div className="flex flex-wrap items-center justify-between gap-4">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
          Центр контроля актов сверки
        </h1>
        <div className="flex items-center gap-3 flex-wrap">
          <Select value={selectedPeriod} onValueChange={setSelectedPeriod}>
            <SelectTrigger className="w-[180px] bg-white" data-testid="period-filter">
              <SelectValue placeholder="Период" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Все периоды</SelectItem>
              {periods.map(p => <SelectItem key={p} value={p}>{p}</SelectItem>)}
            </SelectContent>
          </Select>
          <Select value={selectedEntity} onValueChange={setSelectedEntity}>
            <SelectTrigger className="w-[180px] bg-white" data-testid="entity-filter">
              <SelectValue placeholder="Юрлицо" />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="all">Все юрлица</SelectItem>
              {legalEntities.map(e => <SelectItem key={e} value={e}>{e}</SelectItem>)}
            </SelectContent>
          </Select>
          <Input
            placeholder="Номер акта, контрагент, ИНН..."
            className="w-[240px] bg-white"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            data-testid="search-input"
          />
          <label className="cursor-pointer">
            <input type="file" className="hidden" accept=".json,.csv,.xlsx,.xls" onChange={handleFileUpload} data-testid="file-upload-input" />
            <Button asChild variant="default" className="bg-emerald-600 hover:bg-emerald-700 text-white">
              <span data-testid="upload-button"><Upload size={16} className="mr-2" />Загрузить акты</span>
            </Button>
          </label>
          <Button variant="outline" onClick={handleExport} data-testid="export-button">
            <Download size={16} className="mr-2" />Экспорт
          </Button>
          <Button variant="outline" onClick={handleSeed} data-testid="seed-button" className="text-xs">
            Демо-данные
          </Button>
          <Button variant="outline" onClick={handleDownloadSamples} data-testid="download-samples-button" className="text-base" title="Скачать шаблоны (JSON, CSV, Excel)">
            📎
          </Button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4" data-testid="kpi-cards">
        <StatCard label="Всего актов в цикле" value={stats.total_acts} trend={5} />
        <StatCard label="Загружено" value={stats.ready_to_send} trend={2} color="emerald" />
        <StatCard label="Отправлено / ждём ответ" value={stats.sent_waiting} trend={-3} />
        <StatCard label="Получен подписанный" value={stats.signed} trend={12} color="emerald" />
      </div>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        <StatCard label="Просрочено" value={stats.overdue} trend={-8} color="red" />
        <StatCard label="Корректировки" value={stats.corrections} trend={4} color="amber" />
        <StatCard label="Эскалировано в бухгалтерию" value={stats.escalated} />
        <StatCard label="Закрыто" value={stats.closed} trend={15} color="emerald" />
      </div>

      {/* Attention Section */}
      {attention && (
        <div className="space-y-3" data-testid="attention-section">
          <h2 className="text-xl font-semibold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
            Требует внимания
          </h2>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="alert-card alert-card-red" data-testid="attention-no-response">
              <div className="flex items-start gap-3">
                <AlertCircle className="text-red-500 mt-0.5" size={20} />
                <div>
                  <p className="text-sm text-slate-600">Акты после 3+ уведомлений</p>
                  <p className="text-3xl font-bold text-slate-900 mt-1">{attention.no_response_acts}</p>
                  <p className="text-xs text-slate-500 mt-1">Зависли без ответа, нужна эскалация</p>
                </div>
              </div>
            </div>
            <div className="alert-card alert-card-yellow" data-testid="attention-large-tail">
              <div className="flex items-start gap-3">
                <BarChart3 className="text-amber-500 mt-0.5" size={20} />
                <div>
                  <p className="text-sm text-slate-600">Контрагенты с крупным хвостом</p>
                  <p className="text-3xl font-bold text-slate-900 mt-1">{attention.large_tail_counterparties}</p>
                  <p className="text-xs text-slate-500 mt-1">Сумма необработанных актов &gt; 2 млн ₽</p>
                </div>
              </div>
            </div>
            <div className="alert-card alert-card-blue" data-testid="attention-low-closure">
              <div className="flex items-start gap-3">
                <FileWarning className="text-blue-500 mt-0.5" size={20} />
                <div>
                  <p className="text-sm text-slate-600">Юрлица с низкой долей закрытия</p>
                  <p className="text-3xl font-bold text-slate-900 mt-1">{attention.low_closure_entities}</p>
                  <p className="text-xs text-slate-500 mt-1">Доля подписанных актов &lt; 40%</p>
                </div>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* Process Stages */}
      <div className="space-y-3" data-testid="stages-section">
        <h2 className="text-xl font-semibold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
          Стадии процесса
        </h2>
        <Card className="p-6">
          <div className="space-y-4">
            {stages.map((stage, i) => (
              <div key={i} className="flex items-center gap-4" data-testid={`stage-${i}`}>
                <span className="text-sm text-slate-600 w-44 text-right flex-shrink-0">{stage.name}</span>
                <div className="flex-1 bg-slate-100 rounded-md h-8 overflow-hidden">
                  <div
                    className="progress-bar bg-emerald-500 flex items-center justify-end pr-3"
                    style={{ width: `${Math.max((stage.count / maxStage) * 100, 8)}%` }}
                  >
                    <span className="text-xs font-semibold text-white">{stage.count}</span>
                  </div>
                </div>
              </div>
            ))}
          </div>
        </Card>
      </div>
    </div>
  );
}

function StatCard({ label, value, trend, color }) {
  const trendColor = trend > 0 ? "text-emerald-600" : trend < 0 ? "text-red-500" : "text-slate-400";
  const TrendIcon = trend > 0 ? TrendingUp : trend < 0 ? TrendingDown : TrendingUp;

  return (
    <div className="stat-card" data-testid={`stat-card-${label}`}>
      <p className="text-sm text-slate-500 font-medium">{label}</p>
      <div className="flex items-end justify-between mt-2">
        <p className="text-3xl font-bold text-slate-900">{value}</p>
        {trend !== undefined && (
          <div className={`flex items-center gap-0.5 text-xs font-medium ${trendColor}`}>
            <TrendIcon size={14} />
            <span>{Math.abs(trend)}%</span>
          </div>
        )}
      </div>
    </div>
  );
}
