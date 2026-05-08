import { User, CreditCard, Activity } from 'lucide-react';

interface PatientProfileProps {
  patient: {
    clientId: string;
    nombre: string;
    plan: string;
    deducibleAnual: number;
    deducibleUsado: number;
  } | null;
}

export function PatientProfile({ patient }: PatientProfileProps) {
  if (!patient) {
    return (
      <div className="bg-card border border-border rounded-xl p-6 text-center">
        <User className="w-12 h-12 mx-auto mb-3 text-muted-foreground" />
        <p className="text-muted-foreground">No hay paciente seleccionado</p>
      </div>
    );
  }

  const deducibleRestante = patient.deducibleAnual - patient.deducibleUsado;
  const porcentajeUsado = (patient.deducibleUsado / patient.deducibleAnual) * 100;

  return (
    <div className="bg-card border border-border rounded-xl p-6 space-y-4">
      <div className="flex items-center gap-4">
        <div className="w-16 h-16 rounded-full bg-gradient-to-br from-[#2E86AB] to-[#27AE60] flex items-center justify-center">
          <User className="w-8 h-8 text-white" />
        </div>
        <div>
          <h3 className="font-medium">{patient.nombre}</h3>
          <p className="text-sm text-muted-foreground">{patient.clientId}</p>
        </div>
      </div>

      <div className="space-y-3 pt-4 border-t border-border">
        <div className="flex items-center gap-3">
          <CreditCard className="w-5 h-5 text-[#2E86AB]" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Plan</p>
            <p className="font-medium">{patient.plan}</p>
          </div>
        </div>

        <div className="flex items-center gap-3">
          <Activity className="w-5 h-5 text-[#27AE60]" />
          <div className="flex-1">
            <p className="text-sm text-muted-foreground">Deducible</p>
            <p className="font-medium">${patient.deducibleUsado} / ${patient.deducibleAnual}</p>
          </div>
        </div>

        <div className="space-y-2">
          <div className="flex justify-between text-sm">
            <span className="text-muted-foreground">Restante</span>
            <span className="font-medium text-[#27AE60]">${deducibleRestante}</span>
          </div>
          <div className="w-full h-2 bg-muted rounded-full overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-[#2E86AB] to-[#27AE60] transition-all"
              style={{ width: `${porcentajeUsado}%` }}
            />
          </div>
        </div>
      </div>
    </div>
  );
}
