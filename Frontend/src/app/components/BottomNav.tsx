import { MessageSquare, User, Settings, Hospital } from 'lucide-react';

interface BottomNavProps {
  activeView: 'chat' | 'profile' | 'settings' | 'hospitals';
  onViewChange: (view: 'chat' | 'profile' | 'settings' | 'hospitals') => void;
}

export function BottomNav({ activeView, onViewChange }: BottomNavProps) {
  const items = [
    { id: 'chat' as const, icon: MessageSquare, label: 'Chat' },
    { id: 'hospitals' as const, icon: Hospital, label: 'Hospitales' },
    { id: 'profile' as const, icon: User, label: 'Perfil' },
    { id: 'settings' as const, icon: Settings, label: 'Ajustes' },
  ];

  return (
    <nav className="lg:hidden fixed bottom-0 left-0 right-0 bg-card border-t border-border z-30">
      <div className="flex justify-around items-center h-16 px-2">
        {items.map((item) => (
          <button
            key={item.id}
            onClick={() => onViewChange(item.id)}
            className={`flex flex-col items-center justify-center gap-1 px-3 py-2 rounded-lg transition-colors flex-1 ${
              activeView === item.id
                ? 'text-[#2E86AB]'
                : 'text-muted-foreground'
            }`}
          >
            <item.icon className="w-5 h-5" />
            <span className="text-xs">{item.label}</span>
          </button>
        ))}
      </div>
    </nav>
  );
}
