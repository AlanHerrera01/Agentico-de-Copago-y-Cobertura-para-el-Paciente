import { useState } from 'react';
import { Loader2, Bot } from 'lucide-react';

interface LoginScreenProps {
  onLogin: (nombreCompleto: string, plan: string) => void;
  isLoading: boolean;
}

export function LoginScreen({ onLogin, isLoading }: LoginScreenProps) {
  const [nombre, setNombre] = useState('');
  const [apellido, setApellido] = useState('');
  const [planSeleccionado, setPlanSeleccionado] = useState('Premium');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (nombre.trim() && apellido.trim()) {
      const nombreCompleto = `${nombre.trim()} ${apellido.trim()}`;
      onLogin(nombreCompleto, planSeleccionado);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gradient-to-br from-[#2E86AB] to-[#27AE60] p-4">
      <div className="w-full max-w-md">
        <div className="bg-card rounded-2xl shadow-2xl p-8 space-y-6">
          <div className="text-center space-y-3">
            <div className="w-20 h-20 mx-auto bg-gradient-to-br from-[#2E86AB] to-[#27AE60] rounded-full flex items-center justify-center">
              <Bot className="w-10 h-10 text-white" />
            </div>
            <h1 className="text-2xl">Agentico de Copago y Cobertura</h1>
            <p className="text-muted-foreground">
              🤖 Tu asistente médico con inteligencia artificial
            </p>
          </div>

          <div className="bg-muted rounded-xl p-6 space-y-3">
            <div className="flex items-start gap-3">
              <Bot className="w-6 h-6 text-[#27AE60] flex-shrink-0 mt-1" />
              <div>
                <p className="text-sm">
                  "Hola, soy tu asistente médico con IA.
                </p>
                <p className="text-sm mt-1">
                  Ingresa tu nombre, apellido y selecciona tu plan de seguro.
                </p>
              </div>
            </div>
          </div>

          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-3">
              <input
                type="text"
                value={nombre}
                onChange={(e) => setNombre(e.target.value)}
                placeholder="Tu nombre"
                disabled={isLoading}
                className="w-full px-4 py-3 bg-input-background border border-border rounded-xl focus:outline-none focus:ring-2 focus:ring-[#2E86AB] disabled:opacity-50 text-center text-lg"
              />
              <input
                type="text"
                value={apellido}
                onChange={(e) => setApellido(e.target.value)}
                placeholder="Tu apellido"
                disabled={isLoading}
                className="w-full px-4 py-3 bg-input-background border border-border rounded-xl focus:outline-none focus:ring-2 focus:ring-[#2E86AB] disabled:opacity-50 text-center text-lg"
              />
              
              <select
                value={planSeleccionado}
                onChange={(e) => setPlanSeleccionado(e.target.value)}
                disabled={isLoading}
                className="w-full px-4 py-3 bg-input-background border border-border rounded-xl focus:outline-none focus:ring-2 focus:ring-[#2E86AB] disabled:opacity-50 text-center text-lg"
              >
                <option value="Premium">Premium</option>
                <option value="Standard">Standard</option>
                <option value="Básico">Básico</option>
              </select>
            </div>

            <button
              type="submit"
              disabled={isLoading || (!nombre.trim() || !apellido.trim())}
              className="w-full px-6 py-3 bg-gradient-to-r from-[#2E86AB] to-[#27AE60] text-white rounded-xl hover:opacity-90 transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
            >
              {isLoading ? (
                <>
                  <Loader2 className="w-5 h-5 animate-spin" />
                  <span>Buscando tu información... 🔍</span>
                </>
              ) : (
                <span>Iniciar Consulta</span>
              )}
            </button>
          </form>
        </div>
      </div>
    </div>
  );
}
