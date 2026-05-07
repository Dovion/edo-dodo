import { useState, useEffect } from "react";
import axios from "axios";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Card } from "@/components/ui/card";

const API = `${process.env.REACT_APP_BACKEND_URL}/api`;

export default function Counterparties() {
  const [counterparties, setCounterparties] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchData = async () => {
      try {
        const res = await axios.get(`${API}/counterparties`);
        setCounterparties(res.data);
      } catch (e) {
        console.error(e);
      } finally {
        setLoading(false);
      }
    };
    fetchData();
  }, []);

  if (loading) return <div className="p-8 text-slate-400 text-sm">Загрузка...</div>;

  return (
    <div className="p-6 md:p-8 space-y-6" data-testid="counterparties-page">
      <h1 className="text-2xl font-bold tracking-tight text-slate-900" style={{ fontFamily: 'Manrope, sans-serif' }}>
        Контрагенты
      </h1>

      {/* Summary Cards */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <Card className="p-4">
          <p className="text-sm text-slate-500">Всего контрагентов</p>
          <p className="text-2xl font-bold text-slate-900 mt-1">{counterparties.length}</p>
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

      {/* Table */}
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
            </TableRow>
          </TableHeader>
          <TableBody>
            {counterparties.map((cp, i) => (
              <TableRow key={i} className="hover:bg-slate-50" data-testid={`cp-row-${i}`}>
                <TableCell className="text-sm font-medium text-slate-800">{cp.name}</TableCell>
                <TableCell className="text-sm font-mono text-slate-500">{cp.inn}</TableCell>
                <TableCell className="text-sm text-slate-700">{cp.total_acts}</TableCell>
                <TableCell className="text-sm font-medium">
                  {new Intl.NumberFormat("ru-RU", { style: "currency", currency: "RUB", maximumFractionDigits: 0 }).format(cp.total_amount)}
                </TableCell>
                <TableCell className="text-sm text-emerald-600 font-medium">{cp.closed}</TableCell>
                <TableCell className="text-sm text-blue-600 font-medium">{cp.signed}</TableCell>
                <TableCell className="text-sm text-amber-600 font-medium">{cp.pending}</TableCell>
              </TableRow>
            ))}
            {counterparties.length === 0 && (
              <TableRow><TableCell colSpan={7} className="text-center py-6 text-slate-400">Нет данных о контрагентах</TableCell></TableRow>
            )}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
