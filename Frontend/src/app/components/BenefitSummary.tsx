import { Shield, DollarSign, TrendingDown, CheckCircle } from 'lucide-react';

interface BenefitSummaryProps {
  plan: string;
  coverage: string;
  copago: number;
  deducibleRestante: number;
  specialty: string;
  hospital: string;
}

export function BenefitSummary({ plan, coverage, copago, deducibleRestante, specialty, hospital }: BenefitSummaryProps) {
  return (
    <div className="bg-gradient-to-br from-[#2E86AB]/10 to-[#27AE60]/10 border-2 border-[#2E86AB] rounded-xl p-5 space-y-4">
      <div className="flex items-center gap-2 mb-3">
        <Shield className="w-6 h-6 text-[#2E86AB]" />
        <h4 className="font-medium text-lg">📋 Resumen de tu Beneficio</h4>
      </div>

      <div className="bg-white dark:bg-gray-900 rounded-lg p-4 space-y-3">
        <div className="flex items-start gap-3">
          <CheckCircle className="w-5 h-5 text-[#27AE60] flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Plan de seguro</p>
            <p className="font-medium">{plan}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <CheckCircle className="w-5 h-5 text-[#27AE60] flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Especialidad necesaria</p>
            <p className="font-medium">🏥 {specialty}</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <CheckCircle className="w-5 h-5 text-[#27AE60] flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Tu cobertura</p>
            <p className="font-medium text-[#27AE60]">{coverage} del costo</p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <DollarSign className="w-5 h-5 text-[#2E86AB] flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Copago en {hospital}</p>
            <p className="text-2xl font-bold text-[#2E86AB]">${copago}</p>
            <p className="text-xs text-muted-foreground mt-1">
              El seguro cubre el resto
            </p>
          </div>
        </div>

        <div className="flex items-start gap-3">
          <TrendingDown className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Deducible restante</p>
            <p className="font-medium">${deducibleRestante}</p>
          </div>
        </div>
      </div>

      <div className="bg-[#27AE60]/10 border border-[#27AE60] rounded-lg p-3">
        <p className="text-sm text-center">
          💰 <strong>Total a pagar HOY:</strong> <span className="text-xl font-bold text-[#27AE60]">${copago}</span>
        </p>
      </div>
    </div>
  );
}
