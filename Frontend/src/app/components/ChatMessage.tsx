import { User, Bot, AlertTriangle, CheckCircle } from 'lucide-react';
import { HospitalComparison } from './HospitalComparison';
import { BenefitSummary } from './BenefitSummary';

interface ChatMessageProps {
  message: string;
  isUser: boolean;
  data?: {
    specialty?: string;
    priority?: string;
    coverage?: string;
    estimatedCopay?: number;
    recommendedHospital?: string;
    confidence?: number;
    hasRealData?: boolean;
    symptoms?: string;
    doctor?: string;
    deducibleRestante?: number;
    hospitals?: Array<{
      nombre: string;
      distancia: string;
      precioConsulta: number;
      calificacion: number;
      tieneEspecialidad?: boolean;
      enRed?: boolean;
    }>;
    planName?: string;
    probableDiseases?: string[];
    selectedDisease?: string;
    triageSummary?: string;
    aiTraceId?: string;
  };
}

export function ChatMessage({ message, isUser, data }: ChatMessageProps) {
  const getPriorityColor = (priority: string) => {
    switch (priority) {
      case 'Alta':
      case 'alta':
        return 'text-red-500';
      case 'Media':
      case 'media':
        return 'text-yellow-500';
      default:
        return 'text-green-500';
    }
  };

  const getPriorityIcon = (priority: string) => {
    switch (priority) {
      case 'Alta':
      case 'alta':
        return '🔴';
      case 'Media':
      case 'media':
        return '🟡';
      default:
        return '🟢';
    }
  };

  return (
    <div className={`flex gap-3 mb-4 ${isUser ? 'flex-row-reverse' : 'flex-row'}`}>
      <div className={`flex-shrink-0 w-8 h-8 rounded-full flex items-center justify-center ${
        isUser ? 'bg-[#2E86AB]' : 'bg-gradient-to-br from-[#27AE60] to-[#2E86AB]'
      }`}>
        {isUser ? (
          <User className="w-4 h-4 text-white" />
        ) : (
          <Bot className="w-4 h-4 text-white" />
        )}
      </div>

      <div className={`flex-1 max-w-[85%] md:max-w-[70%] ${isUser ? 'items-end' : 'items-start'} flex flex-col`}>
        <div className={`rounded-2xl px-4 py-3 ${
          isUser
            ? 'bg-[#2E86AB] text-white rounded-tr-sm'
            : 'bg-muted text-foreground rounded-tl-sm'
        }`}>
          <p className="break-words whitespace-pre-line">{message}</p>
        </div>

        {!isUser && data && (
          <div className="mt-3 bg-card border border-border rounded-xl p-4 w-full space-y-3">
            {data.hasRealData ? (
              <div className="flex items-center gap-2 mb-2 pb-2 border-b border-border">
                <CheckCircle className="w-4 h-4 text-green-500" />
                <span className="text-sm text-green-600 dark:text-green-400 font-medium">
                  ✅ Datos reales de tu red médica
                </span>
              </div>
            ) : (
              <div className="flex items-center gap-2 mb-2 pb-2 border-b border-border">
                <AlertTriangle className="w-4 h-4 text-purple-500" />
                <span className="text-sm text-purple-600 dark:text-purple-400 font-medium">
                  💭 Sugerencia de IA (no encontrado en tu red)
                </span>
              </div>
            )}

            {data.specialty && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Especialidad:</span>
                <span className="font-medium">🏥 {data.specialty}</span>
              </div>
            )}

            {data.symptoms && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Síntomas:</span>
                <span className="font-medium">{data.symptoms}</span>
              </div>
            )}

            {data.selectedDisease && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Diagnóstico más probable:</span>
                <span className="font-medium">🧠 {data.selectedDisease}</span>
              </div>
            )}

            {data.probableDiseases && data.probableDiseases.length > 0 && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Top 3 enfermedades probables:</span>
                <ul className="text-sm space-y-1 ml-4 list-disc">
                  {data.probableDiseases.map((dx, index) => (
                    <li key={`${dx}-${index}`}>{dx}</li>
                  ))}
                </ul>
              </div>
            )}

            {data.triageSummary && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Resumen de triaje:</span>
                <span className="font-medium">{data.triageSummary}</span>
              </div>
            )}

            {data.priority && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Prioridad:</span>
                <span className={`font-medium ${getPriorityColor(data.priority)}`}>
                  {getPriorityIcon(data.priority)} {data.priority}
                </span>
              </div>
            )}

            {data.confidence !== undefined && (
              <div>
                <span className="text-sm text-muted-foreground block mb-1">✅ Confianza:</span>
                <span className="font-medium">📊 {data.confidence}%</span>
              </div>
            )}

            {data.hasRealData && data.specialty && data.coverage && data.estimatedCopay !== undefined && (
              <>
                {data.doctor && (
                  <div>
                    <span className="text-sm text-muted-foreground block mb-1">👨‍⚕️ Doctor:</span>
                    <span className="font-medium">{data.doctor}</span>
                  </div>
                )}

                {data.planName && data.recommendedHospital && data.deducibleRestante !== undefined && (
                  <BenefitSummary
                    plan={data.planName}
                    coverage={data.coverage}
                    copago={data.estimatedCopay}
                    deducibleRestante={data.deducibleRestante}
                    specialty={data.specialty}
                    hospital={data.recommendedHospital}
                  />
                )}

                {data.hospitals && data.hospitals.length > 0 && (
                  <HospitalComparison
                    hospitals={data.hospitals}
                    specialty={data.specialty}
                    coverage={data.coverage}
                    bestHospital={data.recommendedHospital}
                  />
                )}
              </>
            )}

            {!data.hasRealData && data.specialty && (
              <div className="mt-3 p-4 bg-yellow-50 dark:bg-yellow-900/20 border border-yellow-200 dark:border-yellow-800 rounded-lg">
                <p className="text-sm font-medium text-yellow-800 dark:text-yellow-300 mb-2">
                  💡 Recomendaciones de IA:
                </p>
                <ul className="text-sm text-yellow-700 dark:text-yellow-400 space-y-1 ml-4 list-disc">
                  <li>Acude con un especialista en {data.specialty}</li>
                  <li>Busca atención en centros fuera de tu red</li>
                  <li>Los costos serán sin cobertura de seguro</li>
                  <li>Considera contactar a tu aseguradora para más opciones</li>
                </ul>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
