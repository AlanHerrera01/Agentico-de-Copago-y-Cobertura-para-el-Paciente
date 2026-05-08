import { MapPin, DollarSign, Star, Phone, Navigation, CheckCircle } from 'lucide-react';

interface HospitalCardProps {
  hospital: {
    nombre: string;
    distancia: string;
    precioConsulta: number;
    calificacion: number;
    tieneEspecialidad?: boolean;
    enRed?: boolean;
  };
  specialty?: string;
  onClick?: () => void;
}

export function HospitalCard({ hospital, specialty, onClick }: HospitalCardProps) {
  return (
    <div
      className={`bg-card border rounded-xl p-4 hover:shadow-lg transition-all ${
        hospital.enRed
          ? 'border-green-200 dark:border-green-800'
          : 'border-border'
      }`}
    >
      <div className="flex items-start justify-between mb-3">
        <h3 className="font-medium flex-1">📍 {hospital.nombre}</h3>
        {hospital.enRed && (
          <span className="text-xs bg-green-100 dark:bg-green-900/30 text-green-700 dark:text-green-300 px-2 py-1 rounded-full">
            ✓ En red
          </span>
        )}
      </div>

      <div className="space-y-2 mb-3">
        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <MapPin className="w-4 h-4" />
          <span>📏 {hospital.distancia}</span>
        </div>

        <div className="flex items-center gap-2 text-sm">
          <Star className="w-4 h-4 fill-yellow-400 text-yellow-400" />
          <span>{'⭐'.repeat(Math.round(hospital.calificacion))}</span>
        </div>

        <div className="flex items-center gap-2 text-sm text-muted-foreground">
          <DollarSign className="w-4 h-4" />
          <span className="font-medium text-foreground">
            💰 {hospital.enRed ? `Copago: $${hospital.precioConsulta}` : `$${hospital.precioConsulta} (sin cobertura)`}
          </span>
        </div>

        {hospital.tieneEspecialidad && specialty && (
          <div className="flex items-center gap-2 text-sm text-green-600 dark:text-green-400">
            <CheckCircle className="w-4 h-4" />
            <span>🏥 ✅ Tiene {specialty}</span>
          </div>
        )}
      </div>

      <div className="flex gap-2 pt-3 border-t border-border">
        <button
          onClick={onClick}
          className="flex-1 px-3 py-2 bg-[#2E86AB] text-white rounded-lg hover:bg-[#2E86AB]/90 transition-colors text-sm flex items-center justify-center gap-2"
        >
          <Phone className="w-4 h-4" />
          📞 Llamar
        </button>
        <button className="flex-1 px-3 py-2 border border-border rounded-lg hover:bg-muted transition-colors text-sm flex items-center justify-center gap-2">
          <Navigation className="w-4 h-4" />
          🗺️ Ir
        </button>
      </div>
    </div>
  );
}
