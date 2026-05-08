import { Brain, Loader2, CheckCircle, Database, Sparkles } from 'lucide-react';

interface AIAnalysisIndicatorProps {
  stage: 'analyzing' | 'searching' | 'complete';
  confidence?: number;
  hasRealData?: boolean;
}

export function AIAnalysisIndicator({ stage, confidence, hasRealData }: AIAnalysisIndicatorProps) {
  return (
    <div className="bg-gradient-to-r from-purple-50 to-blue-50 dark:from-purple-950/20 dark:to-blue-950/20 border border-purple-200 dark:border-purple-800 rounded-xl p-4">
      {stage === 'analyzing' && (
        <div className="flex items-center gap-3">
          <div className="relative">
            <Brain className="w-6 h-6 text-purple-600 dark:text-purple-400 animate-pulse" />
            <Sparkles className="w-3 h-3 text-purple-400 absolute -top-1 -right-1 animate-pulse" />
          </div>
          <div className="flex-1">
            <p className="font-medium text-purple-900 dark:text-purple-100">� Validando con IA...</p>
            <p className="text-sm text-purple-700 dark:text-purple-300">Conversando con Dr. IA para diagnóstico médico</p>
          </div>
          <Loader2 className="w-5 h-5 text-purple-600 animate-spin" />
        </div>
      )}

      {stage === 'searching' && (
        <div className="flex items-center gap-3">
          <Database className="w-6 h-6 text-blue-600 dark:text-blue-400 animate-pulse" />
          <div className="flex-1">
            <p className="font-medium text-blue-900 dark:text-blue-100">🔍 Buscando en tu red médica...</p>
            <p className="text-sm text-blue-700 dark:text-blue-300">Consultando base de datos de hospitales</p>
          </div>
          <Loader2 className="w-5 h-5 text-blue-600 animate-spin" />
        </div>
      )}

      {stage === 'complete' && confidence !== undefined && (
        <div className="space-y-3">
          <div className="flex items-center gap-3">
            <CheckCircle className="w-6 h-6 text-green-600 dark:text-green-400" />
            <div className="flex-1">
              <p className="font-medium text-green-900 dark:text-green-100">
                {hasRealData ? '✅ Datos reales encontrados' : '💭 Análisis de IA completado'}
              </p>
              <p className="text-sm text-green-700 dark:text-green-300">
                Confianza: 📊 {confidence}%
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2 text-xs">
            {hasRealData ? (
              <span className="px-3 py-1 bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300 rounded-full flex items-center gap-1">
                <Database className="w-3 h-3" />
                Origen: Base de datos real
              </span>
            ) : (
              <span className="px-3 py-1 bg-purple-100 dark:bg-purple-900/30 text-purple-800 dark:text-purple-300 rounded-full flex items-center gap-1">
                <Brain className="w-3 h-3" />
                Origen: Sugerencia de IA
              </span>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
