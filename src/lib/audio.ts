let sharedAudioCtx: AudioContext | null = null;

export function getAudioContext(): AudioContext {
  if (!sharedAudioCtx) {
    sharedAudioCtx = new (window.AudioContext || (window as any).webkitAudioContext)();
  }
  return sharedAudioCtx;
}

export function unlockAudio() {
  try {
    const ctx = getAudioContext();
    if (ctx.state === 'suspended') {
      ctx.resume();
    }
    // Play a quick silent sound to unlock audio policies on Chrome Android
    const osc = ctx.createOscillator();
    const gainNode = ctx.createGain();
    gainNode.gain.setValueAtTime(0.0001, ctx.currentTime);
    osc.connect(gainNode);
    gainNode.connect(ctx.destination);
    osc.start();
    osc.stop(ctx.currentTime + 0.01);
  } catch (err) {
    console.warn("Failed to unlock audio:", err);
  }
}

export function playBeep(frequency = 800, duration = 0.5) {
  try {
    const audioCtx = getAudioContext();
    if (audioCtx.state === 'suspended') {
      audioCtx.resume();
    }
    const oscillator = audioCtx.createOscillator();
    const gainNode = audioCtx.createGain();

    // Triangle waves are much more piercing and loud compared to simple sine waves,
    // making them perfect for sports/timing starters while remaining high quality.
    oscillator.type = 'triangle';
    oscillator.frequency.setValueAtTime(frequency, audioCtx.currentTime);
    
    // Hold maximum volume for most of the duration, then quickly ramp down at the very end to prevent popping/clicks.
    gainNode.gain.setValueAtTime(1.0, audioCtx.currentTime);
    gainNode.gain.setValueAtTime(1.0, audioCtx.currentTime + duration - 0.05);
    gainNode.gain.linearRampToValueAtTime(0.001, audioCtx.currentTime + duration);

    oscillator.connect(gainNode);
    gainNode.connect(audioCtx.destination);

    oscillator.start();
    oscillator.stop(audioCtx.currentTime + duration);
  } catch (err) {
    console.warn("AudioContext failed to start. User interaction may be required:", err);
  }
}

