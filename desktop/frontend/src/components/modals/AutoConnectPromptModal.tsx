import { Zap } from "lucide-react";
import type { Device } from "../../types";

interface Props {
  device: Device | null;
  onEnable: () => void;
  onSkip: () => void;
}

export function AutoConnectPromptModal({ device, onEnable, onSkip }: Props) {
  if (!device) return null;

  return (
    <div className="absolute inset-0 z-50 bg-bg-base/80 backdrop-blur-sm flex items-center justify-center p-4 animate-in fade-in duration-200">
      <div className="bg-surface rounded-2xl shadow-2xl w-full max-w-sm p-6 flex flex-col items-center text-center animate-in fade-in zoom-in-95 duration-200">
        <div className="w-16 h-16 rounded-full bg-emerald-500/10 flex items-center justify-center mb-4 border border-emerald-500/20">
          <Zap size={28} className="text-emerald-500" />
        </div>
        <h3 className="text-lg font-semibold text-text mb-1">
          Enable Auto-Connect?
        </h3>
        <p className="text-sm text-light mb-6">
          Would you like to automatically connect to{" "}
          <strong>{device.deviceName}</strong> whenever it is discovered on the
          network?
        </p>
        <div className="flex gap-3 w-full">
          <button
            onClick={onSkip}
            className="flex-1 px-4 py-2.5 bg-panel text-light rounded-lg font-medium hover:text-text hover:bg-border transition-colors"
          >
            Not Now
          </button>
          <button
            onClick={onEnable}
            className="flex-1 px-4 py-2.5 bg-emerald-500/20 border border-emerald-500/30 text-emerald-500 rounded-lg font-medium hover:bg-emerald-500/30 transition-colors"
          >
            Enable
          </button>
        </div>
      </div>
    </div>
  );
}
