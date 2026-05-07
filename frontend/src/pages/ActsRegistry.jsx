import { useState, useEffect, useCallback } from "react";
import axios from "axios";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Input } from "@/components/ui/input";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Send, Eye, ChevronLeft, ChevronRight, Upload, FileText, Trash2, Download } from "lucide-react";
import { toast } from "sonner";

const API = `${process.env.REACT_APP_BACKEND_URL}/api`;

const STATUS_COLORS = {
  "Готов к отправке": "bg-blue-100 text-blue-700 border-blue-200",
  "Отправлен": "bg-amber-100 text-amber-700 border-amber-200",
  "Подписан": "bg-emerald-100 text-emerald-700 border-emerald-200",
  "Нет ответа": "bg-red-100 text-red-700 border-red-200",
  "Корректировки": "bg-orange-100 text-orange-700 border-orange-200",
  "В работе бухгалтерии": "bg-purple-100 text-purple-700 border-purple-200",
  "Закрыт": "bg-slate-100 text-slate-700 border-slate-200",
};

export default function ActsRegistry() {
  const [acts, setActs] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [filters, setFilters] = useState({ status: "all", legal_entity: "all", counterparty: "", period: "all", search: "" });
  const [periods, setPeriods] = useState([]);
  const [legalEntities, setLegalEntities] = useState([]);
  const [selectedAct, setSelectedAct] = useState(null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [actFiles, setActFiles] = useState([]);
  const limit = 20;

  const fetchActs = useCallback(async () => {
    try {
      const params = { page, limit };
      if (filters.status !== "all") params.status = filters.status;
      if (filters.legal_entity !== "all") params.legal_entity = filters.legal_entity;
      if (filters.period !== "all") params.period = filters.period;
      if (filters.search) params.search = filters.search;
      
      const res = await axios.get(`${API}/acts`, { params });
      setActs(res.data.acts);
      setTotal(res.data.total);
    } catch (e) {
      console.error(e);
    }
  }, [page, filters]);

  useEffect(() => {
    const loadFilters = async () => {
      try {
        const [p, e] = await Promise.all([
          axios.get(`${API}/periods`),
          axios.get(`${API}/legal-entities`)
        ]);
        setPeriods(p.data);
        setLegalEntities(e.data);
      } catch (err) { console.error(err); }
    };
    loadFilters();
  }, []);

  useEffect(() => { fetchActs(); }, [fetchActs]);

  const handleSendToSaby = async (actId) => {
    try {
      const res = await axios.post(`${API}/acts/${actId}/send-saby`, { document_type: "reconciliation_act" });
      toast.success(`Отправлено в СБИС. ID: ${res.data.saby_response.document_id}`);
      fetchActs();
    } catch (e) {
      toast.error(e.response?.data?.detail || "Ошибка отправки");
    }
  };

  const handleBatchSend = async () => {
    try {
      const res = await axios.post(`${API}/acts/send-batch`);
      toast.success(res.data.message);
      fetchActs();
    } catch (e) {
      toast.error("Ошибка массовой отправки");
    }
  };

  const viewDetail = async (actId) => {
    try {
      const [actRes, filesRes] = await Promise.all([
        axios.get(`${API}/acts/${actId}`),
        axios.get(`${API}/acts/${actId}/files`),
      ]);
      setSelectedAct(actRes.data);
      setActFiles(filesRes.data);
      setDetailOpen(true);
    } catch (e) {
      toast.error("Ошибка загрузки");
    }
  };

  const handleFileAttach = async (e) => {
    const file = e.target.files[0];
    if (!file || !selectedAct) return;
    const formData = new FormData();
    formData.append("file", file);
    try {
      await axios.post(`${API}/acts/${selectedAct.id}/files`, formData);
      const filesRes = await axios.get(`${API}/acts/${selectedAct.id}/files`);
      setActFiles(filesRes.data);
      toast.success("Файл прикреплён");
    } catch (err) {
      toast.error("Ошибка загрузки файла");
    }
    e.target.value = "";
  };

  const handleFileDownload = async (fileId, filename) => {
    try {
      const res = await axios.get(`${API}/files/${fileId}/download`, { responseType: "blob" });
      const url = URL.createObjectURL(res.data);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      toast.error("Ошибка скачивания");
    }
  };

  const handleFileDelete = async (fileId) => {
    try {
      await axios.delete(`${API}/files/${fileId}`);
      setActFiles(prev => prev.filter(f => f.id !== fileId));
      toast.success("Файл удалён");
    } catch (e) {
      toast.error("Ошибка удаления");
    }
  };

  const totalPages = Math.ceil(total / limit);

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="acts-registry-page">
      <div className="flex items-center justify-between">
        <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
          Реестр актов сверки
        </h1>
        <Button onClick={handleBatchSend} className="bg-emerald-600 hover:bg-emerald-700" data-testid="batch-send-button">
          <Send size={16} className="mr-2" />Отправить все готовые
        </Button>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3" data-testid="filters-bar">
        <Select value={filters.status} onValueChange={(v) => { setFilters(f => ({...f, status: v})); setPage(1); }}>
          <SelectTrigger className="w-[180px] bg-white" data-testid="filter-status">
            <SelectValue placeholder="Статус" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Все статусы</SelectItem>
            {Object.keys(STATUS_COLORS).map(s => <SelectItem key={s} value={s}>{s}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={filters.legal_entity} onValueChange={(v) => { setFilters(f => ({...f, legal_entity: v})); setPage(1); }}>
          <SelectTrigger className="w-[200px] bg-white" data-testid="filter-entity">
            <SelectValue placeholder="Юрлицо" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Все юрлица</SelectItem>
            {legalEntities.map(e => <SelectItem key={e} value={e}>{e}</SelectItem>)}
          </SelectContent>
        </Select>
        <Select value={filters.period} onValueChange={(v) => { setFilters(f => ({...f, period: v})); setPage(1); }}>
          <SelectTrigger className="w-[180px] bg-white" data-testid="filter-period">
            <SelectValue placeholder="Период" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">Все периоды</SelectItem>
            {periods.map(p => <SelectItem key={p} value={p}>{p}</SelectItem>)}
          </SelectContent>
        </Select>
        <Input
          placeholder="Поиск по номеру, контрагенту, ИНН..."
          className="w-[280px] bg-white"
          value={filters.search}
          onChange={(e) => { setFilters(f => ({...f, search: e.target.value})); setPage(1); }}
          data-testid="filter-search"
        />
      </div>

      {/* Table */}
      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden" data-testid="acts-table">
        <Table>
          <TableHeader>
            <TableRow className="bg-slate-50">
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Номер</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">ИНН</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Юрлицо</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Период</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Статус</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Действия</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {acts.map((act) => (
              <TableRow key={act.id} className="hover:bg-slate-50 transition-colors" data-testid={`act-row-${act.id}`}>
                <TableCell className="text-sm font-medium text-slate-800">{act.act_number}</TableCell>
                <TableCell className="text-sm text-slate-700">{act.counterparty}</TableCell>
                <TableCell className="text-sm text-slate-500 font-mono">{act.inn}</TableCell>
                <TableCell className="text-sm text-slate-600">{act.legal_entity}</TableCell>
                <TableCell className="text-sm text-slate-600">{act.period}</TableCell>
                <TableCell className="text-sm text-slate-700 font-medium">
                  {new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(act.amount)}
                </TableCell>
                <TableCell>
                  <Badge variant="outline" className={`${STATUS_COLORS[act.status] || ""} text-xs`} data-testid={`status-badge-${act.id}`}>
                    {act.status}
                  </Badge>
                </TableCell>
                <TableCell>
                  <div className="flex items-center gap-1">
                    <Button variant="ghost" size="sm" onClick={() => viewDetail(act.id)} data-testid={`view-btn-${act.id}`}>
                      <Eye size={14} />
                    </Button>
                    {act.status === "Готов к отправке" && (
                      <Button variant="ghost" size="sm" onClick={() => handleSendToSaby(act.id)} data-testid={`send-btn-${act.id}`}>
                        <Send size={14} className="text-emerald-600" />
                      </Button>
                    )}
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {acts.length === 0 && (
              <TableRow>
                <TableCell colSpan={8} className="text-center py-8 text-slate-400">
                  Нет актов. Загрузите данные или создайте тестовый набор на дашборде.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

      {/* Pagination */}
      {totalPages > 1 && (
        <div className="flex items-center justify-between" data-testid="pagination">
          <span className="text-sm text-slate-500">Всего: {total} актов</span>
          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" disabled={page <= 1} onClick={() => setPage(p => p - 1)}>
              <ChevronLeft size={14} />
            </Button>
            <span className="text-sm text-slate-600">Стр. {page} из {totalPages}</span>
            <Button variant="outline" size="sm" disabled={page >= totalPages} onClick={() => setPage(p => p + 1)}>
              <ChevronRight size={14} />
            </Button>
          </div>
        </div>
      )}

      {/* Detail Dialog */}
      <Dialog open={detailOpen} onOpenChange={setDetailOpen}>
        <DialogContent className="max-w-2xl" data-testid="act-detail-dialog">
          <DialogHeader>
            <DialogTitle>Акт {selectedAct?.act_number}</DialogTitle>
            <DialogDescription>Детальная информация и история изменений</DialogDescription>
          </DialogHeader>
          {selectedAct && (
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4 text-sm">
                <div><span className="text-slate-500">Контрагент:</span> <span className="font-medium">{selectedAct.counterparty}</span></div>
                <div><span className="text-slate-500">ИНН/КПП:</span> <span className="font-mono">{selectedAct.inn}/{selectedAct.kpp}</span></div>
                <div><span className="text-slate-500">Юрлицо:</span> <span className="font-medium">{selectedAct.legal_entity}</span></div>
                <div><span className="text-slate-500">Период:</span> {selectedAct.period}</div>
                <div><span className="text-slate-500">Сумма:</span> <span className="font-medium">{new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB" }).format(selectedAct.amount)}</span></div>
                <div><span className="text-slate-500">Дата формирования:</span> {selectedAct.formation_date}</div>
                <div><span className="text-slate-500">Бухгалтер:</span> {selectedAct.responsible_accountant}</div>
                <div><span className="text-slate-500">Статус:</span> <Badge variant="outline" className={STATUS_COLORS[selectedAct.status]}>{selectedAct.status}</Badge></div>
                {selectedAct.saby_send_id && (
                  <div className="col-span-2"><span className="text-slate-500">СБИС ID:</span> <span className="font-mono text-emerald-600">{selectedAct.saby_send_id}</span></div>
                )}
              </div>
              {selectedAct.history && selectedAct.history.length > 0 && (
                <div>
                  <h3 className="text-sm font-semibold text-slate-800 mb-2">История изменений</h3>
                  <div className="space-y-2 max-h-48 overflow-y-auto">
                    {selectedAct.history.map((h, i) => (
                      <div key={i} className="flex items-start gap-2 text-xs bg-slate-50 rounded p-2">
                        <span className="text-slate-400 whitespace-nowrap">{new Date(h.timestamp).toLocaleString("ru-RU")}</span>
                        <span className="text-slate-600">{h.old_status ? `${h.old_status} → ` : ""}{h.new_status}</span>
                        {h.comment && <span className="text-slate-500 italic">— {h.comment}</span>}
                      </div>
                    ))}
                  </div>
                </div>
              )}
              {/* Files Section */}
              <div>
                <div className="flex items-center justify-between mb-2">
                  <h3 className="text-sm font-semibold text-slate-800">Вложения ({actFiles.length})</h3>
                  <label className="cursor-pointer">
                    <input type="file" className="hidden" onChange={handleFileAttach} data-testid="file-attach-input" />
                    <Button asChild variant="outline" size="sm">
                      <span data-testid="attach-file-button"><Upload size={12} className="mr-1" />Прикрепить</span>
                    </Button>
                  </label>
                </div>
                {actFiles.length > 0 ? (
                  <div className="space-y-2 max-h-40 overflow-y-auto">
                    {actFiles.map((f) => (
                      <div key={f.id} className="flex items-center justify-between bg-slate-50 rounded p-2 text-xs" data-testid={`file-item-${f.id}`}>
                        <div className="flex items-center gap-2 overflow-hidden">
                          <FileText size={14} className="text-slate-400 flex-shrink-0" />
                          <span className="text-slate-700 truncate">{f.original_filename}</span>
                          <span className="text-slate-400">{f.size ? `${(f.size / 1024).toFixed(1)} КБ` : ""}</span>
                        </div>
                        <div className="flex items-center gap-1 flex-shrink-0">
                          <Button variant="ghost" size="sm" className="h-6 w-6 p-0" onClick={() => handleFileDownload(f.id, f.original_filename)} data-testid={`download-file-${f.id}`}>
                            <Download size={12} />
                          </Button>
                          <Button variant="ghost" size="sm" className="h-6 w-6 p-0 text-red-500" onClick={() => handleFileDelete(f.id)} data-testid={`delete-file-${f.id}`}>
                            <Trash2 size={12} />
                          </Button>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <p className="text-xs text-slate-400">Нет вложений. Прикрепите файлы к этому акту.</p>
                )}
              </div>
            </div>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
