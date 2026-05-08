import { useState } from 'react';
import { Send, Loader2 } from 'lucide-react';

interface ChatInputProps {
  onSendMessage: (message: string) => void;
  isLoading: boolean;
}

export function ChatInput({ onSendMessage, isLoading }: ChatInputProps) {
  const [message, setMessage] = useState('');

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (message.trim() && !isLoading) {
      onSendMessage(message);
      setMessage('');
    }
  };

  return (
    <form onSubmit={handleSubmit} className="flex gap-2">
      <input
        type="text"
        value={message}
        onChange={(e) => setMessage(e.target.value)}
        placeholder="Describe tus síntomas..."
        disabled={isLoading}
        className="flex-1 px-4 py-3 bg-input-background border border-border rounded-xl focus:outline-none focus:ring-2 focus:ring-[#2E86AB] disabled:opacity-50"
      />
      <button
        type="submit"
        disabled={isLoading || !message.trim()}
        className="px-6 py-3 bg-[#2E86AB] text-white rounded-xl hover:bg-[#2E86AB]/90 transition-colors disabled:opacity-50 disabled:cursor-not-allowed flex items-center gap-2"
      >
        {isLoading ? (
          <Loader2 className="w-5 h-5 animate-spin" />
        ) : (
          <Send className="w-5 h-5" />
        )}
        <span className="hidden sm:inline">Enviar</span>
      </button>
    </form>
  );
}
