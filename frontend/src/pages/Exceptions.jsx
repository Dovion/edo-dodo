import { useState, useEffect } from "react";
import api from "@/lib/api";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { AlertCircle, Clock, Send, CheckCircle } from "lucide-react";
import { toast } from "sonner";

const STATUS_COLORS = {
  "Нет ответа": "bg-red-100 text-red-700 border-red-200",
  "Корректировки": "bg-orange-100 text-orange-700 border-orange-200",
};

export default function Exceptions() {
  const [acts, setActs] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchExceptions = async () => {
    try {
      const [noResponse, corrections] = await Promise.all([
        api.get(`/acts`, { params: { status: "Нет ответа", limit: 100 } }),
        api.get(`/acts`, { params: { status: "Корректировки", limit: 100 } }),
      ]);
      setActs([...noResponse.data.acts, ...corrections.data.acts]);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchExceptions(); }, []);

  const escalateToAccounting = async (actId) => {
    try {
      await api.patch(`/acts/${actId}/status`, { status: "В работе бухгалтерии", comment: "Эскалировано из очереди проблемных кейсов" });
      toast.success("Передано в бухгалтерию");
      fetchExceptions();
    } catch (e) {
      toast.error("Ошибка");
    }
  };

  const closeAct = async (actId) => {
    try {
      await api.patch(`/acts/${actId}/status`, { status: "Закрыт", comment: "Закрыт (контрагент в исключениях)" });
      toast.success("Акт закрыт");
      fetchExceptions();
    } catch (e) {
      toast.error(e.response?.data?.detail || "Ошибка");
    }
  };

  const resend = async (actId) => {
    try {
      await api.patch(`/acts/${actId}/status`, { status: "Загружено", comment: "Подготовлено к повторной отправке" });
      toast.success("Подготовлено к повторной отправке");
      fetchExceptions();
    } catch (e) {
      toast.error("Ошибка");
    }
  };

  if (loading) return <div className="p-8 text-slate-400 text-sm">Загрузка...</div>;

  const noResponseActs = acts.filter(a => a.status === "Нет ответа");
  const correctionActs = acts.filter(a => a.status === "Корректировки");

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="exceptions-page">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
        Проблемные кейсы
      </h1>

      {/* No Response Section */}
      <div className="space-y-3" data-testid="no-response-section">
        <div className="flex items-center gap-2">
          <Clock size={18} className="text-red-500" />
          <h2 className="text-lg font-semibold text-slate-800">Нет ответа ({noResponseActs.length})</h2>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50">
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Номер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Юрлицо</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Статус</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {noResponseActs.map(act => (
                <TableRow key={act.id} className="hover:bg-slate-50" data-testid={`exception-row-${act.id}`}>
                  <TableCell className="text-sm font-medium">{act.act_number}</TableCell>
                  <TableCell className="text-sm">{act.counterparty}</TableCell>
                  <TableCell className="text-sm text-slate-600">{act.legal_entity}</TableCell>
                  <TableCell className="text-sm font-medium">{new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(act.amount)}</TableCell>
                  <TableCell><Badge variant="outline" className={STATUS_COLORS[act.status]}>{act.status}</Badge></TableCell>
                  <TableCell>
                    {act.counterparty_exception ? (
                      act.status !== "Закрыт" && (
                        <Button variant="outline" size="sm" onClick={() => closeAct(act.id)} data-testid={`close-btn-${act.id}`}>
                          <CheckCircle size={12} className="mr-1" />Закрыть
                        </Button>
                      )
                    ) : (
                      <div className="flex gap-1">
                        <Button variant="outline" size="sm" onClick={() => escalateToAccounting(act.id)} data-testid={`escalate-btn-${act.id}`}>
                          <AlertCircle size={12} className="mr-1" />В бухгалтерию
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => resend(act.id)} data-testid={`resend-btn-${act.id}`}>
                          <Send size={12} className="mr-1" />Повторить
                        </Button>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {noResponseActs.length === 0 && (
                <TableRow><TableCell colSpan={6} className="text-center py-6 text-slate-400">Нет проблемных актов</TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* Corrections Section */}
      <div className="space-y-3" data-testid="corrections-section">
        <div className="flex items-center gap-2">
          <AlertCircle size={18} className="text-orange-500" />
          <h2 className="text-lg font-semibold text-slate-800">Корректировки ({correctionActs.length})</h2>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50">
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Номер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Юрлицо</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Статус</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {correctionActs.map(act => (
                <TableRow key={act.id} className="hover:bg-slate-50" data-testid={`correction-row-${act.id}`}>
                  <TableCell className="text-sm font-medium">{act.act_number}</TableCell>
                  <TableCell className="text-sm">{act.counterparty}</TableCell>
                  <TableCell className="text-sm text-slate-600">{act.legal_entity}</TableCell>
                  <TableCell className="text-sm font-medium">{new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(act.amount)}</TableCell>
                  <TableCell><Badge variant="outline" className={STATUS_COLORS[act.status]}>{act.status}</Badge></TableCell>
                  <TableCell>
                    {act.counterparty_exception ? (
                      act.status !== "Закрыт" && (
                        <Button variant="outline" size="sm" onClick={() => closeAct(act.id)} data-testid={`close-correction-${act.id}`}>
                          <CheckCircle size={12} className="mr-1" />Закрыть
                        </Button>
                      )
                    ) : (
                      <div className="flex gap-1">
                        <Button variant="outline" size="sm" onClick={() => escalateToAccounting(act.id)} data-testid={`escalate-correction-${act.id}`}>
                          <AlertCircle size={12} className="mr-1" />В бухгалтерию
                        </Button>
                        <Button variant="outline" size="sm" onClick={() => resend(act.id)} data-testid={`resend-correction-${act.id}`}>
                          <Send size={12} className="mr-1" />Повторить
                        </Button>
                      </div>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {correctionActs.length === 0 && (
                <TableRow><TableCell colSpan={6} className="text-center py-6 text-slate-400">Нет актов с корректировками</TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>
    </div>
  );
}
