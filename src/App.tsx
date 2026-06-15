import { useState } from 'react';
import { Target, MonitorSmartphone } from 'lucide-react';
import ControllerSession from './components/Controller';
import ClientSession from './components/Client';

function RoleSelection({ onSelect }: { onSelect: (role: 'controller' | 'client') => void }) {
  return (
    <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 p-6 font-sans">
      <div className="max-w-md w-full space-y-8">
        <div className="text-center space-y-3">
           <div className="inline-flex items-center justify-center w-16 h-16 bg-neutral-900 border border-neutral-800 rounded-2xl mb-2">
              <Target className="w-8 h-8 text-neutral-100" />
           </div>
           <h1 className="text-3xl font-medium tracking-tight text-neutral-100">Trapwire Racing</h1>
           <p className="text-neutral-500">Virtual motion detection finish line</p>
        </div>

        <div className="grid gap-4 mt-8">
          <button 
            onClick={() => onSelect('controller')}
            className="group relative overflow-hidden flex flex-col items-center gap-3 bg-neutral-900 hover:bg-neutral-800 border border-neutral-800 rounded-2xl p-8 transition-colors"
          >
             <div className="w-12 h-12 bg-blue-500/10 text-blue-400 group-hover:scale-110 transition-transform rounded-full flex items-center justify-center">
               <MonitorSmartphone className="w-6 h-6" />
             </div>
             <div className="text-center">
                <h3 className="text-lg font-medium text-neutral-200">Controller</h3>
                <p className="text-sm text-neutral-500 mt-1">Create a room and start the race</p>
             </div>
          </button>

          <button 
            onClick={() => onSelect('client')}
             className="group relative overflow-hidden flex flex-col items-center gap-3 bg-neutral-900 hover:bg-neutral-800 border border-neutral-800 rounded-2xl p-8 transition-colors"
          >
             <div className="w-12 h-12 bg-green-500/10 text-green-400 group-hover:scale-110 transition-transform rounded-full flex items-center justify-center">
               <Target className="w-6 h-6" />
             </div>
             <div className="text-center">
                <h3 className="text-lg font-medium text-neutral-200">Client Camera</h3>
                <p className="text-sm text-neutral-500 mt-1">Join room as a trapwire sensor</p>
             </div>
          </button>
        </div>
      </div>
    </div>
  );
}

export default function App() {
  const [role, setRole] = useState<'select' | 'controller' | 'client'>('select');

  if (role === 'controller') {
     return <ControllerSession />;
  }

  if (role === 'client') {
     return <ClientSession />;
  }

  return <RoleSelection onSelect={setRole} />;
}
