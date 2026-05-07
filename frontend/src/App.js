import "@/App.css";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import Layout from "@/components/Layout";
import Dashboard from "@/pages/Dashboard";
import ActsRegistry from "@/pages/ActsRegistry";
import Exceptions from "@/pages/Exceptions";
import AccountingQueue from "@/pages/AccountingQueue";
import Counterparties from "@/pages/Counterparties";
import Settings from "@/pages/Settings";
import { Toaster } from "@/components/ui/sonner";

function App() {
  return (
    <div className="App">
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Dashboard />} />
            <Route path="/acts" element={<ActsRegistry />} />
            <Route path="/exceptions" element={<Exceptions />} />
            <Route path="/accounting" element={<AccountingQueue />} />
            <Route path="/counterparties" element={<Counterparties />} />
            <Route path="/settings" element={<Settings />} />
          </Route>
        </Routes>
      </BrowserRouter>
      <Toaster position="top-right" />
    </div>
  );
}

export default App;
