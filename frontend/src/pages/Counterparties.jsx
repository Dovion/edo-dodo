import { useState, useEffect } from "react";
import api from "@/lib/api";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card } from "@/components/ui/card";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { toast } from "sonner";

export default function Counterparties() {
  const [counterparties, setCounterparties] = useState([]);
  const [loading, setLoading] = useState(true);
  const [updatingKey, setUpdatingKey] = useState(null);

  const fetchData = async () => {
    try {
      const res = await api.get(`/counterparties`);
      setCounterparties(res.data);
    } catch (e) {
      console.error(e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const rowKey = (cp) => `${cp.inn || ""}::${cp.name}`;

  const toggleException = async (cp, checked) => {
    const key = rowKey(cp);
    setUpdatingKey(key);
    try {
      await api.patch(`/counterparties/exceptions`, {
        name: cp.name,
        inn: cp.inn || "",
        exception: checked,
      });
      setCounterparties((prev) =>
        prev.map((item) =>
          rowKey(item) === key ? { ...item, exception: checked } : item
        )
      );
      toast.success(checked ? "Контрагент добавлен в исключения" : "Контрагент убран из исключений");
    } catch (e) {
      toast.error(e.response?.data?.detail || "Ошибка сохранения");
    } finally {
      setUpdatingKey(null);
    }
  };

  if (loading) return <div className="p-8 text-slate-400 text-sm">Загрузка...</div>;

  const exceptionCount = counterparties.filter((c) => c.exception).length;

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="counterparties-page">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
        Контрагенты
      </h1>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
        <Card className="p-4">
          <p className="text-sm text-slate-500">Всего контрагентов</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{counterparties.length}</p>
        </Card>
        <Card className="p-4">
          <p className="text-sm text-slate-500">В исключениях</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{exceptionCount}</p>
        </Card>
        <Card className="p-4">
          <p className="text-sm text-slate-500">Общая сумма актов</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">
            {new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(
              counterparties.reduce((sum, c) => sum + c.total_amount, 0)
            )}
          </p>
        </Card>
        <Card className="p-4">
          <p className="text-sm text-slate-500">С незакрытыми актами</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{counterparties.filter(c => c.pending > 0).length}</p>
        </Card>
      </div>

      <div className="bg-white border border-slate-200 rounded-lg overflow-hidden" data-testid="counterparties-table">
        <Table>
          <TableHeader>
            <TableRow className="bg-slate-50">
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Контрагент</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">ИНН</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Всего актов</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Сумма</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Закрыто</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">Подписано</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase">В ожидании</TableHead>
              <TableHead className="text-xs font-semibold text-slate-500 uppercase w-[140px]">Исключения</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {counterparties.map((cp, i) => (
              <TableRow key={rowKey(cp)} className="hover:bg-slate-50" data-testid={`cp-row-${i}`}>
                <TableCell className="text-sm font-medium text-slate-800">{cp.name}</TableCell>
                <TableCell className="text-sm font-mono text-slate-500">{cp.inn}</TableCell>
                <TableCell className="text-sm text-slate-700">{cp.total_acts}</TableCell>
                <TableCell className="text-sm font-medium">
                  {new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(cp.total_amount)}
                </TableCell>
                <TableCell className="text-sm text-emerald-600 font-medium">{cp.closed}</TableCell>
                <TableCell className="text-sm text-blue-600 font-medium">{cp.signed}</TableCell>
                <TableCell className="text-sm text-amber-600 font-medium">{cp.pending}</TableCell>
                <TableCell>
                  <div className="flex items-center gap-2">
                    <Checkbox
                      id={`exception-${i}`}
                      checked={!!cp.exception}
                      disabled={updatingKey === rowKey(cp)}
                      onCheckedChange={(checked) => toggleException(cp, checked === true)}
                      data-testid={`exception-checkbox-${i}`}
                    />
                    <Label htmlFor={`exception-${i}`} className="text-sm text-slate-600 cursor-pointer">
                      Исключения
                    </Label>
                  </div>
                </TableCell>
              </TableRow>
            ))}
            {counterparties.length === 0 && (
              <TableRow><TableCell colSpan={8} className="text-center py-6 text-slate-400">Нет данных о контрагентах</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
