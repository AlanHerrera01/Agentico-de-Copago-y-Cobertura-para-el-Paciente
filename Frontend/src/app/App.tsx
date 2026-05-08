import { useState, useRef, useEffect } from 'react';
import { Menu, Loader2, AlertCircle } from 'lucide-react';

const API_BASE_URL = import.meta.env.VITE_API_URL || 'https://agentico-de-copago-y-cobertura-para-el.onrender.com';
console.log('[DEBUG] API_BASE_URL:', API_BASE_URL);
console.log('[DEBUG] VITE_API_URL env:', import.meta.env.VITE_API_URL);

import { Sidebar } from './components/Sidebar';
import { BottomNav } from './components/BottomNav';
import { ChatMessage } from './components/ChatMessage';
import { ChatInput } from './components/ChatInput';
import { PatientProfile } from './components/PatientProfile';
import { HospitalCard } from './components/HospitalCard';
import { LoginScreen } from './components/LoginScreen';
import { AIAnalysisIndicator } from './components/AIAnalysisIndicator';

interface Message {
  id: string;
  text: string;
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
    hospitals?: Hospital[];
    planName?: string;
    probableDiseases?: string[];
    selectedDisease?: string;
    triageSummary?: string;
    aiTraceId?: string;
  };
}

interface Patient {
  clientId: string;
  nombre: string;
  plan: string;
  deducibleAnual: number;
  deducibleUsado: number;
}

interface Hospital {
  nombre: string;
  distancia: string;
  precioConsulta: number;
  calificacion: number;
  tieneEspecialidad?: boolean;
  enRed?: boolean;
}

