import { Outlet, NavLink, useLocation } from "react-router-dom";
import { LayoutDashboard, FileText, AlertCircle, Clock, Users, Settings } from "lucide-react";

const navItems = [
  { path: "/", label: "Дашборд", icon: LayoutDashboard },
  { path: "/acts", label: "Акты сверки", icon: FileText },
  { path: "/exceptions", label: "Проблемные кейсы", icon: AlertCircle },
  { path: "/accounting", label: "Очередь бухгалтерии", icon: Clock },
  { path: "/counterparties", label: "Контрагенты", icon: Users },
  { path: "/settings", label: "Настройки", icon: Settings },
];

export default function Layout() {
  const location = useLocation();

  return (
    <div className="flex h-screen overflow-hidden" data-testid="app-layout">
      {/* Sidebar */}
      <aside className="w-64 flex-shrink-0 bg-slate-900 flex flex-col" data-testid="sidebar">
        <div className="px-6 py-5 border-b border-slate-700/50">
          <h1 className="text-lg font-bold text-white tracking-tight" style={{ fontFamily: 'Manrope, sans-serif' }}>
            ЭДО Контроль
          </h1>
        </div>
        <nav className="flex-1 px-3 py-4 space-y-1" data-testid="sidebar-nav">
          {navItems.map((item) => {
            const isActive = location.pathname === item.path;
            const Icon = item.icon;
            return (
              <NavLink
                key={item.path}
                to={item.path}
                data-testid={`nav-${item.path.replace("/", "") || "dashboard"}`}
                className={`sidebar-link ${isActive ? "sidebar-link-active" : "sidebar-link-inactive"}`}
              >
                <Icon size={18} />
                <span>{item.label}</span>
              </NavLink>
            );
          })}
        </nav>
      </aside>

      {/* Main Content */}
      <main className="flex-1 overflow-auto bg-slate-50" data-testid="main-content">
        <Outlet />
      </main>
    </div>
  );
}
