import { useEffect, useRef, useState } from 'react';
import { ref, set, onValue, update, onDisconnect } from 'firebase/database';
import { rtdb } from '../firebase';
import { Session } from '../types';
import { playBeep, unlockAudio } from '../lib/audio';
import { Camera, CheckCircle, Activity, ChevronRight, Lock, Unlock, Sun } from 'lucide-react';

type CameraSource = 'native' | 'browser';

declare global {
  interface Window {
    AndroidCamera?: {
      startCamera: () => void;
      stopCamera: () => void;
      setAeLocked: (locked: boolean) => void;
    };
    NativeCameraBridge?: {
      onFrame: (dataUrl: string, width: number, height: number, timestamp: number) => void;
      onStatus: (active: boolean, aeLocked: boolean, message?: string) => void;
      onError: (message: string) => void;
    };
  }
}

const hasNativeAndroidCamera = () => typeof window !== 'undefined' && !!window.AndroidCamera;

export default function ClientSession() {
  const [code, setCode] = useState('123');
  const [joined, setJoined] = useState(false);
  const [sessionData, setSessionData] = useState<Session | null>(null);
  const [clientId] = useState(() => Math.random().toString(36).substring(2, 8));
  const [deviceName, setDeviceName] = useState('Client ' + Math.floor(Math.random() * 100));
  const [serverOffset, setServerOffset] = useState<number>(0);

  const [triggered, setTriggered] = useState(false);
  const [finalTime, setFinalTime] = useState<number | null>(null);
  const [localTimer, setLocalTimer] = useState(0);

  const [nativeCameraAvailable] = useState(hasNativeAndroidCamera);
  const [cameraActive, setCameraActive] = useState(false);
  const [cameraTrack, setCameraTrack] = useState<MediaStreamTrack | null>(null);
  const [aeLocked, setAeLocked] = useState(false);

  const [wakeLockActive, setWakeLockActive] = useState(false);
  const [wakeLockSupported, setWakeLockSupported] = useState(true);
  const wakeLockRef = useRef<any>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const processCanvasRef = useRef<HTMLCanvasElement>(null);
  const requestRef = useRef<number>(0);
  const previousPixelsRef = useRef<Uint8ClampedArray | null>(null);

  const browserStreamRef = useRef<MediaStream | null>(null);
  const cameraSourceRef = useRef<CameraSource | null>(null);
  const nativeFrameImageRef = useRef<HTMLImageElement | null>(null);
  const nativeFrameReadyRef = useRef(false);

  // Monitor server time offset
  useEffect(() => {
    const offsetRef = ref(rtdb, '.info/serverTimeOffset');
    const unsubOffset = onValue(offsetRef, (snapshot) => {
      if (snapshot.exists()) {
        setServerOffset(snapshot.val() as number);
      }
    });
    return () => unsubOffset();
  }, []);

  useEffect(() => {
    return () => {
      try {
        window.AndroidCamera?.stopCamera();
      } catch (_) {}
      browserStreamRef.current?.getTracks().forEach((track) => track.stop());
      browserStreamRef.current = null;
    };
  }, []);

  const installNativeCameraBridge = () => {
    if (typeof window === 'undefined') return;

    window.NativeCameraBridge = {
      onFrame: (dataUrl: string) => {
        let image = nativeFrameImageRef.current;
        if (!image) {
          image = new Image();
          image.onload = () => {
            nativeFrameReadyRef.current = true;
            setCameraActive(true);
          };
          image.onerror = () => {
            nativeFrameReadyRef.current = false;
          };
          nativeFrameImageRef.current = image;
        }

        nativeFrameReadyRef.current = false;
        image.src = dataUrl;
      },
      onStatus: (active: boolean, nextAeLocked: boolean, message?: string) => {
        setCameraActive(active);
        setAeLocked(nextAeLocked);
        if (message) {
          console.log('[NativeCamera]', message);
        }
      },
      onError: (message: string) => {
        console.error('[NativeCamera]', message);
        alert(message || 'Native Android camera failed.');
      },
    };
  };

  const joinSession = async () => {
    if (code.length !== 3) return;
    try {
      unlockAudio();
      const clientRef = ref(rtdb, `sessions/${code}/clients/${clientId}`);
      await set(clientRef, {
        joinedAt: Date.now(),
        deviceName,
      });
      await onDisconnect(clientRef).remove();
      setJoined(true);
      startCamera();
    } catch (e) {
      console.error(e);
      alert('Failed to join session.');
    }
  };

  const startCamera = async () => {
    if (hasNativeAndroidCamera()) {
      installNativeCameraBridge();
      cameraSourceRef.current = 'native';
      setCameraActive(true);
      window.AndroidCamera?.startCamera();
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: {
          facingMode: 'user',
          width: { ideal: 320, max: 480 },
          height: { ideal: 240, max: 360 },
        },
      });
      browserStreamRef.current = stream;
      cameraSourceRef.current = 'browser';
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
      }
      const track = stream.getVideoTracks()[0];
      if (track) {
        setCameraTrack(track);
      }
      setCameraActive(true);
    } catch (err) {
      console.error('Error accessing camera:', err);
      alert('Camera access denied or not available.');
    }
  };

  const toggleAeLock = async () => {
    if (cameraSourceRef.current === 'native' && window.AndroidCamera) {
      const nextLocked = !aeLocked;
      setAeLocked(nextLocked);
      window.AndroidCamera.setAeLocked(nextLocked);
      return;
    }

    if (!cameraTrack) return;
    try {
      if (aeLocked) {
        // Unlock: Switch back to continuous auto options
        const constraints: any = {
          advanced: [{ exposureMode: 'continuous' }],
        };
        try {
          const caps = (cameraTrack as any).getCapabilities?.() || {};
          if (caps.whiteBalanceMode?.includes('continuous')) {
            constraints.advanced.push({ whiteBalanceMode: 'continuous' });
          }
          if (caps.focusMode?.includes('continuous')) {
            constraints.advanced.push({ focusMode: 'continuous' });
          }
        } catch (_) {}

        await cameraTrack.applyConstraints(constraints);
        setAeLocked(false);
      } else {
        // Lock: Switch to manual (maintaining current automatic values)
        const constraints: any = {
          advanced: [{ exposureMode: 'manual' }],
        };
        try {
          const caps = (cameraTrack as any).getCapabilities?.() || {};
          if (caps.whiteBalanceMode?.includes('manual')) {
            constraints.advanced.push({ whiteBalanceMode: 'manual' });
          }
          if (caps.focusMode?.includes('manual')) {
            constraints.advanced.push({ focusMode: 'manual' });
          }
        } catch (_) {}

        await cameraTrack.applyConstraints(constraints);
        setAeLocked(true);
      }
    } catch (e) {
      console.warn('Could not apply camera constraints fully, forcing state toggle:', e);
      // fallback manual simulation toggle so the UI still displays status changes
      setAeLocked(!aeLocked);
    }
  };

  // Screen Wake Lock control
  useEffect(() => {
    if (!joined) return;

    if (nativeCameraAvailable) {
      // The Android shell keeps the screen awake with FLAG_KEEP_SCREEN_ON.
      setWakeLockSupported(true);
      setWakeLockActive(true);
      return;
    }

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
        const lock = await (navigator as any).wakeLock.request('screen');
        wakeLockRef.current = lock;
        setWakeLockActive(true);
        console.log('Client Screen Wake Lock acquired successfully.');

        lock.addEventListener('release', () => {
          setWakeLockActive(false);
        });
      } catch (err) {
        console.warn('Client Screen Wake Lock request failed:', err);
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
        wakeLockRef.current
          .release()
          .then(() => {
            wakeLockRef.current = null;
          })
          .catch(() => {});
      }
    };
  }, [joined, nativeCameraAvailable]);

  useEffect(() => {
    if (!joined || !code) return;
    const unsubSession = onValue(ref(rtdb, `sessions/${code}`), (snapshot) => {
      if (snapshot.exists()) {
        const data = snapshot.val() as Session;
        setSessionData(data);
      }
    });
    return () => unsubSession();
  }, [joined, code]);

  // Synchronized startup beep based on server time
  useEffect(() => {
    if (!sessionData || sessionData.status !== 'running' || !sessionData.startTime) {
      return;
    }

    const startTime = sessionData.startTime;
    const localTargetTime = startTime - serverOffset;
    const timeUntilStart = localTargetTime - Date.now();

    const goTimeout = setTimeout(() => {
      playBeep(880, 0.4); // higher pitch start signal
    }, Math.max(0, timeUntilStart));

    return () => {
      clearTimeout(goTimeout);
    };
  }, [sessionData?.status, sessionData?.startTime, serverOffset]);

  const recordTime = async (elapsedTime: number) => {
    setTriggered(true);
    setFinalTime(elapsedTime);
    try {
      if (code && clientId) {
        await update(ref(rtdb, `sessions/${code}/clients/${clientId}`), {
          elapsedTime,
        });
      }
    } catch (e) {
      console.error('Failed to save time', e);
    }
  };

  // Main loop
  useEffect(() => {
    const currentSession = sessionData;
    if (!joined || !currentSession) return;

    if (currentSession.status === 'waiting') {
      setTriggered(false);
      setFinalTime(null);
      setLocalTimer(0);
      previousPixelsRef.current = null;
    }

    const drawAndProcessFrame = (source: CanvasImageSource, sourceWidth: number, sourceHeight: number) => {
      if (!canvasRef.current || !processCanvasRef.current) return;

      const canvas = canvasRef.current;
      const ctx = canvas.getContext('2d');
      const pCanvas = processCanvasRef.current;
      const pCtx = pCanvas.getContext('2d', { willReadFrequently: true });
      if (!ctx || !pCtx || !sourceWidth || !sourceHeight) return;

      // Avoid resetting canvas size unless the camera frame size actually changed.
      if (canvas.width !== sourceWidth || canvas.height !== sourceHeight) {
        canvas.width = sourceWidth;
        canvas.height = sourceHeight;
      }
      if (pCanvas.width !== 160 || pCanvas.height !== 120) {
        pCanvas.width = 160;
        pCanvas.height = 120;
      }

      ctx.imageSmoothingEnabled = false;
      pCtx.imageSmoothingEnabled = false;

      // Draw the actual camera image on the visible canvas. This works for both native frames
      // from CameraX and the browser fallback video element.
      ctx.drawImage(source, 0, 0, canvas.width, canvas.height);
      ctx.fillStyle = 'rgba(0, 0, 0, 0.25)';
      ctx.fillRect(0, 0, canvas.width, canvas.height);

      // Draw downsampled frame to process canvas.
      pCtx.drawImage(source, 0, 0, pCanvas.width, pCanvas.height);

      // Trapwire is center 2%.
      const trapWidth = Math.max(1, Math.floor(pCanvas.width * 0.02));
      const trapX = Math.floor(pCanvas.width / 2) - Math.floor(trapWidth / 2);

      // Draw trapwire overlay on display canvas.
      ctx.lineWidth = Math.max(2, canvas.width * 0.01);
      ctx.strokeStyle = currentSession.status === 'running' && !triggered ? 'rgba(239, 68, 68, 0.8)' : 'rgba(156, 163, 175, 0.5)';
      ctx.beginPath();
      const displayX = canvas.width / 2;
      ctx.moveTo(displayX, 0);
      ctx.lineTo(displayX, canvas.height);
      ctx.stroke();

      // Motion detection if running and not yet triggered.
      if (currentSession.status === 'running' && currentSession.startTime && !triggered) {
        const serverNow = Date.now() + serverOffset;

        if (serverNow >= currentSession.startTime) {
          const elapsed = serverNow - currentSession.startTime;
          setLocalTimer(elapsed);

          const imgData = pCtx.getImageData(trapX, 0, trapWidth, pCanvas.height);
          const currentPixels = imgData.data;

          if (previousPixelsRef.current && elapsed > 200) {
            let lumaDiff = 0;
            const numPixels = currentPixels.length / 4;
            for (let i = 0; i < currentPixels.length; i += 4) {
              const r1 = currentPixels[i];
              const g1 = currentPixels[i + 1];
              const b1 = currentPixels[i + 2];
              const luma1 = r1 * 0.299 + g1 * 0.587 + b1 * 0.114;

              const r2 = previousPixelsRef.current[i];
              const g2 = previousPixelsRef.current[i + 1];
              const b2 = previousPixelsRef.current[i + 2];
              const luma2 = r2 * 0.299 + g2 * 0.587 + b2 * 0.114;

              lumaDiff += Math.abs(luma1 - luma2);
            }
            const avgLumaDiff = lumaDiff / numPixels;
            const sensitivity = currentSession.sensitivity ?? 70;
            const targetThreshold = Math.max(1, Math.round(50 - (sensitivity * 48) / 100));

            if (avgLumaDiff > targetThreshold) {
              recordTime(elapsed);
            }
          }

          const newArr = new Uint8ClampedArray(currentPixels.length);
          newArr.set(currentPixels);
          previousPixelsRef.current = newArr;
        } else {
          setLocalTimer(0);
          previousPixelsRef.current = null;
        }
      }
    };

    const loop = () => {
      if (cameraSourceRef.current === 'native') {
        const nativeImage = nativeFrameImageRef.current;
        if (nativeImage && nativeFrameReadyRef.current && nativeImage.naturalWidth > 0 && nativeImage.naturalHeight > 0) {
          drawAndProcessFrame(nativeImage, nativeImage.naturalWidth, nativeImage.naturalHeight);
        }
      } else if (videoRef.current && videoRef.current.readyState === videoRef.current.HAVE_ENOUGH_DATA) {
        drawAndProcessFrame(videoRef.current, videoRef.current.videoWidth, videoRef.current.videoHeight);
      }

      requestRef.current = requestAnimationFrame(loop);
    };

    requestRef.current = requestAnimationFrame(loop);

    return () => cancelAnimationFrame(requestRef.current);
  }, [joined, sessionData, triggered, serverOffset]);

  const cameraModeLabel = nativeCameraAvailable ? 'Native Android camera' : 'Browser camera fallback';

  if (!joined) {
    return (
      <div className="flex flex-col items-center justify-center min-h-screen bg-neutral-950 p-6 font-sans text-neutral-100">
        <div className="w-full max-w-sm bg-neutral-900 border border-neutral-800 rounded-2xl p-6 shadow-2xl space-y-6">
          <div className="flex justify-center">
            <Camera className="w-12 h-12 text-green-500" />
          </div>
          <h2 className="text-2xl font-medium text-center">Join Race</h2>

          <div>
            <label className="block text-sm font-medium text-neutral-400 mb-2">Device Name</label>
            <input
              type="text"
              className="w-full bg-neutral-950 text-neutral-100 border border-neutral-800 rounded-xl px-4 py-3 focus:outline-none focus:ring-2 focus:ring-green-500 transition-all font-medium"
              value={deviceName}
              onChange={(e) => setDeviceName(e.target.value)}
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-neutral-400 mb-2">3-Digit Session Code</label>
            <input
              type="text"
              maxLength={3}
              placeholder="000"
              className="w-full bg-neutral-950 text-neutral-100 border border-neutral-800 rounded-xl px-4 py-4 text-center text-4xl tracking-[0.5em] focus:outline-none focus:ring-2 focus:ring-green-500 transition-all font-mono"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
            />
          </div>

          <button
            onClick={joinSession}
            disabled={code.length !== 3 || !deviceName.trim()}
            className="w-full flex items-center justify-center gap-2 bg-green-600 hover:bg-green-500 disabled:opacity-50 disabled:cursor-not-allowed text-white font-medium py-4 rounded-xl transition-colors text-lg mt-4"
          >
            Connect <ChevronRight className="w-5 h-5" />
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col min-h-screen bg-neutral-950 font-sans text-neutral-100 pb-10">
      {/* Invisible canvas for processing */}
      <canvas ref={processCanvasRef} className="hidden" />

      <div className="p-6 pb-2">
        <div className="flex items-center justify-between bg-neutral-900 border border-neutral-800 rounded-2xl p-4">
          <div>
            <p className="text-xs text-neutral-500 font-medium mb-1 uppercase">CODE</p>
            <p className="text-xl font-mono tracking-widest">{code}</p>
          </div>
          <div className="text-right">
            <div
              className={`px-3 py-1 rounded-full text-xs font-medium inline-flex items-center gap-2
              ${sessionData?.status === 'running' ? 'bg-red-500/10 text-red-500' : 'bg-green-500/10 text-green-400'}`}
            >
              <Activity className="w-3 h-3" />
              {sessionData?.status === 'running' ? 'ARMED' : 'WAITING'}
            </div>
          </div>
        </div>

        {joined && cameraActive && (
          <div className="mt-3 flex items-center justify-between bg-neutral-900 border border-neutral-800 rounded-2xl p-4 animate-in fade-in duration-200">
            <div>
              <p className="text-xs text-neutral-500 font-medium uppercase mb-1">Exposure & Focus Control</p>
              <p className="text-sm text-neutral-300 font-medium">
                {aeLocked ? 'Camera locked (no brightness shift)' : 'Camera auto-adjusting'}
              </p>
              <p className="text-xs text-neutral-500 mt-1">{cameraModeLabel}</p>
            </div>
            <button
              onClick={toggleAeLock}
              className={`flex items-center gap-2 px-3 py-2 rounded-xl text-xs font-semibold border transition-all ${
                aeLocked
                  ? 'bg-amber-500/10 text-amber-400 border-amber-500/20 hover:bg-amber-500/20'
                  : 'bg-green-500/10 text-green-400 border-green-500/20 hover:bg-green-500/20'
              }`}
            >
              {aeLocked ? <Lock className="w-3.5 h-3.5" /> : <Unlock className="w-3.5 h-3.5" />}
              {aeLocked ? 'Unlock AE/Focus' : 'Lock AE/Focus'}
            </button>
          </div>
        )}

        {joined && sessionData && (
          <div className="mt-2 flex items-center justify-between bg-neutral-900 border border-neutral-800 rounded-2xl p-4 animate-in fade-in duration-200">
            <div>
              <p className="text-xs text-neutral-500 font-medium uppercase mb-1">Motion Sensitivity</p>
              <p className="text-sm text-neutral-300 font-medium">
                Set by controller: <span className="text-blue-400 font-bold font-mono">{sessionData.sensitivity ?? 70}%</span>
              </p>
            </div>
            <div className="text-right">
              <p className="text-[10px] text-neutral-500 font-medium uppercase mb-1">Luma Threshold</p>
              <p className="text-xs text-neutral-400 font-mono font-bold bg-neutral-950 px-2.5 py-1 rounded-lg border border-neutral-800">
                {Math.max(1, Math.round(50 - ((sessionData.sensitivity ?? 70) * 48) / 100))} (diff)
              </p>
            </div>
          </div>
        )}

        {joined && (
          <div className="mt-2 flex items-center justify-between bg-neutral-900 border border-neutral-800 rounded-2xl p-4 animate-in fade-in duration-200">
            <div>
              <p className="text-xs text-neutral-500 font-medium uppercase mb-1">Screen Wake Lock</p>
              <p className="text-sm text-neutral-300 font-medium flex items-center gap-1.5">
                {wakeLockSupported ? wakeLockActive ? 'Active (Screen will stay turned on)' : 'Inactive' : 'Not supported by this browser'}
              </p>
            </div>
            <div
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold border ${
                !wakeLockSupported
                  ? 'bg-neutral-900 text-neutral-500 border-neutral-800'
                  : wakeLockActive
                    ? 'bg-green-500/10 text-green-400 border-green-500/20'
                    : 'bg-red-500/10 text-red-400 border-red-500/20'
              }`}
            >
              {wakeLockSupported ? (
                wakeLockActive ? (
                  <Sun className="w-3.5 h-3.5 text-green-400 animate-pulse" />
                ) : (
                  <Sun className="w-3.5 h-3.5 text-red-400 opacity-50" />
                )
              ) : (
                <Sun className="w-3.5 h-3.5 text-neutral-500 opacity-30" />
              )}
              {wakeLockSupported ? (wakeLockActive ? 'STAY ON' : 'LOCKED') : 'UNSUPPORTED'}
            </div>
          </div>
        )}
      </div>

      <div className="relative flex-1 mx-6 mb-6 rounded-2xl overflow-hidden border border-neutral-800 bg-black flex items-center justify-center">
        <video ref={videoRef} autoPlay playsInline muted className="hidden" />
        <canvas ref={canvasRef} className="absolute inset-0 w-full h-full object-cover z-10" />

        {triggered && (
          <div className="absolute inset-x-0 bottom-10 z-20 flex justify-center">
            <div className="bg-green-500 text-black px-6 py-4 rounded-2xl shadow-xl flex items-center gap-3 animate-in fade-in slide-in-from-bottom-4">
              <CheckCircle className="w-8 h-8" />
              <div>
                <p className="text-sm font-semibold opacity-80 leading-none mb-1">TRIGGERED</p>
                <p className="text-3xl font-mono font-bold leading-none">{(finalTime! / 1000).toFixed(3)}s</p>
              </div>
            </div>
          </div>
        )}

        {sessionData?.status === 'running' && !triggered && (
          <div className="absolute inset-x-0 top-10 z-20 flex justify-center">
            <div className="bg-black/60 backdrop-blur border border-white/10 px-4 py-2 rounded-xl text-center shadow-lg">
              <p className="font-mono text-2xl font-medium tracking-tight">{(localTimer / 1000).toFixed(2)}s</p>
            </div>
          </div>
        )}
      </div>

      <div className="px-6 text-center text-neutral-500 text-sm">
        <p>Align the vertical trapwire with the finish line or path. Motion across the line will stop the timer.</p>
      </div>
    </div>
  );
}
