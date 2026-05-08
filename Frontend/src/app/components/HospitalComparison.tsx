import { TrendingDown, Award, MapPin, Star } from 'lucide-react';

interface Hospital {
  nombre: string;
  distancia: string;
  precioConsulta: number;
  calificacion: number;
  tieneEspecialidad?: boolean;
  enRed?: boolean;
}

interface HospitalComparisonProps {
  hospitals: Hospital[];
  specialty: string;
  coverage: string;
  bestHospital?: string;
}

export function HospitalComparison({ hospitals, specialty, coverage, bestHospital }: HospitalComparisonProps) {
  if (hospitals.length === 0) return null;

  const cheapestHospital = hospitals.reduce((prev, current) =>
    prev.precioConsulta < current.precioConsulta ? prev : current
  );

  return (
    <div className="mt-4 bg-gradient-to-br from-blue-50 to-green-50 dark:from-blue-950/20 dark:to-green-950/20 border border-blue-200 dark:border-blue-800 rounded-xl p-5">
      <div className="flex items-center gap-2 mb-4">
        <Award className="w-5 h-5 text-[#27AE60]" />
        <h4 className="font-medium">🏥 HOSPITALES EN TU RED:</h4>
      </div>

      <div className="space-y-3">
        {hospitals.map((hospital, idx) => {
          const isCheapest = hospital.nombre === cheapestHospital.nombre;
          const isBest = hospital.nombre === bestHospital;

          return (
            <div
              key={idx}
              className={`bg-white dark:bg-gray-900 rounded-lg p-4 border-2 transition-all ${
                isBest
                  ? 'border-[#27AE60] shadow-lg'
                  : 'border-border'
              }`}
            >
              <div className="flex items-start justify-between mb-2">
                <div className="flex-1">
                  <div className="flex items-center gap-2">
                    <h5 className="font-medium">📍 {hospital.nombre}</h5>
                    {isBest && (
                      <span className="text-xs bg-[#27AE60] text-white px-2 py-1 rounded-full flex items-center gap-1">
                        <Award className="w-3 h-3" />
                        RECOMENDADO
                      </span>
                    )}
                  </div>
                  <div className="flex items-center gap-4 mt-2 text-sm text-muted-foreground">
                    <span className="flex items-center gap-1">
                      <MapPin className="w-3 h-3" />
                      📏 {hospital.distancia}
                    </span>
                    <span className="flex items-center gap-1">
                      <Star className="w-3 h-3 fill-yellow-400 text-yellow-400" />
                      {hospital.calificacion.toFixed(1)}
                    </span>
                  </div>
                </div>
              </div>

              <div className="flex items-center justify-between mt-3 pt-3 border-t border-border">
                <div>
                  <p className="text-xs text-muted-foreground">Tu copago:</p>
                  <p className="text-xl font-bold text-[#2E86AB]">
                    ${hospital.precioConsulta}
                  </p>
                  {isCheapest && (
                    <p className="text-xs text-[#27AE60] flex items-center gap-1 mt-1">
                      <TrendingDown className="w-3 h-3" />
                      Más económico
                    </p>
                  )}
                </div>
                <div className="text-right">
                  <p className="text-xs text-muted-foreground">Cobertura:</p>
                  <p className="text-sm font-medium text-[#27AE60]">{coverage}</p>
                </div>
              </div>

              {hospital.tieneEspecialidad && (
                <div className="mt-2 text-xs text-green-600 dark:text-green-400">
                  ✅ Tiene {specialty}
                </div>
              )}
            </div>
          );
        })}
      </div>

      {cheapestHospital && (
        <div className="mt-4 p-3 bg-green-100 dark:bg-green-900/30 rounded-lg border border-green-200 dark:border-green-800">
          <p className="text-sm text-green-800 dark:text-green-300">
            💡 <strong>{cheapestHospital.nombre}</strong> es la opción más económica con un copago de <strong>${cheapestHospital.precioConsulta}</strong>
          </p>
        </div>
      )}
    </div>
  );
}
