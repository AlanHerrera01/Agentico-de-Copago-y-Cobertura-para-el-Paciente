import { X, MessageSquare, User, Settings, HelpCircle } from 'lucide-react';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  activeView: 'chat' | 'profile' | 'settings';
  onViewChange: (view: 'chat' | 'profile' | 'settings') => void;
}

export function Sidebar({ isOpen, onClose, activeView, onViewChange }: SidebarProps) {
  const menuItems = [
    { id: 'chat' as const, icon: MessageSquare, label: 'Chat' },
    { id: 'profile' as const, icon: User, label: 'Perfil' },
    { id: 'settings' as const, icon: Settings, label: 'Ajustes' },
  ];

  return (
    <>
      {isOpen && (
        <div
          className="fixed inset-0 bg-black/50 z-40 lg:hidden"
          onClick={onClose}
        />
      )}

      <aside className={`
        fixed lg:sticky top-0 left-0 h-screen w-64 bg-card border-r border-border z-50
        transform transition-transform duration-300 flex flex-col
        ${isOpen ? 'translate-x-0' : '-translate-x-full lg:translate-x-0'}
      `}>
        <div className="p-6 border-b border-border flex items-center justify-between">
          <div>
            <h2 className="font-medium">Agentico Médico</h2>
            <p className="text-sm text-muted-foreground">Copago y Cobertura</p>
          </div>
          <button
            onClick={onClose}
            className="lg:hidden p-2 hover:bg-muted rounded-lg"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          {menuItems.map((item) => (
            <button
              key={item.id}
              onClick={() => {
                onViewChange(item.id);
                if (window.innerWidth < 1024) onClose();
              }}
              className={`w-full flex items-center gap-3 px-4 py-3 rounded-lg transition-colors ${
                activeView === item.id
                  ? 'bg-[#2E86AB] text-white'
                  : 'hover:bg-muted text-foreground'
              }`}
            >
              <item.icon className="w-5 h-5" />
              <span>{item.label}</span>
            </button>
          ))}
        </nav>

        <div className="p-4 border-t border-border">
          <button className="w-full flex items-center gap-3 px-4 py-3 rounded-lg hover:bg-muted transition-colors text-foreground">
            <HelpCircle className="w-5 h-5" />
            <span>Ayuda</span>
          </button>
        </div>
      </aside>
    </>
  );
}
