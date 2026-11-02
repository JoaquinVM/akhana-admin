// POS-15: the reader always beeps when it reads, so the app only sounds when the scan did not add anything
export function scanWarningBeep(): void {
  try {
    const context = new AudioContext();
    const oscillator = context.createOscillator();
    oscillator.type = 'square';
    oscillator.frequency.value = 220;
    oscillator.connect(context.destination);
    oscillator.onended = () => void context.close();
    oscillator.start();
    oscillator.stop(context.currentTime + 0.25);
  } catch {
    // Without audio, the notice on screen is enough
  }
}
