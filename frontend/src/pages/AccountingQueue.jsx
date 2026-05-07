import { useState, useEffect } from "react";
import axios from "axios";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { CheckCircle, Send } from "lucide-react";
import { toast } from "sonner";

const API = `${process.env.REACT_APP_BACKEND_URL}/api`;

export default function AccountingQueue() {
  const [acts, setActs] = useState([]);
  const [signedActs, setSignedActs] = useState([]);
  const [loading, setLoading] = useState(true);

  const fetchData = async () => {
    try {
      const [accounting, signed] = await Promise.all([
        axios.get(`${API}/acts`, { params: { status: "В работе бухгалтерии", limit: 100 } }),
        axios.get(`${API}/acts`, { params: { status: "Подписан", limit: 100 } }),
      ]);
      setActs(accounting.data.acts);
      setSignedActs(signed.data.acts);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { fetchData(); }, []);

  const closeAct = async (actId) => {
    try {
      await axios.patch(`${API}/acts/${actId}/status`, { status: "Закрыт", comment: "Закрыт бухгалтером" });
      toast.success("Акт закрыт");
      fetchData();
    } catch (e) {
      toast.error("Ошибка");
    }
  };

  const prepareResend = async (actId) => {
    try {
      await axios.patch(`${API}/acts/${actId}/status`, { status: "Готов к отправке", comment: "Подготовлено к повторной отправке после работы бухгалтерии" });
      toast.success("Подготовлено к повторной отправке");
      fetchData();
    } catch (e) {
      toast.error("Ошибка");
    }
  };

  if (loading) return <div className="p-8 text-slate-400 text-sm">Загрузка...</div>;

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="accounting-queue-page">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
        Очередь бухгалтерии
      </h1>

      {/* Signed - ready for accounting */}
      <div className="space-y-3" data-testid="signed-section">
        <div className="flex items-center gap-2">
          <CheckCircle size={18} className="text-emerald-500" />
          <h2 className="text-lg font-semibold text-slate-800">Подписаны — готовы к закрытию ({signedActs.length})</h2>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50">
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Номер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Бухгалтер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">СБИС ID</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {signedActs.map(act => (
                <TableRow key={act.id} className="hover:bg-slate-50" data-testid={`signed-row-${act.id}`}>
                  <TableCell className="text-sm font-medium">{act.act_number}</TableCell>
                  <TableCell className="text-sm">{act.counterparty}</TableCell>
                  <TableCell className="text-sm font-medium">{new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(act.amount)}</TableCell>
                  <TableCell className="text-sm text-slate-600">{act.responsible_accountant}</TableCell>
                  <TableCell className="text-xs font-mono text-emerald-600">{act.saby_send_id || "—"}</TableCell>
                  <TableCell>
                    <Button variant="outline" size="sm" onClick={() => closeAct(act.id)} data-testid={`close-btn-${act.id}`}>
                      <CheckCircle size={12} className="mr-1" />Закрыть
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
              {signedActs.length === 0 && (
                <TableRow><TableCell colSpan={6} className="text-center py-6 text-slate-400">Нет подписанных актов</TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* In accounting work */}
      <div className="space-y-3" data-testid="in-work-section">
        <div className="flex items-center gap-2">
          <Send size={18} className="text-purple-500" />
          <h2 className="text-lg font-semibold text-slate-800">В работе бухгалтерии ({acts.length})</h2>
        </div>
        <div className="bg-white border border-slate-200 rounded-lg overflow-hidden">
          <Table>
            <TableHeader>
              <TableRow className="bg-slate-50">
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Номер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Юрлицо</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Бухгалтер</TableHead>
                <TableHead className="text-xs font-semibold text-slate-500 uppercase">Действия</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {acts.map(act => (
                <TableRow key={act.id} className="hover:bg-slate-50" data-testid={`accounting-row-${act.id}`}>
                  <TableCell className="text-sm font-medium">{act.act_number}</TableCell>
                  <TableCell className="text-sm">{act.counterparty}</TableCell>
                  <TableCell className="text-sm text-slate-600">{act.legal_entity}</TableCell>
                  <TableCell className="text-sm font-medium">{new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(act.amount)}</TableCell>
                  <TableCell className="text-sm text-slate-600">{act.responsible_accountant}</TableCell>
                  <TableCell>
                    <div className="flex gap-1">
                      <Button variant="outline" size="sm" onClick={() => closeAct(act.id)} data-testid={`close-work-btn-${act.id}`}>
                        <CheckCircle size={12} className="mr-1" />Закрыть
                      </Button>
                      <Button variant="outline" size="sm" onClick={() => prepareResend(act.id)} data-testid={`resend-work-btn-${act.id}`}>
                        <Send size={12} className="mr-1" />Повторить
                      </Button>
                    </div>
                  </TableCell>
                </TableRow>
              ))}
              {acts.length === 0 && (
                <TableRow><TableCell colSpan={6} className="text-center py-6 text-slate-400">Нет актов в работе</TableCell></TableRow>
              )}
            </TableBody>
          </Table>
        </div>
      </div>
    </div>
  );
}