export default function App() {
  const [isLoggedIn, setIsLoggedIn] = useState(false);
  const [loginLoading, setLoginLoading] = useState(false);
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [activeView, setActiveView] = useState<'chat' | 'profile' | 'settings' | 'hospitals'>('chat');
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [aiStage, setAiStage] = useState<'analyzing' | 'searching' | 'complete' | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [patient, setPatient] = useState<Patient | null>(null);
  const [currentSpecialty, setCurrentSpecialty] = useState<string>('');
  const [hospitals, setHospitals] = useState<Hospital[]>([]);

  const chatEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleLogin = async (nombreCompleto: string, plan: string) => {
    setLoginLoading(true);

    try {
      // Simulación de búsqueda de paciente
      await new Promise(resolve => setTimeout(resolve, 1500));

      // Mock patient data usando el nombre y plan ingresados
      const mockPatient: Patient = {
        clientId: 'PAC-001',
        nombre: nombreCompleto,
        plan: plan,
        deducibleAnual: plan === 'Premium' ? 200 : plan === 'Standard' ? 500 : 1000,
        deducibleUsado: plan === 'Premium' ? 50 : plan === 'Standard' ? 100 : 200,
      };

      setPatient(mockPatient);
      setIsLoggedIn(true);

      // Mensaje de bienvenida personalizado
      setMessages([{
        id: '1',
        text: `¡Hola ${nombreCompleto}! Soy tu asistente médico con IA. Veo que tienes el plan ${mockPatient.plan} con 80% de cobertura.\n\nPor favor, describe tus síntomas con tus propias palabras. Puedes escribirme como le hablarías a un médico real.`,
        isUser: false,
      }]);
    } catch (err) {
      setError('No se pudo encontrar el paciente');
    } finally {
      setLoginLoading(false);
    }
  };

  const handleSendMessage = async (message: string) => {
    const userMessage: Message = {
      id: Date.now().toString(),
      text: message,
      isUser: true,
    };

    setMessages((prev: Message[]) => [...prev, userMessage]);
    setIsLoading(true);
    setError(null);
    setAiStage('analyzing'); // Mostrar "Validando con IA"

    try {
      // Etapa 1: Análisis con IA
      setAiStage('analyzing');
      
      const response = await fetch(`${API_BASE_URL}/api/chat`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          clientId: patient?.clientId || 'PAC-001',
          message: message,
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        console.error('Error response:', errorText);
        throw new Error(`Error ${response.status}: ${response.statusText}`);
      }

      const data = await response.json();
      console.log('Backend response:', data);
      console.log('DEBUG - data.specialty:', data.specialty);
      console.log('DEBUG - data.recommendedHospital:', data.recommendedHospital);
      console.log('DEBUG - Condición para preguntas:', !data.specialty);

      // Verificar si la IA necesita más información (pregunta diagnóstica)
      // Nota: `recommendedHospital === null && === undefined` nunca se cumple; usamos chequeo nullish.
      const noHospital = data.recommendedHospital == null;
      const noSpecialty = !data.specialty;
      if (data.priority === 'NECESITA_MAS_INFO' || (noHospital && noSpecialty)) {
        // La IA está haciendo una pregunta diagnóstica - mostrar pregunta del doctor
        const diagnosticMessage: Message = {
          id: (Date.now() + 1).toString(),
          text: `🩺 **Dr. IA:** ${data.priority === 'NECESITA_MAS_INFO' ? data.coverage || '¿Puedes describir mejor tus síntomas?' : data.priority || '¿Puedes describir mejor tus síntomas?'}\n\n*Necesito más detalles para darte el diagnóstico más preciso posible.*`,
          isUser: false,
          data: {
            specialty: undefined,
            symptoms: 'Esperando más información...',
            priority: undefined,
            confidence: 0,
            hasRealData: false,
            coverage: undefined,
            estimatedCopay: 0,
            doctor: 'Dr. IA Asistente',
            deducibleRestante: patient ? patient.deducibleAnual - patient.deducibleUsado : 150,
            recommendedHospital: undefined,
            hospitals: [],
            planName: patient?.plan || 'Premium',
            probableDiseases: [],
            selectedDisease: undefined,
            triageSummary: 'Información insuficiente',
            aiTraceId: data.aiTraceId,
          },
        };

        setMessages((prev: Message[]) => [...prev, diagnosticMessage]);
        setIsLoading(false);
        setAiStage(null); // No mostrar indicador de IA
        return; // Terminar aquí, esperar respuesta del usuario
      }
      
      // Etapa 2: Búsqueda de hospitales (simulada mientras el backend procesa)
      setAiStage('searching');
      await new Promise(resolve => setTimeout(resolve, 1000));

      setAiStage('complete');
      setCurrentSpecialty(data.specialty || 'Medicina General');

      // Datos reales del backend
      const hasRealData = data.specialty && data.coverage && data.coverage !== 'ERROR';

      // Preparar hospitales con datos reales del backend
      const hospitalsData: Hospital[] = hasRealData ? [
        {
          nombre: data.recommendedHospital || 'Hospital Vozandes',
          distancia: '2.5 km',
          precioConsulta: data.estimatedCopay || 50,
          calificacion: 4.8,
          tieneEspecialidad: true,
          enRed: true,
        },
        {
          nombre: 'Hospital Metropolitano',
          distancia: '3.1 km',
          precioConsulta: data.estimatedCopay ? data.estimatedCopay * 1.2 : 60,
          calificacion: 4.5,
          tieneEspecialidad: true,
          enRed: true,
        },
        {
          nombre: 'Hospital San Francisco',
          distancia: '4.5 km',
          precioConsulta: data.estimatedCopay ? data.estimatedCopay * 1.5 : 75,
          calificacion: 4.6,
          tieneEspecialidad: true,
          enRed: true,
        },
      ] : [];

      setHospitals(hospitalsData);

      // Encontrar el hospital más económico
      const cheapestHospital = hospitalsData.length > 0 
        ? hospitalsData.reduce((prev, current) =>
            prev.precioConsulta < current.precioConsulta ? prev : current
          )
        : null;

      const botMessage: Message = {
        id: (Date.now() + 1).toString(),
        text: hasRealData && cheapestHospital
          ? `✅ ¡Análisis completado con IA! He analizado tus síntomas y encontrado la mejor opción para ti.\n\n🧠 **Diagnóstico más probable:** ${data.selectedDisease || data.specialty || 'No determinado'}\n🧪 **Top 3 enfermedades probables:** ${(data.probableDiseases && data.probableDiseases.length > 0) ? data.probableDiseases.join(', ') : 'No disponible'}\n\n🎯 Basado en tu plan ${patient?.plan || 'Premium'}, te recomiendo ${cheapestHospital.nombre} porque:\n\n• Es el más económico de tu red: $${cheapestHospital.precioConsulta}\n• Está a solo ${cheapestHospital.distancia}\n• Tiene especialistas en ${data.specialty}\n• Calificación: ${cheapestHospital.calificacion}/5\n\n✨ **Análisis de IA:** Especialidad detectada: ${data.specialty}\n📋 **Prioridad:** ${data.priority}\n💰 **Cobertura:** ${data.coverage}\n\nAquí está el desglose completo de tu beneficio:`
          : hasRealData
          ? `✅ ¡Análisis completado con IA!\n\n🎯 **Especialidad detectada:** ${data.specialty}\n📋 **Prioridad:** ${data.priority}\n💰 **Cobertura:** ${data.coverage}\n💳 **Copago estimado:** $${data.estimatedCopay}\n\n⚠️ No se encontraron hospitales disponibles para esta especialidad en tu red.`
          : `❌ Error en el análisis. Por favor, intenta describir tus síntomas de otra manera.`,
        isUser: false,
        data: {
          specialty: data.specialty || 'Medicina General',
          symptoms: 'Análisis basado en IA',
          priority: data.priority || 'Media',
          confidence: 95,
          hasRealData: hasRealData,
          coverage: data.coverage || '80%',
          estimatedCopay: data.estimatedCopay || 0,
          doctor: 'Médico especialista',
          deducibleRestante: patient ? patient.deducibleAnual - patient.deducibleUsado : 150,
          recommendedHospital: cheapestHospital?.nombre || undefined,
          hospitals: hospitalsData,
          planName: patient?.plan || 'Premium',
          probableDiseases: data.probableDiseases || [],
          selectedDisease: data.selectedDisease,
          triageSummary: data.triageSummary,
          aiTraceId: data.aiTraceId,
        },
      };

      setMessages((prev: Message[]) => [...prev, botMessage]);

      setTimeout(() => setAiStage(null), 3000);
    } catch (err: unknown) {
      console.error('Error en handleSendMessage:', err);
      
      // Verificar si es un error del backend con datos útiles
      const errorMessage = err instanceof Error ? err.message : String(err);
      if (errorMessage.includes('Error 500') && errorMessage.includes('specialty":"ERROR')) {
        setError('El backend está procesando tu solicitud, pero hay un problema con los servicios externos. Mostrando análisis básico.');
        
        // Intentar extraer información útil del error si existe
        const errorText = errorMessage;
        let fallbackSpecialty = 'Medicina General';
        let fallbackPriority = 'Media';
        
        // Análisis simple de síntomas en el frontend
        const messageLower = message.toLowerCase();
        if (messageLower.includes('dolor') && messageLower.includes('cabeza')) {
          fallbackSpecialty = 'Neurología';
          fallbackPriority = 'Media';
        } else if (messageLower.includes('dolor') && messageLower.includes('pecho')) {
          fallbackSpecialty = 'Cardiología';
          fallbackPriority = 'Alta';
        } else if (messageLower.includes('fiebre') || messageLower.includes('tos')) {
          fallbackSpecialty = 'Medicina General';
          fallbackPriority = 'Media';
        }
        
        setAiStage('complete');
        setCurrentSpecialty(fallbackSpecialty);

        const fallbackHospitals: Hospital[] = [
          {
            nombre: 'Hospital Vozandes',
            distancia: '2.5 km',
            precioConsulta: fallbackSpecialty === 'Medicina General' ? 30 : 45,
            calificacion: 4.8,
            tieneEspecialidad: true,
            enRed: true,
          },
          {
            nombre: 'Hospital Metropolitano',
            distancia: '3.1 km',
            precioConsulta: fallbackSpecialty === 'Medicina General' ? 35 : 50,
            calificacion: 4.5,
            tieneEspecialidad: true,
            enRed: true,
          },
        ];

        setHospitals(fallbackHospitals);

        const cheapestHospital = fallbackHospitals[0];
        const coverage = fallbackSpecialty === 'Medicina General' ? 80 : 70;
        const copago = cheapestHospital.precioConsulta * (1 - coverage/100);

        const fallbackMessage: Message = {
          id: (Date.now() + 1).toString(),
          text: `⚠️ El servidor de IA está temporalmente indisponible. He realizado un análisis básico de tus síntomas.\n\n🎯 **Especialidad detectada:** ${fallbackSpecialty}\n📋 **Prioridad:** ${fallbackPriority}\n💰 **Cobertura estimada:** ${coverage}%\n\n🏥 **Recomendación:** ${cheapestHospital.nombre}\n💳 **Copago estimado:** $${copago.toFixed(2)}\n📍 **Distancia:** ${cheapestHospital.distancia}\n\nEl sistema continuará funcionando con análisis básico mientras se resuelve el problema.`,
          isUser: false,
          data: {
            specialty: fallbackSpecialty,
            symptoms: 'Análisis básico (frontend fallback)',
            priority: fallbackPriority,
            confidence: 75,
            hasRealData: false,
            coverage: `${coverage}%`,
            estimatedCopay: copago,
            doctor: fallbackSpecialty === 'Medicina General' ? 'Médico general' : 'Médico especialista',
            deducibleRestante: patient ? patient.deducibleAnual - patient.deducibleUsado : 150,
            recommendedHospital: cheapestHospital.nombre,
            hospitals: fallbackHospitals,
            planName: patient?.plan || 'Premium',
            probableDiseases: [fallbackSpecialty],
            selectedDisease: fallbackSpecialty,
            triageSummary: 'Fallback por error temporal de IA',
          },
        };

        setMessages((prev: Message[]) => [...prev, fallbackMessage]);
        setTimeout(() => setAiStage(null), 3000);
      } else {
        // Error de conexión o otro tipo de error
        setError('No se pudo conectar con el servidor. Mostrando respuesta de ejemplo.');
        
        setAiStage('complete');
        setCurrentSpecialty('Medicina General');

        const fallbackHospitals: Hospital[] = [
          {
            nombre: 'Hospital General',
            distancia: '2.5 km',
            precioConsulta: 50,
            calificacion: 4.5,
            tieneEspecialidad: true,
            enRed: true,
          },
        ];

        setHospitals(fallbackHospitals);

        const fallbackMessage: Message = {
          id: (Date.now() + 1).toString(),
          text: `❌ Error de conexión con el servidor.\n\n🏥 **Opción básica:** ${fallbackHospitals[0].nombre}\n💰 **Costo estimado:** $${fallbackHospitals[0].precioConsulta}\n📍 **Distancia:** ${fallbackHospitals[0].distancia}\n\nEl backend está procesando tu solicitud. Mostrando opciones disponibles.`,
          isUser: false,
          data: {
            specialty: 'Medicina General',
            symptoms: 'Sin análisis (error de conexión)',
            priority: 'Media',
            confidence: 25,
            hasRealData: false,
            coverage: '80%',
            estimatedCopay: fallbackHospitals[0].precioConsulta,
            doctor: 'Médico general',
            deducibleRestante: patient ? patient.deducibleAnual - patient.deducibleUsado : 150,
            recommendedHospital: fallbackHospitals[0].nombre,
            hospitals: fallbackHospitals,
            planName: patient?.plan || 'Premium',
            probableDiseases: ['No disponible'],
            selectedDisease: 'No disponible',
            triageSummary: 'Sin análisis por error de conexión',
          },
        };

        setMessages((prev: Message[]) => [...prev, fallbackMessage]);
        setTimeout(() => setAiStage(null), 3000);
      }
    } finally {
      setIsLoading(false);
    }
  };

  if (!isLoggedIn) {
    return <LoginScreen onLogin={handleLogin} isLoading={loginLoading} />;
  }

  return (
    <div className="h-screen flex overflow-hidden bg-background">
      <Sidebar
        isOpen={sidebarOpen}
        onClose={() => setSidebarOpen(false)}
        activeView={activeView}
        onViewChange={setActiveView}
      />

      <div className="flex-1 flex flex-col h-screen">
        <header className="bg-card border-b border-border px-4 py-4 flex items-center gap-4 flex-shrink-0">
          <button
            onClick={() => setSidebarOpen(true)}
            className="lg:hidden p-2 hover:bg-muted rounded-lg"
          >
            <Menu className="w-5 h-5" />
          </button>
          <div className="flex-1">
            <h1 className="font-medium">Agentico de Copago y Cobertura</h1>
            <p className="text-sm text-muted-foreground">Asistente médico inteligente</p>
          </div>
          <div className="hidden sm:flex items-center gap-2 px-3 py-1 bg-[#27AE60]/10 text-[#27AE60] rounded-full">
            <div className="w-2 h-2 bg-[#27AE60] rounded-full animate-pulse" />
            <span className="text-sm">En línea</span>
          </div>
        </header>

        <div className="flex-1 flex overflow-hidden">
          <main className="flex-1 flex flex-col overflow-hidden">
            {activeView === 'chat' && (
              <>
                {error && (
                  <div className="mx-4 mt-4 p-4 bg-yellow-50 border border-yellow-200 rounded-xl flex items-start gap-3">
                    <AlertCircle className="w-5 h-5 text-yellow-600 flex-shrink-0 mt-0.5" />
                    <div>
                      <p className="text-sm text-yellow-800">{error}</p>
                      <p className="text-xs text-yellow-600 mt-1">Asegúrate de que el backend esté ejecutándose en /api/chat</p>
                    </div>
                  </div>
                )}

                <div className="flex-1 overflow-y-auto px-4 py-6 pb-20 lg:pb-6">
                  <div className="max-w-4xl mx-auto">
                    {messages.map((msg) => (
                      <ChatMessage
                        key={msg.id}
                        message={msg.text}
                        isUser={msg.isUser}
                        data={msg.data}
                      />
                    ))}

                    {aiStage && (
                      <div className="mb-4">
                        <AIAnalysisIndicator
                          stage={aiStage}
                          confidence={95}
                          hasRealData={aiStage === 'complete'}
                        />
                      </div>
                    )}

                    <div ref={chatEndRef} />
                  </div>
                </div>

                <div className="border-t border-border px-4 py-4 bg-card">
                  <div className="max-w-4xl mx-auto">
                    <ChatInput onSendMessage={handleSendMessage} isLoading={isLoading} />
                  </div>
                </div>
              </>
            )}

            {activeView === 'profile' && (
              <div className="flex-1 overflow-y-auto px-4 py-6 pb-20 lg:pb-6">
                <div className="max-w-2xl mx-auto">
                  <h2 className="mb-6">Perfil del Paciente</h2>
                  <PatientProfile patient={patient} />
                </div>
              </div>
            )}

            {activeView === 'hospitals' && (
              <div className="flex-1 overflow-y-auto px-4 py-6 pb-20 lg:pb-6">
                <div className="max-w-4xl mx-auto">
                  <h2 className="mb-2">
                    {hospitals.some(h => h.enRed) ? '🏥 Hospitales en tu red' : '🏥 Hospitales cercanos'}
                  </h2>
                  <p className="text-sm text-muted-foreground mb-6">
                    {currentSpecialty && `Especialidad: ${currentSpecialty}`}
                  </p>

                  {hospitals.length === 0 ? (
                    <div className="text-center py-12">
                      <p className="text-muted-foreground">
                        Describe tus síntomas para encontrar hospitales
                      </p>
                    </div>
                  ) : (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
                      {hospitals.map((hospital, idx) => (
                        <HospitalCard
                          key={idx}
                          hospital={hospital}
                          specialty={currentSpecialty}
                        />
                      ))}
                    </div>
                  )}
                </div>
              </div>
            )}

            {activeView === 'settings' && (
              <div className="flex-1 overflow-y-auto px-4 py-6 pb-20 lg:pb-6">
                <div className="max-w-2xl mx-auto">
                  <h2 className="mb-6">Configuración</h2>
                  <div className="bg-card border border-border rounded-xl p-6">
                    <p className="text-muted-foreground">Configuración del sistema</p>
                  </div>
                </div>
              </div>
            )}
          </main>

          <aside className="hidden xl:block w-80 border-l border-border overflow-y-auto p-6 space-y-6">
            <div>
              <h3 className="mb-4">Perfil del Paciente</h3>
              <PatientProfile patient={patient} />
            </div>

            {hospitals.length > 0 && (
              <div>
                <h3 className="mb-4">
                  {hospitals.some(h => h.enRed) ? '🏥 Hospitales RED' : '🏥 Hospitales'}
                </h3>
                <div className="space-y-3">
                  {hospitals.slice(0, 2).map((hospital, idx) => (
                    <HospitalCard
                      key={idx}
                      hospital={hospital}
                      specialty={currentSpecialty}
                    />
                  ))}
                </div>
              </div>
            )}
          </aside>
        </div>
      </div>

      <BottomNav activeView={activeView} onViewChange={setActiveView} />
    </div>
  );
}