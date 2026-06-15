import { useState, useEffect, useRef } from 'react';
import { ref, set, onValue, update, onDisconnect } from 'firebase/database';
import { rtdb } from '../firebase';
import { Session, ClientData } from '../types';
import { playBeep, unlockAudio } from '../lib/audio';
import { Play, Users, Clock, AlertCircle, Sliders, Sun } from 'lucide-react';

export default function ControllerSession() {
  const [code, setCode] = useState('123');
  const [sessionActive, setSessionActive] = useState(false);
  const [clients, setClients] = useState<({ id: string } & ClientData)[]>([]);
  const [sessionData, setSessionData] = useState<Session | null>(null);
  const [serverOffset, setServerOffset] = useState<number>(0);

  const [wakeLockActive, setWakeLockActive] = useState(false);
  const [wakeLockSupported, setWakeLockSupported] = useState(true);
  const wakeLockRef = useRef<any>(null);

  // Monitor server time offset
  useEffect(() => {
    const offsetRef = ref(rtdb, ".info/serverTimeOffset");
    const unsubOffset = onValue(offsetRef, (snapshot) => {
      if (snapshot.exists()) {
        setServerOffset(snapshot.val() as number);
      }
    });
    return () => unsubOffset();
  }, []);

  const createSession = async () => {
    if (code.length !== 3) return;
    try {
      unlockAudio();
      const sessionRef = ref(rtdb, `sessions/${code}`);
      await set(sessionRef, {
        status: 'waiting',
        createdAt: Date.now(),
        sensitivity: 70, // Default motion sensitivity to 70%
      });
      await onDisconnect(sessionRef).remove();
      setSessionActive(true);
    } catch (e) {
      console.error(e);
      alert('Failed to create session. See console.');
    }
  };

  useEffect(() => {
    if (!sessionActive || !code) return;

    // Listen to session
    const unsubSession = onValue(ref(rtdb, `sessions/${code}`), (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.val();
        setSessionData({
          status: data.status,
          createdAt: data.createdAt,
          startTime: data.startTime,
          sensitivity: data.sensitivity ?? 70,
        });
      }
    });

    // Listen to clients
    const unsubClients = onValue(ref(rtdb, `sessions/${code}/clients`), (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.val();
        const cls = Object.keys(data).map(key => ({ id: key, ...data[key] }));
        setClients(cls.sort((a,b) => a.joinedAt - b.joinedAt));
      } else {
        setClients([]);
      }
    });

    return () => {
      unsubSession();
      unsubClients();
    };
  }, [sessionActive, code]);

  // Screen Wake Lock control
  useEffect(() => {
    if (!sessionActive) return;

    if (!('wakeLock' in navigator)) {
      setWakeLockSupported(false);
      return;
    }

    async function requestWakeLock() {
      try {
        if (wakeLockRef.current) {
          try {
            await wakeLockRef.current.release();
          } catch (_) {}
        }
        const lock = await navigator.wakeLock.request('screen');
        wakeLockRef.current = lock;
        setWakeLockActive(true);
        console.log("Controller Screen Wake Lock acquired successfully.");

        lock.addEventListener('release', () => {
          setWakeLockActive(false);
        });
      } catch (err) {
        console.warn("Controller Screen Wake Lock request failed:", err);
        setWakeLockActive(false);
      }
    }

    requestWakeLock();

    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        requestWakeLock();
      }
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      if (wakeLockRef.current) {
        wakeLockRef.current.release().then(() => {
          wakeLockRef.current = null;
        }).catch(() => {});
      }
    };
  }, [sessionActive]);

  // Synchronized startup beep based on server time
  useEffect(() => {
    if (!sessionData || sessionData.status !== 'running' || !sessionData.startTime) {
      return;
    }

    const startTime = sessionData.startTime;
    const localTargetTime = startTime - serverOffset;
    const timeUntilStart = localTargetTime - Date.now();

    let goTimeout: any;

    goTimeout = setTimeout(() => {
      playBeep(880, 0.40); // higher pitch start signal
    }, Math.max(0, timeUntilStart));

    return () => {
      clearTimeout(goTimeout);
    };
  }, [sessionData?.status, sessionData?.startTime, serverOffset]);

  const startRace = async () => {
    if (!code) return;
    unlockAudio();
    const startDelay = 2000; // 2 seconds delay to allow warm up warning click
    const startTime = Date.now() + serverOffset + startDelay;
    
    await update(ref(rtdb, `sessions/${code}`), {
      status: 'running',
      startTime
    });
  };

  const resetRace = async () => {
    if (!code) return;
    const updates: any = {};
    updates[`sessions/${code}/status`] = 'waiting';
    updates[`sessions/${code}/startTime`] = null;

    for (const client of clients) {
      updates[`sessions/${code}/clients/${client.id}/elapsedTime`] = null;
    }
    
    await update(ref(rtdb), updates);
  };

  if (!sessionActive) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 p-6 font-sans text-neutral-100">
        <div className="w-full max-w-sm bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-2xl">
          <div className="flex justify-center mb-6">
            <Users className="w-12 h-12 text-blue-500" />
          </div>
          <h2 className="text-2xl font-medium text-center mb-6">Create Session</h2>
          <div className="space-y-4">
            <div>
              <label className="block text-sm font-medium text-neutral-400 mb-2 text-center">Enter 3-Digit Code</label>
              <input 
                type="text" 
                maxLength={3}
                placeholder="000"
                className="w-full bg-neutral-950 text-neutral-100 border border-neutral-800 rounded-xl px-4 py-4 text-center text-4xl tracking-[0.5em] focus:outline-none focus:ring-2 focus:ring-blue-500 transition-all font-mono"
                value={code}
                onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              />
            </div>
            <button 
              onClick={createSession}
              disabled={code.length !== 3}
              className="w-full bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-4 rounded-xl transition-colors text-lg mt-4"
            >
              Open Room
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-screen bg-neutral-950 p-6 font-sans text-neutral-100">
      <div className="max-w-3xl mx-auto w-full space-y-6">
        <div className="flex items-center justify-between bg-neutral-900 border border-neutral-800 rounded-2xl p-6">
          <div>
            <p className="text-sm text-neutral-400 font-medium mb-1">SESSION CODE</p>
            <p className="text-4xl font-mono tracking-widest">{code}</p>
          </div>
          <div className="text-right">
            <p className="text-sm text-neutral-400 font-medium mb-1">STATUS</p>
            <div className={`px-3 py-1 rounded-full text-sm font-medium inline-block
              ${sessionData?.status === 'running' ? 'bg-green-500/10 text-green-400' : 'bg-yellow-500/10 text-yellow-400'}`}>
              {sessionData?.status?.toUpperCase() || 'WAITING'}
            </div>
          </div>
        </div>

        {/* Sensitivity & Threshold Configuration Card */}
        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-xl space-y-4">
          <div className="flex items-center justify-between">
            <h3 className="text-sm text-neutral-400 font-medium flex items-center gap-2">
              <Sliders className="w-4 h-4 text-blue-400" />
              CLIENT MOTION SENSITIVITY
            </h3>
            <span className="text-2xl font-mono font-bold text-blue-400">{sessionData?.sensitivity ?? 70}%</span>
          </div>
          <div className="space-y-2">
            <input 
              type="range"
              min="10"
              max="100"
              step="5"
              value={sessionData?.sensitivity ?? 70}
              onChange={async (e) => {
                const sensitivityValue = parseInt(e.target.value, 10);
                if (code) {
                  await update(ref(rtdb, `sessions/${code}`), {
                    sensitivity: sensitivityValue
                  });
                }
              }}
              className="w-full h-2.5 bg-neutral-950 rounded-lg appearance-none cursor-pointer accent-blue-500 border border-neutral-800"
            />
            <div className="flex justify-between text-[11px] text-neutral-500 font-medium font-mono">
              <span>MIN (10%)</span>
              <span>DEFAULT (70%)</span>
              <span>MAX (100%)</span>
            </div>
          </div>
          <p className="text-xs text-neutral-500 leading-relaxed">
            Adjusts how easily the client camera trapwires are triggered. Higher percentages make them trigger on smaller/subtle changes. Updates connected devices live.
          </p>
        </div>

        {/* Screen Wake Lock Card */}
        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-xl flex items-center justify-between">
          <div>
            <h3 className="text-sm text-neutral-400 font-medium flex items-center gap-2 mb-1">
              {wakeLockActive ? <Sun className="w-4 h-4 text-green-400 animate-pulse" /> : <Sun className="w-4 h-4 text-neutral-500 opacity-55" />}
              SCREEN WAKE LOCK
            </h3>
            <p className="text-xs text-neutral-500">
              {wakeLockSupported ? (
                wakeLockActive ? "Active — Screen will stay on while the console is open." : "Inactive"
              ) : (
                "Not supported by this browser"
              )}
            </p>
          </div>
          <div className={`px-3 py-1.5 rounded-xl text-xs font-semibold border ${
            !wakeLockSupported 
              ? 'bg-neutral-900 text-neutral-500 border-neutral-800'
              : wakeLockActive 
                ? 'bg-green-500/10 text-green-400 border-green-500/20' 
                : 'bg-red-500/10 text-red-400 border-red-500/20'
          }`}>
            {wakeLockSupported ? (wakeLockActive ? "STAY ON ACTIVE" : "LOCKED") : "UNSUPPORTED"}
          </div>
        </div>

        <div className="bg-neutral-900 border border-neutral-800 rounded-2xl p-6 min-h-[300px]">
          <h3 className="text-lg font-medium text-neutral-200 mb-4 flex items-center gap-2">
            <Users className="w-5 h-5 text-neutral-400" />
            Connected Devices ({clients.length})
          </h3>
          
          {clients.length === 0 ? (
            <div className="text-center py-12 flex flex-col items-center">
              <AlertCircle className="w-12 h-12 text-neutral-600 mb-4" />
              <p className="text-neutral-500 text-lg">Waiting for racers to join...</p>
            </div>
          ) : (
            <div className="space-y-3">
              {clients.map(client => (
                <div key={client.id} className="flex items-center justify-between bg-neutral-950 border border-neutral-800 rounded-xl p-4">
                  <div className="flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-neutral-800 flex items-center justify-center">
                      <span className="text-xs text-neutral-400 font-mono">{client.id.slice(0,2)}</span>
                    </div>
                    <span className="font-medium">{client.deviceName || 'Unknown Client'}</span>
                  </div>
                  <div className="font-mono text-xl">
                    {client.elapsedTime != null ? (
                      <span className="text-green-400 font-bold">{(client.elapsedTime / 1000).toFixed(3)}s</span>
                    ) : sessionData?.status === 'running' ? (
                      <span className="text-neutral-500 flex items-center gap-2">
                        <Clock className="w-4 h-4" />
                        Running...
                      </span>
                    ) : (
                      <span className="text-neutral-600">Ready</span>
                    )}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>

        <div className="grid grid-cols-2 gap-4">
          <button 
            onClick={startRace}
            disabled={clients.length === 0 || sessionData?.status === 'running'}
            className="flex items-center justify-center gap-2 bg-blue-600 hover:bg-blue-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-4 rounded-xl transition-colors text-lg"
          >
            <Play className="w-5 h-5" />
            Start Race (Beep)
          </button>
          
          <button 
            onClick={resetRace}
            disabled={sessionData?.status === 'waiting'}
            className="flex items-center justify-center gap-2 bg-neutral-800 hover:bg-neutral-700 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-4 rounded-xl transition-colors text-lg"
          >
            Reset
          </button>
        </div>
      </div>
    </div>
  );
}
